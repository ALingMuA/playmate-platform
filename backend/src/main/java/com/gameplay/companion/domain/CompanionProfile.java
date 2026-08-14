package com.gameplay.companion.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 陪玩师资料表实体（对应 `companion_profile`，FR-P05/P06）。
 */
@Data
@TableName("companion_profile")
public class CompanionProfile {

    /** 主键ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 关联用户ID */
    private Long userId;

    /** 陪玩师展示名 */
    private String displayName;

    /** 主页简介 */
    private String profileIntro;

    /** 认证游戏能力JSON */
    private String capabilityJson;

    /** 有效评价平均分 */
    private BigDecimal ratingAvg;

    /** 有效评价数量 */
    private Integer ratingCount;

    /** 已完成订单数 */
    private Integer completedOrderCount;

    /** 接单状态：AVAILABLE、BUSY、RESTING、SUSPENDED */
    private String serviceStatus;

    /** 认证状态：APPROVED、SUSPENDED */
    private String certificationStatus;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;
}
