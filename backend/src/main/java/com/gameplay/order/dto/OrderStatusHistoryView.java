package com.gameplay.order.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 订单状态历史视图（FR-U11 订单轨迹）。
 */
@Data
@Builder
public class OrderStatusHistoryView {

    private String fromStatus;
    private String toStatus;
    private String operatorRole;
    private String actionCode;
    private String reason;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;
}
