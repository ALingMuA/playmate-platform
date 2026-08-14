package com.gameplay.companion.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 档期视图（FR-P10）。
 */
@Data
@Builder
public class AvailabilityView {

    private Long id;
    private Long companionUserId;

    /** 可约开始时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime startAt;

    /** 可约结束时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime endAt;

    /** 状态：AVAILABLE、UNAVAILABLE */
    private String availabilityStatus;

    /** 来源：MANUAL、RECURRENCE */
    private String sourceType;

    /** 备注 */
    private String remark;
}
