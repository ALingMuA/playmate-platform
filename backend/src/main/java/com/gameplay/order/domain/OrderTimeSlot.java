package com.gameplay.order.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 订单档期占用表实体（对应 `order_time_slot`，详细设计 7.1）。
 */
@Data
@TableName("order_time_slot")
public class OrderTimeSlot {

    /** 主键ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 订单ID */
    private Long orderId;

    /** 陪玩师用户ID */
    private Long companionUserId;

    /** 占用开始时间 */
    private LocalDateTime startAt;

    /** 占用结束时间 */
    private LocalDateTime endAt;

    /** 占用状态：TEMPORARY、EFFECTIVE、RELEASED */
    private String slotStatus;

    /** 临时占用过期时间 */
    private LocalDateTime expireAt;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;
}
