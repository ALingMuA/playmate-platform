package com.gameplay.companion.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 陪玩服务项目表实体（对应 `companion_service`，FR-P07~P09）。
 */
@Data
@TableName("companion_service")
public class CompanionService {

    /** 主键ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 陪玩师用户ID */
    private Long companionUserId;

    /** 游戏ID */
    private Long gameId;

    /** 服务类型ID */
    private Long serviceTypeId;

    /** 服务标题 */
    private String title;

    /** 服务说明 */
    private String description;

    /** 标签ID数组JSON */
    private String tagIdsJson;

    /** 每小时价格，单位分 */
    private Long priceCents;

    /** 最短服务时长，单位分钟 */
    private Integer minDurationMinutes;

    /** 审核状态：PENDING、APPROVED、REJECTED */
    private String auditStatus;

    /** 服务状态：ON_SHELF、OFF_SHELF */
    private String serviceStatus;

    /** 审核管理员ID */
    private Long auditBy;

    /** 审核意见 */
    private String auditReason;

    /** 审核时间 */
    private LocalDateTime auditAt;

    /** 逻辑删除：0否，1是 */
    @TableLogic
    private Integer deleted;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;
}
