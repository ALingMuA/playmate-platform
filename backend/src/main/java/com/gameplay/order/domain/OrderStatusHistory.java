package com.gameplay.order.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 订单状态历史表实体（对应 `order_status_history`，FR-U11 订单轨迹）。
 */
@Data
@TableName("order_status_history")
public class OrderStatusHistory {

    /** 主键ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 订单ID */
    private Long orderId;

    /** 迁移前状态，创建时为空字符串 */
    private String fromStatus;

    /** 迁移后状态 */
    private String toStatus;

    /** 操作者ID，系统任务为0 */
    private Long operatorId;

    /** 操作者角色 */
    private String operatorRole;

    /** 动作编码 */
    private String actionCode;

    /** 原因说明 */
    private String reason;

    /** 创建时间 */
    private LocalDateTime createdAt;
}
