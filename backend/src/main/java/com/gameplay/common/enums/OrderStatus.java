package com.gameplay.common.enums;

/**
 * 订单状态机（与《详细设计说明书》3.1、6.2 节保持一致）。
 *
 * <p>{@code transition} 是唯一状态迁移入口，禁止 Controller、客服和 AI 直接更新
 * {@code play_order.order_status}。</p>
 */
public enum OrderStatus {

    /** 待支付（创建订单后，超时自动关闭） */
    PENDING_PAYMENT,
    /** 待接单（支付成功后进入，超时自动关闭并退款） */
    WAITING_ACCEPTANCE,
    /** 待服务（陪玩师接单后，等待履约开始） */
    WAITING_SERVICE,
    /** 服务中 */
    IN_SERVICE,
    /** 待确认（陪玩师结束服务，等待用户确认） */
    WAITING_CONFIRMATION,
    /** 售后中 */
    AFTER_SALES,
    /** 已完成 */
    COMPLETED,
    /** 已关闭 */
    CLOSED;

    public boolean canTransitionTo(OrderStatus target) {
        return switch (this) {
            case PENDING_PAYMENT -> target == WAITING_ACCEPTANCE || target == CLOSED;
            case WAITING_ACCEPTANCE -> target == WAITING_SERVICE || target == CLOSED;
            case WAITING_SERVICE -> target == IN_SERVICE || target == AFTER_SALES || target == CLOSED;
            case IN_SERVICE -> target == WAITING_CONFIRMATION || target == AFTER_SALES;
            case WAITING_CONFIRMATION -> target == COMPLETED || target == AFTER_SALES;
            case COMPLETED -> target == AFTER_SALES;
            case AFTER_SALES -> target == COMPLETED || target == CLOSED;
            case CLOSED -> false;
        };
    }
}
