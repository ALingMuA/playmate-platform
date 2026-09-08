package com.gameplay.customer_service;

import com.gameplay.ai.config.AiModelProperties;
import com.gameplay.ai.task.domain.AiReplyTask;
import com.gameplay.ai.task.mapper.AiReplyTaskMapper;
import com.gameplay.ai.task.service.AiReplyTaskWorker;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiReplyTaskRecoveryTest {
    @Test
    void oneBrokenTaskDoesNotPreventRecoveryOfLaterTasks() {
        AiReplyTaskMapper mapper = mock(AiReplyTaskMapper.class);
        AiReplyTask first = new AiReplyTask();
        first.setId(1L);
        AiReplyTask second = new AiReplyTask();
        second.setId(2L);
        when(mapper.selectList(any())).thenReturn(List.of(first, second));
        AiReplyTaskWorker worker = spy(new AiReplyTaskWorker(mapper, null, null, null, null,
                null, null, null, new AiModelProperties(), null));
        doThrow(new IllegalStateException("故障任务")).when(worker).recoverExpired(1L);
        doNothing().when(worker).recoverExpired(2L);

        assertThatCode(worker::recoverExpired).doesNotThrowAnyException();
        verify(worker).recoverExpired(2L);
    }
}
