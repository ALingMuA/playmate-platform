package com.gameplay.companion.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.gameplay.auth.entity.User;
import com.gameplay.auth.mapper.UserMapper;
import com.gameplay.common.exception.BusinessException;
import com.gameplay.common.exception.ErrorCode;
import com.gameplay.companion.domain.CompanionAvailability;
import com.gameplay.companion.dto.AvailabilityBatchCreateRequest;
import com.gameplay.companion.dto.AvailabilityBatchCreateView;
import com.gameplay.companion.dto.AvailabilityChangeRequest;
import com.gameplay.companion.dto.AvailabilityCreateRequest;
import com.gameplay.companion.dto.AvailabilityView;
import com.gameplay.companion.dto.BookedSlotView;
import com.gameplay.companion.dto.ScheduleOverviewView;
import com.gameplay.companion.enums.AvailabilityStatus;
import com.gameplay.companion.mapper.CompanionAvailabilityMapper;
import com.gameplay.order.domain.OrderTimeSlot;
import com.gameplay.order.domain.PlayOrder;
import com.gameplay.order.mapper.OrderTimeSlotMapper;
import com.gameplay.order.mapper.PlayOrderMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 陪玩师档期管理服务（FR-P10/P11）。
 *
 * <p>全新体验：支持区间挖空（Punch-Out 切分）与连续区间智能缝合（Merge）。
 * 陪玩师标记不可约时自动扣减原可用时段，避免报时间重叠错误；
 * 标记可约时自动消除不可约标记并与相邻时段拼接。</p>
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
    private final PlayOrderMapper playOrderMapper;
    private final UserMapper userMapper;

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

    /**
     * 新增可约时段（FR-P10）。
     * <p>全新体验：自动裁剪/消除重叠的 UNAVAILABLE 休息标记；
     * 并将与本时段重叠或相接的已有 AVAILABLE 记录智能合并缝合，避免碎片化与报错。</p>
     */
    @Transactional
    public AvailabilityView createAvailable(Long userId, AvailabilityCreateRequest req) {
        validateTime(req);
        LocalDateTime startAt = req.getStartAt();
        LocalDateTime endAt = req.getEndAt();

        // 1. 检查有效订单占用
        List<OrderTimeSlot> orderConflicts = orderTimeSlotMapper.selectConflictSlotsForUpdate(
                userId, startAt, endAt);
        if (!orderConflicts.isEmpty()) {
            throw new BusinessException(ErrorCode.SLOT_CONFLICT, "所选时段已被有效订单占用");
        }

        // 2. 检查与已有可约档期的时间重叠（若已处于可约状态，提示已重叠避免重复新增）
        boolean hasAvailableOverlap = !availabilityMapper.selectList(
                new LambdaQueryWrapper<CompanionAvailability>()
                        .eq(CompanionAvailability::getCompanionUserId, userId)
                        .eq(CompanionAvailability::getAvailabilityStatus, AvailabilityStatus.AVAILABLE.name())
                        .lt(CompanionAvailability::getStartAt, endAt)
                        .gt(CompanionAvailability::getEndAt, startAt)).isEmpty();
        if (hasAvailableOverlap) {
            throw new BusinessException(ErrorCode.SLOT_CONFLICT, "与已有档期时间重叠");
        }

        // 3. 如果存在 UNAVAILABLE 记录重叠，说明用户要恢复此段可约 -> 对 UNAVAILABLE 进行挖空扣减
        punchOutUnavailableSlots(userId, startAt, endAt);

        // 4. 插入新的可约时段
        CompanionAvailability a = new CompanionAvailability();
        a.setCompanionUserId(userId);
        a.setStartAt(startAt);
        a.setEndAt(endAt);
        a.setAvailabilityStatus(AvailabilityStatus.AVAILABLE.name());
        a.setSourceType("MANUAL");
        a.setTemplateId(0L);
        a.setRemark(req.getRemark() == null ? "" : req.getRemark());
        availabilityMapper.insert(a);

        return toView(a);
    }

    /**
     * 设置临时不可约时段（FR-P10）。
     * <p>全新体验：支持区间挖空（Punch-Out 切分）。当陪玩师在已有可约时段中标记没空时，
     * 自动将原可用区间截断或切分为前后两段，绝不报错“时间重叠”；若该时段已被有效订单锁定，则进行保护拦截。</p>
     */
    @Transactional
    public AvailabilityView createUnavailable(Long userId, AvailabilityCreateRequest req) {
        validateTime(req);
        LocalDateTime startAt = req.getStartAt();
        LocalDateTime endAt = req.getEndAt();

        // 1. 检查有效订单占用：已被订单锁定的时段不能设为不可约
        List<OrderTimeSlot> orderConflicts = orderTimeSlotMapper.selectConflictSlotsForUpdate(
                userId, startAt, endAt);
        if (!orderConflicts.isEmpty()) {
            throw new BusinessException(ErrorCode.SLOT_CONFLICT, "所选时段已被有效订单锁定，无法直接设为休息");
        }

        // 2. 对重叠的 AVAILABLE 档期进行区间挖空扣减（Punch-Out 切分）
        punchOutAvailableSlots(userId, startAt, endAt);

        // 3. 处理已有的 UNAVAILABLE 记录：与新设置的时段做并集融合，防止碎片化
        List<CompanionAvailability> existingUnavail = availabilityMapper.selectList(
                new LambdaQueryWrapper<CompanionAvailability>()
                        .eq(CompanionAvailability::getCompanionUserId, userId)
                        .eq(CompanionAvailability::getAvailabilityStatus, AvailabilityStatus.UNAVAILABLE.name())
                        .le(CompanionAvailability::getStartAt, endAt)
                        .ge(CompanionAvailability::getEndAt, startAt));

        LocalDateTime finalStart = startAt;
        LocalDateTime finalEnd = endAt;
        for (CompanionAvailability u : existingUnavail) {
            if (u.getStartAt().isBefore(finalStart)) {
                finalStart = u.getStartAt();
            }
            if (u.getEndAt().isAfter(finalEnd)) {
                finalEnd = u.getEndAt();
            }
            availabilityMapper.deleteById(u.getId());
        }

        CompanionAvailability record = new CompanionAvailability();
        record.setCompanionUserId(userId);
        record.setStartAt(finalStart);
        record.setEndAt(finalEnd);
        record.setAvailabilityStatus(AvailabilityStatus.UNAVAILABLE.name());
        record.setSourceType("MANUAL");
        record.setTemplateId(0L);
        record.setRemark(req.getRemark() == null ? "" : req.getRemark());
        availabilityMapper.insert(record);

        return toView(record);
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
     * 周日程批量保存：将前端选中的多个区间在一个事务内依次应用。
     * 任一段遇到订单冲突或时间非法时，整个请求回滚，避免出现半套档期。
     */
    @Transactional
    public void applyChanges(Long userId, AvailabilityChangeRequest req) {
        for (AvailabilityChangeRequest.Item item : req.getChanges()) {
            AvailabilityCreateRequest single = new AvailabilityCreateRequest();
            single.setStartAt(item.getStartAt());
            single.setEndAt(item.getEndAt());
            if (item.getStatus() == AvailabilityStatus.AVAILABLE) {
                createAvailable(userId, single);
            } else {
                createUnavailable(userId, single);
            }
        }
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

    /**
     * 周日程全景视图（聚合可用档期 + 订单锁定区间）。
     */
    public ScheduleOverviewView getScheduleOverview(Long userId, LocalDateTime startAt, LocalDateTime endAt) {
        List<AvailabilityView> availabilities = listMine(userId, startAt, endAt);

        LambdaQueryWrapper<PlayOrder> orderWrapper = new LambdaQueryWrapper<PlayOrder>()
                .eq(PlayOrder::getCompanionUserId, userId)
                .in(PlayOrder::getOrderStatus, "WAITING_ACCEPTANCE", "WAITING_SERVICE", "IN_SERVICE", "WAITING_CONFIRMATION");
        if (startAt != null) {
            orderWrapper.ge(PlayOrder::getAppointmentEndAt, startAt);
        }
        if (endAt != null) {
            orderWrapper.le(PlayOrder::getAppointmentStartAt, endAt);
        }
        orderWrapper.orderByAsc(PlayOrder::getAppointmentStartAt);

        List<PlayOrder> orders = playOrderMapper.selectList(orderWrapper);
        Set<Long> userIds = orders.stream().map(PlayOrder::getUserId).collect(Collectors.toSet());
        Map<Long, String> userNicknameMap = userIds.isEmpty() ? Collections.emptyMap() :
                userMapper.selectBatchIds(userIds).stream()
                        .collect(Collectors.toMap(User::getId, u -> u.getNickname() != null ? u.getNickname() : u.getUsername(), (k1, k2) -> k1));

        List<BookedSlotView> bookedSlots = orders.stream().map(o -> BookedSlotView.builder()
                .orderId(o.getId())
                .orderNo(o.getOrderNo())
                .companionUserId(o.getCompanionUserId())
                .customerUserId(o.getUserId())
                .customerNickname(userNicknameMap.getOrDefault(o.getUserId(), "买家"))
                .serviceTitle(o.getServiceTitleSnapshot())
                .gameServer(o.getGameServer())
                .gameNickname(o.getGameNickname())
                .startAt(o.getAppointmentStartAt())
                .endAt(o.getAppointmentEndAt())
                .orderStatus(o.getOrderStatus())
                .build()
        ).toList();

        return ScheduleOverviewView.builder()
                .availabilities(availabilities)
                .bookedSlots(bookedSlots)
                .build();
    }

    /**
     * 一键切换全天状态（全天休息 或 全天可约）。
     */
    @Transactional
    public void toggleDayOff(Long userId, LocalDate date, boolean isOff, String remark) {
        LocalDateTime dayStart = date.atStartOfDay();
        LocalDateTime dayEnd = date.plusDays(1).atStartOfDay();
        AvailabilityCreateRequest req = new AvailabilityCreateRequest();
        req.setStartAt(dayStart);
        req.setEndAt(dayEnd);
        req.setRemark(remark != null && !remark.isBlank() ? remark : (isOff ? "全天休息" : "全天可约"));
        if (isOff) {
            createUnavailable(userId, req);
        } else {
            createAvailable(userId, req);
        }
    }

    /**
     * 区间挖空扣减算法：对重叠的 AVAILABLE 记录进行裁剪或切分。
     */
    private void punchOutAvailableSlots(Long userId, LocalDateTime punchStart, LocalDateTime punchEnd) {
        List<CompanionAvailability> overlaps = availabilityMapper.selectList(
                new LambdaQueryWrapper<CompanionAvailability>()
                        .eq(CompanionAvailability::getCompanionUserId, userId)
                        .eq(CompanionAvailability::getAvailabilityStatus, AvailabilityStatus.AVAILABLE.name())
                        .lt(CompanionAvailability::getStartAt, punchEnd)
                        .gt(CompanionAvailability::getEndAt, punchStart));

        for (CompanionAvailability item : overlaps) {
            boolean leftOver = item.getStartAt().isBefore(punchStart);
            boolean rightOver = item.getEndAt().isAfter(punchEnd);

            if (!leftOver && !rightOver) {
                // 原区间完全落在挖空时段内部 -> 直接删除
                availabilityMapper.deleteById(item.getId());
            } else if (leftOver && rightOver) {
                // 原区间跨越挖空时段 -> 中间切穿，前半段就地更新，后半段新增
                LocalDateTime oldEnd = item.getEndAt();
                item.setEndAt(punchStart);
                availabilityMapper.updateById(item);

                CompanionAvailability afterPiece = new CompanionAvailability();
                afterPiece.setCompanionUserId(userId);
                afterPiece.setStartAt(punchEnd);
                afterPiece.setEndAt(oldEnd);
                afterPiece.setAvailabilityStatus(AvailabilityStatus.AVAILABLE.name());
                afterPiece.setSourceType(item.getSourceType());
                afterPiece.setTemplateId(item.getTemplateId());
                afterPiece.setRemark(item.getRemark());
                availabilityMapper.insert(afterPiece);
            } else if (leftOver) {
                // 右侧被切掉，保留前半段 [start, punchStart]
                item.setEndAt(punchStart);
                availabilityMapper.updateById(item);
            } else {
                // 左侧被切掉，保留后半段 [punchEnd, end]
                item.setStartAt(punchEnd);
                availabilityMapper.updateById(item);
            }
        }
    }

    /**
     * 区间挖空扣减算法：对重叠的 UNAVAILABLE 记录进行裁剪或消除。
     */
    private void punchOutUnavailableSlots(Long userId, LocalDateTime punchStart, LocalDateTime punchEnd) {
        List<CompanionAvailability> overlaps = availabilityMapper.selectList(
                new LambdaQueryWrapper<CompanionAvailability>()
                        .eq(CompanionAvailability::getCompanionUserId, userId)
                        .eq(CompanionAvailability::getAvailabilityStatus, AvailabilityStatus.UNAVAILABLE.name())
                        .lt(CompanionAvailability::getStartAt, punchEnd)
                        .gt(CompanionAvailability::getEndAt, punchStart));

        for (CompanionAvailability item : overlaps) {
            boolean leftOver = item.getStartAt().isBefore(punchStart);
            boolean rightOver = item.getEndAt().isAfter(punchEnd);

            if (!leftOver && !rightOver) {
                availabilityMapper.deleteById(item.getId());
            } else if (leftOver && rightOver) {
                LocalDateTime oldEnd = item.getEndAt();
                item.setEndAt(punchStart);
                availabilityMapper.updateById(item);

                CompanionAvailability afterPiece = new CompanionAvailability();
                afterPiece.setCompanionUserId(userId);
                afterPiece.setStartAt(punchEnd);
                afterPiece.setEndAt(oldEnd);
                afterPiece.setAvailabilityStatus(AvailabilityStatus.UNAVAILABLE.name());
                afterPiece.setSourceType(item.getSourceType());
                afterPiece.setTemplateId(item.getTemplateId());
                afterPiece.setRemark(item.getRemark());
                availabilityMapper.insert(afterPiece);
            } else if (leftOver) {
                item.setEndAt(punchStart);
                availabilityMapper.updateById(item);
            } else {
                item.setStartAt(punchEnd);
                availabilityMapper.updateById(item);
            }
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
