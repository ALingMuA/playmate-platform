package com.gameplay.notification;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gameplay.auth.entity.User;
import com.gameplay.auth.mapper.UserMapper;
import com.gameplay.notification.domain.Notification;
import com.gameplay.notification.mapper.NotificationMapper;
import com.gameplay.notification.service.NotificationService;
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

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class NotificationLifecycleFlowTest {

    private static final String PASSWORD = "Passw0rd123";
    private static final AtomicInteger SEQ = new AtomicInteger();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private NotificationMapper notificationMapper;

    @Autowired
    private UserMapper userMapper;

    private User registerUser() throws Exception {
        String username = "notifu" + System.currentTimeMillis() + "_" + SEQ.incrementAndGet();
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.createObjectNode()
                                .put("username", username)
                                .put("password", PASSWORD)
                                .put("nickname", "测试_" + username)
                                .put("mobile", "1" + (8_200_000_000L + SEQ.incrementAndGet()))
                                .toString()))
                .andExpect(status().isOk());
        return userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getUsername, username));
    }

    private String loginAndGetToken(String account, String password) throws Exception {
        MvcResult res = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.createObjectNode()
                                .put("account", account)
                                .put("password", password)
                                .toString()))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode root = objectMapper.readTree(res.getResponse().getContentAsString());
        return root.path("data").path("token").asText();
    }

    @Test
    @DisplayName("N-01 ~ N-03: 通知列表、未读数、单条标记已读与全部标记已读")
    void testNotificationListAndReadFlow() throws Exception {
        User user = registerUser();
        String token = loginAndGetToken(user.getUsername(), PASSWORD);

        // 插入两条测试通知
        notificationService.record(user.getId(), "SYSTEM", "系统提醒1", "欢迎使用陪玩平台1", "SYSTEM", 0L);
        notificationService.record(user.getId(), "SYSTEM", "系统提醒2", "欢迎使用陪玩平台2", "SYSTEM", 0L);

        // 1. 查询未读数：应为 2
        mockMvc.perform(get("/api/notifications/unread-count")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.count").value(2));

        // 2. 查询分页通知
        MvcResult listRes = mockMvc.perform(get("/api/notifications")
                        .header("Authorization", "Bearer " + token)
                        .param("page", "1")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(2))
                .andReturn();
        JsonNode records = objectMapper.readTree(listRes.getResponse().getContentAsString())
                .path("data").path("records");
        long firstId = records.get(0).path("id").asLong();

        // 3. 标记单条已读
        mockMvc.perform(put("/api/notifications/" + firstId + "/read")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        // 未读数减为 1
        mockMvc.perform(get("/api/notifications/unread-count")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.count").value(1));

        // 4. 全部标记已读
        mockMvc.perform(put("/api/notifications/read-all")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        // 未读数变为 0
        mockMvc.perform(get("/api/notifications/unread-count")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.count").value(0));
    }

    @Test
    @DisplayName("N-04: 用户无法标记他人的通知已读 -> 403 权限拒绝")
    void testCannotReadOtherUserNotification() throws Exception {
        User userA = registerUser();
        User userB = registerUser();
        String tokenB = loginAndGetToken(userB.getUsername(), PASSWORD);

        notificationService.record(userA.getId(), "SYSTEM", "用户A通知", "这是A的私有通知", "SYSTEM", 0L);
        Notification notifA = notificationMapper.selectOne(
                new LambdaQueryWrapper<Notification>().eq(Notification::getUserId, userA.getId()));

        mockMvc.perform(put("/api/notifications/" + notifA.getId() + "/read")
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("N-06: 未读/已读筛选服务端分页、总数准确且用户隔离")
    void testReadStatusFilterPaginationAndIsolation() throws Exception {
        User userA = registerUser();
        User userB = registerUser();
        String tokenA = loginAndGetToken(userA.getUsername(), PASSWORD);
        String tokenB = loginAndGetToken(userB.getUsername(), PASSWORD);
        notificationService.record(userA.getId(), "SYSTEM", "未读1", "内容", "SYSTEM", 0L);
        notificationService.record(userA.getId(), "SYSTEM", "未读2", "内容", "SYSTEM", 0L);
        notificationService.record(userA.getId(), "SYSTEM", "已读", "内容", "SYSTEM", 0L);
        Notification read = notificationMapper.selectOne(new LambdaQueryWrapper<Notification>()
                .eq(Notification::getUserId, userA.getId())
                .eq(Notification::getTitle, "已读"));
        notificationService.markRead(userA.getId(), read.getId());

        mockMvc.perform(get("/api/notifications").header("Authorization", "Bearer " + tokenA)
                        .param("page", "1").param("size", "10").param("readStatus", "0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(2))
                .andExpect(jsonPath("$.data.records[0].readStatus").value(0));
        mockMvc.perform(get("/api/notifications").header("Authorization", "Bearer " + tokenA)
                        .param("page", "1").param("size", "10").param("readStatus", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].readStatus").value(1));
        mockMvc.perform(get("/api/notifications").header("Authorization", "Bearer " + tokenB)
                        .param("page", "1").param("size", "10").param("readStatus", "0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(0));
        mockMvc.perform(get("/api/notifications").header("Authorization", "Bearer " + tokenA)
                        .param("readStatus", "2"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("N-05: 带 eventKey 的通知具备幂等性，同一事件重复生成时只保留一条")
    void testNotificationEventKeyIdempotency() throws Exception {
        User user = registerUser();
        String eventKey = "ORDER:99999:WAITING_ACCEPTANCE";

        notificationService.record(user.getId(), "ORDER_STATUS", "订单提醒", "第1次生成", "ORDER", 99999L, eventKey);
        notificationService.record(user.getId(), "ORDER_STATUS", "订单提醒", "第2次重复调用", "ORDER", 99999L, eventKey);

        long count = notificationMapper.selectCount(
                new LambdaQueryWrapper<Notification>()
                        .eq(Notification::getUserId, user.getId())
                        .eq(Notification::getEventKey, eventKey));
        assertThat(count).isEqualTo(1L);

        Notification recorded = notificationMapper.selectOne(
                new LambdaQueryWrapper<Notification>()
                        .eq(Notification::getUserId, user.getId())
                        .eq(Notification::getEventKey, eventKey));
        assertThat(recorded.getContent()).isEqualTo("第1次生成");
    }
}
