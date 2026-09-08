package com.gameplay.ai.task.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.gameplay.ai.task.domain.AiReplyTask;

import java.time.LocalDateTime;

public record AiTaskView(Long id, Long conversationId, Long userMessageId, String status,
                         String errorCode,
                         @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime createdAt,
                         @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime updatedAt) {
    public static AiTaskView from(AiReplyTask task) {
        return task == null ? null : new AiTaskView(task.getId(), task.getConversationId(),
                task.getUserMessageId(), task.getStatus(), task.getErrorCode(),
                task.getCreatedAt(), task.getUpdatedAt());
    }
}
