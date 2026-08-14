package com.gameplay.admin.mapper;

import lombok.Data;
import org.apache.ibatis.annotations.Select;

/**
 * 数据概览聚合查询（FR-M02/M21）。
 */
public interface AdminStatsMapper {

    /** 用户总数（逻辑删除自动过滤） */
    @Select("SELECT COUNT(*) FROM `user`")
    long countUsers();

    /** 陪玩师数 */
    @Select("SELECT COUNT(*) FROM companion_profile")
    long countCompanions();

    /** 服务项目数 */
    @Select("SELECT COUNT(*) FROM companion_service")
    long countServices();

    /** 订单总数 */
    @Select("SELECT COUNT(*) FROM play_order")
    long countOrders();

    /** 模拟交易额：已支付订单金额合计（排除待支付与已关闭） */
    @Select("SELECT COALESCE(SUM(total_amount_cents), 0) FROM play_order "
            + "WHERE order_status NOT IN ('PENDING_PAYMENT', 'CLOSED')")
    long sumPaidAmountCents();

    /** 今日订单数 */
    @Select("SELECT COUNT(*) FROM play_order WHERE created_at >= CURDATE()")
    long countTodayOrders();

    /** 待审核入驻申请数 */
    @Select("SELECT COUNT(*) FROM companion_application WHERE audit_status = 'PENDING'")
    long countPendingApplications();

    /** 待处理投诉数 */
    @Select("SELECT COUNT(*) FROM complaint WHERE complaint_status = 'PENDING'")
    long countPendingComplaints();

    /** 等待人工的客服会话数 */
    @Select("SELECT COUNT(*) FROM customer_conversation WHERE conversation_status = 'WAITING_HUMAN'")
    long countWaitingHumanConversations();

    /** 聚合结果容器（用于一次返回多值场景，当前各指标单独查询） */
    @Data
    class OverviewAggregate {
        private Long userCount;
        private Long companionCount;
        private Long serviceCount;
        private Long orderCount;
        private Long totalAmountCents;
        private Long todayOrderCount;
        private Long pendingApplications;
        private Long pendingComplaints;
        private Long waitingHumanConversations;
    }
}
