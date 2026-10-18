package com.gameplay.companion.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 陪玩师公开主页资料白名单。
 *
 * <p>certificationStatus 表示平台陪玩资格状态，不代表 capabilities 中的每项能力经过单独核验。</p>
 */
@Data
@Builder
public class PublicCompanionProfileView {

    private Long userId;
    private String displayName;
    private String avatarUrl;
    private String profileIntro;
    private String serviceStatus;
    private BigDecimal ratingAvg;
    private Integer ratingCount;
    private Integer completedOrderCount;
    private List<GameCapabilityDto> capabilities;
    private String certificationStatus;
}
