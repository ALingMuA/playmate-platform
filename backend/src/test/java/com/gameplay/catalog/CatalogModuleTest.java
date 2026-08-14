package com.gameplay.catalog;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
 * catalog 模块集成测试：真实 Spring 上下文 + 真实 MySQL（@Transactional 回滚，不污染数据）。
 *
 * <p>覆盖 FR-U01 游戏/服务类型/标签公开浏览（游客可访问）、
 * FR-M10~M12 游戏/服务类型/标签管理（仅管理员，含唯一性校验与权限控制）。</p>
 *
 * <p>依赖：本机 MySQL 已执行 sql/schema.sql 与 sql/data.sql（admin 种子账号、目录演示数据），
 * 且已配置 application-local.yml。</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class CatalogModuleTest {

    private static final AtomicInteger SEQ = new AtomicInteger();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    /** 生成唯一后缀，避免与种子/历史数据冲突 */
    private String uniqueSuffix() {
        return System.currentTimeMillis() + "_" + SEQ.incrementAndGet();
    }

    /** 管理员登录（种子账号 admin / Admin@123456）并返回 JWT */
    private String adminToken() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody("admin", "Admin@123456")))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        return root.path("data").path("token").asText();
    }

    /** 注册普通用户并登录，用于验证非管理员无权访问管理接口 */
    private String userToken() throws Exception {
        String suffix = uniqueSuffix();
        String username = "catu" + suffix;
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.createObjectNode()
                                .put("username", username)
                                .put("password", "Passw0rd123")
                                .put("nickname", "目录测试用户")
                                .put("mobile", "1" + (3_000_000_000L + (SEQ.get() % 900_000_000L)))
                                .toString()))
                .andExpect(status().isOk());
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(username, "Passw0rd123")))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        return root.path("data").path("token").asText();
    }

    // ==================== FR-U01 公开浏览（游客） ====================

    @Test
    @DisplayName("公开浏览：游客可直接获取游戏列表（含种子游戏）")
    void public_games_accessible_without_token() throws Exception {
        mockMvc.perform(get("/api/games"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].gameName").isNotEmpty());
    }

    @Test
    @DisplayName("公开浏览：游客可获取服务类型与标签（按游戏+通用）")
    void public_service_types_and_tags_without_token() throws Exception {
        mockMvc.perform(get("/api/service-types"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data").isArray());

        // 指定游戏：应同时返回该游戏标签与通用标签（gameId=0）
        mockMvc.perform(get("/api/tags").param("gameId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].tagName").isNotEmpty());
    }

    @Test
    @DisplayName("公开浏览：不存在的游戏详情返回 404 GAME_NOT_FOUND")
    void public_game_detail_not_found() throws Exception {
        mockMvc.perform(get("/api/games/999999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("GAME_NOT_FOUND"));
    }

    // ==================== FR-M10 游戏管理 ====================

    @Test
    @DisplayName("游戏管理：管理员创建/更新/删除游戏全流程")
    void admin_game_crud() throws Exception {
        String token = adminToken();
        String name = "测试游戏" + uniqueSuffix();

        // 创建
        MvcResult created = mockMvc.perform(post("/api/admin/games")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(gameBody(name, "http://img.local/g.png", "集成测试游戏", 9, 1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.id").isNumber())
                .andReturn();
        long id = objectMapper.readTree(created.getResponse().getContentAsString())
                .path("data").path("id").asLong();

        // 更新（改名 + 停用）
        mockMvc.perform(put("/api/admin/games/{id}", id)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(gameBody(name + "改", "", "更新后的简介", 1, 0)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.gameName").value(name + "改"))
                .andExpect(jsonPath("$.data.enabled").value(0));

        // 停用后公开详情应 404（前台不可见）
        mockMvc.perform(get("/api/games/{id}", id))
                .andExpect(status().isNotFound());

        // 删除
        mockMvc.perform(delete("/api/admin/games/{id}", id)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"));
    }

    @Test
    @DisplayName("游戏管理：重复名称创建返回 409 GAME_NAME_EXISTS")
    void admin_game_duplicate_name() throws Exception {
        String token = adminToken();
        String name = "重名游戏" + uniqueSuffix();
        mockMvc.perform(post("/api/admin/games")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(gameBody(name, "", "", 1, 1)))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/admin/games")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(gameBody(name, "", "", 1, 1)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("GAME_NAME_EXISTS"));
    }

    @Test
    @DisplayName("游戏管理：普通用户访问管理接口返回 403 PERMISSION_DENIED")
    void non_admin_forbidden() throws Exception {
        String token = userToken();
        mockMvc.perform(post("/api/admin/games")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(gameBody("越权游戏", "", "", 1, 1)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));
    }

    // ==================== FR-M11 服务类型管理 ====================

    @Test
    @DisplayName("服务类型管理：创建成功且编码自动转为大写，重复编码返回 409")
    void admin_service_type_create_and_duplicate_code() throws Exception {
        String token = adminToken();
        String suffix = uniqueSuffix();
        String code = "test_type_" + suffix;

        mockMvc.perform(post("/api/admin/service-types")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.createObjectNode()
                                .put("typeName", "测试类型" + suffix)
                                .put("typeCode", code)
                                .put("description", "集成测试类型")
                                .put("sortNo", 1)
                                .put("enabled", 1)
                                .toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.typeCode").value(code.toUpperCase()));

        // 重复编码：409 SERVICE_TYPE_CODE_EXISTS
        mockMvc.perform(post("/api/admin/service-types")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.createObjectNode()
                                .put("typeName", "另一类型" + suffix)
                                .put("typeCode", code)
                                .put("enabled", 1)
                                .toString()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SERVICE_TYPE_CODE_EXISTS"));
    }

    // ==================== FR-M12 标签管理 ====================

    @Test
    @DisplayName("标签管理：通用标签与游戏专属标签创建、同分类重名 409、关联游戏不存在 404、非法分类 400")
    void admin_tag_crud_and_validation() throws Exception {
        String token = adminToken();
        String suffix = uniqueSuffix();
        String tagName = "测试标签" + suffix;

        // 通用标签（gameId=0）
        mockMvc.perform(post("/api/admin/tags")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(tagBody(tagName, "STYLE", 0, 1, 1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.gameId").value(0));

        // 同游戏同分类重名：409 TAG_EXISTS
        mockMvc.perform(post("/api/admin/tags")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(tagBody(tagName, "STYLE", 0, 1, 1)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TAG_EXISTS"));

        // 关联不存在的游戏：404 GAME_NOT_FOUND
        mockMvc.perform(post("/api/admin/tags")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(tagBody("孤儿标签" + suffix, "HERO", 999999999L, 1, 1)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("GAME_NOT_FOUND"));

        // 非法分类：400 VALIDATION_FAILED
        mockMvc.perform(post("/api/admin/tags")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(tagBody("坏分类" + suffix, "INVALID_CAT", 0, 1, 1)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    // ==================== 请求体构造（返回 JSON 字符串） ====================

    private String loginBody(String account, String password) {
        return objectMapper.createObjectNode()
                .put("account", account)
                .put("password", password)
                .toString();
    }

    private String gameBody(String name, String iconUrl, String intro, int sortNo, int enabled) {
        return objectMapper.createObjectNode()
                .put("gameName", name)
                .put("gameIconUrl", iconUrl)
                .put("gameIntro", intro)
                .put("sortNo", sortNo)
                .put("enabled", enabled)
                .toString();
    }

    private String tagBody(String name, String category, long gameId, int sortNo, int enabled) {
        return objectMapper.createObjectNode()
                .put("tagName", name)
                .put("tagCategory", category)
                .put("gameId", gameId)
                .put("sortNo", sortNo)
                .put("enabled", enabled)
                .toString();
    }
}
