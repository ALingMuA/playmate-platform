package com.gameplay.customer_service.service;

import com.gameplay.ai.task.dto.AiTaskView;

import com.gameplay.customer_service.dto.ConversationView;
import com.gameplay.customer_service.dto.MessageView;

/**
 * 客服消息实时推送接口（概要设计 4.7：WebSocket 实现，未连接时由 REST 补拉兜底）。
 *
 * <p>由 infrastructure/websocket 模块提供实现；此处仅定义契约，避免业务模块反向依赖 WebSocket。</p>
 */
public interface CsMessageNotifier {

    /** 向会话相关方推送新消息（发起用户、当前客服账号所属用户） */
    void notifyMessage(MessageView message, Long initiatorUserId, Long currentCsAccountUserId);

    /** 向会话相关方推送会话状态变更 */
    void notifyConversationChanged(ConversationView conversation, Long initiatorUserId, Long currentCsAccountUserId);

    void notifyAiStatus(AiTaskView task, Long initiatorUserId);
}
