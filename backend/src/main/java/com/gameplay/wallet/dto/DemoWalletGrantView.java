package com.gameplay.wallet.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 演示余额发放结果视图。
 */
@Data
@Builder
public class DemoWalletGrantView {

    /** 目标用户ID */
    private Long userId;

    /** 发放金额（分） */
    private Long amountCents;

    /** 发放后可用余额（分） */
    private Long balanceAfterCents;

    /** 业务流水号 */
    private String businessNo;

    /** 创建时间 */
    private LocalDateTime createdAt;
}
