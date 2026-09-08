package com.gameplay.ai.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * AI 客服调用日志表实体（对应 `ai_call_log`，FR-C22 审计）。
 */
@Data
@TableName("ai_call_log")
public class AiCallLog {

    /** 主键ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 会话ID */
    private Long conversationId;

    /** 命中知识库ID，未命中为0 */
    private Long knowledgeBaseId;

    /** 提供方：KNOWLEDGE_BASE、OLLAMA、EXTERNAL */
    private String provider;

    /** 脱敏请求摘要 */
    private String requestSummary;

    /** 脱敏响应摘要 */
    private String responseSummary;

    /** 置信度，0至1 */
    private BigDecimal confidence;

    /** 决策：CONTINUE_AI、TRANSFER_HUMAN、FALLBACK_FAQ */
    private String decision;

    /** 异常摘要 */
    private String errorMessage;

    /** 调用耗时毫秒 */
    private Integer elapsedMs;

    private String modelName;

    private Integer inputTokens;

    private Integer outputTokens;

    private String errorCode;

    /** 创建时间 */
    private LocalDateTime createdAt;
}
