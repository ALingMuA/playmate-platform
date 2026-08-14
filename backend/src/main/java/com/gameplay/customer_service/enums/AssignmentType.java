package com.gameplay.customer_service.enums;

/**
 * 会话分配轨迹类型（详细设计 DDL）。
 */
public enum AssignmentType {
    /** AI 转人工 */
    AI_TRANSFER,
    /** 客服主动领取 */
    CLAIM,
    /** 管理员分配 */
    ASSIGN,
    /** 客服转交 */
    TRANSFER,
    /** 升级管理员 */
    ESCALATE_ADMIN
}
