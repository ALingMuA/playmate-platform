package com.gameplay.coupon.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.gameplay.common.exception.BusinessException;
import com.gameplay.common.exception.ErrorCode;
import com.gameplay.companion.domain.CompanionService;
import com.gameplay.companion.mapper.CompanionServiceMapper;
import com.gameplay.coupon.domain.CouponTemplate;
import com.gameplay.coupon.domain.UserCoupon;
import com.gameplay.coupon.dto.*;
import com.gameplay.coupon.enums.CouponScopeType;
import com.gameplay.coupon.enums.CouponTemplateStatus;
import com.gameplay.coupon.enums.CouponType;
import com.gameplay.coupon.enums.UserCouponStatus;
import com.gameplay.coupon.mapper.CouponTemplateMapper;
import com.gameplay.coupon.mapper.UserCouponMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 优惠券应用与领域服务：生命周期、并发领券、核销与释放。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CouponService {

    private final CouponTemplateMapper templateMapper;
    private final UserCouponMapper userCouponMapper;
    private final CompanionServiceMapper companionServiceMapper;

    // ==================== 用户端 ====================

    /**
     * 用户领取优惠券（防超发、防超领）。
     */
    @Transactional
    public UserCouponView claim(Long userId, Long templateId) {
        CouponTemplate template = templateMapper.selectById(templateId);
        if (template == null) {
            throw new BusinessException(ErrorCode.COUPON_TEMPLATE_NOT_FOUND);
        }
        if (!CouponTemplateStatus.PUBLISHED.name().equals(template.getPublishStatus())) {
            throw new BusinessException(ErrorCode.COUPON_NOT_PUBLISHED);
        }
        LocalDateTime now = LocalDateTime.now();
        if (template.getValidFrom() != null && now.isBefore(template.getValidFrom())) {
            throw new BusinessException(ErrorCode.COUPON_EXPIRED, "优惠券活动尚未开始");
        }
        if (template.getValidUntil() != null && now.isAfter(template.getValidUntil())) {
            throw new BusinessException(ErrorCode.COUPON_EXPIRED, "优惠券已过期");
        }

        // 校验每人限领
        int perLimit = template.getPerUserLimit() == null || template.getPerUserLimit() <= 0 ? 1 : template.getPerUserLimit();
        Long claimedCount = userCouponMapper.selectCount(new LambdaQueryWrapper<UserCoupon>()
                .eq(UserCoupon::getUserId, userId)
                .eq(UserCoupon::getTemplateId, templateId));
        if (claimedCount != null && claimedCount >= perLimit) {
            throw new BusinessException(ErrorCode.COUPON_CLAIM_LIMIT_EXCEEDED);
        }

        // 原子递增库存
        int updated = templateMapper.increaseClaimedQuantity(templateId, template.getTotalQuantity());
        if (updated <= 0) {
            throw new BusinessException(ErrorCode.COUPON_OUT_OF_STOCK);
        }

        // 插入用户优惠券
        UserCoupon userCoupon = new UserCoupon();
        userCoupon.setTemplateId(templateId);
        userCoupon.setUserId(userId);
        userCoupon.setCouponCode(generateCouponCode());
        userCoupon.setStatus(UserCouponStatus.AVAILABLE.name());
        userCoupon.setValidFrom(template.getValidFrom());
        userCoupon.setValidUntil(template.getValidUntil());
        userCoupon.setClaimedAt(now);
        userCouponMapper.insert(userCoupon);

        return toUserCouponView(userCoupon, template, true, 0L, null);
    }

    /**
     * 查询我的优惠券列表（分页，支持状态筛选）。
     */
    public Page<UserCouponView> listMine(Long userId, String statusFilter, long page, long size) {
        LocalDateTime now = LocalDateTime.now();
        LambdaQueryWrapper<UserCoupon> query = new LambdaQueryWrapper<UserCoupon>()
                .eq(UserCoupon::getUserId, userId)
                .orderByDesc(UserCoupon::getClaimedAt);

        if (statusFilter != null && !statusFilter.isBlank()) {
            if (UserCouponStatus.EXPIRED.name().equalsIgnoreCase(statusFilter)) {
                // 显式过期或状态为 AVAILABLE 但已过有效期
                query.and(q -> q.eq(UserCoupon::getStatus, UserCouponStatus.EXPIRED.name())
                        .or(o -> o.eq(UserCoupon::getStatus, UserCouponStatus.AVAILABLE.name())
                                .lt(UserCoupon::getValidUntil, now)));
            } else if (UserCouponStatus.AVAILABLE.name().equalsIgnoreCase(statusFilter)) {
                query.eq(UserCoupon::getStatus, UserCouponStatus.AVAILABLE.name())
                        .ge(UserCoupon::getValidUntil, now);
            } else {
                query.eq(UserCoupon::getStatus, statusFilter.toUpperCase());
            }
        }

        Page<UserCoupon> pageParam = new Page<>(page, size);
        Page<UserCoupon> paged = userCouponMapper.selectPage(pageParam, query);

        List<UserCouponView> records = paged.getRecords().stream().map(uc -> {
            CouponTemplate template = templateMapper.selectById(uc.getTemplateId());
            boolean expired = uc.getValidUntil() != null && uc.getValidUntil().isBefore(now);
            String displayStatus = uc.getStatus();
            if (UserCouponStatus.AVAILABLE.name().equals(uc.getStatus()) && expired) {
                displayStatus = UserCouponStatus.EXPIRED.name();
            }
            UserCouponView view = toUserCouponView(uc, template, false, 0L, null);
            view.setStatus(displayStatus);
            return view;
        }).toList();

        Page<UserCouponView> result = new Page<>(paged.getCurrent(), paged.getSize(), paged.getTotal());
        result.setRecords(records);
        return result;
    }

    /**
     * 预约时查询当前用户针对特定服务的可用优惠券列表。
     */
    public List<UserCouponView> listAvailableForOrder(Long userId, Long companionServiceId, Integer durationMinutes) {
        CompanionService service = companionServiceMapper.selectById(companionServiceId);
        if (service == null || durationMinutes == null || durationMinutes <= 0) {
            return Collections.emptyList();
        }
        long originalAmount = calculateOriginalAmount(service.getPriceCents(), durationMinutes);
        LocalDateTime now = LocalDateTime.now();

        // 查询该用户所有处于 AVAILABLE 且未过期的优惠券
        List<UserCoupon> list = userCouponMapper.selectList(new LambdaQueryWrapper<UserCoupon>()
                .eq(UserCoupon::getUserId, userId)
                .eq(UserCoupon::getStatus, UserCouponStatus.AVAILABLE.name())
                .ge(UserCoupon::getValidUntil, now)
                .le(UserCoupon::getValidFrom, now));

        List<UserCouponView> views = new ArrayList<>();
        for (UserCoupon uc : list) {
            CouponTemplate template = templateMapper.selectById(uc.getTemplateId());
            if (template == null) continue;

            boolean scopeMatch = isScopeMatched(template, service);
            boolean thresholdMatch = originalAmount >= (template.getThresholdCents() == null ? 0 : template.getThresholdCents());

            boolean applicable = scopeMatch && thresholdMatch;
            long discount = applicable ? calculateDiscountCents(template, originalAmount) : 0L;
            String reason = null;
            if (!scopeMatch) {
                reason = "该券不适用此服务或游戏";
            } else if (!thresholdMatch) {
                reason = String.format("未达满减门槛(需满%.2f元)", (template.getThresholdCents() / 100.0));
            }
            views.add(toUserCouponView(uc, template, applicable, discount, reason));
        }

        // 排序：可用在先；若都可用按预计优惠降序；不可用排在最后
        views.sort((a, b) -> {
            if (Boolean.TRUE.equals(a.getIsApplicable()) && !Boolean.TRUE.equals(b.getIsApplicable())) return -1;
            if (!Boolean.TRUE.equals(a.getIsApplicable()) && Boolean.TRUE.equals(b.getIsApplicable())) return 1;
            return Long.compare(b.getExpectedDiscountCents() == null ? 0 : b.getExpectedDiscountCents(),
                    a.getExpectedDiscountCents() == null ? 0 : a.getExpectedDiscountCents());
        });

        return views;
    }

    /**
     * 优惠金额预览。
     */
    public CouponPreviewResult preview(Long userId, Long companionServiceId, Integer durationMinutes, Long userCouponId) {
        CompanionService service = companionServiceMapper.selectById(companionServiceId);
        if (service == null) {
            throw new BusinessException(ErrorCode.SERVICE_NOT_AVAILABLE);
        }
        long originalAmount = calculateOriginalAmount(service.getPriceCents(), durationMinutes);

        if (userCouponId == null || userCouponId <= 0) {
            return CouponPreviewResult.builder()
                    .originalAmountCents(originalAmount)
                    .discountAmountCents(0L)
                    .payableAmountCents(originalAmount)
                    .couponId(null)
                    .couponName(null)
                    .message("未选择优惠券")
                    .available(true)
                    .build();
        }

        UserCoupon userCoupon = userCouponMapper.selectById(userCouponId);
        if (userCoupon == null || !userCoupon.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.USER_COUPON_NOT_FOUND);
        }
        CouponTemplate template = templateMapper.selectById(userCoupon.getTemplateId());
        if (template == null) {
            throw new BusinessException(ErrorCode.COUPON_TEMPLATE_NOT_FOUND);
        }

        LocalDateTime now = LocalDateTime.now();
        if (!UserCouponStatus.AVAILABLE.name().equals(userCoupon.getStatus())) {
            throw new BusinessException(ErrorCode.USER_COUPON_NOT_AVAILABLE, "优惠券已被锁定或已使用");
        }
        if (userCoupon.getValidUntil() != null && now.isAfter(userCoupon.getValidUntil())) {
            throw new BusinessException(ErrorCode.COUPON_EXPIRED);
        }
        if (!isScopeMatched(template, service)) {
            throw new BusinessException(ErrorCode.COUPON_SCOPE_MISMATCH);
        }
        if (originalAmount < (template.getThresholdCents() == null ? 0 : template.getThresholdCents())) {
            throw new BusinessException(ErrorCode.COUPON_THRESHOLD_NOT_MET);
        }

        long discount = calculateDiscountCents(template, originalAmount);
        long payable = Math.max(0, originalAmount - discount);

        return CouponPreviewResult.builder()
                .originalAmountCents(originalAmount)
                .discountAmountCents(discount)
                .payableAmountCents(payable)
                .couponId(userCoupon.getId())
                .couponName(template.getName())
                .message(buildDiscountDescription(template, discount))
                .available(true)
                .build();
    }

    /**
     * 锁定优惠券供订单使用（在 OrderService.createOrder 事务中调用）。
     */
    @Transactional
    public LockedCouponInfo lockForOrder(Long userId, Long userCouponId, Long orderId,
                                         long originalAmount, CompanionService service) {
        UserCoupon uc = userCouponMapper.selectByIdForUpdate(userCouponId);
        if (uc == null || !uc.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.USER_COUPON_NOT_FOUND);
        }
        if (!UserCouponStatus.AVAILABLE.name().equals(uc.getStatus())) {
            throw new BusinessException(ErrorCode.USER_COUPON_NOT_AVAILABLE);
        }
        LocalDateTime now = LocalDateTime.now();
        if (uc.getValidUntil() != null && now.isAfter(uc.getValidUntil())) {
            throw new BusinessException(ErrorCode.COUPON_EXPIRED);
        }
        CouponTemplate template = templateMapper.selectById(uc.getTemplateId());
        if (template == null) {
            throw new BusinessException(ErrorCode.COUPON_TEMPLATE_NOT_FOUND);
        }
        if (!isScopeMatched(template, service)) {
            throw new BusinessException(ErrorCode.COUPON_SCOPE_MISMATCH);
        }
        if (originalAmount < (template.getThresholdCents() == null ? 0 : template.getThresholdCents())) {
            throw new BusinessException(ErrorCode.COUPON_THRESHOLD_NOT_MET);
        }

        long discount = calculateDiscountCents(template, originalAmount);

        uc.setStatus(UserCouponStatus.LOCKED.name());
        uc.setLockedOrderId(orderId);
        uc.setLockedAt(now);
        userCouponMapper.updateById(uc);

        return new LockedCouponInfo(uc.getId(), template.getName(), discount);
    }

    /**
     * 支付完成核销优惠券（在 OrderService.pay 事务中调用）。
     */
    @Transactional
    public void useForOrder(Long orderId) {
        UserCoupon uc = userCouponMapper.selectByLockedOrderIdForUpdate(orderId);
        if (uc == null) return;
        if (UserCouponStatus.LOCKED.name().equals(uc.getStatus())) {
            uc.setStatus(UserCouponStatus.USED.name());
            uc.setUsedOrderId(orderId);
            uc.setUsedAt(LocalDateTime.now());
            userCouponMapper.updateById(uc);
        }
    }

    /**
     * 未支付取消或超时关单释放优惠券（在 OrderService.cancel / closeExpiredPendingPayment 事务中调用）。
     */
    @Transactional
    public void releaseFromOrder(Long orderId) {
        UserCoupon uc = userCouponMapper.selectByLockedOrderIdForUpdate(orderId);
        if (uc == null) return;
        if (UserCouponStatus.LOCKED.name().equals(uc.getStatus())) {
            LocalDateTime now = LocalDateTime.now();
            String nextStatus = (uc.getValidUntil() != null && now.isAfter(uc.getValidUntil()))
                    ? UserCouponStatus.EXPIRED.name()
                    : UserCouponStatus.AVAILABLE.name();
            userCouponMapper.releaseLock(uc.getId(), nextStatus);
        }
    }

    // ==================== 管理端 ====================

    public Page<CouponTemplateView> pageTemplates(String status, long page, long size) {
        LambdaQueryWrapper<CouponTemplate> query = new LambdaQueryWrapper<CouponTemplate>()
                .orderByDesc(CouponTemplate::getId);
        if (status != null && !status.isBlank()) {
            query.eq(CouponTemplate::getPublishStatus, status.toUpperCase());
        }
        Page<CouponTemplate> paged = templateMapper.selectPage(new Page<>(page, size), query);
        List<CouponTemplateView> records = paged.getRecords().stream()
                .map(this::toTemplateView)
                .toList();
        Page<CouponTemplateView> result = new Page<>(paged.getCurrent(), paged.getSize(), paged.getTotal());
        result.setRecords(records);
        return result;
    }

    @Transactional
    public CouponTemplateView createTemplate(Long adminId, CouponTemplateRequest req) {
        validateTemplateRequest(req);
        CouponTemplate template = new CouponTemplate();
        copyProperties(req, template);
        template.setPublishStatus(CouponTemplateStatus.DRAFT.name());
        template.setClaimedQuantity(0);
        template.setCreatedBy(adminId);
        template.setPublishedBy(0L);
        templateMapper.insert(template);
        return toTemplateView(template);
    }

    @Transactional
    public CouponTemplateView updateTemplate(Long id, CouponTemplateRequest req) {
        CouponTemplate template = templateMapper.selectByIdForUpdate(id);
        if (template == null) {
            throw new BusinessException(ErrorCode.COUPON_TEMPLATE_NOT_FOUND);
        }
        if (!CouponTemplateStatus.DRAFT.name().equals(template.getPublishStatus())) {
            throw new BusinessException(ErrorCode.COUPON_TEMPLATE_STATUS_INVALID, "仅草稿状态的优惠券模板允许编辑");
        }
        validateTemplateRequest(req);
        copyProperties(req, template);
        templateMapper.updateById(template);
        return toTemplateView(template);
    }

    @Transactional
    public CouponTemplateView publishTemplate(Long adminId, Long id) {
        CouponTemplate template = templateMapper.selectByIdForUpdate(id);
        if (template == null) {
            throw new BusinessException(ErrorCode.COUPON_TEMPLATE_NOT_FOUND);
        }
        if (!CouponTemplateStatus.DRAFT.name().equals(template.getPublishStatus())) {
            throw new BusinessException(ErrorCode.COUPON_TEMPLATE_STATUS_INVALID, "仅草稿状态的模板允许发布");
        }
        template.setPublishStatus(CouponTemplateStatus.PUBLISHED.name());
        template.setPublishedBy(adminId);
        template.setPublishedAt(LocalDateTime.now());
        templateMapper.updateById(template);
        return toTemplateView(template);
    }

    @Transactional
    public CouponTemplateView offlineTemplate(Long id) {
        CouponTemplate template = templateMapper.selectByIdForUpdate(id);
        if (template == null) {
            throw new BusinessException(ErrorCode.COUPON_TEMPLATE_NOT_FOUND);
        }
        if (!CouponTemplateStatus.PUBLISHED.name().equals(template.getPublishStatus())) {
            throw new BusinessException(ErrorCode.COUPON_TEMPLATE_STATUS_INVALID, "仅已发布的模板允许下线");
        }
        template.setPublishStatus(CouponTemplateStatus.OFFLINE.name());
        templateMapper.updateById(template);
        return toTemplateView(template);
    }

    @Transactional
    public void deleteTemplate(Long id) {
        CouponTemplate template = templateMapper.selectByIdForUpdate(id);
        if (template == null) {
            throw new BusinessException(ErrorCode.COUPON_TEMPLATE_NOT_FOUND);
        }
        if (!CouponTemplateStatus.DRAFT.name().equals(template.getPublishStatus())) {
            throw new BusinessException(ErrorCode.COUPON_TEMPLATE_STATUS_INVALID, "仅草稿状态的模板允许删除");
        }
        templateMapper.deleteById(id);
    }

    // ==================== 辅助方法 ====================

    public long calculateOriginalAmount(long priceCents, int durationMinutes) {
        long amount = priceCents * durationMinutes / 60L;
        if (priceCents * durationMinutes % 60L != 0) {
            amount += 1;
        }
        return amount;
    }

    public long calculateDiscountCents(CouponTemplate template, long originalAmount) {
        if (CouponType.FIXED.name().equalsIgnoreCase(template.getCouponType())) {
            long discount = template.getDiscountCents() == null ? 0 : template.getDiscountCents();
            return Math.min(discount, Math.max(0, originalAmount - 1));
        } else if (CouponType.DISCOUNT.name().equalsIgnoreCase(template.getCouponType())) {
            int rateBp = template.getDiscountRateBp() == null ? 10000 : template.getDiscountRateBp();
            long discount = originalAmount * (10000 - rateBp) / 10000;
            if (template.getMaxDiscountCents() != null && template.getMaxDiscountCents() > 0) {
                discount = Math.min(discount, template.getMaxDiscountCents());
            }
            return Math.min(discount, Math.max(0, originalAmount - 1));
        }
        return 0L;
    }

    public boolean isScopeMatched(CouponTemplate template, CompanionService service) {
        if (service == null) return false;
        String scopeType = template.getScopeType() == null ? CouponScopeType.ALL.name() : template.getScopeType();
        if (CouponScopeType.ALL.name().equalsIgnoreCase(scopeType)) {
            return true;
        } else if (CouponScopeType.GAME.name().equalsIgnoreCase(scopeType)) {
            return service.getGameId() != null && service.getGameId().equals(template.getScopeId());
        } else if (CouponScopeType.SERVICE.name().equalsIgnoreCase(scopeType)) {
            return service.getId() != null && service.getId().equals(template.getScopeId());
        } else if (CouponScopeType.COMPANION.name().equalsIgnoreCase(scopeType)) {
            return service.getCompanionUserId() != null && service.getCompanionUserId().equals(template.getScopeId());
        }
        return false;
    }

    private String generateCouponCode() {
        String time = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        int rand = ThreadLocalRandom.current().nextInt(1000, 9999);
        return "CPN" + time + rand;
    }

    private void validateTemplateRequest(CouponTemplateRequest req) {
        if (req.getValidFrom().isAfter(req.getValidUntil())) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "有效开始时间不能晚于结束时间");
        }
        if (CouponType.FIXED.name().equalsIgnoreCase(req.getCouponType())) {
            if (req.getDiscountCents() == null || req.getDiscountCents() <= 0) {
                throw new BusinessException(ErrorCode.VALIDATION_FAILED, "满减券优惠金额必须大于0");
            }
        } else if (CouponType.DISCOUNT.name().equalsIgnoreCase(req.getCouponType())) {
            if (req.getDiscountRateBp() == null || req.getDiscountRateBp() <= 0 || req.getDiscountRateBp() >= 10000) {
                throw new BusinessException(ErrorCode.VALIDATION_FAILED, "折扣券折扣基点必须在 1~9999 之间（例如 8500 为 8.5 折）");
            }
        }
    }

    private void copyProperties(CouponTemplateRequest req, CouponTemplate target) {
        target.setName(req.getName());
        target.setCouponType(req.getCouponType().toUpperCase());
        target.setThresholdCents(req.getThresholdCents() == null ? 0L : req.getThresholdCents());
        target.setDiscountCents(req.getDiscountCents() == null ? 0L : req.getDiscountCents());
        target.setDiscountRateBp(req.getDiscountRateBp() == null ? 10000 : req.getDiscountRateBp());
        target.setMaxDiscountCents(req.getMaxDiscountCents() == null ? 0L : req.getMaxDiscountCents());
        target.setScopeType(req.getScopeType() == null ? "ALL" : req.getScopeType().toUpperCase());
        target.setScopeId(req.getScopeId() == null ? 0L : req.getScopeId());
        target.setActivityId(req.getActivityId());
        target.setTotalQuantity(req.getTotalQuantity() == null ? 0 : req.getTotalQuantity());
        target.setPerUserLimit(req.getPerUserLimit() == null || req.getPerUserLimit() <= 0 ? 1 : req.getPerUserLimit());
        target.setValidFrom(req.getValidFrom());
        target.setValidUntil(req.getValidUntil());
    }

    private String buildDiscountDescription(CouponTemplate template, long actualDiscount) {
        if (CouponType.FIXED.name().equalsIgnoreCase(template.getCouponType())) {
            long thresh = template.getThresholdCents() == null ? 0 : template.getThresholdCents();
            return thresh <= 0 ? String.format("立减 ¥%.2f", actualDiscount / 100.0)
                    : String.format("满 ¥%.2f 减 ¥%.2f", thresh / 100.0, actualDiscount / 100.0);
        } else {
            double rate = (template.getDiscountRateBp() == null ? 10000 : template.getDiscountRateBp()) / 1000.0;
            return String.format("%.1f折特惠，立减 ¥%.2f", rate, actualDiscount / 100.0);
        }
    }

    private UserCouponView toUserCouponView(UserCoupon uc, CouponTemplate template,
                                            boolean isApplicable, Long expectedDiscount, String reason) {
        String name = template == null ? "优惠券" : template.getName();
        String cType = template == null ? "FIXED" : template.getCouponType();
        Long thresh = template == null ? 0L : template.getThresholdCents();
        Long disc = template == null ? 0L : template.getDiscountCents();
        Integer rateBp = template == null ? 10000 : template.getDiscountRateBp();
        Long maxDisc = template == null ? 0L : template.getMaxDiscountCents();
        String sType = template == null ? "ALL" : template.getScopeType();
        Long sId = template == null ? 0L : template.getScopeId();

        return UserCouponView.builder()
                .id(uc.getId())
                .templateId(uc.getTemplateId())
                .templateName(name)
                .couponCode(uc.getCouponCode())
                .couponType(cType)
                .thresholdCents(thresh)
                .discountCents(disc)
                .discountRateBp(rateBp)
                .maxDiscountCents(maxDisc)
                .scopeType(sType)
                .scopeId(sId)
                .status(uc.getStatus())
                .validFrom(uc.getValidFrom())
                .validUntil(uc.getValidUntil())
                .lockedOrderId(uc.getLockedOrderId())
                .usedOrderId(uc.getUsedOrderId())
                .claimedAt(uc.getClaimedAt())
                .usedAt(uc.getUsedAt())
                .isApplicable(isApplicable)
                .expectedDiscountCents(expectedDiscount)
                .notApplicableReason(reason)
                .build();
    }

    private CouponTemplateView toTemplateView(CouponTemplate t) {
        Integer total = t.getTotalQuantity();
        Integer claimed = t.getClaimedQuantity() == null ? 0 : t.getClaimedQuantity();
        Integer remaining = (total == null || total <= 0) ? null : Math.max(0, total - claimed);

        return CouponTemplateView.builder()
                .id(t.getId())
                .name(t.getName())
                .couponType(t.getCouponType())
                .thresholdCents(t.getThresholdCents())
                .discountCents(t.getDiscountCents())
                .discountRateBp(t.getDiscountRateBp())
                .maxDiscountCents(t.getMaxDiscountCents())
                .scopeType(t.getScopeType())
                .scopeId(t.getScopeId())
                .activityId(t.getActivityId())
                .totalQuantity(total)
                .claimedQuantity(claimed)
                .remainingQuantity(remaining)
                .perUserLimit(t.getPerUserLimit())
                .validFrom(t.getValidFrom())
                .validUntil(t.getValidUntil())
                .publishStatus(t.getPublishStatus())
                .createdBy(t.getCreatedBy())
                .publishedBy(t.getPublishedBy())
                .publishedAt(t.getPublishedAt())
                .createdAt(t.getCreatedAt())
                .build();
    }

    public record LockedCouponInfo(Long userCouponId, String couponName, Long discountCents) {}
}
