package com.gameplay.review.enums;

/**
 * 评价展示状态（对应 review.display_status，FR-U16/FR-M08）。
 * <p>被屏蔽（HIDDEN）的评价保留原始记录供管理员审计，不再公开展示，也不参与评分计算。</p>
 */
public enum ReviewDisplayStatus {

    /** 公开可见 */
    VISIBLE,
    /** 已屏蔽（管理员） */
    HIDDEN
}
