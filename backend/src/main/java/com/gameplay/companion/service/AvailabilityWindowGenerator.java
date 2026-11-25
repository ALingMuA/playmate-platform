package com.gameplay.companion.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.gameplay.companion.domain.CompanionAvailability;
import com.gameplay.companion.domain.CompanionAvailabilityTemplate;
import com.gameplay.companion.enums.AvailabilityStatus;
import com.gameplay.companion.mapper.CompanionAvailabilityMapper;
import com.gameplay.companion.mapper.CompanionAvailabilityTemplateMapper;
import com.gameplay.companion.mapper.CompanionProfileMapper;
import com.gameplay.order.domain.OrderTimeSlot;
import com.gameplay.order.mapper.OrderTimeSlotMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * 陪玩师周期档期滚动生成器（独立 Bean，确保 @Transactional + FOR UPDATE 始终经由 Spring AOP 代理生效）。
 */
@Service
@RequiredArgsConstructor
public class AvailabilityWindowGenerator {

    private final CompanionProfileMapper profileMapper;
    private final CompanionAvailabilityTemplateMapper templateMapper;
    private final CompanionAvailabilityMapper availabilityMapper;
    private final OrderTimeSlotMapper orderTimeSlotMapper;

    /**
     * 按天幂等生成核心：对该陪玩师所有 enabled=1 模板，按日查漏补缺填充未来 28 天窗口。
     * <p>支持昨日跨天模板延续到今日凌晨的尾段补全，以及单日部分锁定后剩余空隙的精准补齐。</p>
     */
    @Transactional
    public void ensureWindow(Long userId) {
        // 按统一锁序先锁 companion_profile，防止与并发下单/手动档期调整产生间隙锁死锁
        profileMapper.selectByUserIdForUpdate(userId);

        List<CompanionAvailabilityTemplate> templates = templateMapper.selectList(
                new LambdaQueryWrapper<CompanionAvailabilityTemplate>()
                        .eq(CompanionAvailabilityTemplate::getCompanionUserId, userId)
                        .eq(CompanionAvailabilityTemplate::getEnabled, 1)
                        .last("FOR UPDATE"));
        if (templates.isEmpty()) {
            return;
        }

        LocalDate today = LocalDate.now();
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime todayStart = today.atStartOfDay();
        LocalDate windowEnd = today.plusDays(AvailabilityService.GENERATE_WINDOW_DAYS);

        for (CompanionAvailabilityTemplate t : templates) {
            boolean isAllDay = t.getAllDay() != null && t.getAllDay() == 1;
            boolean isCrossDay = !isAllDay && t.getStartTime() != null && t.getEndTime() != null
                    && !t.getEndTime().isAfter(t.getStartTime());
            LocalDate earliestCheck = isCrossDay ? today.minusDays(1) : today;
            LocalDate from = t.getEffectiveFrom().isAfter(earliestCheck) ? t.getEffectiveFrom() : earliestCheck;
            LocalDate to = (t.getEffectiveUntil() == null || t.getEffectiveUntil().isAfter(windowEnd))
                    ? windowEnd : t.getEffectiveUntil();
            if (from.isAfter(to)) {
                continue;
            }

            Set<Integer> targetDays = parseDaysOfWeek(t.getDayOfWeek());
            for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
                if (!targetDays.contains(date.getDayOfWeek().getValue())) {
                    continue;
                }
                LocalDateTime start;
                LocalDateTime end;
                if (isAllDay) {
                    start = date.atStartOfDay();
                    end = date.plusDays(1).atStartOfDay();
                } else {
                    if (t.getStartTime() == null || t.getEndTime() == null) {
                        continue;
                    }
                    start = date.atTime(t.getStartTime());
                    end = date.atTime(t.getEndTime());
                    if (!end.isAfter(start)) {
                        end = date.plusDays(1).atTime(t.getEndTime());
                    }
                }

                // 昨日跨天模板：仅补齐延续到今日凌晨的尾段 [今日 00:00, end)
                if (date.isBefore(today) && start.isBefore(todayStart)) {
                    start = todayStart;
                }
                if (!end.isAfter(now) || !start.isBefore(end)) {
                    continue;
                }

                List<LocalDateTime[]> blockers = new ArrayList<>();
                List<CompanionAvailability> existingAvail = availabilityMapper.selectList(
                        new LambdaQueryWrapper<CompanionAvailability>()
                                .eq(CompanionAvailability::getCompanionUserId, userId)
                                .lt(CompanionAvailability::getStartAt, end)
                                .gt(CompanionAvailability::getEndAt, start));
                for (CompanionAvailability a : existingAvail) {
                    blockers.add(new LocalDateTime[]{a.getStartAt(), a.getEndAt()});
                }
                List<OrderTimeSlot> orderConflicts = orderTimeSlotMapper.selectConflictSlotsForUpdate(
                        userId, start, end, now);
                for (OrderTimeSlot slot : orderConflicts) {
                    blockers.add(new LocalDateTime[]{slot.getStartAt(), slot.getEndAt()});
                }

                List<LocalDateTime[]> gaps = subtractIntervals(start, end, blockers);
                for (LocalDateTime[] gap : gaps) {
                    if (!gap[1].isAfter(now)) {
                        continue;
                    }
                    insertRecurrenceSafe(userId, t.getId(), gap[0], gap[1], t.getTemplateName());
                }
            }

            templateMapper.update(null, new LambdaUpdateWrapper<CompanionAvailabilityTemplate>()
                    .eq(CompanionAvailabilityTemplate::getId, t.getId())
                    .set(CompanionAvailabilityTemplate::getLastGeneratedUntil, to));
        }
    }

    private void insertRecurrenceSafe(Long userId, Long templateId, LocalDateTime startAt, LocalDateTime endAt, String remark) {
        try {
            CompanionAvailability availability = new CompanionAvailability();
            availability.setCompanionUserId(userId);
            availability.setStartAt(startAt);
            availability.setEndAt(endAt);
            availability.setAvailabilityStatus(AvailabilityStatus.AVAILABLE.name());
            availability.setSourceType("RECURRENCE");
            availability.setTemplateId(templateId);
            availability.setRemark(remark == null ? "" : remark);
            availabilityMapper.insert(availability);
        } catch (DuplicateKeyException e) {
            // 唯一键冲突安全忽略
        }
    }

    /** 从 [targetStart, targetEnd) 中减去一组阻塞区间，返回有序的非空剩余区间 */
    static List<LocalDateTime[]> subtractIntervals(LocalDateTime targetStart, LocalDateTime targetEnd,
                                                   List<LocalDateTime[]> blockers) {
        if (!targetStart.isBefore(targetEnd)) {
            return List.of();
        }
        if (blockers == null || blockers.isEmpty()) {
            return Collections.singletonList(new LocalDateTime[]{targetStart, targetEnd});
        }
        List<LocalDateTime[]> clipped = new ArrayList<>();
        for (LocalDateTime[] b : blockers) {
            if (b == null || b[0] == null || b[1] == null) {
                continue;
            }
            LocalDateTime s = b[0].isBefore(targetStart) ? targetStart : b[0];
            LocalDateTime e = b[1].isAfter(targetEnd) ? targetEnd : b[1];
            if (s.isBefore(e)) {
                clipped.add(new LocalDateTime[]{s, e});
            }
        }
        if (clipped.isEmpty()) {
            return Collections.singletonList(new LocalDateTime[]{targetStart, targetEnd});
        }
        clipped.sort(Comparator.comparing(r -> r[0]));

        List<LocalDateTime[]> gaps = new ArrayList<>();
        LocalDateTime cursor = targetStart;
        for (LocalDateTime[] b : clipped) {
            if (b[0].isAfter(cursor)) {
                gaps.add(new LocalDateTime[]{cursor, b[0]});
            }
            if (b[1].isAfter(cursor)) {
                cursor = b[1];
            }
            if (!cursor.isBefore(targetEnd)) {
                break;
            }
        }
        if (cursor.isBefore(targetEnd)) {
            gaps.add(new LocalDateTime[]{cursor, targetEnd});
        }
        return gaps;
    }

    private Set<Integer> parseDaysOfWeek(String dayOfWeek) {
        Set<Integer> days = new TreeSet<>();
        if (dayOfWeek == null || dayOfWeek.isBlank()) {
            return days;
        }
        for (String part : dayOfWeek.split(",")) {
            try {
                int d = Integer.parseInt(part.trim());
                if (d >= 1 && d <= 7) {
                    days.add(d);
                }
            } catch (NumberFormatException ignored) {
            }
        }
        return days;
    }
}
