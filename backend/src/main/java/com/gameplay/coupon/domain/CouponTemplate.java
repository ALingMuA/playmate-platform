package com.gameplay.coupon.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 优惠券模板实体（对应 coupon_template 表）。
 */
@Data
@TableName("coupon_template")
public class CouponTemplate {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 优惠券名称 */
    private String name;

    /** 优惠券类型：FIXED、DISCOUNT */
    private String couponType;

    /** 使用门槛，单位分，0为无门槛 */
    private Long thresholdCents;

    /** 满减金额，单位分 */
    private Long discountCents;

    /** 折扣基点，如 8500 表示 8.5 折 */
    private Integer discountRateBp;

    /** 最高优惠金额，单位分，0表示不限制 */
    private Long maxDiscountCents;

    /** 适用范围：ALL、GAME、SERVICE、COMPANION */
    private String scopeType;

    /** 适用目标ID，ALL时为0 */
    private Long scopeId;

    /** 关联活动ID，可为空 */
    private Long activityId;

    /** 总发行量，0表示不限量 */
    private Integer totalQuantity;

    /** 已领取数量 */
    private Integer claimedQuantity;

    /** 每人限领数量 */
    private Integer perUserLimit;

    /** 有效期开始时间 */
    private LocalDateTime validFrom;

    /** 有效期结束时间 */
    private LocalDateTime validUntil;

    /** 发布状态：DRAFT、PUBLISHED、OFFLINE */
    private String publishStatus;

    /** 创建管理员ID */
    private Long createdBy;

    /** 发布管理员ID */
    private Long publishedBy;

    /** 发布时间 */
    private LocalDateTime publishedAt;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;
}
