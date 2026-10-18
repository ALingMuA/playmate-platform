package com.gameplay.coupon.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class CouponTemplateRequest {

    @NotBlank(message = "优惠券名称不能为空")
    private String name;

    @NotBlank(message = "优惠券类型不能为空")
    private String couponType; // FIXED, DISCOUNT

    private Long thresholdCents = 0L;

    private Long discountCents = 0L;

    private Integer discountRateBp = 10000;

    private Long maxDiscountCents = 0L;

    private String scopeType = "ALL"; // ALL, GAME, SERVICE, COMPANION

    private Long scopeId = 0L;

    private Long activityId;

    private Integer totalQuantity = 0; // 0 表示不限量

    private Integer perUserLimit = 1;

    @NotNull(message = "有效期开始时间不能为空")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime validFrom;

    @NotNull(message = "有效期结束时间不能为空")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime validUntil;
}
