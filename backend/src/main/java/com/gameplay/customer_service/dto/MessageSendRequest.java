package com.gameplay.customer_service.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 会话消息发送请求（FR-C08：文本消息，clientMsgId 幂等）。
 */
@Data
public class MessageSendRequest {

    /** 客户端消息幂等ID（UUID） */
    @NotBlank(message = "clientMsgId 不能为空")
    @Size(max = 64, message = "clientMsgId 过长")
    private String clientMsgId;

    /** 消息内容 */
    @NotBlank(message = "消息内容不能为空")
    @Size(max = 4000, message = "消息内容过长")
    private String content;

    /** 关联订单ID（选填，用于 AI 应答上下文，FR-C13） */
    @Positive(message = "订单ID不合法")
    private Long relatedOrderId;

    /** 是否请求转人工（FR-C10） */
    private Boolean requestHuman;
}
