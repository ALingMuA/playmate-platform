package com.gameplay.companion.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gameplay.companion.domain.CompanionService;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 陪玩服务项目 Mapper（FR-P07~P09）。
 */
public interface CompanionServiceMapper extends BaseMapper<CompanionService> {

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
}
