package com.gameplay.ai.service;

import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/** 模型请求和审计摘要共用的脱敏边界，先清除混淆控制符并脱敏再截断。 */
@Component
public class AiTextSanitizer {

    private static final Pattern INVISIBLE_CONTROLS = Pattern.compile("[\\u0000-\\u0008\\u000B\\u000C\\u000E-\\u001F\\u007F\\u200B-\\u200D\\uFEFF]");
    private static final Pattern SECRET_LABEL = Pattern.compile(
            "(?i)((?:password|passwd|pwd|api[_ -]?key|access[_ -]?token|refresh[_ -]?token|session[_ -]?id|token|secret|authorization|cookie|口令|密码|密钥|验证码)[\"']?\\s*(?:[:：=]|是|为)\\s*[\"']?)[^\\s\"'`,，;；}]+"
    );
    private static final Pattern QUERY_SECRET = Pattern.compile(
            "(?i)([?&](?:token|api[_-]?key|access[_-]?token|secret|password|auth|signature|credential|ticket)=)[^&\\s#]+"
    );
    private static final Pattern AUTH_HEADER = Pattern.compile("(?i)\\b(?:Bearer|Basic|Token)\\s+[a-z0-9._~+/=-]{6,}");
    private static final Pattern TOKEN = Pattern.compile(
            "(?i)\\b(?:sk-[a-z0-9_-]{8,}|gh[pousr]_[a-z0-9_]{16,}|AKIA[0-9A-Z]{16}|xox[baprs]-[a-z0-9-]{10,}|eyJ[a-z0-9_-]{6,}\\.[a-z0-9_-]{6,}(?:\\.[a-z0-9_-]*)?)\\b"
    );
    private static final Pattern EMAIL = Pattern.compile("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}");
    private static final Pattern PHONE = Pattern.compile("(?<!\\d)(?:\\+?86[- ]?)?1[3-9]\\d{9}(?!\\d)");
    private static final Pattern ID_CARD = Pattern.compile("(?<!\\w)\\d{17}[0-9Xx](?!\\w)");
    private static final Pattern LONG_NUMBER = Pattern.compile("(?<!\\d)\\d{16,19}(?!\\d)");

    public String sanitize(String text, int maxLength) {
        if (text == null || maxLength <= 0) {
            return "";
        }
        String result = INVISIBLE_CONTROLS.matcher(text).replaceAll("");
        result = AUTH_HEADER.matcher(result).replaceAll("[REDACTED]");
        result = QUERY_SECRET.matcher(result).replaceAll("$1[REDACTED]");
        result = SECRET_LABEL.matcher(result).replaceAll("$1[REDACTED]");
        result = TOKEN.matcher(result).replaceAll("[REDACTED]");
        result = EMAIL.matcher(result).replaceAll("[EMAIL]");
        result = PHONE.matcher(result).replaceAll("[PHONE]");
        result = ID_CARD.matcher(result).replaceAll("[ID]");
        result = LONG_NUMBER.matcher(result).replaceAll("[ACCOUNT]");
        return result.length() <= maxLength ? result : result.substring(0, maxLength);
    }
}
