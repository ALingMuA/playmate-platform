package com.gameplay.infrastructure.websocket;

import jakarta.servlet.ServletContext;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.standard.ServletServerContainerFactoryBean;

import java.util.Arrays;

/**
 * WebSocket 配置（概要设计 4.7：客服会话端点 /ws/cs）。
 */
@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketConfigurer {

    private static final String SERVER_CONTAINER_ATTR = "jakarta.websocket.server.ServerContainer";

    private final CustomerServiceWsHandler customerServiceWsHandler;
    private final JwtHandshakeInterceptor jwtHandshakeInterceptor;

    @Value("${app.cors.allowed-origins:http://localhost:5173}")
    private String allowedOrigins;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        String[] origins = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .toArray(String[]::new);
        if (origins.length == 0) {
            origins = new String[]{"http://localhost:5173"};
        }
        registry.addHandler(customerServiceWsHandler, "/ws/cs")
                .addInterceptors(jwtHandshakeInterceptor)
                .setAllowedOrigins(origins);
    }

    /** 增大文本帧上限至 64KB 并设置异步发送与空闲超时；MockServletContext 下自动跳过。 */
    @Bean
    public ServletServerContainerFactoryBean createWebSocketContainer() {
        ServletServerContainerFactoryBean container = new ServletServerContainerFactoryBean() {
            private ServletContext context;

            @Override
            public void setServletContext(ServletContext servletContext) {
                this.context = servletContext;
                super.setServletContext(servletContext);
            }

            @Override
            public void afterPropertiesSet() {
                if (context == null || context.getAttribute(SERVER_CONTAINER_ATTR) == null) {
                    return;
                }
                super.afterPropertiesSet();
            }
        };
        container.setMaxTextMessageBufferSize(65536);
        container.setMaxBinaryMessageBufferSize(65536);
        container.setAsyncSendTimeout(10_000L);
        container.setMaxSessionIdleTimeout(300_000L);
        return container;
    }
}
