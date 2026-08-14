package com.gameplay.review.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.gameplay.auth.security.JwtPrincipal;
import com.gameplay.common.api.ApiResponse;
import com.gameplay.common.exception.BusinessException;
import com.gameplay.common.exception.ErrorCode;
import com.gameplay.review.dto.ReviewCreateRequest;
import com.gameplay.review.dto.ReviewView;
import com.gameplay.review.service.ReviewService;
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
 * 用户端评价接口（FR-U15 提交评价、FR-U16 评价展示）。
 */
@RestController
@RequestMapping("/api/reviews")
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewService reviewService;

    /** 提交评价（FR-U15）：仅本人已完成的订单 */
    @PostMapping
    public ApiResponse<ReviewView> submit(@Valid @RequestBody ReviewCreateRequest request,
                                          @RequestParam Long orderId,
                                          Authentication authentication) {
        return ApiResponse.ok(reviewService.submitReview(currentUserId(authentication), orderId, request));
    }

    /** 我的评价分页 */
    @GetMapping("/mine")
    public ApiResponse<Page<ReviewView>> mine(@RequestParam(defaultValue = "1") long page,
                                              @RequestParam(defaultValue = "10") long size,
                                              Authentication authentication) {
        return ApiResponse.ok(reviewService.listMine(currentUserId(authentication), page, size));
    }

    /** 陪玩师公开评价分页（FR-U16）：仅 VISIBLE，游客可访问 */
    @GetMapping("/companion/{companionUserId}")
    public ApiResponse<Page<ReviewView>> byCompanion(@PathVariable Long companionUserId,
                                                     @RequestParam(defaultValue = "1") long page,
                                                     @RequestParam(defaultValue = "10") long size) {
        return ApiResponse.ok(reviewService.listByCompanion(companionUserId, page, size));
    }

    /** 某订单的评价（下单人/陪玩师/管理员，用于判断是否已评价） */
    @GetMapping("/order/{orderId}")
    public ApiResponse<ReviewView> byOrder(@PathVariable Long orderId, Authentication authentication) {
        return ApiResponse.ok(reviewService.getByOrder(orderId,
                currentUserId(authentication), roles(authentication)));
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
