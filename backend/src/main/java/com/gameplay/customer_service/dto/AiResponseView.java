package com.gameplay.customer_service.dto;

import com.gameplay.ai.task.dto.AiTaskView;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * AI 应答结果视图（详细设计 2.6 响应结构）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiResponseView {

    private Long conversationId;
    private String conversationStatus;
    /** CONTINUE_AI、TRANSFER_HUMAN、FALLBACK_FAQ */
    private String decision;
    private Long userMessageId;
    private MessageView aiMessage;
    private String transferReason;
    private AiTaskView task;
}
