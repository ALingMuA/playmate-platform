package com.gameplay.announcement.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.gameplay.announcement.dto.AnnouncementRequest;
import com.gameplay.announcement.dto.AnnouncementView;
import com.gameplay.announcement.service.AnnouncementService;
import com.gameplay.auth.security.JwtPrincipal;
import com.gameplay.common.api.ApiResponse;
import com.gameplay.common.exception.BusinessException;
import com.gameplay.common.exception.ErrorCode;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理端公告接口（FR-M13）。
 */
@RestController
@RequestMapping("/api/admin/announcements")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminAnnouncementController {

    private final AnnouncementService announcementService;

    /** 分页查询（全部状态） */
    @GetMapping
    public ApiResponse<Page<AnnouncementView>> list(@RequestParam(required = false) String publishStatus,
                                                    @RequestParam(defaultValue = "1") long page,
                                                    @RequestParam(defaultValue = "10") long size) {
        return ApiResponse.ok(announcementService.adminPage(publishStatus, page, size));
    }

    /** 新增公告 */
    @PostMapping
    public ApiResponse<AnnouncementView> create(@Valid @RequestBody AnnouncementRequest request,
                                                Authentication authentication) {
        return ApiResponse.ok(announcementService.create(currentAdminId(authentication), request));
    }

    /** 编辑公告 */
    @PutMapping("/{id}")
    public ApiResponse<AnnouncementView> update(@PathVariable Long id,
                                                @Valid @RequestBody AnnouncementRequest request,
                                                Authentication authentication) {
        return ApiResponse.ok(announcementService.update(id, request));
    }

    /** 发布公告 */
    @PostMapping("/{id}/publish")
    public ApiResponse<AnnouncementView> publish(@PathVariable Long id, Authentication authentication) {
        return ApiResponse.ok(announcementService.publish(id, currentAdminId(authentication)));
    }

    /** 撤回公告 */
    @PostMapping("/{id}/revoke")
    public ApiResponse<AnnouncementView> revoke(@PathVariable Long id, Authentication authentication) {
        return ApiResponse.ok(announcementService.revoke(id, currentAdminId(authentication)));
    }

    /** 删除公告（仅草稿） */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id, Authentication authentication) {
        announcementService.delete(id);
        return ApiResponse.ok();
    }

    private Long currentAdminId(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof JwtPrincipal principal)) {
            throw new BusinessException(ErrorCode.AUTH_TOKEN_INVALID);
        }
        return principal.userId();
    }
}
