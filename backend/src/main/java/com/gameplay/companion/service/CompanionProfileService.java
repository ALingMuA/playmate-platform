package com.gameplay.companion.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gameplay.auth.entity.User;
import com.gameplay.auth.mapper.UserMapper;
import com.gameplay.common.exception.BusinessException;
import com.gameplay.common.exception.ErrorCode;
import com.gameplay.companion.domain.CompanionProfile;
import com.gameplay.companion.dto.BookableServiceAggregateRow;
import com.gameplay.companion.dto.GameCapabilityDto;
import com.gameplay.companion.dto.ProfileUpdateRequest;
import com.gameplay.companion.dto.ProfileView;
import com.gameplay.companion.dto.PublicCompanionCardRow;
import com.gameplay.companion.dto.PublicCompanionCardView;
import com.gameplay.companion.dto.PublicCompanionProfileView;
import com.gameplay.companion.enums.CompanionServiceStatus;
import com.gameplay.companion.mapper.CompanionProfileMapper;
import com.gameplay.companion.mapper.CompanionServiceMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 陪玩师主页与接单状态服务（FR-P05/P06）。
 */
@Service
@RequiredArgsConstructor
public class CompanionProfileService {

    private final CompanionProfileMapper profileMapper;
    private final CompanionServiceMapper serviceMapper;
    private final UserMapper userMapper;
    private final ObjectMapper objectMapper;

    /** 我的陪玩主页 */
    public ProfileView getMyProfile(Long userId) {
        CompanionProfile profile = requireProfile(userId);
        return toView(profile);
    }

    /** 查询游客可见的陪玩师主页白名单资料。 */
    public PublicCompanionProfileView getPublicProfile(Long userId) {
        CompanionProfile profile = getByUserId(userId);
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getId, userId)
                .eq(User::getAccountStatus, "ENABLED"));
        if (profile == null || user == null
                || !"APPROVED".equals(profile.getCertificationStatus())) {
            throw new BusinessException(ErrorCode.COMPANION_PROFILE_NOT_FOUND);
        }

        return PublicCompanionProfileView.builder()
                .userId(profile.getUserId())
                .displayName(profile.getDisplayName())
                .avatarUrl(user.getAvatarUrl())
                .profileIntro(profile.getProfileIntro())
                .serviceStatus(profile.getServiceStatus())
                .startingPriceCents(serviceMapper.selectStartingPriceCents(userId))
                .ratingAvg(profile.getRatingAvg())
                .ratingCount(profile.getRatingCount())
                .completedOrderCount(profile.getCompletedOrderCount())
                .capabilities(fromJson(profile.getCapabilityJson(),
                        new TypeReference<List<GameCapabilityDto>>() {}))
                .certificationStatus(profile.getCertificationStatus())
                .build();
    }

    /**
     * 游客可浏览的陪玩师公开列表（FR-U02：按陪玩师维度浏览）。
     *
     * <p>两步组装：先分页查陪玩师静态资料（含按游戏的 EXISTS 过滤），
     * 再对当页用户批量聚合在架服务的数量、起价与游戏名，避免单条大 SQL。
     * 聚合口径随 gameId 收窄，保证"筛选某游戏时卡片展示该游戏下的服务摘要"。</p>
     */
    public Page<PublicCompanionCardView> listPublicCards(Long gameId, long page, long size) {
        Page<PublicCompanionCardRow> p = profileMapper.selectPublicCards(new Page<>(page, size), gameId);
        List<PublicCompanionCardView> cards = new ArrayList<>(p.getRecords().size());
        if (!p.getRecords().isEmpty()) {
            Map<Long, BookableServiceAggregateRow> aggByUser = serviceMapper
                    .aggregateBookableByUserIds(
                            p.getRecords().stream().map(PublicCompanionCardRow::getUserId).toList(),
                            gameId)
                    .stream()
                    .collect(Collectors.toMap(BookableServiceAggregateRow::getCompanionUserId,
                            Function.identity()));
            for (PublicCompanionCardRow row : p.getRecords()) {
                BookableServiceAggregateRow agg = aggByUser.get(row.getUserId());
                cards.add(PublicCompanionCardView.builder()
                        .userId(row.getUserId())
                        .displayName(row.getDisplayName())
                        .avatarUrl(row.getAvatarUrl())
                        .serviceStatus(row.getServiceStatus())
                        .serviceCount(agg == null ? 0 : agg.getServiceCount().intValue())
                        .startingPriceCents(agg == null ? null : agg.getStartingPriceCents())
                        .ratingAvg(row.getRatingAvg())
                        .ratingCount(row.getRatingCount())
                        .completedOrderCount(row.getCompletedOrderCount())
                        .gameNames(splitGameNames(agg))
                        .build());
            }
        }
        Page<PublicCompanionCardView> result = new Page<>(p.getCurrent(), p.getSize(), p.getTotal());
        result.setRecords(cards);
        return result;
    }

    /** GROUP_CONCAT 产物拆分为游戏名列表；聚合行缺失或为空时返回空列表。 */
    private List<String> splitGameNames(BookableServiceAggregateRow agg) {
        if (agg == null || agg.getGameNames() == null || agg.getGameNames().isBlank()) {
            return List.of();
        }
        return List.of(agg.getGameNames().split(","));
    }

    /**
     * 更新主页（FR-P05）：展示名与简介。
     *
     * <p>游戏能力认证已改为平台审核制：请求中的 capabilities 字段被忽略，
     * 能力变更须提交能力变更申请，审核通过后由审核流程覆盖。</p>
     */
    @Transactional
    public ProfileView updateProfile(Long userId, ProfileUpdateRequest req) {
        CompanionProfile profile = profileMapper.selectByUserIdForUpdate(userId);
        if (profile == null) {
            throw new BusinessException(ErrorCode.COMPANION_NOT_APPROVED);
        }
        String intro = req.getProfileIntro() == null ? "" : req.getProfileIntro();
        profileMapper.update(null, new LambdaUpdateWrapper<CompanionProfile>()
                .eq(CompanionProfile::getUserId, userId)
                .set(CompanionProfile::getDisplayName, req.getDisplayName())
                .set(CompanionProfile::getProfileIntro, intro));
        return toView(requireProfile(userId));
    }

    /** 设置接单状态（FR-P06）：AVAILABLE/BUSY/RESTING；SUSPENDED 仅管理员可设置 */
    @Transactional
    public ProfileView updateServiceStatus(Long userId, String status) {
        CompanionProfile profile = profileMapper.selectByUserIdForUpdate(userId);
        if (profile == null) {
            throw new BusinessException(ErrorCode.COMPANION_NOT_APPROVED);
        }
        if (CompanionServiceStatus.SUSPENDED.name().equals(profile.getServiceStatus())
                || CompanionServiceStatus.SUSPENDED.name().equals(profile.getCertificationStatus())) {
            throw new BusinessException(ErrorCode.COMPANION_PROFILE_SUSPENDED);
        }
        if (CompanionServiceStatus.SUSPENDED.name().equals(status)) {
            throw new BusinessException(ErrorCode.PERMISSION_DENIED, "暂停接单资格仅管理员可操作");
        }
        if (status == null || List.of("AVAILABLE", "BUSY", "RESTING").stream().noneMatch(status::equals)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "接单状态不合法");
        }
        profileMapper.update(null, new LambdaUpdateWrapper<CompanionProfile>()
                .eq(CompanionProfile::getUserId, userId)
                .set(CompanionProfile::getServiceStatus, status));
        return toView(requireProfile(userId));
    }

    /** 校验陪玩师资料存在且未被暂停（供服务/订单模块调用） */
    public CompanionProfile requireActiveProfile(Long userId) {
        CompanionProfile profile = requireProfile(userId);
        if (!"APPROVED".equals(profile.getCertificationStatus())
                || CompanionServiceStatus.SUSPENDED.name().equals(profile.getServiceStatus())) {
            throw new BusinessException(ErrorCode.COMPANION_PROFILE_SUSPENDED);
        }
        return profile;
    }

    /** 按用户ID查询资料（公开，用户端展示） */
    public CompanionProfile getByUserId(Long userId) {
        return profileMapper.selectOne(new LambdaQueryWrapper<CompanionProfile>()
                .eq(CompanionProfile::getUserId, userId));
    }

    public CompanionProfile requireProfile(Long userId) {
        CompanionProfile profile = getByUserId(userId);
        if (profile == null) {
            throw new BusinessException(ErrorCode.COMPANION_NOT_APPROVED);
        }
        return profile;
    }

    public ProfileView toView(CompanionProfile p) {
        return ProfileView.builder()
                .id(p.getId())
                .userId(p.getUserId())
                .displayName(p.getDisplayName())
                .profileIntro(p.getProfileIntro())
                .capabilities(fromJson(p.getCapabilityJson(), new TypeReference<List<GameCapabilityDto>>() {}))
                .ratingAvg(p.getRatingAvg())
                .ratingCount(p.getRatingCount())
                .completedOrderCount(p.getCompletedOrderCount())
                .serviceStatus(p.getServiceStatus())
                .certificationStatus(p.getCertificationStatus())
                .updatedAt(p.getUpdatedAt())
                .build();
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "数据序列化失败");
        }
    }

    private <T> T fromJson(String json, TypeReference<T> type) {
        if (json == null || json.isBlank()) {
            return objectMapper.convertValue(new ArrayList<>(), type);
        }
        try {
            return objectMapper.readValue(json, type);
        } catch (JsonProcessingException e) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "数据解析失败");
        }
    }
}
