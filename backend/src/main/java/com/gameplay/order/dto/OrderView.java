package com.gameplay.order.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 订单视图（FR-U10/U11、FR-P18）。
 */
@Data
@Builder
public class OrderView {

    private Long id;
    private String orderNo;
    private Long userId;
    private String userNickname;
    private Long companionUserId;
    private String companionNameSnapshot;
    private Long companionServiceId;
    private String serviceTitleSnapshot;
    private Long gameId;
    private String serviceTypeNameSnapshot;
    private Long unitPriceCents;
    private Integer durationMinutes;
    private Long totalAmountCents;
    private String gameServer;
    private String gameNickname;
    private String userRemark;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime appointmentStartAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime appointmentEndAt;

    /** 订单状态：PENDING_PAYMENT、WAITING_ACCEPTANCE、WAITING_SERVICE、IN_SERVICE、WAITING_CONFIRMATION、COMPLETED、CLOSED */
    private String orderStatus;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime payExpireAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime acceptExpireAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime startedAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime endedAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime confirmedAt;

    private String closedReason;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    /** 状态轨迹（详情时返回） */
    private List<OrderStatusHistoryView> statusHistories;
}
