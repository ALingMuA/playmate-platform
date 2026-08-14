package com.gameplay.order.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.gameplay.common.enums.OrderStatus;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 陪玩订单主表实体（对应 `play_order`）。
 * <p>状态迁移必须经由 {@code OrderService.transition}，禁止直接修改 orderStatus。</p>
 */
@Data
@TableName("play_order")
public class PlayOrder {

    /** 主键ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 业务订单号 */
    private String orderNo;

    /** 下单用户ID */
    private Long userId;

    /** 陪玩师用户ID */
    private Long companionUserId;

    /** 服务项目ID */
    private Long companionServiceId;

    /** 游戏ID快照 */
    private Long gameId;

    /** 服务标题快照 */
    private String serviceTitleSnapshot;

    /** 服务类型快照 */
    private String serviceTypeNameSnapshot;

    /** 陪玩师昵称快照 */
    private String companionNameSnapshot;

    /** 小时单价快照，单位分 */
    private Long unitPriceCents;

    /** 预约时长，单位分钟 */
    private Integer durationMinutes;

    /** 订单总金额，单位分 */
    private Long totalAmountCents;

    /** 游戏区服 */
    private String gameServer;

    /** 游戏昵称 */
    private String gameNickname;

    /** 用户需求备注 */
    private String userRemark;

    /** 预约开始时间 */
    private LocalDateTime appointmentStartAt;

    /** 预约结束时间 */
    private LocalDateTime appointmentEndAt;

    /** 订单状态枚举 */
    private String orderStatus;

    /** 支付截止时间 */
    private LocalDateTime payExpireAt;

    /** 接单截止时间 */
    private LocalDateTime acceptExpireAt;

    /** 实际开始时间 */
    private LocalDateTime startedAt;

    /** 实际结束时间 */
    private LocalDateTime endedAt;

    /** 确认完成时间 */
    private LocalDateTime confirmedAt;

    /** 关闭或取消原因 */
    private String closedReason;

    /** 乐观锁版本号 */
    private Integer version;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;

    /** 当前状态（枚举视图） */
    public OrderStatus status() {
        return OrderStatus.valueOf(orderStatus);
    }
}
