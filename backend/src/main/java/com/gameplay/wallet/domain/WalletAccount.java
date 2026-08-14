package com.gameplay.wallet.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 虚拟钱包账户表实体（对应 `wallet_account`）。
 */
@Data
@TableName("wallet_account")
public class WalletAccount {

    /** 主键ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 用户ID */
    private Long userId;

    /** 可用虚拟余额，单位分 */
    private Long balanceCents;

    /** 冻结余额或收益，单位分 */
    private Long frozenCents;

    /** 陪玩师累计已结算收益，单位分 */
    private Long totalIncomeCents;

    /** 乐观锁版本号 */
    private Integer version;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;
}
