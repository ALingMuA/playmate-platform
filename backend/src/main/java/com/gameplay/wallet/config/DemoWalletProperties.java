package com.gameplay.wallet.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

/**
 * 演示钱包配置属性。
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.demo-wallet")
public class DemoWalletProperties {

    /** 是否启用演示余额发放功能 */
    private boolean enabled = false;

    /** 允许发放的演示金额档位（分） */
    private List<Long> allowedAmountsCents = Arrays.asList(1000L, 5000L, 10000L, 50000L);
}
