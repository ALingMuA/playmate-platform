package com.gameplay.common.enums;

import java.util.Map;
import java.util.Set;

/**
 * 客服会话状态机（与《详细设计说明书》6.2 节保持一致）。
 */
public enum ConversationStatus {

    /** AI 处理中 */
    AI_PROCESSING,
    /** 等待人工接待 */
    WAITING_HUMAN,
    /** 人工处理中 */
    HUMAN_PROCESSING,
    /** 已升级管理员 */
    ESCALATED_ADMIN,
    /** 已关闭 */
    CLOSED;

    private static final Map<ConversationStatus, Set<ConversationStatus>> TRANSITIONS = Map.of(
        AI_PROCESSING, Set.of(WAITING_HUMAN, CLOSED),
        WAITING_HUMAN, Set.of(HUMAN_PROCESSING, CLOSED),
        HUMAN_PROCESSING, Set.of(ESCALATED_ADMIN, CLOSED),
        ESCALATED_ADMIN, Set.of(CLOSED),
        CLOSED, Set.of()
    );

    public boolean canTransitionTo(ConversationStatus target) {
        return TRANSITIONS.get(this).contains(target);
    }
}
