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
              AND (expire_at IS NULL OR expire_at > NOW())
              AND start_at < #{endAt}
              AND end_at > #{startAt}
            FOR UPDATE
            """)
    List<OrderTimeSlot> selectConflictSlotsForUpdate(@Param("companionUserId") Long companionUserId,
                                                     @Param("startAt") LocalDateTime startAt,
                                                     @Param("endAt") LocalDateTime endAt);

    /** 按订单更新占用状态 */
    @Update("UPDATE order_time_slot SET slot_status = #{status}, updated_at = NOW() WHERE order_id = #{orderId} AND slot_status = #{fromStatus}")
    int updateStatusByOrder(@Param("orderId") Long orderId,
                            @Param("fromStatus") String fromStatus,
                            @Param("status") String status);
}
