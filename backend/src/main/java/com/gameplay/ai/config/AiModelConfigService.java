package com.gameplay.ai.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gameplay.ai.dto.AiSettingsRequest;
import com.gameplay.ai.dto.AiSettingsView;
import com.gameplay.audit.service.OperationLogService;
import com.gameplay.common.exception.BusinessException;
import com.gameplay.common.exception.ErrorCode;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.LinkedHashMap;
import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/** 数据库配置覆盖本机默认值；每次读取独立快照，保证跨进程更新及重启恢复。 */
@Service
public class AiModelConfigService {
    private final AiModelProperties defaults;
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final OperationLogService audit;
    private final SecretKeySpec encryptionKey;
    private final SecureRandom random = new SecureRandom();

    public AiModelConfigService(AiModelProperties defaults, JdbcTemplate jdbc, ObjectMapper mapper,
            OperationLogService audit, @Value("${ai.config.encryption-key:${jwt.secret}}") String secret) {
        this.defaults = defaults;
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.audit = audit;
        try {
            if (secret.getBytes(StandardCharsets.UTF_8).length < 32) throw new IllegalArgumentException();
            Mac derivation = Mac.getInstance("HmacSHA256");
            derivation.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            this.encryptionKey = new SecretKeySpec(derivation.doFinal("gameplay-ai-settings-v1".getBytes(StandardCharsets.UTF_8)), "AES");
        } catch (Exception ignored) {
            throw new IllegalStateException("AI 配置加密主密钥须至少为 32 字节");
        }
    }

    private record Stored(String encrypted, long version) {}
    private Stored stored() {
        try {
            var rows = jdbc.query("SELECT encrypted_config, version FROM ai_model_settings WHERE id = 1",
                    (rs, n) -> new Stored(rs.getString(1), rs.getLong(2)));
            return rows.isEmpty() ? new Stored(null, 0) : rows.get(0);
        } catch (DataAccessException ignored) {
            throw new BusinessException(ErrorCode.AI_SERVICE_UNAVAILABLE, "AI 配置存储不可用，请检查数据库并执行 AI 配置迁移脚本");
        }
    }
    private AiModelProperties properties(Stored stored) {
        return stored.encrypted() == null ? defaults.copy() : decrypt(stored.encrypted());
    }
    public AiModelProperties current() { return properties(stored()); }
    public AiSettingsView view() {
        Stored state = stored();
        return AiSettingsView.from(properties(state), state.encrypted() == null ? "LOCAL" : "DATABASE", state.version());
    }

    public AiModelProperties candidate(AiSettingsRequest request) {
        if (request.getCustomHeaders() == null) throw invalid("附加请求头格式不正确");
        Stored state = stored();
        checkVersion(request.getVersion(), state.version());
        AiModelProperties old = properties(state);
        AiModelProperties next = request.copy();
        if (request.isClearApiKey()) {
            if (StringUtils.hasText(request.getApiKey())) throw invalid("清除密钥时不能同时填写新密钥");
            next.setApiKey("");
        } else if (!StringUtils.hasText(request.getApiKey())) next.setApiKey(old.getApiKey());
        var headers = new LinkedHashMap<String, String>();
        request.getCustomHeaders().forEach((name, value) -> {
            if (!StringUtils.hasText(value)) {
                value = old.getCustomHeaders().entrySet().stream().filter(e -> e.getKey().equalsIgnoreCase(name))
                        .map(java.util.Map.Entry::getValue).findFirst().orElse(null);
                if (value == null) throw invalid("新增请求头必须填写值");
            }
            headers.put(name, value);
        });
        next.setCustomHeaders(headers);
        AiModelConfigValidator.validate(next, next.isEnabled());
        return next;
    }

    @Transactional
    public AiSettingsView save(AiSettingsRequest request, Long operatorId) {
        AiModelProperties next = candidate(request);
        String encrypted = encrypt(next);
        try {
            if (request.getVersion() == 0) {
                jdbc.update("INSERT INTO ai_model_settings(id, encrypted_config, version, updated_by) VALUES(1, ?, 1, ?)", encrypted, operatorId);
            } else {
                int updated = jdbc.update("UPDATE ai_model_settings SET encrypted_config = ?, version = version + 1, updated_by = ?, updated_at = CURRENT_TIMESTAMP WHERE id = 1 AND version = ?",
                        encrypted, operatorId, request.getVersion());
                if (updated != 1) throw conflict();
            }
        } catch (DataAccessException ignored) { throw conflict(); }
        // 审计只记动作和版本；不记录地址、请求体、请求头及任何凭据。
        audit.record(operatorId, "ADMIN", "AI_CONFIG_SAVE", "AI_CONFIG", 1L, "", "",
                "更新 AI 接口配置，版本 " + (request.getVersion() + 1));
        return AiSettingsView.from(next, "DATABASE", request.getVersion() + 1);
    }

    @Transactional
    public AiSettingsView reset(long version, Long operatorId) {
        Stored state = stored();
        checkVersion(version, state.version());
        if (version > 0) {
            int updated = jdbc.update("UPDATE ai_model_settings SET encrypted_config = NULL, version = version + 1, updated_by = ?, updated_at = CURRENT_TIMESTAMP WHERE id = 1 AND version = ?", operatorId, version);
            if (updated != 1) throw conflict();
        }
        audit.record(operatorId, "ADMIN", "AI_CONFIG_RESET", "AI_CONFIG", 1L, "", "", "恢复本机 AI 配置");
        return AiSettingsView.from(defaults.copy(), "LOCAL", version == 0 ? 0 : version + 1);
    }

    private String encrypt(AiModelProperties properties) {
        try {
            byte[] iv = new byte[12]; random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, encryptionKey, new GCMParameterSpec(128, iv));
            byte[] body = cipher.doFinal(mapper.writeValueAsBytes(properties));
            return "v1:" + Base64.getEncoder().encodeToString(ByteBuffer.allocate(iv.length + body.length).put(iv).put(body).array());
        } catch (Exception ignored) { throw unavailable(); }
    }
    private AiModelProperties decrypt(String text) {
        try {
            if (!text.startsWith("v1:")) throw new IllegalArgumentException();
            ByteBuffer buffer = ByteBuffer.wrap(Base64.getDecoder().decode(text.substring(3)));
            byte[] iv = new byte[12]; buffer.get(iv);
            byte[] body = new byte[buffer.remaining()]; buffer.get(body);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, encryptionKey, new GCMParameterSpec(128, iv));
            return mapper.readValue(cipher.doFinal(body), AiModelProperties.class);
        } catch (Exception ignored) { throw unavailable(); }
    }
    private void checkVersion(long expected, long actual) { if (expected != actual) throw conflict(); }
    private BusinessException conflict() { return new BusinessException(ErrorCode.VALIDATION_FAILED, "配置已被其他管理员更新，请重新加载后再试"); }
    private BusinessException invalid(String text) { return new BusinessException(ErrorCode.VALIDATION_FAILED, text); }
    private BusinessException unavailable() { return new BusinessException(ErrorCode.AI_SERVICE_UNAVAILABLE, "AI 配置加解密失败，请检查加密主密钥；可恢复本机配置后重新填写"); }
}
