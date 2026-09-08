package com.gameplay.ai;

import com.gameplay.ai.config.AiModelProperties;
import com.gameplay.ai.domain.AiCallLog;
import com.gameplay.ai.enums.AiDecision;
import com.gameplay.ai.mapper.AiCallLogMapper;
import com.gameplay.ai.service.AiResponseFacade;
import com.gameplay.ai.service.AiTextSanitizer;
import com.gameplay.ai.service.TransferDecisionService;
import com.gameplay.ai.strategy.AiProcessResult;
import com.gameplay.ai.strategy.AiRequest;
import com.gameplay.ai.strategy.AiResponder;
import com.gameplay.ai.strategy.AiResponse;
import com.gameplay.ai.strategy.KnowledgeBaseResponder;
import com.gameplay.ai.strategy.ModelCallException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiResponseFacadeTest {

    private final KnowledgeBaseResponder knowledge = mock(KnowledgeBaseResponder.class);
    private final AiResponder model = mock(AiResponder.class);
    private final AiCallLogMapper logs = mock(AiCallLogMapper.class);
    private final AiModelProperties properties = new AiModelProperties();
    private AiResponseFacade facade;

    @BeforeEach
    void setUp() {
        properties.setEnabled(true);
        properties.setName("mock-model");
        when(model.provider()).thenReturn("EXTERNAL");
        facade = new AiResponseFacade(knowledge, List.of(knowledge, model), new TransferDecisionService(),
                logs, new AiTextSanitizer(), properties);
    }

    @Test
    void usesModelForEveryOrdinaryQuestion() {
        when(model.respond(any())).thenReturn(AiResponse.success("模型生成的预约回答", 0.85, "EXTERNAL", 42L));
        AiProcessResult result = facade.process(question("怎么预约？"));
        assertThat(result.response().content()).contains("模型生成");
        assertThat(result.decision()).isEqualTo(AiDecision.CONTINUE_AI);
        verify(knowledge, never()).respond(any());
    }

    @Test
    void bypassesModelForHumanAndSensitiveBusiness() {
        for (String text : List.of("帮我转人工", "我要申请退款")) {
            assertThat(facade.process(question(text)).decision()).isEqualTo(AiDecision.TRANSFER_HUMAN);
        }
        verify(model, never()).respond(any());
    }

    @Test
    void fallsBackToKnowledgeAndRecordsAttemptedProviderWithoutSensitiveText() {
        when(model.respond(any())).thenThrow(new ModelCallException("MODEL_AUTH"));
        when(knowledge.respond(any())).thenReturn(AiResponse.success("可在列表选择预约。", 0.8, "KNOWLEDGE_BASE", 42L));
        AiProcessResult result = facade.process(question("预约咨询 邮箱private@example.com 密码: fake-secret"));
        assertThat(result.response().fallback()).isTrue();
        assertThat(result.response().errorCode()).isEqualTo("MODEL_AUTH");
        assertThat(result.response().provider()).isEqualTo("KNOWLEDGE_BASE");
        assertThat(result.decision()).isEqualTo(AiDecision.CONTINUE_AI);
        ArgumentCaptor<AiCallLog> captured = ArgumentCaptor.forClass(AiCallLog.class);
        verify(logs).insert(captured.capture());
        assertThat(captured.getValue().getProvider()).isEqualTo("EXTERNAL");
        assertThat(captured.getValue().getModelName()).isEqualTo("mock-model");
        assertThat(captured.getValue().getRequestSummary()).doesNotContain("private@example.com", "fake-secret");
        assertThat(captured.getValue().getErrorCode()).isEqualTo("MODEL_AUTH");
    }

    @Test
    void transfersToHumanWhenBothModelAndKnowledgeUnavailable() {
        when(model.respond(any())).thenThrow(new ModelCallException("MODEL_TIMEOUT"));
        when(knowledge.respond(any())).thenReturn(AiResponse.lowConfidence("KNOWLEDGE_BASE"));
        AiProcessResult result = facade.process(question("请介绍未提供的活动优惠"));
        assertThat(result.decision()).isEqualTo(AiDecision.TRANSFER_HUMAN);
        assertThat(result.response().content()).contains("人工客服");
        assertThat(result.response().errorCode()).isEqualTo("MODEL_TIMEOUT");
    }

    @Test
    void disabledModelRetainsOfflineKnowledgeBehavior() {
        properties.setEnabled(false);
        when(knowledge.respond(any())).thenReturn(AiResponse.success("知识库回答", 0.8, "KNOWLEDGE_BASE", 42L));
        assertThat(facade.process(question("如何预约")).response().provider()).isEqualTo("KNOWLEDGE_BASE");
        verify(model, never()).respond(any());
    }

    private AiRequest question(String content) {
        return new AiRequest(content, null, 1L, null, 0, false);
    }
}
