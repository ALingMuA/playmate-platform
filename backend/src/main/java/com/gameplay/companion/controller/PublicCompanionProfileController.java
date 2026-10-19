package com.gameplay.companion.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.gameplay.common.api.ApiResponse;
import com.gameplay.companion.dto.PublicCompanionCardView;
import com.gameplay.companion.dto.PublicCompanionProfileView;
import com.gameplay.companion.service.CompanionProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 游客可读的陪玩师公开主页资料接口。 */
@RestController
@RequestMapping("/api/companion-profiles")
@RequiredArgsConstructor
public class PublicCompanionProfileController {

    private final CompanionProfileService profileService;

    /**
     * 陪玩师公开列表分页（FR-U02：用户端按陪玩师维度浏览）。
     *
     * <p>只返回认证通过、账号启用且存在在架可预约服务的陪玩师；
     * 携带 gameId 时按"该游戏下有在架服务"收窄，聚合摘要同口径收窄。</p>
     */
    @GetMapping
    public ApiResponse<Page<PublicCompanionCardView>> list(@RequestParam(required = false) Long gameId,
                                                           @RequestParam(defaultValue = "1") long page,
                                                           @RequestParam(defaultValue = "10") long size) {
        return ApiResponse.ok(profileService.listPublicCards(gameId, page, size));
    }

    @GetMapping("/{userId}")
    public ApiResponse<PublicCompanionProfileView> getPublicProfile(@PathVariable Long userId) {
        return ApiResponse.ok(profileService.getPublicProfile(userId));
    }
}
