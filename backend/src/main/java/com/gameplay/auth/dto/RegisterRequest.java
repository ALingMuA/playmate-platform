package com.gameplay.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 注册请求（FR-A01）。
 * <p>username 必填；mobile、email 至少填写一项（业务层校验）。</p>
 */
@Data
public class RegisterRequest {

    /** 登录账号：4~32 位字母、数字或下划线 */
    @NotBlank(message = "用户名不能为空")
    @Pattern(regexp = "^[A-Za-z0-9_]{4,32}$", message = "用户名须为 4~32 位字母、数字或下划线")
    private String username;

    /** 密码：8~32 位，须同时包含字母和数字 */
    @NotBlank(message = "密码不能为空")
    @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d)\\S{8,32}$", message = "密码须为 8~32 位且同时包含字母和数字")
    private String password;

    /** 展示昵称 */
    @NotBlank(message = "昵称不能为空")
    @Size(max = 32, message = "昵称最长 32 个字符")
    private String nickname;

    /** 手机号（11 位，可空） */
    @Pattern(regexp = "^1\\d{10}$", message = "手机号格式不正确")
    private String mobile;

    /** 邮箱（可空） */
    @Email(message = "邮箱格式不正确")
    @Size(max = 100, message = "邮箱最长 100 个字符")
    private String email;
}
