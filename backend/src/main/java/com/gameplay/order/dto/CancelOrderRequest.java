package com.gameplay.order.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 用户取消订单（FR-U12，详细设计 4.1）。
 *
 * <p>取消原因选填：未支付订单直接关闭；已支付订单（待接单/待服务）全额退款并释放档期。</p>
 */
@Data
public class CancelOrderRequest {

    /** 取消原因（选填） */
    @Size(max = 500, message = "原因长度不能超过500")
    private String reason;
}
