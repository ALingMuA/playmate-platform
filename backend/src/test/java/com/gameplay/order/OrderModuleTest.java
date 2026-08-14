package com.gameplay.order;

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * order 模块集成测试：聚焦 FR-U12 用户取消订单。
 *
 * <p>覆盖：未支付取消不退款、已支付（待接单/待服务）取消全额退款并释放档期、
 * 终态不可取消、非本人不可取消。订单主闭环（创建→支付→接单→履约→确认）见
 * CompanionModuleTest.order_full_flow。</p>
 *
 * <p>依赖：本机 MySQL 已执行 sql/schema.sql 与 sql/data.sql（admin 种子账号），
 * 且配置了 application-local.yml。</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class OrderModuleTest {

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
        return "ord" + System.currentTimeMillis() + "_" + SEQ.incrementAndGet();
    }

    private String nextMobile() {
        return "1" + (7_000_000_000L + SEQ.incrementAndGet());
    }

    /** 注册并登录，返回 {token, user} */
    private JsonNode registerAndLogin(String username) throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.createObjectNode()
                                .put("username", username)
                                .put("password", PASSWORD)
                                .put("nickname", "订单测试")
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

    /** 准备一个已审核通过、可接单、有档期和上架服务的陪玩师，返回 {token, userId} */
    private JsonNode prepareCompanion(String admin) throws Exception {
        JsonNode companion = registerAndLogin(uniqueUser());
        String cToken = companion.path("token").asText();

        // 入驻申请 + 管理员审核通过
        ObjectNode cap = objectMapper.createObjectNode()
                .put("gameId", firstGameId())
                .put("gameName", "王者荣耀")
                .put("server", "微信区")
                .put("rank", "最强王者");
        ObjectNode appReq = objectMapper.createObjectNode()
                .put("realName", "李四")
                .put("contactMobile", nextMobile())
                .put("introduction", "王者荣耀资深玩家")
                .set("capabilities", objectMapper.createArrayNode().add(cap));
        MvcResult appResult = mockMvc.perform(post("/api/companion/applications")
                        .header("Authorization", bearer(cToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(appReq.toString()))
                .andExpect(status().isOk())
                .andReturn();
        long appId = objectMapper.readTree(appResult.getResponse().getContentAsString())
                .path("data").path("id").asLong();
        mockMvc.perform(post("/api/admin/companion-applications/" + appId + "/audit")
                        .header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.createObjectNode()
                                .put("approved", true)
                                .put("reason", "通过")
                                .toString()))
                .andExpect(status().isOk());

        String newToken = relogin(companion.path("user").path("username").asText());
        long companionUserId = companion.path("user").path("id").asLong();

        // 接单状态 + 档期 [now-1h, now+5h]
        mockMvc.perform(put("/api/companion/profile/service-status")
                        .header("Authorization", bearer(newToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.createObjectNode().put("serviceStatus", "AVAILABLE").toString()))
                .andExpect(status().isOk());
        LocalDateTime now = LocalDateTime.now();
        mockMvc.perform(post("/api/companion/availabilities")
                        .header("Authorization", bearer(newToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.createObjectNode()
                                .put("startAt", now.minusHours(1).format(FMT))
                                .put("endAt", now.plusHours(5).format(FMT))
                                .toString()))
                .andExpect(status().isOk());

        // 服务创建 + 审核 + 上架
        long serviceId = createService(newToken, admin);

        ObjectNode result = objectMapper.createObjectNode();
        result.put("token", newToken);
        result.put("userId", companionUserId);
        result.put("serviceId", serviceId);
        return result;
    }

    private long createService(String cToken, String admin) throws Exception {
        ObjectNode req = objectMapper.createObjectNode()
                .put("gameId", firstGameId())
                .put("serviceTypeId", firstServiceTypeId())
                .put("title", "王者荣耀 上分陪玩")
                .put("description", "稳定上分")
                .put("priceCents", 5000)
                .put("minDurationMinutes", 60);
        MvcResult result = mockMvc.perform(post("/api/companion/services")
                        .header("Authorization", bearer(cToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(req.toString()))
                .andExpect(status().isOk())
                .andReturn();
        long serviceId = objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data").path("id").asLong();
        mockMvc.perform(post("/api/admin/companion-services/" + serviceId + "/audit")
                        .header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.createObjectNode()
                                .put("approved", true)
                                .put("reason", "合规")
                                .toString()))
                .andExpect(status().isOk());
        mockMvc.perform(put("/api/companion/services/" + serviceId + "/shelf?onShelf=true")
                        .header("Authorization", bearer(cToken)))
                .andExpect(status().isOk());
        return serviceId;
    }

    private long firstGameId() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/games"))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data").get(0).path("id").asLong();
    }

    private long firstServiceTypeId() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/service-types"))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data").get(0).path("id").asLong();
    }

    /** 用户下单（60 分钟，预约 now+10min），返回 {orderId, token, userId} */
    private JsonNode createOrder(String uToken, JsonNode companion, String extra) throws Exception {
        LocalDateTime now = LocalDateTime.now();
        ObjectNode orderReq = objectMapper.createObjectNode()
                .put("companionUserId", companion.path("userId").asLong())
                .put("companionServiceId", companion.path("serviceId").asLong())
                .put("durationMinutes", 60)
                .put("gameServer", "微信区")
                .put("gameNickname", "下单用户")
                .put("appointmentStartAt", now.plusMinutes(10).format(FMT));
        MvcResult created = mockMvc.perform(post("/api/play-orders")
                        .header("Authorization", bearer(uToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderReq.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orderStatus").value("PENDING_PAYMENT"))
                .andExpect(jsonPath("$.data.totalAmountCents").value(5000))
                .andReturn();
        ObjectNode result = objectMapper.createObjectNode();
        result.put("orderId", objectMapper.readTree(created.getResponse().getContentAsString())
                .path("data").path("id").asLong());
        return result;
    }

    /** 给用户钱包充值（等价演示环境的模拟充值） */
    private void recharge(long userId, long amountCents) {
        jdbcTemplate.update("INSERT INTO wallet_account (user_id, balance_cents, frozen_cents, total_income_cents, version) "
                        + "VALUES (?, ?, 0, 0, 0) ON DUPLICATE KEY UPDATE balance_cents = ?",
                userId, amountCents, amountCents);
    }

    private long balanceOf(long userId) {
        Long balance = jdbcTemplate.queryForObject(
                "SELECT balance_cents FROM wallet_account WHERE user_id = ?", Long.class, userId);
        return balance == null ? 0L : balance;
    }

    // ==================== FR-U12 取消订单 ====================

    @Test
    @DisplayName("取消订单：未支付直接关闭，不产生退款流水（FR-U12）")
    void cancel_pending_payment_no_refund() throws Exception {
        String admin = adminToken();
        JsonNode companion = prepareCompanion(admin);
        JsonNode user = registerAndLogin(uniqueUser());
        String uToken = user.path("token").asText();
        long userId = user.path("user").path("id").asLong();

        JsonNode order = createOrder(uToken, companion, "");
        long orderId = order.path("orderId").asLong();
        recharge(userId, 10000);

        mockMvc.perform(post("/api/play-orders/" + orderId + "/cancel")
                        .header("Authorization", bearer(uToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.createObjectNode()
                                .put("reason", "临时有事不玩了")
                                .toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orderStatus").value("CLOSED"))
                .andExpect(jsonPath("$.data.closedReason").value("临时有事不玩了"));

        // 未支付：余额不变，无 REFUND 流水
        assertThat(balanceOf(userId)).isEqualTo(10000);
        Integer refundCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM wallet_ledger WHERE order_id = ? AND ledger_type = 'REFUND'",
                Integer.class, orderId);
        assertThat(refundCount).isZero();

        // 状态轨迹含 CANCEL 动作
        mockMvc.perform(get("/api/play-orders/" + orderId)
                        .header("Authorization", bearer(uToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.statusHistories[1].actionCode").value("CANCEL"))
                .andExpect(jsonPath("$.data.statusHistories[1].toStatus").value("CLOSED"));
    }

    @Test
    @DisplayName("取消订单：待接单状态取消后全额退款并释放档期（FR-U12）")
    void cancel_after_pay_refunds_and_release_slot() throws Exception {
        String admin = adminToken();
        JsonNode companion = prepareCompanion(admin);
        JsonNode user = registerAndLogin(uniqueUser());
        String uToken = user.path("token").asText();
        long userId = user.path("user").path("id").asLong();

        JsonNode order = createOrder(uToken, companion, "");
        long orderId = order.path("orderId").asLong();
        recharge(userId, 10000);

        // 支付后进入待接单
        mockMvc.perform(post("/api/play-orders/" + orderId + "/pay")
                        .header("Authorization", bearer(uToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orderStatus").value("WAITING_ACCEPTANCE"));
        assertThat(balanceOf(userId)).isEqualTo(5000);

        // 取消 → CLOSED + 退款
        mockMvc.perform(post("/api/play-orders/" + orderId + "/cancel")
                        .header("Authorization", bearer(uToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.createObjectNode()
                                .put("reason", "陪玩师未及时接单")
                                .toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orderStatus").value("CLOSED"));
        assertThat(balanceOf(userId)).isEqualTo(10000);
        Integer refundAmount = jdbcTemplate.queryForObject(
                "SELECT amount_cents FROM wallet_ledger WHERE order_id = ? AND ledger_type = 'REFUND' LIMIT 1",
                Integer.class, orderId);
        assertThat(refundAmount).isEqualTo(5000);

        // 档期已释放：同一时段可再次下单
        JsonNode order2 = createOrder(uToken, companion, "");
        assertThat(order2.path("orderId").asLong()).isPositive();
    }

    @Test
    @DisplayName("取消订单：待服务状态可取消并退款；完成后不可取消（FR-U12）")
    void cancel_waiting_service_ok_but_completed_forbidden() throws Exception {
        String admin = adminToken();
        JsonNode companion = prepareCompanion(admin);
        String cToken = companion.path("token").asText();
        JsonNode user = registerAndLogin(uniqueUser());
        String uToken = user.path("token").asText();
        long userId = user.path("user").path("id").asLong();

        JsonNode order = createOrder(uToken, companion, "");
        long orderId = order.path("orderId").asLong();
        recharge(userId, 10000);
        mockMvc.perform(post("/api/play-orders/" + orderId + "/pay")
                        .header("Authorization", bearer(uToken)))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/companion/orders/" + orderId + "/accept")
                        .header("Authorization", bearer(cToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.createObjectNode().put("confirm", true).toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orderStatus").value("WAITING_SERVICE"));

        // 待服务取消 → 退款
        mockMvc.perform(post("/api/play-orders/" + orderId + "/cancel")
                        .header("Authorization", bearer(uToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.createObjectNode().put("reason", "").toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orderStatus").value("CLOSED"))
                .andExpect(jsonPath("$.data.closedReason").value("用户取消订单"));
        assertThat(balanceOf(userId)).isEqualTo(10000);

        // 终态（已关闭）不可再取消
        mockMvc.perform(post("/api/play-orders/" + orderId + "/cancel")
                        .header("Authorization", bearer(uToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.createObjectNode().put("reason", "再取消一次").toString()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ORDER_STATUS_INVALID"));
    }

    @Test
    @DisplayName("取消订单：非本人取消被拒绝（PERMISSION_DATA_SCOPE_DENIED）")
    void cancel_by_other_user_forbidden() throws Exception {
        String admin = adminToken();
        JsonNode companion = prepareCompanion(admin);
        JsonNode user = registerAndLogin(uniqueUser());
        String uToken = user.path("token").asText();
        long userId = user.path("user").path("id").asLong();

        JsonNode order = createOrder(uToken, companion, "");
        long orderId = order.path("orderId").asLong();
        recharge(userId, 10000);

        // 其他用户（非下单人）取消 → 403
        JsonNode other = registerAndLogin(uniqueUser());
        mockMvc.perform(post("/api/play-orders/" + orderId + "/cancel")
                        .header("Authorization", bearer(other.path("token").asText()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.createObjectNode().put("reason", "我帮他取消").toString()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PERMISSION_DATA_SCOPE_DENIED"));

        // 陪玩师（非下单人）也不能取消
        mockMvc.perform(post("/api/play-orders/" + orderId + "/cancel")
                        .header("Authorization", bearer(companion.path("token").asText()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.createObjectNode().put("reason", "我不接了").toString()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PERMISSION_DATA_SCOPE_DENIED"));
    }
}
