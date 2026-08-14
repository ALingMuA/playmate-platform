package com.gameplay.audit.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 操作日志视图。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OperationLogView {

    private Long id;
    private Long operatorId;
    private String operatorRole;
    private String operationType;
    private String targetType;
    private Long targetId;
    private String beforeData;
    private String afterData;
    private String reason;
    private String requestId;
    private String ipAddress;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;
}
