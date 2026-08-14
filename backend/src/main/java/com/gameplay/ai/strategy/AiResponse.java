package com.gameplay.ai.strategy;

/**
 * AI 应答结果（详细设计 3.3）。
 *
 * @param content    应答内容（可能为空，表示无可靠回答）
 * @param confidence 置信度 0~1
 * @param provider   应答提供方编码
 * @param knowledgeBaseId 命中知识库ID，未命中为0
 * @param fallback   是否降级回答
 */
public record AiResponse(
        String content,
        double confidence,
        String provider,
        Long knowledgeBaseId,
        boolean fallback) {

    /** 成功回答 */
    public static AiResponse success(String content, double confidence, String provider, Long knowledgeBaseId) {
        return new AiResponse(content, confidence, provider, knowledgeBaseId, false);
    }

    /** 低置信度（未匹配到可靠规则） */
    public static AiResponse lowConfidence(String provider) {
        return new AiResponse("", 0.0D, provider, 0L, false);
    }

    /** 带降级说明的回答 */
    public AiResponse withFallback(String content, String provider, double confidence) {
        return new AiResponse(content, confidence, provider, this.knowledgeBaseId, true);
    }
}
