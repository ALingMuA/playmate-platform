package com.gameplay.companion.enums;

/**
 * 服务项目上下架状态（companion_service.service_status，FR-P08）。
 */
public enum ShelfStatus {
    /** 已上架，可被预约 */
    ON_SHELF,
    /** 已下架，不可产生新订单 */
    OFF_SHELF
}
