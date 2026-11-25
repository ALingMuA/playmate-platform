package com.gameplay.ai.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.gameplay.common.exception.BusinessException;
import com.gameplay.common.exception.ErrorCode;
import org.springframework.util.StringUtils;

import java.net.InetAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/** 管理配置与实际请求共用校验，防范 SSRF 并确保错误消息不包含用户输入或密钥。 */
public final class AiModelConfigValidator {
    private static final Set<String> RESERVED = Set.of("model", "messages", "stream", "max_tokens",
            "max_completion_tokens", "temperature", "tools", "tool_choice", "functions", "function_call", "n");
    private static final Set<String> BLOCKED_HEADERS = Set.of("authorization", "host", "content-length",
            "connection", "content-type", "transfer-encoding", "upgrade", "expect", "cookie", "proxy-authorization");

    private AiModelConfigValidator() {}

    public static URI endpoint(AiModelProperties p) {
        return endpoint(p, false);
    }

    public static URI endpoint(AiModelProperties p, boolean allowLoopback) {
        try {
            String base = p.getBaseUrl() == null ? "" : p.getBaseUrl().trim().replaceAll("/+$", "");
            String chatPath = p.getChatPath() == null ? "" : p.getChatPath().trim();
            String address = StringUtils.hasText(p.getEndpointUrl()) ? p.getEndpointUrl().trim() : base + chatPath;
            URI uri = URI.create(address);
            if (address.length() > 2000 || uri.getHost() == null || uri.getUserInfo() != null
                    || uri.getFragment() != null) {
                throw new IllegalArgumentException();
            }
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            if (!"https".equals(scheme) && !"http".equals(scheme)) {
                throw new IllegalArgumentException();
            }
            if ("http".equals(scheme) && !allowLoopback) {
                throw new IllegalArgumentException();
            }
            // URL 可携带 api-version 等路由参数；认证凭据只能放在后端请求头中。
            if (uri.getRawQuery() != null) {
                for (String part : uri.getRawQuery().split("&")) {
                    String key = URLDecoder.decode(part.split("=", 2)[0], StandardCharsets.UTF_8)
                            .toLowerCase(Locale.ROOT);
                    if (key.matches(".*(key|token|secret|password|authorization|signature|credential).*")) {
                        throw new IllegalArgumentException();
                    }
                }
            }
            String host = normalizeHost(uri.getHost());
            if (!StringUtils.hasText(host) || host.contains("%")) {
                throw new IllegalArgumentException();
            }
            InetAddress[] resolved = InetAddress.getAllByName(host);
            if (resolved == null || resolved.length == 0) {
                throw new IllegalArgumentException();
            }
            boolean allLoopback = true;
            for (InetAddress addr : resolved) {
                if (isBlockedAddress(addr, allowLoopback)) {
                    throw new IllegalArgumentException();
                }
                if (!addr.isLoopbackAddress()) {
                    allLoopback = false;
                }
            }
            if ("http".equals(scheme) && !(allowLoopback && allLoopback)) {
                throw new IllegalArgumentException();
            }
            int port = uri.getPort();
            if (!allLoopback) {
                if (port != -1 && port != 443) {
                    throw new IllegalArgumentException();
                }
            } else if (port != -1 && (port < 1 || port > 65535)) {
                throw new IllegalArgumentException();
            }
            return uri;
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ignored) {
            throw invalid("接口地址须为公网 HTTPS（443端口），不能指向内网/保留地址或在 URL 中填写密钥");
        }
    }

    public static void validate(AiModelProperties p, boolean requireKey) {
        validate(p, requireKey, false);
    }

    public static void validate(AiModelProperties p, boolean requireKey, boolean allowLoopback) {
        if (!StringUtils.hasText(p.getName()) || p.getName().length() > 100 || controls(p.getName())) {
            throw invalid("模型名称不能为空且不能超过 100 字符");
        }
        if (!StringUtils.hasText(p.getEndpointUrl()) && (p.getBaseUrl() == null || p.getChatPath() == null
                || !p.getChatPath().startsWith("/") || p.getChatPath().startsWith("//")
                || p.getChatPath().contains("..") || p.getChatPath().contains("?") || p.getChatPath().contains("#"))) {
            throw invalid("请求路径须以 / 开头；含查询参数时请填写完整请求地址");
        }
        endpoint(p, allowLoopback);
        if (!Set.of("BEARER", "API_KEY", "NONE").contains(p.getAuthMode() == null ? "" : p.getAuthMode())) {
            throw invalid("不支持的认证方式");
        }
        if ("API_KEY".equals(p.getAuthMode())) {
            validateHeader(p.getAuthHeaderName(), false);
        }
        if (p.getApiKey() == null || p.getApiKey().length() > 8192 || controls(p.getApiKey())
                || !p.getApiKey().equals(p.getApiKey().trim())) {
            throw invalid("API Key 格式不正确");
        }
        if (requireKey && !"NONE".equals(p.getAuthMode()) && !StringUtils.hasText(p.getApiKey())) {
            throw invalid("请配置 API Key 后再启用或测试");
        }
        if ("BEARER".equals(p.getAuthMode()) && p.getApiKey().regionMatches(true, 0, "Bearer ", 0, 7)) {
            throw invalid("API Key 请只填写密钥，不包含 Bearer 前缀");
        }
        if (p.getTimeoutSeconds() < 1 || p.getTimeoutSeconds() > 120 || p.getConnectTimeoutSeconds() < 1
                || p.getConnectTimeoutSeconds() > 30) {
            throw invalid("总超时范围 1–120 秒，连接超时范围 1–30 秒");
        }
        if (p.getMaxOutputTokens() < 64 || p.getMaxOutputTokens() > 32768 || p.getHistoryMessages() < 0
                || p.getHistoryMessages() > 50) {
            throw invalid("输出上限范围 64–32768，历史消息范围 0–50");
        }
        if (!Set.of("max_tokens", "max_completion_tokens")
                .contains(p.getMaxTokensParameter() == null ? "" : p.getMaxTokensParameter())) {
            throw invalid("不支持的输出上限参数名");
        }
        if (p.getTemperature() != null && (!Double.isFinite(p.getTemperature()) || p.getTemperature() < 0
                || p.getTemperature() > 2)) {
            throw invalid("温度范围 0–2，可留空不发送");
        }
        if (p.getCustomHeaders() == null || p.getCustomHeaders().size() > 20) {
            throw invalid("附加请求头最多 20 项");
        }
        var names = new HashSet<String>();
        p.getCustomHeaders().forEach((key, value) -> {
            validateHeader(key, true);
            if (!names.add(key.toLowerCase(Locale.ROOT))
                    || ("API_KEY".equals(p.getAuthMode()) && key.equalsIgnoreCase(p.getAuthHeaderName()))) {
                throw invalid("请求头不能重复或覆盖认证头");
            }
            if (value == null || value.length() > 8192 || controls(value)) {
                throw invalid("请求头值无效");
            }
        });
        if (p.getExtraBody() != null) {
            if (p.getExtraBody().toString().length() > 16000) {
                throw invalid("扩展请求参数过长");
            }
            p.getExtraBody().fieldNames().forEachRemaining(key -> {
                if (RESERVED.contains(key)) {
                    throw invalid("扩展参数不能覆盖核心请求字段或启用工具调用");
                }
            });
            rejectSecrets(p.getExtraBody());
        }
    }

    private static String normalizeHost(String host) {
        if (host == null) {
            return "";
        }
        if (host.startsWith("[") && host.endsWith("]") && host.length() > 2) {
            return host.substring(1, host.length() - 1);
        }
        return host;
    }

    private static boolean isBlockedAddress(InetAddress addr, boolean allowLoopback) {
        if (addr.isAnyLocalAddress() || addr.isLinkLocalAddress()
                || addr.isSiteLocalAddress() || addr.isMulticastAddress()) {
            return true;
        }
        if (addr.isLoopbackAddress()) {
            return !allowLoopback;
        }
        byte[] raw = addr.getAddress();
        if (raw.length == 4) {
            return isBlockedIpv4(raw, allowLoopback);
        }
        if (raw.length == 16) {
            // IPv6 ULA fc00::/7
            if ((raw[0] & 0xFE) == 0xFC) {
                return true;
            }
            // fe80::/10 link-local, fec0::/10 site-local, ff00::/8 multicast
            int b0 = raw[0] & 0xFF;
            int b1 = raw[1] & 0xFF;
            if (b0 == 0xFE && ((b1 & 0xC0) == 0x80 || (b1 & 0xC0) == 0xC0)) {
                return true;
            }
            if (b0 == 0xFF) {
                return true;
            }
            // 100::/64 discard-only
            if (b0 == 0x01 && b1 == 0x00 && raw[2] == 0 && raw[3] == 0
                    && raw[4] == 0 && raw[5] == 0 && raw[6] == 0 && raw[7] == 0) {
                return true;
            }
            // 2001:db8::/32 documentation
            if (b0 == 0x20 && b1 == 0x01 && (raw[2] & 0xFF) == 0x0D && (raw[3] & 0xFF) == 0xB8) {
                return true;
            }
            // 2001::/32 Teredo tunneling
            if (b0 == 0x20 && b1 == 0x01 && raw[2] == 0 && raw[3] == 0) {
                return true;
            }
            // 64:ff9b::/96 & 64:ff9b:1::/48 NAT64
            if (b0 == 0x00 && b1 == 0x64 && (raw[2] & 0xFF) == 0xFF && (raw[3] & 0xFF) == 0x9B) {
                return true;
            }
            // 2002::/16 6to4 embedded IPv4
            if (b0 == 0x20 && b1 == 0x02) {
                return isBlockedIpv4(new byte[]{raw[2], raw[3], raw[4], raw[5]}, allowLoopback);
            }
            // ::/96 IPv4-compatible or ::ffff:0:0/96 IPv4-mapped
            boolean zeroPrefix = true;
            for (int i = 0; i < 10; i++) {
                if (raw[i] != 0) {
                    zeroPrefix = false;
                    break;
                }
            }
            if (zeroPrefix && ((raw[10] == 0 && raw[11] == 0)
                    || ((raw[10] & 0xFF) == 0xFF && (raw[11] & 0xFF) == 0xFF))) {
                return isBlockedIpv4(new byte[]{raw[12], raw[13], raw[14], raw[15]}, allowLoopback);
            }
            return false;
        }
        return true;
    }

    private static boolean isBlockedIpv4(byte[] raw, boolean allowLoopback) {
        int b0 = raw[0] & 0xFF;
        int b1 = raw[1] & 0xFF;
        int b2 = raw[2] & 0xFF;
        if (b0 == 0 || b0 == 10) {
            return true;
        }
        if (b0 == 127) {
            return !allowLoopback;
        }
        // 100.64.0.0/10 CGNAT / 云内部网络
        if (b0 == 100 && b1 >= 64 && b1 <= 127) {
            return true;
        }
        // 169.254.0.0/16 链路本地 / 云元数据
        if (b0 == 169 && b1 == 254) {
            return true;
        }
        // 172.16.0.0/12
        if (b0 == 172 && b1 >= 16 && b1 <= 31) {
            return true;
        }
        // 192.168.0.0/16
        if (b0 == 192 && b1 == 168) {
            return true;
        }
        // 192.0.0.0/24, 192.0.2.0/24
        if (b0 == 192 && b1 == 0 && (b2 == 0 || b2 == 2)) {
            return true;
        }
        // 198.18.0.0/15, 198.51.100.0/24
        if (b0 == 198 && (b1 == 18 || b1 == 19 || (b1 == 51 && b2 == 100))) {
            return true;
        }
        // 203.0.113.0/24
        if (b0 == 203 && b1 == 0 && b2 == 113) {
            return true;
        }
        // 224.0.0.0/4 multicast + 240.0.0.0/4 reserved / broadcast
        return b0 >= 224;
    }

    private static void rejectSecrets(JsonNode node) {
        if (node.isObject()) {
            node.fields().forEachRemaining(field -> {
                if (field.getKey().toLowerCase(Locale.ROOT)
                        .matches("(?:api.?key|access.?token|auth.?token|secret|password|authorization|credentials?)")) {
                    throw invalid("扩展请求参数中不要填写凭据，请使用认证或附加请求头");
                }
                rejectSecrets(field.getValue());
            });
        }
        if (node.isArray()) {
            node.forEach(AiModelConfigValidator::rejectSecrets);
        }
    }

    private static void validateHeader(String name, boolean custom) {
        if (name == null || !name.matches("[A-Za-z0-9!#$%&'*+.^_`|~-]{1,80}")
                || BLOCKED_HEADERS.contains(name.toLowerCase(Locale.ROOT))) {
            throw invalid(custom ? "附加请求头名称无效或覆盖了系统请求头" : "认证请求头名称无效");
        }
    }

    private static boolean controls(String text) {
        return text.chars().anyMatch(c -> c < 32 || c == 127);
    }

    private static BusinessException invalid(String message) {
        return new BusinessException(ErrorCode.VALIDATION_FAILED, message);
    }
}
