package com.gameplay.infrastructure.websocket;

/**
 * WebSocket 消息类型（详细设计 5.2 消息协议）。
 */
public enum WsMessageType {
    /** 客户端 → 服务端：发送会话消息 */
    MESSAGE_SEND,
    /** 服务端 → 客户端：新消息推送 */
    MESSAGE_NEW,
    /** 服务端 → 客户端：AI 应答结果 */
    AI_RESPONSE,
    /** 服务端 → 客户端：会话状态变更 */
    CONVERSATION_CHANGED,
    /** 服务端 → 客户端：错误提示 */
    ERROR
}
