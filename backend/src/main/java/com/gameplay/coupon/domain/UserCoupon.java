package com.gameplay.coupon.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户优惠券实体（对应 user_coupon 表）。
 */
@Data
@TableName("user_coupon")
public class UserCoupon {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 优惠券模板ID */
    private Long templateId;

    /** 用户ID */
    private Long userId;

    /** 优惠券编码 */
    private String couponCode;

    /** 状态：AVAILABLE、LOCKED、USED、EXPIRED */
    private String status;

    /** 有效期开始时间快照 */
    private LocalDateTime validFrom;

    /** 有效期结束时间快照 */
    private LocalDateTime validUntil;

    /** 锁定的订单ID */
    private Long lockedOrderId;

    /** 已核销使用的订单ID */
    private Long usedOrderId;

    /** 领取时间 */
    private LocalDateTime claimedAt;

    /** 锁定时间 */
    private LocalDateTime lockedAt;

    /** 使用时间 */
    private LocalDateTime usedAt;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;
}
