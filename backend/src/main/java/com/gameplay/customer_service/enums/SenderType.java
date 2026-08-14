package com.gameplay.customer_service.enums;

/**
 * 客服消息发送者类型（详细设计 DDL：USER、AI、CS、SYSTEM、ADMIN）。
 */
public enum SenderType {
    /** 用户/陪玩师 */
    USER,
    /** AI 客服 */
    AI,
    /** 人工客服 */
    CS,
    /** 系统消息 */
    SYSTEM,
    /** 管理员 */
    ADMIN
}
