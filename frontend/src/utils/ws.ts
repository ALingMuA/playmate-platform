/**
 * 客服 WebSocket 客户端（概要设计 4.7：端点 /ws/cs，握手带 JWT）。
 *
 * <p>上行帧：MESSAGE_SEND（clientMsgId 幂等）；
 * 下行帧：MESSAGE_NEW / AI_RESPONSE / CONVERSATION_CHANGED / ERROR。
 * 断线自动重连，服务端推送通过 onMessage 回调分发。</p>
 */
import type { AiResponseView, ConversationView, MessageView } from '@/api/cs'

/** 服务端下行帧 */
export type WsPush =
  | { type: 'MESSAGE_NEW'; data: MessageView }
  | { type: 'CONVERSATION_CHANGED'; data: ConversationView }
  | { type: 'AI_RESPONSE'; data: AiResponseView }
  | { type: 'ERROR'; data: string }

/** 上行发送帧参数 */
export interface WsSendParams {
  conversationId: number
  content: string
  clientMsgId: string
  relatedOrderId?: number
  requestHuman?: boolean
}

export class CsSocket {
  private ws: WebSocket | null = null
  private token = ''
  private closedByUser = false
  private reconnectTimer: ReturnType<typeof setTimeout> | null = null
  private readonly handlers = new Set<(msg: WsPush) => void>()

  /** 建立连接（带 token 握手），已有连接则忽略 */
  connect(token: string) {
    if (this.ws && (this.ws.readyState === WebSocket.OPEN || this.ws.readyState === WebSocket.CONNECTING)) {
      return
    }
    this.token = token
    this.closedByUser = false
    this.open()
  }

  private open() {
    const protocol = location.protocol === 'https:' ? 'wss' : 'ws'
    const url = `${protocol}://${location.host}/ws/cs?token=${encodeURIComponent(this.token)}`
    const ws = new WebSocket(url)
    this.ws = ws

    ws.onmessage = (event) => {
      try {
        const msg = JSON.parse(event.data as string) as WsPush
        this.handlers.forEach((h) => h(msg))
      } catch {
        // 忽略无法解析的帧
      }
    }

    ws.onclose = () => {
      this.ws = null
      if (!this.closedByUser && this.token) {
        // 断线自动重连（3 秒后）
        this.reconnectTimer = setTimeout(() => this.open(), 3000)
      }
    }

    ws.onerror = () => {
      // onclose 随后触发，重连逻辑在 onclose 中处理
    }
  }

  /** 发送会话消息（MESSAGE_SEND） */
  sendMessage(params: WsSendParams): boolean {
    if (!this.ws || this.ws.readyState !== WebSocket.OPEN) {
      return false
    }
    this.ws.send(JSON.stringify({ type: 'MESSAGE_SEND', ...params }))
    return true
  }

  /** 订阅服务端推送，返回取消订阅函数 */
  onMessage(handler: (msg: WsPush) => void): () => void {
    this.handlers.add(handler)
    return () => this.handlers.delete(handler)
  }

  /** 连接是否可用 */
  get connected(): boolean {
    return !!this.ws && this.ws.readyState === WebSocket.OPEN
  }

  /** 主动关闭连接（退出登录/离开页面时调用） */
  close() {
    this.closedByUser = true
    if (this.reconnectTimer) {
      clearTimeout(this.reconnectTimer)
      this.reconnectTimer = null
    }
    this.ws?.close()
    this.ws = null
  }
}

/** 全局单例（页面共用同一连接） */
export const csSocket = new CsSocket()
