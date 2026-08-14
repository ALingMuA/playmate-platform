package com.gameplay.admin.service;

import com.gameplay.admin.dto.StatsOverviewView;
import com.gameplay.admin.mapper.AdminStatsMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 数据概览服务（FR-M02 数据概览、FR-M21 数据统计）。
 */
@Service
@RequiredArgsConstructor
public class AdminStatsService {

    private final AdminStatsMapper adminStatsMapper;

    /** 数据概览（FR-M02） */
    public StatsOverviewView overview() {
        return StatsOverviewView.builder()
                .userCount(adminStatsMapper.countUsers())
                .companionCount(adminStatsMapper.countCompanions())
                .serviceCount(adminStatsMapper.countServices())
                .orderCount(adminStatsMapper.countOrders())
                .totalAmountCents(adminStatsMapper.sumPaidAmountCents())
                .todayOrderCount(adminStatsMapper.countTodayOrders())
                .pendingApplications(adminStatsMapper.countPendingApplications())
                .pendingComplaints(adminStatsMapper.countPendingComplaints())
                .waitingHumanConversations(adminStatsMapper.countWaitingHumanConversations())
                .build();
    }
}
