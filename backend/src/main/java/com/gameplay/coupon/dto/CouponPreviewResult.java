package com.gameplay.coupon.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CouponPreviewResult {

    /** 原价，单位分 */
    private Long originalAmountCents;

    /** 优惠抵扣金额，单位分 */
    private Long discountAmountCents;

    /** 实际需支付金额，单位分 */
    private Long payableAmountCents;

    /** 优惠券ID */
    private Long couponId;

    /** 优惠券名称 */
    private String couponName;

    /** 优惠计算说明/描述 */
    private String message;

    /** 是否可用 */
    private Boolean available;
}
