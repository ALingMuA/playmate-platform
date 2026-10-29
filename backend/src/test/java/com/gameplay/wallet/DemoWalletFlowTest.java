package com.gameplay.wallet;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gameplay.auth.entity.User;
import com.gameplay.auth.mapper.UserMapper;
import com.gameplay.wallet.config.DemoWalletProperties;
import com.gameplay.wallet.domain.WalletAccount;
import com.gameplay.wallet.domain.WalletLedger;
import com.gameplay.wallet.mapper.WalletAccountMapper;
import com.gameplay.wallet.mapper.WalletLedgerMapper;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class DemoWalletFlowTest {

    private static final String PASSWORD = "Passw0rd123";
    private static final String ADMIN_ACCOUNT = "admin";
    private static final String ADMIN_PASSWORD = "Admin@123456";
    private static final AtomicInteger SEQ = new AtomicInteger();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private DemoWalletProperties demoWalletProperties;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private WalletAccountMapper walletAccountMapper;

    @Autowired
    private WalletLedgerMapper walletLedgerMapper;

    private String adminToken() throws Exception {
        return loginAndGetToken(ADMIN_ACCOUNT, ADMIN_PASSWORD);
    }

    private User registerUser() throws Exception {
        String username = "demou" + System.currentTimeMillis() + "_" + SEQ.incrementAndGet();
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.createObjectNode()
                                .put("username", username)
                                .put("password", PASSWORD)
                                .put("nickname", "测试_" + username)
                                .put("mobile", "1" + (8_100_000_000L + SEQ.incrementAndGet()))
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
    @DisplayName("W-01: 管理员在开关开启时发放演示余额成功")
    void testAdminGrantDemoWalletSuccess() throws Exception {
        demoWalletProperties.setEnabled(true);
        User user = registerUser();
        String admToken = adminToken();
        String reqId = UUID.randomUUID().toString();

        mockMvc.perform(post("/api/admin/demo-wallet/grants")
                        .header("Authorization", "Bearer " + admToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.createObjectNode()
                                .put("userId", user.getId())
                                .put("amountCents", 10000L)
                                .put("reason", "答辩演示测试准备余额")
                                .put("requestId", reqId)
                                .toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.userId").value(user.getId()))
                .andExpect(jsonPath("$.data.amountCents").value(10000L))
                .andExpect(jsonPath("$.data.businessNo").value("DEMO_CREDIT:" + reqId));

        WalletAccount wallet = walletAccountMapper.selectOne(
                new LambdaQueryWrapper<WalletAccount>().eq(WalletAccount::getUserId, user.getId()));
        assertThat(wallet).isNotNull();
        assertThat(wallet.getBalanceCents()).isEqualTo(10000L);

        WalletLedger ledger = walletLedgerMapper.selectByBusinessNo("DEMO_CREDIT:" + reqId);
        assertThat(ledger).isNotNull();
        assertThat(ledger.getLedgerType()).isEqualTo("DEMO_CREDIT");
        assertThat(ledger.getAmountCents()).isEqualTo(10000L);
    }

    @Test
    @DisplayName("W-02: 普通用户调用管理员演示余额接口返回 403 权限拒绝")
    void testUserAccessDenied() throws Exception {
        demoWalletProperties.setEnabled(true);
        User user = registerUser();
        String userToken = loginAndGetToken(user.getUsername(), PASSWORD);

        mockMvc.perform(post("/api/admin/demo-wallet/grants")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.createObjectNode()
                                .put("userId", user.getId())
                                .put("amountCents", 10000L)
                                .put("reason", "普通用户尝试发放")
                                .put("requestId", UUID.randomUUID().toString())
                                .toString()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("W-03: 功能开关关闭时管理员调用返回 DEMO_WALLET_DISABLED")
    void testGrantWhenDisabled() throws Exception {
        demoWalletProperties.setEnabled(false);
        User user = registerUser();
        String admToken = adminToken();

        mockMvc.perform(post("/api/admin/demo-wallet/grants")
                        .header("Authorization", "Bearer " + admToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.createObjectNode()
                                .put("userId", user.getId())
                                .put("amountCents", 10000L)
                                .put("reason", "开关关闭时发放")
                                .put("requestId", UUID.randomUUID().toString())
                                .toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("DEMO_WALLET_DISABLED"));
    }

    @Test
    @DisplayName("W-04: 重复提交同一 requestId 幂等返回第一次结果，不重复累加余额")
    void testIdempotentGrant() throws Exception {
        demoWalletProperties.setEnabled(true);
        User user = registerUser();
        String admToken = adminToken();
        String reqId = UUID.randomUUID().toString();

        String payload = objectMapper.createObjectNode()
                .put("userId", user.getId())
                .put("amountCents", 5000L)
                .put("reason", "第一次发放")
                .put("requestId", reqId)
                .toString();

        mockMvc.perform(post("/api/admin/demo-wallet/grants")
                        .header("Authorization", "Bearer " + admToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.balanceAfterCents").value(5000L));

        // 第二次重复提交
        mockMvc.perform(post("/api/admin/demo-wallet/grants")
                        .header("Authorization", "Bearer " + admToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.balanceAfterCents").value(5000L));

        WalletAccount wallet = walletAccountMapper.selectOne(
                new LambdaQueryWrapper<WalletAccount>().eq(WalletAccount::getUserId, user.getId()));
        assertThat(wallet.getBalanceCents()).isEqualTo(5000L);
    }

    @Test
    @DisplayName("W-05: 面额不在允许列表返回 DEMO_WALLET_AMOUNT_INVALID")
    void testInvalidAmount() throws Exception {
        demoWalletProperties.setEnabled(true);
        User user = registerUser();
        String admToken = adminToken();

        mockMvc.perform(post("/api/admin/demo-wallet/grants")
                        .header("Authorization", "Bearer " + admToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.createObjectNode()
                                .put("userId", user.getId())
                                .put("amountCents", 99999L)
                                .put("reason", "非法面额")
                                .put("requestId", UUID.randomUUID().toString())
                                .toString()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("DEMO_WALLET_AMOUNT_INVALID"));
    }
}
