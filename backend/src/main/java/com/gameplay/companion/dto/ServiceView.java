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

    /** 服务类型是否仍启用：0 否（已停用，前端标注）、1 是；类型被删除时为 0 */
    private Integer serviceTypeEnabled;

    private String title;
    private String description;

    /** 标签ID列表 */
    private List<Long> tagIds;

    /** 已启用标签名称列表（现有消费方沿用该字段） */
    private List<String> tagNames;

    /** 已停用或已被删除的标签名称（前端据此加"已停用"标注），取不到名称时回落为「标签#id」 */
    private List<String> disabledTagNames;

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