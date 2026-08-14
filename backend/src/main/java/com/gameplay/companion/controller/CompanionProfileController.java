package com.gameplay.companion.controller;

import com.gameplay.auth.security.JwtPrincipal;
import com.gameplay.common.api.ApiResponse;
import com.gameplay.common.exception.BusinessException;
import com.gameplay.common.exception.ErrorCode;
import com.gameplay.companion.dto.ProfileUpdateRequest;
import com.gameplay.companion.dto.ProfileView;
import com.gameplay.companion.dto.ServiceStatusUpdateRequest;
import com.gameplay.companion.service.CompanionProfileService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 陪玩师主页与接单状态接口（FR-P05/P06）。
 */
@RestController
@RequestMapping("/api/companion/profile")
@RequiredArgsConstructor
public class CompanionProfileController {

    private final CompanionProfileService profileService;

    /** 我的陪玩主页 */
    @GetMapping
    public ApiResponse<ProfileView> myProfile(Authentication authentication) {
        return ApiResponse.ok(profileService.getMyProfile(currentUserId(authentication)));
    }

    /** 更新主页（FR-P05） */
    @PutMapping
    public ApiResponse<ProfileView> updateProfile(@Valid @RequestBody ProfileUpdateRequest request,
                                                  Authentication authentication) {
        return ApiResponse.ok(profileService.updateProfile(currentUserId(authentication), request));
    }

    /** 设置接单状态（FR-P06）：AVAILABLE/BUSY/RESTING */
    @PutMapping("/service-status")
    public ApiResponse<ProfileView> updateServiceStatus(@Valid @RequestBody ServiceStatusUpdateRequest request,
                                                        Authentication authentication) {
        return ApiResponse.ok(profileService.updateServiceStatus(currentUserId(authentication),
                request.getServiceStatus()));
    }

    private Long currentUserId(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof JwtPrincipal principal)) {
            throw new BusinessException(ErrorCode.AUTH_TOKEN_INVALID);
        }
        return principal.userId();
    }
}
