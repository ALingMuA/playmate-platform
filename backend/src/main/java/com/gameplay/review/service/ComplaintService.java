package com.gameplay.review.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.gameplay.auth.entity.User;
import com.gameplay.auth.mapper.UserMapper;
import com.gameplay.common.enums.OrderStatus;
import com.gameplay.common.exception.BusinessException;
import com.gameplay.common.exception.ErrorCode;
import com.gameplay.coupon.service.CouponService;
import com.gameplay.order.domain.PlayOrder;
import com.gameplay.order.mapper.PlayOrderMapper;
import com.gameplay.order.domain.OrderDeliveryEvidence;
import com.gameplay.order.dto.DeliveryEvidenceView;
import com.gameplay.order.mapper.OrderDeliveryEvidenceMapper;
import com.gameplay.order.service.OrderService;
import com.gameplay.review.domain.Complaint;
import com.gameplay.review.domain.ComplaintEvidence;
import com.gameplay.review.dto.ComplaintCreateRequest;
import com.gameplay.review.dto.ComplaintEvidenceView;
import com.gameplay.review.dto.ComplaintHandleRequest;
import com.gameplay.review.dto.ComplaintView;
import com.gameplay.review.enums.ComplaintResolutionType;
import com.gameplay.review.enums.ComplaintStatus;
import com.gameplay.review.enums.ComplaintType;
import com.gameplay.review.mapper.ComplaintEvidenceMapper;
import com.gameplay.review.mapper.ComplaintMapper;
import com.gameplay.notification.service.NotificationService;
import com.gameplay.wallet.service.WalletService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 投诉与仲裁领域服务（FR-U17~U19、FR-M18/M19）。
 *
 * <p>规则：投诉必须关联订单；发起后订单进入售后中（状态迁移由 OrderService 执行）；
 * 管理员仲裁通过资金领域服务完成退款/扣回，不允许直接修改余额（概要设计 4.4）。</p>
 */
@Service
@RequiredArgsConstructor
public class ComplaintService {

    /** 完成后可发起投诉的时间窗口（小时，FR-U17） */
    private static final int COMPLAINT_WINDOW_HOURS = 72;

    private final ComplaintMapper complaintMapper;
    private final ComplaintEvidenceMapper evidenceMapper;
    private final OrderDeliveryEvidenceMapper deliveryEvidenceMapper;
    private final PlayOrderMapper orderMapper;
    private final UserMapper userMapper;
    private final OrderService orderService;
    private final WalletService walletService;
    private final CouponService couponService;
    private final NotificationService notificationService;

    /** 发起投诉（FR-U17/U18）：校验订单资格并转入售后 */
    @Transactional
    public ComplaintView createComplaint(Long userId, ComplaintCreateRequest req) {
        ComplaintType type;
        try {
            type = ComplaintType.valueOf(req.getComplaintType());
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.COMPLAINT_TYPE_INVALID);
        }
        PlayOrder order = orderMapper.selectByIdForUpdate(req.getOrderId() == null ? -1L : req.getOrderId());
        if (order == null) {
            throw new BusinessException(ErrorCode.ORDER_NOT_FOUND);
        }
        if (!order.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.PERMISSION_DATA_SCOPE_DENIED);
        }
        // 同一订单仅允许发起一次投诉（拦截进行中及已仲裁后的重复投诉）
        if (complaintMapper.countByOrder(order.getId()) > 0) {
            throw new BusinessException(ErrorCode.COMPLAINT_ALREADY_EXISTS);
        }
        // 资格：服务中、待确认完成 或 完成后72小时内（FR-U17）
        boolean eligible = switch (order.status()) {
            case IN_SERVICE, WAITING_CONFIRMATION -> true;
            case COMPLETED -> order.getConfirmedAt() != null
                    && LocalDateTime.now().isBefore(order.getConfirmedAt().plusHours(COMPLAINT_WINDOW_HOURS));
            default -> false;
        };
        if (!eligible) {
            throw new BusinessException(ErrorCode.COMPLAINT_ORDER_NOT_ELIGIBLE);
        }

        // 订单转入售后中（状态机由 OrderService 校验）
        orderService.enterAfterSales(userId, order.getId(), "用户发起投诉：" + type);

        Complaint complaint = new Complaint();
        complaint.setOrderId(order.getId());
        complaint.setComplainantUserId(userId);
        complaint.setComplaintType(type.name());
        complaint.setDescription(req.getDescription());
        complaint.setComplaintStatus(ComplaintStatus.PENDING.name());
        complaint.setResolutionType("");
        complaint.setRefundAmountCents(0L);
        complaint.setHandledBy(0L);
        complaint.setHandlingOpinion("");
        complaintMapper.insert(complaint);

        // 图片证据（FR-U18）
        if (req.getEvidences() != null) {
            int sort = 0;
            for (ComplaintCreateRequest.EvidenceItem item : req.getEvidences()) {
                ComplaintEvidence evidence = new ComplaintEvidence();
                evidence.setComplaintId(complaint.getId());
                evidence.setFileUrl(item.getFileUrl());
                evidence.setFileName(item.getFileName() == null ? "" : item.getFileName());
                evidence.setSortNo(sort++);
                evidenceMapper.insert(evidence);
            }
        }
        ComplaintView view = toView(complaint, true);
        view.setHandledBy(null);
        return view;
    }

    /** 我的投诉分页（FR-U19 售后进度） */
    public Page<ComplaintView> listMine(Long userId, long page, long size) {
        return listMine(userId, null, page, size);
    }

    /** 我的投诉分页（FR-U19 售后进度，支持状态筛选） */
    public Page<ComplaintView> listMine(Long userId, String status, long page, long size) {
        LambdaQueryWrapper<Complaint> wrapper = new LambdaQueryWrapper<Complaint>()
                .eq(Complaint::getComplainantUserId, userId)
                .orderByDesc(Complaint::getId);
        if (status != null && !status.isBlank()) {
            wrapper.eq(Complaint::getComplaintStatus, status.trim().toUpperCase());
        }
        Page<Complaint> p = complaintMapper.selectPage(new Page<>(page, size), wrapper);
        Page<ComplaintView> vp = toViewPage(p);
        vp.getRecords().forEach(v -> v.setHandledBy(null));
        return vp;
    }

    /** 投诉详情（FR-U19）：发起人或管理员可查看 */
    public ComplaintView detail(Long operatorId, List<String> roles, Long complaintId) {
        Complaint complaint = complaintMapper.selectById(complaintId);
        if (complaint == null) {
            throw new BusinessException(ErrorCode.COMPLAINT_NOT_FOUND);
        }
        boolean isAdmin = roles != null && roles.contains("ADMIN");
        if (!isAdmin && !complaint.getComplainantUserId().equals(operatorId)) {
            throw new BusinessException(ErrorCode.PERMISSION_DATA_SCOPE_DENIED);
        }
        ComplaintView view = toView(complaint, true);
        if (!isAdmin) {
            view.setHandledBy(null);
        }
        return view;
    }

    // ==================== 管理端（FR-M18/M19） ====================

    /** 投诉分页：可按状态、类型、说明关键词过滤 */
    public Page<ComplaintView> adminPage(String status, String complaintType, String keyword,
                                         long page, long size) {
        LambdaQueryWrapper<Complaint> wrapper = new LambdaQueryWrapper<Complaint>()
                .orderByDesc(Complaint::getId);
        if (status != null && !status.isBlank()) {
            wrapper.eq(Complaint::getComplaintStatus, status);
        }
        if (complaintType != null && !complaintType.isBlank()) {
            wrapper.eq(Complaint::getComplaintType, complaintType);
        }
        if (keyword != null && !keyword.isBlank()) {
            wrapper.like(Complaint::getDescription, keyword.trim());
        }
        return toViewPage(complaintMapper.selectPage(new Page<>(page, size), wrapper));
    }

    /**
     * 投诉仲裁（FR-M18/M19）：维持订单、全额退款或部分退款。
     * <p>资金调整全部经由 WalletService（不允许直接修改余额），订单状态由 OrderService 仲裁结束。</p>
     */
    @Transactional
    public ComplaintView adminHandle(Long adminId, Long complaintId, ComplaintHandleRequest req) {
        ComplaintResolutionType resolutionType;
        try {
            resolutionType = ComplaintResolutionType.valueOf(req.getResolutionType());
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "处理类型不合法");
        }
        Complaint complaint = complaintMapper.selectByIdForUpdate(complaintId);
        if (complaint == null) {
            throw new BusinessException(ErrorCode.COMPLAINT_NOT_FOUND);
        }
        if (!ComplaintStatus.PENDING.name().equals(complaint.getComplaintStatus())
                && !ComplaintStatus.PROCESSING.name().equals(complaint.getComplaintStatus())) {
            throw new BusinessException(ErrorCode.COMPLAINT_STATUS_INVALID);
        }
        PlayOrder order = orderMapper.selectByIdForUpdate(complaint.getOrderId());
        if (order == null) {
            throw new BusinessException(ErrorCode.ORDER_NOT_FOUND);
        }

        String opinion = req.getHandlingOpinion();
        switch (resolutionType) {
            case KEEP -> {
                // 维持订单：未结算则正常结算收益，订单回到已完成
                if (!walletService.isSettled(order)) {
                    walletService.settle(order, adminId);
                }
                orderService.resolveAfterSales(order.getId(), OrderStatus.COMPLETED, opinion, adminId);
            }
            case FULL_REFUND -> {
                // 全额退款：已结算收益从陪玩师钱包扣回，退还买家资金与已核销优惠券，订单关闭
                if (walletService.isSettled(order)) {
                    walletService.deductSettledIncome(order, opinion, adminId);
                }
                walletService.refund(order, "投诉全额退款：" + opinion, adminId);
                couponService.refundUsedCoupon(order.getId());
                orderService.resolveAfterSales(order.getId(), OrderStatus.CLOSED, opinion, adminId);
            }
            case PARTIAL_REFUND -> {
                long refundCents = req.getRefundAmountCents() == null ? 0L : req.getRefundAmountCents();
                if (refundCents <= 0 || order.getTotalAmountCents() == null || refundCents > order.getTotalAmountCents()) {
                    throw new BusinessException(ErrorCode.COMPLAINT_REFUND_AMOUNT_INVALID);
                }
                long settlementAmount = (order.getSettlementAmountCents() != null && order.getSettlementAmountCents() > 0)
                        ? order.getSettlementAmountCents() : order.getTotalAmountCents();
                walletService.refundAmount(order, refundCents, complaint.getId(), opinion, adminId);
                if (!walletService.isSettled(order)) {
                    long netShare = Math.max(0L, settlementAmount - refundCents);
                    if (netShare > 0) {
                        walletService.settleAmount(order, netShare, adminId);
                    }
                } else {
                    long clawback = Math.min(refundCents, settlementAmount);
                    if (clawback > 0) {
                        walletService.deductSettledIncomeAmount(order, clawback, complaint.getId(), opinion, adminId);
                    }
                }
                orderService.resolveAfterSales(order.getId(), OrderStatus.COMPLETED, opinion, adminId);
            }
            default -> throw new BusinessException(ErrorCode.VALIDATION_FAILED, "处理类型不合法");
        }

        complaint.setComplaintStatus(ComplaintStatus.RESOLVED.name());
        complaint.setResolutionType(resolutionType.name());
        complaint.setRefundAmountCents(switch (resolutionType) {
            case PARTIAL_REFUND -> req.getRefundAmountCents() == null ? 0L : req.getRefundAmountCents();
            case FULL_REFUND -> order.getTotalAmountCents();
            default -> 0L;
        });
        complaint.setHandledBy(adminId);
        complaint.setHandlingOpinion(opinion);
        complaint.setHandledAt(LocalDateTime.now());
        complaintMapper.updateById(complaint);
        notificationService.record(
                complaint.getComplainantUserId(),
                "COMPLAINT_RESULT",
                "投诉仲裁处理完成",
                "您的订单【" + (order != null ? order.getOrderNo() : "") + "】投诉已被管理员处理：" + (opinion == null ? "" : opinion),
                "COMPLAINT",
                complaint.getId(),
                "COMPLAINT:" + complaint.getId() + ":RESOLVED"
        );
        return toView(complaint, true);
    }

    // ==================== 内部 ====================

    private Page<ComplaintView> toViewPage(Page<Complaint> p) {
        Page<ComplaintView> result = new Page<>(p.getCurrent(), p.getSize(), p.getTotal());
        List<Complaint> records = p.getRecords();
        if (records.isEmpty()) {
            result.setRecords(List.of());
            return result;
        }
        Set<Long> orderIds = records.stream().map(Complaint::getOrderId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, PlayOrder> orderMap = orderIds.isEmpty() ? Collections.emptyMap() :
                orderMapper.selectBatchIds(orderIds).stream()
                        .collect(Collectors.toMap(PlayOrder::getId, Function.identity(), (a, b) -> a));
        Set<Long> userIds = new LinkedHashSet<>();
        for (Complaint c : records) {
            if (c.getComplainantUserId() != null) {
                userIds.add(c.getComplainantUserId());
            }
        }
        for (PlayOrder o : orderMap.values()) {
            if (o.getCompanionUserId() != null) {
                userIds.add(o.getCompanionUserId());
            }
        }
        Map<Long, User> userMap = userIds.isEmpty() ? Collections.emptyMap() :
                userMapper.selectBatchIds(userIds).stream()
                        .collect(Collectors.toMap(User::getId, Function.identity(), (a, b) -> a));

        Set<Long> complaintIds = records.stream().map(Complaint::getId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, List<ComplaintEvidence>> evidenceMap = complaintIds.isEmpty() ? Collections.emptyMap() :
                evidenceMapper.selectList(new LambdaQueryWrapper<ComplaintEvidence>()
                                .in(ComplaintEvidence::getComplaintId, complaintIds)
                                .orderByAsc(ComplaintEvidence::getSortNo))
                        .stream()
                        .collect(Collectors.groupingBy(ComplaintEvidence::getComplaintId));

        result.setRecords(records.stream().map(c -> {
            PlayOrder order = orderMap.get(c.getOrderId());
            User complainant = userMap.get(c.getComplainantUserId());
            User companion = order == null ? null : userMap.get(order.getCompanionUserId());
            List<ComplaintEvidence> evidences = evidenceMap.getOrDefault(c.getId(), List.of());
            return buildView(c, order, complainant, companion, evidences, List.of());
        }).toList());
        return result;
    }

    private ComplaintView toView(Complaint c, boolean includeDeliveryEvidences) {
        PlayOrder order = orderMapper.selectById(c.getOrderId());
        User complainant = userMapper.selectById(c.getComplainantUserId());
        User companion = order == null ? null : userMapper.selectById(order.getCompanionUserId());
        List<ComplaintEvidence> evidences = evidenceMapper.selectList(
                new LambdaQueryWrapper<ComplaintEvidence>()
                        .eq(ComplaintEvidence::getComplaintId, c.getId())
                        .orderByAsc(ComplaintEvidence::getSortNo));
        List<DeliveryEvidenceView> deliveryEvidences = includeDeliveryEvidences ? deliveryEvidenceMapper.selectList(
                        new LambdaQueryWrapper<OrderDeliveryEvidence>()
                                .eq(OrderDeliveryEvidence::getOrderId, c.getOrderId())
                                .orderByAsc(OrderDeliveryEvidence::getSortNo))
                .stream()
                .map(e -> DeliveryEvidenceView.builder()
                        .id(e.getId())
                        .fileUrl(e.getFileUrl())
                        .fileName(e.getFileName())
                        .createdAt(e.getCreatedAt())
                        .build())
                .toList() : List.of();
        return buildView(c, order, complainant, companion, evidences, deliveryEvidences);
    }

    private ComplaintView buildView(Complaint c, PlayOrder order, User complainant, User companion,
                                    List<ComplaintEvidence> evidences, List<DeliveryEvidenceView> deliveryEvidences) {
        return ComplaintView.builder()
                .id(c.getId())
                .orderId(c.getOrderId())
                .orderNo(order == null ? "" : order.getOrderNo())
                .serviceTitleSnapshot(order == null ? "" : order.getServiceTitleSnapshot())
                .orderTotalAmountCents(order == null ? 0L : order.getTotalAmountCents())
                .complainantUserId(c.getComplainantUserId())
                .complainantNickname(complainant == null ? "" : complainant.getNickname())
                .companionUserId(order == null ? null : order.getCompanionUserId())
                .companionNickname(companion == null ? "" : companion.getNickname())
                .complaintType(c.getComplaintType())
                .description(c.getDescription())
                .complaintStatus(c.getComplaintStatus())
                .resolutionType(c.getResolutionType())
                .refundAmountCents(c.getRefundAmountCents())
                .handledBy(c.getHandledBy())
                .handlingOpinion(c.getHandlingOpinion())
                .handledAt(c.getHandledAt())
                .createdAt(c.getCreatedAt())
                .evidences(evidences.stream().map(e -> ComplaintEvidenceView.builder()
                        .id(e.getId())
                        .fileUrl(e.getFileUrl())
                        .fileName(e.getFileName())
                        .createdAt(e.getCreatedAt())
                        .build()).toList())
                .deliveryEvidences(deliveryEvidences)
                .build();
    }
}
