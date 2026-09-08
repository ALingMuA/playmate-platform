package com.gameplay.infrastructure.websocket;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * WebSocket 会话注册表：按用户ID维护在线连接（一个用户可多端连接）。
 *
 * <p>推送失败（连接已关闭）时静默移除会话；离线用户的消息由 REST 补拉接口兜底（详细设计 5.2）。</p>
 */
@Slf4j
@Component
public class WsSessionRegistry {

    /** userId → 在线连接集合 */
    private final Map<Long, Set<WebSocketSession>> sessions = new ConcurrentHashMap<>();

    public void register(Long userId, WebSocketSession session) {
        sessions.computeIfAbsent(userId, k -> new CopyOnWriteArraySet<>()).add(session);
        log.debug("WebSocket 连接注册: userId={}, session={}", userId, session.getId());
    }

    public void unregister(Long userId, WebSocketSession session) {
        Set<WebSocketSession> userSessions = sessions.get(userId);
        if (userSessions != null) {
            userSessions.remove(session);
            if (userSessions.isEmpty()) {
                sessions.remove(userId);
            }
        }
        log.debug("WebSocket 连接注销: userId={}, session={}", userId, session.getId());
    }

    /** 向用户的所有在线连接推送文本消息；离线用户静默忽略 */
    public void sendToUser(Long userId, String payload) {
        if (userId == null || userId <= 0) {
            return;
        }
        Set<WebSocketSession> userSessions = sessions.get(userId);
        if (userSessions == null || userSessions.isEmpty()) {
            return;
        }
        TextMessage message = new TextMessage(payload);
        for (WebSocketSession session : userSessions) {
            try {
                sendToSession(session, message);
            } catch (IOException e) {
                log.warn("WebSocket 推送失败，移除会话: userId={}, session={}，原因: {}",
                        userId, session.getId(), e.getMessage());
                userSessions.remove(session);
            }
        }
    }

    /** 后台 AI 和用户请求可能同时推送，同一连接的写入必须串行。 */
    public void sendToSession(WebSocketSession session, TextMessage message) throws IOException {
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
}
