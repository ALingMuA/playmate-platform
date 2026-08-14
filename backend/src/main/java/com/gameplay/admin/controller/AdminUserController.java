package com.gameplay.admin.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.gameplay.admin.dto.UserAdminView;
import com.gameplay.admin.dto.UserStatusUpdateRequest;
import com.gameplay.admin.service.AdminUserService;
import com.gameplay.auth.security.JwtPrincipal;
import com.gameplay.common.api.ApiResponse;
import com.gameplay.common.exception.BusinessException;
import com.gameplay.common.exception.ErrorCode;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理端用户管理接口（FR-M03/M04）。
 */
@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminUserController {

    private final AdminUserService adminUserService;

    /** 用户分页查询（FR-M03） */
    @GetMapping
    public ApiResponse<Page<UserAdminView>> list(@RequestParam(required = false) String keyword,
                                                 @RequestParam(required = false) String accountStatus,
                                                 @RequestParam(defaultValue = "1") long page,
                                                 @RequestParam(defaultValue = "10") long size) {
        return ApiResponse.ok(adminUserService.page(keyword, accountStatus, page, size));
    }

    /** 启用/禁用用户（FR-M04） */
    @PutMapping("/{id}/status")
    public ApiResponse<UserAdminView> setStatus(@PathVariable Long id,
                                                @Valid @RequestBody UserStatusUpdateRequest request,
                                                Authentication authentication) {
        return ApiResponse.ok(adminUserService.setStatus(id, request));
    }

    private Long currentAdminId(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof JwtPrincipal principal)) {
            throw new BusinessException(ErrorCode.AUTH_TOKEN_INVALID);
        }
        return principal.userId();
    }
}
