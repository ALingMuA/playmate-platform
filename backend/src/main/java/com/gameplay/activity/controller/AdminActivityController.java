package com.gameplay.activity.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.gameplay.activity.dto.ActivityRequest;
import com.gameplay.activity.dto.ActivityView;
import com.gameplay.activity.service.PlatformActivityService;
import com.gameplay.auth.security.JwtPrincipal;
import com.gameplay.common.api.ApiResponse;
import com.gameplay.common.exception.BusinessException;
import com.gameplay.common.exception.ErrorCode;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/**
 * 管理端平台活动管理接口。
 */
@RestController
@RequestMapping("/api/admin/activities")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminActivityController {

    private final PlatformActivityService activityService;

    @GetMapping
    public ApiResponse<Page<ActivityView>> list(@RequestParam(required = false) String status,
                                                @RequestParam(defaultValue = "1") long page,
                                                @RequestParam(defaultValue = "10") long size) {
        return ApiResponse.ok(activityService.pageActivities(status, page, size));
    }

    @PostMapping
    public ApiResponse<ActivityView> create(@Valid @RequestBody ActivityRequest request,
                                            Authentication authentication) {
        return ApiResponse.ok(activityService.createActivity(currentAdminId(authentication), request));
    }

    @PutMapping("/{id}")
    public ApiResponse<ActivityView> update(@PathVariable Long id,
                                            @Valid @RequestBody ActivityRequest request) {
        return ApiResponse.ok(activityService.updateActivity(id, request));
    }

    @PostMapping("/{id}/publish")
    public ApiResponse<ActivityView> publish(@PathVariable Long id) {
        return ApiResponse.ok(activityService.publishActivity(id));
    }

    @PostMapping("/{id}/offline")
    public ApiResponse<ActivityView> offline(@PathVariable Long id) {
        return ApiResponse.ok(activityService.offlineActivity(id));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        activityService.deleteActivity(id);
        return ApiResponse.ok();
    }

    private Long currentAdminId(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof JwtPrincipal principal)) {
            throw new BusinessException(ErrorCode.AUTH_TOKEN_INVALID);
        }
        return principal.userId();
    }
}
