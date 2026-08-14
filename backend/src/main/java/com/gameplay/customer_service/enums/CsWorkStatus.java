package com.gameplay.customer_service.enums;

/**
 * 客服工作状态（FR-C06：在线/忙碌/离线）。
 * <p>仅 ONLINE 且未达接待上限的客服可接收新会话。</p>
 */
public enum CsWorkStatus {
    /** 在线 */
    ONLINE,
    /** 忙碌 */
    BUSY,
    /** 离线 */
    OFFLINE
}
