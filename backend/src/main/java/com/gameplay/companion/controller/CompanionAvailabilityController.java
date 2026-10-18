package com.gameplay.companion.controller;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.gameplay.auth.security.JwtPrincipal;
import com.gameplay.common.api.ApiResponse;
import com.gameplay.common.exception.BusinessException;
import com.gameplay.common.exception.ErrorCode;
import com.gameplay.companion.dto.AvailabilityBatchCreateRequest;
import com.gameplay.companion.dto.AvailabilityBatchCreateView;
import com.gameplay.companion.dto.AvailabilityChangeRequest;
import com.gameplay.companion.dto.AvailabilityCreateRequest;
import com.gameplay.companion.dto.AvailabilityView;
import com.gameplay.companion.service.AvailabilityService;
import com.gameplay.companion.service.AvailabilityTemplateService;
import com.gameplay.companion.dto.RestoreDayRequest;
import com.gameplay.companion.dto.RestoreDayResult;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 陪玩师档期管理接口（FR-P10/P11）。
 */
@RestController
@RequestMapping("/api/companion/availabilities")
@PreAuthorize("hasAnyRole('COMPANION', 'ADMIN')")
@RequiredArgsConstructor
public class CompanionAvailabilityController {

    private final AvailabilityService availabilityService;
    private final AvailabilityTemplateService availabilityTemplateService;

    /** 我的档期列表（可按时间范围过滤；先触发周期模板滚动续期，保证未来窗口实例就绪） */
    @GetMapping
    public ApiResponse<List<AvailabilityView>> list(
            @RequestParam(required = false)
            @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startAt,
            @RequestParam(required = false)
            @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endAt,
            Authentication authentication) {
        Long userId = currentUserId(authentication);
        availabilityTemplateService.ensureUpToDate(userId);
        return ApiResponse.ok(availabilityService.listMine(userId, startAt, endAt));
    }

    /** 周日程全景概览（聚合可用档期 + 订单锁定区间） */
    @GetMapping("/overview")
    public ApiResponse<com.gameplay.companion.dto.ScheduleOverviewView> overview(
            @RequestParam(required = false)
            @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startAt,
            @RequestParam(required = false)
            @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endAt,
            Authentication authentication) {
        Long userId = currentUserId(authentication);
        availabilityTemplateService.ensureUpToDate(userId);
        return ApiResponse.ok(availabilityService.getScheduleOverview(userId, startAt, endAt));
    }

    public record ToggleDayOffRequest(
            @org.springframework.format.annotation.DateTimeFormat(pattern = "yyyy-MM-dd")
            @jakarta.validation.constraints.NotNull(message = "日期不能为空")
            java.time.LocalDate date,
            @jakarta.validation.constraints.NotNull(message = "请指定全天状态")
            Boolean isOff,
            String remark
    ) {}

    /** 一键切换整天状态（全天休息 或 全天可约） */
    @PostMapping("/toggle-day-off")
    public ApiResponse<Void> toggleDayOff(@Valid @RequestBody ToggleDayOffRequest request,
                                          Authentication authentication) {
        Long userId = currentUserId(authentication);
        availabilityService.toggleDayOff(userId, request.date(), request.isOff(), request.remark());
        return ApiResponse.ok();
    }

    /** 单日恢复常规作息（FR-P11）：清除当天例外/加档并由启用模板重新生成常规档期 */
    @PostMapping("/restore-day")
    public ApiResponse<RestoreDayResult> restoreDay(@Valid @RequestBody RestoreDayRequest request,
                                                    Authentication authentication) {
        Long userId = currentUserId(authentication);
        return ApiResponse.ok(availabilityService.restoreDay(userId, request.getDate()));
    }

    /** 新增可约时段（FR-P10） */
    @PostMapping
    public ApiResponse<AvailabilityView> create(@Valid @RequestBody AvailabilityCreateRequest request,
                                                Authentication authentication) {
        return ApiResponse.ok(availabilityService.createAvailable(currentUserId(authentication), request));
    }

    /** 设置临时不可约时段（FR-P10） */
    @PostMapping("/unavailable")
    public ApiResponse<AvailabilityView> createUnavailable(@Valid @RequestBody AvailabilityCreateRequest request,
                                                           Authentication authentication) {
        return ApiResponse.ok(availabilityService.createUnavailable(currentUserId(authentication), request));
    }

    /**
     * 批量创建可约档期（FR-P10 日期范围设置）：日期范围内每日同一时段或全天，
     * 与已有档期/订单占用冲突的日期自动跳过并返回明细。
     */
    @PostMapping("/batch")
    public ApiResponse<AvailabilityBatchCreateView> createBatch(
            @Valid @RequestBody AvailabilityBatchCreateRequest request,
            Authentication authentication) {
        return ApiResponse.ok(availabilityService.createBatch(currentUserId(authentication), request));
    }

    /** 周日程批量保存：多个区间在同一事务内完成 */
    @PostMapping("/changes")
    public ApiResponse<Void> applyChanges(@Valid @RequestBody AvailabilityChangeRequest request,
                                          Authentication authentication) {
        availabilityService.applyChanges(currentUserId(authentication), request);
        return ApiResponse.ok();
    }

    /** 删除档期 */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id, Authentication authentication) {
        availabilityService.delete(currentUserId(authentication), id);
        return ApiResponse.ok();
    }

    private Long currentUserId(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof JwtPrincipal principal)) {
            throw new BusinessException(ErrorCode.AUTH_TOKEN_INVALID);
        }
        return principal.userId();
    }
}
