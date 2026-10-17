package com.gameplay.companion.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 陪玩师已被订单占用的时段视图（用于周日程看板直观展示）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookedSlotView {

    private Long orderId;
    private String orderNo;
    private Long companionUserId;
    private Long customerUserId;
    private String customerNickname;
    private String serviceTitle;
    private String gameServer;
    private String gameNickname;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime startAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime endAt;

    /** 订单状态：WAITING_ACCEPTANCE, WAITING_SERVICE, IN_SERVICE 等 */
    private String orderStatus;
}
