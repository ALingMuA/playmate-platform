package com.gameplay.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gameplay.ai.config.AiModelConfigService;
import com.gameplay.ai.config.AiModelProperties;
import com.gameplay.ai.dto.AiSettingsRequest;
import com.gameplay.ai.service.AiResponseFacade;
import com.gameplay.ai.strategy.AiRequest;
import com.gameplay.audit.service.OperationLogService;
import com.gameplay.auth.security.JwtPrincipal;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 真实 Spring + MySQL + 本机 HTTP 提供商；事务回滚配置，测试无真实外部模型费用。 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AiSettingsFlowTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired JdbcTemplate jdbc;
    @Autowired AiModelConfigService settings;
    @Autowired AiModelProperties defaults;
    @Autowired AiResponseFacade facade;
    @Autowired Environment environment;
    @Autowired OperationLogService audit;
    private HttpServer server;
    private volatile int upstreamStatus;
    private final AtomicReference<JsonNode> captured = new AtomicReference<>();
    private final AtomicReference<String> authHeader = new AtomicReference<>();
    private final AtomicReference<String> customHeader = new AtomicReference<>();

    @BeforeEach void setUp() throws Exception {
        jdbc.update("DELETE FROM ai_model_settings");
        upstreamStatus = 200;
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/custom/chat", exchange -> {
            try (exchange) {
                captured.set(mapper.readTree(exchange.getRequestBody()));
                authHeader.set(exchange.getRequestHeaders().getFirst("x-model-key"));
                customHeader.set(exchange.getRequestHeaders().getFirst("x-project"));
                byte[] data = (upstreamStatus == 200 ? "{\"choices\":[{\"finish_reason\":\"stop\",\"message\":{\"content\":\"{\\\"answer\\\":\\\"您好，请问需要什么帮助？\\\",\\\"needsHuman\\\":false,\\\"kind\\\":\\\"greeting\\\",\\\"knowledgeIds\\\":[]}\"}}]}" : "sensitive provider error must never be returned")
                        .getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(upstreamStatus, data.length);
                exchange.getResponseBody().write(data);
            }
        });
        server.start();
    }
    @AfterEach void tearDown() { server.stop(0); }
    private UsernamePasswordAuthenticationToken admin() {
        return new UsernamePasswordAuthenticationToken(new JwtPrincipal(1L, "test-admin", 0, List.of("ADMIN")), null,
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
    }
    private AiSettingsRequest draft() {
        AiSettingsRequest r = new AiSettingsRequest();
        r.setEnabled(true);
        r.setName("test-model");
        r.setEndpointUrl("http://127.0.0.1:" + server.getAddress().getPort() + "/custom/chat?api-version=1");
        r.setAuthMode("API_KEY");
        r.setAuthHeaderName("x-model-key");
        r.setApiKey("synthetic-api-credential");
        r.setCustomHeaders(new java.util.LinkedHashMap<>(Map.of("x-project", "synthetic-header-credential")));
        r.setTemperature(null);
        r.setMaxTokensParameter("max_completion_tokens");
        r.setMaxOutputTokens(2048);
        r.setExtraBody(mapper.createObjectNode().put("reasoning_effort", "low"));
        return r;
    }

    @Test void adminSaveIsEncryptedAndImmediatelyUsedByTheApplicationAndFreshService() throws Exception {
        var r = draft();
        String view = mvc.perform(put("/api/admin/ai/settings").with(authentication(admin()))
                .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(r)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.apiKeyConfigured").value(true))
                .andExpect(jsonPath("$.data.source").value("DATABASE")).andReturn().getResponse().getContentAsString();
        assertThat(view).doesNotContain("synthetic-api-credential", "synthetic-header-credential", "encrypted_config");
        String encrypted = jdbc.queryForObject("SELECT encrypted_config FROM ai_model_settings WHERE id=1", String.class);
        assertThat(encrypted).startsWith("v1:").doesNotContain("synthetic", "test-model");
        var answer = facade.process(new AiRequest("你好", "", 0L, "", 0, false)).response();
        assertThat(answer.provider()).isEqualTo("EXTERNAL");
        assertThat(answer.fallback()).isFalse();
        assertThat(captured.get().path("model").asText()).isEqualTo("test-model");
        assertThat(captured.get().path("max_completion_tokens").asInt()).isEqualTo(2048);
        assertThat(captured.get().has("temperature")).isFalse();
        assertThat(captured.get().path("reasoning_effort").asText()).isEqualTo("low");
        assertThat(authHeader.get()).isEqualTo("synthetic-api-credential");
        assertThat(customHeader.get()).isEqualTo("synthetic-header-credential");
        String key = environment.getProperty("ai.config.encryption-key", environment.getProperty("jwt.secret"));
        var restarted = new AiModelConfigService(defaults, jdbc, mapper, audit, key);
        assertThat(restarted.current().getName()).isEqualTo("test-model");
        assertThat(restarted.current().getApiKey()).isEqualTo("synthetic-api-credential");
        // 调用者修改返回副本不会改写持久配置。
        restarted.current().setName("should-not-persist");
        assertThat(settings.current().getName()).isEqualTo("test-model");
    }

    @Test void omittedCredentialsArePreservedAndExplicitClearAndResetWork() {
        var r = draft();
        settings.save(r, 1L);
        r.setVersion(1); r.setApiKey(""); r.setCustomHeaders(new java.util.LinkedHashMap<>());
        r.getCustomHeaders().put("x-project", null);
        settings.save(r, 1L);
        assertThat(settings.current().getApiKey()).isEqualTo("synthetic-api-credential");
        assertThat(settings.current().getCustomHeaders().get("x-project")).isEqualTo("synthetic-header-credential");
        r.setVersion(2); r.setEnabled(false); r.setClearApiKey(true); r.getCustomHeaders().clear();
        settings.save(r, 1L);
        assertThat(settings.current().getApiKey()).isEmpty();
        assertThat(settings.current().getCustomHeaders()).isEmpty();
        assertThat(settings.reset(3, 1L).source()).isEqualTo("LOCAL");
        assertThat(settings.current().getName()).isEqualTo(defaults.getName());
        assertThat(settings.view().version()).isEqualTo(4);
    }

    @Test void staleAndInvalidWritesPreserveOldConfiguration() {
        var r = draft(); settings.save(r, 1L);
        r.setName("another-model");
        assertThatThrownBy(() -> settings.save(r, 1L)).hasMessageContaining("其他管理员");
        r.setVersion(1); r.getCustomHeaders().put("Host", "bad.example");
        assertThatThrownBy(() -> settings.save(r, 1L)).hasMessageContaining("请求头");
        assertThat(settings.current().getName()).isEqualTo("test-model");
        assertThat(settings.view().version()).isEqualTo(1);
    }

    @Test void draftProbeDoesNotSaveAndReportsModelNotFoundWithoutRawError() throws Exception {
        var r = draft();
        mvc.perform(post("/api/admin/ai/settings/test").with(authentication(admin()))
                .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(r)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.success").value(true));
        assertThat(settings.view().source()).isEqualTo("LOCAL");
        upstreamStatus = 404;
        String response = mvc.perform(post("/api/admin/ai/settings/test").with(authentication(admin()))
                .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(r)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.errorCode").value("MODEL_NOT_FOUND"))
                .andExpect(jsonPath("$.data.httpStatus").value(404)).andReturn().getResponse().getContentAsString();
        assertThat(response).doesNotContain("sensitive provider", "synthetic-api-credential");
        assertThat(settings.view().version()).isZero();
    }

    @Test void allSettingsEndpointsRequireAdmin() throws Exception {
        var user = new UsernamePasswordAuthenticationToken(new JwtPrincipal(2L, "test-user", 0, List.of("USER")), null,
                List.of(new SimpleGrantedAuthority("ROLE_USER")));
        mvc.perform(get("/api/admin/ai/settings")).andExpect(status().isUnauthorized());
        var requests = List.of(get("/api/admin/ai/settings"), put("/api/admin/ai/settings"),
                post("/api/admin/ai/settings/test"), delete("/api/admin/ai/settings").param("version", "0"));
        for (var request : requests) mvc.perform(request.with(authentication(user)).contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsBytes(draft()))).andExpect(status().isForbidden());
    }

    @Test void secretInUrlOrCoreOverrideRejectedBeforeNetwork() throws Exception {
        var r = draft();
        r.setEndpointUrl(r.getEndpointUrl() + "&api_key=placeholder");
        mvc.perform(put("/api/admin/ai/settings").with(authentication(admin())).contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsBytes(r))).andExpect(status().isBadRequest());
        r = draft(); r.setExtraBody(mapper.createObjectNode().put("stream", true));
        mvc.perform(post("/api/admin/ai/settings/test").with(authentication(admin())).contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsBytes(r))).andExpect(status().isBadRequest());
        assertThat(captured.get()).isNull();
    }
}
