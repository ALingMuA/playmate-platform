package com.gameplay.coupon.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.gameplay.auth.security.JwtPrincipal;
import com.gameplay.common.api.ApiResponse;
import com.gameplay.common.exception.BusinessException;
import com.gameplay.common.exception.ErrorCode;
import com.gameplay.coupon.dto.CouponPreviewRequest;
import com.gameplay.coupon.dto.CouponPreviewResult;
import com.gameplay.coupon.dto.UserCouponView;
import com.gameplay.coupon.service.CouponService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 用户端优惠券接口：领券、查询我的优惠券、预约可用券、优惠预览。
 */
@RestController
@RequestMapping("/api/coupons")
@RequiredArgsConstructor
public class CouponController {

    private final CouponService couponService;

    @PostMapping("/{templateId}/claim")
    public ApiResponse<UserCouponView> claim(@PathVariable Long templateId, Authentication authentication) {
        return ApiResponse.ok(couponService.claim(currentUserId(authentication), templateId));
    }

    @GetMapping("/mine")
    public ApiResponse<Page<UserCouponView>> mine(@RequestParam(required = false) String status,
                                                  @RequestParam(defaultValue = "1") long page,
                                                  @RequestParam(defaultValue = "10") long size,
                                                  Authentication authentication) {
        return ApiResponse.ok(couponService.listMine(currentUserId(authentication), status, page, size));
    }

    @GetMapping("/available")
    public ApiResponse<List<UserCouponView>> available(@RequestParam Long companionServiceId,
                                                       @RequestParam Integer durationMinutes,
                                                       Authentication authentication) {
        return ApiResponse.ok(couponService.listAvailableForOrder(currentUserId(authentication), companionServiceId, durationMinutes));
    }

    @PostMapping("/preview")
    public ApiResponse<CouponPreviewResult> preview(@Valid @RequestBody CouponPreviewRequest request,
                                                    Authentication authentication) {
        return ApiResponse.ok(couponService.preview(currentUserId(authentication),
                request.getCompanionServiceId(), request.getDurationMinutes(), request.getCouponId()));
    }

    private Long currentUserId(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof JwtPrincipal principal)) {
            throw new BusinessException(ErrorCode.AUTH_TOKEN_INVALID);
        }
        return principal.userId();
    }
}
