package com.gameplay.auth.event;

/**
 * 会话失效事件：改密、主动注销全部会话、管理员禁用或重置密码后发布，
 * 供 WebSocket 等长连接模块立即断开目标用户在线会话。
 *
 * @param userId 需要强制下线的用户ID
 */
public record SessionInvalidatedEvent(Long userId) {
}
