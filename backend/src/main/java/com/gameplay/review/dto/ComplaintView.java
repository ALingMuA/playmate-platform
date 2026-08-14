package com.gameplay.review.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 投诉视图（FR-U19、FR-M18）。
 */
@Data
@Builder
public class ComplaintView {

    /** 投诉ID */
    private Long id;

    /** 订单ID */
    private Long orderId;

    /** 订单号 */
    private String orderNo;

    /** 服务标题快照 */
    private String serviceTitleSnapshot;

    /** 订单总金额（分） */
    private Long orderTotalAmountCents;

    /** 投诉发起人ID */
    private Long complainantUserId;

    /** 投诉发起人昵称 */
    private String complainantNickname;

    /** 被投诉陪玩师ID */
    private Long companionUserId;

    /** 被投诉陪玩师昵称 */
    private String companionNickname;

    /** 投诉类型 */
    private String complaintType;

    /** 投诉说明 */
    private String description;

    /** 状态：PENDING、PROCESSING、RESOLVED */
    private String complaintStatus;

    /** 处理类型：KEEP、FULL_REFUND、PARTIAL_REFUND */
    private String resolutionType;

    /** 退款金额（分） */
    private Long refundAmountCents;

    /** 处理管理员ID */
    private Long handledBy;

    /** 处理意见 */
    private String handlingOpinion;

    /** 处理时间 */
    private LocalDateTime handledAt;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 证据列表 */
    private List<ComplaintEvidenceView> evidences;
}
