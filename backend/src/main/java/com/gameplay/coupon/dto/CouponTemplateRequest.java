package com.gameplay.coupon.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
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

    @Min(value = 0, message = "使用门槛不能小于0")
    private Long thresholdCents = 0L;

    @Min(value = 0, message = "满减金额不能小于0")
    private Long discountCents = 0L;

    @Min(value = 1, message = "折扣基点不能小于1")
    @Max(value = 10000, message = "折扣基点不能超过10000")
    private Integer discountRateBp = 10000;

    @Min(value = 0, message = "最高减免金额不能小于0")
    private Long maxDiscountCents = 0L;

    private String scopeType = "ALL"; // ALL, GAME, SERVICE, COMPANION

    @Min(value = 0, message = "适用范围对象ID不能小于0")
    private Long scopeId = 0L;

    private Long activityId;

    @Min(value = 0, message = "总发行量不能小于0")
    private Integer totalQuantity = 0; // 0 表示不限量

    @Min(value = 1, message = "每人限领数量必须大于0")
    private Integer perUserLimit = 1;

    @NotNull(message = "有效期开始时间不能为空")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime validFrom;

    @NotNull(message = "有效期结束时间不能为空")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime validUntil;
}
