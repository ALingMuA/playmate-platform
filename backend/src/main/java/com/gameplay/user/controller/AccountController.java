package com.gameplay.user.controller;

import com.gameplay.auth.dto.UserProfileView;
import com.gameplay.auth.security.JwtPrincipal;
import com.gameplay.common.api.ApiResponse;
import com.gameplay.common.exception.BusinessException;
import com.gameplay.common.exception.ErrorCode;
import com.gameplay.user.dto.UpdateProfileRequest;
import com.gameplay.user.service.AccountService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 账号资料接口（概要设计 8.2：认证与账号分组 `/api/accounts`）。
 *
 * <p>覆盖 FR-A05 个人资料维护与 FR-A06 账号安全（最近登录时间、主动注销会话）。
 * 账号注销申请与联系方式换绑因缺少表结构/验证流程支撑，暂不实现（见 README 迭代计划）。</p>
 */
@RestController
@RequestMapping("/api/accounts")
@RequiredArgsConstructor
public class AccountController {

    private final AccountService accountService;

    /** 当前用户个人资料（FR-A05、FR-A06 最近登录时间） */
    @GetMapping("/profile")
    public ApiResponse<UserProfileView> getProfile(Authentication authentication) {
        return ApiResponse.ok(accountService.getProfile(currentUserId(authentication)));
    }

    /** 更新个人资料（FR-A05）：头像、昵称、性别、简介，字段可选 */
    @PutMapping("/profile")
    public ApiResponse<UserProfileView> updateProfile(@Valid @RequestBody UpdateProfileRequest request,
                                                      Authentication authentication) {
        return ApiResponse.ok(accountService.updateProfile(currentUserId(authentication), request));
    }

    /** 主动注销全部会话（FR-A06）：递增令牌版本，所有旧令牌立即失效 */
    @PostMapping("/logout-all")
    public ApiResponse<Void> logoutAll(Authentication authentication) {
        accountService.logoutAll(currentUserId(authentication));
        return ApiResponse.ok();
    }

    private Long currentUserId(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof JwtPrincipal principal)) {
            throw new BusinessException(ErrorCode.AUTH_TOKEN_INVALID);
        }
        return principal.userId();
    }
}
