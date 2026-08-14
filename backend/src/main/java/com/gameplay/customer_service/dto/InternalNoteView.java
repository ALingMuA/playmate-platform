package com.gameplay.customer_service.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 内部备注视图。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InternalNoteView {

    private Long id;
    private Long conversationId;
    private Long authorUserId;
    private String authorRole;
    private String content;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;
}
