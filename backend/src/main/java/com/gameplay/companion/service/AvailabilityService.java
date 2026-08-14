package com.gameplay.companion.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.gameplay.common.exception.BusinessException;
import com.gameplay.common.exception.ErrorCode;
import com.gameplay.companion.domain.CompanionAvailability;
import com.gameplay.companion.dto.AvailabilityCreateRequest;
import com.gameplay.companion.dto.AvailabilityView;
import com.gameplay.companion.enums.AvailabilityStatus;
import com.gameplay.companion.mapper.CompanionAvailabilityMapper;
import com.gameplay.order.domain.OrderTimeSlot;
import com.gameplay.order.enums.SlotStatus;
import com.gameplay.order.mapper.OrderTimeSlotMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 陪玩师档期管理服务（FR-P10/P11）。
 *
 * <p>新增可约/不可约时段时校验时间合法性、与本陪玩师既有档期及
 * 有效订单占用的冲突（详细设计 7.1 档期并发锁）。</p>
 */
@Service
@RequiredArgsConstructor
public class AvailabilityService {

    private final CompanionAvailabilityMapper availabilityMapper;
    private final OrderTimeSlotMapper orderTimeSlotMapper;

    /** 我的档期列表（可按时间范围过滤） */
    public List<AvailabilityView> listMine(Long userId, LocalDateTime startAt, LocalDateTime endAt) {
        LambdaQueryWrapper<CompanionAvailability> wrapper = new LambdaQueryWrapper<CompanionAvailability>()
                .eq(CompanionAvailability::getCompanionUserId, userId)
                .orderByAsc(CompanionAvailability::getStartAt);
        if (startAt != null) {
            wrapper.ge(CompanionAvailability::getEndAt, startAt);
        }
        if (endAt != null) {
            wrapper.le(CompanionAvailability::getStartAt, endAt);
        }
        return availabilityMapper.selectList(wrapper).stream().map(this::toView).toList();
    }

    /** 新增可约时段（FR-P10） */
    @Transactional
    public AvailabilityView createAvailable(Long userId, AvailabilityCreateRequest req) {
        validateTime(req);
        checkConflicts(userId, req.getStartAt(), req.getEndAt());
        return insert(userId, req, AvailabilityStatus.AVAILABLE);
    }

    /** 设置临时不可约时段（FR-P10） */
    @Transactional
    public AvailabilityView createUnavailable(Long userId, AvailabilityCreateRequest req) {
        validateTime(req);
        checkConflicts(userId, req.getStartAt(), req.getEndAt());
        return insert(userId, req, AvailabilityStatus.UNAVAILABLE);
    }

    /** 删除档期（仅当无有效订单占用该时段） */
    @Transactional
    public void delete(Long userId, Long availabilityId) {
        CompanionAvailability availability = availabilityMapper.selectById(availabilityId);
        if (availability == null) {
            throw new BusinessException(ErrorCode.AVAILABILITY_NOT_FOUND);
        }
        if (!availability.getCompanionUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.PERMISSION_DATA_SCOPE_DENIED);
        }
        List<OrderTimeSlot> conflicts = orderTimeSlotMapper.selectConflictSlotsForUpdate(
                userId, availability.getStartAt(), availability.getEndAt());
        if (!conflicts.isEmpty()) {
            throw new BusinessException(ErrorCode.AVAILABILITY_DELETE_FORBIDDEN);
        }
        availabilityMapper.deleteById(availabilityId);
    }

    private void validateTime(AvailabilityCreateRequest req) {
        if (!req.getStartAt().isBefore(req.getEndAt())) {
            throw new BusinessException(ErrorCode.AVAILABILITY_TIME_INVALID, "开始时间必须早于结束时间");
        }
    }

    /** 与本陪玩师既有档期（同状态重叠）及有效订单占用冲突检查 */
    private void checkConflicts(Long userId, LocalDateTime startAt, LocalDateTime endAt) {
        List<CompanionAvailability> overlap = availabilityMapper.selectList(
                new LambdaQueryWrapper<CompanionAvailability>()
                        .eq(CompanionAvailability::getCompanionUserId, userId)
                        .lt(CompanionAvailability::getStartAt, endAt)
                        .gt(CompanionAvailability::getEndAt, startAt));
        if (!overlap.isEmpty()) {
            throw new BusinessException(ErrorCode.SLOT_CONFLICT, "与已有档期时间重叠");
        }
        List<OrderTimeSlot> orderConflicts = orderTimeSlotMapper.selectConflictSlotsForUpdate(
                userId, startAt, endAt);
        if (!orderConflicts.isEmpty()) {
            throw new BusinessException(ErrorCode.SLOT_CONFLICT, "该时段已被有效订单占用");
        }
    }

    private AvailabilityView insert(Long userId, AvailabilityCreateRequest req,
                                    AvailabilityStatus status) {
        CompanionAvailability availability = new CompanionAvailability();
        availability.setCompanionUserId(userId);
        availability.setStartAt(req.getStartAt());
        availability.setEndAt(req.getEndAt());
        availability.setAvailabilityStatus(status.name());
        availability.setSourceType("MANUAL");
        availability.setRemark(req.getRemark() == null ? "" : req.getRemark());
        availabilityMapper.insert(availability);
        return toView(availability);
    }

    private AvailabilityView toView(CompanionAvailability a) {
        return AvailabilityView.builder()
                .id(a.getId())
                .companionUserId(a.getCompanionUserId())
                .startAt(a.getStartAt())
                .endAt(a.getEndAt())
                .availabilityStatus(a.getAvailabilityStatus())
                .sourceType(a.getSourceType())
                .remark(a.getRemark())
                .build();
    }
}
