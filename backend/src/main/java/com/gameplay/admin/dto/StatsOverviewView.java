package com.gameplay.admin.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 管理端数据概览视图（FR-M02：用户数、陪玩师数、服务数、订单数、交易额、待办）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StatsOverviewView {

    /** 用户总数 */
    private Long userCount;

    /** 陪玩师数 */
    private Long companionCount;

    /** 服务项目数 */
    private Long serviceCount;

    /** 订单总数 */
    private Long orderCount;

    /** 模拟交易额（分） */
    private Long totalAmountCents;

    /** 今日订单数 */
    private Long todayOrderCount;

    /** 待审核入驻申请数 */
    private Long pendingApplications;

    /** 待处理投诉数 */
    private Long pendingComplaints;

    /** 等待人工的客服会话数 */
    private Long waitingHumanConversations;
}
