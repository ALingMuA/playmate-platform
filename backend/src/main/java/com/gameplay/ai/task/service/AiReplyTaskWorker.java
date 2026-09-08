package com.gameplay.ai.task.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.gameplay.ai.config.AiModelProperties;
import com.gameplay.ai.service.AiResponseFacade;
import com.gameplay.ai.strategy.AiProcessResult;
import com.gameplay.ai.strategy.AiRequest;
import com.gameplay.ai.task.domain.AiReplyTask;
import com.gameplay.ai.task.mapper.AiReplyTaskMapper;
import com.gameplay.common.enums.ConversationStatus;
import com.gameplay.customer_service.domain.CustomerConversation;
import com.gameplay.customer_service.domain.CustomerServiceMessage;
import com.gameplay.customer_service.enums.SenderType;
import com.gameplay.customer_service.mapper.CustomerConversationMapper;
import com.gameplay.customer_service.mapper.CustomerServiceMessageMapper;
import com.gameplay.customer_service.service.CustomerConversationService;
import com.gameplay.customer_service.service.CustomerMessageService;
import com.gameplay.order.domain.PlayOrder;
import com.gameplay.order.mapper.PlayOrderMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiReplyTaskWorker {
    private final AiReplyTaskMapper taskMapper;
    private final AiReplyTaskService taskService;
    private final CustomerConversationMapper conversationMapper;
    private final CustomerServiceMessageMapper messageMapper;
    private final CustomerMessageService messageService;
    private final CustomerConversationService conversationService;
    private final PlayOrderMapper orderMapper;
    private final AiResponseFacade facade;
    private final AiModelProperties properties;
    private final TransactionTemplate transactions;

    public List<AiReplyTask> pending(int limit) {
        return taskMapper.selectList(new LambdaQueryWrapper<AiReplyTask>()
                .eq(AiReplyTask::getStatus, "PENDING").orderByAsc(AiReplyTask::getId)
                .last("LIMIT " + Math.min(Math.max(limit, 1), 32)));
    }

    public void process(Long taskId) {
        AiRequest request = transactions.execute(status -> claim(taskId));
        if (request == null) {
            return;
        }
        try {
            // 外部模型调用位于两个短事务之间，不持有会话或任务行锁。
            AiProcessResult result = facade.process(request);
            conversationService.completeAiTask(taskId, result, null);
        } catch (Exception ex) {
            log.warn("AI 任务 {} 失败，异常类型: {}", taskId, ex.getClass().getSimpleName());
            conversationService.completeAiTask(taskId, null, "TASK_EXECUTION_FAILED");
        }
    }

    private AiRequest claim(Long taskId) {
        AiReplyTask candidate = taskMapper.selectById(taskId);
        if (candidate == null) {
            return null;
        }
        CustomerConversation conversation = conversationMapper.selectForUpdate(candidate.getConversationId());
        AiReplyTask task = taskMapper.selectForUpdate(taskId);
        if (!"PENDING".equals(task.getStatus())) {
            return null;
        }
        if (conversation == null || !ConversationStatus.AI_PROCESSING.name().equals(conversation.getConversationStatus())) {
            taskService.finish(task, "CANCELLED", "CONVERSATION_CHANGED");
            if (conversation != null) {
                taskService.notifyStatus(task, conversation.getInitiatorUserId());
            }
            return null;
        }
        CustomerServiceMessage message = messageMapper.selectById(task.getUserMessageId());
        if (message == null || !message.getConversationId().equals(conversation.getId())
                || !SenderType.USER.name().equals(message.getSenderType())
                || !message.getSenderId().equals(conversation.getInitiatorUserId())) {
            conversationService.completeAiTask(taskId, null, "TASK_CONTEXT_INVALID");
            return null;
        }
        String orderSummary = "";
        if (task.getRelatedOrderId() != null && task.getRelatedOrderId() > 0) {
            PlayOrder order = orderMapper.selectById(task.getRelatedOrderId());
            if (order == null || !order.getUserId().equals(conversation.getInitiatorUserId())) {
                conversationService.completeAiTask(taskId, null, "TASK_CONTEXT_INVALID");
                return null;
            }
            orderSummary = "订单状态:" + order.getOrderStatus();
        }
        LocalDateTime now = LocalDateTime.now();
        int changed = taskMapper.update(null, new LambdaUpdateWrapper<AiReplyTask>()
                .eq(AiReplyTask::getId, taskId).eq(AiReplyTask::getStatus, "PENDING")
                .set(AiReplyTask::getStatus, "RUNNING").set(AiReplyTask::getStartedAt, now)
                .set(AiReplyTask::getUpdatedAt, now));
        if (changed == 0) {
            return null;
        }
        task.setStatus("RUNNING");
        task.setUpdatedAt(now);
        taskService.notifyStatus(task, conversation.getInitiatorUserId());
        return new AiRequest(message.getContent(), "来源:" + conversation.getSourceType(), conversation.getId(),
                orderSummary, conversation.getUnresolvedCount(), false,
                messageService.aiHistory(conversation.getId(), message.getId(), properties.getHistoryMessages()));
    }

    /** 超时遗留任务只终止并转人工，避免进程重启后重复请求模型产生额外费用。 */
    public void recoverExpired() {
        LocalDateTime now = LocalDateTime.now();
        List<AiReplyTask> candidates = taskMapper.selectList(new LambdaQueryWrapper<AiReplyTask>()
                .in(AiReplyTask::getStatus, "PENDING", "RUNNING")
                .lt(AiReplyTask::getUpdatedAt, now.minusSeconds(Math.max(properties.getTimeoutSeconds() + 15, 40)))
                .orderByAsc(AiReplyTask::getId).last("LIMIT 100"));
        for (AiReplyTask candidate : candidates) {
            try {
                recoverExpired(candidate.getId());
            } catch (Exception ex) {
                log.warn("AI 任务恢复失败，任务: {}，类型: {}", candidate.getId(), ex.getClass().getSimpleName());
            }
        }
    }

    public void recoverExpired(Long taskId) {
        AiReplyTask candidate = taskMapper.selectById(taskId);
        if (candidate == null) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        transactions.executeWithoutResult(status -> {
            conversationMapper.selectForUpdate(candidate.getConversationId());
            AiReplyTask task = taskMapper.selectForUpdate(taskId);
            if (task == null) {
                return;
            }
            boolean expired = "RUNNING".equals(task.getStatus()) && task.getStartedAt() != null
                    && task.getStartedAt().isBefore(now.minusSeconds(Math.max(properties.getTimeoutSeconds() + 15, 40)));
            expired |= "PENDING".equals(task.getStatus()) && task.getCreatedAt().isBefore(now.minusMinutes(2));
            if (expired) {
                conversationService.completeAiTask(task.getId(), null, "TASK_TIMEOUT");
            }
        });
    }
}
