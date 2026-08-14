package com.gameplay.notification.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.gameplay.common.exception.BusinessException;
import com.gameplay.common.exception.ErrorCode;
import com.gameplay.notification.domain.Notification;
import com.gameplay.notification.dto.NotificationView;
import com.gameplay.notification.mapper.NotificationMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 站内通知服务（FR-A07）。
 *
 * <p>{@code record} 供订单、审核、投诉等模块在业务事件后调用；
 * 通知写入失败不影响主业务（调用方自行保证事务边界）。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationMapper notificationMapper;

    /** 生成通知（FR-A07：申请审核、服务审核、订单状态、投诉结果等事件） */
    @Transactional
    public void record(Long userId, String notificationType, String title, String content,
                       String relatedType, Long relatedId) {
        Notification notification = new Notification();
        notification.setUserId(userId);
        notification.setNotificationType(notificationType);
        notification.setTitle(title);
        notification.setContent(content);
        notification.setRelatedType(relatedType);
        notification.setRelatedId(relatedId == null ? 0L : relatedId);
        notification.setReadStatus(0);
        try {
            notificationMapper.insert(notification);
        } catch (Exception e) {
            log.warn("通知写入失败: userId={}, type={}, 原因: {}", userId, notificationType, e.getMessage());
        }
    }

    /** 我的通知分页（FR-A07） */
    public Page<NotificationView> page(Long userId, long page, long size) {
        Page<Notification> p = notificationMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<Notification>()
                        .eq(Notification::getUserId, userId)
                        .orderByDesc(Notification::getId));
        Page<NotificationView> vp = new Page<>(p.getCurrent(), p.getSize(), p.getTotal());
        vp.setRecords(p.getRecords().stream().map(this::toView).toList());
        return vp;
    }

    /** 未读通知数 */
    public long unreadCount(Long userId) {
        return notificationMapper.selectCount(new LambdaQueryWrapper<Notification>()
                .eq(Notification::getUserId, userId)
                .eq(Notification::getReadStatus, 0));
    }

    /** 标记单条已读（仅本人） */
    @Transactional
    public void markRead(Long userId, Long id) {
        Notification notification = notificationMapper.selectById(id);
        if (notification == null) {
            throw new BusinessException(ErrorCode.NOTIFICATION_NOT_FOUND);
        }
        if (!notification.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.PERMISSION_DATA_SCOPE_DENIED);
        }
        if (notification.getReadStatus() == 0) {
            notification.setReadStatus(1);
            notification.setReadAt(LocalDateTime.now());
            notificationMapper.updateById(notification);
        }
    }

    /** 全部标记已读 */
    @Transactional
    public void markAllRead(Long userId) {
        notificationMapper.update(null,
                new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<Notification>()
                        .eq(Notification::getUserId, userId)
                        .eq(Notification::getReadStatus, 0)
                        .set(Notification::getReadStatus, 1)
                        .set(Notification::getReadAt, LocalDateTime.now()));
    }

    private NotificationView toView(Notification n) {
        return NotificationView.builder()
                .id(n.getId())
                .notificationType(n.getNotificationType())
                .title(n.getTitle())
                .content(n.getContent())
                .relatedType(n.getRelatedType())
                .relatedId(n.getRelatedId())
                .readStatus(n.getReadStatus())
                .readAt(n.getReadAt())
                .createdAt(n.getCreatedAt())
                .build();
    }
}
