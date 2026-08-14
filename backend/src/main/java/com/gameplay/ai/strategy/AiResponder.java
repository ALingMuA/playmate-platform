package com.gameplay.ai.strategy;

/**
 * AI 应答策略接口（详细设计 3.3：可插拔适配层）。
 *
 * <p>{@code KnowledgeBaseResponder} 始终可用（离线演示）；模型增强实现按配置条件启用。</p>
 */
public interface AiResponder {

    /** 生成应答（实现方不应抛出异常，异常由门面降级处理） */
    AiResponse respond(AiRequest request);

    /** 提供方编码，与 {@code ai_call_log.provider} 对应 */
    String provider();
}
