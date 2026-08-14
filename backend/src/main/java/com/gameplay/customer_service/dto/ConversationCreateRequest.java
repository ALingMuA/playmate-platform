package com.gameplay.customer_service.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 发起客服会话请求（FR-C07：帮助中心/个人中心/订单详情）。
 */
@Data
public class ConversationCreateRequest {

    /** 来源：HELP_CENTER、PROFILE、ORDER */
    @NotBlank(message = "会话来源不能为空")
    @Pattern(regexp = "HELP_CENTER|PROFILE|ORDER", message = "会话来源不合法")
    private String sourceType;

    /** 关联订单ID（来源为 ORDER 时必填） */
    @Positive(message = "订单ID不合法")
    private Long relatedOrderId;

    /** 首条消息内容 */
    @Size(max = 4000, message = "消息内容过长")
    private String firstContent;
}
