package com.gameplay.companion.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * 档期批量创建请求（FR-P10 日期范围设置）：日期范围内每日生成同一时段（可全天）。
 */
@Data
public class AvailabilityBatchCreateRequest {

    /** 开始日期 */
    @NotNull(message = "请选择开始日期")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate startDate;

    /** 结束日期（为空表示仅startDate当天） */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate endDate;

    /** 是否全天可约 */
    @NotNull(message = "请选择是否全天可约")
    private Boolean allDay;

    /** 每日开始时间（allDay=false 时必填） */
    @JsonFormat(pattern = "HH:mm:ss")
    private LocalTime startTime;

    /** 每日结束时间（allDay=false 时必填；早于开始时间视为跨天，如 20:00-02:00） */
    @JsonFormat(pattern = "HH:mm:ss")
    private LocalTime endTime;

    /** 备注 */
    @Size(max = 200, message = "备注长度不能超过200")
    private String remark;
}
