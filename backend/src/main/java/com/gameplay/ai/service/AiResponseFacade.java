package com.gameplay.ai.service;

import com.gameplay.ai.domain.AiCallLog;
import com.gameplay.ai.enums.AiDecision;
import com.gameplay.ai.mapper.AiCallLogMapper;
import com.gameplay.ai.strategy.AiProcessResult;
import com.gameplay.ai.strategy.AiRequest;
import com.gameplay.ai.strategy.AiResponse;
import com.gameplay.ai.strategy.AiResponder;
import com.gameplay.ai.strategy.KnowledgeBaseResponder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * AI 应答门面（详细设计 3.3）：知识库优先，可选模型增强，统一转人工判断与调用日志。
 *
 * <p>模型增强仅在配置启用时参与；任何异常都降级为知识库回答或固定提示，不阻塞会话（FR-C22）。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiResponseFacade {

    private final KnowledgeBaseResponder knowledgeBaseResponder;
    private final List<AiResponder> enhancedResponders;
    private final TransferDecisionService transferDecisionService;
    private final AiCallLogMapper aiCallLogMapper;

    /** 处理一条用户消息：返回最终应答与决策 */
    public AiProcessResult process(AiRequest request) {
        Instant start = Instant.now();
        AiResponse base = knowledgeBaseResponder.respond(request);
        AiResponse response = base;
        String fallbackMsg = null;

        if (base.confidence() < 0.70D) {
            AiResponder model = enhancedResponders.stream()
                    .filter(r -> !(r instanceof KnowledgeBaseResponder))
                    .findFirst().orElse(null);
            if (model != null) {
                try {
                    AiResponse enhanced = model.respond(request);
                    if (enhanced.confidence() > base.confidence()) {
                        response = enhanced;
                    }
                } catch (Exception e) {
                    log.warn("AI 模型应答异常，回退知识库: {}", e.getMessage());
                    fallbackMsg = "模型不可用，已回退知识库回答";
                }
            }
        }

        if (!StringUtils.hasText(response.content()) && !response.fallback()) {
            // 无可靠回答：给出引导提示而非猜测（FR-C11 不得猜测或承诺处理结果）
            response = response.withFallback(
                    "【AI客服】抱歉，我暂时无法可靠回答这个问题，已为您转接人工客服，请稍候。",
                    response.provider(), 0.0D);
        }

        AiDecision decision = transferDecisionService.decide(request, response);
        String reason = transferDecisionService.reasonOf(decision, request, response);
        long elapsedMs = Duration.between(start, Instant.now()).toMillis();
        recordLog(request, response, decision, fallbackMsg, elapsedMs);
        return new AiProcessResult(response, decision, reason);
    }

    private void recordLog(AiRequest request, AiResponse response, AiDecision decision,
                           String fallbackMsg, long elapsedMs) {
        try {
            AiCallLog logEntry = new AiCallLog();
            logEntry.setConversationId(request.conversationId());
            logEntry.setKnowledgeBaseId(response.knowledgeBaseId() == null ? 0L : response.knowledgeBaseId());
            logEntry.setProvider(response.provider());
            logEntry.setRequestSummary(truncate(request.content(), 1000));
            logEntry.setResponseSummary(truncate(response.content(), 1000));
            logEntry.setConfidence(BigDecimal.valueOf(Math.max(0, Math.min(1, response.confidence()))));
            logEntry.setDecision(decision.name());
            logEntry.setErrorMessage(truncate(fallbackMsg, 500));
            logEntry.setElapsedMs((int) elapsedMs);
            aiCallLogMapper.insert(logEntry);
        } catch (Exception e) {
            log.warn("AI 调用日志写入失败: {}", e.getMessage());
        }
    }

    private String truncate(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() <= max ? s : s.substring(0, max);
    }
}
