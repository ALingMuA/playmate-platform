package com.gameplay.customer_service.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 关闭会话请求（FR-C29：处理分类 + 处理结果）。
 */
@Data
public class CloseConversationRequest {

    /** 关闭问题分类 */
    @NotBlank(message = "问题分类不能为空")
    @Size(max = 50, message = "问题分类过长")
    private String category;

    /** 处理结果 */
    @NotBlank(message = "处理结果不能为空")
    @Size(max = 1000, message = "处理结果过长")
    private String result;
}
