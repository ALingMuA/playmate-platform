package com.gameplay.ai.dto;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.gameplay.ai.config.AiModelProperties;
import java.util.List;

public record AiSettingsView(boolean enabled, String baseUrl, String endpointUrl, String chatPath,
        String name, String authMode, String authHeaderName, boolean apiKeyConfigured,
        int timeoutSeconds, int connectTimeoutSeconds, int maxOutputTokens, String maxTokensParameter,
        int historyMessages, Double temperature, List<String> customHeaderNames, ObjectNode extraBody,
        String source, long version) {
    public static AiSettingsView from(AiModelProperties p, String source, long version) {
        return new AiSettingsView(p.isEnabled(), p.getBaseUrl(), p.getEndpointUrl(), p.getChatPath(), p.getName(),
                p.getAuthMode(), p.getAuthHeaderName(), p.getApiKey() != null && !p.getApiKey().isBlank(),
                p.getTimeoutSeconds(), p.getConnectTimeoutSeconds(), p.getMaxOutputTokens(), p.getMaxTokensParameter(),
                p.getHistoryMessages(), p.getTemperature(), List.copyOf(p.getCustomHeaders().keySet()),
                p.getExtraBody() == null ? null : p.getExtraBody().deepCopy(), source, version);
    }
}
