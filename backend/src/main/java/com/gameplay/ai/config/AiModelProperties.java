package com.gameplay.ai.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.LinkedHashMap;
import java.util.Map;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "ai.model")
public class AiModelProperties {

    private boolean enabled = false;
    private String baseUrl = "https://token.sensenova.cn/v1";
    private String apiKey = "";
    private String name = "sensenova-6.8-flash-lite";
    private int timeoutSeconds = 25;
    private int maxOutputTokens = 800;
    private int historyMessages = 12;
    /** 留空时使用 baseUrl + chatPath；填写后按完整地址请求。 */
    private String endpointUrl = "";
    private String chatPath = "/chat/completions";
    private String authMode = "BEARER";
    private String authHeaderName = "api-key";
    private int connectTimeoutSeconds = 5;
    private Double temperature = 0.2;
    private String maxTokensParameter = "max_tokens";
    private Map<String, String> customHeaders = new LinkedHashMap<>();
    private ObjectNode extraBody;

    /** 每次请求使用独立副本，防止保存配置影响正在进行的调用。 */
    public AiModelProperties copy() {
        AiModelProperties copy = new AiModelProperties();
        copy.enabled = enabled;
        copy.baseUrl = baseUrl;
        copy.apiKey = apiKey;
        copy.name = name;
        copy.timeoutSeconds = timeoutSeconds;
        copy.maxOutputTokens = maxOutputTokens;
        copy.historyMessages = historyMessages;
        copy.endpointUrl = endpointUrl;
        copy.chatPath = chatPath;
        copy.authMode = authMode;
        copy.authHeaderName = authHeaderName;
        copy.connectTimeoutSeconds = connectTimeoutSeconds;
        copy.temperature = temperature;
        copy.maxTokensParameter = maxTokensParameter;
        copy.customHeaders = new LinkedHashMap<>(customHeaders);
        copy.extraBody = extraBody == null ? null : extraBody.deepCopy();
        return copy;
    }
}
