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

@Slf4j
@Component
@ConditionalOnProperty(name = "app.ai.tasks.scheduling-enabled", havingValue = "true", matchIfMissing = true)
public class AiReplyTaskScheduler {
    private final AiReplyTaskWorker worker;
    private final Set<Long> scheduled = ConcurrentHashMap.newKeySet();
    private final ThreadPoolExecutor executor = new ThreadPoolExecutor(2, 2, 0L, TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(16), runnable -> {
                Thread thread = new Thread(runnable, "ai-reply-worker");
                thread.setDaemon(true);
                return thread;
            }, new ThreadPoolExecutor.AbortPolicy());

    public AiReplyTaskScheduler(AiReplyTaskWorker worker) {
        this.worker = worker;
    }

    @Scheduled(fixedDelayString = "${app.ai.tasks.poll-interval-ms:1000}")
    public void poll() {
        worker.recoverExpired();
        for (var task : worker.pending(20)) {
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

    @PreDestroy
    public void shutdown() {
        executor.shutdownNow();
    }
}
