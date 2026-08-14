package com.gameplay.review.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.gameplay.auth.security.JwtPrincipal;
import com.gameplay.common.api.ApiResponse;
import com.gameplay.common.exception.BusinessException;
import com.gameplay.common.exception.ErrorCode;
import com.gameplay.review.dto.ComplaintCreateRequest;
import com.gameplay.review.dto.ComplaintView;
import com.gameplay.review.service.ComplaintService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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
 * 用户端投诉接口（FR-U17 发起投诉、FR-U18 证据、FR-U19 售后进度）。
 */
@RestController
@RequestMapping("/api/complaints")
@RequiredArgsConstructor
public class ComplaintController {

    private final ComplaintService complaintService;

    /** 发起投诉（FR-U17/U18）：订单进入售后中 */
    @PostMapping
    public ApiResponse<ComplaintView> create(@Valid @RequestBody ComplaintCreateRequest request,
                                             @RequestParam Long orderId,
                                             Authentication authentication) {
        return ApiResponse.ok(complaintService.createComplaint(currentUserId(authentication),
                withOrderId(request, orderId)));
    }

    /** 我的投诉分页（FR-U19 售后进度） */
    @GetMapping("/mine")
    public ApiResponse<Page<ComplaintView>> mine(@RequestParam(defaultValue = "1") long page,
                                                 @RequestParam(defaultValue = "10") long size,
                                                 Authentication authentication) {
        return ApiResponse.ok(complaintService.listMine(currentUserId(authentication), page, size));
    }

    /** 投诉详情（FR-U19） */
    @GetMapping("/{id}")
    public ApiResponse<ComplaintView> detail(@PathVariable Long id, Authentication authentication) {
        return ApiResponse.ok(complaintService.detail(currentUserId(authentication),
                roles(authentication), id));
    }

    private ComplaintCreateRequest withOrderId(ComplaintCreateRequest request, Long orderId) {
        request.setOrderId(orderId);
        return request;
    }

    private Long currentUserId(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof JwtPrincipal principal)) {
            throw new BusinessException(ErrorCode.AUTH_TOKEN_INVALID);
        }
        return principal.userId();
    }

    private List<String> roles(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof JwtPrincipal principal)) {
            throw new BusinessException(ErrorCode.AUTH_TOKEN_INVALID);
        }
        return principal.roles();
    }
}
