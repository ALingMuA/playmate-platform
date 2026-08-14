package com.gameplay.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 用户启用/禁用请求（FR-M04：禁用必须记录原因）。
 */
@Data
public class UserStatusUpdateRequest {

    /** 目标状态：ENABLED、DISABLED */
    @NotBlank(message = "目标状态不能为空")
    private String accountStatus;

    /** 禁用原因（禁用时必填） */
    @Size(max = 500, message = "禁用原因过长")
    private String reason;
}
