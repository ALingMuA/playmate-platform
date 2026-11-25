package com.gameplay.customer_service.service;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.gameplay.ai.task.service.AiReplyTaskService;
import com.gameplay.common.enums.ConversationStatus;
import com.gameplay.customer_service.domain.ConversationAssignment;
import com.gameplay.customer_service.domain.CustomerConversation;
import com.gameplay.customer_service.domain.CustomerServiceMessage;
import com.gameplay.customer_service.dto.ConversationView;
import com.gameplay.customer_service.dto.MessageView;
import com.gameplay.customer_service.enums.AssignmentType;
import com.gameplay.customer_service.enums.ReceptionMode;
import com.gameplay.customer_service.enums.SenderType;
import com.gameplay.customer_service.mapper.ConversationAssignmentMapper;
import com.gameplay.customer_service.mapper.CustomerConversationMapper;
import com.gameplay.customer_service.mapper.CustomerServiceMessageMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 客服会话重排队协作者：在客服账号被禁用时释放名下人工会话回队列，
 * 避免与 {@link CsAccountService} / {@link CustomerConversationService} 形成循环依赖。
 */
@Service
@RequiredArgsConstructor
public class ConversationRequeueService {

    private final CustomerConversationMapper conversationMapper;
    private final ConversationAssignmentMapper assignmentMapper;
    private final CustomerServiceMessageMapper messageMapper;
    private final AiReplyTaskService aiTaskService;
    private final CsMessageNotifier messageNotifier;

    /** 将指定客服名下所有 HUMAN_PROCESSING 会话释放回 WAITING_HUMAN（调用方已锁定客服行） */
    @Transactional
    public void releaseActiveForCsAccount(Long csAccountId, Long operatorId, String reason) {
        if (csAccountId == null || csAccountId <= 0) {
            return;
        }
        List<CustomerConversation> active = conversationMapper.selectActiveHumanProcessingForUpdate(csAccountId);
        for (CustomerConversation c : active) {
            int updated = conversationMapper.update(null, new LambdaUpdateWrapper<CustomerConversation>()
                    .set(CustomerConversation::getConversationStatus, ConversationStatus.WAITING_HUMAN.name())
                    .set(CustomerConversation::getReceptionMode, ReceptionMode.HUMAN.name())
                    .set(CustomerConversation::getCurrentCsAccountId, 0L)
                    .set(CustomerConversation::getTransferReason, reason)
                    .setSql("version = version + 1")
                    .eq(CustomerConversation::getId, c.getId())
                    .eq(CustomerConversation::getVersion, c.getVersion())
                    .eq(CustomerConversation::getConversationStatus, ConversationStatus.HUMAN_PROCESSING.name()));
            if (updated == 0) {
                continue;
            }
            ConversationAssignment assignment = new ConversationAssignment();
            assignment.setConversationId(c.getId());
            assignment.setAssignmentType(AssignmentType.TRANSFER.name());
            assignment.setFromCsAccountId(csAccountId);
            assignment.setToCsAccountId(0L);
            assignment.setOperatorId(operatorId == null ? 0L : operatorId);
            assignment.setReason(reason == null ? "" : reason);
            assignment.setCreatedAt(LocalDateTime.now());
            assignmentMapper.insert(assignment);

            CustomerServiceMessage systemMsg = new CustomerServiceMessage();
            systemMsg.setConversationId(c.getId());
            systemMsg.setClientMsgId("sys-requeue-" + UUID.randomUUID());
            systemMsg.setSenderType(SenderType.SYSTEM.name());
            systemMsg.setSenderId(0L);
            systemMsg.setContentType("TEXT");
            systemMsg.setContent("当前客服已停用或下线，会话已重新进入人工排队，请稍候。");
            systemMsg.setAiMark(0);
            systemMsg.setReadStatus(0);
            systemMsg.setCreatedAt(LocalDateTime.now());
            messageMapper.insert(systemMsg);

            CustomerConversation refreshed = conversationMapper.selectById(c.getId());
            if (refreshed != null) {
                messageNotifier.notifyMessage(toMessageView(systemMsg), refreshed.getInitiatorUserId(), 0L);
                messageNotifier.notifyConversationChanged(toConversationView(refreshed),
                        refreshed.getInitiatorUserId(), 0L);
            }
        }
    }

    private MessageView toMessageView(CustomerServiceMessage m) {
        return MessageView.builder()
                .messageId(m.getId())
                .clientMsgId(m.getClientMsgId())
                .conversationId(m.getConversationId())
                .senderType(m.getSenderType())
                .senderId(m.getSenderId())
                .contentType(m.getContentType())
                .content(m.getContent())
                .aiMark(m.getAiMark())
                .readStatus(m.getReadStatus())
                .createdAt(m.getCreatedAt())
                .build();
    }

    private ConversationView toConversationView(CustomerConversation c) {
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
