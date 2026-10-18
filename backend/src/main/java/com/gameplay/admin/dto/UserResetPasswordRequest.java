package com.gameplay.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 管理端重置用户密码请求 DTO。
 */
@Data
public class UserResetPasswordRequest {

    /**
     * 新密码：8~32 位，必须同时包含字母和数字
     */
    @NotBlank(message = "新密码不能为空")
    @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d)\\S{8,32}$", message = "密码须为 8~32 位且同时包含字母和数字")
    private String newPassword;

    /**
     * 重置原因（记录于操作审计日志）
     */
    @Size(max = 200, message = "重置原因不能超过 200 个字符")
    private String reason;
}
