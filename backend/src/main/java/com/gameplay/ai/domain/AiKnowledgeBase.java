package com.gameplay.ai.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI 客服知识库表实体（对应 `ai_knowledge_base`，FR-C16/C17/C23）。
 * <p>管理员维护的常见问题与标准答案，AI 优先检索命中内容回答。</p>
 */
@Data
@TableName("ai_knowledge_base")
public class AiKnowledgeBase {

    /** 主键ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 知识分类 */
    private String category;

    /** 问题标题 */
    private String title;

    /** 逗号分隔关键词 */
    private String keywords;

    /** 标准答案模板 */
    private String standardAnswer;

    /** 匹配优先级，越大越优先 */
    private Integer priority;

    /** 启用状态：0否，1是 */
    private Integer enabled;

    /** 维护管理员ID */
    private Long maintainedBy;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;
}
