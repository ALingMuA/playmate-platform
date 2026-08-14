package com.gameplay.customer_service.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 客服满意度评价请求（FR-C15：每个会话最多评价一次）。
 */
@Data
public class EvaluationRequest {

    /** 满意度评分：1至5 */
    @NotNull(message = "评分不能为空")
    @Min(value = 1, message = "评分须为1~5")
    @Max(value = 5, message = "评分须为1~5")
    private Integer score;

    /** 评价内容 */
    @Size(max = 1000, message = "评价内容过长")
    private String content;
}
