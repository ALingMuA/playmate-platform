package com.gameplay.admin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gameplay.companion.domain.CompanionProfile;
import com.gameplay.companion.mapper.CompanionProfileMapper;
import com.gameplay.notification.service.NotificationService;
import com.gameplay.audit.service.OperationLogService;
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

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 管理端模块集成测试：用户管理（FR-M03/M04）、公告（FR-A08/M13）、
 * 站内通知（FR-A07）、收藏（FR-U06）、数据概览（FR-M02）、操作日志（FR-M20）。
 *
 * <p>依赖：本机 MySQL 已执行 sql/schema.sql 与 sql/data.sql（admin 种子账号），
 * 且已配置 application-local.yml。数据在事务中回滚。</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AdminModuleTest {

    private static final String PASSWORD = "Passw0rd123";
    private static final String ADMIN_ACCOUNT = "admin";
    private static final String ADMIN_PASSWORD = "Admin@123456";
    private static final AtomicInteger SEQ = new AtomicInteger();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private CompanionProfileMapper companionProfileMapper;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private OperationLogService operationLogService;

    private String adminToken() throws Exception {
        return loginAndGetToken(ADMIN_ACCOUNT, ADMIN_PASSWORD);
    }

    private String registerAndGetToken() throws Exception {
        String username = "adm" + System.currentTimeMillis() + "_" + SEQ.incrementAndGet();
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.createObjectNode()
                                .put("username", username)
                                .put("password", PASSWORD)
                                .put("nickname", "测试_" + username)
                                .put("mobile", "1" + (8_000_000_000L + SEQ.incrementAndGet()))
                                .toString()))
                .andExpect(status().isOk());
        return loginAndGetToken(username, PASSWORD);
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

    // ==================== 用户管理 ====================

    @Test
    @DisplayName("用户查询与禁用后旧令牌失效（FR-M03/M04）")
    void user_query_and_disable() throws Exception {
        String token = registerAndGetToken();
        // 查询列表（keyword 匹配）
        mockMvc.perform(get("/api/admin/users")
                        .header("Authorization", "Bearer " + adminToken())
                        .param("keyword", "adm")
                        .param("page", "1").param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.records").isArray());

        // 获取刚注册用户 ID
        MvcResult listResult = mockMvc.perform(get("/api/admin/users")
                        .header("Authorization", "Bearer " + adminToken())
                        .param("keyword", "adm").param("page", "1").param("size", "50"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode records = objectMapper.readTree(listResult.getResponse().getContentAsString())
                .path("data").path("records");
        long userId = -1;
        for (JsonNode node : records) {
            if (node.path("username").asText().startsWith("adm")) {
                userId = node.path("id").asLong();
                break;
            }
        }
        assertThat(userId).isPositive();

        // 禁用（必须填原因）
        mockMvc.perform(put("/api/admin/users/" + userId + "/status")
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.createObjectNode()
                                .put("accountStatus", "DISABLED")
                                .put("reason", "测试禁用")
                                .toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"));

        // 旧令牌立即失效（禁用状态返回 403 AUTH_ACCOUNT_DISABLED）
        mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_ACCOUNT_DISABLED"));
    }

    // ==================== 公告 ====================

    @Test
    @DisplayName("公告全生命周期（FR-A08/M13）")
    void announcement_lifecycle() throws Exception {
        // 创建草稿
        MvcResult created = mockMvc.perform(post("/api/admin/announcements")
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.createObjectNode()
                                .put("title", "系统维护公告")
                                .put("content", "本周日凌晨系统维护")
                                .toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.publishStatus").value("DRAFT"))
                .andReturn();
        long id = objectMapper.readTree(created.getResponse().getContentAsString())
                .path("data").path("id").asLong();

        // 发布前游客不可见
        mockMvc.perform(get("/api/announcements"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(0));

        // 发布
        mockMvc.perform(post("/api/admin/announcements/" + id + "/publish")
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.publishStatus").value("PUBLISHED"));

        // 游客可见
        mockMvc.perform(get("/api/announcements"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1));
        mockMvc.perform(get("/api/announcements/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("系统维护公告"));

        // 已发布不可编辑/删除
        mockMvc.perform(put("/api/admin/announcements/" + id)
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.createObjectNode()
                                .put("title", "改标题").put("content", "改内容").toString()))
                .andExpect(jsonPath("$.code").value("ANNOUNCEMENT_STATUS_INVALID"));

        // 撤回后游客不可见
        mockMvc.perform(post("/api/admin/announcements/" + id + "/revoke")
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"));
        mockMvc.perform(get("/api/announcements"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(0));
    }

    // ==================== 站内通知 ====================

    @Test
    @DisplayName("站内通知查询与已读（FR-A07）")
    void notification_read_flow() throws Exception {
        String token = registerAndGetToken();
        MvcResult me = mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        long userId = objectMapper.readTree(me.getResponse().getContentAsString())
                .path("data").path("id").asLong();

        // 生成两条通知（直接调用服务）
        notificationService.record(userId, "ORDER_STATUS", "订单待接单", "陪玩师已接单", "ORDER", 1L);
        notificationService.record(userId, "AUDIT_RESULT", "入驻审核通过", "恭喜您成为陪玩师", "COMPANION", 2L);

        // 未读数 = 2
        mockMvc.perform(get("/api/notifications/unread-count")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.count").value(2));

        // 列表
        MvcResult list = mockMvc.perform(get("/api/notifications")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(2))
                .andReturn();
        long firstId = objectMapper.readTree(list.getResponse().getContentAsString())
                .path("data").path("records").get(0).path("id").asLong();

        // 标记单条已读
        mockMvc.perform(put("/api/notifications/" + firstId + "/read")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"));
        mockMvc.perform(get("/api/notifications/unread-count")
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.data.count").value(1));

        // 全部已读
        mockMvc.perform(put("/api/notifications/read-all")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/notifications/unread-count")
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.data.count").value(0));
    }

    // ==================== 收藏 ====================

    @Test
    @DisplayName("收藏闭环（FR-U06）")
    void favorite_flow() throws Exception {
        // 准备审核通过的陪玩师
        long companionUserId = 9_000_000_001L + SEQ.incrementAndGet();
        CompanionProfile profile = new CompanionProfile();
        profile.setUserId(companionUserId);
        profile.setDisplayName("测试陪玩师");
        profile.setProfileIntro("测试简介");
        profile.setCertificationStatus("APPROVED");
        profile.setServiceStatus("AVAILABLE");
        companionProfileMapper.insert(profile);

        String token = registerAndGetToken();

        // 收藏
        mockMvc.perform(post("/api/favorites/" + companionUserId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"));

        // 重复收藏 409
        mockMvc.perform(post("/api/favorites/" + companionUserId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.code").value("FAVORITE_ALREADY_EXISTS"));

        // 列表含该陪玩师
        mockMvc.perform(get("/api/favorites")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].displayName").value("测试陪玩师"));

        // 取消收藏
        mockMvc.perform(delete("/api/favorites/" + companionUserId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"));
        mockMvc.perform(get("/api/favorites")
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.data.total").value(0));
    }

    // ==================== 数据概览与操作日志 ====================

    @Test
    @DisplayName("数据概览返回全部指标（FR-M02）")
    void stats_overview() throws Exception {
        mockMvc.perform(get("/api/admin/stats/overview")
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.userCount").isNumber())
                .andExpect(jsonPath("$.data.companionCount").isNumber())
                .andExpect(jsonPath("$.data.serviceCount").isNumber())
                .andExpect(jsonPath("$.data.orderCount").isNumber())
                .andExpect(jsonPath("$.data.totalAmountCents").isNumber())
                .andExpect(jsonPath("$.data.todayOrderCount").isNumber())
                .andExpect(jsonPath("$.data.pendingApplications").isNumber())
                .andExpect(jsonPath("$.data.pendingComplaints").isNumber())
                .andExpect(jsonPath("$.data.waitingHumanConversations").isNumber());
    }

    @Test
    @DisplayName("操作日志记录与查询（FR-M20）")
    void operation_log_flow() throws Exception {
        // 记录日志（直接调用服务）
        operationLogService.record(1L, "ADMIN", "DISABLE_USER", "USER", 100L,
                "{\"status\":\"ENABLED\"}", "{\"status\":\"DISABLED\"}", "违规封禁");

        // 查询
        mockMvc.perform(get("/api/admin/operation-logs")
                        .header("Authorization", "Bearer " + adminToken())
                        .param("operationType", "DISABLE_USER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].reason").value("违规封禁"));
    }
}