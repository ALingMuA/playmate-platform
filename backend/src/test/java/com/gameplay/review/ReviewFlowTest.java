package com.gameplay.review;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gameplay.companion.domain.CompanionProfile;
import com.gameplay.companion.mapper.CompanionProfileMapper;
import com.gameplay.order.domain.PlayOrder;
import com.gameplay.order.mapper.PlayOrderMapper;
import com.gameplay.wallet.domain.WalletAccount;
import com.gameplay.wallet.mapper.WalletAccountMapper;
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
 * review 模块集成测试：评价、投诉与仲裁闭环（FR-U15~U19、FR-M08/M18/M19）。
 *
 * <p>覆盖：已确认订单评价与评分重算、重复评价拦截、未完成订单评价拦截、
 * 发起投诉后订单进入售后、进行中投诉不可重复发起、管理员全额退款仲裁（资金闭环）、
 * 完成后超过 72 小时不可投诉。</p>
 *
 * <p>依赖：本机 MySQL 已执行 sql/schema.sql 与 sql/data.sql（admin 种子账号与目录数据），
 * 且已配置 application-local.yml。数据在事务中回滚。</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ReviewFlowTest {

    private static final String PASSWORD = "Passw0rd123";
    private static final String ADMIN_ACCOUNT = "admin";
    private static final String ADMIN_PASSWORD = "Admin@123456";
    private static final AtomicInteger SEQ = new AtomicInteger();
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private WalletAccountMapper walletAccountMapper;

    @Autowired
    private CompanionProfileMapper companionProfileMapper;

    @Autowired
    private PlayOrderMapper playOrderMapper;

    /** 注册并登录，返回 {id, token} */
    private com.fasterxml.jackson.databind.node.ObjectNode registerAndLogin() throws Exception {
        String username = "rv" + System.currentTimeMillis() + "_" + SEQ.incrementAndGet();
        MvcResult registered = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.createObjectNode()
                                .put("username", username)
                                .put("password", PASSWORD)
                                .put("nickname", "测试_" + username)
                                .put("mobile", "1" + (5_000_000_000L + SEQ.incrementAndGet()))
                                .toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andReturn();
        long id = objectMapper.readTree(registered.getResponse().getContentAsString())
                .path("data").path("id").asLong();
        String token = loginAndGetToken(username, PASSWORD);
        return objectMapper.createObjectNode()
                .put("id", id)
                .put("token", token);
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

    /** 给用户钱包直接充值（演示环境无充值接口，测试数据事务回滚） */
    private void recharge(long userId, long cents) {
        WalletAccount wallet = new WalletAccount();
        wallet.setUserId(userId);
        wallet.setBalanceCents(cents);
        wallet.setFrozenCents(0L);
        wallet.setTotalIncomeCents(0L);
        wallet.setVersion(0);
        walletAccountMapper.insert(wallet);
    }

    /** 查询第一个启用的游戏 ID（种子数据可能多次初始化，ID 不固定） */
    private long firstGameId() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/games"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode array = objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data");
        assertThat(array.isArray() && array.size() > 0).isTrue();
        return array.get(0).path("id").asLong();
    }

    /** 查询第一个启用的服务类型 ID（种子数据多次初始化，ID 不固定） */
    private long firstServiceTypeId() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/service-types"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode array = objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data");
        assertThat(array.isArray() && array.size() > 0).isTrue();
        return array.get(0).path("id").asLong();
    }

    /** 提交入驻申请并由管理员审核通过 */
    private void approveCompanionApplication(String companionToken, long gameId) throws Exception {
        com.fasterxml.jackson.databind.node.ObjectNode capability = objectMapper.createObjectNode()
                .put("gameId", gameId)
                .put("gameName", "测试游戏")
                .put("server", "微信区")
                .put("rank", "王者");
        capability.set("positionTagIds", objectMapper.createArrayNode().add(1));
        com.fasterxml.jackson.databind.node.ObjectNode applicationBody = objectMapper.createObjectNode()
                .put("realName", "测试陪玩")
                .put("contactMobile", "1" + (5_000_000_000L + SEQ.incrementAndGet()))
                .put("introduction", "集成测试入驻申请");
        applicationBody.set("capabilities", objectMapper.createArrayNode().add(capability));
        applicationBody.set("proofUrls", objectMapper.createArrayNode());
        mockMvc.perform(post("/api/companion/applications")
                        .header("Authorization", "Bearer " + companionToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(applicationBody.toString()))
                .andExpect(status().isOk())
                .andReturn();

        String adminToken = loginAndGetToken(ADMIN_ACCOUNT, ADMIN_PASSWORD);
        MvcResult list = mockMvc.perform(get("/api/admin/companion-applications")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("status", "PENDING"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode records = objectMapper.readTree(list.getResponse().getContentAsString())
                .path("data").path("records");
        long applicationId = 0;
        for (JsonNode node : records) {
            if ("测试陪玩".equals(node.path("realName").asText())
                    && node.path("auditStatus").asText().equals("PENDING")) {
                applicationId = node.path("id").asLong();
            }
        }
        assertThat(applicationId).isPositive();
        mockMvc.perform(post("/api/admin/companion-applications/" + applicationId + "/audit")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.createObjectNode()
                                .put("approved", true)
                                .put("reason", "测试通过")
                                .toString()))
                .andExpect(status().isOk())
                .andReturn();
    }

    /** 陪玩师创建服务 -> 管理员审核通过 -> 上架，返回服务 id */
    private long setupService(String companionToken, long gameId, long serviceTypeId) throws Exception {
        String adminToken = loginAndGetToken(ADMIN_ACCOUNT, ADMIN_PASSWORD);
        com.fasterxml.jackson.databind.node.ObjectNode serviceBody = objectMapper.createObjectNode()
                .put("gameId", gameId)
                .put("serviceTypeId", serviceTypeId)
                .put("title", "测试上分服务")
                .put("description", "集成测试服务")
                .put("priceCents", 6000)
                .put("minDurationMinutes", 60);
        serviceBody.set("tagIds", objectMapper.createArrayNode().add(6));
        MvcResult created = mockMvc.perform(post("/api/companion/services")
                        .header("Authorization", "Bearer " + companionToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(serviceBody.toString()))
                .andExpect(status().isOk())
                .andReturn();
        long serviceId = objectMapper.readTree(created.getResponse().getContentAsString())
                .path("data").path("id").asLong();
        assertThat(serviceId).isPositive();

        mockMvc.perform(post("/api/admin/companion-services/" + serviceId + "/audit")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.createObjectNode()
                                .put("approved", true)
                                .put("reason", "测试通过")
                                .toString()))
                .andExpect(status().isOk())
                .andReturn();
        mockMvc.perform(put("/api/companion/services/" + serviceId + "/shelf")
                        .header("Authorization", "Bearer " + companionToken)
                        .param("onShelf", "true"))
                .andExpect(status().isOk())
                .andReturn();
        return serviceId;
    }

    /** 陪玩师创建覆盖未来 2 小时的档期（从当前时间开始，覆盖预约的 now+1 分钟） */
    private void setupAvailability(String companionToken) throws Exception {
        LocalDateTime start = LocalDateTime.now();
        LocalDateTime end = start.plusHours(2);
        mockMvc.perform(post("/api/companion/availabilities")
                        .header("Authorization", "Bearer " + companionToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.createObjectNode()
                                .put("startAt", start.format(FMT))
                                .put("endAt", end.format(FMT))
                                .put("remark", "集成测试档期")
                                .toString()))
                .andExpect(status().isOk())
                .andReturn();
    }

    /** 准备一对买家/陪玩师（陪玩师已入驻+服务+档期），返回 {buyerId, buyerToken, companionId, companionToken, serviceId} */
    private JsonNode prepareParties() throws Exception {
        long gameId = firstGameId();
        long serviceTypeId = firstServiceTypeId();
        JsonNode buyer = registerAndLogin();
        JsonNode companion = registerAndLogin();
        recharge(buyer.path("id").asLong(), 100_000L);
        approveCompanionApplication(companion.path("token").asText(), gameId);
        // 审核通过后默认休息中，需设为可接单（FR-P06）
        mockMvc.perform(put("/api/companion/profile/service-status")
                        .header("Authorization", "Bearer " + companion.path("token").asText())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"serviceStatus\":\"AVAILABLE\"}"))
                .andExpect(status().isOk())
                .andReturn();
        long serviceId = setupService(companion.path("token").asText(), gameId, serviceTypeId);
        setupAvailability(companion.path("token").asText());
        return objectMapper.createObjectNode()
                .put("buyerId", buyer.path("id").asLong())
                .put("buyerToken", buyer.path("token").asText())
                .put("companionId", companion.path("id").asLong())
                .put("companionToken", companion.path("token").asText())
                .put("serviceId", serviceId);
    }

    /**
     * 完整走一遍订单：下单 -> 支付 -> 接单 -> 开始 -> 结束 -> 确认完成，返回订单 id。
     * 预约开始时间设为 now+1 分钟（开始服务允许提前 15 分钟，避免等待）。
     */
    private long setupCompletedOrder(String buyerToken, String companionToken,
                                     long companionUserId, long serviceId) throws Exception {
        LocalDateTime start = LocalDateTime.now().plusMinutes(1);
        MvcResult created = mockMvc.perform(post("/api/play-orders")
                        .header("Authorization", "Bearer " + buyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.createObjectNode()
                                .put("companionUserId", companionUserId)
                                .put("companionServiceId", serviceId)
                                .put("durationMinutes", 60)
                                .put("gameServer", "微信区")
                                .put("gameNickname", "测试玩家")
                                .put("userRemark", "集成测试订单")
                                .put("appointmentStartAt", start.format(FMT))
                                .toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andReturn();
        long orderId = objectMapper.readTree(created.getResponse().getContentAsString())
                .path("data").path("id").asLong();

        mockMvc.perform(post("/api/play-orders/" + orderId + "/pay")
                        .header("Authorization", "Bearer " + buyerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orderStatus").value("WAITING_ACCEPTANCE"))
                .andReturn();
        mockMvc.perform(post("/api/companion/orders/" + orderId + "/accept")
                        .header("Authorization", "Bearer " + companionToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"confirm\":true}"))
                .andExpect(status().isOk())
                .andReturn();
        mockMvc.perform(post("/api/companion/orders/" + orderId + "/start")
                        .header("Authorization", "Bearer " + companionToken))
                .andExpect(status().isOk())
                .andReturn();
        mockMvc.perform(post("/api/companion/orders/" + orderId + "/end")
                        .header("Authorization", "Bearer " + companionToken))
                .andExpect(status().isOk())
                .andReturn();
        mockMvc.perform(post("/api/play-orders/" + orderId + "/confirm")
                        .header("Authorization", "Bearer " + buyerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orderStatus").value("COMPLETED"))
                .andReturn();
        return orderId;
    }

    // ==================== 完整闭环（FR-U15~U19、FR-M08/M18/M19） ====================

    @Test
    @DisplayName("完整闭环：评价评分重算 -> 投诉售后 -> 管理员全额退款仲裁 -> 资金闭环")
    void review_complaint_full_flow() throws Exception {
        JsonNode parties = prepareParties();
        long buyerId = parties.path("buyerId").asLong();
        String buyerToken = parties.path("buyerToken").asText();
        long companionId = parties.path("companionId").asLong();
        String companionToken = parties.path("companionToken").asText();
        long serviceId = parties.path("serviceId").asLong();

        long orderId = setupCompletedOrder(buyerToken, companionToken, companionId, serviceId);

        // 1. 提交评价（FR-U15）
        com.fasterxml.jackson.databind.node.ObjectNode reviewBody = objectMapper.createObjectNode()
                .put("score", 5)
                .put("content", "王者荣耀上分服务体验很好，准时且耐心");
        reviewBody.set("tags", objectMapper.createArrayNode().add("上分快").add("脾气好"));
        mockMvc.perform(post("/api/reviews")
                        .header("Authorization", "Bearer " + buyerToken)
                        .param("orderId", String.valueOf(orderId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reviewBody.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.score").value(5))
                .andExpect(jsonPath("$.data.displayStatus").value("VISIBLE"))
                .andReturn();

        // 2. 评分重算（FR-U16）
        CompanionProfile profile = companionProfileMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<CompanionProfile>()
                        .eq(CompanionProfile::getUserId, companionId));
        assertThat(profile.getRatingCount()).isEqualTo(1);
        assertThat(profile.getRatingAvg()).isEqualByComparingTo("5.0");

        // 3. 公开评价列表（游客可访问）
        mockMvc.perform(get("/api/reviews/companion/" + companionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].content").value("王者荣耀上分服务体验很好，准时且耐心"))
                .andReturn();

        // 4. 发起投诉（FR-U17/U18）：完成后 72 小时内，携带图片证据
        com.fasterxml.jackson.databind.node.ObjectNode evidenceItem = objectMapper.createObjectNode()
                .put("fileUrl", "/api/files/evidence/20260811/test.jpg")
                .put("fileName", "test.jpg");
        com.fasterxml.jackson.databind.node.ObjectNode complaintBody = objectMapper.createObjectNode()
                .put("complaintType", "LATE")
                .put("description", "陪玩师迟到 30 分钟，体验差");
        complaintBody.set("evidences", objectMapper.createArrayNode().add(evidenceItem));
        mockMvc.perform(post("/api/complaints")
                        .header("Authorization", "Bearer " + buyerToken)
                        .param("orderId", String.valueOf(orderId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(complaintBody.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.complaintStatus").value("PENDING"))
                .andExpect(jsonPath("$.data.evidences.length()").value(1))
                .andReturn();

        // 5. 订单进入售后中
        mockMvc.perform(get("/api/play-orders/" + orderId)
                        .header("Authorization", "Bearer " + buyerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orderStatus").value("AFTER_SALES"))
                .andReturn();

        // 6. 进行中投诉不可重复发起
        mockMvc.perform(post("/api/complaints")
                        .header("Authorization", "Bearer " + buyerToken)
                        .param("orderId", String.valueOf(orderId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.createObjectNode()
                                .put("complaintType", "ATTITUDE")
                                .put("description", "重复投诉")
                                .toString()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("COMPLAINT_ALREADY_EXISTS"))
                .andReturn();

        // 7. 管理员查询投诉并全额退款仲裁（FR-M18/M19）
        String adminToken = loginAndGetToken(ADMIN_ACCOUNT, ADMIN_PASSWORD);
        MvcResult list = mockMvc.perform(get("/api/admin/complaints")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("status", "PENDING"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode complaintNode = objectMapper.readTree(list.getResponse().getContentAsString())
                .path("data").path("records").get(0);
        long complaintId = complaintNode.path("id").asLong();
        assertThat(complaintId).isPositive();

        mockMvc.perform(post("/api/admin/complaints/" + complaintId + "/handle")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.createObjectNode()
                                .put("resolutionType", "FULL_REFUND")
                                .put("handlingOpinion", "核实迟到属实，全额退款")
                                .toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.complaintStatus").value("RESOLVED"))
                .andExpect(jsonPath("$.data.resolutionType").value("FULL_REFUND"))
                .andExpect(jsonPath("$.data.refundAmountCents").value(6000))
                .andReturn();

        // 8. 订单全额退款后关闭
        mockMvc.perform(get("/api/play-orders/" + orderId)
                        .header("Authorization", "Bearer " + buyerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orderStatus").value("CLOSED"))
                .andReturn();

        // 9. 资金闭环：买家余额恢复为充值额，陪玩师收益被扣回（余额 0）
        MvcResult buyerWallet = mockMvc.perform(get("/api/wallet/me")
                        .header("Authorization", "Bearer " + buyerToken))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(objectMapper.readTree(buyerWallet.getResponse().getContentAsString())
                .path("data").path("balanceCents").asLong()).isEqualTo(100_000L);
        MvcResult companionWallet = mockMvc.perform(get("/api/wallet/me")
                        .header("Authorization", "Bearer " + companionToken))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(objectMapper.readTree(companionWallet.getResponse().getContentAsString())
                .path("data").path("balanceCents").asLong()).isZero();
    }

    @Test
    @DisplayName("评价规则：未完成订单不能评价；重复评价返回 409")
    void review_rules() throws Exception {
        JsonNode parties = prepareParties();
        long buyerId = parties.path("buyerId").asLong();
        String buyerToken = parties.path("buyerToken").asText();
        long companionId = parties.path("companionId").asLong();
        String companionToken = parties.path("companionToken").asText();
        long serviceId = parties.path("serviceId").asLong();

        // 未完成订单（待支付）不能评价
        LocalDateTime start = LocalDateTime.now().plusMinutes(1);
        MvcResult created = mockMvc.perform(post("/api/play-orders")
                        .header("Authorization", "Bearer " + buyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.createObjectNode()
                                .put("companionUserId", companionId)
                                .put("companionServiceId", serviceId)
                                .put("durationMinutes", 60)
                                .put("appointmentStartAt", start.format(FMT))
                                .toString()))
                .andExpect(status().isOk())
                .andReturn();
        long pendingOrderId = objectMapper.readTree(created.getResponse().getContentAsString())
                .path("data").path("id").asLong();
        mockMvc.perform(post("/api/reviews")
                        .header("Authorization", "Bearer " + buyerToken)
                        .param("orderId", String.valueOf(pendingOrderId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.createObjectNode()
                                .put("score", 5)
                                .put("content", "未完成订单评价")
                                .toString()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("REVIEW_ORDER_NOT_ELIGIBLE"))
                .andReturn();

        // 取消未支付订单释放临时档期，避免后续订单时段冲突
        mockMvc.perform(post("/api/play-orders/" + pendingOrderId + "/cancel")
                        .header("Authorization", "Bearer " + buyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"测试取消\"}"))
                .andExpect(status().isOk())
                .andReturn();

        // 完成订单后评价，再重复评价被拦截
        long doneOrderId = setupCompletedOrder(buyerToken, companionToken, companionId, serviceId);
        for (int i = 0; i < 2; i++) {
            int expectStatus = i == 0 ? 200 : 409;
            String expectCode = i == 0 ? "SUCCESS" : "REVIEW_ALREADY_EXISTS";
            mockMvc.perform(post("/api/reviews")
                            .header("Authorization", "Bearer " + buyerToken)
                            .param("orderId", String.valueOf(doneOrderId))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.createObjectNode()
                                    .put("score", 4)
                                    .put("content", "重复评价测试")
                                    .toString()))
                    .andExpect(status().is(expectStatus))
                    .andExpect(jsonPath("$.code").value(expectCode))
                    .andReturn();
        }
    }

    @Test
    @DisplayName("投诉规则：完成后超过 72 小时不能投诉")
    void complaint_window_expired() throws Exception {
        JsonNode parties = prepareParties();
        long buyerId = parties.path("buyerId").asLong();
        String buyerToken = parties.path("buyerToken").asText();
        long companionId = parties.path("companionId").asLong();
        String companionToken = parties.path("companionToken").asText();
        long serviceId = parties.path("serviceId").asLong();

        long orderId = setupCompletedOrder(buyerToken, companionToken, companionId, serviceId);

        // 把确认时间改到 5 天前，模拟超过 72 小时投诉窗口
        playOrderMapper.update(null, new UpdateWrapper<PlayOrder>()
                .eq("id", orderId)
                .set("confirmed_at", LocalDateTime.now().minusDays(5)));

        mockMvc.perform(post("/api/complaints")
                        .header("Authorization", "Bearer " + buyerToken)
                        .param("orderId", String.valueOf(orderId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.createObjectNode()
                                .put("complaintType", "OTHER")
                                .put("description", "超窗投诉")
                                .toString()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("COMPLAINT_ORDER_NOT_ELIGIBLE"))
                .andReturn();
    }
}
