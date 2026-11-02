package com.gameplay.order.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 陪玩师履约凭证。 */
@Data
@TableName("order_delivery_evidence")
public class OrderDeliveryEvidence {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long orderId;
    private Long companionUserId;
    private String fileUrl;
    private String fileName;
    private Integer sortNo;
    private LocalDateTime createdAt;
}
