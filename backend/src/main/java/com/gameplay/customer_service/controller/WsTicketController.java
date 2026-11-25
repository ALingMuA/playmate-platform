package com.gameplay.customer_service.controller;

import com.gameplay.auth.security.JwtPrincipal;
import com.gameplay.common.api.ApiResponse;
import com.gameplay.common.exception.BusinessException;
import com.gameplay.common.exception.ErrorCode;
import com.gameplay.infrastructure.websocket.WsTicketService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * WebSocket 一次性握手票据接口（所有已登录用户均可申请）。
 */
@RestController
@RequestMapping("/api/customer-service")
@RequiredArgsConstructor
public class WsTicketController {

    private final WsTicketService wsTicketService;

    /** 申请一次性 WebSocket 连接票据（30 秒内单次有效） */
    @PostMapping("/ws-ticket")
    public ApiResponse<Map<String, String>> issueTicket(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof JwtPrincipal principal)) {
            throw new BusinessException(ErrorCode.AUTH_TOKEN_INVALID);
        }
        String ticket = wsTicketService.issue(principal);
        return ApiResponse.ok(Map.of("ticket", ticket));
    }
}
