package com.gameplay.companion.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.gameplay.common.api.ApiResponse;
import com.gameplay.companion.dto.ServiceView;
import com.gameplay.companion.service.CompanionServiceService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 公开服务浏览接口（FR-U02/U03：用户端查找可预约服务）。
 *
 * <p>只返回审核通过（APPROVED）且已上架（ON_SHELF）的服务；
 * 按游戏、陪玩师过滤，支持分页。</p>
 */
@RestController
@RequestMapping("/api/companion-services")
@RequiredArgsConstructor
public class PublicCompanionServiceController {

    private final CompanionServiceService serviceService;

    /** 可预约服务分页（FR-U02/U03） */
    @GetMapping
    public ApiResponse<Page<ServiceView>> list(@RequestParam(required = false) Long gameId,
                                               @RequestParam(required = false) Long companionUserId,
                                               @RequestParam(defaultValue = "1") long page,
                                               @RequestParam(defaultValue = "10") long size) {
        return ApiResponse.ok(serviceService.listBookable(gameId, companionUserId, page, size));
    }
}
