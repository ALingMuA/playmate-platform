package com.gameplay.customer_service.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 重置客服密码请求（FR-C04：重置后原会话失效，下次登录须修改密码）。
 */
@Data
public class CsAccountResetPasswordRequest {

    /** 新临时密码 */
    @NotBlank(message = "临时密码不能为空")
    @Size(min = 8, max = 32, message = "密码长度须为8~32位")
    private String newPassword;
}
