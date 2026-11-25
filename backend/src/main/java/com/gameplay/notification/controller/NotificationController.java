package com.gameplay.notification.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.gameplay.auth.security.JwtPrincipal;
import com.gameplay.common.api.ApiResponse;
import com.gameplay.common.exception.BusinessException;
import com.gameplay.common.exception.ErrorCode;
import com.gameplay.notification.dto.NotificationView;
import com.gameplay.notification.service.NotificationService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 用户端站内通知接口（FR-A07）。
 */
@Validated
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    /** 我的通知分页 */
    @GetMapping
    public ApiResponse<Page<NotificationView>> list(@RequestParam(defaultValue = "1") @Min(1) long page,
                                                    @RequestParam(defaultValue = "10") @Min(1) @Max(100) long size,
                                                    @RequestParam(required = false) Integer readStatus,
                                                    Authentication authentication) {
        if (readStatus != null && readStatus != 0 && readStatus != 1) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "readStatus 仅支持 0（未读）或 1（已读）");
        }
        return ApiResponse.ok(notificationService.page(currentUserId(authentication), page, size, readStatus));
    }

    /** 未读通知数 */
    @GetMapping("/unread-count")
    public ApiResponse<Map<String, Long>> unreadCount(Authentication authentication) {
        return ApiResponse.ok(Map.of("count", notificationService.unreadCount(currentUserId(authentication))));
    }

    /** 标记单条已读 */
    @PutMapping("/{id}/read")
    public ApiResponse<Void> markRead(@PathVariable Long id, Authentication authentication) {
        notificationService.markRead(currentUserId(authentication), id);
        return ApiResponse.ok();
    }

    /** 全部已读 */
    @PutMapping("/read-all")
    public ApiResponse<Void> markAllRead(Authentication authentication) {
        notificationService.markAllRead(currentUserId(authentication));
        return ApiResponse.ok();
    }

    private Long currentUserId(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof JwtPrincipal principal)) {
            throw new BusinessException(ErrorCode.AUTH_TOKEN_INVALID);
        }
        return principal.userId();
    }
}
