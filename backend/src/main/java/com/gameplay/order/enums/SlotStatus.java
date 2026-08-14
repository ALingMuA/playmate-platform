package com.gameplay.order.enums;

/**
 * 订单档期占用状态（order_time_slot.slot_status，详细设计 7.1）。
 */
public enum SlotStatus {
    /** 临时占用（支付前） */
    TEMPORARY,
    /** 有效占用（支付后） */
    EFFECTIVE,
    /** 已释放（订单关闭/取消） */
    RELEASED
}
