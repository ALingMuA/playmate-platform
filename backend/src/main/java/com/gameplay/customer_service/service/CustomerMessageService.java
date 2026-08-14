package com.gameplay.customer_service.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.gameplay.common.exception.BusinessException;
import com.gameplay.common.exception.ErrorCode;
import com.gameplay.customer_service.domain.CustomerServiceMessage;
import com.gameplay.customer_service.domain.InternalNote;
import com.gameplay.customer_service.dto.InternalNoteRequest;
import com.gameplay.customer_service.dto.InternalNoteView;
import com.gameplay.customer_service.dto.MessageView;
import com.gameplay.customer_service.enums.SenderType;
import com.gameplay.customer_service.mapper.CustomerServiceMessageMapper;
import com.gameplay.customer_service.mapper.InternalNoteMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 客服消息服务（FR-C08、FR-C26、FR-C27）。
 *
 * <p>消息以 clientMsgId 幂等（uk_csm_client_msg_id 唯一索引兜底）：
 * 重复提交返回既有消息；消息查询按 id 游标分页，供 WebSocket 断线后补拉（详细设计 5.2）。</p>
 */
@Service
@RequiredArgsConstructor
public class CustomerMessageService {

    public static final int DEFAULT_PAGE_SIZE = 50;
    public static final int MAX_PAGE_SIZE = 200;

    private final CustomerServiceMessageMapper messageMapper;
    private final InternalNoteMapper internalNoteMapper;

    /**
     * 保存消息（幂等）：clientMsgId 已存在时直接返回既有消息。
     *
     * @param senderType 发送者类型
     * @param senderId   发送者ID（AI/系统为0）
     * @param aiMark     是否 AI 生成
     */
    @Transactional
    public MessageView saveMessage(Long conversationId, String clientMsgId, SenderType senderType,
                                   Long senderId, String content, boolean aiMark) {
        if (!StringUtils.hasText(clientMsgId)) {
            clientMsgId = "sys-" + java.util.UUID.randomUUID();
        }
        CustomerServiceMessage existing = messageMapper.selectOne(new LambdaQueryWrapper<CustomerServiceMessage>()
                .eq(CustomerServiceMessage::getClientMsgId, clientMsgId));
        if (existing != null) {
            return toView(existing);
        }
        CustomerServiceMessage message = new CustomerServiceMessage();
        message.setConversationId(conversationId);
        message.setClientMsgId(clientMsgId);
        message.setSenderType(senderType.name());
        message.setSenderId(senderId == null ? 0L : senderId);
        message.setContentType("TEXT");
        message.setContent(content);
        message.setAiMark(aiMark ? 1 : 0);
        message.setReadStatus(0);
        try {
            messageMapper.insert(message);
        } catch (DuplicateKeyException e) {
            CustomerServiceMessage dup = messageMapper.selectOne(new LambdaQueryWrapper<CustomerServiceMessage>()
                    .eq(CustomerServiceMessage::getClientMsgId, clientMsgId));
            if (dup == null) {
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, "消息保存冲突");
            }
            return toView(dup);
        }
        return toView(message);
    }

    /** 会话消息游标分页补拉（详细设计 5.2：afterId 游标） */
    public List<MessageView> listMessages(Long conversationId, Long afterId, int size) {
        int limit = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        List<CustomerServiceMessage> messages = messageMapper.selectList(
                new LambdaQueryWrapper<CustomerServiceMessage>()
                        .eq(CustomerServiceMessage::getConversationId, conversationId)
                        .gt(afterId != null && afterId > 0, CustomerServiceMessage::getId, afterId)
                        .orderByAsc(CustomerServiceMessage::getId)
                        .last("LIMIT " + limit));
        return messages.stream().map(this::toView).toList();
    }

    /** 最新消息游标（新会话或重连时使用） */
    public long latestMessageId(Long conversationId) {
        CustomerServiceMessage last = messageMapper.selectOne(
                new LambdaQueryWrapper<CustomerServiceMessage>()
                        .eq(CustomerServiceMessage::getConversationId, conversationId)
                        .orderByDesc(CustomerServiceMessage::getId)
                        .last("LIMIT 1"));
        return last == null ? 0L : last.getId();
    }

    /** 客服/管理员添加内部备注（FR-C27，用户不可见） */
    @Transactional
    public InternalNoteView addInternalNote(Long authorUserId, String authorRole,
                                            Long conversationId, InternalNoteRequest req) {
        InternalNote note = new InternalNote();
        note.setConversationId(conversationId);
        note.setAuthorUserId(authorUserId);
        note.setAuthorRole(authorRole);
        note.setContent(req.getContent());
        internalNoteMapper.insert(note);
        return toView(note);
    }

    /** 会话内部备注列表（仅客服/管理员调用） */
    public List<InternalNoteView> listInternalNotes(Long conversationId) {
        return internalNoteMapper.selectList(new LambdaQueryWrapper<InternalNote>()
                        .eq(InternalNote::getConversationId, conversationId)
                        .orderByAsc(InternalNote::getId))
                .stream().map(this::toView).toList();
    }

    private MessageView toView(CustomerServiceMessage m) {
        return MessageView.builder()
                .messageId(m.getId())
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

    private InternalNoteView toView(InternalNote n) {
        return InternalNoteView.builder()
                .id(n.getId())
                .conversationId(n.getConversationId())
                .authorUserId(n.getAuthorUserId())
                .authorRole(n.getAuthorRole())
                .content(n.getContent())
                .createdAt(n.getCreatedAt())
                .build();
    }
}
