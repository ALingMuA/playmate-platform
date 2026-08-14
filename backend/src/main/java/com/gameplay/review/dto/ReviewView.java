package com.gameplay.review.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 评价视图（FR-U15/U16、FR-M08）。
 */
@Data
@Builder
public class ReviewView {

    /** 评价ID */
    private Long id;

    /** 订单ID */
    private Long orderId;

    /** 评价用户ID */
    private Long userId;

    /** 评价用户昵称 */
    private String userNickname;

    /** 被评价陪玩师ID */
    private Long companionUserId;

    /** 评分 */
    private Integer score;

    /** 评价标签 */
    private List<String> tags;

    /** 评价内容 */
    private String content;

    /** 展示状态：VISIBLE、HIDDEN */
    private String displayStatus;

    /** 创建时间 */
    private LocalDateTime createdAt;
}
