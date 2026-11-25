package com.gameplay.infrastructure.websocket;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gameplay.auth.entity.Role;
import com.gameplay.auth.mapper.RoleMapper;
import com.gameplay.auth.security.JwtPrincipal;
import com.gameplay.auth.service.AccountAuthService;
import com.gameplay.common.enums.ConversationStatus;
import com.gameplay.common.exception.BusinessException;
import com.gameplay.common.exception.ErrorCode;
import com.gameplay.customer_service.domain.CustomerConversation;
import com.gameplay.customer_service.dto.MessageSendRequest;
import com.gameplay.customer_service.service.CustomerConversationService;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.List;
import java.util.Map;

/**
 * 客服 WebSocket 处理器（详细设计 5.2）。
 *
 * <p>握手时已完成一次性票据鉴权（{@link JwtHandshakeInterceptor}），
 * 每次收到消息时复核账号状态、令牌版本与实时启用角色，并按会话上下文路由：
 * 发起人走 AI/留言链路，当前客服走人工回复，管理员对升级会话走管理员回复。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CustomerServiceWsHandler extends TextWebSocketHandler {

    /** 会话连接属性键 */
    public static final String ATTR_PRINCIPAL = "principal";
    public static final String ATTR_USER_ID = "userId";
    public static final String ATTR_ROLES = "roles";

    private final ObjectMapper objectMapper;
    private final WsSessionRegistry sessionRegistry;
    private final CustomerConversationService conversationService;
    private final AccountAuthService accountAuthService;
    private final RoleMapper roleMapper;
    private final Validator validator;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        Long userId = userId(session);
        if (userId == null) {
            closeQuietly(session, CloseStatus.POLICY_VIOLATION);
            return;
        }
        sessionRegistry.register(userId, session);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        JwtPrincipal principal = principal(session);
        Long userId = principal != null ? principal.userId() : userId(session);
        if (principal == null || principal.userId() == null || principal.tokenVersion() == null) {
            closeInvalidSession(userId, session, "会话认证信息失效，请重新连接");
            return;
        }
        List<String> liveRoles;
        try {
            accountAuthService.assertEnabledAndTokenVersion(principal.userId(), principal.tokenVersion());
            liveRoles = roleMapper.selectRolesByUserId(principal.userId()).stream()
                    .map(Role::getRoleCode)
                    .toList();
            session.getAttributes().put(ATTR_ROLES, liveRoles);
        } catch (BusinessException ex) {
            closeInvalidSession(userId, session, ex.getMessage());
            return;
        }

        JsonNode root;
        try {
            root = objectMapper.readTree(message.getPayload());
        } catch (Exception ex) {
            sendError(userId, session, "消息格式不正确", 0L, "");
            return;
        }
        if (root == null || !root.isObject()) {
            sendError(userId, session, "消息格式不正确", 0L, "");
            return;
        }
        String type = root.path("type").asText();
        if (!WsMessageType.MESSAGE_SEND.name().equals(type)) {
            sendError(userId, session, "不支持的消息类型", 0L, "");
            return;
        }
        Long conversationId = root.path("conversationId").asLong();
        MessageSendRequest request = buildRequest(root);
        try {
            var violations = validator.validate(request);
            if (conversationId <= 0 || !violations.isEmpty()) {
                throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                        violations.isEmpty() ? "会话ID不合法" : violations.iterator().next().getMessage());
            }
            CustomerConversation conversation = conversationService.findById(conversationId);
            if (conversation == null) {
                throw new BusinessException(ErrorCode.CS_CONVERSATION_NOT_FOUND);
            }
            // 按会话角色上下文路由，避免客服/管理员自身发起求助时被误判为人工回复
            if (conversation.getInitiatorUserId().equals(userId)) {
                conversationService.aiRespond(userId, conversationId, request);
            } else if (ConversationStatus.ESCALATED_ADMIN.name().equals(conversation.getConversationStatus())
                    && liveRoles.contains("ADMIN")) {
                conversationService.adminReply(userId, conversationId, request);
            } else if (liveRoles.contains("CUSTOMER_SERVICE")) {
                conversationService.reply(userId, conversationId, request);
            } else if (liveRoles.contains("ADMIN")) {
                conversationService.adminReply(userId, conversationId, request);
            } else {
                throw new BusinessException(ErrorCode.PERMISSION_DATA_SCOPE_DENIED);
            }
        } catch (BusinessException ex) {
            sendError(userId, session, ex.getMessage(), conversationId, request.getClientMsgId());
        } catch (Exception ex) {
            log.warn("客服消息处理失败，类型: {}", ex.getClass().getSimpleName());
            sendError(userId, session, "消息暂未送达，请重试", conversationId, request.getClientMsgId());
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        Long userId = userId(session);
        log.warn("WebSocket 传输异常: userId={}, session={}, err={}",
                userId, session.getId(), exception == null ? "" : exception.getClass().getSimpleName());
        if (userId != null) {
            sessionRegistry.unregister(userId, session);
        }
        closeQuietly(session, CloseStatus.SERVER_ERROR);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        Long userId = userId(session);
        if (userId != null) {
            sessionRegistry.unregister(userId, session);
        }
    }

    private void closeInvalidSession(Long userId, WebSocketSession session, String reason) {
        sendError(userId, session, reason, 0L, "");
        if (userId != null) {
            sessionRegistry.unregister(userId, session);
        }
        closeQuietly(session, CloseStatus.POLICY_VIOLATION);
    }

    private void closeQuietly(WebSocketSession session, CloseStatus status) {
        try {
            if (session.isOpen()) {
                session.close(status);
            }
        } catch (Exception ignored) {
        }
    }

    private MessageSendRequest buildRequest(JsonNode root) {
        MessageSendRequest request = new MessageSendRequest();
        request.setClientMsgId(root.path("clientMsgId").asText());
        request.setContent(root.path("content").asText());
        request.setRelatedOrderId(root.hasNonNull("relatedOrderId")
                ? root.path("relatedOrderId").asLong() : null);
        request.setRequestHuman(root.path("requestHuman").asBoolean(false) ? true : null);
        return request;
    }

    private JwtPrincipal principal(WebSocketSession session) {
        Object value = session.getAttributes().get(ATTR_PRINCIPAL);
        return value instanceof JwtPrincipal p ? p : null;
    }

    private Long userId(WebSocketSession session) {
        Object value = session.getAttributes().get(ATTR_USER_ID);
        if (value instanceof Long id) {
            return id;
        }
        JwtPrincipal principal = principal(session);
        return principal == null ? null : principal.userId();
    }

    private void sendError(Long userId, WebSocketSession session, String text,
                           Long conversationId, String clientMsgId) {
        try {
            sessionRegistry.sendToSession(userId, session, new TextMessage(objectMapper.writeValueAsString(
                    Map.of("type", WsMessageType.ERROR.name(), "message", text,
                            "data", Map.of("message", text, "conversationId", conversationId,
                                    "clientMsgId", clientMsgId == null ? "" : clientMsgId)))));
        } catch (Exception ex) {
            log.warn("WebSocket 错误帧发送失败: {}", ex.getMessage());
        }
    }
}
