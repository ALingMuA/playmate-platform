package com.gameplay.customer_service.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 客服消息视图。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MessageView {

    private Long messageId;
    private String clientMsgId;
    private Long conversationId;
    private String senderType;
    private Long senderId;
    private String contentType;
    private String content;
    /** AI标识：0否，1是 */
    private Integer aiMark;
    private Integer readStatus;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;
}
