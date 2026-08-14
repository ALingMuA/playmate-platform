package com.gameplay.customer_service.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.gameplay.auth.security.JwtPrincipal;
import com.gameplay.common.api.ApiResponse;
import com.gameplay.common.exception.BusinessException;
import com.gameplay.common.exception.ErrorCode;
import com.gameplay.customer_service.dto.CsAccountCreateRequest;
import com.gameplay.customer_service.dto.CsAccountResetPasswordRequest;
import com.gameplay.customer_service.dto.CsAccountStatusUpdateRequest;
import com.gameplay.customer_service.dto.CsAccountView;
import com.gameplay.customer_service.service.CsAccountService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理端客服账号接口（FR-C01~C05）。
 */
@RestController
@RequestMapping("/api/admin/cs-accounts")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminCsAccountController {

    private final CsAccountService csAccountService;

    /** 分页查询客服账号（FR-C02） */
    @GetMapping
    public ApiResponse<Page<CsAccountView>> list(
            @RequestParam(required = false) String csAccount,
            @RequestParam(required = false) String csName,
            @RequestParam(required = false) String accountStatus,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size) {
        return ApiResponse.ok(csAccountService.list(csAccount, csName, accountStatus, page, size));
    }

    /** 创建客服账号（FR-C01） */
    @PostMapping
    public ApiResponse<CsAccountView> create(@Valid @RequestBody CsAccountCreateRequest request,
                                             Authentication authentication) {
        return ApiResponse.ok(csAccountService.create(currentUserId(authentication), request));
    }

    /** 启用/禁用客服账号（FR-C03） */
    @PutMapping("/{id}/status")
    public ApiResponse<CsAccountView> setStatus(@PathVariable Long id,
                                                @Valid @RequestBody CsAccountStatusUpdateRequest request,
                                                Authentication authentication) {
        return ApiResponse.ok(csAccountService.setStatus(currentUserId(authentication), id, request));
    }

    /** 重置客服密码（FR-C04） */
    @PutMapping("/{id}/reset-password")
    public ApiResponse<CsAccountView> resetPassword(@PathVariable Long id,
                                                    @Valid @RequestBody CsAccountResetPasswordRequest request,
                                                    Authentication authentication) {
        return ApiResponse.ok(csAccountService.resetPassword(currentUserId(authentication), id, request));
    }

    private Long currentUserId(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof JwtPrincipal principal)) {
            throw new BusinessException(ErrorCode.AUTH_TOKEN_INVALID);
        }
        return principal.userId();
    }
}
