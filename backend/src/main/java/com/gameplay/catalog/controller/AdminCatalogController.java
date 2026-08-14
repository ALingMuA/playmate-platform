package com.gameplay.catalog.controller;

import com.gameplay.catalog.domain.Game;
import com.gameplay.catalog.domain.ServiceType;
import com.gameplay.catalog.domain.Tag;
import com.gameplay.catalog.dto.GameRequest;
import com.gameplay.catalog.dto.ServiceTypeRequest;
import com.gameplay.catalog.dto.TagRequest;
import com.gameplay.catalog.service.GameService;
import com.gameplay.catalog.service.ServiceTypeService;
import com.gameplay.catalog.service.TagService;
import com.gameplay.common.api.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 目录管理接口（仅管理员，FR-M10 游戏管理、FR-M11 服务类型管理、FR-M12 标签管理）。
 *
 * <p>类级 {@code @PreAuthorize("hasRole('ADMIN')")} 统一限定管理员角色；
 * 非管理员访问返回 403 PERMISSION_DENIED（全局异常处理）。</p>
 */
@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminCatalogController {

    private final GameService gameService;
    private final ServiceTypeService serviceTypeService;
    private final TagService tagService;

    // ==================== 游戏管理（FR-M10） ====================

    /** 游戏列表（含停用），可按名称关键词与启用状态过滤 */
    @GetMapping("/games")
    public ApiResponse<List<Game>> listGames(@RequestParam(required = false) String keyword,
                                             @RequestParam(required = false) Integer enabled) {
        return ApiResponse.ok(gameService.listGames(keyword, enabled));
    }

    /** 新增游戏 */
    @PostMapping("/games")
    public ApiResponse<Game> createGame(@Valid @RequestBody GameRequest request) {
        return ApiResponse.ok(gameService.createGame(request));
    }

    /** 编辑游戏 */
    @PutMapping("/games/{id}")
    public ApiResponse<Game> updateGame(@PathVariable Long id, @Valid @RequestBody GameRequest request) {
        return ApiResponse.ok(gameService.updateGame(id, request));
    }

    /** 删除游戏 */
    @DeleteMapping("/games/{id}")
    public ApiResponse<Void> deleteGame(@PathVariable Long id) {
        gameService.deleteGame(id);
        return ApiResponse.ok();
    }

    // ==================== 服务类型管理（FR-M11） ====================

    /** 服务类型列表（含停用），可按名称关键词与启用状态过滤 */
    @GetMapping("/service-types")
    public ApiResponse<List<ServiceType>> listServiceTypes(@RequestParam(required = false) String keyword,
                                                           @RequestParam(required = false) Integer enabled) {
        return ApiResponse.ok(serviceTypeService.listTypes(keyword, enabled));
    }

    /** 新增服务类型 */
    @PostMapping("/service-types")
    public ApiResponse<ServiceType> createServiceType(@Valid @RequestBody ServiceTypeRequest request) {
        return ApiResponse.ok(serviceTypeService.createType(request));
    }

    /** 编辑服务类型 */
    @PutMapping("/service-types/{id}")
    public ApiResponse<ServiceType> updateServiceType(@PathVariable Long id,
                                                      @Valid @RequestBody ServiceTypeRequest request) {
        return ApiResponse.ok(serviceTypeService.updateType(id, request));
    }

    /** 删除服务类型 */
    @DeleteMapping("/service-types/{id}")
    public ApiResponse<Void> deleteServiceType(@PathVariable Long id) {
        serviceTypeService.deleteType(id);
        return ApiResponse.ok();
    }

    // ==================== 标签管理（FR-M12） ====================

    /** 标签列表（含停用），可按名称关键词、分类、所属游戏、启用状态过滤 */
    @GetMapping("/tags")
    public ApiResponse<List<Tag>> listTags(@RequestParam(required = false) String keyword,
                                           @RequestParam(required = false) String category,
                                           @RequestParam(required = false) Long gameId,
                                           @RequestParam(required = false) Integer enabled) {
        return ApiResponse.ok(tagService.listTags(keyword, category, gameId, enabled));
    }

    /** 新增标签 */
    @PostMapping("/tags")
    public ApiResponse<Tag> createTag(@Valid @RequestBody TagRequest request) {
        return ApiResponse.ok(tagService.createTag(request));
    }

    /** 编辑标签 */
    @PutMapping("/tags/{id}")
    public ApiResponse<Tag> updateTag(@PathVariable Long id, @Valid @RequestBody TagRequest request) {
        return ApiResponse.ok(tagService.updateTag(id, request));
    }

    /** 删除标签 */
    @DeleteMapping("/tags/{id}")
    public ApiResponse<Void> deleteTag(@PathVariable Long id) {
        tagService.deleteTag(id);
        return ApiResponse.ok();
    }
}
