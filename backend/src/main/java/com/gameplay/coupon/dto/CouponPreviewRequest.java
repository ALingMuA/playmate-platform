package com.gameplay.coupon.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CouponPreviewRequest {

    @NotNull(message = "服务项目不能为空")
    private Long companionServiceId;

    @NotNull(message = "时长不能为空")
    @Min(value = 1, message = "服务时长不能少于1分钟")
    @Max(value = 1440, message = "单次服务时长不能超过1440分钟")
    private Integer durationMinutes;

    /** 可选传入优惠券ID进行预览；不传时仅返回原价 */
    private Long couponId;
}
