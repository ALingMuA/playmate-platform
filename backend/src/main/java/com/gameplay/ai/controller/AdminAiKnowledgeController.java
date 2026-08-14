package com.gameplay.ai.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.gameplay.ai.dto.KnowledgeBaseRequest;
import com.gameplay.ai.dto.KnowledgeBaseView;
import com.gameplay.ai.service.AiKnowledgeBaseService;
import com.gameplay.auth.security.JwtPrincipal;
import com.gameplay.common.api.ApiResponse;
import com.gameplay.common.exception.BusinessException;
import com.gameplay.common.exception.ErrorCode;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理端 AI 知识库接口（FR-C23）。
 */
@RestController
@RequestMapping("/api/admin/ai-knowledge")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminAiKnowledgeController {

    private final AiKnowledgeBaseService knowledgeBaseService;

    /** 分页查询知识库 */
    @GetMapping
    public ApiResponse<Page<KnowledgeBaseView>> list(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) Integer enabled,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size,
            Authentication authentication) {
        return ApiResponse.ok(knowledgeBaseService.list(currentUserId(authentication), category, enabled, page, size));
    }

    /** 新增知识条目 */
    @PostMapping
    public ApiResponse<KnowledgeBaseView> create(@Valid @RequestBody KnowledgeBaseRequest request,
                                                 Authentication authentication) {
        return ApiResponse.ok(knowledgeBaseService.create(currentUserId(authentication), request));
    }

    /** 编辑知识条目 */
    @PutMapping("/{id}")
    public ApiResponse<KnowledgeBaseView> update(@PathVariable Long id,
                                                 @Valid @RequestBody KnowledgeBaseRequest request,
                                                 Authentication authentication) {
        return ApiResponse.ok(knowledgeBaseService.update(id, request));
    }

    /** 启用/停用 */
    @PutMapping("/{id}/enabled")
    public ApiResponse<KnowledgeBaseView> setEnabled(@PathVariable Long id,
                                                     @RequestParam boolean enabled,
                                                     Authentication authentication) {
        return ApiResponse.ok(knowledgeBaseService.setEnabled(id, enabled));
    }

    /** 删除知识条目 */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id, Authentication authentication) {
        knowledgeBaseService.delete(id);
        return ApiResponse.ok();
    }

    private Long currentUserId(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof JwtPrincipal principal)) {
            throw new BusinessException(ErrorCode.AUTH_TOKEN_INVALID);
        }
        return principal.userId();
    }
}
