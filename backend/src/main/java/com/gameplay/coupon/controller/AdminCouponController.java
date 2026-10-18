package com.gameplay.coupon.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.gameplay.auth.security.JwtPrincipal;
import com.gameplay.common.api.ApiResponse;
import com.gameplay.common.exception.BusinessException;
import com.gameplay.common.exception.ErrorCode;
import com.gameplay.coupon.dto.CouponTemplateRequest;
import com.gameplay.coupon.dto.CouponTemplateView;
import com.gameplay.coupon.service.CouponService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/**
 * 管理端优惠券模板管理接口。
 */
@RestController
@RequestMapping("/api/admin/coupons")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminCouponController {

    private final CouponService couponService;

    @GetMapping
    public ApiResponse<Page<CouponTemplateView>> list(@RequestParam(required = false) String publishStatus,
                                                      @RequestParam(defaultValue = "1") long page,
                                                      @RequestParam(defaultValue = "10") long size) {
        return ApiResponse.ok(couponService.pageTemplates(publishStatus, page, size));
    }

    @PostMapping
    public ApiResponse<CouponTemplateView> create(@Valid @RequestBody CouponTemplateRequest request,
                                                  Authentication authentication) {
        return ApiResponse.ok(couponService.createTemplate(currentAdminId(authentication), request));
    }

    @PutMapping("/{id}")
    public ApiResponse<CouponTemplateView> update(@PathVariable Long id,
                                                  @Valid @RequestBody CouponTemplateRequest request) {
        return ApiResponse.ok(couponService.updateTemplate(id, request));
    }

    @PostMapping("/{id}/publish")
    public ApiResponse<CouponTemplateView> publish(@PathVariable Long id, Authentication authentication) {
        return ApiResponse.ok(couponService.publishTemplate(currentAdminId(authentication), id));
    }

    @PostMapping("/{id}/offline")
    public ApiResponse<CouponTemplateView> offline(@PathVariable Long id) {
        return ApiResponse.ok(couponService.offlineTemplate(id));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        couponService.deleteTemplate(id);
        return ApiResponse.ok();
    }

    private Long currentAdminId(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof JwtPrincipal principal)) {
            throw new BusinessException(ErrorCode.AUTH_TOKEN_INVALID);
        }
        return principal.userId();
    }
}
