package com.gameplay;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * 启动冒烟测试：验证 Spring 上下文可正常加载。
 * 依赖本机 MySQL（game_companion_app 库）与 application-local.yml（见 application-test.yml）。
 */
@SpringBootTest
@ActiveProfiles("test")
class GameCompanionApplicationTests {

    @Test
    void contextLoads() {
    }
}
