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
import com.gameplay.companion.enums.CompanionServiceStatus;
import com.gameplay.companion.mapper.CompanionAvailabilityMapper;
import com.gameplay.companion.mapper.CompanionProfileMapper;
import com.gameplay.order.domain.OrderTimeSlot;
import com.gameplay.order.mapper.OrderTimeSlotMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** 提供陪玩师游客可见的未来档期摘要。 */
@Service
@RequiredArgsConstructor
public class PublicCompanionAvailabilityService {

    private final CompanionAvailabilityMapper availabilityMapper;
    private final CompanionProfileMapper profileMapper;
    private final UserMapper userMapper;
    private final OrderTimeSlotMapper orderTimeSlotMapper;

    public List<PublicAvailabilityView> listAvailable(Long companionUserId, int days) {
        CompanionProfile profile = profileMapper.selectOne(new LambdaQueryWrapper<CompanionProfile>()
                .eq(CompanionProfile::getUserId, companionUserId));
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getId, companionUserId)
                .eq(User::getAccountStatus, "ENABLED"));
        if (profile == null || user == null
                || !"APPROVED".equals(profile.getCertificationStatus())
                || CompanionServiceStatus.SUSPENDED.name().equals(profile.getServiceStatus())) {
            throw new BusinessException(ErrorCode.COMPANION_PROFILE_NOT_FOUND);
        }
        if (!"AVAILABLE".equals(profile.getServiceStatus())) {
            return List.of();
        }

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime until = now.plusDays(days);
        List<CompanionAvailability> slots = availabilityMapper.selectList(
                new LambdaQueryWrapper<CompanionAvailability>()
                        .eq(CompanionAvailability::getCompanionUserId, companionUserId)
                        .eq(CompanionAvailability::getAvailabilityStatus, AvailabilityStatus.AVAILABLE.name())
                        .gt(CompanionAvailability::getEndAt, now)
                        .lt(CompanionAvailability::getStartAt, until)
                        .orderByAsc(CompanionAvailability::getStartAt));
        if (slots.isEmpty()) {
            return List.of();
        }

        LocalDateTime maxEnd = slots.stream()
                .map(CompanionAvailability::getEndAt)
                .max(LocalDateTime::compareTo)
                .orElse(until);
        List<OrderTimeSlot> booked = orderTimeSlotMapper.selectActiveSlots(
                companionUserId, now, maxEnd.isAfter(until) ? maxEnd : until, now);
        List<LocalDateTime[]> blockers = booked.stream()
                .map(s -> new LocalDateTime[]{s.getStartAt(), s.getEndAt()})
                .toList();

        List<PublicAvailabilityView> views = new ArrayList<>();
        for (CompanionAvailability slot : slots) {
            LocalDateTime clampedStart = slot.getStartAt().isBefore(now) ? now : slot.getStartAt();
            LocalDateTime end = slot.getEndAt();
            if (!clampedStart.isBefore(end)) {
                continue;
            }
            for (LocalDateTime[] free : AvailabilityWindowGenerator.subtractIntervals(clampedStart, end, blockers)) {
                views.add(PublicAvailabilityView.builder()
                        .startAt(free[0])
                        .endAt(free[1])
                        .build());
            }
        }
        views.sort(Comparator.comparing(PublicAvailabilityView::getStartAt));
        return views;
    }
}
