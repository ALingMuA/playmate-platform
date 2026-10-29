package com.gameplay.wallet.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 演示钱包配置信息视图。
 */
@Data
@Builder
public class DemoWalletConfigView {

    /** 是否启用演示余额发放 */
    private boolean enabled;

    /** 允许发放的档位（分） */
    private List<Long> allowedAmountsCents;
}
