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

    /** 我的模板列表（先滚动续期，保证未来窗口内的实例就绪） */
    @Transactional
    public List<AvailabilityTemplateView> listMine(Long userId) {
        ensureUpToDate(userId);
        return templateMapper.selectList(new LambdaQueryWrapper<CompanionAvailabilityTemplate>()
                        .eq(CompanionAvailabilityTemplate::getCompanionUserId, userId)
                        .orderByDesc(CompanionAvailabilityTemplate::getEnabled)
                        .orderByAsc(CompanionAvailabilityTemplate::getId))
                .stream().map(this::toView).toList();
    }

    /** 新建模板：校验后立即生成未来窗口内的可约档期（FR-P10） */
    @Transactional
    public AvailabilityTemplateView create(Long userId, AvailabilityTemplateSaveRequest req) {
        CompanionAvailabilityTemplate template = validateAndBuild(userId, req);
        template.setEnabled(1);
        template.setLastGeneratedUntil(LocalDate.now().minusDays(1));
        templateMapper.insert(template);
        generateFromWatermark(userId, template);
        return toView(template);
    }

    /** 修改模板：清理未来未占用实例后立即按新规则重生成（改模板即时生效） */
    @Transactional
    public AvailabilityTemplateView update(Long userId, Long templateId, AvailabilityTemplateSaveRequest req) {
        requireOwned(userId, templateId);
        availabilityService.deleteFutureInstancesOfTemplate(userId, templateId);
        CompanionAvailabilityTemplate template = validateAndBuild(userId, req);
        template.setId(templateId);
        template.setEnabled(1);
        template.setLastGeneratedUntil(LocalDate.now().minusDays(1));
        templateMapper.updateById(template);
        generateFromWatermark(userId, template);
        return toView(template);
    }

    /** 启停模板：停用清理未来实例；启用从今天起重新生成 */
    @Transactional
    public AvailabilityTemplateView toggleStatus(Long userId, Long templateId, boolean enabled) {
        CompanionAvailabilityTemplate template = requireOwned(userId, templateId);
        if (enabled) {
            if (template.getEnabled() != 1) {
                template.setEnabled(1);
                template.setLastGeneratedUntil(LocalDate.now().minusDays(1));
                templateMapper.updateById(template);
                generateFromWatermark(userId, template);
            }
        } else {
            availabilityService.deleteFutureInstancesOfTemplate(userId, templateId);
            template.setEnabled(0);
            templateMapper.updateById(template);
        }
        return toView(template);
    }

    /** 删除模板：清理未来未占用实例后删除规则（历史实例保留，不影响已产生订单） */
    @Transactional
    public void delete(Long userId, Long templateId) {
        requireOwned(userId, templateId);
        availabilityService.deleteFutureInstancesOfTemplate(userId, templateId);
        templateMapper.deleteById(templateId);
    }

    /**
     * 滚动续期：将启用中的模板生成窗口补齐到今天 + 28 天（FR-P10 周期设置的核心）。
     * 由陪玩端档期/模板列表访问触发；水位线只前进不回填，手动删除某天的生成实例不会再生。
     */
    public void ensureUpToDate(Long userId) {
        List<CompanionAvailabilityTemplate> templates = templateMapper.selectList(
                new LambdaQueryWrapper<CompanionAvailabilityTemplate>()
                        .eq(CompanionAvailabilityTemplate::getCompanionUserId, userId)
                        .eq(CompanionAvailabilityTemplate::getEnabled, 1));
        for (CompanionAvailabilityTemplate template : templates) {
            generateFromWatermark(userId, template);
        }
    }

    /** 从「今天、生效开始日、水位线+1」三者最大值起补齐生成窗口，完成后推进水位线 */
    private void generateFromWatermark(Long userId, CompanionAvailabilityTemplate template) {
        LocalDate today = LocalDate.now();
        LocalDate windowEnd = today.plusDays(AvailabilityService.GENERATE_WINDOW_DAYS);
        LocalDate genEnd = template.getEffectiveUntil() != null
                && template.getEffectiveUntil().isBefore(windowEnd)
                ? template.getEffectiveUntil() : windowEnd;
        LocalDate genFrom = Stream.of(today, template.getEffectiveFrom(),
                        template.getLastGeneratedUntil().plusDays(1))
                .max(LocalDate::compareTo).orElseThrow();
        if (genFrom.isAfter(genEnd)) {
            return;
        }
        availabilityService.generateRange(userId, genFrom, genEnd, template.getAllDay() == 1,
                template.getStartTime(), template.getEndTime(), parseDaysOfWeek(template.getDayOfWeek()),
                "RECURRENCE", template.getId(), template.getTemplateName());
        template.setLastGeneratedUntil(genEnd);
        templateMapper.updateById(template);
    }

    /** 校验请求参数并构建模板实体（dayOfWeek 规范化为升序去重格式） */
    private CompanionAvailabilityTemplate validateAndBuild(Long userId, AvailabilityTemplateSaveRequest req) {
        Set<Integer> days = parseDaysOfWeek(req.getDayOfWeek());
        if (Boolean.FALSE.equals(req.getAllDay())
                && (req.getStartTime() == null || req.getEndTime() == null)) {
            throw new BusinessException(ErrorCode.AVAILABILITY_TEMPLATE_PARAM_INVALID, "请选择每日开始与结束时间");
        }
        if (Boolean.FALSE.equals(req.getAllDay()) && req.getStartTime().equals(req.getEndTime())) {
            throw new BusinessException(ErrorCode.AVAILABILITY_TEMPLATE_PARAM_INVALID, "结束时间不能等于开始时间");
        }
        LocalDate today = LocalDate.now();
        if (req.getEffectiveFrom().isBefore(today)) {
            throw new BusinessException(ErrorCode.AVAILABILITY_TEMPLATE_PARAM_INVALID, "生效开始日期不能早于今天");
        }
        if (req.getEffectiveUntil() != null) {
            if (req.getEffectiveUntil().isBefore(req.getEffectiveFrom())) {
                throw new BusinessException(ErrorCode.AVAILABILITY_TEMPLATE_PARAM_INVALID,
                        "生效结束日期不能早于开始日期");
            }
            if (req.getEffectiveFrom().plusDays(MAX_TEMPLATE_RANGE_DAYS).isBefore(req.getEffectiveUntil())) {
                throw new BusinessException(ErrorCode.AVAILABILITY_TEMPLATE_PARAM_INVALID,
                        "模板生效跨度一次最多 " + MAX_TEMPLATE_RANGE_DAYS + " 天（长期模板可不填截止日期）");
            }
        }
        CompanionAvailabilityTemplate template = new CompanionAvailabilityTemplate();
        template.setCompanionUserId(userId);
        template.setTemplateName(req.getTemplateName().trim());
        template.setDayOfWeek(days.stream().map(String::valueOf).collect(Collectors.joining(",")));
        template.setAllDay(Boolean.TRUE.equals(req.getAllDay()) ? 1 : 0);
        template.setStartTime(req.getStartTime());
        template.setEndTime(req.getEndTime());
        template.setEffectiveFrom(req.getEffectiveFrom());
        template.setEffectiveUntil(req.getEffectiveUntil());
        return template;
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
