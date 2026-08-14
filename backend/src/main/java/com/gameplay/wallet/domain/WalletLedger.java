package com.gameplay.wallet.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 虚拟资金流水表实体（对应 `wallet_ledger`，FR-P19 收益明细）。
 */
@Data
@TableName("wallet_ledger")
public class WalletLedger {

    /** 主键ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 幂等业务流水号 */
    private String businessNo;

    /** 资金所属用户ID */
    private Long userId;

    /** 关联订单ID */
    private Long orderId;

    /** 流水类型：PAYMENT、REFUND、SETTLEMENT、ADJUSTMENT */
    private String ledgerType;

    /** 方向：IN、OUT */
    private String direction;

    /** 变动金额，单位分且为正数 */
    private Long amountCents;

    /** 变动前可用余额 */
    private Long balanceBeforeCents;

    /** 变动后可用余额 */
    private Long balanceAfterCents;

    /** 变动前冻结金额 */
    private Long frozenBeforeCents;

    /** 变动后冻结金额 */
    private Long frozenAfterCents;

    /** 流水说明 */
    private String remark;

    /** 创建者ID，系统为0 */
    private Long createdBy;

    /** 创建时间 */
    private LocalDateTime createdAt;
}
