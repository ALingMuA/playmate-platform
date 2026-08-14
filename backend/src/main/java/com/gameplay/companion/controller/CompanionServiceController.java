package com.gameplay.companion.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.gameplay.auth.security.JwtPrincipal;
import com.gameplay.common.api.ApiResponse;
import com.gameplay.common.exception.BusinessException;
import com.gameplay.common.exception.ErrorCode;
import com.gameplay.companion.dto.ServiceRequest;
import com.gameplay.companion.dto.ServiceView;
import com.gameplay.companion.service.CompanionServiceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 陪玩师服务项目管理接口（FR-P07~P09）。
 */
@RestController
@RequestMapping("/api/companion/services")
@RequiredArgsConstructor
public class CompanionServiceController {

    private final CompanionServiceService serviceService;

    /** 我的服务分页 */
    @GetMapping
    public ApiResponse<Page<ServiceView>> list(@RequestParam(defaultValue = "1") long page,
                                               @RequestParam(defaultValue = "10") long size,
                                               Authentication authentication) {
        return ApiResponse.ok(serviceService.listMy(currentUserId(authentication), page, size));
    }

    /** 新增服务项目（FR-P07） */
    @PostMapping
    public ApiResponse<ServiceView> create(@Valid @RequestBody ServiceRequest request,
                                           Authentication authentication) {
        return ApiResponse.ok(serviceService.create(currentUserId(authentication), request));
    }

    /** 服务详情 */
    @GetMapping("/{id}")
    public ApiResponse<ServiceView> detail(@PathVariable Long id, Authentication authentication) {
        return ApiResponse.ok(serviceService.getMyDetail(currentUserId(authentication), id));
    }

    /** 编辑服务（FR-P08，编辑后重新审核） */
    @PutMapping("/{id}")
    public ApiResponse<ServiceView> update(@PathVariable Long id,
                                           @Valid @RequestBody ServiceRequest request,
                                           Authentication authentication) {
        return ApiResponse.ok(serviceService.update(currentUserId(authentication), id, request));
    }

    /** 上架/下架（FR-P08） */
    @PutMapping("/{id}/shelf")
    public ApiResponse<ServiceView> setShelf(@PathVariable Long id,
                                             @RequestParam boolean onShelf,
                                             Authentication authentication) {
        return ApiResponse.ok(serviceService.setShelf(currentUserId(authentication), id, onShelf));
    }

    private Long currentUserId(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof JwtPrincipal principal)) {
            throw new BusinessException(ErrorCode.AUTH_TOKEN_INVALID);
        }
        return principal.userId();
    }
}
