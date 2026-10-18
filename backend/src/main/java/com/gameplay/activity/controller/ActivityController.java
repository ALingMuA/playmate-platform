package com.gameplay.activity.controller;

import com.gameplay.activity.dto.ActivityView;
import com.gameplay.activity.service.PlatformActivityService;
import com.gameplay.common.api.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 平台活动公开接口（游客与用户可访问）。
 */
@RestController
@RequestMapping("/api/activities")
@RequiredArgsConstructor
public class ActivityController {

    private final PlatformActivityService activityService;

    @GetMapping
    public ApiResponse<List<ActivityView>> list() {
        return ApiResponse.ok(activityService.listPublicActivities());
    }

    @GetMapping("/{id}")
    public ApiResponse<ActivityView> detail(@PathVariable Long id) {
        return ApiResponse.ok(activityService.getActivityDetail(id));
    }
}
