package com.gameplay.review.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gameplay.review.domain.Review;
import lombok.Data;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;

/**
 * 订单评价 Mapper。
 */
public interface ReviewMapper extends BaseMapper<Review> {

    /** 陪玩师有效评价聚合：平均分与数量（仅统计 VISIBLE，FR-U16） */
    @Select("""
            SELECT COALESCE(ROUND(AVG(score), 1), 0) AS avg_score,
                   COUNT(*) AS cnt
            FROM review
            WHERE companion_user_id = #{companionUserId}
              AND display_status = 'VISIBLE'
            """)
    ScoreAggregate aggregateVisibleByCompanion(@Param("companionUserId") Long companionUserId);

    /** 评分聚合结果（MyBatis 结果映射需具体类，不能用接口） */
    @Data
    class ScoreAggregate {
        /** 平均分 */
        private BigDecimal avgScore;
        /** 数量 */
        private Long cnt;
    }
}
