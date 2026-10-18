package com.gameplay.companion.controller;

import com.gameplay.common.api.ApiResponse;
import com.gameplay.companion.dto.PublicCompanionProfileView;
import com.gameplay.companion.service.CompanionProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 游客可读的陪玩师公开主页资料接口。 */
@RestController
@RequestMapping("/api/companion-profiles")
@RequiredArgsConstructor
public class PublicCompanionProfileController {

    private final CompanionProfileService profileService;

    @GetMapping("/{userId}")
    public ApiResponse<PublicCompanionProfileView> getPublicProfile(@PathVariable Long userId) {
        return ApiResponse.ok(profileService.getPublicProfile(userId));
    }
}
