package com.gameplay.companion.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.gameplay.auth.security.JwtPrincipal;
import com.gameplay.common.api.ApiResponse;
import com.gameplay.common.exception.BusinessException;
import com.gameplay.common.exception.ErrorCode;
import com.gameplay.order.dto.AcceptOrderRequest;
import com.gameplay.order.dto.OrderView;
import com.gameplay.order.dto.RejectOrderRequest;
import com.gameplay.order.service.OrderService;
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

/**
 * 陪玩师接单履约接口（FR-P13~P18）。
 */
@RestController
@RequestMapping("/api/companion/orders")
@RequiredArgsConstructor
public class CompanionOrderController {

    private final OrderService orderService;

    /** 我的订单（FR-P18），status 可选：WAITING_ACCEPTANCE/WAITING_SERVICE/IN_SERVICE/WAITING_CONFIRMATION/COMPLETED/CLOSED */
    @GetMapping
    public ApiResponse<Page<OrderView>> list(@RequestParam(required = false) String status,
                                             @RequestParam(defaultValue = "1") long page,
                                             @RequestParam(defaultValue = "10") long size,
                                             Authentication authentication) {
        return ApiResponse.ok(orderService.listCompanionOrders(currentUserId(authentication), status, page, size));
    }

    /** 订单详情（FR-P18） */
    @GetMapping("/{id}")
    public ApiResponse<OrderView> detail(@PathVariable Long id, Authentication authentication) {
        Long userId = currentUserId(authentication);
        return ApiResponse.ok(orderService.detail(userId, principalRoles(authentication), id));
    }

    /** 接受订单（FR-P13，详细设计 2.4） */
    @PostMapping("/{id}/accept")
    public ApiResponse<OrderView> accept(@PathVariable Long id,
                                         @Valid @RequestBody AcceptOrderRequest request,
                                         Authentication authentication) {
        return ApiResponse.ok(orderService.accept(currentUserId(authentication), id));
    }

    /** 拒绝订单（FR-P14） */
    @PostMapping("/{id}/reject")
    public ApiResponse<OrderView> reject(@PathVariable Long id,
                                         @Valid @RequestBody RejectOrderRequest request,
                                         Authentication authentication) {
        return ApiResponse.ok(orderService.reject(currentUserId(authentication), id, request.getReason()));
    }

    /** 开始服务（FR-P15） */
    @PostMapping("/{id}/start")
    public ApiResponse<OrderView> start(@PathVariable Long id, Authentication authentication) {
        return ApiResponse.ok(orderService.startService(currentUserId(authentication), id));
    }

    /** 结束服务（FR-P16） */
    @PostMapping("/{id}/end")
    public ApiResponse<OrderView> end(@PathVariable Long id, Authentication authentication) {
        return ApiResponse.ok(orderService.endService(currentUserId(authentication), id));
    }

    private Long currentUserId(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof JwtPrincipal principal)) {
            throw new BusinessException(ErrorCode.AUTH_TOKEN_INVALID);
        }
        return principal.userId();
    }

    private java.util.List<String> principalRoles(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof JwtPrincipal principal)) {
            throw new BusinessException(ErrorCode.AUTH_TOKEN_INVALID);
        }
        return principal.roles();
    }
}
