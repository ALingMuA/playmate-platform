package com.gameplay.wallet.dto;

import lombok.Builder;
import lombok.Data;

/**
 * 钱包概览（FR-P19/FR-U09）。
 */
@Data
@Builder
public class WalletView {

    /** 可用余额，单位分 */
    private Long balanceCents;

    /** 冻结金额，单位分 */
    private Long frozenCents;

    /** 累计已结算收益，单位分 */
    private Long totalIncomeCents;
}
