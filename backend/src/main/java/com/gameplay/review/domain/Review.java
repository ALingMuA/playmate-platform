package com.gameplay.review.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 订单评价表实体（对应 `review`，FR-U15/U16）。
 * <p>每个订单最多一条评价（uk_review_order），展示状态由管理员控制。</p>
 */
@Data
@TableName("review")
public class Review {

    /** 主键ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 订单ID */
    private Long orderId;

    /** 评价用户ID */
    private Long userId;

    /** 被评价陪玩师ID */
    private Long companionUserId;

    /** 评分：1至5 */
    private Integer score;

    /** 评价标签JSON数组 */
    private String tagJson;

    /** 评价内容 */
    private String content;

    /** 展示状态：VISIBLE、HIDDEN */
    private String displayStatus;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;
}
