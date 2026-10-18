package com.gameplay.coupon.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class UserCouponView {

    private Long id;
    private Long templateId;
    private String templateName;
    private String couponCode;
    private String couponType;
    private Long thresholdCents;
    private Long discountCents;
    private Integer discountRateBp;
    private Long maxDiscountCents;
    private String scopeType;
    private Long scopeId;
    private String status;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime validFrom;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime validUntil;

    private Long lockedOrderId;
    private Long usedOrderId;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime claimedAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime usedAt;

    /** 在预约时是否可用（动态计算） */
    private Boolean isApplicable;
    /** 预计优惠金额（分） */
    private Long expectedDiscountCents;
    /** 不可用原因说明 */
    private String notApplicableReason;
}
