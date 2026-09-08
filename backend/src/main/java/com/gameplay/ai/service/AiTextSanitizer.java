package com.gameplay.ai.service;

import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/** 模型请求和审计摘要共用的脱敏边界，先脱敏再截断。 */
@Component
public class AiTextSanitizer {

    private static final Pattern SECRET_LABEL = Pattern.compile(
            "(?i)((?:password|passwd|api[_ -]?key|access[_ -]?token|token|secret|authorization|cookie|密码|密钥|验证码)\\s*(?:[:：=]|是|为)\\s*)[^\\s,，;；]+"
    );
    private static final Pattern BEARER = Pattern.compile("(?i)\\bBearer\\s+[a-z0-9._~+/=-]+");
    private static final Pattern TOKEN = Pattern.compile("(?i)\\b(?:sk-[a-z0-9_-]{8,}|eyJ[a-z0-9_-]+\\.[a-z0-9_-]+\\.[a-z0-9_-]+)\\b");
    private static final Pattern EMAIL = Pattern.compile("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}");
    private static final Pattern PHONE = Pattern.compile("(?<!\\d)(?:\\+?86[- ]?)?1[3-9]\\d{9}(?!\\d)");
    private static final Pattern ID_CARD = Pattern.compile("(?<!\\w)\\d{17}[0-9Xx](?!\\w)");
    private static final Pattern LONG_NUMBER = Pattern.compile("(?<!\\d)\\d{16,19}(?!\\d)");

    public String sanitize(String text, int maxLength) {
        if (text == null || maxLength <= 0) {
            return "";
        }
        String result = BEARER.matcher(text).replaceAll("Bearer [REDACTED]");
        result = SECRET_LABEL.matcher(result).replaceAll("$1[REDACTED]");
        result = TOKEN.matcher(result).replaceAll("[REDACTED]");
        result = EMAIL.matcher(result).replaceAll("[EMAIL]");
        result = PHONE.matcher(result).replaceAll("[PHONE]");
        result = ID_CARD.matcher(result).replaceAll("[ID]");
        result = LONG_NUMBER.matcher(result).replaceAll("[ACCOUNT]");
        return result.length() <= maxLength ? result : result.substring(0, maxLength);
    }
}
