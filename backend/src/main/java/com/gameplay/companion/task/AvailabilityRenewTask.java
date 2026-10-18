package com.gameplay.companion.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.gameplay.companion.domain.CompanionAvailabilityTemplate;
import com.gameplay.companion.mapper.CompanionAvailabilityTemplateMapper;
import com.gameplay.companion.service.AvailabilityTemplateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 档期每日自动续期任务（FR-P10 周期设置）。
 * <p>每天 03:10 为所有启用模板的陪玩师补齐未来 28 天档期，避免长期不登录导致档期断档。</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AvailabilityRenewTask {

    private final CompanionAvailabilityTemplateMapper templateMapper;
    private final AvailabilityTemplateService templateService;

    /** 每天 03:10 为所有启用模板的陪玩师补齐未来 28 天，避免长期不登录导致档期断档 */
    @Scheduled(cron = "0 10 3 * * *")
    public void renewAll() {
        List<Long> userIds = templateMapper.selectList(
                new LambdaQueryWrapper<CompanionAvailabilityTemplate>()
                        .eq(CompanionAvailabilityTemplate::getEnabled, 1)
                        .select(CompanionAvailabilityTemplate::getCompanionUserId))
                .stream().map(CompanionAvailabilityTemplate::getCompanionUserId).distinct().toList();
        for (Long userId : userIds) {
            try {
                templateService.ensureWindow(userId);
            } catch (Exception e) {
                log.warn("档期续期失败 userId={}", userId, e);
            }
        }
    }
}
