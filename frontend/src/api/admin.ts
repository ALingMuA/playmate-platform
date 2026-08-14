import { request, type PageResult } from '@/api/http'

// ==================== 数据概览（FR-M02） ====================

/** 管理端数据概览视图 */
export interface StatsOverview {
  userCount: number
  companionCount: number
  serviceCount: number
  orderCount: number
  /** 模拟交易额（分） */
  totalAmountCents: number
  todayOrderCount: number
  pendingApplications: number
  pendingComplaints: number
  waitingHumanConversations: number
}

export function adminStatsOverview(): Promise<StatsOverview> {
  return request<StatsOverview>({ url: '/admin/stats/overview', method: 'get' })
}

// ==================== 用户管理（FR-M03/M04） ====================

/** 管理端用户视图 */
export interface UserAdminView {
  id: number
  username: string
  nickname: string
  mobile: string
  email: string
  avatarUrl: string
  gender: number
  introduction: string
  accountStatus: string
  roles: string[]
  /** 陪玩师资格状态（非陪玩师为 null） */
  companionCertificationStatus?: string
  lastLoginAt?: string
  createdAt: string
}

/** 用户分页查询（FR-M03：账号/昵称/手机号关键词 + 状态） */
export function adminUsers(
  keyword?: string,
  accountStatus?: string,
  page = 1,
  size = 10,
): Promise<PageResult<UserAdminView>> {
  return request<PageResult<UserAdminView>>({
    url: '/admin/users',
    method: 'get',
    params: { keyword, accountStatus, page, size },
  })
}

/** 启用/禁用用户（FR-M04：禁用必须记录原因） */
export function adminSetUserStatus(id: number, accountStatus: string, reason?: string): Promise<void> {
  return request<void>({
    url: `/admin/users/${id}/status`,
    method: 'put',
    data: { accountStatus, reason },
  })
}

// ==================== 公告管理（FR-M13） ====================

/** 公告视图 */
export interface AnnouncementView {
  id: number
  title: string
  content: string
  /** DRAFT / PUBLISHED / REVOKED */
  publishStatus: string
  publishedBy?: number
  publishedAt?: string
  createdAt: string
  updatedAt: string
}

export function adminAnnouncements(
  publishStatus?: string,
  page = 1,
  size = 10,
): Promise<PageResult<AnnouncementView>> {
  return request<PageResult<AnnouncementView>>({
    url: '/admin/announcements',
    method: 'get',
    params: { publishStatus, page, size },
  })
}

export function adminCreateAnnouncement(data: { title: string; content: string }): Promise<AnnouncementView> {
  return request<AnnouncementView>({ url: '/admin/announcements', method: 'post', data })
}

export function adminUpdateAnnouncement(id: number, data: { title: string; content: string }): Promise<AnnouncementView> {
  return request<AnnouncementView>({ url: `/admin/announcements/${id}`, method: 'put', data })
}

export function adminPublishAnnouncement(id: number): Promise<AnnouncementView> {
  return request<AnnouncementView>({ url: `/admin/announcements/${id}/publish`, method: 'post' })
}

export function adminRevokeAnnouncement(id: number): Promise<AnnouncementView> {
  return request<AnnouncementView>({ url: `/admin/announcements/${id}/revoke`, method: 'post' })
}

export function adminDeleteAnnouncement(id: number): Promise<void> {
  return request<void>({ url: `/admin/announcements/${id}`, method: 'delete' })
}

// ==================== 知识库管理（FR-C23） ====================

/** 知识库条目视图 */
export interface KnowledgeBaseView {
  id: number
  category: string
  title: string
  keywords: string
  standardAnswer: string
  priority: number
  /** 启用状态：0否，1是 */
  enabled: number
  maintainedBy?: number
  createdAt: string
  updatedAt: string
}

export function adminKnowledgeList(
  category?: string,
  enabled?: number,
  page = 1,
  size = 10,
): Promise<PageResult<KnowledgeBaseView>> {
  return request<PageResult<KnowledgeBaseView>>({
    url: '/admin/ai-knowledge',
    method: 'get',
    params: { category, enabled, page, size },
  })
}

export function adminCreateKnowledge(data: {
  category: string
  title: string
  keywords: string
  standardAnswer: string
  priority?: number
}): Promise<KnowledgeBaseView> {
  return request<KnowledgeBaseView>({ url: '/admin/ai-knowledge', method: 'post', data })
}

export function adminUpdateKnowledge(
  id: number,
  data: {
    category: string
    title: string
    keywords: string
    standardAnswer: string
    priority?: number
  },
): Promise<KnowledgeBaseView> {
  return request<KnowledgeBaseView>({ url: `/admin/ai-knowledge/${id}`, method: 'put', data })
}

export function adminSetKnowledgeEnabled(id: number, enabled: boolean): Promise<KnowledgeBaseView> {
  return request<KnowledgeBaseView>({
    url: `/admin/ai-knowledge/${id}/enabled`,
    method: 'put',
    params: { enabled },
  })
}

export function adminDeleteKnowledge(id: number): Promise<void> {
  return request<void>({ url: `/admin/ai-knowledge/${id}`, method: 'delete' })
}

// ==================== 操作日志（FR-M20） ====================

/** 操作日志视图 */
export interface OperationLogView {
  id: number
  operatorId: number
  operatorRole: string
  operationType: string
  targetType: string
  targetId?: number
  beforeData?: string
  afterData?: string
  reason?: string
  requestId?: string
  ipAddress?: string
  createdAt: string
}

export function adminOperationLogs(
  operatorId?: number,
  operationType?: string,
  targetType?: string,
  page = 1,
  size = 10,
): Promise<PageResult<OperationLogView>> {
  return request<PageResult<OperationLogView>>({
    url: '/admin/operation-logs',
    method: 'get',
    params: { operatorId, operationType, targetType, page, size },
  })
}

// ==================== 公开公告（FR-A08，游客可访问） ====================

export function publicAnnouncements(page = 1, size = 10): Promise<PageResult<AnnouncementView>> {
  return request<PageResult<AnnouncementView>>({
    url: '/announcements',
    method: 'get',
    params: { page, size },
  })
}
