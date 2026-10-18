package com.gameplay.companion.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * 周期模板新增/编辑请求（FR-P10 周期设置）。
 *
 * <p>存储模型统一为「周几集合 + 每日时段（或全天）+ 生效日期范围」；
 * 前端可基于此表达"每周固定""每天""指定日期范围重复"三种使用形态。</p>
 */
@Data
public class AvailabilityTemplateSaveRequest {

    /** 模板名称 */
    @NotBlank(message = "请填写模板名称")
    @Size(max = 50, message = "模板名称长度不能超过50")
    private String templateName;

    /** 生效周几集合，逗号分隔（1=周一..7=周日），至少选择一天 */
    @NotBlank(message = "请选择生效的星期")
    private String dayOfWeek;

    /** 是否全天可约 */
    @NotNull(message = "请选择是否全天可约")
    private Boolean allDay;

    /** 每日开始时间（allDay=false 时必填） */
    @JsonFormat(pattern = "HH:mm:ss")
    private LocalTime startTime;

    /** 每日结束时间（allDay=false 时必填；早于开始时间视为跨天，如 20:00-02:00） */
    @JsonFormat(pattern = "HH:mm:ss")
    private LocalTime endTime;

    /** 生效开始日期 */
    @NotNull(message = "请选择生效开始日期")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate effectiveFrom;

    /** 生效结束日期（为空表示长期有效） */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate effectiveUntil;
}
