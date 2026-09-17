package com.gameplay.order.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.gameplay.auth.entity.User;
import com.gameplay.auth.mapper.UserMapper;
import com.gameplay.common.enums.OrderStatus;
import com.gameplay.common.exception.BusinessException;
import com.gameplay.common.exception.ErrorCode;
import com.gameplay.companion.domain.CompanionAvailability;
import com.gameplay.companion.domain.CompanionProfile;
import com.gameplay.companion.domain.CompanionService;
import com.gameplay.companion.enums.AvailabilityStatus;
import com.gameplay.companion.enums.CompanionServiceStatus;
import com.gameplay.companion.enums.ServiceAuditStatus;
import com.gameplay.companion.enums.ShelfStatus;
import com.gameplay.companion.mapper.CompanionAvailabilityMapper;
import com.gameplay.catalog.domain.Game;
import com.gameplay.catalog.mapper.GameMapper;
import com.gameplay.companion.mapper.CompanionProfileMapper;
import com.gameplay.companion.mapper.CompanionServiceMapper;
import com.gameplay.order.domain.OrderStatusHistory;
import com.gameplay.order.domain.OrderTimeSlot;
import com.gameplay.order.domain.PlayOrder;
import com.gameplay.order.dto.CreateOrderRequest;
import com.gameplay.order.dto.OrderStatusHistoryView;
import com.gameplay.order.dto.OrderView;
import com.gameplay.order.enums.SlotStatus;
import com.gameplay.order.mapper.OrderStatusHistoryMapper;
import com.gameplay.order.mapper.OrderTimeSlotMapper;
import com.gameplay.order.mapper.PlayOrderMapper;
import com.gameplay.wallet.service.WalletService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 订单领域服务：唯一状态迁移入口（概要设计 4.3、详细设计 3.1）。
 *
 * <p>Controller、客服和 AI 均不得直接修改 {@code play_order.order_status}；
 * 所有流转经 {@link #transition} 校验状态机并写入 {@code order_status_history}。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    /** 支付/接单超时时间（分钟） */
    private static final int PAY_EXPIRE_MINUTES = 30;
    private static final int ACCEPT_EXPIRE_MINUTES = 30;
    /** 允许提前开始服务的时间窗口（分钟） */
    private static final int START_EARLY_MINUTES = 15;

    private final PlayOrderMapper orderMapper;
    private final OrderTimeSlotMapper slotMapper;
    private final OrderStatusHistoryMapper historyMapper;
    private final CompanionServiceMapper companionServiceMapper;
    private final CompanionProfileMapper companionProfileMapper;
    private final GameMapper gameMapper;
    private final CompanionAvailabilityMapper availabilityMapper;
    private final UserMapper userMapper;
    private final WalletService walletService;

    // ==================== 用户端 ====================

    /** 创建预约订单（FR-U07/U08）：校验服务、陪玩师、档期与价格，占用临时档期 */
    @Transactional
    public OrderView createOrder(Long userId, CreateOrderRequest req) {
        // 1. 服务校验：审核通过 + 已上架 + 归属一致
        CompanionService service = companionServiceMapper.selectById(req.getCompanionServiceId());
        if (service == null || !service.getCompanionUserId().equals(req.getCompanionUserId())) {
            throw new BusinessException(ErrorCode.SERVICE_NOT_AVAILABLE, "服务项目不存在或与陪玩师不匹配");
        }
        if (!ServiceAuditStatus.APPROVED.name().equals(service.getAuditStatus())
                || !ShelfStatus.ON_SHELF.name().equals(service.getServiceStatus())) {
            throw new BusinessException(ErrorCode.SERVICE_NOT_AVAILABLE);
        }
        // 1.1 游戏启用校验：游戏停用后不得再产生新订单（与用户端列表隐藏口径一致，FR-M10）
        Game game = gameMapper.selectById(service.getGameId());
        if (game == null || !Integer.valueOf(1).equals(game.getEnabled())) {
            throw new BusinessException(ErrorCode.SERVICE_NOT_AVAILABLE, "该游戏已停用，暂不可预约");
        }
        // 2. 不能预约自己
        if (req.getCompanionUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.ORDER_SELF_BOOKING_FORBIDDEN);
        }
        // 3. 陪玩师状态校验
        CompanionProfile profile = companionProfileMapper.selectOne(
                new LambdaQueryWrapper<CompanionProfile>()
                        .eq(CompanionProfile::getUserId, req.getCompanionUserId()));
        if (profile == null
                || !CompanionServiceStatus.AVAILABLE.name().equals(profile.getServiceStatus())) {
            throw new BusinessException(ErrorCode.COMPANION_NOT_AVAILABLE);
        }
        // 4. 时长与时间校验
        if (req.getDurationMinutes() < service.getMinDurationMinutes()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                    "服务时长不能低于最短时长 " + service.getMinDurationMinutes() + " 分钟");
        }
        LocalDateTime now = LocalDateTime.now();
        if (req.getAppointmentStartAt().isBefore(now)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "预约开始时间不能早于当前时间");
        }
        LocalDateTime startAt = req.getAppointmentStartAt();
        LocalDateTime endAt = startAt.plusMinutes(req.getDurationMinutes());

        // 5. 档期校验：可约档期须完全覆盖预约区间，且无有效订单占用冲突（详细设计 7.1）
        checkAvailabilityAndSlotConflict(req.getCompanionUserId(), startAt, endAt);

        // 6. 金额计算（按分钟比例计费，向上取整到分）
        long totalAmount = service.getPriceCents() * req.getDurationMinutes() / 60L;
        if (service.getPriceCents() * req.getDurationMinutes() % 60L != 0) {
            totalAmount += 1;
        }

        // 7. 落库
        PlayOrder order = new PlayOrder();
        order.setOrderNo(generateOrderNo());
        order.setUserId(userId);
        order.setCompanionUserId(req.getCompanionUserId());
        order.setCompanionServiceId(service.getId());
        order.setGameId(service.getGameId());
        order.setServiceTitleSnapshot(service.getTitle());
        order.setServiceTypeNameSnapshot("");
        order.setCompanionNameSnapshot(profile.getDisplayName());
        order.setUnitPriceCents(service.getPriceCents());
        order.setDurationMinutes(req.getDurationMinutes());
        order.setTotalAmountCents(totalAmount);
        order.setGameServer(req.getGameServer() == null ? "" : req.getGameServer());
        order.setGameNickname(req.getGameNickname() == null ? "" : req.getGameNickname());
        order.setUserRemark(req.getUserRemark() == null ? "" : req.getUserRemark());
        order.setAppointmentStartAt(startAt);
        order.setAppointmentEndAt(endAt);
        order.setOrderStatus(OrderStatus.PENDING_PAYMENT.name());
        order.setPayExpireAt(now.plusMinutes(PAY_EXPIRE_MINUTES));
        order.setClosedReason("");
        order.setVersion(0);
        orderMapper.insert(order);

        // 临时档期占用（支付后转有效）
        OrderTimeSlot slot = new OrderTimeSlot();
        slot.setOrderId(order.getId());
        slot.setCompanionUserId(req.getCompanionUserId());
        slot.setStartAt(startAt);
        slot.setEndAt(endAt);
        slot.setSlotStatus(SlotStatus.TEMPORARY.name());
        slot.setExpireAt(order.getPayExpireAt());
        slotMapper.insert(slot);

        recordHistory(order.getId(), "", OrderStatus.PENDING_PAYMENT.name(),
                userId, "USER", "CREATE", "创建订单");
        return detailView(order);
    }

    /** 模拟支付（FR-U09）：虚拟余额扣款，档期占用转有效 */
    @Transactional
    public OrderView pay(Long userId, Long orderId) {
        PlayOrder order = orderMapper.selectByIdForUpdate(orderId);
        if (order == null) {
            throw new BusinessException(ErrorCode.ORDER_NOT_FOUND);
        }
        if (!order.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.PERMISSION_DATA_SCOPE_DENIED);
        }
        if (!OrderStatus.PENDING_PAYMENT.name().equals(order.getOrderStatus())) {
            throw new BusinessException(ErrorCode.ORDER_ALREADY_PAID);
        }
        if (LocalDateTime.now().isAfter(order.getPayExpireAt())) {
            throw new BusinessException(ErrorCode.ORDER_PAYMENT_EXPIRED);
        }
        walletService.pay(order, userId);
        transition(order, OrderStatus.WAITING_ACCEPTANCE, userId, "USER", "PAY", "模拟支付成功");
        order.setAcceptExpireAt(LocalDateTime.now().plusMinutes(ACCEPT_EXPIRE_MINUTES));
        orderMapper.updateById(order);
        slotMapper.updateStatusByOrder(order.getId(), SlotStatus.TEMPORARY.name(), SlotStatus.EFFECTIVE.name());
        return detailView(order);
    }

    // ==================== 陪玩师端 ====================

    /** 接受订单（FR-P13，详细设计 2.4） */
    @Transactional
    public OrderView accept(Long companionUserId, Long orderId) {
        PlayOrder order = requireCompanionOrder(companionUserId, orderId);
        if (!OrderStatus.WAITING_ACCEPTANCE.name().equals(order.getOrderStatus())) {
            throw new BusinessException(ErrorCode.ORDER_STATUS_INVALID);
        }
        if (LocalDateTime.now().isAfter(order.getAcceptExpireAt())) {
            throw new BusinessException(ErrorCode.ORDER_ACCEPT_EXPIRED);
        }
        transition(order, OrderStatus.WAITING_SERVICE, companionUserId, "COMPANION", "ACCEPT", "接单成功");
        return detailView(order);
    }

    /** 拒绝订单（FR-P14）：订单关闭并退还用户余额，释放档期 */
    @Transactional
    public OrderView reject(Long companionUserId, Long orderId, String reason) {
        PlayOrder order = requireCompanionOrder(companionUserId, orderId);
        if (!OrderStatus.WAITING_ACCEPTANCE.name().equals(order.getOrderStatus())) {
            throw new BusinessException(ErrorCode.ORDER_STATUS_INVALID);
        }
        walletService.refund(order, reason, companionUserId);
        transition(order, OrderStatus.CLOSED, companionUserId, "COMPANION", "REJECT", reason);
        order.setClosedReason(reason);
        orderMapper.updateById(order);
        releaseSlot(order.getId());
        return detailView(order);
    }

    /** 开始服务（FR-P15）：到达允许开始时间窗口后可由陪玩师发起 */
    @Transactional
    public OrderView startService(Long companionUserId, Long orderId) {
        PlayOrder order = requireCompanionOrder(companionUserId, orderId);
        if (!OrderStatus.WAITING_SERVICE.name().equals(order.getOrderStatus())) {
            throw new BusinessException(ErrorCode.ORDER_STATUS_INVALID);
        }
        if (LocalDateTime.now().isBefore(order.getAppointmentStartAt().minusMinutes(START_EARLY_MINUTES))) {
            throw new BusinessException(ErrorCode.ORDER_START_TIME_INVALID,
                    "预约开始时间前 " + START_EARLY_MINUTES + " 分钟内才能开始服务");
        }
        transition(order, OrderStatus.IN_SERVICE, companionUserId, "COMPANION", "START", "开始服务");
        order.setStartedAt(LocalDateTime.now());
        orderMapper.updateById(order);
        return detailView(order);
    }

    /** 结束服务（FR-P16）：订单转为待用户确认 */
    @Transactional
    public OrderView endService(Long companionUserId, Long orderId) {
        PlayOrder order = requireCompanionOrder(companionUserId, orderId);
        if (!OrderStatus.IN_SERVICE.name().equals(order.getOrderStatus())) {
            throw new BusinessException(ErrorCode.ORDER_STATUS_INVALID);
        }
        transition(order, OrderStatus.WAITING_CONFIRMATION, companionUserId, "COMPANION", "END", "结束服务");
        order.setEndedAt(LocalDateTime.now());
        orderMapper.updateById(order);
        return detailView(order);
    }

    /** 用户确认完成（FR-U13）：收益结算到陪玩师钱包 */
    @Transactional
    public OrderView confirmCompleted(Long userId, Long orderId) {
        PlayOrder order = orderMapper.selectByIdForUpdate(orderId);
        if (order == null) {
            throw new BusinessException(ErrorCode.ORDER_NOT_FOUND);
        }
        if (!order.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.PERMISSION_DATA_SCOPE_DENIED);
        }
        if (!OrderStatus.WAITING_CONFIRMATION.name().equals(order.getOrderStatus())) {
            throw new BusinessException(ErrorCode.ORDER_STATUS_INVALID);
        }
        walletService.settle(order, userId);
        transition(order, OrderStatus.COMPLETED, userId, "USER", "CONFIRM", "用户确认完成");
        order.setConfirmedAt(LocalDateTime.now());
        orderMapper.updateById(order);
        // 累计陪玩师已完成订单数
        CompanionProfile profile = companionProfileMapper.selectOne(
                new LambdaQueryWrapper<CompanionProfile>()
                        .eq(CompanionProfile::getUserId, order.getCompanionUserId()));
        if (profile != null) {
            profile.setCompletedOrderCount(profile.getCompletedOrderCount() + 1);
            companionProfileMapper.updateById(profile);
        }
        return detailView(order);
    }

    /** 用户取消订单（FR-U12，详细设计 4.1） */
    @Transactional
    public OrderView cancel(Long userId, Long orderId, String reason) {
        PlayOrder order = orderMapper.selectByIdForUpdate(orderId);
        if (order == null) {
            throw new BusinessException(ErrorCode.ORDER_NOT_FOUND);
        }
        if (!order.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.PERMISSION_DATA_SCOPE_DENIED);
        }
        boolean paid = switch (order.status()) {
            case PENDING_PAYMENT -> false; // 未支付：直接关闭，无需退款
            case WAITING_ACCEPTANCE, WAITING_SERVICE -> true; // 已支付：全额退款
            default -> throw new BusinessException(ErrorCode.ORDER_STATUS_INVALID,
                    "当前状态不可取消，如有疑问请联系客服");
        };
        String reasonText = reason == null || reason.isBlank() ? "用户取消订单" : reason;
        if (paid) {
            walletService.refund(order, reasonText, userId);
        }
        transition(order, OrderStatus.CLOSED, userId, "USER", "CANCEL", reasonText);
        order.setClosedReason(reasonText);
        orderMapper.updateById(order);
        releaseSlot(order.getId());
        return detailView(order);
    }

    // ==================== 售后仲裁（review 模块调用，FR-U17/FR-M18） ====================

    /** 订单进入售后（发起投诉，FR-U17）：仅 WAITING_CONFIRMATION 或 COMPLETED 可转入 AFTER_SALES */
    @Transactional
    public void enterAfterSales(Long userId, Long orderId, String reason) {
        PlayOrder order = orderMapper.selectByIdForUpdate(orderId);
        if (order == null) {
            throw new BusinessException(ErrorCode.ORDER_NOT_FOUND);
        }
        if (!order.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.PERMISSION_DATA_SCOPE_DENIED);
        }
        OrderStatus current = order.status();
        if (current != OrderStatus.WAITING_CONFIRMATION && current != OrderStatus.COMPLETED) {
            throw new BusinessException(ErrorCode.ORDER_STATUS_INVALID, "当前订单状态不支持发起投诉");
        }
        transition(order, OrderStatus.AFTER_SALES, userId, "USER", "COMPLAINT", reason);
    }

    /** 售后仲裁结束（FR-M18）：AFTER_SALES 流转到 COMPLETED（维持/部分退款）或 CLOSED（全额退款） */
    @Transactional
    public void resolveAfterSales(Long orderId, OrderStatus target, String reason, Long adminId) {
        if (target != OrderStatus.COMPLETED && target != OrderStatus.CLOSED) {
            throw new BusinessException(ErrorCode.ORDER_STATUS_INVALID, "售后仲裁仅可结束为已完成或已关闭");
        }
        PlayOrder order = orderMapper.selectByIdForUpdate(orderId);
        if (order == null) {
            throw new BusinessException(ErrorCode.ORDER_NOT_FOUND);
        }
        if (order.status() != OrderStatus.AFTER_SALES) {
            throw new BusinessException(ErrorCode.ORDER_STATUS_INVALID, "订单不在售后中，无法仲裁");
        }
        transition(order, target, adminId, "ADMIN", "ARBITRATE", reason);
        if (target == OrderStatus.CLOSED) {
            order.setClosedReason(reason);
            orderMapper.updateById(order);
        }
    }

    // ==================== 定时任务（详细设计 7.2） ====================

    /** 支付超时自动关闭 */
    @Transactional
    public void closeExpiredPendingPayment(Long orderId) {
        PlayOrder order = orderMapper.selectByIdForUpdate(orderId);
        if (order == null || !OrderStatus.PENDING_PAYMENT.name().equals(order.getOrderStatus())) {
            return;
        }
        transition(order, OrderStatus.CLOSED, 0L, "SYSTEM", "CLOSE_EXPIRED_PAYMENT", "支付超时自动关闭");
        order.setClosedReason("支付超时自动关闭");
        orderMapper.updateById(order);
        releaseSlot(order.getId());
    }

    /** 接单超时自动关闭并退款 */
    @Transactional
    public void closeExpiredWaitingAcceptance(Long orderId) {
        PlayOrder order = orderMapper.selectByIdForUpdate(orderId);
        if (order == null || !OrderStatus.WAITING_ACCEPTANCE.name().equals(order.getOrderStatus())) {
            return;
        }
        walletService.refund(order, "接单超时自动退款", 0L);
        transition(order, OrderStatus.CLOSED, 0L, "SYSTEM", "CLOSE_EXPIRED_ACCEPTANCE", "接单超时自动关闭");
        order.setClosedReason("接单超时自动关闭并退款");
        orderMapper.updateById(order);
        releaseSlot(order.getId());
    }

    /** 结束满24小时且用户未确认时自动完成并结算 */
    @Transactional
    public void autoConfirmCompleted(Long orderId) {
        PlayOrder order = orderMapper.selectByIdForUpdate(orderId);
        if (order == null || !OrderStatus.WAITING_CONFIRMATION.name().equals(order.getOrderStatus())) {
            return;
        }
        walletService.settle(order, 0L);
        transition(order, OrderStatus.COMPLETED, 0L, "SYSTEM", "AUTO_CONFIRM", "超过24小时自动确认完成");
        order.setConfirmedAt(LocalDateTime.now());
        orderMapper.updateById(order);
        CompanionProfile profile = companionProfileMapper.selectOne(
                new LambdaQueryWrapper<CompanionProfile>()
                        .eq(CompanionProfile::getUserId, order.getCompanionUserId()));
        if (profile != null) {
            profile.setCompletedOrderCount(profile.getCompletedOrderCount() + 1);
            companionProfileMapper.updateById(profile);
        }
    }

    // ==================== 查询 ====================

    /** 陪玩师订单分页（FR-P18），status 为空返回全部；多个状态用逗号分隔，如 COMPLETED,CLOSED */
    public Page<OrderView> listCompanionOrders(Long companionUserId, String status, long page, long size) {
        LambdaQueryWrapper<PlayOrder> wrapper = new LambdaQueryWrapper<PlayOrder>()
                .eq(PlayOrder::getCompanionUserId, companionUserId)
                .orderByDesc(PlayOrder::getId);
        applyStatusFilter(wrapper, status);
        return toViewPage(orderMapper.selectPage(new Page<>(page, size), wrapper), false);
    }

    /** 用户订单分页（FR-U10），status 为空返回全部；多个状态用逗号分隔 */
    public Page<OrderView> listUserOrders(Long userId, String status, long page, long size) {
        LambdaQueryWrapper<PlayOrder> wrapper = new LambdaQueryWrapper<PlayOrder>()
                .eq(PlayOrder::getUserId, userId)
                .orderByDesc(PlayOrder::getId);
        applyStatusFilter(wrapper, status);
        return toViewPage(orderMapper.selectPage(new Page<>(page, size), wrapper), false);
    }

    /** 订单详情（FR-U11/FR-P18）：本人、相关陪玩师或管理员可查看 */
    public OrderView detail(Long operatorId, List<String> roles, Long orderId) {
        PlayOrder order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException(ErrorCode.ORDER_NOT_FOUND);
        }
        boolean isAdmin = roles != null && roles.contains("ADMIN");
        if (!isAdmin && !order.getUserId().equals(operatorId)
                && !order.getCompanionUserId().equals(operatorId)) {
            throw new BusinessException(ErrorCode.PERMISSION_DATA_SCOPE_DENIED);
        }
        return detailView(order);
    }

    // ==================== 内部 ====================

    /** 状态迁移唯一入口：校验状态机 + 更新状态 + 写入历史 */
    private void transition(PlayOrder order, OrderStatus target, Long operatorId,
                            String operatorRole, String action, String reason) {
        OrderStatus current = order.status();
        if (!current.canTransitionTo(target)) {
            throw new BusinessException(ErrorCode.ORDER_STATUS_INVALID,
                    "订单状态不允许从 " + current + " 流转到 " + target);
        }
        int rows = orderMapper.updateStatusIfCurrent(order.getId(), current.name(), target.name());
        if (rows != 1) {
            throw new BusinessException(ErrorCode.ORDER_STATUS_INVALID, "订单状态已变更，请刷新后重试");
        }
        order.setOrderStatus(target.name());
        recordHistory(order.getId(), current.name(), target.name(),
                operatorId, operatorRole, action, reason);
        log.info("订单 {} 状态迁移: {} -> {} (动作 {}, 操作者 {})",
                order.getOrderNo(), current, target, action, operatorId);
    }

    /** 只写状态历史（创建订单等无迁移场景） */
    private void recordHistory(Long orderId, String fromStatus, String toStatus,
                               Long operatorId, String operatorRole,
                               String action, String reason) {
        OrderStatusHistory history = new OrderStatusHistory();
        history.setOrderId(orderId);
        history.setFromStatus(fromStatus);
        history.setToStatus(toStatus);
        history.setOperatorId(operatorId);
        history.setOperatorRole(operatorRole);
        history.setActionCode(action);
        history.setReason(reason == null ? "" : reason);
        historyMapper.insert(history);
    }

    private void releaseSlot(Long orderId) {
        slotMapper.updateStatusByOrder(orderId, SlotStatus.EFFECTIVE.name(), SlotStatus.RELEASED.name());
        slotMapper.updateStatusByOrder(orderId, SlotStatus.TEMPORARY.name(), SlotStatus.RELEASED.name());
    }

    private PlayOrder requireCompanionOrder(Long companionUserId, Long orderId) {
        PlayOrder order = orderMapper.selectByIdForUpdate(orderId);
        if (order == null) {
            throw new BusinessException(ErrorCode.ORDER_NOT_FOUND);
        }
        if (!order.getCompanionUserId().equals(companionUserId)) {
            throw new BusinessException(ErrorCode.PERMISSION_DATA_SCOPE_DENIED);
        }
        return order;
    }

    /** 档期校验：可约档期须完全覆盖预约区间；存在临时/有效订单占用则冲突（详细设计 7.1） */
    private void checkAvailabilityAndSlotConflict(Long companionUserId,
                                                  LocalDateTime startAt, LocalDateTime endAt) {
        List<CompanionAvailability> overlap = availabilityMapper.selectList(
                new LambdaQueryWrapper<CompanionAvailability>()
                        .eq(CompanionAvailability::getCompanionUserId, companionUserId)
                        .lt(CompanionAvailability::getStartAt, endAt)
                        .gt(CompanionAvailability::getEndAt, startAt));
        boolean covered = false;
        for (CompanionAvailability availability : overlap) {
            if (AvailabilityStatus.UNAVAILABLE.name().equals(availability.getAvailabilityStatus())) {
                throw new BusinessException(ErrorCode.SLOT_CONFLICT, "预约时段与不可约时段重叠");
            }
            if (!availability.getStartAt().isAfter(startAt) && !availability.getEndAt().isBefore(endAt)) {
                covered = true;
            }
        }
        if (!covered) {
            throw new BusinessException(ErrorCode.SERVICE_NOT_AVAILABLE,
                    "预约时段不在陪玩师可约档期内");
        }
        List<OrderTimeSlot> orderConflicts = slotMapper.selectConflictSlotsForUpdate(
                companionUserId, startAt, endAt);
        if (!orderConflicts.isEmpty()) {
            throw new BusinessException(ErrorCode.SLOT_CONFLICT, "预约时段已被其他订单占用");
        }
    }

    private String generateOrderNo() {
        return "PO" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                + String.format("%04d", ThreadLocalRandom.current().nextInt(10000));
    }

    /** status 逗号分隔多状态过滤 */
    private void applyStatusFilter(LambdaQueryWrapper<PlayOrder> wrapper, String status) {
        if (status != null && !status.isBlank()) {
            java.util.List<String> statuses = java.util.Arrays.stream(status.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .toList();
            if (statuses.size() == 1) {
                wrapper.eq(PlayOrder::getOrderStatus, statuses.get(0));
            } else if (statuses.size() > 1) {
                wrapper.in(PlayOrder::getOrderStatus, statuses);
            }
        }
    }

    private Page<OrderView> toViewPage(Page<PlayOrder> p, boolean withHistory) {
        Page<OrderView> result = new Page<>(p.getCurrent(), p.getSize(), p.getTotal());
        result.setRecords(p.getRecords().stream()
                .map(order -> withHistory ? detailView(order) : summaryView(order))
                .toList());
        return result;
    }

    private OrderView detailView(PlayOrder order) {
        OrderView view = summaryView(order);
        List<OrderStatusHistory> histories = historyMapper.selectList(
                new LambdaQueryWrapper<OrderStatusHistory>()
                        .eq(OrderStatusHistory::getOrderId, order.getId())
                        .orderByAsc(OrderStatusHistory::getId));
        view.setStatusHistories(histories.stream()
                .map(h -> OrderStatusHistoryView.builder()
                        .fromStatus(h.getFromStatus())
                        .toStatus(h.getToStatus())
                        .operatorRole(h.getOperatorRole())
                        .actionCode(h.getActionCode())
                        .reason(h.getReason())
                        .createdAt(h.getCreatedAt())
                        .build())
                .toList());
        return view;
    }

    private OrderView summaryView(PlayOrder order) {
        User user = userMapper.selectById(order.getUserId());
        return OrderView.builder()
                .id(order.getId())
                .orderNo(order.getOrderNo())
                .userId(order.getUserId())
                .userNickname(user == null ? "" : user.getNickname())
                .companionUserId(order.getCompanionUserId())
                .companionNameSnapshot(order.getCompanionNameSnapshot())
                .companionServiceId(order.getCompanionServiceId())
                .serviceTitleSnapshot(order.getServiceTitleSnapshot())
                .gameId(order.getGameId())
                .serviceTypeNameSnapshot(order.getServiceTypeNameSnapshot())
                .unitPriceCents(order.getUnitPriceCents())
                .durationMinutes(order.getDurationMinutes())
                .totalAmountCents(order.getTotalAmountCents())
                .gameServer(order.getGameServer())
                .gameNickname(order.getGameNickname())
                .userRemark(order.getUserRemark())
                .appointmentStartAt(order.getAppointmentStartAt())
                .appointmentEndAt(order.getAppointmentEndAt())
                .orderStatus(order.getOrderStatus())
                .payExpireAt(order.getPayExpireAt())
                .acceptExpireAt(order.getAcceptExpireAt())
                .startedAt(order.getStartedAt())
                .endedAt(order.getEndedAt())
                .confirmedAt(order.getConfirmedAt())
                .closedReason(order.getClosedReason())
                .createdAt(order.getCreatedAt())
                .build();
    }
}