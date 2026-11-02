package com.gameplay.order;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * order 模块集成测试：陪玩师结束服务时的履约凭证闭环。
 *
 * <p>覆盖：无请求体兼容旧调用；携带 5 张凭证结束服务后，凭证随状态迁移保存，
 * 并可从买家/陪玩师订单详情及管理员投诉详情读取；超过 5 张被参数校验拒绝；
 * 状态迁移失败时不落凭证；非订单参与者无法从订单详情或投诉详情读取凭证。</p>
 *
 * <p>依赖：本机 MySQL 已执行 sql/schema.sql（含 order_delivery_evidence 表）与
 * sql/data.sql（admin 种子账号及目录数据），且已配置 application-local.yml。
 * 测试数据通过 {@link Transactional} 回滚，不污染现有数据。</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class OrderDeliveryEvidenceFlowTest {

    private static final String PASSWORD = "Passw0rd123";
    private static final String ADMIN_ACCOUNT = "admin";
    private static final String ADMIN_PASSWORD = "Admin@123456";
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final AtomicInteger SEQ = new AtomicInteger();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Value("${file.storage.dir:./uploads}")
    private String fileStorageDir;

    private final List<Path> uploadedTestFiles = new ArrayList<>();

    @AfterEach
    void removeUploadedTestFiles() throws Exception {
        for (Path path : uploadedTestFiles) {
            Files.deleteIfExists(path);
        }
        uploadedTestFiles.clear();
    }

    // ==================== 测试数据与认证辅助 ====================

    private String uniqueUser() {
        return "ode" + System.currentTimeMillis() + "_" + SEQ.incrementAndGet();
    }

    private String nextMobile() {
        return "1" + (8_000_000_000L + SEQ.incrementAndGet());
    }

    /** 注册并登录，返回 {token, user} */
    private JsonNode registerAndLogin(String username) throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.createObjectNode()
                                .put("username", username)
                                .put("password", PASSWORD)
                                .put("nickname", "履约凭证测试")
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

    private String loginAndGetToken(String account, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.createObjectNode()
                                .put("account", account)
                                .put("password", password)
                                .toString()))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data").path("token").asText();
    }

    private String adminToken() throws Exception {
        return loginAndGetToken(ADMIN_ACCOUNT, ADMIN_PASSWORD);
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    /** 准备一个已审核通过、可接单、有档期和上架服务的陪玩师，返回 {token, userId, serviceId} */
    private JsonNode prepareCompanion(String admin) throws Exception {
        JsonNode companion = registerAndLogin(uniqueUser());
        String companionToken = companion.path("token").asText();

        // 入驻申请 + 管理员审核通过
        ObjectNode capability = objectMapper.createObjectNode()
                .put("gameId", firstGameId())
                .put("gameName", "王者荣耀")
                .put("server", "微信区")
                .put("rank", "最强王者");
        ObjectNode application = objectMapper.createObjectNode()
                .put("realName", "李四")
                .put("contactMobile", nextMobile())
                .put("introduction", "王者荣耀资深玩家");
        application.set("capabilities", objectMapper.createArrayNode().add(capability));
        application.set("proofUrls", objectMapper.createArrayNode());
        MvcResult applicationResult = mockMvc.perform(post("/api/companion/applications")
                        .header("Authorization", bearer(companionToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(application.toString()))
                .andExpect(status().isOk())
                .andReturn();
        long applicationId = objectMapper.readTree(applicationResult.getResponse().getContentAsString())
                .path("data").path("id").asLong();
        mockMvc.perform(post("/api/admin/companion-applications/" + applicationId + "/audit")
                        .header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.createObjectNode()
                                .put("approved", true)
                                .put("reason", "通过")
                                .toString()))
                .andExpect(status().isOk());

        // 审核通过后重新登录，获取包含 COMPANION 角色的 JWT
        String newToken = loginAndGetToken(companion.path("user").path("username").asText(), PASSWORD);
        long companionUserId = companion.path("user").path("id").asLong();

        // 接单状态 + 覆盖后续预约的档期
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
                                .put("endAt", now.plusHours(5).format(FMT))
                                .toString()))
                .andExpect(status().isOk());

        long serviceId = createService(newToken, admin);

        ObjectNode result = objectMapper.createObjectNode();
        result.put("token", newToken);
        result.put("userId", companionUserId);
        result.put("serviceId", serviceId);
        return result;
    }

    /** 陪玩师创建服务 -> 管理员审核通过 -> 上架，返回服务 id */
    private long createService(String companionToken, String admin) throws Exception {
        ObjectNode request = objectMapper.createObjectNode()
                .put("gameId", firstGameId())
                .put("serviceTypeId", firstServiceTypeId())
                .put("title", "王者荣耀 上分陪玩")
                .put("description", "稳定上分")
                .put("priceCents", 5000)
                .put("minDurationMinutes", 60);
        request.set("tagIds", objectMapper.createArrayNode());
        MvcResult result = mockMvc.perform(post("/api/companion/services")
                        .header("Authorization", bearer(companionToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request.toString()))
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
                        .header("Authorization", bearer(companionToken)))
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

    /** 用户下单（60 分钟，预约 now+10min），返回订单 id */
    private long createOrder(String buyerToken, JsonNode companion) throws Exception {
        ObjectNode request = objectMapper.createObjectNode()
                .put("companionUserId", companion.path("userId").asLong())
                .put("companionServiceId", companion.path("serviceId").asLong())
                .put("durationMinutes", 60)
                .put("gameServer", "微信区")
                .put("gameNickname", "下单用户")
                .put("appointmentStartAt", LocalDateTime.now().plusMinutes(10).format(FMT));
        MvcResult created = mockMvc.perform(post("/api/play-orders")
                        .header("Authorization", bearer(buyerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orderStatus").value("PENDING_PAYMENT"))
                .andReturn();
        return objectMapper.readTree(created.getResponse().getContentAsString())
                .path("data").path("id").asLong();
    }

    /** 给用户钱包直接充值（演示环境无充值接口，测试数据事务回滚） */
    private void recharge(long userId, long amountCents) {
        jdbcTemplate.update("INSERT INTO wallet_account (user_id, balance_cents, frozen_cents, total_income_cents, version) "
                        + "VALUES (?, ?, 0, 0, 0) ON DUPLICATE KEY UPDATE balance_cents = ?",
                userId, amountCents, amountCents);
    }

    /**
     * 准备一笔已支付、已接单、已开始服务的订单。
     * 返回 {orderId, buyerId, buyerToken, companionId, companionToken}。
     */
    private JsonNode prepareInServiceOrder() throws Exception {
        String admin = adminToken();
        JsonNode companion = prepareCompanion(admin);
        JsonNode buyer = registerAndLogin(uniqueUser());
        long buyerId = buyer.path("user").path("id").asLong();
        String buyerToken = buyer.path("token").asText();
        recharge(buyerId, 10000);

        long orderId = createOrder(buyerToken, companion);
        mockMvc.perform(post("/api/play-orders/" + orderId + "/pay")
                        .header("Authorization", bearer(buyerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orderStatus").value("WAITING_ACCEPTANCE"));
        mockMvc.perform(post("/api/companion/orders/" + orderId + "/accept")
                        .header("Authorization", bearer(companion.path("token").asText()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.createObjectNode().put("confirm", true).toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orderStatus").value("WAITING_SERVICE"));
        mockMvc.perform(post("/api/companion/orders/" + orderId + "/start")
                        .header("Authorization", bearer(companion.path("token").asText())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orderStatus").value("IN_SERVICE"));

        ObjectNode result = objectMapper.createObjectNode();
        result.put("orderId", orderId);
        result.put("buyerId", buyerId);
        result.put("buyerToken", buyerToken);
        result.put("companionId", companion.path("userId").asLong());
        result.put("companionToken", companion.path("token").asText());
        return result;
    }

    /** 生成 count 个符合上传接口格式的履约凭证地址（8 位日期 + 32 位 UUID + 图片扩展名） */
    private List<String> newEvidenceUrls(int count) {
        String day = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        List<String> urls = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            urls.add("/api/files/delivery/" + day + "/"
                    + UUID.randomUUID().toString().replace("-", "") + ".png");
        }
        return urls;
    }

    private ArrayNode evidenceArray(List<String> urls) {
        ArrayNode evidences = objectMapper.createArrayNode();
        for (int i = 0; i < urls.size(); i++) {
            evidences.add(objectMapper.createObjectNode()
                    .put("fileUrl", urls.get(i))
                    .put("fileName", "履约凭证" + (i + 1) + ".png"));
        }
        return evidences;
    }

    private ResultActions endWithEvidences(String companionToken, long orderId, List<String> urls) throws Exception {
        ObjectNode body = objectMapper.createObjectNode();
        body.set("evidences", evidenceArray(urls));
        return mockMvc.perform(post("/api/companion/orders/" + orderId + "/end")
                .header("Authorization", bearer(companionToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body.toString()));
    }

    private int evidenceCount(long orderId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM order_delivery_evidence WHERE order_id = ?", Integer.class, orderId);
        return count == null ? 0 : count;
    }

    private List<String> evidenceUrlsInDb(long orderId) {
        return jdbcTemplate.queryForList(
                "SELECT file_url FROM order_delivery_evidence WHERE order_id = ? ORDER BY sort_no",
                String.class, orderId);
    }

    private String uploadEvidence(String companionToken) throws Exception {
        MockMultipartFile image = new MockMultipartFile("file", "履约测试.png", "image/png",
                new byte[]{(byte) 0x89, 'P', 'N', 'G'});
        MvcResult result = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .multipart("/api/files/upload")
                        .file(image)
                        .param("category", "delivery")
                        .header("Authorization", bearer(companionToken)))
                .andExpect(status().isOk())
                .andReturn();
        String url = objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data").path("url").asText();
        Path storedPath = Paths.get(fileStorageDir).toAbsolutePath().normalize()
                .resolve(url.substring("/api/files/".length())).normalize();
        uploadedTestFiles.add(storedPath);
        return url;
    }

    // ==================== 履约凭证 ====================

    @Test
    @DisplayName("结束服务：无请求体兼容旧调用，订单进入待确认且不产生凭证")
    void end_without_body_is_backward_compatible() throws Exception {
        JsonNode order = prepareInServiceOrder();
        long orderId = order.path("orderId").asLong();
        String buyerToken = order.path("buyerToken").asText();
        String companionToken = order.path("companionToken").asText();

        mockMvc.perform(post("/api/companion/orders/" + orderId + "/end")
                        .header("Authorization", bearer(companionToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.orderStatus").value("WAITING_CONFIRMATION"))
                .andExpect(jsonPath("$.data.deliveryEvidences.length()").value(0));

        assertThat(evidenceCount(orderId)).isZero();
        mockMvc.perform(get("/api/play-orders/" + orderId)
                        .header("Authorization", bearer(buyerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orderStatus").value("WAITING_CONFIRMATION"))
                .andExpect(jsonPath("$.data.deliveryEvidences.length()").value(0));
    }

    @Test
    @DisplayName("结束服务：5 张凭证随状态迁移保存，买家/陪玩师/管理员投诉详情均可读取")
    void end_with_five_evidences_persists_and_is_visible_to_authorized_readers() throws Exception {
        JsonNode order = prepareInServiceOrder();
        long orderId = order.path("orderId").asLong();
        String buyerToken = order.path("buyerToken").asText();
        String companionToken = order.path("companionToken").asText();
        List<String> urls = newEvidenceUrls(5);

        endWithEvidences(companionToken, orderId, urls)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.orderStatus").value("WAITING_CONFIRMATION"))
                .andExpect(jsonPath("$.data.deliveryEvidences.length()").value(5))
                .andExpect(jsonPath("$.data.deliveryEvidences[0].fileUrl").value(urls.get(0)))
                .andExpect(jsonPath("$.data.deliveryEvidences[0].fileName").value("履约凭证1.png"))
                .andExpect(jsonPath("$.data.deliveryEvidences[4].fileUrl").value(urls.get(4)))
                .andExpect(jsonPath("$.data.deliveryEvidences[4].fileName").value("履约凭证5.png"));

        // 同一事务内落库 5 条，且 sort_no 顺序与请求顺序一致
        assertThat(evidenceCount(orderId)).isEqualTo(5);
        assertThat(evidenceUrlsInDb(orderId)).containsExactlyElementsOf(urls);

        // 买家订单详情可读
        mockMvc.perform(get("/api/play-orders/" + orderId)
                        .header("Authorization", bearer(buyerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.deliveryEvidences.length()").value(5))
                .andExpect(jsonPath("$.data.deliveryEvidences[2].fileUrl").value(urls.get(2)));

        // 陪玩师订单详情可读
        mockMvc.perform(get("/api/companion/orders/" + orderId)
                        .header("Authorization", bearer(companionToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.deliveryEvidences.length()").value(5))
                .andExpect(jsonPath("$.data.deliveryEvidences[4].fileUrl").value(urls.get(4)));

        // 买家发起投诉（待确认状态可投诉），投诉视图沿用同一订单的履约凭证
        MvcResult complaintResult = mockMvc.perform(post("/api/complaints")
                        .header("Authorization", bearer(buyerToken))
                        .param("orderId", String.valueOf(orderId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.createObjectNode()
                                .put("complaintType", "OTHER")
                                .put("description", "核对履约凭证读取范围")
                                .toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.deliveryEvidences.length()").value(5))
                .andReturn();
        long complaintId = objectMapper.readTree(complaintResult.getResponse().getContentAsString())
                .path("data").path("id").asLong();

        // 订单转入售后后凭证仍可读
        mockMvc.perform(get("/api/play-orders/" + orderId)
                        .header("Authorization", bearer(buyerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orderStatus").value("AFTER_SALES"))
                .andExpect(jsonPath("$.data.deliveryEvidences.length()").value(5));

        // 管理员投诉详情可读
        String admin = adminToken();
        mockMvc.perform(get("/api/admin/complaints/" + complaintId)
                        .header("Authorization", bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.deliveryEvidences.length()").value(5))
                .andExpect(jsonPath("$.data.deliveryEvidences[0].fileUrl").value(urls.get(0)))
                .andExpect(jsonPath("$.data.deliveryEvidences[4].fileUrl").value(urls.get(4)));

        assertThat(evidenceCount(orderId)).isEqualTo(5);
    }

    @Test
    @DisplayName("结束服务：超过 5 张凭证被校验拒绝，订单状态与凭证均不变化")
    void end_with_more_than_five_evidences_is_rejected() throws Exception {
        JsonNode order = prepareInServiceOrder();
        long orderId = order.path("orderId").asLong();
        String buyerToken = order.path("buyerToken").asText();
        String companionToken = order.path("companionToken").asText();
        List<String> urls = newEvidenceUrls(6);

        endWithEvidences(companionToken, orderId, urls)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        assertThat(evidenceCount(orderId)).isZero();
        mockMvc.perform(get("/api/play-orders/" + orderId)
                        .header("Authorization", bearer(buyerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orderStatus").value("IN_SERVICE"))
                .andExpect(jsonPath("$.data.deliveryEvidences.length()").value(0));
    }

    @Test
    @DisplayName("结束服务：状态迁移失败时不写入任何履约凭证")
    void end_with_invalid_state_leaves_no_evidence() throws Exception {
        String admin = adminToken();
        JsonNode companion = prepareCompanion(admin);
        JsonNode buyer = registerAndLogin(uniqueUser());
        String buyerToken = buyer.path("token").asText();
        String companionToken = companion.path("token").asText();
        recharge(buyer.path("user").path("id").asLong(), 10000);

        // 仅支付并接单，不开始服务；此时结束服务应被状态机拒绝
        long orderId = createOrder(buyerToken, companion);
        mockMvc.perform(post("/api/play-orders/" + orderId + "/pay")
                        .header("Authorization", bearer(buyerToken)))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/companion/orders/" + orderId + "/accept")
                        .header("Authorization", bearer(companionToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.createObjectNode().put("confirm", true).toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orderStatus").value("WAITING_SERVICE"));

        List<String> urls = newEvidenceUrls(1);
        endWithEvidences(companionToken, orderId, urls)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ORDER_STATUS_INVALID"));

        assertThat(evidenceCount(orderId)).isZero();
        mockMvc.perform(get("/api/play-orders/" + orderId)
                        .header("Authorization", bearer(buyerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orderStatus").value("WAITING_SERVICE"))
                .andExpect(jsonPath("$.data.deliveryEvidences.length()").value(0));
    }

    @Test
    @DisplayName("履约凭证：非订单参与者无法从订单详情或投诉详情读取")
    void delivery_evidence_is_not_readable_by_non_participant() throws Exception {
        JsonNode order = prepareInServiceOrder();
        long orderId = order.path("orderId").asLong();
        String buyerToken = order.path("buyerToken").asText();
        String companionToken = order.path("companionToken").asText();
        List<String> urls = newEvidenceUrls(1);
        endWithEvidences(companionToken, orderId, urls)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.deliveryEvidences.length()").value(1));

        JsonNode outsider = registerAndLogin(uniqueUser());
        String outsiderToken = outsider.path("token").asText();

        // 非参与者无法读取订单详情（因此也读不到履约凭证）
        mockMvc.perform(get("/api/play-orders/" + orderId)
                        .header("Authorization", bearer(outsiderToken)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PERMISSION_DATA_SCOPE_DENIED"));

        // 买家发起投诉后，非参与者同样无法读取投诉详情中的履约凭证
        MvcResult complaintResult = mockMvc.perform(post("/api/complaints")
                        .header("Authorization", bearer(buyerToken))
                        .param("orderId", String.valueOf(orderId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.createObjectNode()
                                .put("complaintType", "OTHER")
                                .put("description", "越权读取校验")
                                .toString()))
                .andExpect(status().isOk())
                .andReturn();
        long complaintId = objectMapper.readTree(complaintResult.getResponse().getContentAsString())
                .path("data").path("id").asLong();

        mockMvc.perform(get("/api/complaints/" + complaintId)
                        .header("Authorization", bearer(outsiderToken)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PERMISSION_DATA_SCOPE_DENIED"));
    }

    @Test
    @DisplayName("履约凭证文件：仅订单参与者和管理员可直接读取图片")
    void delivery_file_access_is_scoped_to_order_participants_and_admin() throws Exception {
        JsonNode order = prepareInServiceOrder();
        long orderId = order.path("orderId").asLong();
        String buyerToken = order.path("buyerToken").asText();
        String companionToken = order.path("companionToken").asText();
        String evidenceUrl = uploadEvidence(companionToken);

        endWithEvidences(companionToken, orderId, List.of(evidenceUrl))
                .andExpect(status().isOk());

        String filePath = evidenceUrl.substring("/api".length());
        mockMvc.perform(get(filePath))
                .andExpect(status().isUnauthorized());

        JsonNode outsider = registerAndLogin(uniqueUser());
        mockMvc.perform(get(filePath)
                        .header("Authorization", bearer(outsider.path("token").asText())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PERMISSION_DATA_SCOPE_DENIED"));

        for (String token : List.of(buyerToken, companionToken, adminToken())) {
            mockMvc.perform(get(filePath).header("Authorization", bearer(token)))
                    .andExpect(status().isOk())
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                            .content().contentType("image/png"));
        }
    }
}
