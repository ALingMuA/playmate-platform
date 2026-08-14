package com.gameplay.review.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.gameplay.auth.security.JwtPrincipal;
import com.gameplay.common.api.ApiResponse;
import com.gameplay.common.exception.BusinessException;
import com.gameplay.common.exception.ErrorCode;
import com.gameplay.review.dto.ComplaintHandleRequest;
import com.gameplay.review.dto.ComplaintView;
import com.gameplay.review.service.ComplaintService;
import jakarta.validation.Valid;
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

import java.util.List;

/**
 * 管理后台投诉处理接口（FR-M18 投诉审核、FR-M19 虚拟资金调整）。
 */
@RestController
@RequestMapping("/api/admin/complaints")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminComplaintController {

    private final ComplaintService complaintService;

    /** 投诉分页查询（FR-M18） */
    @GetMapping
    public ApiResponse<Page<ComplaintView>> list(@RequestParam(required = false) String status,
                                                 @RequestParam(required = false) String complaintType,
                                                 @RequestParam(required = false) String keyword,
                                                 @RequestParam(defaultValue = "1") long page,
                                                 @RequestParam(defaultValue = "10") long size) {
        return ApiResponse.ok(complaintService.adminPage(status, complaintType, keyword, page, size));
    }

    /** 投诉详情（含证据） */
    @GetMapping("/{id}")
    public ApiResponse<ComplaintView> detail(@PathVariable Long id) {
        return ApiResponse.ok(complaintService.detail(0L, List.of("ADMIN"), id));
    }

    /** 投诉仲裁（FR-M18/M19）：KEEP / FULL_REFUND / PARTIAL_REFUND */
    @PostMapping("/{id}/handle")
    public ApiResponse<ComplaintView> handle(@PathVariable Long id,
                                             @Valid @RequestBody ComplaintHandleRequest request,
                                             Authentication authentication) {
        return ApiResponse.ok(complaintService.adminHandle(currentAdminId(authentication), id, request));
    }

    private Long currentAdminId(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof JwtPrincipal principal)) {
            throw new BusinessException(ErrorCode.AUTH_TOKEN_INVALID);
        }
        return principal.userId();
    }
}
