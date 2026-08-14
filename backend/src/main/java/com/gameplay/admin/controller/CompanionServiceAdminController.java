package com.gameplay.admin.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.gameplay.auth.security.JwtPrincipal;
import com.gameplay.common.api.ApiResponse;
import com.gameplay.common.exception.BusinessException;
import com.gameplay.common.exception.ErrorCode;
import com.gameplay.companion.dto.ServiceView;
import com.gameplay.companion.service.CompanionServiceService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理后台服务项目审核接口（FR-M09）。
 */
@RestController
@RequestMapping("/api/admin/companion-services")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class CompanionServiceAdminController {

    private final CompanionServiceService serviceService;

    /** 服务分页（可按审核状态筛选） */
    @GetMapping
    public ApiResponse<Page<ServiceView>> list(@RequestParam(required = false) String auditStatus,
                                               @RequestParam(defaultValue = "1") long page,
                                               @RequestParam(defaultValue = "10") long size) {
        return ApiResponse.ok(serviceService.adminPage(auditStatus, page, size));
    }

    /** 审核通过/驳回 */
    @PostMapping("/{id}/audit")
    public ApiResponse<Void> audit(@PathVariable Long id,
                                   @Valid @RequestBody AuditRequest request,
                                   Authentication authentication) {
        serviceService.adminAudit(id, request.isApproved(), request.getReason(),
                currentAdminId(authentication));
        return ApiResponse.ok();
    }

    @Data
    public static class AuditRequest {
        /** 是否通过 */
        private boolean approved;

        /** 审核意见（驳回必填） */
        @NotBlank(message = "驳回时必须填写原因")
        @Size(max = 500, message = "审核意见长度不能超过500")
        private String reason;
    }

    private Long currentAdminId(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof JwtPrincipal principal)) {
            throw new BusinessException(ErrorCode.AUTH_TOKEN_INVALID);
        }
        return principal.userId();
    }
}
