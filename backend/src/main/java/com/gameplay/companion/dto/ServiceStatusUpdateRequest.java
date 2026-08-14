package com.gameplay.companion.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 陪玩师接单状态设置（FR-P06）。
 */
@Data
public class ServiceStatusUpdateRequest {

    /** 接单状态：AVAILABLE、BUSY、RESTING */
    @NotBlank(message = "请选择接单状态")
    private String serviceStatus;
}
