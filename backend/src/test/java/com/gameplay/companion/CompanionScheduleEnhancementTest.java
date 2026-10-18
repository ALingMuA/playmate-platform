package com.gameplay.companion;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.gameplay.companion.task.AvailabilityRenewTask;
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

import java.time.LocalDate;
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

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class CompanionScheduleEnhancementTest {

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
    private AvailabilityRenewTask renewTask;

    @Autowired
    private com.gameplay.companion.mapper.CompanionAvailabilityTemplateMapper templateMapper;

    private String uniqueUser() {
        return "sch" + System.currentTimeMillis() + "_" + SEQ.incrementAndGet();
    }

    private String nextMobile() {
        return "1" + (6_000_000_000L + SEQ.incrementAndGet());
    }

    private JsonNode registerAndLogin(String username) throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.createObjectNode()
                                .put("username", username)
                                .put("password", PASSWORD)
                                .put("nickname", "作息测试")
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

    private long firstGameId() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/games"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode data = objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
        assertThat(data.size()).isPositive();
        return data.get(0).path("id").asLong();
    }

    /** 注册并审核通过陪玩师 */
    private String createApprovedCompanion() throws Exception {
        String username = uniqueUser();
        JsonNode authData = registerAndLogin(username);
        String token = authData.path("token").asText();

        // 提交入驻申请
        ObjectNode applyReq = objectMapper.createObjectNode();
        applyReq.put("realName", "测试陪玩");
        applyReq.put("contactMobile", nextMobile());
        applyReq.put("introduction", "专注上分");
        var caps = applyReq.putArray("capabilities");
        caps.addObject()
                .put("gameId", firstGameId())
                .put("server", "一区")
                .put("rank", "最强王者");

        MvcResult applyRes = mockMvc.perform(post("/api/companion/applications")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(applyReq.toString()))
                .andExpect(status().isOk())
                .andReturn();
        long applicationId = objectMapper.readTree(applyRes.getResponse().getContentAsString())
                .path("data").path("id").asLong();

        // 管理员审核通过
        String adminTok = adminToken();
        mockMvc.perform(post("/api/admin/companion-applications/" + applicationId + "/audit")
                        .header("Authorization", "Bearer " + adminTok)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.createObjectNode()
                                .put("approved", true)
                                .put("reason", "通过")
                                .toString()))
                .andExpect(status().isOk());

        // 重新登录以获取 COMPANION 角色 token
        MvcResult reLoginRes = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.createObjectNode()
                                .put("account", username)
                                .put("password", PASSWORD)
                                .toString()))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(reLoginRes.getResponse().getContentAsString())
                .path("data").path("token").asText();
    }

    @Test
    @DisplayName("普通用户访问陪玩档期接口应被拒绝（RBAC 403）")
    void customer_role_forbidden_on_companion_schedule() throws Exception {
        String username = uniqueUser();
        JsonNode auth = registerAndLogin(username);
        String customerToken = auth.path("token").asText();

        // 访问档期列表
        mockMvc.perform(get("/api/companion/availabilities")
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isForbidden());

        // 访问模板列表
        mockMvc.perform(get("/api/companion/availability-templates")
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("新增过去时间的档期应返回 422 AVAILABILITY_PAST_TIME")
    void create_past_availability_fails_422() throws Exception {
        String token = createApprovedCompanion();
        LocalDateTime pastStart = LocalDateTime.now().minusDays(1).withHour(10).withMinute(0);
        LocalDateTime pastEnd = LocalDateTime.now().minusDays(1).withHour(12).withMinute(0);

        mockMvc.perform(post("/api/companion/availabilities")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.createObjectNode()
                                .put("startAt", FMT.format(pastStart))
                                .put("endAt", FMT.format(pastEnd))
                                .toString()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("AVAILABILITY_PAST_TIME"));

        // 今天较早时段但跨越到未来的情况，startAt 早于当前时间也应被拦截
        LocalDateTime todayPastStart = LocalDateTime.now().minusHours(2);
        LocalDateTime todayFutureEnd = LocalDateTime.now().plusHours(2);
        mockMvc.perform(post("/api/companion/availabilities")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.createObjectNode()
                                .put("startAt", FMT.format(todayPastStart))
                                .put("endAt", FMT.format(todayFutureEnd))
                                .toString()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("AVAILABILITY_PAST_TIME"));
    }

    @Test
    @DisplayName("切换过去日期的全天休息应返回 422，参数为空返回 400")
    void toggle_day_off_validation() throws Exception {
        String token = createApprovedCompanion();

        // 过去日期
        mockMvc.perform(post("/api/companion/availabilities/toggle-day-off")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.createObjectNode()
                                .put("date", LocalDate.now().minusDays(1).toString())
                                .put("isOff", true)
                                .toString()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("AVAILABILITY_PAST_TIME"));

        // isOff 缺失 -> 400
        mockMvc.perform(post("/api/companion/availabilities/toggle-day-off")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.createObjectNode()
                                .put("date", LocalDate.now().toString())
                                .toString()))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("编辑历史模板不报 422，保持原生效日期，停用状态更新不自动启用，清空截止日期生效")
    void edit_historical_template_semantics() throws Exception {
        String token = createApprovedCompanion();

        // 1. 创建常规模板（今天生效）
        LocalDate today = LocalDate.now();
        ObjectNode createReq = objectMapper.createObjectNode();
        createReq.put("templateName", "常规作息");
        createReq.put("dayOfWeek", "1,2,3,4,5");
        createReq.put("allDay", false);
        createReq.put("startTime", "18:00:00");
        createReq.put("endTime", "23:00:00");
        createReq.put("effectiveFrom", today.toString());
        createReq.put("effectiveUntil", today.plusDays(30).toString());

        MvcResult createRes = mockMvc.perform(post("/api/companion/availability-templates")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createReq.toString()))
                .andExpect(status().isOk())
                .andReturn();
        long templateId = objectMapper.readTree(createRes.getResponse().getContentAsString())
                .path("data").path("id").asLong();

        // 2. 人工将 effective_from 篡改为 7 天前（模拟历史模板）
        jdbcTemplate.update("UPDATE companion_availability_template SET effective_from = ? WHERE id = ?",
                today.minusDays(7), templateId);
        // 清理缓存：重新更新以刷新 MyBatis L1 缓存
        com.gameplay.companion.domain.CompanionAvailabilityTemplate t = templateMapper.selectById(templateId);
        t.setEffectiveFrom(today.minusDays(7));
        templateMapper.updateById(t);

        // 3. 停用该模板
        mockMvc.perform(put("/api/companion/availability-templates/" + templateId + "/status")
                        .header("Authorization", "Bearer " + token)
                        .param("enabled", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.enabled").value(false));

        // 4. 编辑该模板（传入原历史生效日期，effectiveUntil 为 null 清空截止日）
        ObjectNode updateReq = objectMapper.createObjectNode();
        updateReq.put("templateName", "调整后的常规作息");
        updateReq.put("dayOfWeek", "1,2,3,4,5,6");
        updateReq.put("allDay", false);
        updateReq.put("startTime", "19:00:00");
        updateReq.put("endTime", "22:00:00");
        updateReq.put("effectiveFrom", today.minusDays(7).toString());
        updateReq.putNull("effectiveUntil");

        mockMvc.perform(put("/api/companion/availability-templates/" + templateId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateReq.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.templateName").value("调整后的常规作息"))
                .andExpect(jsonPath("$.data.enabled").value(false)) // 停用状态保持！
                .andExpect(jsonPath("$.data.effectiveFrom").value(today.minusDays(7).toString()));

        // 检查数据库中 effective_until 是否已清空为 NULL
        LocalDate dbUntil = jdbcTemplate.queryForObject(
                "SELECT effective_until FROM companion_availability_template WHERE id = ?",
                LocalDate.class, templateId);
        assertThat(dbUntil).isNull();
    }

    @Test
    @DisplayName("单日恢复常规作息（restore-day）清理例外并由启用模板重新生成")
    void restore_day_success_and_conflict() throws Exception {
        String token = createApprovedCompanion();
        LocalDate targetDate = LocalDate.now().plusDays(2);
        int dayOfWeekVal = targetDate.getDayOfWeek().getValue();

        // 1. 创建包含该天的模板
        ObjectNode createReq = objectMapper.createObjectNode();
        createReq.put("templateName", "常规接单");
        createReq.put("dayOfWeek", String.valueOf(dayOfWeekVal));
        createReq.put("allDay", false);
        createReq.put("startTime", "14:00:00");
        createReq.put("endTime", "18:00:00");
        createReq.put("effectiveFrom", LocalDate.now().toString());

        mockMvc.perform(post("/api/companion/availability-templates")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createReq.toString()))
                .andExpect(status().isOk());

        // 2. 陪玩师当天请假/标记休息（toggle-day-off isOff=true）
        mockMvc.perform(post("/api/companion/availabilities/toggle-day-off")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.createObjectNode()
                                .put("date", targetDate.toString())
                                .put("isOff", true)
                                .put("remark", "临时有事")
                                .toString()))
                .andExpect(status().isOk());

        // 3. 执行单日恢复常规（POST /restore-day）
        mockMvc.perform(post("/api/companion/availabilities/restore-day")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.createObjectNode()
                                .put("date", targetDate.toString())
                                .toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.removedManual").value(1))
                .andExpect(jsonPath("$.data.regenerated").value(1));

        // 4. overview 校验该天实例已就绪，且带 serverNow 与 generatedUntil
        MvcResult overviewRes = mockMvc.perform(get("/api/companion/availabilities/overview")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.serverNow").isNotEmpty())
                .andExpect(jsonPath("$.data.generatedUntil").isNotEmpty())
                .andReturn();

        // 5. 校验订单冲突拦截：如果在某日有手动记录且被订单锁定，restore-day 应返回 409
        LocalDate conflictDate = LocalDate.now().plusDays(4);
        Long companionUserId = jdbcTemplate.queryForObject(
                "SELECT companion_user_id FROM companion_availability_template WHERE template_name = '常规接单' LIMIT 1",
                Long.class);

        // 创建手动加档
        LocalDateTime slotStart = conflictDate.atTime(10, 0);
        LocalDateTime slotEnd = conflictDate.atTime(12, 0);
        mockMvc.perform(post("/api/companion/availabilities")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.createObjectNode()
                                .put("startAt", FMT.format(slotStart))
                                .put("endAt", FMT.format(slotEnd))
                                .put("remark", "手动测试加档")
                                .toString()))
                .andExpect(status().isOk());

        // 插入订单占用记录模拟该手动档期有有效订单锁定
        jdbcTemplate.update(
                "INSERT INTO order_time_slot (order_id, companion_user_id, start_at, end_at, slot_status) VALUES (?, ?, ?, ?, ?)",
                99999L, companionUserId, slotStart, slotEnd, "EFFECTIVE");

        // 尝试 restore-day 应触发 409 AVAILABILITY_RESTORE_CONFLICT
        mockMvc.perform(post("/api/companion/availabilities/restore-day")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.createObjectNode()
                                .put("date", conflictDate.toString())
                                .toString()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("AVAILABILITY_RESTORE_CONFLICT"));
    }

    @Test
    @DisplayName("每日定时滚动续期任务（AvailabilityRenewTask）执行无异常")
    void renew_task_runs_cleanly() {
        renewTask.renewAll();
    }
}
