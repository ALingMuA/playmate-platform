package com.gameplay.customer_service.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.gameplay.ai.enums.AiDecision;
import com.gameplay.ai.strategy.AiProcessResult;
import com.gameplay.ai.task.domain.AiReplyTask;
import com.gameplay.ai.task.dto.AiTaskView;
import com.gameplay.ai.task.mapper.AiReplyTaskMapper;
import com.gameplay.ai.task.service.AiReplyTaskService;
import com.gameplay.common.enums.ConversationStatus;
import com.gameplay.common.exception.BusinessException;
import com.gameplay.common.exception.ErrorCode;
import com.gameplay.customer_service.domain.ConversationAssignment;
import com.gameplay.customer_service.domain.CustomerConversation;
import com.gameplay.customer_service.domain.CustomerServiceAccount;
import com.gameplay.customer_service.domain.CustomerServiceMessage;
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
import com.gameplay.customer_service.mapper.CustomerServiceMessageMapper;
import com.gameplay.customer_service.mapper.ServiceEvaluationMapper;
import com.gameplay.order.domain.PlayOrder;
import com.gameplay.order.mapper.PlayOrderMapper;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

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
    private static final AtomicInteger CONVERSATION_SEQ = new AtomicInteger();

    private final CustomerConversationMapper conversationMapper;
    private final ConversationAssignmentMapper assignmentMapper;
    private final CustomerServiceMessageMapper messageMapper;
    private final ServiceEvaluationMapper evaluationMapper;
    private final CustomerServiceAccountMapper csAccountMapper;
    private final CustomerMessageService messageService;
    private final CsAccountService csAccountService;
    private final AiReplyTaskService aiTaskService;
    private final AiReplyTaskMapper aiTaskMapper;
    private final CsMessageNotifier messageNotifier;
    private final PlayOrderMapper orderMapper;
    private final Validator validator;

    // ==================== 用户侧：会话生命周期 ====================

    /** 发起客服会话（FR-C07）：来源帮助中心/个人中心/订单详情 */
    @Transactional
    public ConversationView create(Long userId, ConversationCreateRequest req) {
        validateRelatedOrder(userId, req.getSourceType(), req.getRelatedOrderId());
        LocalDateTime now = LocalDateTime.now();
        CustomerConversation conversation = new CustomerConversation();
        conversation.setInitiatorUserId(userId);
        conversation.setSourceType(req.getSourceType());
        conversation.setRelatedOrderId(req.getRelatedOrderId() == null ? 0L : req.getRelatedOrderId());
        conversation.setConversationStatus(ConversationStatus.AI_PROCESSING.name());
        conversation.setReceptionMode(ReceptionMode.AI.name());
        conversation.setCurrentCsAccountId(0L);
        conversation.setTransferReason("");
        conversation.setUnresolvedCount(0);
        conversation.setVersion(0);
        conversation.setCreatedAt(now);
        conversation.setUpdatedAt(now);

        for (int attempt = 0; attempt < 3; attempt++) {
            conversation.setConversationNo(nextConversationNo());
            try {
                conversationMapper.insert(conversation);
                break;
            } catch (DuplicateKeyException ex) {
                if (attempt == 2) {
                    throw ex;
                }
            }
        }

        if (StringUtils.hasText(req.getFirstContent())) {
            MessageView firstMessage = messageService.saveMessage(conversation.getId(),
                    java.util.UUID.randomUUID().toString(),
                    SenderType.USER, userId, req.getFirstContent(), false);
            aiTaskService.enqueue(conversation, firstMessage.getMessageId(), conversation.getRelatedOrderId());
            notifyMessage(conversation, firstMessage);
        }
        return toView(requireConversation(conversation.getId()));
    }

    /** 保存用户消息并排入持久任务队列，模型请求由独立工作线程执行。 */
    @Transactional
    public AiResponseView aiRespond(Long userId, Long conversationId, MessageSendRequest req) {
        validateMessageRequest(req);
        CustomerConversation conversation = lockConversation(conversationId);
        assertInitiator(conversation, userId);
        assertNotClosed(conversation);
        Long relatedOrderId = req.getRelatedOrderId() == null ? conversation.getRelatedOrderId() : req.getRelatedOrderId();
        if (relatedOrderId != null && relatedOrderId > 0) {
            assertOrderOwner(userId, relatedOrderId);
        }
        MessageView userMessage = messageService.saveMessage(conversationId, req.getClientMsgId(),
                SenderType.USER, userId, req.getContent(), false);

        AiReplyTask task = aiTaskService.forMessage(userMessage.getMessageId());
        if (Boolean.TRUE.equals(req.getRequestHuman())) {
            transferToHuman(conversation, "用户主动要求转人工");
        } else if (ConversationStatus.AI_PROCESSING.name().equals(conversation.getConversationStatus())) {
            task = aiTaskService.enqueue(conversation, userMessage.getMessageId(), relatedOrderId);
        }
        CustomerConversation refreshed = requireConversation(conversationId);
        notifyMessage(refreshed, userMessage);
        ConversationView view = toView(refreshed);
        notifyAll(refreshed, view);
        return AiResponseView.builder()
                .conversationId(conversationId)
                .conversationStatus(view.getConversationStatus())
                .decision(Boolean.TRUE.equals(req.getRequestHuman()) ? AiDecision.TRANSFER_HUMAN.name()
                        : AiDecision.CONTINUE_AI.name())
                .userMessageId(userMessage.getMessageId())
                .task(AiTaskView.from(task == null ? null : aiTaskMapper.selectById(task.getId())))
                .transferReason(view.getTransferReason())
                .build();
    }

    /** 完成任务前重查会话状态，确保转人工后的迟到回答不会落库。 */
    @Transactional
    public void completeAiTask(Long taskId, AiProcessResult result, String failureCode) {
        AiReplyTask candidate = aiTaskMapper.selectById(taskId);
        if (candidate == null) {
            return;
        }
        CustomerConversation conversation = lockConversation(candidate.getConversationId());
        AiReplyTask task = aiTaskMapper.selectForUpdate(taskId);
        if (!"RUNNING".equals(task.getStatus()) && !(failureCode != null && "PENDING".equals(task.getStatus()))) {
            return;
        }
        if (!ConversationStatus.AI_PROCESSING.name().equals(conversation.getConversationStatus())) {
            aiTaskService.finish(task, "CANCELLED", "CONVERSATION_CHANGED");
        } else if (failureCode != null) {
            aiTaskService.finish(task, "FAILED", failureCode);
            MessageView failure = messageService.saveMessage(conversation.getId(), "ai-task-" + taskId,
                    SenderType.SYSTEM, 0L, "AI 客服暂时无法完成回答，已为您转接人工客服。", false);
            notifyMessage(conversation, failure);
            transferToHuman(conversation, "AI 回答未完成，转人工继续处理");
        } else {
            aiTaskService.finish(task, result.response().fallback() ? "FALLBACK" : "COMPLETED",
                    result.response().errorCode());
            if (StringUtils.hasText(result.response().content())) {
                MessageView aiMessage = messageService.saveMessage(conversation.getId(), "ai-task-" + taskId,
                        SenderType.AI, 0L, result.response().content(), true);
                notifyMessage(conversation, aiMessage);
            }
            if (result.decision() == AiDecision.TRANSFER_HUMAN) {
                transferToHuman(conversation, result.reason());
            }
        }
        aiTaskService.notifyStatus(task, conversation.getInitiatorUserId());
        CustomerConversation refreshed = requireConversation(conversation.getId());
        notifyAll(refreshed, toView(refreshed));
    }

    /** 用户主动转人工（FR-C10） */
    @Transactional
    public ConversationView requestHuman(Long userId, Long conversationId, String reason) {
        CustomerConversation conversation = lockConversation(conversationId);
        assertInitiator(conversation, userId);
        assertNotClosed(conversation);
        transferToHuman(conversation, StringUtils.hasText(reason) ? reason : "用户主动要求转人工");
        CustomerConversation refreshed = requireConversation(conversationId);
        ConversationView view = toView(refreshed);
        notifyAll(refreshed, view);
        return view;
    }

    /** 我的会话列表（FR-C14：仅本人历史会话，不含内部备注） */
    public List<ConversationView> listMine(Long userId) {
        return conversationMapper.selectList(new LambdaQueryWrapper<CustomerConversation>()
                        .eq(CustomerConversation::getInitiatorUserId, userId)
                        .orderByDesc(CustomerConversation::getId))
                .stream().map(this::toView).toList();
    }

    /** 按 ID 查询会话（只读，供 WS 路由器使用） */
    public CustomerConversation findById(Long conversationId) {
        return conversationMapper.selectById(conversationId);
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

    /** 人工会话队列（FR-C24）：等待人工接待的会话，批量获取最新用户消息与排队时长 */
    public List<QueueItemView> queue(Long csUserId) {
        csAccountService.assertAvailable(csUserId);
        List<CustomerConversation> waiting = conversationMapper.selectList(
                new LambdaQueryWrapper<CustomerConversation>()
                        .eq(CustomerConversation::getConversationStatus, ConversationStatus.WAITING_HUMAN.name())
                        .orderByAsc(CustomerConversation::getId));
        if (waiting.isEmpty()) {
            return List.of();
        }
        List<Long> ids = waiting.stream().map(CustomerConversation::getId).toList();
        Map<Long, String> latestTopics = messageMapper.selectLatestUserMessages(ids).stream()
                .collect(Collectors.toMap(CustomerServiceMessage::getConversationId,
                        CustomerServiceMessage::getContent, (a, b) -> b));
        Map<Long, LocalDateTime> queuedTimes = assignmentMapper.selectLatestQueueTimes(ids).stream()
                .filter(row -> row.getQueuedAt() != null)
                .collect(Collectors.toMap(ConversationAssignmentMapper.ConversationQueuedAt::getConversationId,
                        ConversationAssignmentMapper.ConversationQueuedAt::getQueuedAt, (a, b) -> b));

        LocalDateTime now = LocalDateTime.now();
        return waiting.stream()
                .map(c -> toQueueItem(c, latestTopics.get(c.getId()), queuedTimes.get(c.getId()), now))
                .sorted(Comparator.comparing(QueueItemView::getQueueSeconds, Comparator.reverseOrder())
                        .thenComparing(QueueItemView::getConversationId))
                .toList();
    }

    /** 领取会话（FR-C25）：先锁定客服行再校验容量并锁定会话，避免并发超上限或锁序死锁 */
    @Transactional
    public ConversationView claim(Long csUserId, Long conversationId, ConversationClaimRequest req) {
        CustomerServiceAccount account = csAccountService.lockAndAssertAvailable(csUserId);
        long active = conversationMapper.selectCount(new LambdaQueryWrapper<CustomerConversation>()
                .eq(CustomerConversation::getCurrentCsAccountId, account.getId())
                .eq(CustomerConversation::getConversationStatus, ConversationStatus.HUMAN_PROCESSING.name()));
        if (active >= account.getMaxActiveConversations()) {
            throw new BusinessException(ErrorCode.CS_AGENT_CAPACITY_EXCEEDED);
        }

        CustomerConversation conversation = lockConversation(conversationId);
        if (!ConversationStatus.WAITING_HUMAN.name().equals(conversation.getConversationStatus())) {
            throw new BusinessException(ErrorCode.CS_CONVERSATION_ALREADY_CLAIMED, "会话不在等待人工状态");
        }
        if (!req.getExpectedVersion().equals(conversation.getVersion())) {
            throw new BusinessException(ErrorCode.CS_CONVERSATION_VERSION_CONFLICT);
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
        CustomerConversation refreshed = requireConversation(conversationId);
        ConversationView view = toView(refreshed);
        notifyAll(refreshed, view);
        return view;
    }

    /** 客服回复消息（FR-C26）：仅当前启用客服且处于 HUMAN_PROCESSING */
    @Transactional
    public MessageView reply(Long csUserId, Long conversationId, MessageSendRequest req) {
        validateMessageRequest(req);
        CustomerConversation conversation = lockConversation(conversationId);
        csAccountService.assertCurrentHandler(csUserId, conversation.getCurrentCsAccountId());
        assertNotClosed(conversation);
        if (!ConversationStatus.HUMAN_PROCESSING.name().equals(conversation.getConversationStatus())) {
            throw new BusinessException(ErrorCode.CS_CONVERSATION_STATUS_INVALID, "仅人工处理中的会话允许客服回复");
        }
        MessageView message = messageService.saveMessage(conversationId, req.getClientMsgId(),
                SenderType.CS, csUserId, req.getContent(), false);
        notifyMessage(conversation, message);
        return message;
    }

    /** 关闭会话（FR-C29）：处理分类 + 结果（仅 HUMAN_PROCESSING） */
    @Transactional
    public ConversationView close(Long csUserId, Long conversationId, CloseConversationRequest req) {
        CustomerConversation conversation = lockConversation(conversationId);
        csAccountService.assertCurrentHandler(csUserId, conversation.getCurrentCsAccountId());
        assertNotClosed(conversation);
        if (!ConversationStatus.HUMAN_PROCESSING.name().equals(conversation.getConversationStatus())) {
            throw new BusinessException(ErrorCode.CS_CONVERSATION_STATUS_INVALID, "仅人工处理中的会话允许客服关闭");
        }
        aiTaskService.cancelActive(conversation);
        conversationMapper.update(null, new LambdaUpdateWrapper<CustomerConversation>()
                .set(CustomerConversation::getConversationStatus, ConversationStatus.CLOSED.name())
                .set(CustomerConversation::getClosedCategory, req.getCategory())
                .set(CustomerConversation::getClosedResult, req.getResult())
                .set(CustomerConversation::getClosedAt, LocalDateTime.now())
                .setSql("version = version + 1")
                .eq(CustomerConversation::getId, conversationId)
                .eq(CustomerConversation::getVersion, conversation.getVersion()));
        MessageView closeMsg = messageService.saveMessage(conversationId, "sys-close-" + java.util.UUID.randomUUID(),
                SenderType.SYSTEM, 0L, "会话已关闭：" + req.getCategory(), false);
        CustomerConversation refreshed = requireConversation(conversationId);
        notifyMessage(refreshed, closeMsg);
        ConversationView view = toView(refreshed);
        notifyAll(refreshed, view);
        return view;
    }

    /** 转交管理员（FR-C28）：清空 currentCsAccountId 并释放客服接待名额 */
    @Transactional
    public ConversationView escalate(Long csUserId, Long conversationId, EscalateRequest req) {
        CustomerConversation conversation = lockConversation(conversationId);
        csAccountService.assertCurrentHandler(csUserId, conversation.getCurrentCsAccountId());
        if (!ConversationStatus.HUMAN_PROCESSING.name().equals(conversation.getConversationStatus())) {
            throw new BusinessException(ErrorCode.CS_CONVERSATION_STATUS_INVALID, "当前状态不允许转交管理员");
        }
        Long fromCsAccountId = conversation.getCurrentCsAccountId();
        int updated = conversationMapper.update(null, new LambdaUpdateWrapper<CustomerConversation>()
                .set(CustomerConversation::getConversationStatus, ConversationStatus.ESCALATED_ADMIN.name())
                .set(CustomerConversation::getReceptionMode, ReceptionMode.ADMIN.name())
                .set(CustomerConversation::getCurrentCsAccountId, 0L)
                .set(CustomerConversation::getTransferReason, req.getReason())
                .setSql("version = version + 1")
                .eq(CustomerConversation::getId, conversationId)
                .eq(CustomerConversation::getVersion, conversation.getVersion())
                .eq(CustomerConversation::getConversationStatus, ConversationStatus.HUMAN_PROCESSING.name()));
        if (updated == 0) {
            throw new BusinessException(ErrorCode.CS_CONVERSATION_VERSION_CONFLICT);
        }
        recordAssignment(conversationId, AssignmentType.ESCALATE_ADMIN,
                fromCsAccountId, 0L, csUserId, req.getReason());
        MessageView escalateMsg = messageService.saveMessage(conversationId,
                "sys-escalate-" + java.util.UUID.randomUUID(),
                SenderType.SYSTEM, 0L, "会话已转交管理员处理：" + req.getReason(), false);
        CustomerConversation refreshed = requireConversation(conversationId);
        messageNotifier.notifyMessage(escalateMsg, refreshed.getInitiatorUserId(), csUserId);
        ConversationView view = toView(refreshed);
        messageNotifier.notifyConversationChanged(view, refreshed.getInitiatorUserId(), csUserId);
        return view;
    }

    /** 我的处理中会话（客服：仅 HUMAN_PROCESSING） */
    public List<ConversationView> listAssigned(Long csUserId) {
        CustomerServiceAccount account = csAccountService.getByUserId(csUserId);
        if (account == null) {
            return List.of();
        }
        return conversationMapper.selectList(new LambdaQueryWrapper<CustomerConversation>()
                        .eq(CustomerConversation::getCurrentCsAccountId, account.getId())
                        .eq(CustomerConversation::getConversationStatus, ConversationStatus.HUMAN_PROCESSING.name())
                        .orderByDesc(CustomerConversation::getId))
                .stream().map(this::toView).toList();
    }

    // ==================== 管理端：升级会话处理 ====================

    /** 管理端分页查询升级给管理员的会话 */
    public Page<ConversationView> listEscalated(String status, long page, long size) {
        String target = StringUtils.hasText(status) ? status.trim() : ConversationStatus.ESCALATED_ADMIN.name();
        if (!ConversationStatus.ESCALATED_ADMIN.name().equals(target)
                && !ConversationStatus.CLOSED.name().equals(target)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "不支持的升级会话状态过滤");
        }
        LambdaQueryWrapper<CustomerConversation> wrapper = new LambdaQueryWrapper<CustomerConversation>()
                .eq(CustomerConversation::getConversationStatus, target)
                .orderByDesc(CustomerConversation::getId);
        if (ConversationStatus.CLOSED.name().equals(target)) {
            wrapper.eq(CustomerConversation::getReceptionMode, ReceptionMode.ADMIN.name());
        }
        Page<CustomerConversation> p = conversationMapper.selectPage(new Page<>(page, size), wrapper);
        Page<ConversationView> vp = new Page<>(p.getCurrent(), p.getSize(), p.getTotal());
        vp.setRecords(p.getRecords().stream().map(this::toView).toList());
        return vp;
    }

    /** 管理员回复已升级会话 */
    @Transactional
    public MessageView adminReply(Long adminUserId, Long conversationId, MessageSendRequest req) {
        validateMessageRequest(req);
        CustomerConversation conversation = lockConversation(conversationId);
        assertNotClosed(conversation);
        if (!ConversationStatus.ESCALATED_ADMIN.name().equals(conversation.getConversationStatus())) {
            throw new BusinessException(ErrorCode.CS_CONVERSATION_STATUS_INVALID, "仅已升级管理员的会话允许管理员回复");
        }
        MessageView message = messageService.saveMessage(conversationId, req.getClientMsgId(),
                SenderType.ADMIN, adminUserId, req.getContent(), false);
        messageNotifier.notifyMessage(message, conversation.getInitiatorUserId(), adminUserId);
        return message;
    }

    /** 管理员关闭已升级会话 */
    @Transactional
    public ConversationView adminClose(Long adminUserId, Long conversationId, CloseConversationRequest req) {
        CustomerConversation conversation = lockConversation(conversationId);
        assertNotClosed(conversation);
        if (!ConversationStatus.ESCALATED_ADMIN.name().equals(conversation.getConversationStatus())) {
            throw new BusinessException(ErrorCode.CS_CONVERSATION_STATUS_INVALID, "仅已升级管理员的会话允许管理员关闭");
        }
        aiTaskService.cancelActive(conversation);
        conversationMapper.update(null, new LambdaUpdateWrapper<CustomerConversation>()
                .set(CustomerConversation::getConversationStatus, ConversationStatus.CLOSED.name())
                .set(CustomerConversation::getClosedCategory, req.getCategory())
                .set(CustomerConversation::getClosedResult, req.getResult())
                .set(CustomerConversation::getClosedAt, LocalDateTime.now())
                .setSql("version = version + 1")
                .eq(CustomerConversation::getId, conversationId)
                .eq(CustomerConversation::getVersion, conversation.getVersion()));
        MessageView closeMsg = messageService.saveMessage(conversationId, "sys-close-" + java.util.UUID.randomUUID(),
                SenderType.SYSTEM, 0L, "会话已由管理员关闭：" + req.getCategory(), false);
        CustomerConversation refreshed = requireConversation(conversationId);
        messageNotifier.notifyMessage(closeMsg, refreshed.getInitiatorUserId(), adminUserId);
        ConversationView view = toView(refreshed);
        messageNotifier.notifyConversationChanged(view, refreshed.getInitiatorUserId(), adminUserId);
        return view;
    }

    // ==================== 内部方法 ====================

    /** 服务入口也校验外部幂等键，避免直接调用绕过 HTTP/WS 校验并占用系统消息键。 */
    private void validateMessageRequest(MessageSendRequest request) {
        if (request == null) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED);
        }
        var violations = validator.validate(request);
        if (!violations.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, violations.iterator().next().getMessage());
        }
    }

    /** AI 转人工：状态 → WAITING_HUMAN，写轨迹与系统消息 */
    private void transferToHuman(CustomerConversation conversation, String reason) {
        aiTaskService.cancelActive(conversation);
        if (!ConversationStatus.AI_PROCESSING.name().equals(conversation.getConversationStatus())) {
            return;
        }
        conversationMapper.update(null, new LambdaUpdateWrapper<CustomerConversation>()
                .set(CustomerConversation::getConversationStatus, ConversationStatus.WAITING_HUMAN.name())
                .set(CustomerConversation::getReceptionMode, ReceptionMode.HUMAN.name())
                .set(CustomerConversation::getTransferReason, reason)
                .setSql("version = version + 1")
                .eq(CustomerConversation::getId, conversation.getId())
                .eq(CustomerConversation::getVersion, conversation.getVersion()));
        recordAssignment(conversation.getId(), AssignmentType.AI_TRANSFER, 0L, 0L, 0L, reason);
        MessageView message = messageService.saveMessage(conversation.getId(),
                "sys-transfer-" + java.util.UUID.randomUUID(),
                SenderType.SYSTEM, 0L, "已转人工客服，请稍候，客服人员将尽快接入。", false);
        notifyMessage(conversation, message);
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
        assignment.setCreatedAt(LocalDateTime.now());
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

    /** 会话编号：CS + 时间戳 + 6 位非负并发安全序号 */
    private String nextConversationNo() {
        int seq = Math.floorMod(CONVERSATION_SEQ.getAndIncrement(), 1000);
        int rand = ThreadLocalRandom.current().nextInt(1000);
        return "CS" + LocalDateTime.now().format(NO_FORMAT)
                + String.format(Locale.ROOT, "%03d%03d", seq, rand);
    }

    private void validateRelatedOrder(Long userId, String sourceType, Long relatedOrderId) {
        if ("ORDER".equals(sourceType)) {
            if (relatedOrderId == null || relatedOrderId <= 0) {
                throw new BusinessException(ErrorCode.VALIDATION_FAILED, "订单来源必须关联订单");
            }
        }
        if (relatedOrderId != null && relatedOrderId > 0) {
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

    private CustomerConversation requireConversation(Long conversationId) {
        CustomerConversation conversation = conversationMapper.selectById(conversationId);
        if (conversation == null) {
            throw new BusinessException(ErrorCode.CS_CONVERSATION_NOT_FOUND);
        }
        return conversation;
    }

    private CustomerConversation lockConversation(Long conversationId) {
        CustomerConversation conversation = conversationMapper.selectForUpdate(conversationId);
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

    private QueueItemView toQueueItem(CustomerConversation c, String latestTopic,
                                      LocalDateTime queuedAt, LocalDateTime now) {
        String topic = StringUtils.hasText(latestTopic) ? latestTopic : c.getSourceType();
        LocalDateTime since = queuedAt != null ? queuedAt
                : (c.getCreatedAt() != null ? c.getCreatedAt() : now);
        long waitSeconds = Math.max(0L, Duration.between(since, now).getSeconds());
        return QueueItemView.builder()
                .conversationId(c.getId())
                .conversationNo(c.getConversationNo())
                .topic(topic.length() > 50 ? topic.substring(0, 50) : topic)
                .sourceType(c.getSourceType())
                .relatedOrderId(c.getRelatedOrderId())
                .transferReason(c.getTransferReason())
                .version(c.getVersion())
                .queueSeconds(waitSeconds)
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
                .aiTask(aiTaskService.latest(c.getId()))
                .version(c.getVersion())
                .createdAt(c.getCreatedAt())
                .closedAt(c.getClosedAt())
                .build();
    }
}
