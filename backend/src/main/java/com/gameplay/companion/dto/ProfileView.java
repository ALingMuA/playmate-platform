package com.gameplay.companion.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 陪玩师主页视图（FR-P05/P06，含接单状态）。
 */
@Data
@Builder
public class ProfileView {

    private Long id;
    private Long userId;

    /** 展示名 */
    private String displayName;

    /** 主页简介 */
    private String profileIntro;

    /** 认证游戏能力列表 */
    private List<GameCapabilityDto> capabilities;

    /** 有效评价平均分 */
    private BigDecimal ratingAvg;

    /** 有效评价数量 */
    private Integer ratingCount;

    /** 已完成订单数 */
    private Integer completedOrderCount;

    /** 接单状态：AVAILABLE、BUSY、RESTING、SUSPENDED */
    private String serviceStatus;

    /** 认证状态：APPROVED、SUSPENDED */
    private String certificationStatus;

    /** 更新时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedAt;
}
