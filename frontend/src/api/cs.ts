import { request, type PageResult } from '@/api/http'

// ==================== 类型 ====================

/** 会话消息视图（FR-C08） */
export interface MessageView {
  messageId: number
  conversationId: number
  /** USER / AI / CS / SYSTEM / ADMIN */
  senderType: string
  senderId: number
  contentType: string
  content: string
  /** AI 标识：0否，1是（FR-C21 AI 身份标识） */
  aiMark: number
  readStatus: number
  createdAt: string
}

/** 客服会话视图（FR-C12 会话状态） */
export interface ConversationView {
  id: number
  conversationNo: string
  initiatorUserId: number
  sourceType: string
  relatedOrderId?: number
  /** AI_PROCESSING / WAITING_HUMAN / HUMAN_PROCESSING / ESCALATED_ADMIN / CLOSED */
  conversationStatus: string
  receptionMode: string
  currentCsAccountId?: number
  transferReason?: string
  unresolvedCount: number
  version: number
  createdAt: string
  closedAt?: string
}

/** AI 应答结果（FR-C09/C11） */
export interface AiResponseView {
  conversationId: number
  conversationStatus: string
  /** CONTINUE_AI / TRANSFER_HUMAN / FALLBACK_FAQ */
  decision: string
  userMessageId: number
  aiMessage?: MessageView
  transferReason?: string
}

/** 会话队列项（FR-C24） */
export interface QueueItemView {
  conversationId: number
  conversationNo: string
  topic: string
  sourceType: string
  relatedOrderId?: number
  transferReason?: string
  version: number
  queueSeconds: number
  createdAt: string
}

/** 客服账号视图（FR-C01~C06） */
export interface CsAccountView {
  id: number
  userId: number
  csAccount: string
  csName: string
  contactMobile: string
  accountStatus: string
  workStatus: string
  /** 首次或重置后强制改密：0否，1是 */
  forceChangePassword: number
  maxActiveConversations: number
  disabledReason?: string
  createdAt: string
}

/** 内部备注（FR-C27） */
export interface InternalNoteView {
  id: number
  conversationId: number
  authorUserId: number
  authorRole: string
  content: string
  createdAt: string
}

// ==================== 用户端（FR-C07~C15） ====================

/** 发起客服会话（FR-C07） */
export function createConversation(data: {
  sourceType: 'HELP_CENTER' | 'PROFILE' | 'ORDER'
  relatedOrderId?: number
  firstContent?: string
}): Promise<ConversationView> {
  return request<ConversationView>({ url: '/customer-service/conversations', method: 'post', data })
}

/** 我的会话列表（FR-C14） */
export function myConversations(): Promise<ConversationView[]> {
  return request<ConversationView[]>({ url: '/customer-service/conversations/mine', method: 'get' })
}

/** 会话详情 */
export function conversationDetail(id: number): Promise<ConversationView> {
  return request<ConversationView>({ url: `/customer-service/conversations/${id}`, method: 'get' })
}

/** 会话消息列表（REST 补拉兜底） */
export function conversationMessages(id: number, afterId?: number, size = 50): Promise<MessageView[]> {
  return request<MessageView[]>({
    url: `/customer-service/conversations/${id}/messages`,
    method: 'get',
    params: { afterId, size },
  })
}

/** 主动申请转人工（FR-C10） */
export function requestHuman(id: number): Promise<ConversationView> {
  return request<ConversationView>({ url: `/customer-service/conversations/${id}/request-human`, method: 'post' })
}

/** 服务满意度评价（FR-C15，每个会话最多一次） */
export function evaluateConversation(id: number, data: { score: number; content?: string }): Promise<void> {
  return request<void>({ url: `/customer-service/conversations/${id}/evaluation`, method: 'post', data })
}

// ==================== 客服工作台（FR-C06/C24~C29） ====================

/** 当前客服账号（含强制改密标志，FR-C04） */
export function csMe(): Promise<CsAccountView> {
  return request<CsAccountView>({ url: '/customer-service/me', method: 'get' })
}

/** 设置工作状态（FR-C06：ONLINE/BUSY/OFFLINE） */
export function csSetWorkStatus(workStatus: string): Promise<CsAccountView> {
  return request<CsAccountView>({ url: '/customer-service/me/work-status', method: 'put', data: { workStatus } })
}

/** 等待人工会话队列（FR-C24） */
export function csQueue(): Promise<QueueItemView[]> {
  return request<QueueItemView[]>({ url: '/customer-service/queue', method: 'get' })
}

/** 我的已分配会话（FR-C29 只能查询本人接待） */
export function csAssigned(): Promise<ConversationView[]> {
  return request<ConversationView[]>({ url: '/customer-service/conversations/assigned', method: 'get' })
}

/** 领取会话（FR-C25，乐观锁 expectedVersion） */
export function csClaim(id: number, expectedVersion: number, claimRemark?: string): Promise<ConversationView> {
  return request<ConversationView>({
    url: `/customer-service/conversations/${id}/claim`,
    method: 'post',
    data: { expectedVersion, claimRemark },
  })
}

/** 人工回复（REST 兜底，正常走 WebSocket） */
export function csReply(id: number, content: string): Promise<MessageView> {
  return request<MessageView>({
    url: `/customer-service/conversations/${id}/messages`,
    method: 'post',
    data: { clientMsgId: crypto.randomUUID(), content },
  })
}

/** 会话内部备注列表（FR-C27） */
export function csNotes(id: number): Promise<InternalNoteView[]> {
  return request<InternalNoteView[]>({ url: `/customer-service/conversations/${id}/notes`, method: 'get' })
}

/** 添加内部备注（FR-C27） */
export function csAddNote(id: number, content: string): Promise<InternalNoteView> {
  return request<InternalNoteView>({
    url: `/customer-service/conversations/${id}/notes`,
    method: 'post',
    data: { content },
  })
}

/** 转交管理员（FR-C28） */
export function csEscalate(id: number, reason: string): Promise<ConversationView> {
  return request<ConversationView>({
    url: `/customer-service/conversations/${id}/escalate`,
    method: 'post',
    data: { reason },
  })
}

/** 关闭会话（FR-C29：处理分类 + 结果） */
export function csClose(id: number, category: string, result: string): Promise<ConversationView> {
  return request<ConversationView>({
    url: `/customer-service/conversations/${id}/close`,
    method: 'post',
    data: { category, result },
  })
}

// ==================== 管理端：客服账号（FR-C01~C05） ====================

export interface CsAccountCreatePayload {
  csAccount: string
  csName: string
  contactMobile?: string
  /** 初始密码（客服首次登录须修改，FR-C04） */
  initialPassword: string
}

export function adminCsAccounts(
  csAccount?: string,
  csName?: string,
  accountStatus?: string,
  page = 1,
  size = 10,
): Promise<PageResult<CsAccountView>> {
  return request<PageResult<CsAccountView>>({
    url: '/admin/cs-accounts',
    method: 'get',
    params: { csAccount, csName, accountStatus, page, size },
  })
}

export function adminCreateCsAccount(data: CsAccountCreatePayload): Promise<CsAccountView> {
  return request<CsAccountView>({ url: '/admin/cs-accounts', method: 'post', data })
}

export function adminSetCsAccountStatus(id: number, accountStatus: string, reason?: string): Promise<CsAccountView> {
  return request<CsAccountView>({
    url: `/admin/cs-accounts/${id}/status`,
    method: 'put',
    data: { accountStatus, reason },
  })
}

export function adminResetCsPassword(id: number, newPassword: string): Promise<CsAccountView> {
  return request<CsAccountView>({
    url: `/admin/cs-accounts/${id}/reset-password`,
    method: 'put',
    data: { newPassword },
  })
}
