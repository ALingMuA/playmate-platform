package com.gameplay.announcement.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 平台公告表实体（对应 `announcement`，FR-A08 查看、FR-M13 管理）。
 */
@Data
@TableName("announcement")
public class Announcement {

    /** 主键ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 公告标题 */
    private String title;

    /** 公告内容 */
    private String content;

    /** 状态：DRAFT、PUBLISHED、REVOKED */
    private String publishStatus;

    /** 发布管理员ID */
    private Long publishedBy;

    /** 发布时间 */
    private LocalDateTime publishedAt;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;
}
