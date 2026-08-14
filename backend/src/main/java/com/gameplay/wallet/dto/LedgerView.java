package com.gameplay.wallet.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 资金流水视图（FR-P19 收益明细）。
 */
@Data
@Builder
public class LedgerView {

    /** 幂等业务流水号 */
    private String businessNo;

    /** 关联订单ID */
    private Long orderId;

    /** 流水类型：PAYMENT、REFUND、SETTLEMENT、ADJUSTMENT */
    private String ledgerType;

    /** 方向：IN、OUT */
    private String direction;

    /** 变动金额，单位分 */
    private Long amountCents;

    /** 变动后可用余额 */
    private Long balanceAfterCents;

    /** 变动后冻结金额 */
    private Long frozenAfterCents;

    /** 流水说明 */
    private String remark;

    /** 创建时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;
}
