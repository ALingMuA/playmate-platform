package com.gameplay.companion.controller;

import com.gameplay.auth.security.JwtPrincipal;
import com.gameplay.common.api.ApiResponse;
import com.gameplay.common.exception.BusinessException;
import com.gameplay.common.exception.ErrorCode;
import com.gameplay.companion.dto.AvailabilityTemplateSaveRequest;
import com.gameplay.companion.dto.AvailabilityTemplateView;
import com.gameplay.companion.service.AvailabilityTemplateService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 陪玩师档期周期模板接口（FR-P10 周期设置）：按周几 + 每日时段（或全天）+ 生效日期范围
 * 循环生成可约档期，滚动续期到未来 28 天，设置一次长期生效。
 */
@RestController
@RequestMapping("/api/companion/availability-templates")
@PreAuthorize("hasAnyRole('COMPANION', 'ADMIN')")
@RequiredArgsConstructor
public class CompanionAvailabilityTemplateController {

    private final AvailabilityTemplateService templateService;

    /** 我的模板列表（触发滚动续期） */
    @GetMapping
    public ApiResponse<List<AvailabilityTemplateView>> list(Authentication authentication) {
        return ApiResponse.ok(templateService.listMine(currentUserId(authentication)));
    }

    /** 新建模板并立即生成未来窗口档期 */
    @PostMapping
    public ApiResponse<AvailabilityTemplateView> create(@Valid @RequestBody AvailabilityTemplateSaveRequest request,
                                                        Authentication authentication) {
        return ApiResponse.ok(templateService.create(currentUserId(authentication), request));
    }

    /** 修改模板：清理未来未占用实例并按新规则重生成 */
    @PutMapping("/{id}")
    public ApiResponse<AvailabilityTemplateView> update(@PathVariable Long id,
                                                         @Valid @RequestBody AvailabilityTemplateSaveRequest request,
                                                         Authentication authentication) {
        return ApiResponse.ok(templateService.update(currentUserId(authentication), id, request));
    }

    /** 启用/停用模板 */
    @PutMapping("/{id}/status")
    public ApiResponse<AvailabilityTemplateView> toggleStatus(@PathVariable Long id,
                                                              @RequestParam boolean enabled,
                                                              Authentication authentication) {
        return ApiResponse.ok(templateService.toggleStatus(currentUserId(authentication), id, enabled));
    }

    /** 删除模板（清理未来未占用实例，历史实例保留） */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id, Authentication authentication) {
        templateService.delete(currentUserId(authentication), id);
        return ApiResponse.ok();
    }

    private Long currentUserId(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof JwtPrincipal principal)) {
            throw new BusinessException(ErrorCode.AUTH_TOKEN_INVALID);
        }
        return principal.userId();
    }
}
