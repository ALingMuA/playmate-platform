package com.gameplay.infrastructure.websocket;

import com.gameplay.auth.security.JwtPrincipal;
import com.gameplay.auth.security.JwtTokenService;
import com.gameplay.auth.service.AccountAuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.Map;

/**
 * WebSocket 握手 JWT 认证（详细设计 7.3）。
 *
 * <p>从查询参数 token 提取 JWT，复用与 REST 一致的 {@link JwtTokenService} 校验签名、
 * 有效期、账户启用状态与令牌版本；认证结果写入 attributes，Handler 不信任客户端自报身份。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtHandshakeInterceptor implements HandshakeInterceptor {

    private final JwtTokenService jwtTokenService;
    private final AccountAuthService accountAuthService;

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {
        URI uri = request.getURI();
        String token = UriComponentsBuilder.fromUri(uri).build().getQueryParams().getFirst("token");
        if (!StringUtils.hasText(token)) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }
        try {
            JwtPrincipal principal = jwtTokenService.parse(token);
            accountAuthService.assertEnabledAndTokenVersion(principal.userId(), principal.tokenVersion());
            attributes.put("principal", principal);
            attributes.put("userId", principal.userId());
            attributes.put("roles", principal.roles());
            return true;
        } catch (Exception ex) {
            log.warn("WebSocket 握手鉴权失败: {}", ex.getMessage());
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
        // 认证结果已写入 attributes，无需额外处理
    }
}
