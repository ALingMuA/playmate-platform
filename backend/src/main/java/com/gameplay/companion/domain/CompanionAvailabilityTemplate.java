package com.gameplay.companion.domain;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * 陪玩师档期周期模板实体（对应 `companion_availability_template`，FR-P10 周期设置）。
 *
 * <p>规则模型：生效日期范围内，按周几集合循环，每日生成固定时段（或全天）的可约档期。
 * 滚动续期到未来 28 天，设置一次长期生效。</p>
 */
@Data
@TableName("companion_availability_template")
public class CompanionAvailabilityTemplate {

    /** 主键ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 陪玩师用户ID */
    private Long companionUserId;

    /** 模板名称 */
    private String templateName;

    /** 生效周几集合，逗号分隔（1=周一..7=周日） */
    private String dayOfWeek;

    /** 是否全天可约：0否，1是 */
    private Integer allDay;

    /** 每日开始时间（all_day=0 时必填） */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalTime startTime;

    /** 每日结束时间（all_day=0 时必填；早于开始时间视为跨天） */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalTime endTime;

    /** 生效开始日期 */
    private LocalDate effectiveFrom;

    /** 生效结束日期（NULL 表示长期有效） */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDate effectiveUntil;

    /** 是否启用：0停用，1启用 */
    private Integer enabled;

    /** 已生成到的日期水位线（滚动续期使用） */
    private LocalDate lastGeneratedUntil;

    /** 创建时间 */
    @TableField(updateStrategy = FieldStrategy.NEVER)
    private LocalDateTime createdAt;

    /** 更新时间 */
    @TableField(updateStrategy = FieldStrategy.NEVER)
    private LocalDateTime updatedAt;
}
