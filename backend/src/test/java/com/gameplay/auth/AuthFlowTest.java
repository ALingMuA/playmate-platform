package com.gameplay.auth;

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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * auth 模块集成测试：真实 Spring 上下文 + 真实 MySQL（@Transactional 回滚，不污染数据）。
 *
 * <p>覆盖 FR-A01 注册、FR-A02 登录、FR-A03 身份与令牌校验（JWT 过滤器）、
 * FR-A04 修改密码与令牌版本失效机制，以及预置管理员种子数据。</p>
 *
 * <p>依赖：本机 MySQL 已执行 sql/schema.sql 与 sql/data.sql（admin 种子账号），
 * 且已配置 application-local.yml（数据库密码与 JWT 密钥）。</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AuthFlowTest {

    private static final String PASSWORD = "Passw0rd123";
    private static final AtomicInteger SEQ = new AtomicInteger();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    /** 生成唯一用户名 */
    private String uniqueUser() {
        return "it" + System.currentTimeMillis() + "_" + SEQ.incrementAndGet();
    }

    /** 生成唯一 11 位手机号（1 开头，满足 ^1\d{10}$） */
    private String nextMobile() {
        return "1" + (3_000_000_000L + SEQ.incrementAndGet());
    }

    private MvcResult register(String username, String password, String nickname,
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
        return mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(node.toString()))
                .andReturn();
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
        return extractToken(result);
    }

    private String extractToken(MvcResult result) throws Exception {
        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        return root.path("data").path("token").asText();
    }

    private List<String> roleList(MvcResult result) throws Exception {
        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        List<String> roles = new ArrayList<>();
        root.path("data").path("user").path("roles").forEach(n -> roles.add(n.asText()));
        return roles;
    }

    // ==================== FR-A01 注册 ====================

    @Test
    @DisplayName("注册成功：返回用户信息并绑定 USER 角色")
    void register_success() throws Exception {
        MvcResult result = register(uniqueUser(), PASSWORD, "集成测试", nextMobile(), null);

        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(root.path("code").asText()).isEqualTo("SUCCESS");
        assertThat(root.path("data").path("id").asLong()).isPositive();
        assertThat(root.path("data").path("roles").get(0).asText()).isEqualTo("USER");
        assertThat(root.path("data").has("passwordHash")).isFalse();
    }

    @Test
    @DisplayName("注册：重复用户名返回 409 ACCOUNT_USERNAME_EXISTS")
    void register_duplicate_username() throws Exception {
        String username = uniqueUser();
        register(username, PASSWORD, "测试", nextMobile(), null);
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.createObjectNode()
                                .put("username", username)
                                .put("password", PASSWORD)
                                .put("nickname", "测试2")
                                .put("mobile", nextMobile())
                                .toString()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ACCOUNT_USERNAME_EXISTS"));
    }

    @Test
    @DisplayName("注册：重复手机号返回 409 ACCOUNT_MOBILE_EXISTS")
    void register_duplicate_mobile() throws Exception {
        String mobile = nextMobile();
        register(uniqueUser(), PASSWORD, "测试", mobile, null);
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.createObjectNode()
                                .put("username", uniqueUser())
                                .put("password", PASSWORD)
                                .put("nickname", "测试2")
                                .put("mobile", mobile)
                                .toString()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ACCOUNT_MOBILE_EXISTS"));
    }

    @Test
    @DisplayName("注册：密码过弱返回 400 VALIDATION_FAILED")
    void register_weak_password() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.createObjectNode()
                                .put("username", uniqueUser())
                                .put("password", "123")
                                .put("nickname", "测试")
                                .put("mobile", nextMobile())
                                .toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("注册：手机号与邮箱均未填写返回 400")
    void register_without_contact() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.createObjectNode()
                                .put("username", uniqueUser())
                                .put("password", PASSWORD)
                                .put("nickname", "测试")
                                .toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    // ==================== FR-A02 登录 ====================

    @Test
    @DisplayName("登录：用户名/手机号/邮箱三种账号均可登录并签发 JWT")
    void login_by_username_mobile_email() throws Exception {
        String username = uniqueUser();
        String mobile = nextMobile();
        String email = username + "@test.local";
        register(username, PASSWORD, "测试", mobile, email);

        assertThat(loginAndGetToken(username, PASSWORD)).isNotBlank();
        assertThat(loginAndGetToken(mobile, PASSWORD)).isNotBlank();
        assertThat(loginAndGetToken(email, PASSWORD)).isNotBlank();
    }

    @Test
    @DisplayName("登录：密码错误返回 401 AUTH_CREDENTIAL_INVALID（不区分账号是否存在）")
    void login_wrong_password() throws Exception {
        String username = uniqueUser();
        register(username, PASSWORD, "测试", nextMobile(), null);
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.createObjectNode()
                                .put("account", username)
                                .put("password", "WrongPass1")
                                .toString()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_CREDENTIAL_INVALID"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.createObjectNode()
                                .put("account", "no_such_user_xyz")
                                .put("password", PASSWORD)
                                .toString()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_CREDENTIAL_INVALID"));
    }

    // ==================== FR-A03 身份与令牌校验 ====================

    @Test
    @DisplayName("鉴权：无令牌访问 /me 返回 401 AUTH_TOKEN_MISSING")
    void me_without_token() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_TOKEN_MISSING"));
    }

    @Test
    @DisplayName("鉴权：伪造令牌返回 401 AUTH_TOKEN_INVALID")
    void me_with_fake_token() throws Exception {
        mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", "Bearer not.a.valid.jwt"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_TOKEN_INVALID"));
    }

    @Test
    @DisplayName("鉴权：有效令牌访问 /me 返回用户信息与角色")
    void me_with_valid_token() throws Exception {
        String username = uniqueUser();
        register(username, PASSWORD, "测试", nextMobile(), null);
        String token = loginAndGetToken(username, PASSWORD);

        mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.username").value(username))
                .andExpect(jsonPath("$.data.roles[0]").value("USER"))
                .andExpect(jsonPath("$.data.passwordHash").doesNotExist());
    }

    // ==================== FR-A04 修改密码与令牌版本 ====================

    @Test
    @DisplayName("修改密码：成功后旧令牌立即失效（401 VERSION_MISMATCH），新令牌可用")
    void change_password_invalidates_old_token() throws Exception {
        String username = uniqueUser();
        register(username, PASSWORD, "测试", nextMobile(), null);
        String oldToken = loginAndGetToken(username, PASSWORD);

        MvcResult changeResult = mockMvc.perform(post("/api/auth/change-password")
                        .header("Authorization", "Bearer " + oldToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.createObjectNode()
                                .put("oldPassword", PASSWORD)
                                .put("newPassword", "NewPass456")
                                .toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andReturn();
        String newToken = extractToken(changeResult);
        assertThat(newToken).isNotBlank().isNotEqualTo(oldToken);

        // 旧令牌失效（令牌版本机制）
        mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", "Bearer " + oldToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_TOKEN_VERSION_MISMATCH"));

        // 新令牌可用，且新密码可重新登录
        mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", "Bearer " + newToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value(username));
        assertThat(loginAndGetToken(username, "NewPass456")).isNotBlank();
    }

    @Test
    @DisplayName("修改密码：原密码错误返回 401 AUTH_PASSWORD_INCORRECT")
    void change_password_wrong_old_password() throws Exception {
        String username = uniqueUser();
        register(username, PASSWORD, "测试", nextMobile(), null);
        String token = loginAndGetToken(username, PASSWORD);

        mockMvc.perform(post("/api/auth/change-password")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.createObjectNode()
                                .put("oldPassword", "WrongOld1")
                                .put("newPassword", "NewPass456")
                                .toString()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_PASSWORD_INCORRECT"));
    }

    // ==================== 种子数据 ====================

    @Test
    @DisplayName("预置管理员：BCrypt 种子密码可登录且持有 ADMIN 角色")
    void admin_seed_login() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.createObjectNode()
                                .put("account", "admin")
                                .put("password", "Admin@123456")
                                .toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andReturn();

        assertThat(roleList(result)).contains("ADMIN");
    }
}
