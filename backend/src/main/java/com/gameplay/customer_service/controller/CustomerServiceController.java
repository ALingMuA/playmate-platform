package com.gameplay.customer_service.controller;

import com.gameplay.auth.security.JwtPrincipal;
import com.gameplay.common.api.ApiResponse;
import com.gameplay.common.exception.BusinessException;
import com.gameplay.common.exception.ErrorCode;
import com.gameplay.customer_service.dto.AiResponseView;
import com.gameplay.customer_service.dto.CloseConversationRequest;
import com.gameplay.customer_service.dto.ConversationCreateRequest;
import com.gameplay.customer_service.dto.ConversationView;
import com.gameplay.customer_service.dto.EvaluationRequest;
import com.gameplay.customer_service.dto.MessageSendRequest;
import com.gameplay.customer_service.dto.MessageView;
import com.gameplay.customer_service.service.CustomerConversationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 用户端客服会话接口（FR-C07~C15）。
 */
@RestController
@RequestMapping("/api/customer-service/conversations")
@RequiredArgsConstructor
public class CustomerServiceController {

    private final CustomerConversationService conversationService;

    /** 发起客服会话（FR-C07） */
    @PostMapping
    public ApiResponse<ConversationView> create(@Valid @RequestBody ConversationCreateRequest request,
                                                Authentication authentication) {
        return ApiResponse.ok(conversationService.create(currentUserId(authentication), request));
    }

    /** 触发 AI 应答（FR-C09、FR-C16~C22，详细设计 2.6） */
    @PostMapping("/{conversationId}/ai-responses")
    public ApiResponse<AiResponseView> aiRespond(@PathVariable Long conversationId,
                                                 @Valid @RequestBody MessageSendRequest request,
                                                 Authentication authentication) {
        return ApiResponse.ok(conversationService.aiRespond(
                currentUserId(authentication), conversationId, request));
    }

    /** 用户主动转人工（FR-C10） */
    @PostMapping("/{conversationId}/request-human")
    public ApiResponse<ConversationView> requestHuman(@PathVariable Long conversationId,
                                                      @RequestParam(required = false) String reason,
                                                      Authentication authentication) {
        return ApiResponse.ok(conversationService.requestHuman(
                currentUserId(authentication), conversationId, reason));
    }

    /** 我的会话列表（FR-C14） */
    @GetMapping("/mine")
    public ApiResponse<List<ConversationView>> mine(Authentication authentication) {
        return ApiResponse.ok(conversationService.listMine(currentUserId(authentication)));
    }

    /** 会话详情（FR-C12 状态查看） */
    @GetMapping("/{conversationId}")
    public ApiResponse<ConversationView> detail(@PathVariable Long conversationId,
                                                Authentication authentication) {
        return ApiResponse.ok(conversationService.getDetail(conversationId,
                currentUserId(authentication), roles(authentication)));
    }

    /** 会话消息补拉（详细设计 5.2：afterId 游标） */
    @GetMapping("/{conversationId}/messages")
    public ApiResponse<List<MessageView>> messages(@PathVariable Long conversationId,
                                                   @RequestParam(required = false) Long afterId,
                                                   @RequestParam(defaultValue = "50") int size,
                                                   Authentication authentication) {
        return ApiResponse.ok(conversationService.listMessages(conversationId, afterId, size,
                currentUserId(authentication), roles(authentication)));
    }

    /** 客服满意度评价（FR-C15） */
    @PostMapping("/{conversationId}/evaluation")
    public ApiResponse<Void> evaluate(@PathVariable Long conversationId,
                                      @Valid @RequestBody EvaluationRequest request,
                                      Authentication authentication) {
        conversationService.evaluate(currentUserId(authentication), conversationId, request);
        return ApiResponse.ok();
    }

    private Long currentUserId(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof JwtPrincipal principal)) {
            throw new BusinessException(ErrorCode.AUTH_TOKEN_INVALID);
        }
        return principal.userId();
    }

    private List<String> roles(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof JwtPrincipal principal)) {
            throw new BusinessException(ErrorCode.AUTH_TOKEN_INVALID);
        }
        return principal.roles();
    }
}