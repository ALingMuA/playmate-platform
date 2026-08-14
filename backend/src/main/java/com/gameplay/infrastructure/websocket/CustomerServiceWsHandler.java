package com.gameplay.infrastructure.websocket;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gameplay.auth.security.JwtPrincipal;
import com.gameplay.common.exception.BusinessException;
import com.gameplay.customer_service.dto.MessageSendRequest;
import com.gameplay.customer_service.service.CustomerConversationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.Map;

/**
 * 客服 WebSocket 处理器（详细设计 5.2）。
 *
 * <p>握手时已完成 JWT 鉴权（{@link JwtHandshakeInterceptor}），本类只信任 attributes 中的身份。
 * 收到 {@code MESSAGE_SEND} 帧后：用户身份走 AI 应答链路（保存消息 + AI 回复 + 可能转人工），
 * 客服身份走人工回复链路；结果通过 {@code MESSAGE_NEW/AI_RESPONSE/CONVERSATION_CHANGED} 推送。</p>
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
    private final CsWsNotifier wsNotifier;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        Long userId = userId(session);
        sessionRegistry.register(userId, session);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        Long userId = userId(session);
        JsonNode root = objectMapper.readTree(message.getPayload());
        String type = root.path("type").asText();
        if (!WsMessageType.MESSAGE_SEND.name().equals(type)) {
            sendError(session, "不支持的消息类型");
            return;
        }
        Long conversationId = root.path("conversationId").asLong();
        MessageSendRequest request = buildRequest(root);
        try {
            // 消息落库与推送（MESSAGE_NEW / CONVERSATION_CHANGED）由会话服务完成，
            // 通过 CsMessageNotifier 广播给会话双方；离线方由 REST 补拉兜底。
            if (isCs(session)) {
                conversationService.reply(userId, conversationId, request);
            } else {
                conversationService.aiRespond(userId, conversationId, request);
            }
        } catch (BusinessException ex) {
            sendError(session, ex.getMessage());
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        Long userId = userId(session);
        if (userId != null) {
            sessionRegistry.unregister(userId, session);
        }
    }

    private MessageSendRequest buildRequest(JsonNode root) {
        MessageSendRequest request = new MessageSendRequest();
        request.setClientMsgId(root.path("clientMsgId").asText());
        request.setContent(root.path("content").asText());
        request.setRelatedOrderId(root.path("relatedOrderId").asLong(0) > 0
                ? root.path("relatedOrderId").asLong() : null);
        request.setRequestHuman(root.path("requestHuman").asBoolean(false) ? true : null);
        return request;
    }

    /** 客服身份判断：握手拦截器已写入角色列表（详细设计 7.3） */
    @SuppressWarnings("unchecked")
    private boolean isCs(WebSocketSession session) {
        Object roles = session.getAttributes().get(ATTR_ROLES);
        if (roles instanceof java.util.List<?> list) {
            return list.contains("CUSTOMER_SERVICE");
        }
        return false;
    }

    private Long userId(WebSocketSession session) {
        Object value = session.getAttributes().get(ATTR_USER_ID);
        if (value instanceof Long id) {
            return id;
        }
        JwtPrincipal principal = (JwtPrincipal) session.getAttributes().get(ATTR_PRINCIPAL);
        return principal == null ? null : principal.userId();
    }

    private void sendError(WebSocketSession session, String text) {
        try {
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(
                    Map.of("type", WsMessageType.ERROR.name(), "message", text))));
        } catch (Exception ex) {
            log.warn("WebSocket 错误帧发送失败: {}", ex.getMessage());
        }
    }
}