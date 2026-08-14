package com.gameplay.customer_service.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 客服内部备注表实体（对应 `internal_note`，FR-C27）。
 * <p>仅客服与管理员可见，用户不可见。</p>
 */
@Data
@TableName("internal_note")
public class InternalNote {

    /** 主键ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 会话ID */
    private Long conversationId;

    /** 客服或管理员用户ID */
    private Long authorUserId;

    /** 作者角色 */
    private String authorRole;

    /** 内部备注内容，用户不可见 */
    private String content;

    /** 创建时间 */
    private LocalDateTime createdAt;
}
