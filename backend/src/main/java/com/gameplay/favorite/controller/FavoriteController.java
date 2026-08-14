package com.gameplay.favorite.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.gameplay.auth.security.JwtPrincipal;
import com.gameplay.common.api.ApiResponse;
import com.gameplay.common.exception.BusinessException;
import com.gameplay.common.exception.ErrorCode;
import com.gameplay.favorite.dto.FavoriteView;
import com.gameplay.favorite.service.FavoriteService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户端收藏接口（FR-U06）。
 */
@RestController
@RequestMapping("/api/favorites")
@RequiredArgsConstructor
public class FavoriteController {

    private final FavoriteService favoriteService;

    /** 收藏陪玩师 */
    @PostMapping("/{companionUserId}")
    public ApiResponse<Void> add(@PathVariable Long companionUserId, Authentication authentication) {
        favoriteService.add(currentUserId(authentication), companionUserId);
        return ApiResponse.ok();
    }

    /** 取消收藏 */
    @DeleteMapping("/{companionUserId}")
    public ApiResponse<Void> remove(@PathVariable Long companionUserId, Authentication authentication) {
        favoriteService.remove(currentUserId(authentication), companionUserId);
        return ApiResponse.ok();
    }

    /** 我的收藏分页 */
    @GetMapping
    public ApiResponse<Page<FavoriteView>> list(@RequestParam(defaultValue = "1") long page,
                                                @RequestParam(defaultValue = "10") long size,
                                                Authentication authentication) {
        return ApiResponse.ok(favoriteService.page(currentUserId(authentication), page, size));
    }

    private Long currentUserId(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof JwtPrincipal principal)) {
            throw new BusinessException(ErrorCode.AUTH_TOKEN_INVALID);
        }
        return principal.userId();
    }
}
