package com.gameplay.activity.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ActivityRequest {

    @NotBlank(message = "活动标题不能为空")
    private String title;

    private String subtitle = "";

    private String description = "";

    private String bannerImage = "";

    private String bgGradient = "linear-gradient(135deg, #667eea 0%, #764ba2 100%)";

    private String icon = "🎁";

    private String tag = "限时活动";

    private String tagColor = "#ff6b6b";

    private String actionType = "COUPON_CLAIM"; // COUPON_CLAIM, COMPANION_LIST, PAGE_NAV

    private String actionRoute = "";

    private Long couponTemplateId;

    private Integer sortOrder = 0;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime startTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime endTime;
}
