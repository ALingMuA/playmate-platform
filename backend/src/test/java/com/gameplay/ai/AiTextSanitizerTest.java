package com.gameplay.ai;

import com.gameplay.ai.service.AiTextSanitizer;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AiTextSanitizerTest {

    private final AiTextSanitizer sanitizer = new AiTextSanitizer();

    @Test
    void redactsCommonCredentialAndContactFormatsBeforeTruncation() {
        String input = "密码是 synthetic-secret，验证码：543210；Bearer fake-bearer-credential "
                + "test@example.com 13800138000 6222021234567890123";
        String result = sanitizer.sanitize(input, 1000);
        assertThat(result).doesNotContain("synthetic-secret", "543210", "fake-bearer-credential",
                "test@example.com", "13800138000", "6222021234567890123");
        assertThat(result).contains("[REDACTED]", "[EMAIL]", "[PHONE]", "[ACCOUNT]");
        assertThat(sanitizer.sanitize("密码是synthetic-secret", 10)).doesNotContain("synthe");
    }

    @Test
    void redactsJsonQuotedQueryAndZeroWidthObfuscatedSecrets() {
        String input = "{\"api_key\":\"sk-abc123456789\"} "
                + "https://example.com/ws?ticket=secret-ticket-123&api_key=leak-key "
                + "138\u200B00138000 Basic dXNlcjpwYXNz";
        String result = sanitizer.sanitize(input, 1000);
        assertThat(result).doesNotContain("sk-abc123456789", "secret-ticket-123", "leak-key",
                "13800138000", "dXNlcjpwYXNz");
        assertThat(result).contains("[PHONE]", "[REDACTED]");
    }

    @Test
    void preservesNecessaryOrderSummaryAndHandlesMissingText() {
        assertThat(sanitizer.sanitize("订单#123 待支付 20元", 100)).isEqualTo("订单#123 待支付 20元");
        assertThat(sanitizer.sanitize(null, 100)).isEmpty();
    }
}
