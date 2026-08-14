package com.gameplay.review.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gameplay.auth.entity.User;
import com.gameplay.auth.mapper.UserMapper;
import com.gameplay.common.enums.OrderStatus;
import com.gameplay.common.exception.BusinessException;
import com.gameplay.common.exception.ErrorCode;
import com.gameplay.companion.domain.CompanionProfile;
import com.gameplay.companion.mapper.CompanionProfileMapper;
import com.gameplay.order.domain.PlayOrder;
import com.gameplay.order.mapper.PlayOrderMapper;
import com.gameplay.review.domain.Review;
import com.gameplay.review.dto.ReviewCreateRequest;
import com.gameplay.review.dto.ReviewView;
import com.gameplay.review.enums.ReviewDisplayStatus;
import com.gameplay.review.mapper.ReviewMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * 评价领域服务（FR-U15/U16、FR-M08）。
 *
 * <p>规则：仅本人已完成的订单可评价；每个订单唯一一条评价（uk_review_order 兜底）；
 * 评分统计只纳入 VISIBLE 评价，屏蔽/恢复后自动重算陪玩师评分。</p>
 */
@Service
@RequiredArgsConstructor
public class ReviewService {

    private final ReviewMapper reviewMapper;
    private final PlayOrderMapper orderMapper;
    private final UserMapper userMapper;
    private final CompanionProfileMapper companionProfileMapper;
    private final ObjectMapper objectMapper;

    /** 提交评价（FR-U15） */
    @Transactional
    public ReviewView submitReview(Long userId, Long orderId, ReviewCreateRequest req) {
        PlayOrder order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException(ErrorCode.ORDER_NOT_FOUND);
        }
        if (!order.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.PERMISSION_DATA_SCOPE_DENIED);
        }
        if (order.status() != OrderStatus.COMPLETED) {
            throw new BusinessException(ErrorCode.REVIEW_ORDER_NOT_ELIGIBLE,
                    "只有本人已完成的订单可以评价");
        }
        // 每个订单只能评价一次（唯一索引兜底）
        long exists = reviewMapper.selectCount(new LambdaQueryWrapper<Review>()
                .eq(Review::getOrderId, orderId));
        if (exists > 0) {
            throw new BusinessException(ErrorCode.REVIEW_ALREADY_EXISTS);
        }

        Review review = new Review();
        review.setOrderId(orderId);
        review.setUserId(userId);
        review.setCompanionUserId(order.getCompanionUserId());
        review.setScore(req.getScore());
        review.setTagJson(toJson(req.getTags() == null ? List.of() : req.getTags()));
        review.setContent(req.getContent());
        review.setDisplayStatus(ReviewDisplayStatus.VISIBLE.name());
        try {
            reviewMapper.insert(review);
        } catch (DuplicateKeyException e) {
            throw new BusinessException(ErrorCode.REVIEW_ALREADY_EXISTS);
        }
        recalcCompanionRating(order.getCompanionUserId());
        return toView(review);
    }

    /** 陪玩师公开评价分页（FR-U16）：仅 VISIBLE，含评分统计 */
    public Page<ReviewView> listByCompanion(Long companionUserId, long page, long size) {
        Page<Review> p = reviewMapper.selectPage(
                new Page<>(page, size),
                new LambdaQueryWrapper<Review>()
                        .eq(Review::getCompanionUserId, companionUserId)
                        .eq(Review::getDisplayStatus, ReviewDisplayStatus.VISIBLE.name())
                        .orderByDesc(Review::getId));
        return toViewPage(p);
    }

    /** 我的评价分页（含已屏蔽，FR-U15 个人中心） */
    public Page<ReviewView> listMine(Long userId, long page, long size) {
        Page<Review> p = reviewMapper.selectPage(
                new Page<>(page, size),
                new LambdaQueryWrapper<Review>()
                        .eq(Review::getUserId, userId)
                        .orderByDesc(Review::getId));
        return toViewPage(p);
    }

    /** 某订单的评价（下单人、陪玩师或管理员可查看，FR-U15 评价状态） */
    public ReviewView getByOrder(Long orderId, Long operatorId, List<String> roles) {
        Review review = reviewMapper.selectOne(new LambdaQueryWrapper<Review>()
                .eq(Review::getOrderId, orderId));
        if (review == null) {
            return null;
        }
        PlayOrder order = orderMapper.selectById(orderId);
        boolean isAdmin = roles != null && roles.contains("ADMIN");
        if (!isAdmin && !order.getUserId().equals(operatorId)
                && !order.getCompanionUserId().equals(operatorId)) {
            throw new BusinessException(ErrorCode.PERMISSION_DATA_SCOPE_DENIED);
        }
        return toView(review);
    }

    // ==================== 管理端（FR-M08） ====================

    /** 评价分页查询：可按内容关键词、评分、展示状态过滤 */
    public Page<ReviewView> adminPage(String keyword, Integer score, String displayStatus,
                                      long page, long size) {
        LambdaQueryWrapper<Review> wrapper = new LambdaQueryWrapper<Review>()
                .orderByDesc(Review::getId);
        if (keyword != null && !keyword.isBlank()) {
            wrapper.like(Review::getContent, keyword.trim());
        }
        if (score != null) {
            wrapper.eq(Review::getScore, score);
        }
        if (displayStatus != null && !displayStatus.isBlank()) {
            wrapper.eq(Review::getDisplayStatus, displayStatus);
        }
        return toViewPage(reviewMapper.selectPage(new Page<>(page, size), wrapper));
    }

    /** 屏蔽/恢复评价（FR-M08）：变更后重算陪玩师评分 */
    @Transactional
    public ReviewView adminSetDisplayStatus(Long reviewId, String displayStatus, Long adminId) {
        if (displayStatus == null
                || (!ReviewDisplayStatus.VISIBLE.name().equals(displayStatus)
                && !ReviewDisplayStatus.HIDDEN.name().equals(displayStatus))) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "展示状态不合法");
        }
        Review review = reviewMapper.selectById(reviewId);
        if (review == null) {
            throw new BusinessException(ErrorCode.REVIEW_NOT_FOUND);
        }
        if (!displayStatus.equals(review.getDisplayStatus())) {
            review.setDisplayStatus(displayStatus);
            reviewMapper.updateById(review);
            recalcCompanionRating(review.getCompanionUserId());
        }
        return toView(review);
    }

    // ==================== 内部 ====================

    /** 重算陪玩师有效评价评分（仅统计 VISIBLE，FR-U16） */
    private void recalcCompanionRating(Long companionUserId) {
        ReviewMapper.ScoreAggregate agg = reviewMapper.aggregateVisibleByCompanion(companionUserId);
        CompanionProfile profile = companionProfileMapper.selectOne(
                new LambdaQueryWrapper<CompanionProfile>()
                        .eq(CompanionProfile::getUserId, companionUserId));
        if (profile == null) {
            return;
        }
        profile.setRatingAvg(agg == null ? java.math.BigDecimal.ZERO : agg.getAvgScore());
        profile.setRatingCount(agg == null ? 0 : agg.getCnt().intValue());
        companionProfileMapper.updateById(profile);
    }

    private Page<ReviewView> toViewPage(Page<Review> p) {
        Page<ReviewView> result = new Page<>(p.getCurrent(), p.getSize(), p.getTotal());
        result.setRecords(p.getRecords().stream().map(this::toView).toList());
        return result;
    }

    private ReviewView toView(Review r) {
        User user = userMapper.selectById(r.getUserId());
        return ReviewView.builder()
                .id(r.getId())
                .orderId(r.getOrderId())
                .userId(r.getUserId())
                .userNickname(user == null ? "" : user.getNickname())
                .companionUserId(r.getCompanionUserId())
                .score(r.getScore())
                .tags(fromJson(r.getTagJson(), new TypeReference<List<String>>() {}))
                .content(r.getContent())
                .displayStatus(r.getDisplayStatus())
                .createdAt(r.getCreatedAt())
                .build();
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "数据序列化失败");
        }
    }

    private <T> T fromJson(String json, TypeReference<T> type) {
        if (json == null || json.isBlank()) {
            return objectMapper.convertValue(new ArrayList<>(), type);
        }
        try {
            return objectMapper.readValue(json, type);
        } catch (JsonProcessingException e) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "数据解析失败");
        }
    }
}
