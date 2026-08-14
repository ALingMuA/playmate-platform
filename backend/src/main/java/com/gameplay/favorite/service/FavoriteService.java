package com.gameplay.favorite.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.gameplay.common.exception.BusinessException;
import com.gameplay.common.exception.ErrorCode;
import com.gameplay.companion.domain.CompanionProfile;
import com.gameplay.companion.mapper.CompanionProfileMapper;
import com.gameplay.favorite.domain.Favorite;
import com.gameplay.favorite.dto.FavoriteView;
import com.gameplay.favorite.mapper.FavoriteMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 收藏领域服务（FR-U06：收藏/取消/列表）。
 */
@Service
@RequiredArgsConstructor
public class FavoriteService {

    private final FavoriteMapper favoriteMapper;
    private final CompanionProfileMapper companionProfileMapper;

    /** 收藏陪玩师（FR-U06）：仅审核通过的陪玩师可收藏 */
    @Transactional
    public void add(Long userId, Long companionUserId) {
        CompanionProfile profile = companionProfileMapper.selectOne(
                new LambdaQueryWrapper<CompanionProfile>()
                        .eq(CompanionProfile::getUserId, companionUserId)
                        .last("LIMIT 1"));
        if (profile == null || !"APPROVED".equals(profile.getCertificationStatus())) {
            throw new BusinessException(ErrorCode.FAVORITE_COMPANION_INVALID);
        }
        long exists = favoriteMapper.selectCount(new LambdaQueryWrapper<Favorite>()
                .eq(Favorite::getUserId, userId)
                .eq(Favorite::getCompanionUserId, companionUserId));
        if (exists > 0) {
            throw new BusinessException(ErrorCode.FAVORITE_ALREADY_EXISTS);
        }
        Favorite favorite = new Favorite();
        favorite.setUserId(userId);
        favorite.setCompanionUserId(companionUserId);
        try {
            favoriteMapper.insert(favorite);
        } catch (DuplicateKeyException e) {
            throw new BusinessException(ErrorCode.FAVORITE_ALREADY_EXISTS);
        }
    }

    /** 取消收藏（FR-U06） */
    @Transactional
    public void remove(Long userId, Long companionUserId) {
        Favorite favorite = favoriteMapper.selectOne(new LambdaQueryWrapper<Favorite>()
                .eq(Favorite::getUserId, userId)
                .eq(Favorite::getCompanionUserId, companionUserId));
        if (favorite == null) {
            throw new BusinessException(ErrorCode.FAVORITE_NOT_FOUND);
        }
        favoriteMapper.deleteById(favorite.getId());
    }

    /** 我的收藏分页（FR-U06） */
    public Page<FavoriteView> page(Long userId, long page, long size) {
        Page<Favorite> p = favoriteMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<Favorite>()
                        .eq(Favorite::getUserId, userId)
                        .orderByDesc(Favorite::getId));
        List<FavoriteView> views = p.getRecords().stream().map(f -> {
            CompanionProfile profile = companionProfileMapper.selectOne(
                    new LambdaQueryWrapper<CompanionProfile>()
                            .eq(CompanionProfile::getUserId, f.getCompanionUserId())
                            .last("LIMIT 1"));
            return FavoriteView.builder()
                    .companionUserId(f.getCompanionUserId())
                    .displayName(profile == null ? "" : profile.getDisplayName())
                    .profileIntro(profile == null ? "" : profile.getProfileIntro())
                    .ratingAvg(profile == null ? null : profile.getRatingAvg())
                    .ratingCount(profile == null ? null : profile.getRatingCount())
                    .serviceStatus(profile == null ? null : profile.getServiceStatus())
                    .createdAt(f.getCreatedAt())
                    .build();
        }).toList();
        Page<FavoriteView> vp = new Page<>(p.getCurrent(), p.getSize(), p.getTotal());
        vp.setRecords(views);
        return vp;
    }
}
