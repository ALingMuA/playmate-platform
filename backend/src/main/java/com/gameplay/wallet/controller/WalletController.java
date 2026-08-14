package com.gameplay.wallet.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.gameplay.auth.security.JwtPrincipal;
import com.gameplay.common.api.ApiResponse;
import com.gameplay.common.exception.BusinessException;
import com.gameplay.common.exception.ErrorCode;
import com.gameplay.wallet.dto.LedgerView;
import com.gameplay.wallet.dto.WalletView;
import com.gameplay.wallet.service.WalletService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户端钱包接口（FR-U09 虚拟余额、FR-P19 流水）。
 */
@RestController
@RequestMapping("/api/wallet")
@RequiredArgsConstructor
public class WalletController {

    private final WalletService walletService;

    /** 我的钱包概览 */
    @GetMapping("/me")
    public ApiResponse<WalletView> me(Authentication authentication) {
        return ApiResponse.ok(walletService.view(currentUserId(authentication)));
    }

    /** 我的资金流水 */
    @GetMapping("/me/ledgers")
    public ApiResponse<Page<LedgerView>> ledgers(@RequestParam(defaultValue = "1") long page,
                                                 @RequestParam(defaultValue = "10") long size,
                                                 Authentication authentication) {
        return ApiResponse.ok(walletService.ledgers(currentUserId(authentication), page, size));
    }

    private Long currentUserId(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof JwtPrincipal principal)) {
            throw new BusinessException(ErrorCode.AUTH_TOKEN_INVALID);
        }
        return principal.userId();
    }
}
