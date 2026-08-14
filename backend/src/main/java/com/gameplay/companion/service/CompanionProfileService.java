package com.gameplay.companion.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gameplay.common.exception.BusinessException;
import com.gameplay.common.exception.ErrorCode;
import com.gameplay.companion.domain.CompanionProfile;
import com.gameplay.companion.dto.GameCapabilityDto;
import com.gameplay.companion.dto.ProfileUpdateRequest;
import com.gameplay.companion.dto.ProfileView;
import com.gameplay.companion.enums.CompanionServiceStatus;
import com.gameplay.companion.mapper.CompanionProfileMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * 陪玩师主页与接单状态服务（FR-P05/P06）。
 */
@Service
@RequiredArgsConstructor
public class CompanionProfileService {

    private final CompanionProfileMapper profileMapper;
    private final ObjectMapper objectMapper;

    /** 我的陪玩主页 */
    public ProfileView getMyProfile(Long userId) {
        CompanionProfile profile = requireProfile(userId);
        return toView(profile);
    }

    /** 更新主页（FR-P05）：展示名、简介、认证能力 */
    @Transactional
    public ProfileView updateProfile(Long userId, ProfileUpdateRequest req) {
        CompanionProfile profile = requireProfile(userId);
        profile.setDisplayName(req.getDisplayName());
        profile.setProfileIntro(req.getProfileIntro() == null ? "" : req.getProfileIntro());
        profile.setCapabilityJson(toJson(req.getCapabilities() == null ? List.of() : req.getCapabilities()));
        profileMapper.updateById(profile);
        return toView(profile);
    }

    /** 设置接单状态（FR-P06）：AVAILABLE/BUSY/RESTING；SUSPENDED 仅管理员可设置 */
    @Transactional
    public ProfileView updateServiceStatus(Long userId, String status) {
        CompanionProfile profile = requireProfile(userId);
        if (CompanionServiceStatus.SUSPENDED.name().equals(status)) {
            throw new BusinessException(ErrorCode.PERMISSION_DENIED, "暂停接单资格仅管理员可操作");
        }
        if (status == null || List.of("AVAILABLE", "BUSY", "RESTING").stream().noneMatch(status::equals)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "接单状态不合法");
        }
        profile.setServiceStatus(status);
        profileMapper.updateById(profile);
        return toView(profile);
    }

    /** 校验陪玩师资料存在且未被暂停（供服务/订单模块调用） */
    public CompanionProfile requireActiveProfile(Long userId) {
        CompanionProfile profile = requireProfile(userId);
        if (CompanionServiceStatus.SUSPENDED.name().equals(profile.getCertificationStatus())) {
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
