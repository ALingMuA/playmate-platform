package com.gameplay.admin.controller;

import com.gameplay.admin.dto.StatsOverviewView;
import com.gameplay.admin.service.AdminStatsService;
import com.gameplay.common.api.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理端数据概览接口（FR-M02）。
 */
@RestController
@RequestMapping("/api/admin/stats")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminStatsController {

    private final AdminStatsService adminStatsService;

    /** 数据概览 */
    @GetMapping("/overview")
    public ApiResponse<StatsOverviewView> overview() {
        return ApiResponse.ok(adminStatsService.overview());
    }
}
