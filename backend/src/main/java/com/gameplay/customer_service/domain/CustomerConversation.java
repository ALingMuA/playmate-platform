package com.gameplay.customer_service.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 客服会话主表实体（对应 `customer_conversation`，FR-C07~C29）。
 * <p>状态流转以 {@code conversationStatus} 为准，乐观锁字段 {@code version} 防止并发领取。</p>
 */
@Data
@TableName("customer_conversation")
public class CustomerConversation {

    /** 主键ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 会话编号 */
    private String conversationNo;

    /** 发起用户或陪玩师ID */
    private Long initiatorUserId;

    /** 来源：HELP_CENTER、PROFILE、ORDER */
    private String sourceType;

    /** 关联订单ID，无关联为0 */
    private Long relatedOrderId;

    /** 会话状态枚举（AI_PROCESSING/WAITING_HUMAN/HUMAN_PROCESSING/ESCALATED_ADMIN/CLOSED） */
    private String conversationStatus;

    /** 接待模式：AI、HUMAN、ADMIN */
    private String receptionMode;

    /** 当前客服账号ID，无客服为0 */
    private Long currentCsAccountId;

    /** 转人工或转管理员原因 */
    private String transferReason;

    /** 连续未解决次数 */
    private Integer unresolvedCount;

    /** 关闭问题分类 */
    private String closedCategory;

    /** 关闭处理结果 */
    private String closedResult;

    /** 关闭时间 */
    private LocalDateTime closedAt;

    /** 乐观锁版本号 */
    private Integer version;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;
}
