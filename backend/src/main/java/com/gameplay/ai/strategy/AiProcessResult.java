package com.gameplay.ai.strategy;

import com.gameplay.ai.enums.AiDecision;

/**
 * AI 应答门面处理结果：应答内容 + 转人工决策。
 *
 * @param response 最终应答
 * @param decision 决策
 * @param reason   转人工原因（决策为 CONTINUE_AI 时为空）
 */
public record AiProcessResult(AiResponse response, AiDecision decision, String reason) {
}
