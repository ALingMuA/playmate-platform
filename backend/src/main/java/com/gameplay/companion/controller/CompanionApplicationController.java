package com.gameplay.companion.controller;

import com.gameplay.auth.security.JwtPrincipal;
import com.gameplay.common.api.ApiResponse;
import com.gameplay.common.exception.BusinessException;
import com.gameplay.common.exception.ErrorCode;
import com.gameplay.companion.dto.ApplicationSubmitRequest;
import com.gameplay.companion.dto.ApplicationView;
import com.gameplay.companion.service.ApplicationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 陪玩师入驻申请接口（FR-P01~P04，陪玩师端）。
 */
@RestController
@RequestMapping("/api/companion/applications")
@RequiredArgsConstructor
public class CompanionApplicationController {

    private final ApplicationService applicationService;

    /** 提交入驻申请（FR-P01/P02/P04） */
    @PostMapping
    public ApiResponse<ApplicationView> submit(@Valid @RequestBody ApplicationSubmitRequest request,
                                               Authentication authentication) {
        return ApiResponse.ok(applicationService.submit(currentUserId(authentication), request));
    }

    /** 我的申请记录（FR-P03） */
    @GetMapping("/mine")
    public ApiResponse<List<ApplicationView>> myApplications(Authentication authentication) {
        return ApiResponse.ok(applicationService.myApplications(currentUserId(authentication)));
    }

    /** 申请详情（FR-P03） */
    @GetMapping("/{id}")
    public ApiResponse<ApplicationView> detail(@PathVariable Long id, Authentication authentication) {
        return ApiResponse.ok(applicationService.getDetail(currentUserId(authentication), id));
    }

    private Long currentUserId(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof JwtPrincipal principal)) {
            throw new BusinessException(ErrorCode.AUTH_TOKEN_INVALID);
        }
        return principal.userId();
    }
}
