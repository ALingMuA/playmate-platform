package com.gameplay.companion.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 服务项目视图（FR-P07~P09）。
 */
@Data
@Builder
public class ServiceView {

    private Long id;
    private Long companionUserId;
    private Long gameId;
    private String gameName;
    private Long serviceTypeId;
    private String serviceTypeName;
    private String title;
    private String description;

    /** 标签ID列表 */
    private List<Long> tagIds;

    /** 标签名称列表 */
    private List<String> tagNames;

    /** 每小时价格，单位分 */
    private Long priceCents;

    /** 最短服务时长，单位分钟 */
    private Integer minDurationMinutes;

    /** 审核状态：PENDING、APPROVED、REJECTED */
    private String auditStatus;

    /** 服务状态：ON_SHELF、OFF_SHELF */
    private String serviceStatus;

    /** 审核意见 */
    private String auditReason;

    /** 创建时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    /** 更新时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedAt;
}