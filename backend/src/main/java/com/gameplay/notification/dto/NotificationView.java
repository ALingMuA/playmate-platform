package com.gameplay.notification.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 站内通知视图。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationView {

    private Long id;
    private String notificationType;
    private String title;
    private String content;
    private String relatedType;
    private Long relatedId;
    /** 已读状态：0否，1是 */
    private Integer readStatus;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime readAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;
}
