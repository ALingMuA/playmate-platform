package com.gameplay.ai.enums;

/**
 * AI 应答提供方（详细设计 DDL ai_call_log.provider）。
 */
public enum AiProvider {
    /** 知识库模板匹配（离线可用） */
    KNOWLEDGE_BASE,
    /** Ollama 本地模型 */
    OLLAMA,
    /** 外部大模型 */
    EXTERNAL
}
