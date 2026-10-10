package com.gameplay.activity.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 平台运营活动实体（对应 platform_activity 表）。
 */
@Data
@TableName("platform_activity")
public class PlatformActivity {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 活动标题 */
    private String title;

    /** 副标题 */
    private String subtitle;

    /** 描述说明 */
    private String description;

    /** 横幅图片 */
    private String bannerImage;

    /** 背景渐变色，例如 linear-gradient(135deg, #667eea 0%, #764ba2 100%) */
    private String bgGradient;

    /** 图标或 emoji */
    private String icon;

    /** 活动标签，例如 限时活动 */
    private String tag;

    /** 标签颜色 */
    private String tagColor;

    /** 动作类型：COUPON_CLAIM(领券), COMPANION_LIST(找陪玩), PAGE_NAV(页面跳转) */
    private String actionType;

    /** 跳转路径或路由 */
    private String actionRoute;

    /** 关联的优惠券模板ID（当 actionType 为 COUPON_CLAIM 时） */
    private Long couponTemplateId;

    /** 排序权重，越小越靠前 */
    private Integer sortOrder;

    /** 状态：DRAFT, PUBLISHED, OFFLINE */
    private String status;

    /** 活动生效开始时间 */
    private LocalDateTime startTime;

    /** 活动生效结束时间 */
    private LocalDateTime endTime;

    /** 创建管理员ID */
    private Long createdBy;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;
}
