package com.gameplay.ai.task.service;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Component
@ConditionalOnProperty(name = "app.ai.tasks.scheduling-enabled", havingValue = "true", matchIfMissing = true)
public class AiReplyTaskScheduler {

    private static final int WORKER_THREADS = 8;

    private final AiReplyTaskWorker worker;
    private final Set<Long> scheduled = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean shuttingDown = new AtomicBoolean(false);
    private final AtomicInteger threadSeq = new AtomicInteger();
    private final ThreadPoolExecutor executor = new ThreadPoolExecutor(
            WORKER_THREADS, WORKER_THREADS, 0L, TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(WORKER_THREADS),
            runnable -> {
                Thread thread = new Thread(runnable, "ai-reply-worker-" + threadSeq.incrementAndGet());
                thread.setDaemon(false);
                return thread;
            },
            new ThreadPoolExecutor.AbortPolicy());

    public AiReplyTaskScheduler(AiReplyTaskWorker worker) {
        this.worker = worker;
    }

    /** 仅按当前空闲工作槽位拉取待处理任务，避免内存队列积压导致 PENDING 超时误判。 */
    @Scheduled(fixedDelayString = "${app.ai.tasks.poll-interval-ms:1000}")
    public void poll() {
        if (shuttingDown.get()) {
            return;
        }
        int freeSlots = WORKER_THREADS - scheduled.size();
        if (freeSlots <= 0) {
            return;
        }
        for (var task : worker.pending(freeSlots)) {
            if (shuttingDown.get()) {
                break;
            }
            Long taskId = task.getId();
            if (!scheduled.add(taskId)) {
                continue;
            }
            try {
                executor.execute(() -> {
                    try {
                        worker.process(taskId);
                    } catch (Exception ex) {
                        log.warn("AI 任务调度失败，任务: {}，类型: {}", taskId, ex.getClass().getSimpleName());
                    } finally {
                        scheduled.remove(taskId);
                    }
                });
            } catch (RejectedExecutionException ex) {
                scheduled.remove(taskId);
                break;
            }
        }
    }

    /** 独立调度超时任务恢复，避免大批量恢复事务阻塞任务派发轮询。 */
    @Scheduled(fixedDelayString = "${app.ai.tasks.recover-interval-ms:15000}")
    public void recover() {
        if (shuttingDown.get()) {
            return;
        }
        worker.recoverExpired();
    }

    @PreDestroy
    public void shutdown() {
        shuttingDown.set(true);
        executor.shutdown();
        try {
            if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
                executor.shutdownNow();
                executor.awaitTermination(5, TimeUnit.SECONDS);
            }
        } catch (InterruptedException ex) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
