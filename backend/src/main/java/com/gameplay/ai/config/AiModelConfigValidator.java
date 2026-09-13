package com.gameplay.ai.config;

import com.gameplay.common.exception.BusinessException;
import com.gameplay.common.exception.ErrorCode;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;
import org.springframework.util.StringUtils;

/** 管理配置与实际请求共用校验，错误消息不包含用户输入或密钥。 */
public final class AiModelConfigValidator {
    private static final Set<String> RESERVED = Set.of("model", "messages", "stream", "max_tokens",
            "max_completion_tokens", "temperature", "tools", "tool_choice", "functions", "function_call", "n");
    private static final Set<String> BLOCKED_HEADERS = Set.of("authorization", "host", "content-length",
            "connection", "content-type", "transfer-encoding", "upgrade", "expect", "cookie", "proxy-authorization");
    private AiModelConfigValidator() {}

    public static URI endpoint(AiModelProperties p) {
        try {
            String address = StringUtils.hasText(p.getEndpointUrl()) ? p.getEndpointUrl().trim()
                    : p.getBaseUrl().replaceAll("/+$", "") + p.getChatPath();
            URI uri = URI.create(address);
            boolean loopback = Set.of("localhost", "127.0.0.1", "[::1]", "::1").contains(
                    uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT));
            if (address.length() > 2000 || uri.getHost() == null || uri.getUserInfo() != null
                    || uri.getFragment() != null || !("https".equalsIgnoreCase(uri.getScheme())
                    || ("http".equalsIgnoreCase(uri.getScheme()) && loopback))) {
                throw new IllegalArgumentException();
            }
            // URL 可携带 api-version 等路由参数；认证凭据只能放在后端请求头中。
            if (uri.getRawQuery() != null) for (String part : uri.getRawQuery().split("&")) {
                String key = URLDecoder.decode(part.split("=", 2)[0], StandardCharsets.UTF_8).toLowerCase(Locale.ROOT);
                if (key.matches(".*(key|token|secret|password|authorization|signature|credential).*"))
                    throw new IllegalArgumentException();
            }
            return uri;
        } catch (Exception ignored) {
            throw invalid("接口地址须为 HTTPS 或本机 HTTP，不能在 URL 中填写密钥");
        }
    }

    public static void validate(AiModelProperties p, boolean requireKey) {
        if (!StringUtils.hasText(p.getName()) || p.getName().length() > 100 || controls(p.getName()))
            throw invalid("模型名称不能为空且不能超过 100 字符");
        if (!StringUtils.hasText(p.getEndpointUrl()) && (p.getBaseUrl() == null || p.getChatPath() == null
                || !p.getChatPath().startsWith("/") || p.getChatPath().startsWith("//")
                || p.getChatPath().contains("..") || p.getChatPath().contains("?") || p.getChatPath().contains("#")))
            throw invalid("请求路径须以 / 开头；含查询参数时请填写完整请求地址");
        endpoint(p);
        if (!Set.of("BEARER", "API_KEY", "NONE").contains(p.getAuthMode() == null ? "" : p.getAuthMode()))
            throw invalid("不支持的认证方式");
        if ("API_KEY".equals(p.getAuthMode())) validateHeader(p.getAuthHeaderName(), false);
        if (p.getApiKey() == null || p.getApiKey().length() > 8192 || controls(p.getApiKey())
                || !p.getApiKey().equals(p.getApiKey().trim())) throw invalid("API Key 格式不正确");
        if (requireKey && !"NONE".equals(p.getAuthMode()) && !StringUtils.hasText(p.getApiKey()))
            throw invalid("请配置 API Key 后再启用或测试");
        if ("BEARER".equals(p.getAuthMode()) && p.getApiKey().regionMatches(true, 0, "Bearer ", 0, 7))
            throw invalid("API Key 请只填写密钥，不包含 Bearer 前缀");
        if (p.getTimeoutSeconds() < 1 || p.getTimeoutSeconds() > 120 || p.getConnectTimeoutSeconds() < 1
                || p.getConnectTimeoutSeconds() > 30) throw invalid("总超时范围 1–120 秒，连接超时范围 1–30 秒");
        if (p.getMaxOutputTokens() < 64 || p.getMaxOutputTokens() > 32768 || p.getHistoryMessages() < 0
                || p.getHistoryMessages() > 50) throw invalid("输出上限范围 64–32768，历史消息范围 0–50");
        if (!Set.of("max_tokens", "max_completion_tokens").contains(p.getMaxTokensParameter() == null ? "" : p.getMaxTokensParameter()))
            throw invalid("不支持的输出上限参数名");
        if (p.getTemperature() != null && (!Double.isFinite(p.getTemperature()) || p.getTemperature() < 0
                || p.getTemperature() > 2)) throw invalid("温度范围 0–2，可留空不发送");
        if (p.getCustomHeaders() == null || p.getCustomHeaders().size() > 20) throw invalid("附加请求头最多 20 项");
        var names = new java.util.HashSet<String>();
        p.getCustomHeaders().forEach((key, value) -> {
            validateHeader(key, true);
            if (!names.add(key.toLowerCase(Locale.ROOT)) || ("API_KEY".equals(p.getAuthMode()) && key.equalsIgnoreCase(p.getAuthHeaderName())))
                throw invalid("请求头不能重复或覆盖认证头");
            if (value == null || value.length() > 8192 || controls(value)) throw invalid("请求头值无效");
        });
        if (p.getExtraBody() != null) {
            if (p.getExtraBody().toString().length() > 16000) throw invalid("扩展请求参数过长");
            p.getExtraBody().fieldNames().forEachRemaining(key -> {
                if (RESERVED.contains(key)) throw invalid("扩展参数不能覆盖核心请求字段或启用工具调用");
            });
            rejectSecrets(p.getExtraBody());
        }
    }

    private static void rejectSecrets(com.fasterxml.jackson.databind.JsonNode node) {
        if (node.isObject()) node.fields().forEachRemaining(field -> {
            if (field.getKey().toLowerCase(Locale.ROOT).matches("(?:api.?key|access.?token|auth.?token|secret|password|authorization|credentials?)"))
                throw invalid("扩展请求参数中不要填写凭据，请使用认证或附加请求头");
            rejectSecrets(field.getValue());
        });
        if (node.isArray()) node.forEach(AiModelConfigValidator::rejectSecrets);
    }

    private static void validateHeader(String name, boolean custom) {
        if (name == null || !name.matches("[A-Za-z0-9!#$%&'*+.^_`|~-]{1,80}")
                || BLOCKED_HEADERS.contains(name.toLowerCase(Locale.ROOT)))
            throw invalid(custom ? "附加请求头名称无效或覆盖了系统请求头" : "认证请求头名称无效");
    }
    private static boolean controls(String text) { return text.chars().anyMatch(c -> c < 32 || c == 127); }
    private static BusinessException invalid(String message) { return new BusinessException(ErrorCode.VALIDATION_FAILED, message); }
}
