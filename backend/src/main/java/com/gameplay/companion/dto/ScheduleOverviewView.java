package com.gameplay.companion.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 陪玩师周日程全景视图（聚合可用档期、不可约时段以及已被订单锁定的时段）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScheduleOverviewView {

    /** 陪玩师档期列表（包含 AVAILABLE 与 UNAVAILABLE） */
    private List<AvailabilityView> availabilities;

    /** 处于该周期内的有效订单锁定区间 */
    private List<BookedSlotView> bookedSlots;
}
