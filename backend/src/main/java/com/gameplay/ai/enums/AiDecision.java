package com.gameplay.ai.enums;

/**
 * AI 应答决策（详细设计 DDL ai_call_log.decision）。
 */
public enum AiDecision {
    /** 继续 AI 接待 */
    CONTINUE_AI,
    /** 转人工客服 */
    TRANSFER_HUMAN,
    /** 降级返回固定 FAQ */
    FALLBACK_FAQ
}
