package com.gameplay.auth.controller;

import com.gameplay.auth.dto.AuthResponse;
import com.gameplay.auth.dto.ChangePasswordRequest;
import com.gameplay.auth.dto.LoginRequest;
import com.gameplay.auth.dto.RegisterRequest;
import com.gameplay.auth.dto.UserProfileView;
import com.gameplay.auth.security.JwtPrincipal;
import com.gameplay.auth.service.AuthService;
import com.gameplay.common.api.ApiResponse;
import com.gameplay.common.exception.BusinessException;
import com.gameplay.common.exception.ErrorCode;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证与账号接口（概要设计 8.2：认证与账号分组 `/api/auth`）。
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /** 用户注册（FR-A01），匿名 */
    @PostMapping("/register")
    public ApiResponse<UserProfileView> register(@Valid @RequestBody RegisterRequest request) {
        return ApiResponse.ok(authService.register(request));
    }

    /** 用户登录（FR-A02），匿名 */
    @PostMapping("/login")
    public ApiResponse<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.ok(authService.login(request));
    }

    /**
     * 退出登录（FR-A02）。
     * <p>JWT 无状态，服务端不保存会话，退出由客户端丢弃令牌实现；本接口作为统一出口保留。</p>
     */
    @PostMapping("/logout")
    public ApiResponse<Void> logout() {
        return ApiResponse.ok();
    }

    /** 修改密码（FR-A04，登录后）：成功后旧令牌全部失效，返回新令牌 */
    @PostMapping("/change-password")
    public ApiResponse<AuthResponse> changePassword(@Valid @RequestBody ChangePasswordRequest request,
                                                    Authentication authentication) {
        return ApiResponse.ok(authService.changePassword(currentUserId(authentication), request));
    }

    /** 当前登录用户信息（FR-A05） */
    @GetMapping("/me")
    public ApiResponse<UserProfileView> me(Authentication authentication) {
        return ApiResponse.ok(authService.getCurrentUser(currentUserId(authentication)));
    }

    private Long currentUserId(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof JwtPrincipal principal)) {
            throw new BusinessException(ErrorCode.AUTH_TOKEN_INVALID);
        }
        return principal.userId();
    }
}
