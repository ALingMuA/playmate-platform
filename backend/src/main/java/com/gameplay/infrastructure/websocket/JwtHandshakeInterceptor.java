package com.gameplay.infrastructure.websocket;

import com.gameplay.auth.entity.Role;
import com.gameplay.auth.mapper.RoleMapper;
import com.gameplay.auth.security.JwtPrincipal;
import com.gameplay.auth.service.AccountAuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.List;
import java.util.Map;

/**
 * WebSocket 握手一次性票据认证（详细设计 7.3）。
 *
 * <p>从查询参数 {@code ticket} 提取单次消费票据（不再接受 URL 中的长效 JWT），
 * 原子消费后复核数据库账号状态、令牌版本与实时启用角色，写入 attributes。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtHandshakeInterceptor implements HandshakeInterceptor {

    private final WsTicketService wsTicketService;
    private final AccountAuthService accountAuthService;
    private final RoleMapper roleMapper;

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {
        URI uri = request.getURI();
        String ticket = UriComponentsBuilder.fromUri(uri).build().getQueryParams().getFirst("ticket");
        WsTicketService.TicketEntry entry = wsTicketService.consume(ticket).orElse(null);
        if (entry == null) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }
        try {
            accountAuthService.assertEnabledAndTokenVersion(entry.userId(), entry.tokenVersion());
            List<String> roles = roleMapper.selectRolesByUserId(entry.userId()).stream()
                    .map(Role::getRoleCode)
                    .toList();
            JwtPrincipal principal = new JwtPrincipal(
                    entry.userId(), entry.username(), entry.tokenVersion(), roles);
            attributes.put(CustomerServiceWsHandler.ATTR_PRINCIPAL, principal);
            attributes.put(CustomerServiceWsHandler.ATTR_USER_ID, principal.userId());
            attributes.put(CustomerServiceWsHandler.ATTR_ROLES, roles);
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
