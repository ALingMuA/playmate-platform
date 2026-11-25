package com.gameplay.announcement.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gameplay.announcement.dto.AnnouncementRequest;
import com.gameplay.announcement.dto.AnnouncementView;
import com.gameplay.announcement.service.AnnouncementService;
import com.gameplay.audit.service.OperationLogService;
import com.gameplay.auth.security.JwtPrincipal;
import com.gameplay.common.api.ApiResponse;
import com.gameplay.common.exception.BusinessException;
import com.gameplay.common.exception.ErrorCode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
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
@Validated
@RestController
@RequestMapping("/api/admin/announcements")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminAnnouncementController {

    private final AnnouncementService announcementService;
    private final OperationLogService operationLogService;
    private final ObjectMapper objectMapper;

    /** 分页查询（全部状态） */
    @GetMapping
    public ApiResponse<Page<AnnouncementView>> list(@RequestParam(required = false) String publishStatus,
                                                    @RequestParam(defaultValue = "1") @Min(1) long page,
                                                    @RequestParam(defaultValue = "10") @Min(1) @Max(100) long size) {
        return ApiResponse.ok(announcementService.adminPage(publishStatus, page, size));
    }

    /** 新增公告 */
    @PostMapping
    public ApiResponse<AnnouncementView> create(@Valid @RequestBody AnnouncementRequest request,
                                                Authentication authentication) {
        Long adminId = currentAdminId(authentication);
        AnnouncementView created = announcementService.create(adminId, request);
        recordAudit(adminId, "CREATE_ANNOUNCEMENT", created.getId(), null, created);
        return ApiResponse.ok(created);
    }

    /** 编辑公告 */
    @PutMapping("/{id}")
    public ApiResponse<AnnouncementView> update(@PathVariable Long id,
                                                @Valid @RequestBody AnnouncementRequest request,
                                                Authentication authentication) {
        Long adminId = currentAdminId(authentication);
        AnnouncementView before = announcementService.adminDetail(id);
        AnnouncementView after = announcementService.update(id, request);
        recordAudit(adminId, "UPDATE_ANNOUNCEMENT", id, before, after);
        return ApiResponse.ok(after);
    }

    /** 发布公告 */
    @PostMapping("/{id}/publish")
    public ApiResponse<AnnouncementView> publish(@PathVariable Long id, Authentication authentication) {
        Long adminId = currentAdminId(authentication);
        AnnouncementView before = announcementService.adminDetail(id);
        AnnouncementView after = announcementService.publish(id, adminId);
        recordAudit(adminId, "PUBLISH_ANNOUNCEMENT", id, before, after);
        return ApiResponse.ok(after);
    }

    /** 撤回公告 */
    @PostMapping("/{id}/revoke")
    public ApiResponse<AnnouncementView> revoke(@PathVariable Long id, Authentication authentication) {
        Long adminId = currentAdminId(authentication);
        AnnouncementView before = announcementService.adminDetail(id);
        AnnouncementView after = announcementService.revoke(id, adminId);
        recordAudit(adminId, "REVOKE_ANNOUNCEMENT", id, before, after);
        return ApiResponse.ok(after);
    }

    /** 恢复已撤回公告至草稿 */
    @PostMapping("/{id}/restore-draft")
    public ApiResponse<AnnouncementView> restoreDraft(@PathVariable Long id, Authentication authentication) {
        Long adminId = currentAdminId(authentication);
        AnnouncementView before = announcementService.adminDetail(id);
        AnnouncementView after = announcementService.restoreDraft(id);
        recordAudit(adminId, "RESTORE_DRAFT_ANNOUNCEMENT", id, before, after);
        return ApiResponse.ok(after);
    }

    /** 删除公告（仅草稿） */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id, Authentication authentication) {
        Long adminId = currentAdminId(authentication);
        AnnouncementView before = announcementService.adminDetail(id);
        announcementService.delete(id);
        recordAudit(adminId, "DELETE_ANNOUNCEMENT", id, before, null);
        return ApiResponse.ok();
    }

    private void recordAudit(Long adminId, String operationType, Long targetId,
                             AnnouncementView before, AnnouncementView after) {
        operationLogService.record(
                adminId,
                "ADMIN",
                operationType,
                "ANNOUNCEMENT",
                targetId,
                toJson(before),
                toJson(after),
                ""
        );
    }

    private String toJson(AnnouncementView view) {
        if (view == null) {
            return "";
        }
        try {
            return objectMapper.writeValueAsString(view);
        } catch (JsonProcessingException e) {
            return String.valueOf(view.getTitle());
        }
    }

    private Long currentAdminId(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof JwtPrincipal principal)) {
            throw new BusinessException(ErrorCode.AUTH_TOKEN_INVALID);
        }
        return principal.userId();
    }
}
