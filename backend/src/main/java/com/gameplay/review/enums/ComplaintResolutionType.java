package com.gameplay.review.enums;

/**
 * 投诉处理类型（对应 complaint.resolution_type，FR-M18/M19）。
 */
public enum ComplaintResolutionType {

    /** 维持订单（不退款） */
    KEEP,
    /** 全额退款 */
    FULL_REFUND,
    /** 部分退款（refund_amount_cents 指定金额） */
    PARTIAL_REFUND
}
