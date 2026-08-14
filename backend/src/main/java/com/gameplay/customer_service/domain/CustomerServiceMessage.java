package com.gameplay.customer_service.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 客服文本消息表实体（对应 `customer_service_message`，FR-C08）。
 * <p>clientMsgId 全局唯一（uk_csm_client_msg_id），用于消息幂等去重。</p>
 */
@Data
@TableName("customer_service_message")
public class CustomerServiceMessage {

    /** 主键ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 会话ID */
    private Long conversationId;

    /** 客户端消息幂等ID */
    private String clientMsgId;

    /** 发送者：USER、AI、CS、SYSTEM、ADMIN */
    private String senderType;

    /** 发送者ID，AI或系统为0 */
    private Long senderId;

    /** 内容类型，首期仅TEXT */
    private String contentType;

    /** 文本消息内容 */
    private String content;

    /** AI标识：0否，1是 */
    private Integer aiMark;

    /** 已读状态：0否，1是 */
    private Integer readStatus;

    /** 创建时间 */
    private LocalDateTime createdAt;
}
