package com.gameplay.infrastructure.websocket;

import com.gameplay.auth.event.SessionInvalidatedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WebSocket 会话注册表：按用户ID维护在线连接（一个用户可多端连接）。
 *
 * <p>使用原子 Map 操作避免并发注册/注销竞态，并对每个底层会话包装
 * {@link ConcurrentWebSocketSessionDecorator} 以支持并发串行写入与有界缓冲保护。</p>
 */
@Slf4j
@Component
public class WsSessionRegistry {

    private static final int SEND_TIME_LIMIT_MS = 5_000;
    private static final int BUFFER_SIZE_LIMIT_BYTES = 256 * 1024;

    /** userId → (sessionId → 装饰后在线连接) */
    private final ConcurrentHashMap<Long, ConcurrentHashMap<String, WebSocketSession>> sessions = new ConcurrentHashMap<>();

    public void register(Long userId, WebSocketSession session) {
        if (userId == null || session == null) {
            return;
        }
        WebSocketSession wrapped = session instanceof ConcurrentWebSocketSessionDecorator
                ? session
                : new ConcurrentWebSocketSessionDecorator(session, SEND_TIME_LIMIT_MS, BUFFER_SIZE_LIMIT_BYTES);
        sessions.compute(userId, (id, userSessions) -> {
            ConcurrentHashMap<String, WebSocketSession> map = userSessions != null
                    ? userSessions : new ConcurrentHashMap<>();
            map.put(session.getId(), wrapped);
            return map;
        });
        log.debug("WebSocket 连接注册: userId={}, session={}", userId, session.getId());
    }

    public void unregister(Long userId, WebSocketSession session) {
        if (userId == null || session == null) {
            return;
        }
        sessions.computeIfPresent(userId, (id, userSessions) -> {
            userSessions.remove(session.getId());
            return userSessions.isEmpty() ? null : userSessions;
        });
        log.debug("WebSocket 连接注销: userId={}, session={}", userId, session.getId());
    }

    /** 主动断开指定用户的全部在线 WebSocket 会话（禁用、改密、重置密码、注销全端） */
    public void disconnectUser(Long userId, CloseStatus status) {
        if (userId == null || userId <= 0) {
            return;
        }
        Map<String, WebSocketSession> removed = sessions.remove(userId);
        if (removed == null || removed.isEmpty()) {
            return;
        }
        CloseStatus closeStatus = status != null ? status : CloseStatus.POLICY_VIOLATION;
        for (WebSocketSession session : removed.values()) {
            try {
                if (session.isOpen()) {
                    session.close(closeStatus);
                }
            } catch (IOException | RuntimeException ex) {
                log.debug("关闭 WebSocket 会话失败: userId={}, session={}, err={}",
                        userId, session.getId(), ex.getMessage());
            }
        }
    }

    @EventListener
    public void onSessionInvalidated(SessionInvalidatedEvent event) {
        if (event != null && event.userId() != null) {
            disconnectUser(event.userId(), CloseStatus.POLICY_VIOLATION);
        }
    }

    /** 向用户的所有在线连接推送文本消息；单个连接失败不影响其余连接 */
    public void sendToUser(Long userId, String payload) {
        if (userId == null || userId <= 0) {
            return;
        }
        ConcurrentHashMap<String, WebSocketSession> userSessions = sessions.get(userId);
        if (userSessions == null || userSessions.isEmpty()) {
            return;
        }
        TextMessage message = new TextMessage(payload);
        for (WebSocketSession session : List.copyOf(userSessions.values())) {
            try {
                sendToSession(session, message);
            } catch (IOException | RuntimeException ex) {
                log.warn("WebSocket 推送失败，移除会话: userId={}, session={}，原因: {}",
                        userId, session.getId(), ex.getMessage());
                removeFailedSession(userId, session);
            }
        }
    }

    /** 使用注册的并发安全装饰器发送单条消息（用于处理器直接回复错误帧） */
    public void sendToSession(Long userId, WebSocketSession rawSession, TextMessage message) throws IOException {
        if (rawSession == null) {
            return;
        }
        ConcurrentHashMap<String, WebSocketSession> userSessions = userId == null ? null : sessions.get(userId);
        WebSocketSession target = userSessions == null ? null : userSessions.get(rawSession.getId());
        sendToSession(target != null ? target : rawSession, message);
    }

    /** 后台 AI 和用户请求可能同时推送，同一连接的写入必须串行。 */
    public void sendToSession(WebSocketSession session, TextMessage message) throws IOException {
        if (session instanceof ConcurrentWebSocketSessionDecorator) {
            if (session.isOpen()) {
                session.sendMessage(message);
            }
            return;
        }
        synchronized (session) {
            if (session.isOpen()) {
                session.sendMessage(message);
            }
        }
    }

    /** 当前在线用户数（统计用） */
    public int onlineCount() {
        return sessions.size();
    }

    private void removeFailedSession(Long userId, WebSocketSession session) {
        sessions.computeIfPresent(userId, (id, map) -> {
            map.remove(session.getId(), session);
            return map.isEmpty() ? null : map;
        });
    }
}
