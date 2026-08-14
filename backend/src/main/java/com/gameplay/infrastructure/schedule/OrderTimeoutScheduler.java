package com.gameplay.infrastructure.schedule;

import com.gameplay.order.mapper.PlayOrderMapper;
import com.gameplay.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 订单超时定时任务（详细设计 7.2）。
 *
 * <p>每分钟执行一次：先按状态和截止时间定位候选订单，
 * 处理时再次行锁确认当前状态，防止与用户操作并发时误关闭或重复结算。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderTimeoutScheduler {

    private static final int BATCH_LIMIT = 200;

    private final PlayOrderMapper orderMapper;
    private final OrderService orderService;

    /** 支付超时关闭（PENDING_PAYMENT 且 pay_expire_at <= now） */
    @Scheduled(cron = "0 * * * * ?")
    public void closeExpiredPendingPayments() {
        orderMapper.findExpiredPendingPayments(LocalDateTime.now(), BATCH_LIMIT)
                .forEach(order -> {
                    try {
                        orderService.closeExpiredPendingPayment(order.getId());
                    } catch (Exception e) {
                        log.warn("关闭支付超时订单 {} 失败: {}", order.getId(), e.getMessage());
                    }
                });
    }

    /** 接单超时关闭并退款（WAITING_ACCEPTANCE 且 accept_expire_at <= now） */
    @Scheduled(cron = "0 * * * * ?")
    public void closeExpiredWaitingAcceptances() {
        orderMapper.findExpiredWaitingAcceptances(LocalDateTime.now(), BATCH_LIMIT)
                .forEach(order -> {
                    try {
                        orderService.closeExpiredWaitingAcceptance(order.getId());
                    } catch (Exception e) {
                        log.warn("关闭接单超时订单 {} 失败: {}", order.getId(), e.getMessage());
                    }
                });
    }

    /** 自动确认完成（WAITING_CONFIRMATION 且结束后满24小时） */
    @Scheduled(cron = "0 * * * * ?")
    public void autoConfirmOrders() {
        LocalDateTime deadline = LocalDateTime.now().minusHours(24);
        orderMapper.findNeedAutoConfirm(deadline, BATCH_LIMIT)
                .forEach(order -> {
                    try {
                        orderService.autoConfirmCompleted(order.getId());
                    } catch (Exception e) {
                        log.warn("自动确认订单 {} 失败: {}", order.getId(), e.getMessage());
                    }
                });
    }
}
