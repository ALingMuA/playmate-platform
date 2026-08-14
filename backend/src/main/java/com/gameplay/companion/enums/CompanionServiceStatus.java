package com.gameplay.companion.enums;

/**
 * 陪玩师接单状态（companion_profile.service_status，FR-P06）。
 */
public enum CompanionServiceStatus {
    /** 可接单 */
    AVAILABLE,
    /** 忙碌 */
    BUSY,
    /** 休息 */
    RESTING,
    /** 被暂停（仅管理员可设置） */
    SUSPENDED
}
