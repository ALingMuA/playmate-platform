package com.gameplay.companion.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 档期新增（FR-P10：可约时段 / 临时不可约时段）。
 */
@Data
public class AvailabilityCreateRequest {

    /** 开始时间 */
    @NotNull(message = "请选择开始时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime startAt;

    /** 结束时间 */
    @NotNull(message = "请选择结束时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime endAt;

    /** 备注 */
    @Size(max = 200, message = "备注长度不能超过200")
    private String remark;
}
