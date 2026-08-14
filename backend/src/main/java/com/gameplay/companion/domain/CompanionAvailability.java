package com.gameplay.companion.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 陪玩师可约档期表实体（对应 `companion_availability`，FR-P10/P11）。
 */
@Data
@TableName("companion_availability")
public class CompanionAvailability {

    /** 主键ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 陪玩师用户ID */
    private Long companionUserId;

    /** 可约开始时间 */
    private LocalDateTime startAt;

    /** 可约结束时间 */
    private LocalDateTime endAt;

    /** 状态：AVAILABLE、UNAVAILABLE */
    private String availabilityStatus;

    /** 来源：MANUAL、RECURRENCE */
    private String sourceType;

    /** 备注 */
    private String remark;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;
}
