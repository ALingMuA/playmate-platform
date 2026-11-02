package com.gameplay.admin.controller;

import com.gameplay.auth.security.JwtPrincipal;
import com.gameplay.common.api.ApiResponse;
import com.gameplay.common.exception.BusinessException;
import com.gameplay.common.exception.ErrorCode;
import com.gameplay.wallet.dto.DemoWalletConfigView;
import com.gameplay.wallet.dto.DemoWalletGrantRequest;
import com.gameplay.wallet.dto.DemoWalletGrantView;
import com.gameplay.wallet.dto.WalletView;
import com.gameplay.wallet.service.WalletService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 演示钱包管理端接口（仅管理员且演示开关开启时可用）。
 */
@RestController
@RequestMapping("/api/admin/demo-wallet")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminDemoWalletController {

    private final WalletService walletService;

    /** 查询演示钱包配置与允许面额 */
    @GetMapping("/config")
    public ApiResponse<DemoWalletConfigView> config() {
        return ApiResponse.ok(walletService.getDemoConfig());
    }

    /** 查询目标用户演示余额（不创建钱包账户）。 */
    @GetMapping("/users/{userId}/balance")
    public ApiResponse<WalletView> balance(@PathVariable Long userId) {
        return ApiResponse.ok(walletService.demoBalanceView(userId));
    }

    /** 发放演示余额 */
    @PostMapping("/grants")
    public ApiResponse<DemoWalletGrantView> grant(@Valid @RequestBody DemoWalletGrantRequest request,
                                                  Authentication authentication) {
        Long adminId = currentAdminId(authentication);
        DemoWalletGrantView view = walletService.grantDemoCredit(
                request.getUserId(),
                request.getAmountCents(),
                request.getReason(),
                request.getRequestId(),
                adminId
        );
        return ApiResponse.ok(view);
    }

    private Long currentAdminId(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof JwtPrincipal principal)) {
            throw new BusinessException(ErrorCode.AUTH_TOKEN_INVALID);
        }
        return principal.userId();
    }
}
