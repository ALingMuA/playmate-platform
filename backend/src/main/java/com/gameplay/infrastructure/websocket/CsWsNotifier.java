package com.gameplay.infrastructure.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gameplay.customer_service.dto.ConversationView;
import com.gameplay.customer_service.dto.MessageView;
import com.gameplay.customer_service.service.CsMessageNotifier;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * WebSocket 消息推送实现（CsMessageNotifier）。
 *
 * <p>向会话发起用户与当前客服账号所属用户推送 MESSAGE_NEW / CONVERSATION_CHANGED；
 * 接收方离线时由 REST 消息补拉接口兜底。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CsWsNotifier implements CsMessageNotifier {

    private final WsSessionRegistry sessionRegistry;
    private final ObjectMapper objectMapper;

    @Override
    public void notifyMessage(MessageView message, Long initiatorUserId, Long currentCsAccountUserId) {
        String payload = payload(WsMessageType.MESSAGE_NEW, message);
        sessionRegistry.sendToUser(initiatorUserId, payload);
        sessionRegistry.sendToUser(currentCsAccountUserId, payload);
    }

    @Override
    public void notifyConversationChanged(ConversationView conversation,
                                          Long initiatorUserId, Long currentCsAccountUserId) {
        String payload = payload(WsMessageType.CONVERSATION_CHANGED, conversation);
        sessionRegistry.sendToUser(initiatorUserId, payload);
        sessionRegistry.sendToUser(currentCsAccountUserId, payload);
    }

    /** 推送 AI 应答结果 */
    public void notifyAiResponse(Object data, Long initiatorUserId, Long currentCsAccountUserId) {
        String payload = payload(WsMessageType.AI_RESPONSE, data);
        sessionRegistry.sendToUser(initiatorUserId, payload);
        sessionRegistry.sendToUser(currentCsAccountUserId, payload);
    }

    @SneakyThrows
    private String payload(WsMessageType type, Object data) {
        return objectMapper.writeValueAsString(Map.of("type", type.name(), "data", data));
    }
}
