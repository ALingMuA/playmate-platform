package com.gameplay.announcement.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.gameplay.announcement.domain.Announcement;
import com.gameplay.announcement.dto.AnnouncementRequest;
import com.gameplay.announcement.dto.AnnouncementView;
import com.gameplay.announcement.mapper.AnnouncementMapper;
import com.gameplay.common.exception.BusinessException;
import com.gameplay.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

/**
 * 公告领域服务（FR-A08 用户查看、FR-M13 管理）。
 *
 * <p>状态机：DRAFT → PUBLISHED → REVOKED；仅草稿可编辑/删除，仅已发布可撤回。</p>
 */
@Service
@RequiredArgsConstructor
public class AnnouncementService {

    private final AnnouncementMapper announcementMapper;

    /** 新增公告（草稿，FR-M13） */
    @Transactional
    public AnnouncementView create(Long adminId, AnnouncementRequest req) {
        Announcement announcement = new Announcement();
        announcement.setTitle(req.getTitle());
        announcement.setContent(req.getContent());
        announcement.setPublishStatus("DRAFT");
        announcement.setPublishedBy(0L);
        announcementMapper.insert(announcement);
        return toView(announcement);
    }

    /** 编辑公告（仅草稿，FR-M13） */
    @Transactional
    public AnnouncementView update(Long id, AnnouncementRequest req) {
        Announcement announcement = requireAnnouncement(id);
        if (!"DRAFT".equals(announcement.getPublishStatus())) {
            throw new BusinessException(ErrorCode.ANNOUNCEMENT_STATUS_INVALID, "仅草稿状态可编辑");
        }
        announcement.setTitle(req.getTitle());
        announcement.setContent(req.getContent());
        announcementMapper.updateById(announcement);
        return toView(announcement);
    }

    /** 发布公告（草稿 → 已发布，FR-M13） */
    @Transactional
    public AnnouncementView publish(Long id, Long adminId) {
        Announcement announcement = requireAnnouncement(id);
        if (!"DRAFT".equals(announcement.getPublishStatus())) {
            throw new BusinessException(ErrorCode.ANNOUNCEMENT_STATUS_INVALID, "仅草稿状态可发布");
        }
        announcement.setPublishStatus("PUBLISHED");
        announcement.setPublishedBy(adminId);
        announcement.setPublishedAt(LocalDateTime.now());
        announcementMapper.updateById(announcement);
        return toView(announcement);
    }

    /** 撤回公告（已发布 → 已撤回，FR-M13） */
    @Transactional
    public AnnouncementView revoke(Long id, Long adminId) {
        Announcement announcement = requireAnnouncement(id);
        if (!"PUBLISHED".equals(announcement.getPublishStatus())) {
            throw new BusinessException(ErrorCode.ANNOUNCEMENT_STATUS_INVALID, "仅已发布状态可撤回");
        }
        announcement.setPublishStatus("REVOKED");
        announcementMapper.updateById(announcement);
        return toView(announcement);
    }

    /** 删除公告（仅草稿，FR-M13） */
    @Transactional
    public void delete(Long id) {
        Announcement announcement = requireAnnouncement(id);
        if (!"DRAFT".equals(announcement.getPublishStatus())) {
            throw new BusinessException(ErrorCode.ANNOUNCEMENT_STATUS_INVALID, "仅草稿状态可删除");
        }
        announcementMapper.deleteById(id);
    }

    /** 管理端分页查询（全部状态） */
    public Page<AnnouncementView> adminPage(String publishStatus, long page, long size) {
        Page<Announcement> p = announcementMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<Announcement>()
                        .eq(StringUtils.hasText(publishStatus), Announcement::getPublishStatus, publishStatus)
                        .orderByDesc(Announcement::getId));
        return toViewPage(p);
    }

    /** 公开分页查询（FR-A08：仅已发布） */
    public Page<AnnouncementView> publicPage(long page, long size) {
        Page<Announcement> p = announcementMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<Announcement>()
                        .eq(Announcement::getPublishStatus, "PUBLISHED")
                        .orderByDesc(Announcement::getPublishedAt));
        return toViewPage(p);
    }

    /** 公开单条（FR-A08：仅已发布） */
    public AnnouncementView publicDetail(Long id) {
        Announcement announcement = requireAnnouncement(id);
        if (!"PUBLISHED".equals(announcement.getPublishStatus())) {
            throw new BusinessException(ErrorCode.ANNOUNCEMENT_NOT_FOUND);
        }
        return toView(announcement);
    }

    private Announcement requireAnnouncement(Long id) {
        Announcement announcement = announcementMapper.selectById(id);
        if (announcement == null) {
            throw new BusinessException(ErrorCode.ANNOUNCEMENT_NOT_FOUND);
        }
        return announcement;
    }

    private Page<AnnouncementView> toViewPage(Page<Announcement> p) {
        Page<AnnouncementView> vp = new Page<>(p.getCurrent(), p.getSize(), p.getTotal());
        vp.setRecords(p.getRecords().stream().map(this::toView).toList());
        return vp;
    }

    private AnnouncementView toView(Announcement a) {
        return AnnouncementView.builder()
                .id(a.getId())
                .title(a.getTitle())
                .content(a.getContent())
                .publishStatus(a.getPublishStatus())
                .publishedBy(a.getPublishedBy())
                .publishedAt(a.getPublishedAt())
                .createdAt(a.getCreatedAt())
                .updatedAt(a.getUpdatedAt())
                .build();
    }
}
