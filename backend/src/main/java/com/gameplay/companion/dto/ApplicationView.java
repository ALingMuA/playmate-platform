package com.gameplay.companion.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 入驻申请视图（FR-P03 申请状态查询）。
 */
@Data
@Builder
public class ApplicationView {

    private Long id;

    /** 申请类型：ENTRY 入驻申请、CAPABILITY_UPDATE 能力变更申请 */
    private String applicationType;

    private String realName;
    private String contactMobile;
    private String introduction;

    /** 游戏能力列表 */
    private List<GameCapabilityDto> capabilities;

    /** 证明图片地址列表 */
    private List<String> proofUrls;

    /** 审核状态：PENDING、APPROVED、REJECTED */
    private String auditStatus;

    /** 审核意见或驳回原因 */
    private String auditReason;

    /** 审核时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime auditedAt;

    /** 创建时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;
}
