package com.gameplay.ai.strategy;

import com.gameplay.ai.enums.AiProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 模型增强应答器（详细设计 3.3：可选，配置 ai.model.enabled=true 时启用）。
 *
 * <p>演示环境默认关闭。开启时仍以知识库为主，仅在知识库低置信度时尝试模型增强；
 * 模型不可用或返回异常时由门面回退知识库，不阻塞会话。</p>
 */
@Component
@ConditionalOnProperty(name = "ai.model.enabled", havingValue = "true")
public class ModelEnhancedResponder implements AiResponder {

    @Override
    public AiResponse respond(AiRequest request) {
        // 演示实现：不真正调用外部模型，返回低置信度促使门面保持知识库回答。
        // 接入真实模型时在此调用模型客户端（仅传当前会话脱敏上下文）。
        return AiResponse.lowConfidence(AiProvider.EXTERNAL.name());
    }

    @Override
    public String provider() {
        return AiProvider.EXTERNAL.name();
    }
}
