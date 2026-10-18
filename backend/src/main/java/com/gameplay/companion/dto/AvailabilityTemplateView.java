package com.gameplay.companion.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * 周期模板视图（FR-P10 周期设置）。
 */
@Data
@Builder
public class AvailabilityTemplateView {

    private Long id;

    /** 模板名称 */
    private String templateName;

    /** 生效周几集合，逗号分隔（1=周一..7=周日） */
    private String dayOfWeek;

    /** 是否全天可约 */
    private Boolean allDay;

    /** 每日开始时间 */
    @JsonFormat(pattern = "HH:mm:ss")
    private LocalTime startTime;

    /** 每日结束时间 */
    @JsonFormat(pattern = "HH:mm:ss")
    private LocalTime endTime;

    /** 生效开始日期 */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate effectiveFrom;

    /** 生效结束日期（null 表示长期有效） */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate effectiveUntil;

    /** 是否启用 */
    private Boolean enabled;

    /** 已生成到的日期水位线 */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate lastGeneratedUntil;

    /** 展示用规则描述，如"周一至周五 19:00-23:00（10-17 起长期）" */
    private String ruleText;
}
