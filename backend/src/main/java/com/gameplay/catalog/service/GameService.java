package com.gameplay.catalog.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.gameplay.catalog.domain.Game;
import com.gameplay.catalog.dto.GameRequest;
import com.gameplay.catalog.mapper.GameMapper;
import com.gameplay.common.exception.BusinessException;
import com.gameplay.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 游戏服务（FR-U01 游戏分类浏览、FR-M10 游戏管理）。
 *
 * <p>公开查询只返回启用状态的游戏；管理接口（增删改查）仅管理员可调用，
 * 权限控制在 {@code AdminCatalogController} 的 {@code @PreAuthorize} 上。</p>
 */
@Service
@RequiredArgsConstructor
public class GameService {

    private final GameMapper gameMapper;

    // ==================== 公开查询（前台，仅启用） ====================

    /** 已启用游戏列表，按排序号、ID 升序（FR-U01） */
    public List<Game> listEnabledGames() {
        return gameMapper.selectList(new LambdaQueryWrapper<Game>()
                .eq(Game::getEnabled, 1)
                .orderByAsc(Game::getSortNo)
                .orderByAsc(Game::getId));
    }

    /**
     * 游戏详情（FR-U01）。
     * <p>仅返回启用状态的游戏；不存在或已停用时按"未找到"处理，避免向游客暴露停用数据。</p>
     */
    public Game getEnabledGame(Long id) {
        Game game = gameMapper.selectOne(new LambdaQueryWrapper<Game>()
                .eq(Game::getId, id)
                .eq(Game::getEnabled, 1));
        if (game == null) {
            throw new BusinessException(ErrorCode.GAME_NOT_FOUND);
        }
        return game;
    }

    // ==================== 管理操作（FR-M10） ====================

    /** 管理列表：可按名称关键词模糊查询、按启用状态过滤，按排序号、ID 升序 */
    public List<Game> listGames(String keyword, Integer enabled) {
        return gameMapper.selectList(new LambdaQueryWrapper<Game>()
                .like(StringUtils.hasText(keyword), Game::getGameName, keyword)
                .eq(enabled != null, Game::getEnabled, enabled)
                .orderByAsc(Game::getSortNo)
                .orderByAsc(Game::getId));
    }

    /** 新增游戏（FR-M10）：名称唯一性校验后落库 */
    @Transactional
    public Game createGame(GameRequest request) {
        String name = request.getGameName().trim();
        checkNameUnique(name, null);
        Game game = new Game();
        game.setGameName(name);
        game.setGameIconUrl(defaultOrEmpty(request.getGameIconUrl()));
        game.setGameIntro(defaultOrEmpty(request.getGameIntro()));
        game.setSortNo(request.getSortNo() != null ? request.getSortNo() : 0);
        game.setEnabled(request.getEnabled() != null ? request.getEnabled() : 1);
        gameMapper.insert(game);
        return game;
    }

    /** 编辑游戏（FR-M10）：名称唯一性校验（排除自身）后全量更新 */
    @Transactional
    public Game updateGame(Long id, GameRequest request) {
        Game game = requireGame(id);
        String name = request.getGameName().trim();
        checkNameUnique(name, id);
        game.setGameName(name);
        game.setGameIconUrl(defaultOrEmpty(request.getGameIconUrl()));
        game.setGameIntro(defaultOrEmpty(request.getGameIntro()));
        game.setSortNo(request.getSortNo() != null ? request.getSortNo() : 0);
        game.setEnabled(request.getEnabled() != null ? request.getEnabled() : 1);
        gameMapper.updateById(game);
        return game;
    }

    /**
     * 删除游戏（FR-M10）。
     * <p>当前为物理删除；后续服务/订单模块接入后，此处应校验是否存在
     * 关联的陪玩服务或有效订单（停用优先，不允许直接删除）。</p>
     */
    @Transactional
    public void deleteGame(Long id) {
        requireGame(id);
        gameMapper.deleteById(id);
    }

    /** 按 ID 查询游戏，不存在时抛出 GAME_NOT_FOUND */
    public Game requireGame(Long id) {
        Game game = gameMapper.selectById(id);
        if (game == null) {
            throw new BusinessException(ErrorCode.GAME_NOT_FOUND);
        }
        return game;
    }

    /** 名称唯一性校验：排除 excludeId（更新时传自身 ID） */
    private void checkNameUnique(String name, Long excludeId) {
        Long count = gameMapper.selectCount(new LambdaQueryWrapper<Game>()
                .eq(Game::getGameName, name)
                .ne(excludeId != null, Game::getId, excludeId));
        if (count > 0) {
            throw new BusinessException(ErrorCode.GAME_NAME_EXISTS);
        }
    }

    /** 空值转空串，与 DDL 的 DEFAULT '' 保持一致 */
    private String defaultOrEmpty(String value) {
        return value != null ? value : "";
    }
}
