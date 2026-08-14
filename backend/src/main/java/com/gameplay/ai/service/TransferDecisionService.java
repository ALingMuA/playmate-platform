package com.gameplay.ai.service;

import com.gameplay.ai.enums.AiDecision;
import com.gameplay.ai.strategy.AiRequest;
import com.gameplay.ai.strategy.AiResponse;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 转人工判断服务（FR-C11、FR-C19、FR-C20）。
 *
 * <p>命中以下任一条件即转人工：用户主动要求、命中敏感业务词、回答置信度不足、
 * 连续未解决达到阈值。AI 不得执行退款、改单、封禁等承诺（FR-C20）。</p>
 */
@Service
public class TransferDecisionService {

    /** 连续未解决达到该次数必须转人工 */
    static final int MAX_UNRESOLVED_COUNT = 2;

    /** 敏感业务词：AI 只能解释规则，命中即转人工（FR-C20） */
    private static final List<String> SENSITIVE_TERMS = List.of(
            "退款", "退钱", "投诉", "封禁", "解封", "改单", "补偿", "申诉",
            "余额调整", "提现", "仲裁", "举报", "赔", "销号", "注销", "换绑");

    /**
     * 决策：CONTINUE_AI 或 TRANSFER_HUMAN（附原因）。
     */
    public AiDecision decide(AiRequest request, AiResponse response) {
        if (request.requestHuman()) {
            return AiDecision.TRANSFER_HUMAN;
        }
        String content = request.content() == null ? "" : request.content();
        for (String term : SENSITIVE_TERMS) {
            if (content.contains(term)) {
                return AiDecision.TRANSFER_HUMAN;
            }
        }
        if (response.confidence() < 0.70D) {
            return AiDecision.TRANSFER_HUMAN;
        }
        if (request.unresolvedCount() >= MAX_UNRESOLVED_COUNT) {
            return AiDecision.TRANSFER_HUMAN;
        }
        return AiDecision.CONTINUE_AI;
    }

    /** 生成转人工原因描述 */
    public String reasonOf(AiDecision decision, AiRequest request, AiResponse response) {
        if (decision != AiDecision.TRANSFER_HUMAN) {
            return "";
        }
        if (request.requestHuman()) {
            return "用户主动要求转人工";
        }
        String content = request.content() == null ? "" : request.content();
        for (String term : SENSITIVE_TERMS) {
            if (content.contains(term)) {
                return "命中敏感业务词：" + term;
            }
        }
        if (response.confidence() < 0.70D) {
            return "AI 回答置信度不足";
        }
        return "连续未解决次数达到阈值";
    }
}
