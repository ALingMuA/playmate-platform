/**
 * 客服 WebSocket 客户端（概要设计 4.7：端点 /ws/cs，基于一次性握手票据建连）。
 *
 * <p>上行帧：MESSAGE_SEND（clientMsgId 幂等）；
 * 下行帧：MESSAGE_NEW / AI_STATUS / AI_RESPONSE / CONVERSATION_CHANGED / ERROR。
 * 断线采用封顶指数退避重连，服务端推送通过 onMessage 回调分发。</p>
 */
import { fetchWsTicket, type AiResponseView, type AiTaskView, type ConversationView, type MessageView } from '@/api/cs'
import { ApiError } from '@/api/http'

/** 服务端下行帧 */
export type WsPush =
  | { type: 'MESSAGE_NEW'; data: MessageView }
  | { type: 'AI_STATUS'; data: AiTaskView }
  | { type: 'CONVERSATION_CHANGED'; data: ConversationView }
  | { type: 'AI_RESPONSE'; data: AiResponseView }
  | { type: 'ERROR'; data: { message: string; conversationId?: number; clientMsgId?: string } | string; message?: string }

export type WsConnectionState = 'CONNECTING' | 'OPEN' | 'CLOSED'

/** 上行发送帧参数 */
export interface WsSendParams {
  conversationId: number
  content: string
  clientMsgId: string
  relatedOrderId?: number
  requestHuman?: boolean
}

const MAX_RETRIES = 8
const BASE_RETRY_DELAY_MS = 1000
const MAX_RETRY_DELAY_MS = 30000
const NO_RETRY_CLOSE_CODES = new Set([1008, 4401])

export class CsSocket {
  private ws: WebSocket | null = null
  private token = ''
  private closedByUser = false
  private opening = false
  private connectSeq = 0
  private retryCount = 0
  private reconnectTimer: ReturnType<typeof setTimeout> | null = null
  private readonly handlers = new Set<(msg: WsPush) => void>()
  private readonly connectionHandlers = new Set<(state: WsConnectionState) => void>()

  private notifyConnection(state: WsConnectionState) {
    this.connectionHandlers.forEach((handler) => handler(state))
  }

  private notifyError(message: string) {
    const push: WsPush = { type: 'ERROR', data: { message } }
    this.handlers.forEach((handler) => handler(push))
  }

  private clearReconnectTimer() {
    if (this.reconnectTimer) {
      clearTimeout(this.reconnectTimer)
      this.reconnectTimer = null
    }
  }

  private teardownSocket() {
    this.clearReconnectTimer()
    const current = this.ws
    this.ws = null
    if (current) {
      current.onopen = null
      current.onmessage = null
      current.onclose = null
      current.onerror = null
      current.close()
    }
  }

  /** 建立连接（以最新 token 交换一次性票据握手）；token 变化时自动重连 */
  connect(token: string) {
    if (!token) {
      this.close()
      return
    }
    const hasLiveOrPending = this.opening
      || !!this.reconnectTimer
      || (!!this.ws && (this.ws.readyState === WebSocket.OPEN || this.ws.readyState === WebSocket.CONNECTING))
    if (this.token === token && !this.closedByUser && hasLiveOrPending) {
      return
    }
    this.teardownSocket()
    this.token = token
    this.closedByUser = false
    this.retryCount = 0
    this.connectSeq += 1
    void this.open(this.connectSeq, token)
  }

  private scheduleReconnect(seq: number, activeToken: string) {
    if (this.closedByUser || seq !== this.connectSeq || this.token !== activeToken) {
      return
    }
    if (this.retryCount >= MAX_RETRIES) {
      this.notifyError('实时客服连接多次重连失败，请检查网络或刷新页面重试')
      return
    }
    const expDelay = Math.min(MAX_RETRY_DELAY_MS, BASE_RETRY_DELAY_MS * (2 ** this.retryCount))
    const jitter = Math.floor(Math.random() * 500)
    this.retryCount += 1
    this.clearReconnectTimer()
    this.reconnectTimer = setTimeout(() => {
      this.reconnectTimer = null
      if (!this.closedByUser && seq === this.connectSeq && this.token === activeToken) {
        void this.open(seq, activeToken)
      }
    }, expDelay + jitter)
  }

  private async open(seq: number, activeToken: string) {
    if (this.closedByUser || seq !== this.connectSeq || this.token !== activeToken) {
      return
    }
    this.opening = true
    this.notifyConnection('CONNECTING')

    let ticket = ''
    try {
      const res = await fetchWsTicket()
      ticket = res?.ticket ?? ''
    } catch (err) {
      if (seq === this.connectSeq) this.opening = false
      if (this.closedByUser || seq !== this.connectSeq || this.token !== activeToken) {
        return
      }
      this.notifyConnection('CLOSED')
      if (err instanceof ApiError && (err.status === 401 || err.status === 403)) {
        return
      }
      this.scheduleReconnect(seq, activeToken)
      return
    }

    if (this.closedByUser || seq !== this.connectSeq || this.token !== activeToken || !ticket) {
      if (seq === this.connectSeq) this.opening = false
      if (!this.closedByUser && seq === this.connectSeq && !ticket) {
        this.notifyConnection('CLOSED')
        this.scheduleReconnect(seq, activeToken)
      }
      return
    }

    const protocol = location.protocol === 'https:' ? 'wss' : 'ws'
    const url = `${protocol}://${location.host}/ws/cs?ticket=${encodeURIComponent(ticket)}`
    const ws = new WebSocket(url)
    this.ws = ws
    this.opening = false

    ws.onopen = () => {
      if (this.ws !== ws || seq !== this.connectSeq) return
      this.retryCount = 0
      this.notifyConnection('OPEN')
    }

    ws.onmessage = (event) => {
      if (this.ws !== ws) return
      try {
        const msg = JSON.parse(event.data as string) as WsPush
        this.handlers.forEach((h) => h(msg))
      } catch {
        // 忽略无法解析的帧
      }
    }

    ws.onclose = (event) => {
      if (this.ws !== ws) return
      this.ws = null
      this.notifyConnection('CLOSED')
      if (NO_RETRY_CLOSE_CODES.has(event.code)) {
        return
      }
      if (!this.closedByUser && this.token && seq === this.connectSeq) {
        this.scheduleReconnect(seq, activeToken)
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

  /** 订阅连接状态；订阅后会立即收到当前状态。 */
  onConnectionState(handler: (state: WsConnectionState) => void): () => void {
    this.connectionHandlers.add(handler)
    handler(this.connected ? 'OPEN' : (this.ws || this.opening) ? 'CONNECTING' : 'CLOSED')
    return () => this.connectionHandlers.delete(handler)
  }

  /** 连接是否可用 */
  get connected(): boolean {
    return !!this.ws && this.ws.readyState === WebSocket.OPEN
  }

  /** 主动关闭连接（退出登录/离开页面时调用） */
  close() {
    this.closedByUser = true
    this.connectSeq += 1
    this.opening = false
    this.retryCount = 0
    this.token = ''
    this.teardownSocket()
    this.notifyConnection('CLOSED')
  }
}

/** 全局单例（页面共用同一连接） */
export const csSocket = new CsSocket()
