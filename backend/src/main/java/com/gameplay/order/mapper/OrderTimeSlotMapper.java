package com.gameplay.order.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gameplay.order.domain.OrderTimeSlot;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 订单档期占用 Mapper（详细设计 7.1 档期并发锁）。
 */
public interface OrderTimeSlotMapper extends BaseMapper<OrderTimeSlot> {

    /**
     * 锁定与给定时段重叠的未释放占用记录（事务内 FOR UPDATE）。
     * <p>索引 idx_ots_conflict(companion_user_id, slot_status, start_at, end_at) 支撑范围查询。</p>
     */
    @Select("""
            SELECT id, order_id, companion_user_id, start_at, end_at, slot_status, expire_at
            FROM order_time_slot
            WHERE companion_user_id = #{companionUserId}
              AND slot_status IN ('TEMPORARY', 'EFFECTIVE')
              AND (slot_status = 'EFFECTIVE' OR expire_at IS NULL OR expire_at > #{now})
              AND start_at < #{endAt}
              AND end_at > #{startAt}
            FOR UPDATE
            """)
    List<OrderTimeSlot> selectConflictSlotsForUpdate(@Param("companionUserId") Long companionUserId,
                                                     @Param("startAt") LocalDateTime startAt,
                                                     @Param("endAt") LocalDateTime endAt,
                                                     @Param("now") LocalDateTime now);

    /** 只读查询与给定时段重叠的未释放占用记录（不加锁，供公开可约时段扣减） */
    @Select("""
            SELECT id, order_id, companion_user_id, start_at, end_at, slot_status, expire_at
            FROM order_time_slot
            WHERE companion_user_id = #{companionUserId}
              AND slot_status IN ('TEMPORARY', 'EFFECTIVE')
              AND (slot_status = 'EFFECTIVE' OR expire_at IS NULL OR expire_at > #{now})
              AND start_at < #{endAt}
              AND end_at > #{startAt}
            """)
    List<OrderTimeSlot> selectActiveSlots(@Param("companionUserId") Long companionUserId,
                                          @Param("startAt") LocalDateTime startAt,
                                          @Param("endAt") LocalDateTime endAt,
                                          @Param("now") LocalDateTime now);

    /** 按订单更新占用状态（转为 EFFECTIVE 时清空临时过期时间 expire_at） */
    @Update("""
            UPDATE order_time_slot
            SET slot_status = #{status},
                expire_at = CASE WHEN #{status} = 'EFFECTIVE' THEN NULL ELSE expire_at END,
                updated_at = NOW()
            WHERE order_id = #{orderId} AND slot_status = #{fromStatus}
            """)
    int updateStatusByOrder(@Param("orderId") Long orderId,
                            @Param("fromStatus") String fromStatus,
                            @Param("status") String status);
}
