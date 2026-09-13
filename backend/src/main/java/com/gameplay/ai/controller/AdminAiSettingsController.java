package com.gameplay.ai.controller;

import com.gameplay.ai.config.AiModelConfigService;
import com.gameplay.ai.config.AiModelConfigValidator;
import com.gameplay.ai.dto.AiSettingsRequest;
import com.gameplay.ai.dto.AiSettingsView;
import com.gameplay.ai.strategy.ModelCallException;
import com.gameplay.ai.strategy.ModelEnhancedResponder;
import com.gameplay.auth.security.JwtPrincipal;
import com.gameplay.common.api.ApiResponse;
import com.gameplay.common.exception.BusinessException;
import com.gameplay.common.exception.ErrorCode;
import java.util.concurrent.Semaphore;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/ai/settings")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminAiSettingsController {
    private final AiModelConfigService settings;
    private final ModelEnhancedResponder responder;
    private final Semaphore probes = new Semaphore(2);

    @GetMapping
    public ApiResponse<AiSettingsView> get() { return ApiResponse.ok(settings.view()); }

    @PutMapping
    public ApiResponse<AiSettingsView> save(@RequestBody AiSettingsRequest request, Authentication auth) {
        return ApiResponse.ok(settings.save(request, userId(auth)));
    }

    @DeleteMapping
    public ApiResponse<AiSettingsView> reset(@RequestParam long version, Authentication auth) {
        return ApiResponse.ok(settings.reset(version, userId(auth)));
    }

    public record TestResult(boolean success, String errorCode, String message, long elapsedMs,
                             Integer httpStatus, String model) {}

    @PostMapping("/test")
    public ApiResponse<TestResult> test(@RequestBody AiSettingsRequest request) {
        var candidate = settings.candidate(request);
        AiModelConfigValidator.validate(candidate, true);
        if (!probes.tryAcquire()) return ApiResponse.ok(new TestResult(false, "TEST_BUSY", "正在测试其他配置，请稍后再试", 0, null, candidate.getName()));
        long start = System.nanoTime();
        try {
            var result = responder.probe(candidate);
            if (result.needsHuman() || result.fallback()) throw new ModelCallException("MODEL_UNGROUNDED_RESPONSE", 200);
            return ApiResponse.ok(new TestResult(true, null, "连接成功，模型回答已通过客服格式校验；测试未保存配置", elapsed(start), 200, candidate.getName()));
        } catch (ModelCallException error) {
            return ApiResponse.ok(new TestResult(false, error.errorCode(), message(error.errorCode()), elapsed(start), error.httpStatus(), candidate.getName()));
        } catch (Exception ignored) {
            return ApiResponse.ok(new TestResult(false, "MODEL_UNAVAILABLE", "连接失败，请检查接口地址与网络", elapsed(start), null, candidate.getName()));
        } finally { probes.release(); }
    }

    private long elapsed(long start) { return (System.nanoTime() - start) / 1_000_000; }
    private String message(String code) {
        return switch (code) {
            case "MODEL_AUTH" -> "鉴权失败，请检查密钥、认证方式、账号权限或余额";
            case "MODEL_NOT_FOUND" -> "接口或模型不存在（404），请核对完整请求地址、模型 ID 和账号可用模型";
            case "MODEL_BAD_REQUEST" -> "提供商拒绝请求参数，请检查模型支持的温度、输出上限参数名和扩展参数";
            case "MODEL_RATE_LIMIT" -> "提供商限流或额度不足，请稍后重试并检查账号额度";
            case "MODEL_TIMEOUT" -> "模型响应超时，可调整超时时间或更换响应更快的模型";
            case "MODEL_OUTPUT_LIMIT" -> "输出达到 token 上限，回答被截断；请提高输出上限或调整推理参数";
            case "MODEL_INVALID_RESPONSE" -> "接口已返回，但响应不是完整的 Chat Completions 客服 JSON；请检查协议或更换模型";
            case "MODEL_CONFIGURATION" -> "请求配置无效，请检查地址、认证和参数";
            case "MODEL_UNGROUNDED_RESPONSE", "MODEL_UNSUPPORTED_REFERENCE" -> "模型已响应，但回答未通过客服依据校验";
            default -> "模型服务暂不可用，请检查网络和提供商状态";
        };
    }
    private Long userId(Authentication auth) {
        if (auth == null || !(auth.getPrincipal() instanceof JwtPrincipal principal))
            throw new BusinessException(ErrorCode.AUTH_TOKEN_INVALID);
        return principal.userId();
    }
}
