package com.gameplay.customer_service.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 客服工作状态设置请求（FR-C06：ONLINE、BUSY、OFFLINE）。
 */
@Data
public class CsWorkStatusUpdateRequest {

    @NotBlank(message = "工作状态不能为空")
    private String workStatus;
}
