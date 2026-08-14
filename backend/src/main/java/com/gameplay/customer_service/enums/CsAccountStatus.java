package com.gameplay.customer_service.enums;

/**
 * 客服账号状态（FR-C03：启用/禁用）。
 */
public enum CsAccountStatus {
    /** 启用 */
    ENABLED,
    /** 禁用（必须记录原因，立即禁止登录并终止会话） */
    DISABLED
}
