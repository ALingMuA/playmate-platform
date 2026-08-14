package com.gameplay.companion.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.gameplay.auth.security.JwtPrincipal;
import com.gameplay.common.api.ApiResponse;
import com.gameplay.common.exception.BusinessException;
import com.gameplay.common.exception.ErrorCode;
import com.gameplay.companion.dto.EarningsView;
import com.gameplay.companion.service.EarningsService;
import com.gameplay.wallet.dto.LedgerView;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 陪玩师收益接口（FR-P19）。
 */
@RestController
@RequestMapping("/api/companion/earnings")
@RequiredArgsConstructor
public class EarningsController {

    private final EarningsService earningsService;

    /** 收益概览 */
    @GetMapping
    public ApiResponse<EarningsView> overview(Authentication authentication) {
        return ApiResponse.ok(earningsService.overview(currentUserId(authentication)));
    }

    /** 收益流水（分页） */
    @GetMapping("/ledgers")
    public ApiResponse<Page<LedgerView>> ledgers(@RequestParam(defaultValue = "1") long page,
                                                 @RequestParam(defaultValue = "10") long size,
                                                 Authentication authentication) {
        return ApiResponse.ok(earningsService.ledgers(currentUserId(authentication), page, size));
    }

    private Long currentUserId(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof JwtPrincipal principal)) {
            throw new BusinessException(ErrorCode.AUTH_TOKEN_INVALID);
        }
        return principal.userId();
    }
}
