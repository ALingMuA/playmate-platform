package com.gameplay.auth.security;

import java.util.List;

/**
 * JWT 解析结果（当前登录身份）。
 * <p>来源为 JWT 声明：账号ID、用户名、角色集合、令牌版本（详细设计 7.3）。</p>
 */
public record JwtPrincipal(
        Long userId,
        String username,
        Integer tokenVersion,
        List<String> roles) {
}
