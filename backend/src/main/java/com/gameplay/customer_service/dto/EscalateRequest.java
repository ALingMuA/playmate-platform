package com.gameplay.customer_service.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 转交管理员请求（FR-C28：退款、投诉仲裁、封禁申诉等超权限事项）。
 */
@Data
public class EscalateRequest {

    @NotBlank(message = "转交原因不能为空")
    @Size(max = 500, message = "转交原因过长")
    private String reason;
}
