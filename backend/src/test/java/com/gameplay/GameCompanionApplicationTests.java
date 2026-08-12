package com.gameplay;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 启动冒烟测试：验证 Spring 上下文可正常加载。
 * 依赖本机 MySQL（game_companion 库）可用。
 */
@SpringBootTest
class GameCompanionApplicationTests {

    @Test
    void contextLoads() {
    }
}
