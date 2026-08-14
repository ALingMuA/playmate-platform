package com.gameplay.customer_service.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.gameplay.ai.enums.AiDecision;
import com.gameplay.ai.service.AiResponseFacade;
import com.gameplay.ai.strategy.AiProcessResult;
import com.gameplay.ai.strategy.AiRequest;
import com.gameplay.common.enums.ConversationStatus;
import com.gameplay.common.exception.BusinessException;
import com.gameplay.common.exception.ErrorCode;
import com.gameplay.customer_service.domain.ConversationAssignment;
import com.gameplay.customer_service.domain.CustomerConversation;
import com.gameplay.customer_service.domain.CustomerServiceAccount;
import com.gameplay.customer_service.domain.ServiceEvaluation;
import com.gameplay.customer_service.dto.AiResponseView;
import com.gameplay.customer_service.dto.CloseConversationRequest;
import com.gameplay.customer_service.dto.ConversationClaimRequest;
import com.gameplay.customer_service.dto.ConversationCreateRequest;
import com.gameplay.customer_service.dto.ConversationView;
import com.gameplay.customer_service.dto.EscalateRequest;
import com.gameplay.customer_service.dto.EvaluationRequest;
import com.gameplay.customer_service.dto.MessageSendRequest;
import com.gameplay.customer_service.dto.MessageView;
import com.gameplay.customer_service.dto.QueueItemView;
import com.gameplay.customer_service.enums.AssignmentType;
import com.gameplay.customer_service.enums.ReceptionMode;
import com.gameplay.customer_service.enums.SenderType;
import com.gameplay.customer_service.mapper.ConversationAssignmentMapper;
import com.gameplay.customer_service.mapper.CustomerConversationMapper;
import com.gameplay.customer_service.mapper.CustomerServiceAccountMapper;
import com.gameplay.customer_service.mapper.ServiceEvaluationMapper;
import com.gameplay.order.domain.PlayOrder;
import com.gameplay.order.mapper.PlayOrderMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * 客服会话领域服务（FR-C07~C29）。
 *
 * <p>状态机见 {@link ConversationStatus}：AI_PROCESSING → WAITING_HUMAN → HUMAN_PROCESSING
 * →（ESCALATED_ADMIN）→ CLOSED。所有状态迁移均写 {@code conversation_assignment} 轨迹并推送通知。</p>
 */
@Service
@RequiredArgsConstructor
public class CustomerConversationService {

    private static final DateTimeFormatter NO_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final CustomerConversationMapper conversationMapper;
    private final ConversationAssignmentMapper assignmentMapper;
    private final ServiceEvaluationMapper evaluationMapper;
    private final CustomerServiceAccountMapper csAccountMapper;
    private final CustomerMessageService messageService;
    private final CsAccountService csAccountService;
    private final AiResponseFacade aiResponseFacade;
    private final CsMessageNotifier messageNotifier;
    private final PlayOrderMapper orderMapper;

    // ==================== 用户侧：会话生命周期 ====================

    /** 发起客服会话（FR-C07）：来源帮助中心/个人中心/订单详情 */
    @Transactional
    public ConversationView create(Long userId, ConversationCreateRequest req) {
        validateRelatedOrder(userId, req.getSourceType(), req.getRelatedOrderId());
        CustomerConversation conversation = new CustomerConversation();
        conversation.setConversationNo(nextConversationNo());
        conversation.setInitiatorUserId(userId);
        conversation.setSourceType(req.getSourceType());
        conversation.setRelatedOrderId(req.getRelatedOrderId() == null ? 0L : req.getRelatedOrderId());
        conversation.setConversationStatus(ConversationStatus.AI_PROCESSING.name());
        conversation.setReceptionMode(ReceptionMode.AI.name());
        conversation.setCurrentCsAccountId(0L);
        conversation.setTransferReason("");
        conversation.setUnresolvedCount(0);
        conversation.setVersion(0);
        conversationMapper.insert(conversation);
        // 首条消息
        if (StringUtils.hasText(req.getFirstContent())) {
            messageService.saveMessage(conversation.getId(), "cs-" + java.util.UUID.randomUUID(),
                    SenderType.USER, userId, req.getFirstContent(), false);
        }
        return toView(conversation);
    }

    /** 触发 AI 应答（FR-C09/C16~C22）：保存用户消息并返回 AI 处理结果 */
    @Transactional
    public AiResponseView aiRespond(Long userId, Long conversationId, MessageSendRequest req) {
        CustomerConversation conversation = requireConversation(conversationId);
        assertInitiator(conversation, userId);
        assertNotClosed(conversation);
        // 关联订单仅限本人（FR-C13）
        Long relatedOrderId = req.getRelatedOrderId() == null ? conversation.getRelatedOrderId() : req.getRelatedOrderId();
        if (relatedOrderId != null && relatedOrderId > 0) {
            assertOrderOwner(userId, relatedOrderId);
        }
        MessageView userMessage = messageService.saveMessage(conversationId, req.getClientMsgId(),
                SenderType.USER, userId, req.getContent(), false);

        AiRequest aiRequest = new AiRequest(
                req.getContent(),
                "来源:" + conversation.getSourceType(),
                conversationId,
                orderSummary(relatedOrderId),
                conversation.getUnresolvedCount(),
                Boolean.TRUE.equals(req.getRequestHuman()));
        AiProcessResult result = aiResponseFacade.process(aiRequest);
        MessageView aiMessage = null;
        if (StringUtils.hasText(result.response().content())) {
            aiMessage = messageService.saveMessage(conversationId, "ai-" + java.util.UUID.randomUUID(),
                    SenderType.AI, 0L, result.response().content(), true);
        }
        // 转人工处理
        if (result.decision() == AiDecision.TRANSFER_HUMAN) {
            transferToHuman(conversation, result.reason());
        }
        // 推送 AI 消息与会话状态变更（离线方由 REST 补拉兜底）
        if (aiMessage != null) {
            notifyMessage(conversation, aiMessage);
        }
        ConversationView view = toView(requireConversation(conversationId));
        notifyAll(conversation, view);
        return AiResponseView.builder()
                .conversationId(conversationId)
                .conversationStatus(view.getConversationStatus())
                .decision(result.decision().name())
                .userMessageId(userMessage.getMessageId())
                .aiMessage(aiMessage)
                .transferReason(result.reason())
                .build();
    }

    /** 用户主动转人工（FR-C10） */
    @Transactional
    public ConversationView requestHuman(Long userId, Long conversationId, String reason) {
        CustomerConversation conversation = requireConversation(conversationId);
        assertInitiator(conversation, userId);
        assertNotClosed(conversation);
        transferToHuman(conversation, StringUtils.hasText(reason) ? reason : "用户主动要求转人工");
        ConversationView view = toView(requireConversation(conversationId));
        notifyAll(conversation, view);
        return view;
    }

    /** 我的会话列表（FR-C14：仅本人历史会话，不含内部备注） */
    public List<ConversationView> listMine(Long userId) {
        return conversationMapper.selectList(new LambdaQueryWrapper<CustomerConversation>()
                        .eq(CustomerConversation::getInitiatorUserId, userId)
                        .orderByDesc(CustomerConversation::getId))
                .stream().map(this::toView).toList();
    }

    /** 会话详情（数据范围：发起人/当前客服/管理员） */
    public ConversationView getDetail(Long conversationId, Long operatorId, List<String> roles) {
        CustomerConversation conversation = requireConversation(conversationId);
        assertDataScope(conversation, operatorId, roles);
        return toView(conversation);
    }

    /** 会话消息（数据范围校验后按游标补拉） */
    public List<MessageView> listMessages(Long conversationId, Long afterId, int size,
                                          Long operatorId, List<String> roles) {
        requireConversation(conversationId);
        CustomerConversation conversation = requireConversation(conversationId);
        assertDataScope(conversation, operatorId, roles);
        return messageService.listMessages(conversationId, afterId, size);
    }

    /** 客服满意度评价（FR-C15：已关闭会话，每个会话最多一次） */
    @Transactional
    public void evaluate(Long userId, Long conversationId, EvaluationRequest req) {
        CustomerConversation conversation = requireConversation(conversationId);
        assertInitiator(conversation, userId);
        if (!ConversationStatus.CLOSED.name().equals(conversation.getConversationStatus())) {
            throw new BusinessException(ErrorCode.CS_CONVERSATION_NOT_CLOSED);
        }
        long exists = evaluationMapper.selectCount(new LambdaQueryWrapper<ServiceEvaluation>()
                .eq(ServiceEvaluation::getConversationId, conversationId));
        if (exists > 0) {
            throw new BusinessException(ErrorCode.CS_ALREADY_EVALUATED);
        }
        ServiceEvaluation evaluation = new ServiceEvaluation();
        evaluation.setConversationId(conversationId);
        evaluation.setEvaluatorUserId(userId);
        evaluation.setScore(req.getScore());
        evaluation.setContent(req.getContent() == null ? "" : req.getContent());
        try {
            evaluationMapper.insert(evaluation);
        } catch (DuplicateKeyException e) {
            throw new BusinessException(ErrorCode.CS_ALREADY_EVALUATED);
        }
    }

    // ==================== 客服侧：工作台 ====================

    /** 人工会话队列（FR-C24）：等待人工接待的会话，含排队时长 */
    public List<QueueItemView> queue(Long csUserId) {
        csAccountService.assertAvailable(csUserId);
        List<CustomerConversation> waiting = conversationMapper.selectList(
                new LambdaQueryWrapper<CustomerConversation>()
                        .eq(CustomerConversation::getConversationStatus, ConversationStatus.WAITING_HUMAN.name())
                        .orderByAsc(CustomerConversation::getId));
        LocalDateTime now = LocalDateTime.now();
        return waiting.stream().map(c -> toQueueItem(c, now)).toList();
    }

    /** 领取会话（FR-C25）：乐观锁 + 容量校验 */
    @Transactional
    public ConversationView claim(Long csUserId, Long conversationId, ConversationClaimRequest req) {
        CustomerServiceAccount account = csAccountService.assertAvailable(csUserId);
        CustomerConversation conversation = requireConversation(conversationId);
        if (!ConversationStatus.WAITING_HUMAN.name().equals(conversation.getConversationStatus())) {
            throw new BusinessException(ErrorCode.CS_CONVERSATION_ALREADY_CLAIMED, "会话不在等待人工状态");
        }
        if (!req.getExpectedVersion().equals(conversation.getVersion())) {
            throw new BusinessException(ErrorCode.CS_CONVERSATION_VERSION_CONFLICT);
        }
        // 容量校验：处理中（人工/管理员）会话数不能超过上限
        long active = conversationMapper.selectCount(new LambdaQueryWrapper<CustomerConversation>()
                .eq(CustomerConversation::getCurrentCsAccountId, account.getId())
                .in(CustomerConversation::getConversationStatus,
                        ConversationStatus.HUMAN_PROCESSING.name(),
                        ConversationStatus.ESCALATED_ADMIN.name()));
        if (active >= account.getMaxActiveConversations()) {
            throw new BusinessException(ErrorCode.CS_AGENT_CAPACITY_EXCEEDED);
        }
        int updated = conversationMapper.update(null, new LambdaUpdateWrapper<CustomerConversation>()
                .set(CustomerConversation::getConversationStatus, ConversationStatus.HUMAN_PROCESSING.name())
                .set(CustomerConversation::getReceptionMode, ReceptionMode.HUMAN.name())
                .set(CustomerConversation::getCurrentCsAccountId, account.getId())
                .setSql("version = version + 1")
                .eq(CustomerConversation::getId, conversationId)
                .eq(CustomerConversation::getVersion, conversation.getVersion())
                .eq(CustomerConversation::getConversationStatus, ConversationStatus.WAITING_HUMAN.name()));
        if (updated == 0) {
            throw new BusinessException(ErrorCode.CS_CONVERSATION_ALREADY_CLAIMED, "会话状态已变化，请刷新后重试");
        }
        recordAssignment(conversationId, AssignmentType.CLAIM, 0L, account.getId(), csUserId,
                req.getClaimRemark() == null ? "" : req.getClaimRemark());
        ConversationView view = toView(requireConversation(conversationId));
        notifyAll(conversation, view);
        return view;
    }

    /** 客服回复消息（FR-C26）：仅当前处理客服 */
    @Transactional
    public MessageView reply(Long csUserId, Long conversationId, MessageSendRequest req) {
        CustomerConversation conversation = requireConversation(conversationId);
        csAccountService.assertCurrentHandler(csUserId, conversation.getCurrentCsAccountId());
        assertNotClosed(conversation);
        MessageView message = messageService.saveMessage(conversationId, req.getClientMsgId(),
                SenderType.CS, csUserId, req.getContent(), false);
        notifyMessage(conversation, message);
        return message;
    }

    /** 关闭会话（FR-C29）：处理分类 + 结果 */
    @Transactional
    public ConversationView close(Long csUserId, Long conversationId, CloseConversationRequest req) {
        CustomerConversation conversation = requireConversation(conversationId);
        csAccountService.assertCurrentHandler(csUserId, conversation.getCurrentCsAccountId());
        assertNotClosed(conversation);
        conversationMapper.update(null, new LambdaUpdateWrapper<CustomerConversation>()
                .set(CustomerConversation::getConversationStatus, ConversationStatus.CLOSED.name())
                .set(CustomerConversation::getClosedCategory, req.getCategory())
                .set(CustomerConversation::getClosedResult, req.getResult())
                .set(CustomerConversation::getClosedAt, LocalDateTime.now())
                .setSql("version = version + 1")
                .eq(CustomerConversation::getId, conversationId)
                .eq(CustomerConversation::getVersion, conversation.getVersion()));
        messageService.saveMessage(conversationId, "sys-close-" + java.util.UUID.randomUUID(),
                SenderType.SYSTEM, 0L, "会话已关闭：" + req.getCategory(), false);
        ConversationView view = toView(requireConversation(conversationId));
        notifyAll(conversation, view);
        return view;
    }

    /** 转交管理员（FR-C28）：退款/投诉仲裁/封禁申诉等超权限事项 */
    @Transactional
    public ConversationView escalate(Long csUserId, Long conversationId, EscalateRequest req) {
        CustomerConversation conversation = requireConversation(conversationId);
        csAccountService.assertCurrentHandler(csUserId, conversation.getCurrentCsAccountId());
        if (ConversationStatus.CLOSED.name().equals(conversation.getConversationStatus())
                || ConversationStatus.ESCALATED_ADMIN.name().equals(conversation.getConversationStatus())) {
            throw new BusinessException(ErrorCode.CS_CONVERSATION_STATUS_INVALID, "当前状态不允许转交管理员");
        }
        conversationMapper.update(null, new LambdaUpdateWrapper<CustomerConversation>()
                .set(CustomerConversation::getConversationStatus, ConversationStatus.ESCALATED_ADMIN.name())
                .set(CustomerConversation::getReceptionMode, ReceptionMode.ADMIN.name())
                .set(CustomerConversation::getTransferReason, req.getReason())
                .setSql("version = version + 1")
                .eq(CustomerConversation::getId, conversationId)
                .eq(CustomerConversation::getVersion, conversation.getVersion()));
        recordAssignment(conversationId, AssignmentType.ESCALATE_ADMIN,
                conversation.getCurrentCsAccountId(), 0L, csUserId, req.getReason());
        messageService.saveMessage(conversationId, "sys-escalate-" + java.util.UUID.randomUUID(),
                SenderType.SYSTEM, 0L, "会话已转交管理员处理：" + req.getReason(), false);
        ConversationView view = toView(requireConversation(conversationId));
        notifyAll(conversation, view);
        return view;
    }

    /** 我的处理中会话（客服） */
    public List<ConversationView> listAssigned(Long csUserId) {
        CustomerServiceAccount account = csAccountService.getByUserId(csUserId);
        if (account == null) {
            return List.of();
        }
        return conversationMapper.selectList(new LambdaQueryWrapper<CustomerConversation>()
                        .eq(CustomerConversation::getCurrentCsAccountId, account.getId())
                        .in(CustomerConversation::getConversationStatus,
                                ConversationStatus.HUMAN_PROCESSING.name(),
                                ConversationStatus.ESCALATED_ADMIN.name())
                        .orderByDesc(CustomerConversation::getId))
                .stream().map(this::toView).toList();
    }

    // ==================== 内部方法 ====================

    /** AI 转人工：状态 → WAITING_HUMAN，写轨迹与系统消息 */
    private void transferToHuman(CustomerConversation conversation, String reason) {
        if (!ConversationStatus.AI_PROCESSING.name().equals(conversation.getConversationStatus())) {
            return; // 已转人工或已关闭，不重复处理
        }
        conversationMapper.update(null, new LambdaUpdateWrapper<CustomerConversation>()
                .set(CustomerConversation::getConversationStatus, ConversationStatus.WAITING_HUMAN.name())
                .set(CustomerConversation::getReceptionMode, ReceptionMode.HUMAN.name())
                .set(CustomerConversation::getTransferReason, reason)
                .setSql("version = version + 1")
                .eq(CustomerConversation::getId, conversation.getId())
                .eq(CustomerConversation::getVersion, conversation.getVersion()));
        recordAssignment(conversation.getId(), AssignmentType.AI_TRANSFER, 0L, 0L, 0L, reason);
        messageService.saveMessage(conversation.getId(), "sys-transfer-" + java.util.UUID.randomUUID(),
                SenderType.SYSTEM, 0L, "已转人工客服，请稍候，客服人员将尽快接入。", false);
    }

    private void recordAssignment(Long conversationId, AssignmentType type, Long fromCsId,
                                  Long toCsId, Long operatorId, String reason) {
        ConversationAssignment assignment = new ConversationAssignment();
        assignment.setConversationId(conversationId);
        assignment.setAssignmentType(type.name());
        assignment.setFromCsAccountId(fromCsId == null ? 0L : fromCsId);
        assignment.setToCsAccountId(toCsId == null ? 0L : toCsId);
        assignment.setOperatorId(operatorId == null ? 0L : operatorId);
        assignment.setReason(reason == null ? "" : reason);
        assignmentMapper.insert(assignment);
    }

    private void notifyMessage(CustomerConversation conversation, MessageView message) {
        messageNotifier.notifyMessage(message, conversation.getInitiatorUserId(),
                csUserIdOf(conversation.getCurrentCsAccountId()));
    }

    private void notifyAll(CustomerConversation conversation, ConversationView view) {
        messageNotifier.notifyConversationChanged(view, conversation.getInitiatorUserId(),
                csUserIdOf(conversation.getCurrentCsAccountId()));
    }

    /** 客服账号 → 内部用户ID（WebSocket 按用户ID路由推送） */
    private Long csUserIdOf(Long csAccountId) {
        if (csAccountId == null || csAccountId == 0L) {
            return 0L;
        }
        CustomerServiceAccount account = csAccountMapper.selectById(csAccountId);
        return account == null ? 0L : account.getUserId();
    }

    /** 会话编号：CS + 时间戳 + 序号 */
    private String nextConversationNo() {
        return "CS" + LocalDateTime.now().format(NO_FORMAT)
                + String.format(Locale.ROOT, "%03d", System.nanoTime() % 1000);
    }

    private void validateRelatedOrder(Long userId, String sourceType, Long relatedOrderId) {
        if ("ORDER".equals(sourceType)) {
            if (relatedOrderId == null || relatedOrderId <= 0) {
                throw new BusinessException(ErrorCode.VALIDATION_FAILED, "订单来源必须关联订单");
            }
            assertOrderOwner(userId, relatedOrderId);
        }
    }

    private void assertOrderOwner(Long userId, Long orderId) {
        PlayOrder order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException(ErrorCode.ORDER_NOT_FOUND);
        }
        if (!order.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.PERMISSION_DATA_SCOPE_DENIED, "只能关联本人的订单");
        }
    }

    /** 脱敏订单摘要（FR-C13：不向 AI 或客服提供超出业务需要的数据） */
    private String orderSummary(Long relatedOrderId) {
        if (relatedOrderId == null || relatedOrderId <= 0) {
            return "";
        }
        PlayOrder order = orderMapper.selectById(relatedOrderId);
        if (order == null) {
            return "";
        }
        return "订单#" + order.getOrderNo() + " 状态:" + order.getOrderStatus();
    }

    private CustomerConversation requireConversation(Long conversationId) {
        CustomerConversation conversation = conversationMapper.selectById(conversationId);
        if (conversation == null) {
            throw new BusinessException(ErrorCode.CS_CONVERSATION_NOT_FOUND);
        }
        return conversation;
    }

    private void assertInitiator(CustomerConversation conversation, Long userId) {
        if (!conversation.getInitiatorUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.PERMISSION_DATA_SCOPE_DENIED);
        }
    }

    private void assertNotClosed(CustomerConversation conversation) {
        if (ConversationStatus.CLOSED.name().equals(conversation.getConversationStatus())) {
            throw new BusinessException(ErrorCode.CS_CONVERSATION_CLOSED);
        }
    }

    /** 数据范围：发起人本人、会话当前客服、管理员 */
    private void assertDataScope(CustomerConversation conversation, Long operatorId, List<String> roles) {
        boolean isAdmin = roles != null && roles.contains("ADMIN");
        if (isAdmin || conversation.getInitiatorUserId().equals(operatorId)) {
            return;
        }
        if (conversation.getCurrentCsAccountId() != null && conversation.getCurrentCsAccountId() > 0) {
            CustomerServiceAccount account = csAccountService.getByUserId(operatorId);
            if (account != null && account.getId().equals(conversation.getCurrentCsAccountId())) {
                return;
            }
        }
        throw new BusinessException(ErrorCode.PERMISSION_DATA_SCOPE_DENIED);
    }

    private QueueItemView toQueueItem(CustomerConversation c, LocalDateTime now) {
        String topic = messageService.listMessages(c.getId(), null, 1).stream()
                .filter(m -> SenderType.USER.name().equals(m.getSenderType()))
                .map(MessageView::getContent)
                .findFirst().orElse(c.getSourceType());
        return QueueItemView.builder()
                .conversationId(c.getId())
                .conversationNo(c.getConversationNo())
                .topic(topic.length() > 50 ? topic.substring(0, 50) : topic)
                .sourceType(c.getSourceType())
                .relatedOrderId(c.getRelatedOrderId())
                .transferReason(c.getTransferReason())
                .queueSeconds(Duration.between(c.getCreatedAt(), now).getSeconds())
                .createdAt(c.getCreatedAt())
                .build();
    }

    private ConversationView toView(CustomerConversation c) {
        return ConversationView.builder()
                .id(c.getId())
                .conversationNo(c.getConversationNo())
                .initiatorUserId(c.getInitiatorUserId())
                .sourceType(c.getSourceType())
                .relatedOrderId(c.getRelatedOrderId())
                .conversationStatus(c.getConversationStatus())
                .receptionMode(c.getReceptionMode())
                .currentCsAccountId(c.getCurrentCsAccountId())
                .transferReason(c.getTransferReason())
                .unresolvedCount(c.getUnresolvedCount())
                .version(c.getVersion())
                .createdAt(c.getCreatedAt())
                .closedAt(c.getClosedAt())
                .build();
    }
}