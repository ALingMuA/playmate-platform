package com.gameplay.customer_service.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 内部备注请求（FR-C27：仅客服/管理员可见）。
 */
@Data
public class InternalNoteRequest {

    @NotBlank(message = "备注内容不能为空")
    @Size(max = 2000, message = "备注内容过长")
    private String content;
}
