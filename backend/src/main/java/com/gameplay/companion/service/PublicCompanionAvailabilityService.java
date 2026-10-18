package com.gameplay.companion.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.gameplay.auth.entity.User;
import com.gameplay.auth.mapper.UserMapper;
import com.gameplay.common.exception.BusinessException;
import com.gameplay.common.exception.ErrorCode;
import com.gameplay.companion.domain.CompanionAvailability;
import com.gameplay.companion.domain.CompanionProfile;
import com.gameplay.companion.dto.PublicAvailabilityView;
import com.gameplay.companion.enums.AvailabilityStatus;
import com.gameplay.companion.mapper.CompanionAvailabilityMapper;
import com.gameplay.companion.mapper.CompanionProfileMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/** 提供陪玩师游客可见的未来档期摘要。 */
@Service
@RequiredArgsConstructor
public class PublicCompanionAvailabilityService {

    private final CompanionAvailabilityMapper availabilityMapper;
    private final CompanionProfileMapper profileMapper;
    private final UserMapper userMapper;

    public List<PublicAvailabilityView> listAvailable(Long companionUserId, int days) {
        CompanionProfile profile = profileMapper.selectOne(new LambdaQueryWrapper<CompanionProfile>()
                .eq(CompanionProfile::getUserId, companionUserId));
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getId, companionUserId)
                .eq(User::getAccountStatus, "ENABLED"));
        if (profile == null || user == null || !"APPROVED".equals(profile.getCertificationStatus())) {
            throw new BusinessException(ErrorCode.COMPANION_PROFILE_NOT_FOUND);
        }
        if (!"AVAILABLE".equals(profile.getServiceStatus())) {
            return List.of();
        }

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime until = now.plusDays(days);
        return availabilityMapper.selectList(new LambdaQueryWrapper<CompanionAvailability>()
                        .eq(CompanionAvailability::getCompanionUserId, companionUserId)
                        .eq(CompanionAvailability::getAvailabilityStatus, AvailabilityStatus.AVAILABLE.name())
                        .ge(CompanionAvailability::getStartAt, now)
                        .le(CompanionAvailability::getStartAt, until)
                        .orderByAsc(CompanionAvailability::getStartAt))
                .stream()
                .map(availability -> PublicAvailabilityView.builder()
                        .startAt(availability.getStartAt())
                        .endAt(availability.getEndAt())
                        .build())
                .toList();
    }
}
