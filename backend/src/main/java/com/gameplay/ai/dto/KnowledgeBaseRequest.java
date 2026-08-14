package com.gameplay.ai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 知识库新增/编辑请求（FR-C23）。
 */
@Data
public class KnowledgeBaseRequest {

    /** 知识分类 */
    @NotBlank(message = "知识分类不能为空")
    @Size(max = 50, message = "知识分类过长")
    private String category;

    /** 问题标题 */
    @NotBlank(message = "问题标题不能为空")
    @Size(max = 100, message = "问题标题过长")
    private String title;

    /** 逗号分隔关键词 */
    @NotBlank(message = "关键词不能为空")
    @Size(max = 1000, message = "关键词过长")
    private String keywords;

    /** 标准答案模板 */
    @NotBlank(message = "标准答案不能为空")
    @Size(max = 4000, message = "标准答案过长")
    private String standardAnswer;

    /** 匹配优先级，越大越优先 */
    private Integer priority;
}
