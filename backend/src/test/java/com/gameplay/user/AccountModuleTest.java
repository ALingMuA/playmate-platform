package com.gameplay.user;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * user 模块集成测试：真实 Spring 上下文 + 真实 MySQL（@Transactional 回滚，不污染数据）。
 *
 * <p>覆盖 FR-A05 个人资料查询与维护（头像、昵称、性别、简介）、
 * FR-A06 最近登录时间查看与主动注销全部会话（令牌版本失效）。</p>
 *
 * <p>依赖：本机 MySQL 已执行 sql/schema.sql 与 sql/data.sql，
 * 且已配置 application-local.yml（数据库密码与 JWT 密钥）。</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AccountModuleTest {

    private static final String PASSWORD = "Passw0rd123";
    private static final AtomicInteger SEQ = new AtomicInteger();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String uniqueUser() {
        return "it" + System.currentTimeMillis() + "_" + SEQ.incrementAndGet();
    }

    private String nextMobile() {
        return "1" + (3_000_000_000L + SEQ.incrementAndGet());
    }

    /** 注册并登录，返回 JWT */
    private String registerAndLogin(String username, String password, String nickname,
                                    String mobile, String email) throws Exception {
        var node = objectMapper.createObjectNode()
                .put("username", username)
                .put("password", password)
                .put("nickname", nickname);
        if (mobile != null) {
            node.put("mobile", mobile);
        }
        if (email != null) {
            node.put("email", email);
        }
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(node.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"));

        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.createObjectNode()
                                .put("account", username)
                                .put("password", password)
                                .toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data").path("token").asText();
    }

    private MvcResult putProfile(String token, JsonNode body) throws Exception {
        return mockMvc.perform(put("/api/accounts/profile")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body.toString()))
                .andReturn();
    }

    // ==================== FR-A05 个人资料查询 ====================

    @Test
    @DisplayName("查询个人资料：登录后返回资料与最近登录时间（FR-A05、FR-A06）")
    void get_profile_success() throws Exception {
        String username = uniqueUser();
        String token = registerAndLogin(username, PASSWORD, "资料测试", nextMobile(), null);

        mockMvc.perform(get("/api/accounts/profile")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.username").value(username))
                .andExpect(jsonPath("$.data.nickname").value("资料测试"))
                .andExpect(jsonPath("$.data.roles[0]").value("USER"))
                .andExpect(jsonPath("$.data.lastLoginAt").isNotEmpty())
                .andExpect(jsonPath("$.data.passwordHash").doesNotExist());
    }

    @Test
    @DisplayName("查询个人资料：无令牌返回 401 AUTH_TOKEN_MISSING")
    void get_profile_without_token() throws Exception {
        mockMvc.perform(get("/api/accounts/profile"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_TOKEN_MISSING"));
    }

    // ==================== FR-A05 个人资料维护 ====================

    @Test
    @DisplayName("更新个人资料：头像、昵称、性别、简介全部更新并落库")
    void update_profile_full() throws Exception {
        String username = uniqueUser();
        String token = registerAndLogin(username, PASSWORD, "旧昵称", nextMobile(), null);

        MvcResult result = putProfile(token, objectMapper.createObjectNode()
                .put("nickname", "新昵称")
                .put("avatarUrl", "/uploads/avatar/1.png")
                .put("gender", 1)
                .put("introduction", "大家好，我是陪玩爱好者"));

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        JsonNode data = objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
        assertThat(data.path("nickname").asText()).isEqualTo("新昵称");
        assertThat(data.path("avatarUrl").asText()).isEqualTo("/uploads/avatar/1.png");
        assertThat(data.path("gender").asInt()).isEqualTo(1);
        assertThat(data.path("introduction").asText()).isEqualTo("大家好，我是陪玩爱好者");

        // 重新查询确认落库
        mockMvc.perform(get("/api/accounts/profile")
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.data.nickname").value("新昵称"))
                .andExpect(jsonPath("$.data.gender").value(1))
                .andExpect(jsonPath("$.data.avatarUrl").value("/uploads/avatar/1.png"))
                .andExpect(jsonPath("$.data.introduction").value("大家好，我是陪玩爱好者"));
    }

    @Test
    @DisplayName("更新个人资料：部分字段更新，其余字段保持不变")
    void update_profile_partial() throws Exception {
        String username = uniqueUser();
        String token = registerAndLogin(username, PASSWORD, "保留昵称", nextMobile(), null);
        putProfile(token, objectMapper.createObjectNode()
                .put("gender", 2)
                .put("introduction", "只改性别和简介"));

        mockMvc.perform(get("/api/accounts/profile")
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.data.nickname").value("保留昵称"))
                .andExpect(jsonPath("$.data.gender").value(2))
                .andExpect(jsonPath("$.data.introduction").value("只改性别和简介"));
    }

    @Test
    @DisplayName("更新个人资料：传空串清空简介与头像")
    void update_profile_clear_fields() throws Exception {
        String username = uniqueUser();
        String token = registerAndLogin(username, PASSWORD, "测试", nextMobile(), null);
        putProfile(token, objectMapper.createObjectNode()
                .put("introduction", "先填写简介")
                .put("avatarUrl", "/uploads/avatar/1.png"));
        putProfile(token, objectMapper.createObjectNode()
                .put("introduction", "")
                .put("avatarUrl", ""));

        mockMvc.perform(get("/api/accounts/profile")
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.data.introduction").value(""))
                .andExpect(jsonPath("$.data.avatarUrl").value(""));
    }

    @Test
    @DisplayName("更新个人资料：非法性别返回 400 VALIDATION_FAILED")
    void update_profile_invalid_gender() throws Exception {
        String username = uniqueUser();
        String token = registerAndLogin(username, PASSWORD, "测试", nextMobile(), null);

        mockMvc.perform(put("/api/accounts/profile")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.createObjectNode().put("gender", 3).toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("更新个人资料：昵称为空白返回 400 VALIDATION_FAILED")
    void update_profile_blank_nickname() throws Exception {
        String username = uniqueUser();
        String token = registerAndLogin(username, PASSWORD, "测试", nextMobile(), null);

        mockMvc.perform(put("/api/accounts/profile")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.createObjectNode().put("nickname", "   ").toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("更新个人资料：全字段为空时幂等返回当前资料")
    void update_profile_noop() throws Exception {
        String username = uniqueUser();
        String token = registerAndLogin(username, PASSWORD, "幂等测试", nextMobile(), null);

        MvcResult result = putProfile(token, objectMapper.createObjectNode());
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data").path("nickname").asText()).isEqualTo("幂等测试");
    }

    // ==================== FR-A06 主动注销会话 ====================

    @Test
    @DisplayName("注销全部会话：所有旧令牌立即失效（401 VERSION_MISMATCH），重新登录可用")
    void logout_all_invalidates_all_tokens() throws Exception {
        String username = uniqueUser();
        String token1 = registerAndLogin(username, PASSWORD, "测试", nextMobile(), null);

        // 同一账号再次登录，获得第二个有效令牌
        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.createObjectNode()
                                .put("account", username)
                                .put("password", PASSWORD)
                                .toString()))
                .andExpect(status().isOk())
                .andReturn();
        String token2 = objectMapper.readTree(loginResult.getResponse().getContentAsString())
                .path("data").path("token").asText();
        assertThat(token2).isNotBlank();

        // 用 token1 注销全部会话
        mockMvc.perform(post("/api/accounts/logout-all")
                        .header("Authorization", "Bearer " + token1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"));

        // 全部已签发令牌立即失效
        for (String stale : new String[]{token1, token2}) {
            mockMvc.perform(get("/api/accounts/profile")
                            .header("Authorization", "Bearer " + stale))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("AUTH_TOKEN_VERSION_MISMATCH"));
        }

        // 重新登录签发新令牌可用
        MvcResult relogin = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.createObjectNode()
                                .put("account", username)
                                .put("password", PASSWORD)
                                .toString()))
                .andExpect(status().isOk())
                .andReturn();
        String newToken = objectMapper.readTree(relogin.getResponse().getContentAsString())
                .path("data").path("token").asText();
        assertThat(newToken).isNotBlank();

        mockMvc.perform(get("/api/accounts/profile")
                        .header("Authorization", "Bearer " + newToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value(username));
    }

    @Test
    @DisplayName("注销全部会话：无令牌返回 401 AUTH_TOKEN_MISSING")
    void logout_all_without_token() throws Exception {
        mockMvc.perform(post("/api/accounts/logout-all"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_TOKEN_MISSING"));
    }
}
