package com.gameplay.order.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gameplay.common.enums.OrderStatus;
import com.gameplay.order.domain.PlayOrder;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 陪玩订单 Mapper。
 * <p>状态更新必须通过条件更新（updateStatusIfCurrent）防并发，
 * 不允许直接 UPDATE play_order SET order_status。</p>
 */
public interface PlayOrderMapper extends BaseMapper<PlayOrder> {

    /** 行锁读取订单（支付/接单等写操作入口） */
    @Select("SELECT * FROM play_order WHERE id = #{id} FOR UPDATE")
    PlayOrder selectByIdForUpdate(@Param("id") Long id);

    /** 仅当订单处于期望状态时迁移状态（乐观条件更新，返回受影响行数） */
    @Update("""
            UPDATE play_order
            SET order_status = #{toStatus},
                version = version + 1,
                updated_at = NOW()
            WHERE id = #{id} AND order_status = #{fromStatus}
            """)
    int updateStatusIfCurrent(@Param("id") Long id,
                              @Param("fromStatus") String fromStatus,
                              @Param("toStatus") String toStatus);

    /** 支付超时待关闭订单（详细设计 7.2） */
    @Select("SELECT * FROM play_order WHERE order_status = 'PENDING_PAYMENT' AND pay_expire_at <= #{now} LIMIT #{limit}")
    List<PlayOrder> findExpiredPendingPayments(@Param("now") LocalDateTime now, @Param("limit") int limit);

    /** 接单超时待关闭订单（详细设计 7.2） */
    @Select("SELECT * FROM play_order WHERE order_status = 'WAITING_ACCEPTANCE' AND accept_expire_at <= #{now} LIMIT #{limit}")
    List<PlayOrder> findExpiredWaitingAcceptances(@Param("now") LocalDateTime now, @Param("limit") int limit);

    /** 待自动确认完成的订单：结束后满24小时（详细设计 7.2） */
    @Select("SELECT * FROM play_order WHERE order_status = 'WAITING_CONFIRMATION' AND ended_at <= #{deadline} LIMIT #{limit}")
    List<PlayOrder> findNeedAutoConfirm(@Param("deadline") LocalDateTime deadline, @Param("limit") int limit);
}
