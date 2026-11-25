package com.gameplay.customer_service;

import com.gameplay.auth.entity.Role;
import com.gameplay.auth.entity.User;
import com.gameplay.auth.event.SessionInvalidatedEvent;
import com.gameplay.auth.mapper.RoleMapper;
import com.gameplay.auth.security.JwtPrincipal;
import com.gameplay.auth.service.AccountAuthService;
import com.gameplay.common.exception.BusinessException;
import com.gameplay.common.exception.ErrorCode;
import com.gameplay.infrastructure.websocket.JwtHandshakeInterceptor;
import com.gameplay.infrastructure.websocket.WsSessionRegistry;
import com.gameplay.infrastructure.websocket.WsTicketService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WsTicketAndRegistryTest {

    @Test
    void ticketIsSingleUseAndRejectsQueryJwt() {
        WsTicketService ticketService = new WsTicketService();
        AccountAuthService authService = mock(AccountAuthService.class);
        RoleMapper roleMapper = mock(RoleMapper.class);
        Role userRole = new Role();
        userRole.setRoleCode("USER");
        when(authService.assertEnabledAndTokenVersion(10L, 2)).thenReturn(new User());
        when(roleMapper.selectRolesByUserId(10L)).thenReturn(List.of(userRole));

        JwtHandshakeInterceptor interceptor = new JwtHandshakeInterceptor(ticketService, authService, roleMapper);
        WebSocketHandler handler = mock(WebSocketHandler.class);

        // Legacy ?token=<JWT> must be rejected
        ServerHttpRequest jwtReq = mock(ServerHttpRequest.class);
        when(jwtReq.getURI()).thenReturn(URI.create("ws://localhost:8080/ws/cs?token=legacy.jwt.token"));
        ServerHttpResponse jwtResp = mock(ServerHttpResponse.class);
        assertThat(interceptor.beforeHandshake(jwtReq, jwtResp, handler, new HashMap<>())).isFalse();
        verify(jwtResp).setStatusCode(HttpStatus.UNAUTHORIZED);

        // Valid ticket works exactly once and populates live roles
        String ticket = ticketService.issue(new JwtPrincipal(10L, "u10", 2, List.of("OLD_ROLE")));
        ServerHttpRequest ticketReq = mock(ServerHttpRequest.class);
        when(ticketReq.getURI()).thenReturn(URI.create("ws://localhost:8080/ws/cs?ticket=" + ticket));
        ServerHttpResponse ticketResp = mock(ServerHttpResponse.class);
        Map<String, Object> attrs = new HashMap<>();
        assertThat(interceptor.beforeHandshake(ticketReq, ticketResp, handler, attrs)).isTrue();
        assertThat(attrs.get("roles")).isEqualTo(List.of("USER"));

        // Replay of the same ticket must fail with 401
        ServerHttpResponse replayResp = mock(ServerHttpResponse.class);
        assertThat(interceptor.beforeHandshake(ticketReq, replayResp, handler, new HashMap<>())).isFalse();
        verify(replayResp).setStatusCode(HttpStatus.UNAUTHORIZED);

        // Revoked user fails handshake
        String revokedTicket = ticketService.issue(new JwtPrincipal(11L, "u11", 1, List.of("USER")));
        doThrow(new BusinessException(ErrorCode.AUTH_ACCOUNT_DISABLED))
                .when(authService).assertEnabledAndTokenVersion(11L, 1);
        ServerHttpRequest revokedReq = mock(ServerHttpRequest.class);
        when(revokedReq.getURI()).thenReturn(URI.create("ws://localhost:8080/ws/cs?ticket=" + revokedTicket));
        ServerHttpResponse revokedResp = mock(ServerHttpResponse.class);
        assertThat(interceptor.beforeHandshake(revokedReq, revokedResp, handler, new HashMap<>())).isFalse();
        verify(revokedResp).setStatusCode(HttpStatus.UNAUTHORIZED);

        // SessionInvalidatedEvent revokes unconsumed tickets
        String t3 = ticketService.issue(new JwtPrincipal(12L, "u12", 1, List.of("USER")));
        ticketService.onSessionInvalidated(new SessionInvalidatedEvent(12L));
        assertThat(ticketService.consume(t3)).isEmpty();
    }

    @Test
    void registryHandlesFailuresAndDisconnectsWithoutLeaking() throws Exception {
        WsSessionRegistry registry = new WsSessionRegistry();
        WebSocketSession broken = mock(WebSocketSession.class);
        when(broken.getId()).thenReturn("s-broken");
        when(broken.isOpen()).thenReturn(true);
        doThrow(new IOException("broken pipe")).when(broken).sendMessage(any());

        WebSocketSession healthy = mock(WebSocketSession.class);
        when(healthy.getId()).thenReturn("s-healthy");
        when(healthy.isOpen()).thenReturn(true);

        registry.register(100L, broken);
        registry.register(100L, healthy);
        assertThat(registry.onlineCount()).isEqualTo(1);

        // Sending removes broken session without aborting healthy session
        registry.sendToUser(100L, "{\"type\":\"PING\"}");
        verify(healthy).sendMessage(any(TextMessage.class));
        assertThat(registry.onlineCount()).isEqualTo(1);

        // Unregister cleans map entry completely when last session leaves
        registry.unregister(100L, healthy);
        assertThat(registry.onlineCount()).isZero();

        // Register & disconnectUser closes session and clears map
        WebSocketSession s2 = mock(WebSocketSession.class);
        when(s2.getId()).thenReturn("s2");
        when(s2.isOpen()).thenReturn(true);
        registry.register(200L, s2);
        registry.disconnectUser(200L, CloseStatus.POLICY_VIOLATION);
        verify(s2).close(CloseStatus.POLICY_VIOLATION);
        assertThat(registry.onlineCount()).isZero();

        // Unregistering missing session does not fail or remove unrelated session
        WebSocketSession s3 = mock(WebSocketSession.class);
        when(s3.getId()).thenReturn("s3");
        WebSocketSession old = mock(WebSocketSession.class);
        when(old.getId()).thenReturn("s-old");
        registry.register(300L, s3);
        registry.unregister(300L, old);
        assertThat(registry.onlineCount()).isEqualTo(1);
        verify(s3, never()).close(any());
    }
}
