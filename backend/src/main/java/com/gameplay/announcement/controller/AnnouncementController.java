package com.gameplay.announcement.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.gameplay.announcement.dto.AnnouncementView;
import com.gameplay.announcement.service.AnnouncementService;
import com.gameplay.common.api.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 公告公开查看接口（FR-A08：仅已发布，游客可访问）。
 */
@RestController
@RequestMapping("/api/announcements")
@RequiredArgsConstructor
public class AnnouncementController {

    private final AnnouncementService announcementService;

    /** 已发布公告分页 */
    @GetMapping
    public ApiResponse<Page<AnnouncementView>> list(@RequestParam(defaultValue = "1") long page,
                                                    @RequestParam(defaultValue = "10") long size) {
        return ApiResponse.ok(announcementService.publicPage(page, size));
    }

    /** 已发布公告详情 */
    @GetMapping("/{id}")
    public ApiResponse<AnnouncementView> detail(@PathVariable Long id) {
        return ApiResponse.ok(announcementService.publicDetail(id));
    }
}
