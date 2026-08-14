package com.gameplay.review.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.gameplay.auth.security.JwtPrincipal;
import com.gameplay.common.api.ApiResponse;
import com.gameplay.common.exception.BusinessException;
import com.gameplay.common.exception.ErrorCode;
import com.gameplay.review.dto.ReviewView;
import com.gameplay.review.service.ReviewService;
import lombok.Data;
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
 * 管理后台评价管理接口（FR-M08：查询、屏蔽/恢复）。
 */
@RestController
@RequestMapping("/api/admin/reviews")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminReviewController {

    private final ReviewService reviewService;

    /** 评价分页查询（FR-M08） */
    @GetMapping
    public ApiResponse<Page<ReviewView>> list(@RequestParam(required = false) String keyword,
                                              @RequestParam(required = false) Integer score,
                                              @RequestParam(required = false) String displayStatus,
                                              @RequestParam(defaultValue = "1") long page,
                                              @RequestParam(defaultValue = "10") long size) {
        return ApiResponse.ok(reviewService.adminPage(keyword, score, displayStatus, page, size));
    }

    /** 屏蔽/恢复评价（FR-M08）：displayStatus = VISIBLE | HIDDEN */
    @PutMapping("/{id}/display-status")
    public ApiResponse<ReviewView> setDisplayStatus(@PathVariable Long id,
                                                    @RequestBody DisplayStatusRequest request,
                                                    Authentication authentication) {
        return ApiResponse.ok(reviewService.adminSetDisplayStatus(id, request.getDisplayStatus(),
                currentAdminId(authentication)));
    }

    /** 展示状态请求体 */
    @Data
    public static class DisplayStatusRequest {
        /** 目标展示状态：VISIBLE | HIDDEN */
        private String displayStatus;
    }

    private Long currentAdminId(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof JwtPrincipal principal)) {
            throw new BusinessException(ErrorCode.AUTH_TOKEN_INVALID);
        }
        return principal.userId();
    }
}
