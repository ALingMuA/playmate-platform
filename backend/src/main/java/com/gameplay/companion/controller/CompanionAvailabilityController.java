package com.gameplay.companion.controller;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.gameplay.auth.security.JwtPrincipal;
import com.gameplay.common.api.ApiResponse;
import com.gameplay.common.exception.BusinessException;
import com.gameplay.common.exception.ErrorCode;
import com.gameplay.companion.dto.AvailabilityCreateRequest;
import com.gameplay.companion.dto.AvailabilityView;
import com.gameplay.companion.service.AvailabilityService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 陪玩师档期管理接口（FR-P10/P11）。
 */
@RestController
@RequestMapping("/api/companion/availabilities")
@RequiredArgsConstructor
public class CompanionAvailabilityController {

    private final AvailabilityService availabilityService;

    /** 我的档期列表（可按时间范围过滤） */
    @GetMapping
    public ApiResponse<List<AvailabilityView>> list(
            @RequestParam(required = false)
            @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startAt,
            @RequestParam(required = false)
            @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endAt,
            Authentication authentication) {
        return ApiResponse.ok(availabilityService.listMine(currentUserId(authentication), startAt, endAt));
    }

    /** 新增可约时段（FR-P10） */
    @PostMapping
    public ApiResponse<AvailabilityView> create(@Valid @RequestBody AvailabilityCreateRequest request,
                                                Authentication authentication) {
        return ApiResponse.ok(availabilityService.createAvailable(currentUserId(authentication), request));
    }

    /** 设置临时不可约时段（FR-P10） */
    @PostMapping("/unavailable")
    public ApiResponse<AvailabilityView> createUnavailable(@Valid @RequestBody AvailabilityCreateRequest request,
                                                           Authentication authentication) {
        return ApiResponse.ok(availabilityService.createUnavailable(currentUserId(authentication), request));
    }

    /** 删除档期 */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id, Authentication authentication) {
        availabilityService.delete(currentUserId(authentication), id);
        return ApiResponse.ok();
    }

    private Long currentUserId(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof JwtPrincipal principal)) {
            throw new BusinessException(ErrorCode.AUTH_TOKEN_INVALID);
        }
        return principal.userId();
    }
}
