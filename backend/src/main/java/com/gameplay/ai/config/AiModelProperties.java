package com.gameplay.ai.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "ai.model")
public class AiModelProperties {

    private boolean enabled = false;
    private String baseUrl = "https://token.sensenova.cn/v1";
    private String apiKey = "";
    private String name = "sensenova-6.7-flash-lite";
    private int timeoutSeconds = 25;
    private int maxOutputTokens = 800;
    private int historyMessages = 12;
}
