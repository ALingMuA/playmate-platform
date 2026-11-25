package com.gameplay.customer_service.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.gameplay.auth.security.JwtPrincipal;
import com.gameplay.common.api.ApiResponse;
import com.gameplay.common.exception.BusinessException;
import com.gameplay.common.exception.ErrorCode;
import com.gameplay.customer_service.dto.CloseConversationRequest;
import com.gameplay.customer_service.dto.ConversationView;
import com.gameplay.customer_service.dto.InternalNoteRequest;
import com.gameplay.customer_service.dto.InternalNoteView;
import com.gameplay.customer_service.dto.MessageSendRequest;
import com.gameplay.customer_service.dto.MessageView;
import com.gameplay.customer_service.service.CustomerConversationService;
import com.gameplay.customer_service.service.CustomerMessageService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 管理端升级客服会话处理接口（FR-C28：管理员接管 ESCALATED_ADMIN 会话的查看、回复与关闭）。
 */
@RestController
@RequestMapping("/api/admin/customer-service/conversations")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
@Validated
public class AdminCsEscalationController {

    private final CustomerConversationService conversationService;
    private final CustomerMessageService messageService;

    /** 分页查询已升级管理员会话（默认 ESCALATED_ADMIN） */
    @GetMapping
    public ApiResponse<Page<ConversationView>> list(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") @Min(1) long page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) long size) {
        return ApiResponse.ok(conversationService.listEscalated(status, page, size));
    }

    /** 管理员查看升级会话详情 */
    @GetMapping("/{conversationId}")
    public ApiResponse<ConversationView> detail(@PathVariable Long conversationId,
                                                Authentication authentication) {
        return ApiResponse.ok(conversationService.getDetail(
                conversationId, currentUserId(authentication), List.of("ADMIN")));
    }

    /** 管理员查看升级会话消息 */
    @GetMapping("/{conversationId}/messages")
    public ApiResponse<List<MessageView>> messages(@PathVariable Long conversationId,
                                                   @RequestParam(required = false) Long afterId,
                                                   @RequestParam(defaultValue = "50") @Min(1) @Max(200) int size,
                                                   Authentication authentication) {
        return ApiResponse.ok(conversationService.listMessages(
                conversationId, afterId, size, currentUserId(authentication), List.of("ADMIN")));
    }

    /** 管理员回复升级会话 */
    @PostMapping("/{conversationId}/messages")
    public ApiResponse<MessageView> reply(@PathVariable Long conversationId,
                                          @Valid @RequestBody MessageSendRequest request,
                                          Authentication authentication) {
        return ApiResponse.ok(conversationService.adminReply(
                currentUserId(authentication), conversationId, request));
    }

    /** 管理员关闭升级会话 */
    @PostMapping("/{conversationId}/close")
    public ApiResponse<ConversationView> close(@PathVariable Long conversationId,
                                               @Valid @RequestBody CloseConversationRequest request,
                                               Authentication authentication) {
        return ApiResponse.ok(conversationService.adminClose(
                currentUserId(authentication), conversationId, request));
    }

    /** 管理员查看内部备注 */
    @GetMapping("/{conversationId}/notes")
    public ApiResponse<List<InternalNoteView>> notes(@PathVariable Long conversationId,
                                                     Authentication authentication) {
        return ApiResponse.ok(messageService.listInternalNotes(
                currentUserId(authentication), true, conversationId));
    }

    /** 管理员添加内部备注 */
    @PostMapping("/{conversationId}/notes")
    public ApiResponse<InternalNoteView> addNote(@PathVariable Long conversationId,
                                                 @Valid @RequestBody InternalNoteRequest request,
                                                 Authentication authentication) {
        return ApiResponse.ok(messageService.addInternalNote(
                currentUserId(authentication), "ADMIN", conversationId, request));
    }

    private Long currentUserId(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof JwtPrincipal principal)) {
            throw new BusinessException(ErrorCode.AUTH_TOKEN_INVALID);
        }
        return principal.userId();
    }
}
