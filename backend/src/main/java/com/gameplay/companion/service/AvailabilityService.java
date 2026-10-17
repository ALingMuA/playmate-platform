package com.gameplay.companion.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.gameplay.common.exception.BusinessException;
import com.gameplay.common.exception.ErrorCode;
import com.gameplay.companion.domain.CompanionAvailability;
import com.gameplay.companion.dto.AvailabilityBatchCreateRequest;
import com.gameplay.companion.dto.AvailabilityBatchCreateView;
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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 陪玩师档期管理服务（FR-P10/P11）。
 *
 * <p>单条新增可约/不可约时段时校验时间合法性、与本陪玩师既有档期及
 * 有效订单占用的冲突（详细设计 7.1 档期并发锁）。</p>
 *
 * <p>批量/周期生成（FR-P10 周期设置）：在日期范围内逐日展开同一时段（或全天），
 * 与既有档期或订单占用重叠的日期自动跳过并记录原因，不中断整体生成；
 * 全天档期落库为 [当天 00:00, 次日 00:00)，相邻日期相接可无缝拼接覆盖；
 * 结束时间早于开始时间视为跨天时段（如 20:00-02:00）。</p>
 */
@Service
@RequiredArgsConstructor
public class AvailabilityService {

    /** 周期模板滚动续期的生成窗口天数：模板自动补齐到未来 28 天 */
    public static final int GENERATE_WINDOW_DAYS = 28;

    /** 单次批量/模板生成范围的防呆上限天数 */
    private static final int MAX_GENERATE_DAYS = 366;

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
        return insert(userId, req, AvailabilityStatus.AVAILABLE, 0L);
    }

    /** 设置临时不可约时段（FR-P10） */
    @Transactional
    public AvailabilityView createUnavailable(Long userId, AvailabilityCreateRequest req) {
        validateTime(req);
        checkConflicts(userId, req.getStartAt(), req.getEndAt());
        return insert(userId, req, AvailabilityStatus.UNAVAILABLE, 0L);
    }

    /**
     * 一次性批量创建可约档期（FR-P10 日期范围设置）：[startDate, endDate] 内每日同一时段或全天。
     * 与既有档期/订单占用冲突的日期跳过，返回成功与跳过明细。
     */
    @Transactional
    public AvailabilityBatchCreateView createBatch(Long userId, AvailabilityBatchCreateRequest req) {
        LocalDate from = req.getStartDate();
        LocalDate to = req.getEndDate() == null ? from : req.getEndDate();
        validateDailySlot(req.getAllDay(), req.getStartTime(), req.getEndTime(), from, to);
        GenerationResult result = generateRange(userId, from, to, req.getAllDay(),
                req.getStartTime(), req.getEndTime(), null, "MANUAL", 0L, req.getRemark());
        return AvailabilityBatchCreateView.builder()
                .createdCount(result.created)
                .skippedCount(result.skipped.size())
                .skippedDetails(result.skipped)
                .build();
    }

    /**
     * 生成引擎核心：在 [fromDate, toDate] 内逐日生成可约档期（FR-P10 周期/范围设置）。
     *
     * <p>daysOfWeek 指定只生成某些周几（1=周一..7=周日，null=全部）；
     * 与既有档期或有效订单占用重叠的日期跳过并记录原因，不中断整体生成。
     * sourceType/templateId 标记生成来源，供模板更新/停用/删除时精确清理。</p>
     */
    @Transactional
    public GenerationResult generateRange(Long userId, LocalDate fromDate, LocalDate toDate,
                                          boolean allDay, LocalTime startTime, LocalTime endTime,
                                          Set<Integer> daysOfWeek, String sourceType,
                                          Long templateId, String remark) {
        if (toDate.isBefore(fromDate)) {
            throw new BusinessException(ErrorCode.AVAILABILITY_TIME_INVALID, "结束日期不能早于开始日期");
        }
        if (ChronoUnit.DAYS.between(fromDate, toDate) + 1 > MAX_GENERATE_DAYS) {
            throw new BusinessException(ErrorCode.AVAILABILITY_TIME_INVALID,
                    "日期范围一次最多生成 " + MAX_GENERATE_DAYS + " 天");
        }
        GenerationResult result = new GenerationResult();
        for (LocalDate date = fromDate; !date.isAfter(toDate); date = date.plusDays(1)) {
            if (daysOfWeek != null && !daysOfWeek.contains(date.getDayOfWeek().getValue())) {
                continue;
            }
            LocalDateTime start;
            LocalDateTime end;
            if (allDay) {
                // 全天 = [当天 00:00, 次日 00:00)，相邻日期相接可无缝拼接
                start = date.atStartOfDay();
                end = date.plusDays(1).atStartOfDay();
            } else {
                start = date.atTime(startTime);
                end = date.atTime(endTime);
                if (!end.isAfter(start)) {
                    // 结束时间不晚于开始 → 跨天时段（如 20:00-02:00）
                    end = date.plusDays(1).atTime(endTime);
                }
            }
            if (hasAvailabilityOverlap(userId, start, end)) {
                result.skipped.add(date + "：与已有档期重叠");
                continue;
            }
            if (!orderTimeSlotMapper.selectConflictSlotsForUpdate(userId, start, end).isEmpty()) {
                result.skipped.add(date + "：该时段已被有效订单占用");
                continue;
            }
            CompanionAvailability availability = new CompanionAvailability();
            availability.setCompanionUserId(userId);
            availability.setStartAt(start);
            availability.setEndAt(end);
            availability.setAvailabilityStatus(AvailabilityStatus.AVAILABLE.name());
            availability.setSourceType(sourceType);
            availability.setTemplateId(templateId);
            availability.setRemark(remark == null ? "" : remark);
            availabilityMapper.insert(availability);
            result.created++;
        }
        return result;
    }

    /**
     * 清理模板未来（尚未开始且可删除）的生成实例，已被有效订单占用的保留。
     * 模板修改/停用/删除时调用：先清理再按新规则重新生成或保持清空（FR-P10 周期设置）。
     */
    @Transactional
    public int deleteFutureInstancesOfTemplate(Long userId, Long templateId) {
        List<CompanionAvailability> future = availabilityMapper.selectList(
                new LambdaQueryWrapper<CompanionAvailability>()
                        .eq(CompanionAvailability::getCompanionUserId, userId)
                        .eq(CompanionAvailability::getTemplateId, templateId)
                        .ge(CompanionAvailability::getStartAt, LocalDateTime.now()));
        int removed = 0;
        for (CompanionAvailability availability : future) {
            if (!orderTimeSlotMapper.selectConflictSlotsForUpdate(
                    userId, availability.getStartAt(), availability.getEndAt()).isEmpty()) {
                continue;
            }
            availabilityMapper.deleteById(availability.getId());
            removed++;
        }
        return removed;
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

    /** 校验批量/周期生成的时段与日期范围参数 */
    private void validateDailySlot(boolean allDay, LocalTime startTime, LocalTime endTime,
                                   LocalDate from, LocalDate to) {
        if (!allDay) {
            if (startTime == null || endTime == null) {
                throw new BusinessException(ErrorCode.AVAILABILITY_TIME_INVALID, "请选择每日的开始与结束时间");
            }
            if (startTime.equals(endTime)) {
                throw new BusinessException(ErrorCode.AVAILABILITY_TIME_INVALID, "结束时间不能等于开始时间");
            }
        }
        if (to.isBefore(from)) {
            throw new BusinessException(ErrorCode.AVAILABILITY_TIME_INVALID, "结束日期不能早于开始日期");
        }
    }

    /** 与本陪玩师既有档期（同状态重叠）及有效订单占用冲突检查 */
    private void checkConflicts(Long userId, LocalDateTime startAt, LocalDateTime endAt) {
        if (hasAvailabilityOverlap(userId, startAt, endAt)) {
            throw new BusinessException(ErrorCode.SLOT_CONFLICT, "与已有档期时间重叠");
        }
        List<OrderTimeSlot> orderConflicts = orderTimeSlotMapper.selectConflictSlotsForUpdate(
                userId, startAt, endAt);
        if (!orderConflicts.isEmpty()) {
            throw new BusinessException(ErrorCode.SLOT_CONFLICT, "该时段已被有效订单占用");
        }
    }

    /** 与本陪玩师任何既有档期是否存在时间重叠 */
    private boolean hasAvailabilityOverlap(Long userId, LocalDateTime startAt, LocalDateTime endAt) {
        return !availabilityMapper.selectList(
                new LambdaQueryWrapper<CompanionAvailability>()
                        .eq(CompanionAvailability::getCompanionUserId, userId)
                        .lt(CompanionAvailability::getStartAt, endAt)
                        .gt(CompanionAvailability::getEndAt, startAt)).isEmpty();
    }

    private AvailabilityView insert(Long userId, AvailabilityCreateRequest req,
                                    AvailabilityStatus status, Long templateId) {
        CompanionAvailability availability = new CompanionAvailability();
        availability.setCompanionUserId(userId);
        availability.setStartAt(req.getStartAt());
        availability.setEndAt(req.getEndAt());
        availability.setAvailabilityStatus(status.name());
        availability.setSourceType("MANUAL");
        availability.setTemplateId(templateId);
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
                .templateId(a.getTemplateId())
                .remark(a.getRemark())
                .build();
    }

    /** 生成结果：成功条数与跳过明细 */
    public static class GenerationResult {
        private int created;
        private final List<String> skipped = new ArrayList<>();

        public int getCreated() {
            return created;
        }

        public List<String> getSkipped() {
            return skipped;
        }
    }
}
