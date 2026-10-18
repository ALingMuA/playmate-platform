package com.gameplay.companion.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 陪玩师周日程全景视图（聚合可用档期、不可约时段以及已被订单锁定的时段）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScheduleOverviewView {

    /** 服务器当前时间（用于前端统一时间基准与过期计算） */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime serverNow;

    /** 档期已安排至的日期（yyyy-MM-dd） */
    private String generatedUntil;

    /** 陪玩师档期列表（包含 AVAILABLE 与 UNAVAILABLE） */
    private List<AvailabilityView> availabilities;

    /** 处于该周期内的有效订单锁定区间 */
    private List<BookedSlotView> bookedSlots;
}
