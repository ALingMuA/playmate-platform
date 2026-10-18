package com.gameplay.activity.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class ActivityView {

    private Long id;
    private String title;
    private String subtitle;
    private String description;
    private String bannerImage;
    private String bgGradient;
    private String icon;
    private String tag;
    private String tagColor;
    private String actionType;
    private String actionRoute;
    private Long couponTemplateId;
    private String couponTemplateName;
    private Integer sortOrder;
    private String status;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime startTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime endTime;

    private Long createdBy;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;
}
