package com.gameplay.review.enums;

/**
 * 投诉处理状态（对应 complaint.complaint_status，FR-U19/FR-M18）。
 */
public enum ComplaintStatus {

    /** 待处理 */
    PENDING,
    /** 处理中 */
    PROCESSING,
    /** 已处理 */
    RESOLVED
}
