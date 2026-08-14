package com.gameplay.ai.strategy;

import com.gameplay.ai.domain.AiKnowledgeBase;
import com.gameplay.ai.enums.AiProvider;
import com.gameplay.ai.service.AiKnowledgeBaseService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 知识库模板应答器（详细设计 3.3：始终可用，离线演示）。
 *
 * <p>检索启用知识库中命中关键词的条目，按命中比例计算置信度；
 * 未命中返回低置信度空回答，由门面决定降级或转人工。</p>
 */
@Component
@RequiredArgsConstructor
public class KnowledgeBaseResponder implements AiResponder {

    /** 视为可靠回答的最低置信度 */
    static final double RELIABLE_CONFIDENCE = 0.70D;

    private final AiKnowledgeBaseService knowledgeBaseService;

    @Override
    public AiResponse respond(AiRequest request) {
        AiKnowledgeBaseService.MatchResult match = knowledgeBaseService.match(request.content());
        if (match == null || match.confidence() < RELIABLE_CONFIDENCE) {
            return AiResponse.lowConfidence(AiProvider.KNOWLEDGE_BASE.name());
        }
        // 明确标识 AI 客服身份（FR-C21）
        String content = "【AI客服】" + match.entry().getStandardAnswer();
        return AiResponse.success(content, match.confidence(),
                AiProvider.KNOWLEDGE_BASE.name(), match.entry().getId());
    }

    @Override
    public String provider() {
        return AiProvider.KNOWLEDGE_BASE.name();
    }
}
