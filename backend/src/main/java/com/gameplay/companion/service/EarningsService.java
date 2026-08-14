package com.gameplay.companion.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.gameplay.companion.domain.CompanionProfile;
import com.gameplay.companion.dto.EarningsView;
import com.gameplay.wallet.dto.LedgerView;
import com.gameplay.wallet.dto.WalletView;
import com.gameplay.wallet.service.WalletService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 陪玩师收益服务（FR-P19）：收益概览与流水分页。
 */
@Service
@RequiredArgsConstructor
public class EarningsService {

    private final WalletService walletService;
    private final CompanionProfileService profileService;

    /** 收益概览：钱包 + 已完成订单数 + 评分 */
    public EarningsView overview(Long userId) {
        WalletView wallet = walletService.view(userId);
        CompanionProfile profile = profileService.getByUserId(userId);
        if (profile == null) {
            return EarningsView.builder()
                    .balanceCents(wallet.getBalanceCents())
                    .frozenCents(wallet.getFrozenCents())
                    .totalIncomeCents(wallet.getTotalIncomeCents())
                    .completedOrderCount(0)
                    .ratingAvg(null)
                    .ratingCount(0)
                    .build();
        }
        return EarningsView.builder()
                .balanceCents(wallet.getBalanceCents())
                .frozenCents(wallet.getFrozenCents())
                .totalIncomeCents(wallet.getTotalIncomeCents())
                .completedOrderCount(profile.getCompletedOrderCount())
                .ratingAvg(profile.getRatingAvg())
                .ratingCount(profile.getRatingCount())
                .build();
    }

    /** 收益流水分页 */
    public Page<LedgerView> ledgers(Long userId, long page, long size) {
        return walletService.ledgers(userId, page, size);
    }
}
