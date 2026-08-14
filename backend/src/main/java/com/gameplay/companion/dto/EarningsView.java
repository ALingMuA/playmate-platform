package com.gameplay.companion.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 陪玩师收益概览（FR-P19）。
 */
@Data
@Builder
public class EarningsView {

    /** 可用余额（含收益入账），单位分 */
    private Long balanceCents;

    /** 冻结金额，单位分 */
    private Long frozenCents;

    /** 累计已结算收益，单位分 */
    private Long totalIncomeCents;

    /** 已完成订单数 */
    private Integer completedOrderCount;

    /** 有效评价平均分 */
    private BigDecimal ratingAvg;

    /** 有效评价数量 */
    private Integer ratingCount;
}
