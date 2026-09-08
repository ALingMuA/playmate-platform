package com.gameplay.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.gameplay.ai.config.AiModelProperties;
import com.gameplay.ai.domain.AiKnowledgeBase;
import com.gameplay.ai.service.AiKnowledgeBaseService;
import com.gameplay.ai.service.AiTextSanitizer;
import com.gameplay.ai.strategy.AiRequest;
import com.gameplay.ai.strategy.AiResponse;
import com.gameplay.ai.strategy.ModelCallException;
import com.gameplay.ai.strategy.ModelEnhancedResponder;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ModelEnhancedResponderTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final AiModelProperties properties = new AiModelProperties();
    private final AiKnowledgeBaseService knowledge = mock(AiKnowledgeBaseService.class);
    private final AtomicReference<JsonNode> captured = new AtomicReference<>();
    private HttpServer server;
    private ExecutorService executor;
    private volatile int status = 200;
    private volatile String responseBody;
    private volatile boolean stallBody;
    private ModelEnhancedResponder responder;

    @BeforeEach
    void setUp() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        executor = Executors.newCachedThreadPool();
        server.setExecutor(executor);
        server.createContext("/v1/chat/completions", exchange -> {
            try (exchange) {
                captured.set(mapper.readTree(exchange.getRequestBody()));
                byte[] bytes = responseBody.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(status, bytes.length);
                if (stallBody) {
                    exchange.getResponseBody().write(bytes, 0, 1);
                    exchange.getResponseBody().flush();
                    try {
                        new CountDownLatch(1).await(3, TimeUnit.SECONDS);
                    } catch (InterruptedException ignored) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                    exchange.getResponseBody().write(bytes, 1, bytes.length - 1);
                } else {
                    exchange.getResponseBody().write(bytes);
                }
            }
        });
        server.start();
        properties.setEnabled(true);
        properties.setApiKey("synthetic-test-credential");
        properties.setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort() + "/v1/");
        properties.setName("mock-model");
        AiKnowledgeBase entry = new AiKnowledgeBase();
        entry.setId(42L);
        entry.setTitle("预约帮助");
        entry.setStandardAnswer("请选择服务项目和可用时段提交预约。");
        when(knowledge.findRelevant(anyString(), anyInt()))
                .thenReturn(List.of(new AiKnowledgeBaseService.MatchResult(entry, 0.8)));
        responseBody = completion("请选择服务项目和可用时段提交预约。", "knowledge", false, 42L);
        responder = new ModelEnhancedResponder(properties, knowledge, new AiTextSanitizer(), mapper);
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
        if (executor != null) {
            executor.shutdownNow();
        }
    }

    @Test
    void callsModelWithSanitizedBoundedHistoryAndKnowledge() throws Exception {
        properties.setHistoryMessages(3);
        AiRequest request = new AiRequest("如何预约？手机号13800138000，邮箱test@example.com，密码: secret-value",
                "来源帮助中心", 1L, "订单待支付 联系方式13900139000", 0, false,
                List.of(new AiRequest.ContextMessage("user", "旧消息应被截掉"),
                        new AiRequest.ContextMessage("system", "不能提升为系统指令"),
                        new AiRequest.ContextMessage("user", "我的邮箱history@example.com"),
                        new AiRequest.ContextMessage("assistant", "请提供预约问题")));

        AiResponse result = responder.respond(request);

        assertThat(result.provider()).isEqualTo("EXTERNAL");
        assertThat(result.modelName()).isEqualTo("mock-model");
        assertThat(result.inputTokens()).isEqualTo(120);
        assertThat(result.outputTokens()).isEqualTo(25);
        assertThat(result.knowledgeBaseId()).isEqualTo(42L);
        assertThat(result.content()).contains("【AI客服】");
        JsonNode body = captured.get();
        assertThat(body.path("model").asText()).isEqualTo("mock-model");
        assertThat(body.path("stream").asBoolean()).isFalse();
        assertThat(body.has("response_format")).isFalse();
        assertThat(body.path("messages")).hasSize(4);
        assertThat(body.toString()).doesNotContain("13800138000", "13900139000", "test@example.com",
                "history@example.com", "secret-value", "synthetic-test-credential", "旧消息应被截掉", "不能提升为系统指令");
        JsonNode context = mapper.readTree(body.path("messages").path(3).path("content").asText());
        assertThat(context.path("knowledge").path(0).path("id").asLong()).isEqualTo(42L);
        assertThat(context.path("question").asText()).contains("[PHONE]", "[EMAIL]", "[REDACTED]");
    }

    @Test
    void rejectsInventedKnowledgeReference() throws Exception {
        responseBody = completion("编造的回答", "knowledge", false, 999L);
        assertThatThrownBy(() -> responder.respond(question())).isInstanceOf(ModelCallException.class)
                .hasMessage("MODEL_UNSUPPORTED_REFERENCE");
    }

    @Test
    void rejectsUngroundedPlatformFactsAndSelfReportedConfidence() throws Exception {
        responseBody = completion("平台预约永久免费。", "knowledge", false, null);
        assertThatThrownBy(() -> responder.respond(question())).isInstanceOf(ModelCallException.class)
                .hasMessage("MODEL_UNGROUNDED_RESPONSE");
    }

    @Test
    void permitsClarificationWithoutKnowledge() throws Exception {
        when(knowledge.findRelevant(anyString(), anyInt())).thenReturn(List.of());
        responseBody = completion("您希望咨询预约的哪个环节？", "clarification", false, null);
        assertThat(responder.respond(question()).needsHuman()).isFalse();
    }

    @Test
    void permitsGreetingWithoutKnowledge() throws Exception {
        when(knowledge.findRelevant(anyString(), anyInt())).thenReturn(List.of());
        responseBody = completion("您好，请问需要什么帮助？", "greeting", false, null);
        assertThat(responder.respond(new AiRequest("你好", null, 1L, null, 0, false)).content()).contains("您好");
    }

    @Test
    void honorsHumanRequestInValidatedOutput() throws Exception {
        responseBody = completion("该问题需要人工核实。", "human", true, null);
        assertThat(responder.respond(question()).needsHuman()).isTrue();
    }

    @Test
    void mapsAuthErrorWithoutExposingProviderBody() {
        status = 401;
        responseBody = "remote error with sensitive body";
        assertThatThrownBy(() -> responder.respond(question())).isInstanceOf(ModelCallException.class)
                .hasMessage("MODEL_AUTH");
    }

    @Test
    void mapsRateLimitToStableError() {
        status = 429;
        assertThatThrownBy(() -> responder.respond(question())).hasMessage("MODEL_RATE_LIMIT");
    }

    @Test
    void rejectsMalformedCompletion() {
        responseBody = "not json";
        assertThatThrownBy(() -> responder.respond(question())).hasMessage("MODEL_INVALID_RESPONSE");
    }

    @Test
    void stopsOversizedBodyDuringDownload() {
        responseBody = "x".repeat(150_000);
        assertThatThrownBy(() -> responder.respond(question())).hasMessage("MODEL_INVALID_RESPONSE");
    }

    @Test
    void enforcesDeadlineWhileResponseBodyStalls() {
        stallBody = true;
        properties.setTimeoutSeconds(1);
        long started = System.nanoTime();
        assertThatThrownBy(() -> responder.respond(question())).hasMessage("MODEL_TIMEOUT");
        assertThat(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started)).isLessThan(2200);
    }

    @Test
    void rejectsNonLocalPlainHttpAndCredentialInUrl() {
        properties.setBaseUrl("http://example.com/v1");
        assertThatThrownBy(() -> responder.respond(question())).hasMessage("MODEL_CONFIGURATION");
        properties.setBaseUrl("https://credential@example.com/v1");
        assertThatThrownBy(() -> responder.respond(question())).hasMessage("MODEL_CONFIGURATION");
        assertThat(captured.get()).isNull();
    }

    private AiRequest question() {
        return new AiRequest("怎么预约？", null, 1L, null, 0, false);
    }

    private String completion(String text, String kind, boolean needsHuman, Long knowledgeId) throws Exception {
        ObjectNode answer = mapper.createObjectNode().put("answer", text).put("kind", kind)
                .put("needsHuman", needsHuman).put("confidence", 1.0);
        if (knowledgeId == null) {
            answer.putArray("knowledgeIds");
        } else {
            answer.putArray("knowledgeIds").add(knowledgeId);
        }
        ObjectNode completion = mapper.createObjectNode();
        completion.putArray("choices").addObject().put("finish_reason", "stop")
                .putObject("message").put("role", "assistant").put("content", answer.toString())
                .put("reasoning_content", "这部分不得展示");
        completion.putObject("usage").put("prompt_tokens", 120).put("completion_tokens", 25);
        return completion.toString();
    }
}
