package com.gameplay.ai.strategy;

import java.util.List;

/**
 * AI 应答请求（详细设计 3.3）。
 *
 * <p>仅携带当前会话必要信息：用户消息内容、会话上下文意图提示（脱敏）、
 * 关联订单摘要（脱敏）与连续未解决次数；不传递任何用户敏感字段。</p>
 *
 * @param content          用户最新消息内容
 * @param intentHints      会话上下文意图提示（如来源、历史话题），可空
 * @param conversationId   会话ID
 * @param relatedOrderSummary 关联订单脱敏摘要（如"订单#12345 待支付"），可空
 * @param unresolvedCount  连续未解决次数（转人工判断用）
 * @param requestHuman     用户是否主动要求人工
 */
public record AiRequest(
        String content,
        String intentHints,
        Long conversationId,
        String relatedOrderSummary,
        int unresolvedCount,
        boolean requestHuman,
        List<ContextMessage> history) {

    public AiRequest {
        history = history == null ? List.of() : List.copyOf(history);
    }

    public AiRequest(String content, String intentHints, Long conversationId,
                     String relatedOrderSummary, int unresolvedCount, boolean requestHuman) {
        this(content, intentHints, conversationId, relatedOrderSummary, unresolvedCount, requestHuman, List.of());
    }

    public record ContextMessage(String role, String content) {
    }

    /** 当前用户消息内容（脱敏后长度上限） */
    public static final int MAX_CONTENT_LENGTH = 4000;
}
