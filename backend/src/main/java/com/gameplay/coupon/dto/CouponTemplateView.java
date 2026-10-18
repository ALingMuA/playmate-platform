package com.gameplay.coupon.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class CouponTemplateView {

    private Long id;
    private String name;
    private String couponType;
    private Long thresholdCents;
    private Long discountCents;
    private Integer discountRateBp;
    private Long maxDiscountCents;
    private String scopeType;
    private Long scopeId;
    private Long activityId;
    private Integer totalQuantity;
    private Integer claimedQuantity;
    private Integer remainingQuantity;
    private Integer perUserLimit;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime validFrom;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime validUntil;

    private String publishStatus;
    private Long createdBy;
    private Long publishedBy;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime publishedAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;
}
