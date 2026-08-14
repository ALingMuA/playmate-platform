package com.gameplay.notification.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 站内通知表实体（对应 `notification`，FR-A07）。
 */
@Data
@TableName("notification")
public class Notification {

    /** 主键ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 接收用户ID */
    private Long userId;

    /** 通知类型 */
    private String notificationType;

    /** 通知标题 */
    private String title;

    /** 通知内容 */
    private String content;

    /** 关联业务类型 */
    private String relatedType;

    /** 关联业务ID */
    private Long relatedId;

    /** 已读状态：0否，1是 */
    private Integer readStatus;

    /** 读取时间 */
    private LocalDateTime readAt;

    /** 创建时间 */
    private LocalDateTime createdAt;
}
