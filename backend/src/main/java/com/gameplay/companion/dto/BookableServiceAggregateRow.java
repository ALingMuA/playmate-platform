package com.gameplay.companion.dto;

import lombok.Data;

/**
 * 按陪玩师聚合的公开可预约服务统计行（Mapper 映射用）。
 *
 * <p>口径：审核通过（APPROVED）+ 已上架（ON_SHELF）+ 未删除 + 所属游戏启用，
 * 与公开服务列表 {@code CompanionServiceService#listBookable} 保持一致；
 * 可选按 gameId 收窄，用于"游戏维度"下的陪玩师筛选。</p>
 */
@Data
public class BookableServiceAggregateRow {

    private Long companionUserId;

    /** 该口径下最低每小时价格（分） */
    private Long startingPriceCents;

    /** 该口径下在架服务数量 */
    private Long serviceCount;

    /** 该口径下在架服务覆盖的游戏名，逗号分隔（GROUP_CONCAT 产物） */
    private String gameNames;
}
