package com.gameplay.ai.service;

import com.gameplay.ai.config.AiModelProperties;
import com.gameplay.ai.domain.AiCallLog;
import com.gameplay.ai.enums.AiDecision;
import com.gameplay.ai.mapper.AiCallLogMapper;
import com.gameplay.ai.strategy.AiProcessResult;
import com.gameplay.ai.strategy.AiRequest;
import com.gameplay.ai.strategy.AiResponse;
import com.gameplay.ai.strategy.AiResponder;
import com.gameplay.ai.strategy.KnowledgeBaseResponder;
import com.gameplay.ai.strategy.ModelCallException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * 模型生成优先，确定性转人工规则前置，故障回退可靠知识库。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiResponseFacade {

    private final KnowledgeBaseResponder knowledgeBaseResponder;
    private final List<AiResponder> enhancedResponders;
    private final TransferDecisionService transferDecisionService;
    private final AiCallLogMapper aiCallLogMapper;
    private final AiTextSanitizer sanitizer;
    private final AiModelProperties modelProperties;

    /** 处理一条用户消息：返回最终应答与决策 */
    public AiProcessResult process(AiRequest request) {
        Instant start = Instant.now();
        AiResponse response;
        String attemptedProvider = null;
        String errorCode = null;
        if (transferDecisionService.requiresHumanBeforeModel(request)) {
            response = new AiResponse("【AI客服】这个问题需要人工客服处理，正在为您转接。", 0,
                    "KNOWLEDGE_BASE", 0L, false, true, null, null, null, null);
        } else {
            AiResponder model = enhancedResponders.stream()
                    .filter(r -> !(r instanceof KnowledgeBaseResponder))
                    .findFirst().orElse(null);
            if (model != null && modelProperties.isEnabled()) {
                attemptedProvider = model.provider();
                try {
                    response = model.respond(request);
                } catch (Exception failure) {
                    errorCode = failure instanceof ModelCallException known ? known.errorCode() : "MODEL_UNAVAILABLE";
                    log.warn("AI 模型调用失败，类别={}", errorCode);
                    AiResponse base = knowledgeBaseResponder.respond(request);
                    response = new AiResponse(base.content(), base.confidence(), base.provider(), base.knowledgeBaseId(),
                            true, false, sanitizer.sanitize(modelProperties.getName(), 100), null, null, errorCode);
                }
            } else {
                response = knowledgeBaseResponder.respond(request);
            }
        }

        if (!StringUtils.hasText(response.content())) {
            // 无可靠回答：给出引导提示而非猜测（FR-C11 不得猜测或承诺处理结果）
            response = response.withFallback(
                    "【AI客服】抱歉，我暂时无法可靠回答这个问题，已为您转接人工客服，请稍候。",
                    response.provider(), 0.0D);
        }

        AiDecision decision = transferDecisionService.decide(request, response);
        String reason = transferDecisionService.reasonOf(decision, request, response);
        long elapsedMs = Duration.between(start, Instant.now()).toMillis();
        recordLog(request, response, decision, attemptedProvider, errorCode, elapsedMs);
        return new AiProcessResult(response, decision, reason);
    }

    private void recordLog(AiRequest request, AiResponse response, AiDecision decision,
                           String attemptedProvider, String errorCode, long elapsedMs) {
        try {
            AiCallLog logEntry = new AiCallLog();
            logEntry.setConversationId(request.conversationId());
            logEntry.setKnowledgeBaseId(response.knowledgeBaseId() == null ? 0L : response.knowledgeBaseId());
            logEntry.setProvider(attemptedProvider == null ? response.provider() : attemptedProvider);
            logEntry.setRequestSummary(sanitizer.sanitize(request.content(), 1000));
            logEntry.setResponseSummary(sanitizer.sanitize(response.content(), 1000));
            logEntry.setConfidence(BigDecimal.valueOf(Math.max(0, Math.min(1, response.confidence()))));
            logEntry.setDecision(decision.name());
            logEntry.setErrorMessage(errorCode == null ? "" : "模型调用失败，已采用知识库降级或转人工");
            logEntry.setErrorCode(errorCode);
            logEntry.setModelName(response.modelName());
            logEntry.setInputTokens(response.inputTokens());
            logEntry.setOutputTokens(response.outputTokens());
            logEntry.setElapsedMs((int) Math.min(Integer.MAX_VALUE, elapsedMs));
            aiCallLogMapper.insert(logEntry);
        } catch (Exception ignored) {
            log.warn("AI 调用日志写入失败，类别=LOG_WRITE_FAILED");
        }
    }
}
