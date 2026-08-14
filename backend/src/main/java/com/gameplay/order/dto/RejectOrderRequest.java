package com.gameplay.order.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 陪玩师拒绝订单（FR-P14）。
 */
@Data
public class RejectOrderRequest {

    /** 拒绝原因 */
    @NotBlank(message = "请填写拒绝原因")
    @Size(max = 500, message = "原因长度不能超过500")
    private String reason;
}
