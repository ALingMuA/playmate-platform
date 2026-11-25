package com.gameplay.infrastructure.schedule;

import com.gameplay.order.domain.PlayOrder;
import com.gameplay.order.mapper.PlayOrderMapper;
import com.gameplay.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Consumer;

/**
 * 订单超时定时任务（详细设计 7.2）。
 *
 * <p>每分钟执行一次：先按状态和截止时间游标分页定位候选订单，
 * 处理时再次行锁确认当前状态，防止与用户操作并发时误关闭、重复结算或毒丸阻塞后续批次。</p>
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
        LocalDateTime now = LocalDateTime.now();
        processBatches(
                afterId -> orderMapper.findExpiredPendingPaymentsAfter(now, afterId, BATCH_LIMIT),
                order -> orderService.closeExpiredPendingPayment(order.getId()),
                "关闭支付超时订单");
    }

    /** 接单超时关闭并退款（WAITING_ACCEPTANCE 且 accept_expire_at <= now） */
    @Scheduled(cron = "0 * * * * ?")
    public void closeExpiredWaitingAcceptances() {
        LocalDateTime now = LocalDateTime.now();
        processBatches(
                afterId -> orderMapper.findExpiredWaitingAcceptancesAfter(now, afterId, BATCH_LIMIT),
                order -> orderService.closeExpiredWaitingAcceptance(order.getId()),
                "关闭接单超时订单");
    }

    /** 自动确认完成（WAITING_CONFIRMATION 且结束后满24小时） */
    @Scheduled(cron = "0 * * * * ?")
    public void autoConfirmOrders() {
        LocalDateTime deadline = LocalDateTime.now().minusHours(24);
        processBatches(
                afterId -> orderMapper.findNeedAutoConfirmAfter(deadline, afterId, BATCH_LIMIT),
                order -> orderService.autoConfirmCompleted(order.getId()),
                "自动确认订单");
    }

    private void processBatches(java.util.function.LongFunction<List<PlayOrder>> batchFetcher,
                                Consumer<PlayOrder> handler,
                                String actionLabel) {
        long afterId = 0L;
        while (true) {
            List<PlayOrder> batch = batchFetcher.apply(afterId);
            if (batch == null || batch.isEmpty()) {
                break;
            }
            for (PlayOrder order : batch) {
                afterId = Math.max(afterId, order.getId());
                try {
                    handler.accept(order);
                } catch (Exception e) {
                    log.warn("{} {} 失败: {}", actionLabel, order.getId(), e.getMessage());
                }
            }
            if (batch.size() < BATCH_LIMIT) {
                break;
            }
        }
    }
}
