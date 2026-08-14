package com.gameplay.review.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 订单投诉表实体（对应 `complaint`，FR-U17~U19、FR-M18/M19）。
 * <p>投诉必须关联订单，发起后订单进入售后中；处理结果由管理员仲裁产生。</p>
 */
@Data
@TableName("complaint")
public class Complaint {

    /** 主键ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 订单ID */
    private Long orderId;

    /** 投诉发起人ID */
    private Long complainantUserId;

    /** 投诉类型 */
    private String complaintType;

    /** 投诉说明 */
    private String description;

    /** 状态：PENDING、PROCESSING、RESOLVED */
    private String complaintStatus;

    /** 处理类型：KEEP、FULL_REFUND、PARTIAL_REFUND */
    private String resolutionType;

    /** 退款金额，单位分 */
    private Long refundAmountCents;

    /** 处理管理员ID */
    private Long handledBy;

    /** 处理意见 */
    private String handlingOpinion;

    /** 处理时间 */
    private LocalDateTime handledAt;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;
}
