package com.gameplay.companion.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 陪玩师公开列表分页查询行（Mapper 映射用）。
 *
 * <p>仅承载 companion_profile 与 user 联查的静态资料列；
 * 服务数、起价、游戏名等聚合字段由服务层二次查询填充到
 * {@link PublicCompanionCardView}。</p>
 */
@Data
public class PublicCompanionCardRow {

    private Long userId;
    private String displayName;
    private String avatarUrl;
    private String serviceStatus;
    private BigDecimal ratingAvg;
    private Integer ratingCount;
    private Integer completedOrderCount;
}
