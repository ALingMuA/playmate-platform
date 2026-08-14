package com.gameplay.catalog.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.gameplay.catalog.domain.Tag;
import com.gameplay.catalog.dto.TagRequest;
import com.gameplay.catalog.dto.TagView;
import com.gameplay.catalog.mapper.TagMapper;
import com.gameplay.common.exception.BusinessException;
import com.gameplay.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 服务标签服务（FR-M12 标签管理）。
 *
 * <p>标签用于描述陪玩师能力（位置、英雄、风格），gameId=0 为通用标签，
 * 其余标签归属于具体游戏；前台按"所选游戏 + 通用"返回启用标签。</p>
 */
@Service
@RequiredArgsConstructor
public class TagService {

    private final TagMapper tagMapper;
    private final GameService gameService;

    // ==================== 公开查询（前台，仅启用） ====================

    /**
     * 标签列表（FR-U05 陪玩师详情、FR-P02 能力认证选标签用）。
     *
     * <p>gameId 为空时返回全部启用标签；指定 gameId 时返回该游戏标签与通用标签（gameId=0），
     * 按分类、排序号、ID 升序排列。</p>
     */
    public List<TagView> listEnabledTags(Long gameId) {
        List<Tag> tags = tagMapper.selectList(new LambdaQueryWrapper<Tag>()
                .eq(Tag::getEnabled, 1)
                .and(gameId != null, w -> w.eq(Tag::getGameId, gameId).or().eq(Tag::getGameId, 0L))
                .orderByAsc(Tag::getTagCategory)
                .orderByAsc(Tag::getSortNo)
                .orderByAsc(Tag::getId));
        return tags.stream().map(TagView::from).toList();
    }

    // ==================== 管理操作（FR-M12） ====================

    /** 管理列表：可按名称关键词、分类、所属游戏、启用状态过滤 */
    public List<Tag> listTags(String keyword, String category, Long gameId, Integer enabled) {
        return tagMapper.selectList(new LambdaQueryWrapper<Tag>()
                .like(StringUtils.hasText(keyword), Tag::getTagName, keyword)
                .eq(StringUtils.hasText(category), Tag::getTagCategory, category)
                .eq(gameId != null, Tag::getGameId, gameId)
                .eq(enabled != null, Tag::getEnabled, enabled)
                .orderByAsc(Tag::getTagCategory)
                .orderByAsc(Tag::getSortNo)
                .orderByAsc(Tag::getId));
    }

    /** 新增标签（FR-M12）：同一游戏同一分类下名称唯一；关联游戏需存在 */
    @Transactional
    public Tag createTag(TagRequest request) {
        String name = request.getTagName().trim();
        String category = request.getTagCategory().toUpperCase();
        long gameId = request.getGameId() != null ? request.getGameId() : 0L;
        if (gameId > 0) {
            // 非通用标签必须归属一个真实存在的游戏
            gameService.requireGame(gameId);
        }
        checkTagUnique(name, category, gameId, null);
        Tag tag = new Tag();
        tag.setTagName(name);
        tag.setTagCategory(category);
        tag.setGameId(gameId);
        tag.setSortNo(request.getSortNo() != null ? request.getSortNo() : 0);
        tag.setEnabled(request.getEnabled() != null ? request.getEnabled() : 1);
        tagMapper.insert(tag);
        return tag;
    }

    /** 编辑标签（FR-M12） */
    @Transactional
    public Tag updateTag(Long id, TagRequest request) {
        Tag tag = requireTag(id);
        String name = request.getTagName().trim();
        String category = request.getTagCategory().toUpperCase();
        long gameId = request.getGameId() != null ? request.getGameId() : 0L;
        if (gameId > 0) {
            gameService.requireGame(gameId);
        }
        checkTagUnique(name, category, gameId, id);
        tag.setTagName(name);
        tag.setTagCategory(category);
        tag.setGameId(gameId);
        tag.setSortNo(request.getSortNo() != null ? request.getSortNo() : 0);
        tag.setEnabled(request.getEnabled() != null ? request.getEnabled() : 1);
        tagMapper.updateById(tag);
        return tag;
    }

    /** 删除标签（FR-M12）。后续陪玩师资料接入后需校验标签引用 */
    @Transactional
    public void deleteTag(Long id) {
        requireTag(id);
        tagMapper.deleteById(id);
    }

    /** 按 ID 查询标签，不存在时抛出 TAG_NOT_FOUND */
    public Tag requireTag(Long id) {
        Tag tag = tagMapper.selectById(id);
        if (tag == null) {
            throw new BusinessException(ErrorCode.TAG_NOT_FOUND);
        }
        return tag;
    }

    /** 同游戏同分类下名称唯一性校验（排除 excludeId） */
    private void checkTagUnique(String name, String category, Long gameId, Long excludeId) {
        Long count = tagMapper.selectCount(new LambdaQueryWrapper<Tag>()
                .eq(Tag::getTagName, name)
                .eq(Tag::getTagCategory, category)
                .eq(Tag::getGameId, gameId)
                .ne(excludeId != null, Tag::getId, excludeId));
        if (count > 0) {
            throw new BusinessException(ErrorCode.TAG_EXISTS);
        }
    }
}
