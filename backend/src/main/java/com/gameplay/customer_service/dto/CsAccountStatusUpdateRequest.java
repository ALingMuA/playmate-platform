package com.gameplay.customer_service.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 启用/禁用客服账号请求（FR-C03：禁用必须填写原因）。
 */
@Data
public class CsAccountStatusUpdateRequest {

    /** 目标状态：ENABLED、DISABLED */
    @NotBlank(message = "目标状态不能为空")
    private String accountStatus;

    /** 禁用原因（禁用时必填） */
    @Size(max = 500, message = "禁用原因过长")
    private String reason;
}
