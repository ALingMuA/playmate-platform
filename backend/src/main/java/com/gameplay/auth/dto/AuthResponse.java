package com.gameplay.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 登录/改密成功响应：JWT 与用户信息。
 */
@Data
@AllArgsConstructor
public class AuthResponse {

    /** JWT 令牌 */
    private String token;

    /** 令牌类型 */
    private String tokenType;

    /** 有效期（秒） */
    private long expiresInSeconds;

    /** 当前用户信息 */
    private UserProfileView user;
}
