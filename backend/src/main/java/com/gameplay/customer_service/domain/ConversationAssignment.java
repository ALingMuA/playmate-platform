package com.gameplay.customer_service.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 客服会话分配轨迹表实体（对应 `conversation_assignment`）。
 */
@Data
@TableName("conversation_assignment")
public class ConversationAssignment {

    /** 主键ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 会话ID */
    private Long conversationId;

    /** 类型：AI_TRANSFER、CLAIM、ASSIGN、TRANSFER、ESCALATE_ADMIN */
    private String assignmentType;

    /** 原客服账号ID */
    private Long fromCsAccountId;

    /** 目标客服账号ID */
    private Long toCsAccountId;

    /** 操作人ID，系统为0 */
    private Long operatorId;

    /** 分配或转交原因 */
    private String reason;

    /** 创建时间 */
    private LocalDateTime createdAt;
}
