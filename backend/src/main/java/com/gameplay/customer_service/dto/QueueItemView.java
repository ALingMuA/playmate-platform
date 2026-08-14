package com.gameplay.customer_service.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 人工会话队列条目视图（FR-C24：主题、来源、优先级、排队时长）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QueueItemView {

    private Long conversationId;
    private String conversationNo;
    /** 会话主题（取最近一条用户消息摘要） */
    private String topic;
    private String sourceType;
    private Long relatedOrderId;
    private String transferReason;
    /** 排队时长（秒） */
    private Long queueSeconds;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;
}
