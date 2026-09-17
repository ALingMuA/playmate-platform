package com.gameplay.companion;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * companion 模块集成测试：入驻申请 → 管理员审核 → 主页/服务/档期 → 订单闭环 → 收益结算。
 *
 * <p>覆盖 FR-P01~P19 主链路（FR-M06 审核、FR-U07~U13 订单、FR-U09 支付、FR-P19 收益）。
 * 依赖：本机 MySQL 已执行 sql/schema.sql 与 sql/data.sql，且配置了 application-local.yml。</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class CompanionModuleTest {

    private static final String PASSWORD = "Passw0rd123";
    private static final AtomicInteger SEQ = new AtomicInteger();
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private String uniqueUser() {
        return "cmp" + System.currentTimeMillis() + "_" + SEQ.incrementAndGet();
    }

    private String nextMobile() {
        return "1" + (5_000_000_000L + SEQ.incrementAndGet());
    }

    /** 注册并登录，返回 {token, user} */
    private JsonNode registerAndLogin(String username) throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.createObjectNode()
                                .put("username", username)
                                .put("password", PASSWORD)
                                .put("nickname", "陪玩测试")
                                .put("mobile", nextMobile())
                                .put("email", username + "@test.local")
                                .toString()))
                .andExpect(status().isOk());
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.createObjectNode()
                                .put("account", username)
                                .put("password", PASSWORD)
                                .toString()))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
    }

    private String adminToken() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.createObjectNode()
                                .put("account", "admin")
                                .put("password", "Admin@123456")
                                .toString()))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data").path("token").asText();
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    /** 重新登录（审核通过后获得 COMPANION 角色）并返回新令牌 */
    private String relogin(String username) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.createObjectNode()
                                .put("account", username)
                                .put("password", PASSWORD)
                                .toString()))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data").path("token").asText();
    }

    /** 提交入驻申请 */
    private long submitApplication(String token) throws Exception {
        long gameId = firstGameId();
        ObjectNode cap = objectMapper.createObjectNode()
                .put("gameId", gameId)
                .put("gameName", "王者荣耀")
                .put("server", "微信区")
                .put("rank", "最强王者");
        ObjectNode req = objectMapper.createObjectNode()
                .put("realName", "张三")
                .put("contactMobile", nextMobile())
                .put("introduction", "五年王者荣耀经验，擅长打野")
                .set("capabilities", objectMapper.createArrayNode().add(cap));
        MvcResult result = mockMvc.perform(post("/api/companion/applications")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(req.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.auditStatus").value("PENDING"))
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data").path("id").asLong();
    }

    /** 管理员审核通过入驻申请 */
    private void auditApproveApplication(String adminToken, long applicationId) throws Exception {
        mockMvc.perform(post("/api/admin/companion-applications/" + applicationId + "/audit")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.createObjectNode()
                                .put("approved", true)
                                .put("reason", "材料齐全，通过")
                                .toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"));
    }

    /** 创建服务并返回 id */
    private long createService(String token) throws Exception {
        ObjectNode req = objectMapper.createObjectNode()
                .put("gameId", firstGameId())
                .put("serviceTypeId", firstServiceTypeId())
                .put("title", "王者荣耀 荣耀王者带飞")
                .put("description", "稳定上分，包赢包C")
                .put("priceCents", 5000)
                .put("minDurationMinutes", 60);
        MvcResult result = mockMvc.perform(post("/api/companion/services")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(req.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.auditStatus").value("PENDING"))
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data").path("id").asLong();
    }

    /** 管理员审核通过服务项目 */
    private void auditApproveService(String adminToken, long serviceId) throws Exception {
        mockMvc.perform(post("/api/admin/companion-services/" + serviceId + "/audit")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.createObjectNode()
                                .put("approved", true)
                                .put("reason", "合规")
                                .toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"));
    }

    /** 从公开接口获取第一个已启用游戏的 id */
    private long firstGameId() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/games"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode data = objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
        assertThat(data.size()).isPositive();
        return data.get(0).path("id").asLong();
    }

    /** 从公开接口获取第一个已启用服务类型的 id */
    private long firstServiceTypeId() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/service-types"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode data = objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
        assertThat(data.size()).isPositive();
        return data.get(0).path("id").asLong();
    }

    /** 准备一个已审核通过的陪玩师，返回 {token: 新令牌, userId} */
    private JsonNode prepareCompanion(String admin) throws Exception {
        JsonNode companion = registerAndLogin(uniqueUser());
        String cToken = companion.path("token").asText();
        long appId = submitApplication(cToken);
        auditApproveApplication(admin, appId);
        ObjectNode result = objectMapper.createObjectNode();
        result.put("token", relogin(companion.path("user").path("username").asText()));
        result.put("userId", companion.path("user").path("id").asLong());
        return result;
    }

    // ==================== FR-P01~P04 入驻申请 ====================

    @Test
    @DisplayName("入驻申请：提交后待审核，重复提交被拦截（409）")
    void application_submit_and_duplicate() throws Exception {
        JsonNode user = registerAndLogin(uniqueUser());
        String token = user.path("token").asText();

        long appId = submitApplication(token);
        mockMvc.perform(get("/api/companion/applications/mine")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(appId))
                .andExpect(jsonPath("$.data[0].auditStatus").value("PENDING"));

        // 重复提交拦截
        mockMvc.perform(post("/api/companion/applications")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.createObjectNode()
                                .put("realName", "张三")
                                .put("contactMobile", nextMobile())
                                .put("introduction", "再次申请")
                                .set("capabilities", objectMapper.createArrayNode()
                                        .add(objectMapper.createObjectNode()
                                                .put("gameId", firstGameId()).put("server", "微信区").put("rank", "钻石")))
                                .toString()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("COMPANION_APPLICATION_ALREADY_PENDING"));
    }

    @Test
    @DisplayName("入驻审核：通过后授予 COMPANION 角色并创建主页（FR-M06/FR-P03）")
    void application_audit_approve_grants_role() throws Exception {
        JsonNode user = registerAndLogin(uniqueUser());
        String token = user.path("token").asText();
        long userId = user.path("user").path("id").asLong();
        long appId = submitApplication(token);
        String admin = adminToken();
        auditApproveApplication(admin, appId);

        // 申请状态变为已通过
        mockMvc.perform(get("/api/companion/applications/mine")
                        .header("Authorization", bearer(token)))
                .andExpect(jsonPath("$.data[0].auditStatus").value("APPROVED"));

        // 重新登录后持有 COMPANION 角色，且主页可查询
        String newToken = relogin(user.path("user").path("username").asText());
        mockMvc.perform(get("/api/companion/profile")
                        .header("Authorization", bearer(newToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userId").value(userId))
                .andExpect(jsonPath("$.data.serviceStatus").value("RESTING"));
    }

    @Test
    @DisplayName("入驻审核：驳回必须填写原因；驳回后可重新提交（FR-P04）")
    void application_audit_reject_and_resubmit() throws Exception {
        JsonNode user = registerAndLogin(uniqueUser());
        String token = user.path("token").asText();
        long appId = submitApplication(token);
        String admin = adminToken();

        // 驳回必填原因
        mockMvc.perform(post("/api/admin/companion-applications/" + appId + "/audit")
                        .header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.createObjectNode()
                                .put("approved", false)
                                .put("reason", "")
                                .toString()))
                .andExpect(status().isBadRequest());

        // 正常驳回
        mockMvc.perform(post("/api/admin/companion-applications/" + appId + "/audit")
                        .header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.createObjectNode()
                                .put("approved", false)
                                .put("reason", "证明图片不清晰")
                                .toString()))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/companion/applications/mine")
                        .header("Authorization", bearer(token)))
                .andExpect(jsonPath("$.data[0].auditStatus").value("REJECTED"))
                .andExpect(jsonPath("$.data[0].auditReason").value("证明图片不清晰"));

        // 驳回后可重新提交（历史保留）
        long newAppId = submitApplication(token);
        assertThat(newAppId).isNotEqualTo(appId);
        mockMvc.perform(get("/api/companion/applications/mine")
                        .header("Authorization", bearer(token)))
                .andExpect(jsonPath("$.data.length()").value(2));
    }

    // ==================== FR-P05/P06 主页与接单状态 ====================

    @Test
    @DisplayName("主页维护：更新展示名/简介/能力；接单状态可切换（FR-P05/P06）")
    void profile_update_and_service_status() throws Exception {
        String admin = adminToken();
        String cToken = prepareCompanion(admin).path("token").asText();

        // 更新主页
        mockMvc.perform(put("/api/companion/profile")
                        .header("Authorization", bearer(cToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.createObjectNode()
                                .put("displayName", "王者大神")
                                .put("profileIntro", "专注上分五年")
                                .set("capabilities", objectMapper.createArrayNode()
                                        .add(objectMapper.createObjectNode()
                                                .put("gameId", 1)
                                                .put("gameName", "王者荣耀")
                                                .put("server", "微信区")
                                                .put("rank", "荣耀王者")))
                                .toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.displayName").value("王者大神"))
                .andExpect(jsonPath("$.data.capabilities[0].rank").value("荣耀王者"));

        // 接单状态：RESTING → AVAILABLE
        mockMvc.perform(put("/api/companion/profile/service-status")
                        .header("Authorization", bearer(cToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.createObjectNode()
                                .put("serviceStatus", "AVAILABLE")
                                .toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.serviceStatus").value("AVAILABLE"));

        // 非法状态
        mockMvc.perform(put("/api/companion/profile/service-status")
                        .header("Authorization", bearer(cToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.createObjectNode()
                                .put("serviceStatus", "SUSPENDED")
                                .toString()))
                .andExpect(status().isForbidden());
    }

    // ==================== FR-P07~P09 服务项目 ====================

    @Test
    @DisplayName("服务管理：未审核不能上架；审核通过后可上架；编辑后重新审核（FR-P07~P09）")
    void service_flow() throws Exception {
        String admin = adminToken();
        String cToken = prepareCompanion(admin).path("token").asText();

        long serviceId = createService(cToken);

        // 未审核上架 → 409
        mockMvc.perform(put("/api/companion/services/" + serviceId + "/shelf?onShelf=true")
                        .header("Authorization", bearer(cToken)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("COMPANION_SERVICE_NOT_APPROVED"));

        // 管理端审核通过后上架成功
        auditApproveService(admin, serviceId);
        mockMvc.perform(put("/api/companion/services/" + serviceId + "/shelf?onShelf=true")
                        .header("Authorization", bearer(cToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.serviceStatus").value("ON_SHELF"));

        // 编辑后重新待审核
        ObjectNode update = objectMapper.createObjectNode()
                .put("gameId", firstGameId())
                .put("serviceTypeId", firstServiceTypeId())
                .put("title", "王者荣耀 荣耀王者带飞（改价）")
                .put("description", "稳定上分")
                .put("priceCents", 6000)
                .put("minDurationMinutes", 60);
        mockMvc.perform(put("/api/companion/services/" + serviceId)
                        .header("Authorization", bearer(cToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(update.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.auditStatus").value("PENDING"));
    }

    // ==================== FR-P10/P11 档期 ====================

    @Test
    @DisplayName("档期管理：新增可约/不可约时段，重叠被拦截（FR-P10/P11）")
    void availability_flow() throws Exception {
        String admin = adminToken();
        String cToken = prepareCompanion(admin).path("token").asText();

        String day = LocalDateTime.now().plusDays(1).format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        ObjectNode slot = objectMapper.createObjectNode()
                .put("startAt", day + " 10:00:00")
                .put("endAt", day + " 22:00:00");
        mockMvc.perform(post("/api/companion/availabilities")
                        .header("Authorization", bearer(cToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(slot.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.availabilityStatus").value("AVAILABLE"));

        // 重叠档期 → 409 SLOT_CONFLICT
        ObjectNode overlap = objectMapper.createObjectNode()
                .put("startAt", day + " 12:00:00")
                .put("endAt", day + " 14:00:00");
        mockMvc.perform(post("/api/companion/availabilities")
                        .header("Authorization", bearer(cToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(overlap.toString()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SLOT_CONFLICT"));

        // 临时不可约（不重叠）
        ObjectNode unavailable = objectMapper.createObjectNode()
                .put("startAt", day + " 08:00:00")
                .put("endAt", day + " 09:00:00")
                .put("remark", "上课");
        mockMvc.perform(post("/api/companion/availabilities/unavailable")
                        .header("Authorization", bearer(cToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(unavailable.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.availabilityStatus").value("UNAVAILABLE"));

        mockMvc.perform(get("/api/companion/availabilities")
                        .header("Authorization", bearer(cToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2));
    }

    // ==================== FR-P13~P19 订单闭环 ====================

    @Test
    @DisplayName("订单闭环：创建→支付→接单→开始→结束→确认→收益结算（FR-U07~U13、FR-P13~P19）")
    void order_full_flow() throws Exception {
        // 1. 陪玩师准备：审核通过、主页、可接单、档期、服务
        String admin = adminToken();
        JsonNode companion = prepareCompanion(admin);
        String cToken = companion.path("token").asText();
        long companionUserId = companion.path("userId").asLong();

        mockMvc.perform(put("/api/companion/profile/service-status")
                        .header("Authorization", bearer(cToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.createObjectNode()
                                .put("serviceStatus", "AVAILABLE")
                                .toString()))
                .andExpect(status().isOk());

        // 档期覆盖 [now-1h, now+5h]
        LocalDateTime now = LocalDateTime.now();
        mockMvc.perform(post("/api/companion/availabilities")
                        .header("Authorization", bearer(cToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.createObjectNode()
                                .put("startAt", now.minusHours(1).format(FMT))
                                .put("endAt", now.plusHours(5).format(FMT))
                                .toString()))
                .andExpect(status().isOk());

        long serviceId = createService(cToken);
        auditApproveService(admin, serviceId);
        mockMvc.perform(put("/api/companion/services/" + serviceId + "/shelf?onShelf=true")
                        .header("Authorization", bearer(cToken)))
                .andExpect(status().isOk());

        // 2. 用户下单（FR-U07）：预约 now+10min 起 120 分钟
        JsonNode user = registerAndLogin(uniqueUser());
        String uToken = user.path("token").asText();
        ObjectNode orderReq = objectMapper.createObjectNode()
                .put("companionUserId", companionUserId)
                .put("companionServiceId", serviceId)
                .put("durationMinutes", 120)
                .put("gameServer", "微信区")
                .put("gameNickname", "路人甲")
                .put("userRemark", "帮忙上分")
                .put("appointmentStartAt", now.plusMinutes(10).format(FMT));
        MvcResult created = mockMvc.perform(post("/api/play-orders")
                        .header("Authorization", bearer(uToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderReq.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orderStatus").value("PENDING_PAYMENT"))
                .andExpect(jsonPath("$.data.totalAmountCents").value(10000))
                .andReturn();
        long orderId = objectMapper.readTree(created.getResponse().getContentAsString())
                .path("data").path("id").asLong();

        // 3. 支付（FR-U09）：余额不足 → 422
        mockMvc.perform(post("/api/play-orders/" + orderId + "/pay")
                        .header("Authorization", bearer(uToken)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("WALLET_BALANCE_INSUFFICIENT"));

        // 测试事务内充值（等价于演示环境的"虚拟余额充值"）
        jdbcTemplate.update("INSERT INTO wallet_account (user_id, balance_cents, frozen_cents, total_income_cents, version) "
                        + "VALUES (?, ?, 0, 0, 0) ON DUPLICATE KEY UPDATE balance_cents = ?",
                user.path("user").path("id").asLong(), 20000, 20000);

        mockMvc.perform(post("/api/play-orders/" + orderId + "/pay")
                        .header("Authorization", bearer(uToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orderStatus").value("WAITING_ACCEPTANCE"));

        // 4. 陪玩师接单（FR-P13）
        mockMvc.perform(post("/api/companion/orders/" + orderId + "/accept")
                        .header("Authorization", bearer(cToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.createObjectNode()
                                .put("confirm", true)
                                .toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orderStatus").value("WAITING_SERVICE"));

        // 5. 开始服务（FR-P15）：预约时间为 now+10min，已进入可开始窗口
        mockMvc.perform(post("/api/companion/orders/" + orderId + "/start")
                        .header("Authorization", bearer(cToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orderStatus").value("IN_SERVICE"));

        // 6. 结束服务（FR-P16）
        mockMvc.perform(post("/api/companion/orders/" + orderId + "/end")
                        .header("Authorization", bearer(cToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orderStatus").value("WAITING_CONFIRMATION"));

        // 7. 用户确认完成（FR-U13）→ 陪玩师收益入账
        mockMvc.perform(post("/api/play-orders/" + orderId + "/confirm")
                        .header("Authorization", bearer(uToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orderStatus").value("COMPLETED"));

        // 8. 收益查询（FR-P19）
        mockMvc.perform(get("/api/companion/earnings")
                        .header("Authorization", bearer(cToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalIncomeCents").value(10000))
                .andExpect(jsonPath("$.data.completedOrderCount").value(1));

        mockMvc.perform(get("/api/companion/earnings/ledgers")
                        .header("Authorization", bearer(cToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[0].ledgerType").value("SETTLEMENT"))
                .andExpect(jsonPath("$.data.records[0].amountCents").value(10000));

        // 9. 订单详情含状态轨迹（FR-U11/FR-P18）：CREATE/PAY/ACCEPT/START/END/CONFIRM
        mockMvc.perform(get("/api/companion/orders/" + orderId)
                        .header("Authorization", bearer(cToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.statusHistories.length()").value(6));

        // 10. 用户侧流水可见退款/支付记录
        mockMvc.perform(get("/api/wallet/me")
                        .header("Authorization", bearer(uToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.balanceCents").value(10000));
    }

    @Test
    @DisplayName("拒绝订单：陪玩师拒绝后订单关闭并退款（FR-P14）")
    void order_reject_refunds() throws Exception {
        // 准备陪玩师
        String admin = adminToken();
        JsonNode companion = prepareCompanion(admin);
        String cToken = companion.path("token").asText();
        long companionUserId = companion.path("userId").asLong();
        mockMvc.perform(put("/api/companion/profile/service-status")
                        .header("Authorization", bearer(cToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.createObjectNode().put("serviceStatus", "AVAILABLE").toString()))
                .andExpect(status().isOk());
        LocalDateTime now = LocalDateTime.now();
        mockMvc.perform(post("/api/companion/availabilities")
                        .header("Authorization", bearer(cToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.createObjectNode()
                                .put("startAt", now.minusHours(1).format(FMT))
                                .put("endAt", now.plusHours(5).format(FMT))
                                .toString()))
                .andExpect(status().isOk());
        long serviceId = createService(cToken);
        auditApproveService(admin, serviceId);
        mockMvc.perform(put("/api/companion/services/" + serviceId + "/shelf?onShelf=true")
                        .header("Authorization", bearer(cToken)))
                .andExpect(status().isOk());

        // 用户下单并支付
        JsonNode user = registerAndLogin(uniqueUser());
        String uToken = user.path("token").asText();
        ObjectNode orderReq = objectMapper.createObjectNode()
                .put("companionUserId", companionUserId)
                .put("companionServiceId", serviceId)
                .put("durationMinutes", 60)
                .put("appointmentStartAt", now.plusMinutes(10).format(FMT));
        MvcResult created = mockMvc.perform(post("/api/play-orders")
                        .header("Authorization", bearer(uToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderReq.toString()))
                .andExpect(status().isOk())
                .andReturn();
        long orderId = objectMapper.readTree(created.getResponse().getContentAsString())
                .path("data").path("id").asLong();
        jdbcTemplate.update("INSERT INTO wallet_account (user_id, balance_cents, frozen_cents, total_income_cents, version) "
                        + "VALUES (?, ?, 0, 0, 0) ON DUPLICATE KEY UPDATE balance_cents = ?",
                user.path("user").path("id").asLong(), 10000, 10000);
        mockMvc.perform(post("/api/play-orders/" + orderId + "/pay")
                        .header("Authorization", bearer(uToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orderStatus").value("WAITING_ACCEPTANCE"));

        // 陪玩师拒绝 → CLOSED + 退款
        mockMvc.perform(post("/api/companion/orders/" + orderId + "/reject")
                        .header("Authorization", bearer(cToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.createObjectNode()
                                .put("reason", "档期冲突，无法接单")
                                .toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orderStatus").value("CLOSED"));

        // 用户钱包回到支付前余额（退款入账）
        mockMvc.perform(get("/api/wallet/me")
                        .header("Authorization", bearer(uToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.balanceCents").value(10000));
    }

    @Test
    @DisplayName("开始服务：未到允许开始时间窗口被拦截（FR-P15）")
    void start_service_window_check() throws Exception {
        // 准备陪玩师与可约服务
        String admin = adminToken();
        JsonNode companion = prepareCompanion(admin);
        String cToken = companion.path("token").asText();
        long companionUserId = companion.path("userId").asLong();
        mockMvc.perform(put("/api/companion/profile/service-status")
                        .header("Authorization", bearer(cToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.createObjectNode().put("serviceStatus", "AVAILABLE").toString()))
                .andExpect(status().isOk());
        LocalDateTime now = LocalDateTime.now();
        mockMvc.perform(post("/api/companion/availabilities")
                        .header("Authorization", bearer(cToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.createObjectNode()
                                .put("startAt", now.minusHours(1).format(FMT))
                                .put("endAt", now.plusHours(8).format(FMT))
                                .toString()))
                .andExpect(status().isOk());
        long serviceId = createService(cToken);
        auditApproveService(admin, serviceId);
        mockMvc.perform(put("/api/companion/services/" + serviceId + "/shelf?onShelf=true")
                        .header("Authorization", bearer(cToken)))
                .andExpect(status().isOk());

        // 用户下单：预约时间在 4 小时后（未到窗口）
        JsonNode user = registerAndLogin(uniqueUser());
        String uToken = user.path("token").asText();
        ObjectNode orderReq = objectMapper.createObjectNode()
                .put("companionUserId", companionUserId)
                .put("companionServiceId", serviceId)
                .put("durationMinutes", 60)
                .put("appointmentStartAt", now.plusHours(4).format(FMT));
        MvcResult created = mockMvc.perform(post("/api/play-orders")
                        .header("Authorization", bearer(uToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderReq.toString()))
                .andExpect(status().isOk())
                .andReturn();
        long orderId = objectMapper.readTree(created.getResponse().getContentAsString())
                .path("data").path("id").asLong();

        // 支付 + 接单后尝试开始服务 → 未到窗口被拦截
        jdbcTemplate.update("INSERT INTO wallet_account (user_id, balance_cents, frozen_cents, total_income_cents, version) "
                        + "VALUES (?, ?, 0, 0, 0) ON DUPLICATE KEY UPDATE balance_cents = ?",
                user.path("user").path("id").asLong(), 10000, 10000);
        mockMvc.perform(post("/api/play-orders/" + orderId + "/pay")
                        .header("Authorization", bearer(uToken)))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/companion/orders/" + orderId + "/accept")
                        .header("Authorization", bearer(cToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.createObjectNode().put("confirm", true).toString()))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/companion/orders/" + orderId + "/start")
                        .header("Authorization", bearer(cToken)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ORDER_START_TIME_INVALID"));
    }

    @Test
    @DisplayName("权限校验：非陪玩师不能提交服务/档期；非本人不能查看他人申请")
    void permission_checks() throws Exception {
        // 普通用户（非陪玩师）调用陪玩师接口 → 403
        JsonNode normal = registerAndLogin(uniqueUser());
        String nToken = normal.path("token").asText();
        mockMvc.perform(get("/api/companion/profile")
                        .header("Authorization", bearer(nToken)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("COMPANION_NOT_APPROVED"));

        // 申请详情只能本人查看
        String admin = adminToken();
        JsonNode a = registerAndLogin(uniqueUser());
        String aToken = a.path("token").asText();
        long appId = submitApplication(aToken);
        mockMvc.perform(get("/api/companion/applications/" + appId)
                        .header("Authorization", bearer(nToken)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PERMISSION_DATA_SCOPE_DENIED"));
    }

    // ==================== FR-M10~M12 目录引用校验与停用收口 ====================

    @Test
    @DisplayName("目录删除护栏：被服务引用的游戏/服务类型/标签返回 409 CATALOG_IN_USE")
    void catalog_delete_referenced_blocks() throws Exception {
        String admin = adminToken();
        JsonNode companion = prepareCompanion(admin);
        String cToken = companion.path("token").asText();
        long gameId = firstGameId();
        long typeId = firstServiceTypeId();
        long tagId = firstTagId(gameId);

        // 创建引用了「该游戏 + 该类型 + 该标签」的服务
        long serviceId = createServiceWithTag(cToken, gameId, typeId, tagId);
        assertThat(serviceId).isPositive();

        mockMvc.perform(delete("/api/admin/service-types/" + typeId)
                        .header("Authorization", bearer(admin)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CATALOG_IN_USE"));

        mockMvc.perform(delete("/api/admin/tags/" + tagId)
                        .header("Authorization", bearer(admin)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CATALOG_IN_USE"));

        mockMvc.perform(delete("/api/admin/games/" + gameId)
                        .header("Authorization", bearer(admin)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CATALOG_IN_USE"));

        // 三个目录项都还在（未被误删）
        mockMvc.perform(get("/api/games/" + gameId)).andExpect(status().isOk());
        mockMvc.perform(get("/api/service-types")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("停用游戏收口：服务从公开列表隐藏，且直接下单被拒（P4/P7）")
    void disabled_game_hides_service_and_blocks_order() throws Exception {
        String admin = adminToken();
        JsonNode companion = prepareCompanion(admin);
        String cToken = companion.path("token").asText();
        long companionUserId = companion.path("userId").asLong();
        long gameId = firstGameId();
        String gameName = gameName(gameId);

        // 陪玩师可接单 + 档期覆盖，服务过审并上架
        mockMvc.perform(put("/api/companion/profile/service-status")
                        .header("Authorization", bearer(cToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.createObjectNode().put("serviceStatus", "AVAILABLE").toString()))
                .andExpect(status().isOk());
        LocalDateTime now = LocalDateTime.now();
        mockMvc.perform(post("/api/companion/availabilities")
                        .header("Authorization", bearer(cToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.createObjectNode()
                                .put("startAt", now.minusHours(1).format(FMT))
                                .put("endAt", now.plusHours(5).format(FMT))
                                .toString()))
                .andExpect(status().isOk());
        long serviceId = createService(cToken);
        auditApproveService(admin, serviceId);
        mockMvc.perform(put("/api/companion/services/" + serviceId + "/shelf?onShelf=true")
                        .header("Authorization", bearer(cToken)))
                .andExpect(status().isOk());

        // 停用前：公开列表可见
        assertThat(publicListHasService(serviceId, companionUserId)).isTrue();

        // 管理员停用该游戏
        setGameEnabled(admin, gameId, gameName, 0);

        // P4：公开列表不再返回该服务
        assertThat(publicListHasService(serviceId, companionUserId)).isFalse();

        // P7：拿到 serviceId 直接下单也要被拒（修复前会下单成功）
        JsonNode user = registerAndLogin(uniqueUser());
        mockMvc.perform(post("/api/play-orders")
                        .header("Authorization", bearer(user.path("token").asText()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.createObjectNode()
                                .put("companionUserId", companionUserId)
                                .put("companionServiceId", serviceId)
                                .put("durationMinutes", 120)
                                .put("gameServer", "微信区")
                                .put("gameNickname", "路人乙")
                                .put("userRemark", "停用游戏直接下单")
                                .put("appointmentStartAt", now.plusMinutes(30).format(FMT))
                                .toString()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("SERVICE_NOT_AVAILABLE"));

        // 恢复启用后服务重新可见（停用是可逆的）
        setGameEnabled(admin, gameId, gameName, 1);
        assertThat(publicListHasService(serviceId, companionUserId)).isTrue();
    }

    /** 创建带标签的服务项目 */
    private long createServiceWithTag(String token, long gameId, long serviceTypeId, long tagId) throws Exception {
        ObjectNode req = objectMapper.createObjectNode()
                .put("gameId", gameId)
                .put("serviceTypeId", serviceTypeId)
                .put("title", "目录校验服务" + uniqueUser())
                .put("description", "用于目录引用校验")
                .put("priceCents", 5000)
                .put("minDurationMinutes", 60);
        req.putArray("tagIds").add(tagId);
        MvcResult result = mockMvc.perform(post("/api/companion/services")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(req.toString()))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data").path("id").asLong();
    }

    /** 取指定游戏的第一个已启用标签 id */
    private long firstTagId(long gameId) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/tags").param("gameId", String.valueOf(gameId)))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode data = objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
        assertThat(data.size()).isPositive();
        return data.get(0).path("id").asLong();
    }

    /** 游戏名称（管理端更新为全量更新，需回填既有字段） */
    private String gameName(long gameId) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/games")).andExpect(status().isOk()).andReturn();
        JsonNode data = objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
        for (JsonNode game : data) {
            if (game.path("id").asLong() == gameId) {
                return game.path("gameName").asText();
            }
        }
        throw new IllegalStateException("游戏不存在: " + gameId);
    }

    /** 管理员启用/停用游戏 */
    private void setGameEnabled(String adminToken, long gameId, String gameName, int enabled) throws Exception {
        mockMvc.perform(put("/api/admin/games/" + gameId)
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.createObjectNode()
                                .put("gameName", gameName)
                                .put("gameIconUrl", "")
                                .put("gameIntro", "")
                                .put("sortNo", 1)
                                .put("enabled", enabled)
                                .toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"));
    }

    /** 公开可预约服务列表中是否存在指定服务（分页 total 也应同步变化） */
    private boolean publicListHasService(long serviceId, long companionUserId) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/companion-services")
                        .param("companionUserId", String.valueOf(companionUserId))
                        .param("page", "1")
                        .param("size", "50"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode data = objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
        for (JsonNode record : data.path("records")) {
            if (record.path("id").asLong() == serviceId) {
                return true;
            }
        }
        return false;
    }
}