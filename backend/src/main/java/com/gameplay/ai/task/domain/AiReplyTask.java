package com.gameplay.ai.task.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("ai_reply_task")
public class AiReplyTask {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long conversationId;
    private Long userMessageId;
    private Long relatedOrderId;
    private String status;
    private String errorCode;
    private LocalDateTime startedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
