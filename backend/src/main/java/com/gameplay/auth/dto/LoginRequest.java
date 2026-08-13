package com.gameplay.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 登录请求（FR-A02）。账号支持用户名、手机号或邮箱。
 */
@Data
public class LoginRequest {

    /** 账号：用户名 / 手机号 / 邮箱 */
    @NotBlank(message = "账号不能为空")
    @Size(max = 100, message = "账号最长 100 个字符")
    private String account;

    /** 密码 */
    @NotBlank(message = "密码不能为空")
    private String password;
}
