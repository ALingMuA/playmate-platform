package com.gameplay.catalog.controller;

import com.gameplay.catalog.domain.Game;
import com.gameplay.catalog.domain.ServiceType;
import com.gameplay.catalog.dto.TagView;
import com.gameplay.catalog.service.GameService;
import com.gameplay.catalog.service.ServiceTypeService;
import com.gameplay.catalog.service.TagService;
import com.gameplay.common.api.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 目录公开接口（概要设计 8.x 分组：游戏与服务浏览）。
 *
 * <p>游客与登录用户均可访问（SecurityConfig 已放行 /api/games、/api/service-types、/api/tags）；
 * 只返回启用状态的基础数据，与《概要设计》"前台查询只显示已启用游戏"约束一致。</p>
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class CatalogController {

    private final GameService gameService;
    private final ServiceTypeService serviceTypeService;
    private final TagService tagService;

    /** 已启用游戏列表（FR-U01 游戏分类浏览） */
    @GetMapping("/games")
    public ApiResponse<List<Game>> listGames() {
        return ApiResponse.ok(gameService.listEnabledGames());
    }

    /** 已启用游戏详情（FR-U01） */
    @GetMapping("/games/{id}")
    public ApiResponse<Game> getGame(@PathVariable Long id) {
        return ApiResponse.ok(gameService.getEnabledGame(id));
    }

    /** 已启用服务类型列表（FR-M11 前台展示） */
    @GetMapping("/service-types")
    public ApiResponse<List<ServiceType>> listServiceTypes() {
        return ApiResponse.ok(serviceTypeService.listEnabledTypes());
    }

    /**
     * 标签列表（FR-U05、FR-P02）。
     * <p>gameId 为空返回全部启用标签；指定 gameId 返回该游戏标签 + 通用标签（gameId=0）。</p>
     */
    @GetMapping("/tags")
    public ApiResponse<List<TagView>> listTags(@RequestParam(required = false) Long gameId) {
        return ApiResponse.ok(tagService.listEnabledTags(gameId));
    }
}
