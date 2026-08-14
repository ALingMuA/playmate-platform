package com.gameplay.order.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 陪玩师接单（FR-P13，详细设计 2.4）。
 */
@Data
public class AcceptOrderRequest {

    /** 确认接单标记，必须为 true */
    @AssertTrue(message = "请确认接单")
    private Boolean confirm;

    /** 接单备注 */
    @Size(max = 300, message = "备注长度不能超过300")
    private String companionRemark;
}
