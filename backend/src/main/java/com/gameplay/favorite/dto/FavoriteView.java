package com.gameplay.favorite.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 收藏视图（FR-U06 收藏列表）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FavoriteView {

    private Long companionUserId;
    private String displayName;
    private String profileIntro;
    private BigDecimal ratingAvg;
    private Integer ratingCount;
    private String serviceStatus;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;
}
