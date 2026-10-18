package com.gameplay.activity.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.gameplay.activity.domain.PlatformActivity;
import com.gameplay.activity.dto.ActivityRequest;
import com.gameplay.activity.dto.ActivityView;
import com.gameplay.activity.enums.ActivityStatus;
import com.gameplay.activity.mapper.PlatformActivityMapper;
import com.gameplay.common.exception.BusinessException;
import com.gameplay.common.exception.ErrorCode;
import com.gameplay.coupon.domain.CouponTemplate;
import com.gameplay.coupon.mapper.CouponTemplateMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class PlatformActivityService {

    private final PlatformActivityMapper activityMapper;
    private final CouponTemplateMapper couponTemplateMapper;

    // ==================== 用户端公开接口 ====================

    /**
     * 查询前台展示的活动列表（已发布、生效期内、按排序权重升序）。
     */
    public List<ActivityView> listPublicActivities() {
        LocalDateTime now = LocalDateTime.now();
        LambdaQueryWrapper<PlatformActivity> query = new LambdaQueryWrapper<PlatformActivity>()
                .eq(PlatformActivity::getStatus, ActivityStatus.PUBLISHED.name())
                .and(w -> w.isNull(PlatformActivity::getStartTime).or().le(PlatformActivity::getStartTime, now))
                .and(w -> w.isNull(PlatformActivity::getEndTime).or().ge(PlatformActivity::getEndTime, now))
                .orderByAsc(PlatformActivity::getSortOrder)
                .orderByAsc(PlatformActivity::getId);

        List<PlatformActivity> list = activityMapper.selectList(query);
        return list.stream().map(this::toView).toList();
    }

    /**
     * 获取活动详情。
     */
    public ActivityView getActivityDetail(Long id) {
        PlatformActivity activity = activityMapper.selectById(id);
        if (activity == null || !ActivityStatus.PUBLISHED.name().equals(activity.getStatus())) {
            throw new BusinessException(ErrorCode.ACTIVITY_NOT_FOUND);
        }
        return toView(activity);
    }

    // ==================== 管理端接口 ====================

    public Page<ActivityView> pageActivities(String status, long page, long size) {
        LambdaQueryWrapper<PlatformActivity> query = new LambdaQueryWrapper<PlatformActivity>()
                .orderByAsc(PlatformActivity::getSortOrder)
                .orderByDesc(PlatformActivity::getId);
        if (status != null && !status.isBlank()) {
            query.eq(PlatformActivity::getStatus, status.toUpperCase());
        }
        Page<PlatformActivity> paged = activityMapper.selectPage(new Page<>(page, size), query);
        Page<ActivityView> result = new Page<>(paged.getCurrent(), paged.getSize(), paged.getTotal());
        result.setRecords(paged.getRecords().stream().map(this::toView).toList());
        return result;
    }

    @Transactional
    public ActivityView createActivity(Long adminId, ActivityRequest req) {
        PlatformActivity activity = new PlatformActivity();
        copyProperties(req, activity);
        activity.setStatus(ActivityStatus.DRAFT.name());
        activity.setCreatedBy(adminId);
        activityMapper.insert(activity);
        return toView(activity);
    }

    @Transactional
    public ActivityView updateActivity(Long id, ActivityRequest req) {
        PlatformActivity activity = activityMapper.selectById(id);
        if (activity == null) {
            throw new BusinessException(ErrorCode.ACTIVITY_NOT_FOUND);
        }
        copyProperties(req, activity);
        activityMapper.updateById(activity);
        return toView(activity);
    }

    @Transactional
    public ActivityView publishActivity(Long id) {
        PlatformActivity activity = activityMapper.selectById(id);
        if (activity == null) {
            throw new BusinessException(ErrorCode.ACTIVITY_NOT_FOUND);
        }
        activity.setStatus(ActivityStatus.PUBLISHED.name());
        activityMapper.updateById(activity);
        return toView(activity);
    }

    @Transactional
    public ActivityView offlineActivity(Long id) {
        PlatformActivity activity = activityMapper.selectById(id);
        if (activity == null) {
            throw new BusinessException(ErrorCode.ACTIVITY_NOT_FOUND);
        }
        activity.setStatus(ActivityStatus.OFFLINE.name());
        activityMapper.updateById(activity);
        return toView(activity);
    }

    @Transactional
    public void deleteActivity(Long id) {
        PlatformActivity activity = activityMapper.selectById(id);
        if (activity == null) {
            throw new BusinessException(ErrorCode.ACTIVITY_NOT_FOUND);
        }
        if (ActivityStatus.PUBLISHED.name().equals(activity.getStatus())) {
            throw new BusinessException(ErrorCode.ACTIVITY_STATUS_INVALID, "已发布的活动不能直接删除，请先下线");
        }
        activityMapper.deleteById(id);
    }

    // ==================== 辅助映射 ====================

    private void copyProperties(ActivityRequest req, PlatformActivity target) {
        target.setTitle(req.getTitle());
        target.setSubtitle(req.getSubtitle() == null ? "" : req.getSubtitle());
        target.setDescription(req.getDescription() == null ? "" : req.getDescription());
        target.setBannerImage(req.getBannerImage() == null ? "" : req.getBannerImage());
        target.setBgGradient(req.getBgGradient() == null ? "linear-gradient(135deg, #667eea 0%, #764ba2 100%)" : req.getBgGradient());
        target.setIcon(req.getIcon() == null ? "🎁" : req.getIcon());
        target.setTag(req.getTag() == null ? "" : req.getTag());
        target.setTagColor(req.getTagColor() == null ? "#ff6b6b" : req.getTagColor());
        target.setActionType(req.getActionType() == null ? "COUPON_CLAIM" : req.getActionType());
        target.setActionRoute(req.getActionRoute() == null ? "" : req.getActionRoute());
        target.setCouponTemplateId(req.getCouponTemplateId());
        target.setSortOrder(req.getSortOrder() == null ? 0 : req.getSortOrder());
        target.setStartTime(req.getStartTime());
        target.setEndTime(req.getEndTime());
    }

    private ActivityView toView(PlatformActivity a) {
        String templateName = null;
        if (a.getCouponTemplateId() != null && a.getCouponTemplateId() > 0) {
            CouponTemplate template = couponTemplateMapper.selectById(a.getCouponTemplateId());
            if (template != null) {
                templateName = template.getName();
            }
        }
        return ActivityView.builder()
                .id(a.getId())
                .title(a.getTitle())
                .subtitle(a.getSubtitle())
                .description(a.getDescription())
                .bannerImage(a.getBannerImage())
                .bgGradient(a.getBgGradient())
                .icon(a.getIcon())
                .tag(a.getTag())
                .tagColor(a.getTagColor())
                .actionType(a.getActionType())
                .actionRoute(a.getActionRoute())
                .couponTemplateId(a.getCouponTemplateId())
                .couponTemplateName(templateName)
                .sortOrder(a.getSortOrder())
                .status(a.getStatus())
                .startTime(a.getStartTime())
                .endTime(a.getEndTime())
                .createdBy(a.getCreatedBy())
                .createdAt(a.getCreatedAt())
                .build();
    }
}
