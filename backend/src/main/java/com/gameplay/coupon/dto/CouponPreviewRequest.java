package com.gameplay.coupon.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CouponPreviewRequest {

    @NotNull(message = "服务项目不能为空")
    private Long companionServiceId;

    @NotNull(message = "时长不能为空")
    private Integer durationMinutes;

    /** 可选传入优惠券ID进行预览；不传时仅返回原价 */
    private Long couponId;
}
