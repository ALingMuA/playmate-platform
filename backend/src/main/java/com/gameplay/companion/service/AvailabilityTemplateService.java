package com.gameplay.companion.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.gameplay.common.exception.BusinessException;
import com.gameplay.common.exception.ErrorCode;
import com.gameplay.companion.domain.CompanionAvailabilityTemplate;
import com.gameplay.companion.dto.AvailabilityTemplateSaveRequest;
import com.gameplay.companion.dto.AvailabilityTemplateView;
import com.gameplay.companion.mapper.CompanionAvailabilityTemplateMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 陪玩师档期周期模板服务（FR-P10 周期设置）。
 *
 * <p>模板 = 周几集合 + 每日时段（或全天）+ 生效日期范围，创建/启用即生成未来窗口内的可约档期
 * 实例（source_type=RECURRENCE，template_id 标记归属）。陪玩端每次访问档期或模板列表时，
 * {@link #ensureUpToDate} 将启用中的模板滚动补齐到未来 28 天——设置一次长期生效，
 * 无需每周重复设置。修改/停用/删除模板时先清理未来未占用实例再按新规则重生成，
 * 已被有效订单锁定的时间不受影响。</p>
 */
@Service
@RequiredArgsConstructor
public class AvailabilityTemplateService {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("MM-dd");

    /** 模板生效跨度防呆上限（天）：长期模板 effectiveUntil 为空，有截止日的最长两年 */
    private static final int MAX_TEMPLATE_RANGE_DAYS = 730;

    private static final String[] WEEK_NAMES = {"周一", "周二", "周三", "周四", "周五", "周六", "周日"};

    private final CompanionAvailabilityTemplateMapper templateMapper;
    private final AvailabilityService availabilityService;

    private final java.util.concurrent.ConcurrentHashMap<Long, java.time.Instant> ensureThrottleMap = new java.util.concurrent.ConcurrentHashMap<>();

    /** 我的模板列表（先滚动续期，保证未来窗口内的实例就绪） */
    @Transactional
    public List<AvailabilityTemplateView> listMine(Long userId) {
        ensureWindowThrottled(userId);
        return templateMapper.selectList(new LambdaQueryWrapper<CompanionAvailabilityTemplate>()
                        .eq(CompanionAvailabilityTemplate::getCompanionUserId, userId)
                        .orderByDesc(CompanionAvailabilityTemplate::getEnabled)
                        .orderByAsc(CompanionAvailabilityTemplate::getId))
                .stream().map(this::toView).toList();
    }

    /** 新建模板：校验后立即按天幂等生成未来窗口内的可约档期（FR-P10） */
    @Transactional
    public AvailabilityTemplateView create(Long userId, AvailabilityTemplateSaveRequest req) {
        validateCommon(req);
        if (req.getEffectiveFrom() == null || req.getEffectiveFrom().isBefore(LocalDate.now())) {
            throw new BusinessException(ErrorCode.AVAILABILITY_TEMPLATE_PARAM_INVALID, "生效开始日期不能早于今天");
        }
        CompanionAvailabilityTemplate template = new CompanionAvailabilityTemplate();
        template.setCompanionUserId(userId);
        template.setTemplateName(req.getTemplateName().trim());
        template.setDayOfWeek(normalizeDays(req.getDayOfWeek()));
        template.setAllDay(Boolean.TRUE.equals(req.getAllDay()) ? 1 : 0);
        template.setStartTime(Boolean.TRUE.equals(req.getAllDay()) ? null : req.getStartTime());
        template.setEndTime(Boolean.TRUE.equals(req.getAllDay()) ? null : req.getEndTime());
        template.setEffectiveFrom(req.getEffectiveFrom());
        template.setEffectiveUntil(req.getEffectiveUntil());
        template.setEnabled(1);
        template.setLastGeneratedUntil(LocalDate.now().minusDays(1));
        templateMapper.insert(template);
        ensureWindow(userId);
        return toView(templateMapper.selectById(template.getId()));
    }

    /** 修改模板：清理未来未占用实例后按新规则幂等重生成（不改动启停状态，支持历史生效日与清空截止日） */
    @Transactional
    public AvailabilityTemplateView update(Long userId, Long templateId, AvailabilityTemplateSaveRequest req) {
        CompanionAvailabilityTemplate existing = requireOwned(userId, templateId);
        validateCommon(req);
        // 编辑场景：effectiveFrom 早于今天/为空保留原值（绝不 422）
        if (req.getEffectiveFrom() != null && !req.getEffectiveFrom().isBefore(LocalDate.now())) {
            existing.setEffectiveFrom(req.getEffectiveFrom());
        }
        existing.setTemplateName(req.getTemplateName().trim());
        existing.setDayOfWeek(normalizeDays(req.getDayOfWeek()));
        existing.setAllDay(Boolean.TRUE.equals(req.getAllDay()) ? 1 : 0);
        existing.setStartTime(Boolean.TRUE.equals(req.getAllDay()) ? null : req.getStartTime());
        existing.setEndTime(Boolean.TRUE.equals(req.getAllDay()) ? null : req.getEndTime());
        existing.setEffectiveUntil(req.getEffectiveUntil()); // 允许 null，实体已加 ALWAYS 策略
        // 不修改 enabled！保持现有状态
        availabilityService.deleteFutureInstancesOfTemplate(userId, templateId);
        templateMapper.updateById(existing);
        if (existing.getEnabled() == 1) {
            ensureWindow(userId);
        }
        return toView(templateMapper.selectById(templateId));
    }

    /** 启停模板：停用清理未来实例；启用从今天起重新生成 */
    @Transactional
    public AvailabilityTemplateView toggleStatus(Long userId, Long templateId, boolean enabled) {
        CompanionAvailabilityTemplate template = requireOwned(userId, templateId);
        if (enabled) {
            if (template.getEnabled() != 1) {
                template.setEnabled(1);
                templateMapper.updateById(template);
                ensureWindow(userId);
            }
        } else {
            availabilityService.deleteFutureInstancesOfTemplate(userId, templateId);
            template.setEnabled(0);
            templateMapper.updateById(template);
        }
        return toView(templateMapper.selectById(templateId));
    }

    /** 删除模板：清理未来未占用实例后删除规则（历史实例保留，不影响已产生订单） */
    @Transactional
    public void delete(Long userId, Long templateId) {
        requireOwned(userId, templateId);
        availabilityService.deleteFutureInstancesOfTemplate(userId, templateId);
        templateMapper.deleteById(templateId);
    }

    /**
     * GET 请求节流版滚动续期：每用户 5 分钟内最多执行一次 ensureWindow。
     */
    public void ensureWindowThrottled(Long userId) {
        java.time.Instant lastRun = ensureThrottleMap.get(userId);
        if (lastRun != null && java.time.Duration.between(lastRun, java.time.Instant.now()).toMinutes() < 5) {
            return;
        }
        ensureWindow(userId);
        ensureThrottleMap.put(userId, java.time.Instant.now());
    }

    /** 兼容旧接口调用 */
    public void ensureUpToDate(Long userId) {
        ensureWindowThrottled(userId);
    }

    /**
     * 按天幂等生成核心：对该陪玩师所有 enabled=1 模板，按日查漏补缺填充未来 28 天窗口。
     */
    @Transactional
    public void ensureWindow(Long userId) {
        LocalDate today = LocalDate.now();
        LocalDate windowEnd = today.plusDays(AvailabilityService.GENERATE_WINDOW_DAYS); // 28 天

        // 锁住该陪玩师的模板行，串行化同一用户的并发生成
        List<CompanionAvailabilityTemplate> templates = templateMapper.selectList(
                new LambdaQueryWrapper<CompanionAvailabilityTemplate>()
                        .eq(CompanionAvailabilityTemplate::getCompanionUserId, userId)
                        .eq(CompanionAvailabilityTemplate::getEnabled, 1)
                        .last("FOR UPDATE"));

        if (templates.isEmpty()) {
            return;
        }

        for (CompanionAvailabilityTemplate t : templates) {
            LocalDate from = t.getEffectiveFrom().isAfter(today) ? t.getEffectiveFrom() : today;
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
                if (t.getAllDay() != null && t.getAllDay() == 1) {
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

                // 今天已完全过去的时段不生成
                if (date.isEqual(today) && !end.isAfter(LocalDateTime.now())) {
                    continue;
                }

                // 1. 是否已生成相同 templateId + startAt（幂等）
                if (availabilityService.existsGenerated(t.getId(), start)) {
                    continue;
                }
                // 2. 是否与用户显式休息重叠（休息优先）
                if (availabilityService.hasUnavailableOverlap(userId, start, end)) {
                    continue;
                }
                // 3. 是否已有可约档期（手动或其他模板）
                if (availabilityService.hasAvailableOverlap(userId, start, end)) {
                    continue;
                }
                // 4. 是否已被有效订单占用
                if (availabilityService.hasOrderConflict(userId, start, end)) {
                    continue;
                }

                // 5. 插入（唯一键兜底，DuplicateKey 忽略）
                availabilityService.insertRecurrenceSafe(userId, t.getId(), start, end, t.getTemplateName());
            }
            t.setLastGeneratedUntil(to);
            templateMapper.updateById(t);
        }
        ensureThrottleMap.put(userId, java.time.Instant.now());
    }

    /** 通用校验（名称、星期、时间区间、生效跨度） */
    private void validateCommon(AvailabilityTemplateSaveRequest req) {
        if (req.getTemplateName() == null || req.getTemplateName().trim().isEmpty()) {
            throw new BusinessException(ErrorCode.AVAILABILITY_TEMPLATE_PARAM_INVALID, "模板名称不能为空");
        }
        parseDaysOfWeek(req.getDayOfWeek());
        if (Boolean.FALSE.equals(req.getAllDay())
                && (req.getStartTime() == null || req.getEndTime() == null)) {
            throw new BusinessException(ErrorCode.AVAILABILITY_TEMPLATE_PARAM_INVALID, "请选择每日开始与结束时间");
        }
        if (Boolean.FALSE.equals(req.getAllDay()) && req.getStartTime().equals(req.getEndTime())) {
            throw new BusinessException(ErrorCode.AVAILABILITY_TEMPLATE_PARAM_INVALID, "结束时间不能等于开始时间");
        }
        if (req.getEffectiveUntil() != null && req.getEffectiveFrom() != null) {
            if (req.getEffectiveUntil().isBefore(req.getEffectiveFrom())) {
                throw new BusinessException(ErrorCode.AVAILABILITY_TEMPLATE_PARAM_INVALID,
                        "生效结束日期不能早于开始日期");
            }
            if (req.getEffectiveFrom().plusDays(MAX_TEMPLATE_RANGE_DAYS).isBefore(req.getEffectiveUntil())) {
                throw new BusinessException(ErrorCode.AVAILABILITY_TEMPLATE_PARAM_INVALID,
                        "模板生效跨度一次最多 " + MAX_TEMPLATE_RANGE_DAYS + " 天（长期模板可不填截止日期）");
            }
        }
    }

    /** 规范化周几字符串为升序去重（如 "3,1" -> "1,3"） */
    private String normalizeDays(String dayOfWeek) {
        Set<Integer> days = parseDaysOfWeek(dayOfWeek);
        return days.stream().map(String::valueOf).collect(Collectors.joining(","));
    }

    /** 解析周几集合：逗号分隔数字（1=周一..7=周日），非法或为空即拒绝 */
    private Set<Integer> parseDaysOfWeek(String dayOfWeek) {
        Set<Integer> days = new TreeSet<>();
        for (String part : dayOfWeek.split(",")) {
            int day;
            try {
                day = Integer.parseInt(part.trim());
            } catch (NumberFormatException e) {
                throw invalidDayOfWeek();
            }
            if (day < 1 || day > 7) {
                throw invalidDayOfWeek();
            }
            days.add(day);
        }
        if (days.isEmpty()) {
            throw invalidDayOfWeek();
        }
        return days;
    }

    private BusinessException invalidDayOfWeek() {
        return new BusinessException(ErrorCode.AVAILABILITY_TEMPLATE_PARAM_INVALID,
                "生效星期必须为 1-7 的数字（1=周一..7=周日），逗号分隔");
    }

    /** 校验模板存在且归属当前用户 */
    private CompanionAvailabilityTemplate requireOwned(Long userId, Long templateId) {
        CompanionAvailabilityTemplate template = templateMapper.selectById(templateId);
        if (template == null) {
            throw new BusinessException(ErrorCode.AVAILABILITY_TEMPLATE_NOT_FOUND);
        }
        if (!template.getCompanionUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.PERMISSION_DATA_SCOPE_DENIED);
        }
        return template;
    }

    /** 视图转换：附带展示用规则描述文本 */
    private AvailabilityTemplateView toView(CompanionAvailabilityTemplate t) {
        return AvailabilityTemplateView.builder()
                .id(t.getId())
                .templateName(t.getTemplateName())
                .dayOfWeek(t.getDayOfWeek())
                .allDay(t.getAllDay() == 1)
                .startTime(t.getStartTime())
                .endTime(t.getEndTime())
                .effectiveFrom(t.getEffectiveFrom())
                .effectiveUntil(t.getEffectiveUntil())
                .enabled(t.getEnabled() == 1)
                .lastGeneratedUntil(t.getLastGeneratedUntil())
                .ruleText(buildRuleText(t))
                .build();
    }

    /** 组装规则描述：如"周一至周五 19:00-23:00（10-17 起长期）" */
    private String buildRuleText(CompanionAvailabilityTemplate t) {
        Set<Integer> days = parseDaysOfWeek(t.getDayOfWeek());
        String dayText;
        if (days.size() == 7) {
            dayText = "每天";
        } else {
            dayText = days.stream().map(d -> WEEK_NAMES[d - 1]).collect(Collectors.joining("、"));
        }
        String timeText;
        if (t.getAllDay() == 1) {
            timeText = "全天";
        } else if (t.getEndTime().isAfter(t.getStartTime())) {
            timeText = TIME_FMT.format(t.getStartTime()) + "-" + TIME_FMT.format(t.getEndTime());
        } else {
            timeText = TIME_FMT.format(t.getStartTime()) + "-次日" + TIME_FMT.format(t.getEndTime());
        }
        String rangeText = t.getEffectiveUntil() == null
                ? DATE_FMT.format(t.getEffectiveFrom()) + " 起长期"
                : DATE_FMT.format(t.getEffectiveFrom()) + " 至 " + DATE_FMT.format(t.getEffectiveUntil());
        return dayText + " " + timeText + "（" + rangeText + "）";
    }
}
