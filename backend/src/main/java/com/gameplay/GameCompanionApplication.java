package com.gameplay;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 游戏陪玩系统后端启动类。
 *
 * <p>单体应用：按业务模块拆分 Controller / Application Service / Domain Service / Mapper，
 * 模块目录见 com.gameplay 下各子包（auth、user、companion、catalog、order、review、
 * customer_service、ai、admin、file、infrastructure）。</p>
 */
@SpringBootApplication
@EnableScheduling
@MapperScan("com.gameplay.**.mapper")
public class GameCompanionApplication {

    public static void main(String[] args) {
        SpringApplication.run(GameCompanionApplication.class, args);
    }
}
