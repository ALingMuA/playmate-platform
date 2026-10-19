package com.gameplay.companion.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 陪玩师公开列表卡片（FR-U02：用户端按陪玩师维度浏览）。
 *
 * <p>仅包含游客可见字段。列表只收录"存在在架可预约服务"的陪玩师，
 * 因此 serviceCount 恒大于等于 1；服务数、起价与游戏名的聚合口径
 * 随查询携带的 gameId 而定（筛选某游戏时统计该游戏下的服务）。</p>
 */
@Data
@Builder
public class PublicCompanionCardView {

    private Long userId;
    private String displayName;
    private String avatarUrl;

    /** 接单状态：AVAILABLE、BUSY、RESTING */
    private String serviceStatus;

    /** 当前筛选口径下的在架服务数量 */
    private Integer serviceCount;

    /** 当前筛选口径下最低服务价格（分） */
    private Long startingPriceCents;

    private BigDecimal ratingAvg;
    private Integer ratingCount;
    private Integer completedOrderCount;

    /** 当前筛选口径下在架服务覆盖的游戏名（去重） */
    private List<String> gameNames;
}
