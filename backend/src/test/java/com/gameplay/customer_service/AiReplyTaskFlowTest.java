package com.gameplay.customer_service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.gameplay.ai.enums.AiDecision;
import com.gameplay.ai.service.AiResponseFacade;
import com.gameplay.ai.strategy.AiProcessResult;
import com.gameplay.ai.strategy.AiRequest;
import com.gameplay.ai.strategy.AiResponse;
import com.gameplay.ai.task.domain.AiReplyTask;
import com.gameplay.ai.task.mapper.AiReplyTaskMapper;
import com.gameplay.ai.task.service.AiReplyTaskWorker;
import com.gameplay.common.exception.BusinessException;
import com.gameplay.customer_service.domain.CustomerServiceMessage;
import com.gameplay.customer_service.dto.AiResponseView;
import com.gameplay.customer_service.dto.ConversationCreateRequest;
import com.gameplay.customer_service.dto.ConversationView;
import com.gameplay.customer_service.dto.InternalNoteRequest;
import com.gameplay.customer_service.dto.MessageSendRequest;
import com.gameplay.customer_service.enums.SenderType;
import com.gameplay.customer_service.mapper.CustomerServiceMessageMapper;
import com.gameplay.customer_service.service.CustomerConversationService;
import com.gameplay.customer_service.service.CustomerMessageService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 使用真实提交和独立线程，验证模型调用期间没有数据库事务及跨线程状态竞争。 */
@SpringBootTest(properties = "app.ai.tasks.requests-per-minute=100")
@ActiveProfiles("test")
class AiReplyTaskFlowTest {
    @Autowired private CustomerConversationService conversations;
    @Autowired private CustomerMessageService messages;
    @Autowired private CustomerServiceMessageMapper messageMapper;
    @Autowired private AiReplyTaskMapper taskMapper;
    @Autowired private AiReplyTaskWorker worker;
    @Autowired private JdbcTemplate jdbc;
    @MockitoBean private AiResponseFacade facade;

    private final Long userId = 9_000_000_000L + Math.abs((long) UUID.randomUUID().hashCode());
    private final List<Long> createdConversations = new ArrayList<>();

    @AfterEach
    void removeOnlyOwnFixtures() {
        for (Long id : createdConversations) {
            jdbc.update("DELETE FROM ai_reply_task WHERE conversation_id = ?", id);
            jdbc.update("DELETE FROM ai_call_log WHERE conversation_id = ?", id);
            jdbc.update("DELETE FROM conversation_assignment WHERE conversation_id = ?", id);
            jdbc.update("DELETE FROM internal_note WHERE conversation_id = ?", id);
            jdbc.update("DELETE FROM customer_service_message WHERE conversation_id = ?", id);
            jdbc.update("DELETE FROM customer_conversation WHERE id = ?", id);
        }
    }

    @Test
    void firstQuestionIsQueuedOnceAndModelRunsOutsideTransaction() throws Exception {
        when(facade.process(any())).thenAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            return answer();
        });
        ConversationView conversation = create("如何预约陪玩");
        assertThat(conversation.getAiTask().status()).isEqualTo("PENDING");
        verify(facade, never()).process(any());
        CompletableFuture.runAsync(() -> worker.process(conversation.getAiTask().id())).get(10, TimeUnit.SECONDS);
        assertThat(taskMapper.selectById(conversation.getAiTask().id()).getStatus()).isEqualTo("COMPLETED");
        CustomerServiceMessage first = userMessages(conversation.getId()).get(0);
        AiResponseView duplicate = conversations.aiRespond(userId, conversation.getId(),
                request(first.getClientMsgId(), first.getContent()));
        assertThat(duplicate.getTask().id()).isEqualTo(conversation.getAiTask().id());
        worker.process(duplicate.getTask().id());
        verify(facade, times(1)).process(any());
        assertThat(userMessages(conversation.getId())).hasSize(1);
        assertThat(aiMessages(conversation.getId())).hasSize(1);
    }

    @Test
    void handoffCancelsRunningTaskAndDiscardsLateAnswer() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        when(facade.process(any())).thenAnswer(invocation -> {
            entered.countDown();
            assertThat(release.await(10, TimeUnit.SECONDS)).isTrue();
            return answer();
        });
        ConversationView conversation = create("请帮我查询预约流程");
        CompletableFuture<Void> processing = CompletableFuture.runAsync(() -> worker.process(conversation.getAiTask().id()));
        try {
            assertThat(entered.await(10, TimeUnit.SECONDS)).isTrue();
            ConversationView transferred = conversations.requestHuman(userId, conversation.getId(), "需要人工");
            assertThat(transferred.getConversationStatus()).isEqualTo("WAITING_HUMAN");
            assertThat(transferred.getAiTask().status()).isEqualTo("CANCELLED");
        } finally {
            release.countDown();
            processing.get(10, TimeUnit.SECONDS);
        }
        assertThat(aiMessages(conversation.getId())).isEmpty();
    }

    @Test
    void concurrentReplayIsIdempotentAndConflictingMessageIsRejected() throws Exception {
        ConversationView conversation = create(null);
        String clientId = UUID.randomUUID().toString();
        CountDownLatch start = new CountDownLatch(1);
        var first = CompletableFuture.supplyAsync(() -> sendAfter(start, conversation.getId(), clientId));
        var second = CompletableFuture.supplyAsync(() -> sendAfter(start, conversation.getId(), clientId));
        start.countDown();
        assertThat(first.get(10, TimeUnit.SECONDS).getTask().id())
                .isEqualTo(second.get(10, TimeUnit.SECONDS).getTask().id());
        assertThat(userMessages(conversation.getId())).hasSize(1);
        assertThatThrownBy(() -> conversations.aiRespond(userId, conversation.getId(), request(clientId, "被篡改的内容")))
                .isInstanceOf(BusinessException.class).hasMessageContaining("幂等键");
        ConversationView other = create(null);
        assertThatThrownBy(() -> conversations.aiRespond(userId, other.getId(), request(clientId, "如何预约")))
                .isInstanceOf(BusinessException.class).hasMessageContaining("幂等键");
        assertThat(userMessages(other.getId())).isEmpty();
        assertThatThrownBy(() -> conversations.aiRespond(userId + 1, conversation.getId(),
                request(UUID.randomUUID().toString(), "越权消息")))
                .isInstanceOf(BusinessException.class).hasMessageContaining("数据访问权限");
        assertThatThrownBy(() -> conversations.aiRespond(userId, conversation.getId(),
                request(UUID.randomUUID().toString(), "下一条问题")))
                .isInstanceOf(BusinessException.class).hasMessageContaining("上一条问题");
        assertThat(userMessages(conversation.getId())).hasSize(1);
    }

    @Test
    void expiredTasksFailWithoutRepeatingModelCalls() {
        ConversationView running = create("已经执行但未完成");
        jdbc.update("UPDATE ai_reply_task SET status = 'RUNNING', started_at = DATE_SUB(NOW(), INTERVAL 5 MINUTE), "
                + "updated_at = DATE_SUB(NOW(), INTERVAL 5 MINUTE) WHERE id = ?", running.getAiTask().id());
        ConversationView pending = create("排队过久");
        jdbc.update("UPDATE ai_reply_task SET created_at = DATE_SUB(NOW(), INTERVAL 5 MINUTE), "
                + "updated_at = DATE_SUB(NOW(), INTERVAL 5 MINUTE) WHERE id = ?", pending.getAiTask().id());
        for (ConversationView conversation : List.of(running, pending)) {
            worker.recoverExpired(conversation.getAiTask().id());
            AiReplyTask task = taskMapper.selectById(conversation.getAiTask().id());
            assertThat(task.getStatus()).isEqualTo("FAILED");
            assertThat(task.getErrorCode()).isEqualTo("TASK_TIMEOUT");
            worker.process(task.getId());
            assertThat(conversations.getDetail(conversation.getId(), userId, List.of()).getConversationStatus())
                    .isEqualTo("WAITING_HUMAN");
        }
        verify(facade, never()).process(any());
    }

    @Test
    void externalMessageIdsCannotReserveSystemReplyKeys() {
        when(facade.process(any())).thenReturn(answer());
        ConversationView victim = create("正常的咨询");
        ConversationView attacker = create(null);
        for (String clientId : List.of("ai-task-" + victim.getAiTask().id(), "sys-transfer-reserved", "cs-reserved")) {
            MessageSendRequest poison = request(clientId, "尝试占用系统回复键");
            poison.setRequestHuman(true);
            assertThatThrownBy(() -> conversations.aiRespond(userId, attacker.getId(), poison))
                    .isInstanceOf(BusinessException.class).hasMessageContaining("标准 UUID");
            assertThatThrownBy(() -> conversations.reply(userId, attacker.getId(), poison))
                    .isInstanceOf(BusinessException.class).hasMessageContaining("标准 UUID");
        }
        assertThat(userMessages(attacker.getId())).isEmpty();
        worker.process(victim.getAiTask().id());
        assertThat(taskMapper.selectById(victim.getAiTask().id()).getStatus()).isEqualTo("COMPLETED");
        assertThat(aiMessages(victim.getId())).hasSize(1);
    }

    @Test
    void historyContainsOnlyPriorUserAndAiMessagesFromCurrentConversation() {
        AtomicReference<AiRequest> captured = new AtomicReference<>();
        doAnswer(invocation -> {
            captured.set(invocation.getArgument(0));
            return answer();
        }).when(facade).process(any());
        ConversationView conversation = create("第一问");
        worker.process(conversation.getAiTask().id());
        create("其他会话的内容");
        messages.saveMessage(conversation.getId(), UUID.randomUUID().toString(), SenderType.CS, 0L,
                "人工消息不作为模型历史", false);
        InternalNoteRequest note = new InternalNoteRequest();
        note.setContent("内部备注不发送给模型");
        messages.addInternalNote(0L, "ADMIN", conversation.getId(), note);
        AiResponseView response = conversations.aiRespond(userId, conversation.getId(),
                request(UUID.randomUUID().toString(), "第二问"));
        worker.process(response.getTask().id());
        assertThat(captured.get().history()).containsExactly(new AiRequest.ContextMessage("user", "第一问"),
                new AiRequest.ContextMessage("assistant", "【AI客服】可在服务列表选择陪玩并预约。"));
        assertThat(captured.get().content()).isEqualTo("第二问");
    }

    private ConversationView create(String content) {
        ConversationCreateRequest request = new ConversationCreateRequest();
        request.setSourceType("HELP_CENTER");
        request.setFirstContent(content);
        ConversationView conversation = conversations.create(userId, request);
        createdConversations.add(conversation.getId());
        return conversation;
    }

    private AiResponseView sendAfter(CountDownLatch start, Long conversationId, String clientId) {
        try {
            if (!start.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("未收到并发测试启动信号");
            }
            return conversations.aiRespond(userId, conversationId, request(clientId, "如何预约"));
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(ex);
        }
    }

    private MessageSendRequest request(String clientId, String content) {
        MessageSendRequest request = new MessageSendRequest();
        request.setClientMsgId(clientId);
        request.setContent(content);
        return request;
    }

    private List<CustomerServiceMessage> userMessages(Long conversationId) {
        return messageMapper.selectList(new LambdaQueryWrapper<CustomerServiceMessage>()
                .eq(CustomerServiceMessage::getConversationId, conversationId)
                .eq(CustomerServiceMessage::getSenderType, SenderType.USER.name()));
    }

    private List<CustomerServiceMessage> aiMessages(Long conversationId) {
        return messageMapper.selectList(new LambdaQueryWrapper<CustomerServiceMessage>()
                .eq(CustomerServiceMessage::getConversationId, conversationId)
                .eq(CustomerServiceMessage::getSenderType, SenderType.AI.name()));
    }

    private AiProcessResult answer() {
        return new AiProcessResult(AiResponse.success("【AI客服】可在服务列表选择陪玩并预约。", 0.9,
                "MODEL", 0L), AiDecision.CONTINUE_AI, "");
    }
}
