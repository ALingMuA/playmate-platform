package com.gameplay.companion.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gameplay.companion.domain.CompanionService;
import com.gameplay.companion.dto.BookableServiceAggregateRow;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Collection;
import java.util.List;

/**
 * 陪玩服务项目 Mapper（FR-P07~P09）。
 */
public interface CompanionServiceMapper extends BaseMapper<CompanionService> {

    @Select("SELECT * FROM companion_service WHERE id = #{id} AND deleted = 0 FOR UPDATE")
    CompanionService selectByIdForUpdate(@Param("id") Long id);

    /** 统计陪玩师当前公开可预约服务的最低价格。 */
    @Select("""
            SELECT cs.price_cents AS starting_price_cents
            FROM companion_service cs
            INNER JOIN game g ON g.id = cs.game_id
            WHERE cs.companion_user_id = #{companionUserId}
              AND cs.audit_status = 'APPROVED'
              AND cs.service_status = 'ON_SHELF'
              AND cs.deleted = 0
              AND g.enabled = 1
            ORDER BY cs.price_cents ASC
            LIMIT 1
            """)
    Long selectStartingPriceCents(@Param("companionUserId") Long companionUserId);

    /**
     * 批量聚合一批陪玩师在当前筛选口径下的在架服务统计（FR-U02 陪玩师列表卡片）。
     *
     * <p>口径与 {@code listBookable} 一致；userIds 为空时由调用方跳过，
     * 避免拼出非法的 IN () 子句。</p>
     */
    @Select("""
            <script>
            SELECT cs.companion_user_id AS companionUserId,
                   MIN(cs.price_cents) AS startingPriceCents,
                   COUNT(*) AS serviceCount,
                   GROUP_CONCAT(DISTINCT g.game_name ORDER BY g.game_name SEPARATOR ',') AS gameNames
            FROM companion_service cs
            INNER JOIN game g ON g.id = cs.game_id AND g.enabled = 1
            WHERE cs.audit_status = 'APPROVED'
              AND cs.service_status = 'ON_SHELF'
              AND cs.deleted = 0
              <if test="gameId != null">
              AND cs.game_id = #{gameId}
              </if>
              AND cs.companion_user_id IN
              <foreach collection="userIds" item="id" open="(" separator="," close=")">#{id}</foreach>
            GROUP BY cs.companion_user_id
            </script>
            """)
    List<BookableServiceAggregateRow> aggregateBookableByUserIds(@Param("userIds") Collection<Long> userIds,
                                                                 @Param("gameId") Long gameId);
}
