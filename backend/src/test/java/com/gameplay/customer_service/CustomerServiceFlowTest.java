package com.gameplay.customer_service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.gameplay.ai.task.service.AiReplyTaskWorker;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * customer_service + ai 模块集成测试（FR-C01~C29、FR-C23）。
 *
 * <p>覆盖：客服账号创建/禁用、知识库命中 AI 应答、敏感词自动转人工、用户主动转人工、
 * 人工队列、客服领取/回复/关闭、满意度评价的完整闭环。</p>
 *
 * <p>依赖：本机 MySQL 已执行 sql/schema.sql 与 sql/data.sql（admin 种子账号），
 * 且已配置 application-local.yml。数据在事务中回滚。</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class CustomerServiceFlowTest {

    private static final String PASSWORD = "Passw0rd123";
    private static final String ADMIN_ACCOUNT = "admin";
    private static final String ADMIN_PASSWORD = "Admin@123456";
    private static final AtomicInteger SEQ = new AtomicInteger();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AiReplyTaskWorker taskWorker;

    // ==================== 辅助方法 ====================

    private String adminToken() throws Exception {
        return loginAndGetToken(ADMIN_ACCOUNT, ADMIN_PASSWORD);
    }

    /** 注册并登录普通用户，返回 {id, token} */
    private ObjectNode registerAndLogin() throws Exception {
        String username = "cs" + System.currentTimeMillis() + "_" + SEQ.incrementAndGet();
        MvcResult registered = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.createObjectNode()
                                .put("username", username)
                                .put("password", PASSWORD)
                                .put("nickname", "测试_" + username)
                                .put("mobile", "1" + (6_000_000_000L + SEQ.incrementAndGet()))
                                .toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andReturn();
        long id = objectMapper.readTree(registered.getResponse().getContentAsString())
                .path("data").path("id").asLong();
        return objectMapper.createObjectNode()
                .put("id", id)
                .put("token", loginAndGetToken(username, PASSWORD));
    }

    private String loginAndGetToken(String account, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.createObjectNode()
                                .put("account", account)
                                .put("password", password)
                                .toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data").path("token").asText();
    }

    /** 管理员创建客服账号并完成强制改密，返回 {csAccount, token} */
    private ObjectNode createCsAccount() throws Exception {
        String csAccount = "csagent" + System.currentTimeMillis() + "_" + SEQ.incrementAndGet();
        String initialPassword = "Cs@12345678";
        String newPassword = "CsNew@123456";
        mockMvc.perform(post("/api/admin/cs-accounts")
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.createObjectNode()
                                .put("csAccount", csAccount)
                                .put("csName", "客服" + csAccount)
                                .put("contactMobile", "1" + (7_000_000_000L + SEQ.incrementAndGet()))
                                .put("initialPassword", initialPassword)
                                .toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"));
        // 首次登录强制改密（FR-C04）：改密后旧令牌失效，返回新令牌
        String tempToken = loginAndGetToken(csAccount, initialPassword);
        MvcResult changed = mockMvc.perform(post("/api/auth/change-password")
                        .header("Authorization", "Bearer " + tempToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.createObjectNode()
                                .put("oldPassword", initialPassword)
                                .put("newPassword", newPassword)
                                .toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andReturn();
        String newToken = objectMapper.readTree(changed.getResponse().getContentAsString())
                .path("data").path("token").asText();
        return objectMapper.createObjectNode()
                .put("csAccount", csAccount)
                .put("token", newToken);
    }

    /** 管理员新增知识库条目 */
    private void createKnowledge(String category, String title, String keywords, String answer) throws Exception {
        mockMvc.perform(post("/api/admin/ai-knowledge")
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.createObjectNode()
                                .put("category", category)
                                .put("title", title)
                                .put("keywords", keywords)
                                .put("standardAnswer", answer)
                                .put("priority", 10)
                                .toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"));
    }

    /** 用户发起会话 */
    private long createConversation(String token, String sourceType, String firstContent) throws Exception {
        ObjectNode body = objectMapper.createObjectNode().put("sourceType", sourceType);
        if (firstContent != null) {
            body.put("firstContent", firstContent);
        }
        MvcResult result = mockMvc.perform(post("/api/customer-service/conversations")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data").path("id").asLong();
    }

    /** 用户发送消息触发 AI 应答 */
    private JsonNode aiRespond(String token, long conversationId, String content) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/customer-service/conversations/" + conversationId + "/ai-responses")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.createObjectNode()
                                .put("clientMsgId", UUID.randomUUID().toString())
                                .put("content", content)
                                .toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andReturn();
        JsonNode accepted = objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
        assertThat(accepted.path("task").path("status").asText()).isEqualTo("PENDING");
        assertThat(accepted.path("aiMessage").isNull()).isTrue();
        taskWorker.process(accepted.path("task").path("id").asLong());
        MvcResult detail = mockMvc.perform(get("/api/customer-service/conversations/" + conversationId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn();
        ObjectNode data = (ObjectNode) objectMapper.readTree(detail.getResponse().getContentAsString()).path("data");
        MvcResult messages = mockMvc.perform(get("/api/customer-service/conversations/" + conversationId + "/messages")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn();
        for (JsonNode message : objectMapper.readTree(messages.getResponse().getContentAsString()).path("data")) {
            if ("AI".equals(message.path("senderType").asText())) {
                data.set("aiMessage", message);
            }
        }
        return data;
    }

    // ==================== 测试用例 ====================

    @Test
    @DisplayName("外部消息不能占用 AI 系统幂等键")
    void rejects_internal_message_key_over_http() throws Exception {
        ObjectNode user = registerAndLogin();
        String token = user.path("token").asText();
        long conversationId = createConversation(token, "HELP_CENTER", null);
        mockMvc.perform(post("/api/customer-service/conversations/" + conversationId + "/ai-responses")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.createObjectNode()
                                .put("clientMsgId", "ai-task-123")
                                .put("content", "抢占系统消息键")
                                .put("requestHuman", true).toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("知识库命中时 AI 正常应答（FR-C09/C16/C17/C21）")
    void ai_answer_with_knowledge_base() throws Exception {
        createKnowledge("订单", "如何取消订单", "取消订单,取消预约",
                "您可以在我的订单详情页点击取消订单按钮，系统将自动退还虚拟余额。");
        ObjectNode user = registerAndLogin();
        long conversationId = createConversation(user.path("token").asText(), "HELP_CENTER", null);

        JsonNode data = aiRespond(user.path("token").asText(), conversationId, "请问怎么取消订单？");

        assertThat(data.path("aiTask").path("status").asText()).isEqualTo("COMPLETED");
        assertThat(data.path("conversationStatus").asText()).isEqualTo("AI_PROCESSING");
        assertThat(data.path("aiMessage").path("content").asText())
                .contains("【AI客服】")
                .contains("取消订单");
        assertThat(data.path("aiMessage").path("aiMark").asInt()).isEqualTo(1);
    }

    @Test
    @DisplayName("敏感词命中自动转人工（FR-C11/C19/C20）")
    void sensitive_word_transfers_to_human() throws Exception {
        ObjectNode user = registerAndLogin();
        long conversationId = createConversation(user.path("token").asText(), "HELP_CENTER", null);

        JsonNode data = aiRespond(user.path("token").asText(), conversationId, "我要申请退款");

        assertThat(data.path("aiTask").path("status").asText()).isIn("COMPLETED", "FALLBACK");
        assertThat(data.path("conversationStatus").asText()).isEqualTo("WAITING_HUMAN");
        assertThat(data.path("transferReason").asText()).contains("退款");
    }

    @Test
    @DisplayName("客服账号生命周期与人工接待闭环（FR-C01~C06、C24~C29、C15）")
    void cs_claim_reply_close_evaluate_flow() throws Exception {
        // 准备客服与知识库
        ObjectNode cs = createCsAccount();
        String csToken = cs.path("token").asText();
        // 客服上线
        mockMvc.perform(put("/api/customer-service/me/work-status")
                        .header("Authorization", "Bearer " + csToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.createObjectNode().put("workStatus", "ONLINE").toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.workStatus").value("ONLINE"));

        // 用户发起会话并触发转人工
        ObjectNode user = registerAndLogin();
        String userToken = user.path("token").asText();
        long conversationId = createConversation(userToken, "HELP_CENTER", null);
        aiRespond(userToken, conversationId, "我要投诉");

        // 人工队列可见（含 version 与排队时长）
        MvcResult queueResult = mockMvc.perform(get("/api/customer-service/queue")
                        .header("Authorization", "Bearer " + csToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andReturn();
        JsonNode queue = objectMapper.readTree(queueResult.getResponse().getContentAsString()).path("data");
        assertThat(queue.isArray() && queue.size() > 0).isTrue();
        JsonNode item = null;
        for (JsonNode node : queue) {
            if (node.path("conversationId").asLong() == conversationId) {
                item = node;
                break;
            }
        }
        assertThat(item).isNotNull();
        int version = item.path("version").asInt();

        // 客服领取
        mockMvc.perform(post("/api/customer-service/conversations/" + conversationId + "/claim")
                        .header("Authorization", "Bearer " + csToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.createObjectNode()
                                .put("expectedVersion", version)
                                .put("claimRemark", "我来处理")
                                .toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.conversationStatus").value("HUMAN_PROCESSING"));

        // 客服回复
        mockMvc.perform(post("/api/customer-service/conversations/" + conversationId + "/messages")
                        .header("Authorization", "Bearer " + csToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.createObjectNode()
                                .put("clientMsgId", UUID.randomUUID().toString())
                                .put("content", "您好，已收到您的投诉，正在为您核实。")
                                .toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"));

        // 人工接待中用户再发消息：仅保存消息，不再触发 AI（消息可见）
        mockMvc.perform(post("/api/customer-service/conversations/" + conversationId + "/ai-responses")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.createObjectNode()
                                .put("clientMsgId", UUID.randomUUID().toString())
                                .put("content", "好的，谢谢")
                                .toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"));

        // 消息补拉（REST 兜底）：按游标查看全部消息
        MvcResult msgResult = mockMvc.perform(get("/api/customer-service/conversations/" + conversationId + "/messages")
                        .header("Authorization", "Bearer " + userToken)
                        .param("afterId", "0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andReturn();
        JsonNode messages = objectMapper.readTree(msgResult.getResponse().getContentAsString()).path("data");
        assertThat(messages.isArray() && messages.size() >= 4).isTrue();

        // 客服关闭会话
        mockMvc.perform(post("/api/customer-service/conversations/" + conversationId + "/close")
                        .header("Authorization", "Bearer " + csToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.createObjectNode()
                                .put("category", "投诉处理")
                                .put("result", "已核实并处理完毕")
                                .toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.conversationStatus").value("CLOSED"));

        // 用户满意度评价（FR-C15）
        mockMvc.perform(post("/api/customer-service/conversations/" + conversationId + "/evaluation")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.createObjectNode()
                                .put("score", 5)
                                .put("content", "处理很及时")
                                .toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"));

        // 重复评价被拦截
        mockMvc.perform(post("/api/customer-service/conversations/" + conversationId + "/evaluation")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.createObjectNode().put("score", 3).toString()))
                .andExpect(jsonPath("$.code").value("CS_ALREADY_EVALUATED"));
    }

    @Test
    @DisplayName("禁用客服账号后旧令牌失效（FR-C03）")
    void disable_cs_account_invalidates_token() throws Exception {
        // 创建客服账号并取得账号 ID
        String csAccount = "csagent" + System.currentTimeMillis() + "_" + SEQ.incrementAndGet();
        String initialPassword = "Cs@12345678";
        MvcResult created = mockMvc.perform(post("/api/admin/cs-accounts")
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.createObjectNode()
                                .put("csAccount", csAccount)
                                .put("csName", "客服" + csAccount)
                                .put("contactMobile", "1" + (7_000_000_000L + SEQ.incrementAndGet()))
                                .put("initialPassword", initialPassword)
                                .toString()))
                .andExpect(status().isOk())
                .andReturn();
        long accountId = objectMapper.readTree(created.getResponse().getContentAsString())
                .path("data").path("id").asLong();
        String csToken = loginAndGetToken(csAccount, initialPassword);

        // 禁用（必须填写原因）
        mockMvc.perform(put("/api/admin/cs-accounts/" + accountId + "/status")
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.createObjectNode()
                                .put("accountStatus", "DISABLED")
                                .put("reason", "测试禁用")
                                .toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"));

        // 旧令牌访问客服接口被拒绝（禁用时递增令牌版本，返回 401 AUTH_TOKEN_VERSION_MISMATCH）
        mockMvc.perform(get("/api/customer-service/queue")
                        .header("Authorization", "Bearer " + csToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_TOKEN_VERSION_MISMATCH"));
    }

    @Test
    @DisplayName("未命中知识库时自动转人工且保留引导消息（FR-C11/C22）")
    void unknown_question_transfers_to_human() throws Exception {
        ObjectNode user = registerAndLogin();
        long conversationId = createConversation(user.path("token").asText(), "HELP_CENTER", null);

        JsonNode data = aiRespond(user.path("token").asText(), conversationId, "今天天气怎么样？");

        assertThat(data.path("aiTask").path("status").asText()).isEqualTo("FALLBACK");
        assertThat(data.path("conversationStatus").asText()).isEqualTo("WAITING_HUMAN");
        assertThat(data.path("aiMessage").path("content").asText()).contains("人工客服");
    }
}
