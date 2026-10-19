package com.gameplay.companion.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.gameplay.companion.domain.CompanionProfile;
import com.gameplay.companion.dto.PublicCompanionCardRow;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 陪玩师资料 Mapper（FR-P05/P06）。
 */
public interface CompanionProfileMapper extends BaseMapper<CompanionProfile> {

    /**
     * 公开陪玩师列表分页（FR-U02：用户端按陪玩师维度浏览）。
     *
     * <p>可见性条件与 {@code getPublicProfile} 一致：认证通过 + 账号启用；
     * 另要求存在至少一条在架可预约服务（EXISTS），避免列表出现无法预约的空卡片。
     * 按游戏筛选时，EXISTS 限定该游戏下的在架服务，走 idx_cs_list 索引。</p>
     */
    @Select("""
            <script>
            SELECT cp.user_id AS userId,
                   cp.display_name AS displayName,
                   u.avatar_url AS avatarUrl,
                   cp.service_status AS serviceStatus,
                   cp.rating_avg AS ratingAvg,
                   cp.rating_count AS ratingCount,
                   cp.completed_order_count AS completedOrderCount
            FROM companion_profile cp
            INNER JOIN `user` u ON u.id = cp.user_id
                AND u.deleted = 0
                AND u.account_status = 'ENABLED'
            WHERE cp.certification_status = 'APPROVED'
              AND cp.service_status != 'SUSPENDED'
              AND EXISTS (
                  SELECT 1
                  FROM companion_service cs
                  INNER JOIN game g ON g.id = cs.game_id AND g.enabled = 1
                  WHERE cs.companion_user_id = cp.user_id
                    AND cs.audit_status = 'APPROVED'
                    AND cs.service_status = 'ON_SHELF'
                    AND cs.deleted = 0
                    <if test="gameId != null">
                    AND cs.game_id = #{gameId}
                    </if>
              )
            ORDER BY cp.rating_avg DESC, cp.completed_order_count DESC, cp.user_id ASC
            </script>
            """)
    Page<PublicCompanionCardRow> selectPublicCards(Page<PublicCompanionCardRow> page, @Param("gameId") Long gameId);
}
