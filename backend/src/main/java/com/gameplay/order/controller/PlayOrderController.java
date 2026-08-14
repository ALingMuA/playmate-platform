package com.gameplay.order.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.gameplay.auth.security.JwtPrincipal;
import com.gameplay.common.api.ApiResponse;
import com.gameplay.common.exception.BusinessException;
import com.gameplay.common.exception.ErrorCode;
import com.gameplay.order.dto.CancelOrderRequest;
import com.gameplay.order.dto.CreateOrderRequest;
import com.gameplay.order.dto.OrderView;
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

import java.util.List;

/**
 * 用户端订单接口（FR-U07~U13）。
 */
@RestController
@RequestMapping("/api/play-orders")
@RequiredArgsConstructor
public class PlayOrderController {

    private final OrderService orderService;

    /** 创建预约订单（FR-U07/U08） */
    @PostMapping
    public ApiResponse<OrderView> create(@Valid @RequestBody CreateOrderRequest request,
                                         Authentication authentication) {
        return ApiResponse.ok(orderService.createOrder(currentUserId(authentication), request));
    }

    /** 模拟支付（FR-U09） */
    @PostMapping("/{id}/pay")
    public ApiResponse<OrderView> pay(@PathVariable Long id, Authentication authentication) {
        return ApiResponse.ok(orderService.pay(currentUserId(authentication), id));
    }

    /** 我的订单（FR-U10） */
    @GetMapping("/mine")
    public ApiResponse<Page<OrderView>> mine(@RequestParam(required = false) String status,
                                             @RequestParam(defaultValue = "1") long page,
                                             @RequestParam(defaultValue = "10") long size,
                                             Authentication authentication) {
        return ApiResponse.ok(orderService.listUserOrders(currentUserId(authentication), status, page, size));
    }

    /** 订单详情（FR-U11） */
    @GetMapping("/{id}")
    public ApiResponse<OrderView> detail(@PathVariable Long id, Authentication authentication) {
        Long userId = currentUserId(authentication);
        return ApiResponse.ok(orderService.detail(userId, roles(authentication), id));
    }

    /** 取消订单（FR-U12）：未支付直接关闭，已支付（待接单/待服务）全额退款 */
    @PostMapping("/{id}/cancel")
    public ApiResponse<OrderView> cancel(@PathVariable Long id,
                                         @Valid @RequestBody CancelOrderRequest request,
                                         Authentication authentication) {
        return ApiResponse.ok(orderService.cancel(currentUserId(authentication), id, request.getReason()));
    }

    /** 确认完成（FR-U13） */
    @PostMapping("/{id}/confirm")
    public ApiResponse<OrderView> confirm(@PathVariable Long id, Authentication authentication) {
        return ApiResponse.ok(orderService.confirmCompleted(currentUserId(authentication), id));
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
