package com.gameplay.customer_service.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 客服会话视图。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConversationView {

    private Long id;
    private String conversationNo;
    private Long initiatorUserId;
    private String sourceType;
    private Long relatedOrderId;
    private String conversationStatus;
    private String receptionMode;
    private Long currentCsAccountId;
    private String transferReason;
    private Integer unresolvedCount;
    /** 乐观锁版本号（领取时提交） */
    private Integer version;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime closedAt;
}
