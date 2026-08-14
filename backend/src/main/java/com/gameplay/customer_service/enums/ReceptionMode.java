package com.gameplay.customer_service.enums;

/**
 * 会话接待模式（详细设计 DDL：AI、HUMAN、ADMIN）。
 */
public enum ReceptionMode {
    /** AI 客服接待 */
    AI,
    /** 人工客服接待 */
    HUMAN,
    /** 管理员接待（转交管理员后） */
    ADMIN
}
