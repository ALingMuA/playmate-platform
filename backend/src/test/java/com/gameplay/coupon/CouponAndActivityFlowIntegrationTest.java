package com.gameplay.coupon;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.gameplay.coupon.domain.UserCoupon;
import com.gameplay.coupon.enums.UserCouponStatus;
import com.gameplay.coupon.mapper.UserCouponMapper;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class CouponAndActivityFlowIntegrationTest {

    private static final String PASSWORD = "Passw0rd123";
    private static final AtomicInteger SEQ = new AtomicInteger();
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private UserCouponMapper userCouponMapper;

    private String uniqueUser() {
        return "cpn" + System.currentTimeMillis() + "_" + SEQ.incrementAndGet();
    }

    private String nextMobile() {
        return "1" + (7_500_000_000L + SEQ.incrementAndGet());
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private JsonNode registerAndLogin(String username) throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.createObjectNode()
                                .put("username", username)
                                .put("password", PASSWORD)
                                .put("nickname", "优惠测试")
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

    private JsonNode prepareCompanion(String admin) throws Exception {
        JsonNode companion = registerAndLogin(uniqueUser());
        String cToken = companion.path("token").asText();

        ObjectNode cap = objectMapper.createObjectNode()
                .put("gameId", firstGameId())
                .put("gameName", "王者荣耀")
                .put("server", "微信区")
                .put("rank", "最强王者");
        ObjectNode appReq = objectMapper.createObjectNode()
                .put("realName", "张三")
                .put("contactMobile", nextMobile())
                .put("introduction", "资深陪玩大神")
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
                                .put("startAt", now.format(FMT))
                                .put("endAt", now.plusHours(12).format(FMT))
                                .toString()))
                .andExpect(status().isOk());

        // 服务价格 10000 分 (100元/小时)
        ObjectNode sReq = objectMapper.createObjectNode()
                .put("gameId", firstGameId())
                .put("serviceTypeId", firstServiceTypeId())
                .put("title", "排位上分服务")
                .put("description", "包胜教学")
                .put("priceCents", 10000)
                .put("minDurationMinutes", 60);
        MvcResult sResult = mockMvc.perform(post("/api/companion/services")
                        .header("Authorization", bearer(newToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(sReq.toString()))
                .andExpect(status().isOk())
                .andReturn();
        long serviceId = objectMapper.readTree(sResult.getResponse().getContentAsString())
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
                        .header("Authorization", bearer(newToken)))
                .andExpect(status().isOk());

        ObjectNode res = objectMapper.createObjectNode();
        res.put("token", newToken);
        res.put("userId", companionUserId);
        res.put("serviceId", serviceId);
        return res;
    }

    @Test
    @DisplayName("平台活动管理与公开展示生命周期")
    void testActivityLifecycle() throws Exception {
        String adminTok = adminToken();

        // 1. 管理端创建草稿活动
        ObjectNode actReq = objectMapper.createObjectNode()
                .put("title", "测试狂欢活动")
                .put("subtitle", "立享特惠")
                .put("description", "活动描述内容")
                .put("actionType", "COUPON_CLAIM")
                .put("sortOrder", 10)
                .put("startTime", LocalDateTime.now().minusDays(1).format(FMT))
                .put("endTime", LocalDateTime.now().plusDays(10).format(FMT));

        MvcResult createRes = mockMvc.perform(post("/api/admin/activities")
                        .header("Authorization", bearer(adminTok))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(actReq.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("测试狂欢活动"))
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andReturn();

        long actId = objectMapper.readTree(createRes.getResponse().getContentAsString()).path("data").path("id").asLong();

        // 2. 发布活动
        mockMvc.perform(post("/api/admin/activities/" + actId + "/publish")
                        .header("Authorization", bearer(adminTok)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PUBLISHED"));

        // 3. 用户端匿名获取公开活动列表
        mockMvc.perform(get("/api/activities"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());

        // 4. 用户端获取活动详情
        mockMvc.perform(get("/api/activities/" + actId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(actId))
                .andExpect(jsonPath("$.data.title").value("测试狂欢活动"));

        // 5. 下线与删除
        mockMvc.perform(post("/api/admin/activities/" + actId + "/offline")
                        .header("Authorization", bearer(adminTok)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("OFFLINE"));

        mockMvc.perform(delete("/api/admin/activities/" + actId)
                        .header("Authorization", bearer(adminTok)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("优惠券模板发布、领券、限领控制与我的优惠券查询")
    void testCouponTemplateAndClaimFlow() throws Exception {
        String adminTok = adminToken();
        JsonNode userSession = registerAndLogin(uniqueUser());
        String userTok = userSession.path("token").asText();

        // 1. 管理端创建模板（满100减25，限领1张，总量2张）
        ObjectNode cpnReq = objectMapper.createObjectNode()
                .put("name", "测试满减券")
                .put("couponType", "FIXED")
                .put("thresholdCents", 10000L)
                .put("discountCents", 2500L)
                .put("scopeType", "ALL")
                .put("totalQuantity", 2)
                .put("perUserLimit", 1)
                .put("validFrom", LocalDateTime.now().minusDays(1).format(FMT))
                .put("validUntil", LocalDateTime.now().plusDays(5).format(FMT));

        MvcResult createRes = mockMvc.perform(post("/api/admin/coupons")
                        .header("Authorization", bearer(adminTok))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cpnReq.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("测试满减券"))
                .andReturn();

        long templateId = objectMapper.readTree(createRes.getResponse().getContentAsString()).path("data").path("id").asLong();

        // 2. 发布模板
        mockMvc.perform(post("/api/admin/coupons/" + templateId + "/publish")
                        .header("Authorization", bearer(adminTok)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.publishStatus").value("PUBLISHED"));

        // 3. 用户第1次领券成功
        MvcResult claimRes = mockMvc.perform(post("/api/coupons/" + templateId + "/claim")
                        .header("Authorization", bearer(userTok)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("AVAILABLE"))
                .andExpect(jsonPath("$.data.templateName").value("测试满减券"))
                .andReturn();

        long userCouponId = objectMapper.readTree(claimRes.getResponse().getContentAsString()).path("data").path("id").asLong();
        assertThat(userCouponId).isPositive();

        // 4. 用户重复领券，应触发 422 限领异常
        mockMvc.perform(post("/api/coupons/" + templateId + "/claim")
                        .header("Authorization", bearer(userTok)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("COUPON_CLAIM_LIMIT_EXCEEDED"));

        // 5. 查询“我的优惠券”
        mockMvc.perform(get("/api/coupons/mine?status=AVAILABLE")
                        .header("Authorization", bearer(userTok)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records").isArray())
                .andExpect(jsonPath("$.data.total").value(1));
    }

    @Test
    @DisplayName("下单选择优惠券、金额预览、锁券、支付核销与平台补贴结算")
    void testCouponOrderPreviewAndPaymentFlow() throws Exception {
        String adminTok = adminToken();
        JsonNode companion = prepareCompanion(adminTok);
        long compUserId = companion.path("userId").asLong();
        long serviceId = companion.path("serviceId").asLong();

        // 顾客
        JsonNode custUser = registerAndLogin(uniqueUser());
        String custTok = custUser.path("token").asText();
        long custUserId = custUser.path("user").path("id").asLong();

        // 给顾客充值 500 元 (50000分)
        jdbcTemplate.update("INSERT INTO wallet_account (user_id, balance_cents, frozen_cents, total_income_cents, version) VALUES (?, 50000, 0, 0, 0) " +
                "ON DUPLICATE KEY UPDATE balance_cents = balance_cents + 50000", custUserId);

        // 领一张新人券 (id=1, 种子数据: 无门槛减20元 = 2000分)
        MvcResult claimRes = mockMvc.perform(post("/api/coupons/1/claim")
                        .header("Authorization", bearer(custTok)))
                .andExpect(status().isOk())
                .andReturn();
        long userCouponId = objectMapper.readTree(claimRes.getResponse().getContentAsString()).path("data").path("id").asLong();

        // 预览优惠：1小时服务原价 10000分，优惠 2000分，实付 8000分
        mockMvc.perform(post("/api/coupons/preview")
                        .header("Authorization", bearer(custTok))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.createObjectNode()
                                .put("companionServiceId", serviceId)
                                .put("durationMinutes", 60)
                                .put("couponId", userCouponId)
                                .toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.originalAmountCents").value(10000))
                .andExpect(jsonPath("$.data.discountAmountCents").value(2000))
                .andExpect(jsonPath("$.data.payableAmountCents").value(8000));

        // 下单带入优惠券
        LocalDateTime startAt = LocalDateTime.now().plusHours(2).withMinute(0).withSecond(0);
        ObjectNode orderReq = objectMapper.createObjectNode()
                .put("companionUserId", compUserId)
                .put("companionServiceId", serviceId)
                .put("durationMinutes", 60)
                .put("appointmentStartAt", startAt.format(FMT))
                .put("couponId", userCouponId);

        MvcResult orderRes = mockMvc.perform(post("/api/play-orders")
                        .header("Authorization", bearer(custTok))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderReq.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.originalAmountCents").value(10000))
                .andExpect(jsonPath("$.data.discountAmountCents").value(2000))
                .andExpect(jsonPath("$.data.totalAmountCents").value(8000))
                .andExpect(jsonPath("$.data.settlementAmountCents").value(10000))
                .andExpect(jsonPath("$.data.couponId").value(userCouponId))
                .andReturn();

        long orderId = objectMapper.readTree(orderRes.getResponse().getContentAsString()).path("data").path("id").asLong();

        // 校验券状态变为 LOCKED
        UserCoupon uc = userCouponMapper.selectById(userCouponId);
        assertThat(uc.getStatus()).isEqualTo(UserCouponStatus.LOCKED.name());
        assertThat(uc.getLockedOrderId()).isEqualTo(orderId);

        // 支付订单（钱包应扣实付 8000 分）
        mockMvc.perform(post("/api/play-orders/" + orderId + "/pay")
                        .header("Authorization", bearer(custTok)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orderStatus").value("WAITING_ACCEPTANCE"));

        // 校验券状态变为 USED
        uc = userCouponMapper.selectById(userCouponId);
        assertThat(uc.getStatus()).isEqualTo(UserCouponStatus.USED.name());
        assertThat(uc.getUsedOrderId()).isEqualTo(orderId);

        // 校验顾客余额：50000 - 8000 = 42000
        Long custBalance = jdbcTemplate.queryForObject("SELECT balance_cents FROM wallet_account WHERE user_id = ?", Long.class, custUserId);
        assertThat(custBalance).isEqualTo(42000L);
    }

    @Test
    @DisplayName("未支付订单取消后，优惠券正常释放并可重新使用")
    void testCouponOrderCancelAndReleaseFlow() throws Exception {
        String adminTok = adminToken();
        JsonNode companion = prepareCompanion(adminTok);
        long compUserId = companion.path("userId").asLong();
        long serviceId = companion.path("serviceId").asLong();

        JsonNode custUser = registerAndLogin(uniqueUser());
        String custTok = custUser.path("token").asText();

        // 领券 (id=1, 20元立减)
        MvcResult claimRes = mockMvc.perform(post("/api/coupons/1/claim")
                        .header("Authorization", bearer(custTok)))
                .andExpect(status().isOk())
                .andReturn();
        long userCouponId = objectMapper.readTree(claimRes.getResponse().getContentAsString()).path("data").path("id").asLong();

        // 下单锁定
        LocalDateTime startAt = LocalDateTime.now().plusHours(3).withMinute(0).withSecond(0);
        MvcResult orderRes = mockMvc.perform(post("/api/play-orders")
                        .header("Authorization", bearer(custTok))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.createObjectNode()
                                .put("companionUserId", compUserId)
                                .put("companionServiceId", serviceId)
                                .put("durationMinutes", 60)
                                .put("appointmentStartAt", startAt.format(FMT))
                                .put("couponId", userCouponId)
                                .toString()))
                .andExpect(status().isOk())
                .andReturn();

        long orderId = objectMapper.readTree(orderRes.getResponse().getContentAsString()).path("data").path("id").asLong();
        assertThat(userCouponMapper.selectById(userCouponId).getStatus()).isEqualTo(UserCouponStatus.LOCKED.name());

        // 用户主动取消未支付订单
        mockMvc.perform(post("/api/play-orders/" + orderId + "/cancel")
                        .header("Authorization", bearer(custTok))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.createObjectNode().put("reason", "不打算玩了").toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orderStatus").value("CLOSED"));

        // 优惠券应自动恢复为 AVAILABLE
        UserCoupon uc = userCouponMapper.selectById(userCouponId);
        assertThat(uc.getStatus()).isEqualTo(UserCouponStatus.AVAILABLE.name());
        assertThat(uc.getLockedOrderId()).isNull();
    }
}
