package com.gameplay.customer_service.controller;

import com.gameplay.auth.security.JwtPrincipal;
import com.gameplay.common.api.ApiResponse;
import com.gameplay.common.exception.BusinessException;
import com.gameplay.common.exception.ErrorCode;
import com.gameplay.customer_service.dto.CloseConversationRequest;
import com.gameplay.customer_service.dto.ConversationClaimRequest;
import com.gameplay.customer_service.dto.ConversationView;
import com.gameplay.customer_service.dto.CsWorkStatusUpdateRequest;
import com.gameplay.customer_service.dto.CsAccountView;
import com.gameplay.customer_service.dto.EscalateRequest;
import com.gameplay.customer_service.dto.InternalNoteRequest;
import com.gameplay.customer_service.dto.InternalNoteView;
import com.gameplay.customer_service.dto.MessageSendRequest;
import com.gameplay.customer_service.dto.MessageView;
import com.gameplay.customer_service.dto.QueueItemView;
import com.gameplay.customer_service.service.CsAccountService;
import com.gameplay.customer_service.service.CustomerConversationService;
import com.gameplay.customer_service.service.CustomerMessageService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 客服工作台接口（FR-C06、FR-C24~C29）。
 */
@RestController
@RequestMapping("/api/customer-service")
@RequiredArgsConstructor
@PreAuthorize("hasRole('CUSTOMER_SERVICE')")
public class CsWorkbenchController {

    private final CsAccountService csAccountService;
    private final CustomerConversationService conversationService;
    private final CustomerMessageService messageService;

    /** 我的客服账号（含工作状态） */
    @GetMapping("/me")
    public ApiResponse<CsAccountView> me(Authentication authentication) {
        return ApiResponse.ok(csAccountService.getViewByUserId(currentUserId(authentication)));
    }

    /** 设置工作状态（FR-C06） */
    @PutMapping("/me/work-status")
    public ApiResponse<CsAccountView> setWorkStatus(@Valid @RequestBody CsWorkStatusUpdateRequest request,
                                                    Authentication authentication) {
        return ApiResponse.ok(csAccountService.setWorkStatus(currentUserId(authentication), request));
    }

    /** 人工会话队列（FR-C24） */
    @GetMapping("/queue")
    public ApiResponse<List<QueueItemView>> queue(Authentication authentication) {
        return ApiResponse.ok(conversationService.queue(currentUserId(authentication)));
    }

    /** 我的处理中会话 */
    @GetMapping("/conversations/assigned")
    public ApiResponse<List<ConversationView>> assigned(Authentication authentication) {
        return ApiResponse.ok(conversationService.listAssigned(currentUserId(authentication)));
    }

    /** 领取会话（FR-C25） */
    @PostMapping("/conversations/{conversationId}/claim")
    public ApiResponse<ConversationView> claim(@PathVariable Long conversationId,
                                               @Valid @RequestBody ConversationClaimRequest request,
                                               Authentication authentication) {
        return ApiResponse.ok(conversationService.claim(
                currentUserId(authentication), conversationId, request));
    }

    /** 客服回复（FR-C26） */
    @PostMapping("/conversations/{conversationId}/messages")
    public ApiResponse<MessageView> reply(@PathVariable Long conversationId,
                                          @Valid @RequestBody MessageSendRequest request,
                                          Authentication authentication) {
        return ApiResponse.ok(conversationService.reply(
                currentUserId(authentication), conversationId, request));
    }

    /** 内部备注（FR-C27） */
    @PostMapping("/conversations/{conversationId}/notes")
    public ApiResponse<InternalNoteView> addNote(@PathVariable Long conversationId,
                                                 @Valid @RequestBody InternalNoteRequest request,
                                                 Authentication authentication) {
        return ApiResponse.ok(messageService.addInternalNote(currentUserId(authentication),
                "CUSTOMER_SERVICE", conversationId, request));
    }

    /** 内部备注列表（FR-C27，用户不可见） */
    @GetMapping("/conversations/{conversationId}/notes")
    public ApiResponse<List<InternalNoteView>> notes(@PathVariable Long conversationId,
                                                     Authentication authentication) {
        return ApiResponse.ok(messageService.listInternalNotes(conversationId));
    }

    /** 转交管理员（FR-C28） */
    @PostMapping("/conversations/{conversationId}/escalate")
    public ApiResponse<ConversationView> escalate(@PathVariable Long conversationId,
                                                  @Valid @RequestBody EscalateRequest request,
                                                  Authentication authentication) {
        return ApiResponse.ok(conversationService.escalate(
                currentUserId(authentication), conversationId, request));
    }

    /** 关闭会话（FR-C29） */
    @PostMapping("/conversations/{conversationId}/close")
    public ApiResponse<ConversationView> close(@PathVariable Long conversationId,
                                               @Valid @RequestBody CloseConversationRequest request,
                                               Authentication authentication) {
        return ApiResponse.ok(conversationService.close(
                currentUserId(authentication), conversationId, request));
    }

    private Long currentUserId(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof JwtPrincipal principal)) {
            throw new BusinessException(ErrorCode.AUTH_TOKEN_INVALID);
        }
        return principal.userId();
    }
}