package com.gameplay.ai.task.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.gameplay.ai.task.domain.AiReplyTask;
import com.gameplay.ai.task.dto.AiTaskView;
import com.gameplay.ai.task.mapper.AiReplyTaskMapper;
import com.gameplay.common.exception.BusinessException;
import com.gameplay.common.exception.ErrorCode;
import com.gameplay.customer_service.domain.CustomerConversation;
import com.gameplay.customer_service.service.CsMessageNotifier;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AiReplyTaskService {
    private final AiReplyTaskMapper taskMapper;
    private final CsMessageNotifier notifier;

    @Value("${app.ai.tasks.max-pending:100}")
    private int maxPending;
    @Value("${app.ai.tasks.requests-per-minute:12}")
    private int requestsPerMinute;

    // 调用方持有会话行锁，保证同一会话只能创建一个活跃任务。
    public AiReplyTask enqueue(CustomerConversation conversation, Long messageId, Long relatedOrderId) {
        AiReplyTask existing = forMessage(messageId);
        if (existing != null) {
            if (!existing.getRelatedOrderId().equals(relatedOrderId == null ? 0L : relatedOrderId)) {
                throw new BusinessException(ErrorCode.IDEMPOTENCY_KEY_CONFLICT);
            }
            notifyStatus(existing, conversation.getInitiatorUserId());
            return existing;
        }
        if (taskMapper.selectCount(active().eq(AiReplyTask::getConversationId, conversation.getId())) > 0) {
            throw new BusinessException(ErrorCode.AI_SERVICE_UNAVAILABLE, "上一条问题正在回答，请稍候再发送，或转接人工客服。");
        }
        if (taskMapper.selectCount(active()) >= Math.max(1, maxPending)) {
            throw new BusinessException(ErrorCode.AI_SERVICE_UNAVAILABLE, "AI 客服当前繁忙，请稍后重试或转接人工客服。");
        }
        if (taskMapper.countRecentForUser(conversation.getInitiatorUserId(), LocalDateTime.now().minusMinutes(1))
                >= Math.max(1, requestsPerMinute)) {
            throw new BusinessException(ErrorCode.AI_SERVICE_UNAVAILABLE, "提问过于频繁，请稍后重试或转接人工客服。");
        }
        AiReplyTask task = new AiReplyTask();
        task.setConversationId(conversation.getId());
        task.setUserMessageId(messageId);
        task.setRelatedOrderId(relatedOrderId == null ? 0L : relatedOrderId);
        task.setStatus("PENDING");
        task.setErrorCode("");
        task.setCreatedAt(LocalDateTime.now());
        task.setUpdatedAt(task.getCreatedAt());
        taskMapper.insert(task);
        notifyStatus(task, conversation.getInitiatorUserId());
        return task;
    }

    public AiReplyTask forMessage(Long messageId) {
        return taskMapper.selectOne(new LambdaQueryWrapper<AiReplyTask>()
                .eq(AiReplyTask::getUserMessageId, messageId));
    }

    public AiTaskView latest(Long conversationId) {
        return AiTaskView.from(taskMapper.selectOne(new LambdaQueryWrapper<AiReplyTask>()
                .eq(AiReplyTask::getConversationId, conversationId)
                .orderByDesc(AiReplyTask::getId).last("LIMIT 1")));
    }

    public void cancelActive(CustomerConversation conversation) {
        List<AiReplyTask> active = taskMapper.selectList(active()
                .eq(AiReplyTask::getConversationId, conversation.getId()));
        for (AiReplyTask task : active) {
            finish(task, "CANCELLED", "CONVERSATION_CHANGED");
            notifyStatus(task, conversation.getInitiatorUserId());
        }
    }

    public void finish(AiReplyTask task, String status, String errorCode) {
        task.setStatus(status);
        task.setErrorCode(errorCode == null ? "" : errorCode);
        task.setUpdatedAt(LocalDateTime.now());
        taskMapper.update(null, new LambdaUpdateWrapper<AiReplyTask>()
                .eq(AiReplyTask::getId, task.getId())
                .set(AiReplyTask::getStatus, task.getStatus())
                .set(AiReplyTask::getErrorCode, task.getErrorCode())
                .set(AiReplyTask::getUpdatedAt, task.getUpdatedAt()));
    }

    public void notifyStatus(AiReplyTask task, Long userId) {
        notifier.notifyAiStatus(AiTaskView.from(task), userId);
    }

    private LambdaQueryWrapper<AiReplyTask> active() {
        return new LambdaQueryWrapper<AiReplyTask>().in(AiReplyTask::getStatus, "PENDING", "RUNNING");
    }
}
