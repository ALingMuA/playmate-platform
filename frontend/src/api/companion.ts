import { request, type PageResult } from '@/api/http'

// ==================== 类型 ====================

/** 游戏能力认证项（FR-P02） */
export interface GameCapability {
  gameId: number
  gameName?: string
  server: string
  rank: string
  positionTagIds?: number[]
}

/** 入驻申请（FR-P01~P04） */
export interface Application {
  id: number
  realName: string
  contactMobile: string
  introduction: string
  capabilities: GameCapability[]
  proofUrls: string[]
  auditStatus: 'PENDING' | 'APPROVED' | 'REJECTED'
  auditReason: string
  auditedAt?: string
  createdAt: string
}

/** 陪玩主页（FR-P05/P06） */
export interface CompanionProfile {
  id: number
  userId: number
  displayName: string
  profileIntro: string
  capabilities: GameCapability[]
  ratingAvg: number
  ratingCount: number
  completedOrderCount: number
  serviceStatus: 'AVAILABLE' | 'BUSY' | 'RESTING' | 'SUSPENDED'
  certificationStatus: 'APPROVED' | 'SUSPENDED'
  updatedAt?: string
}

/** 服务项目（FR-P07~P09） */
export interface CompanionService {
  id: number
  gameId: number
  gameName: string
  serviceTypeId: number
  serviceTypeName: string
  title: string
  description: string
  tagIds: number[]
  tagNames: string[]
  priceCents: number
  minDurationMinutes: number
  /** 服务类型是否仍启用：0 表示类型已被停用或删除（前端标注"已停用"） */
  serviceTypeEnabled?: number
  /** 已停用或已删除的标签名列表（前端加"已停用"标注） */
  disabledTagNames?: string[]
  auditStatus: 'PENDING' | 'APPROVED' | 'REJECTED'
  serviceStatus: 'ON_SHELF' | 'OFF_SHELF'
  auditReason: string
  createdAt: string
  updatedAt: string
}

/** 档期（FR-P10） */
export interface Availability {
  id: number
  companionUserId: number
  startAt: string
  endAt: string
  availabilityStatus: 'AVAILABLE' | 'UNAVAILABLE'
  sourceType: 'MANUAL' | 'RECURRENCE'
  remark: string
}

/** 收益概览（FR-P19） */
export interface Earnings {
  balanceCents: number
  frozenCents: number
  totalIncomeCents: number
  completedOrderCount: number
  ratingAvg: number | null
  ratingCount: number
}

// ==================== 入驻申请 ====================

export function submitApplication(data: {
  realName: string
  contactMobile: string
  introduction: string
  capabilities: GameCapability[]
  proofUrls?: string[]
}): Promise<Application> {
  return request<Application>({ url: '/companion/applications', method: 'post', data })
}

export function myApplications(): Promise<Application[]> {
  return request<Application[]>({ url: '/companion/applications/mine', method: 'get' })
}

// ==================== 主页 ====================

export function myProfile(silentError = false): Promise<CompanionProfile> {
  // silentError：非陪玩师调用会返回 403 COMPANION_NOT_APPROVED，属正常的"状态探测"结果
  return request<CompanionProfile>({ url: '/companion/profile', method: 'get', silentError })
}

export function updateProfile(data: {
  displayName: string
  profileIntro?: string
  capabilities?: GameCapability[]
}): Promise<CompanionProfile> {
  return request<CompanionProfile>({ url: '/companion/profile', method: 'put', data })
}

export function updateServiceStatus(serviceStatus: string): Promise<CompanionProfile> {
  return request<CompanionProfile>({
    url: '/companion/profile/service-status',
    method: 'put',
    data: { serviceStatus },
  })
}

// ==================== 服务项目 ====================

export interface ServicePayload {
  gameId: number
  serviceTypeId: number
  title: string
  description?: string
  tagIds?: number[]
  priceCents: number
  minDurationMinutes: number
}

export function listMyServices(page = 1, size = 10): Promise<PageResult<CompanionService>> {
  return request<PageResult<CompanionService>>({
    url: '/companion/services',
    method: 'get',
    params: { page, size },
  })
}

export function createService(data: ServicePayload): Promise<CompanionService> {
  return request<CompanionService>({ url: '/companion/services', method: 'post', data })
}

export function updateService(id: number, data: ServicePayload): Promise<CompanionService> {
  return request<CompanionService>({ url: `/companion/services/${id}`, method: 'put', data })
}

export function setServiceShelf(id: number, onShelf: boolean): Promise<CompanionService> {
  return request<CompanionService>({
    url: `/companion/services/${id}/shelf`,
    method: 'put',
    params: { onShelf },
  })
}

// ==================== 档期 ====================

export function listAvailabilities(startAt?: string, endAt?: string): Promise<Availability[]> {
  return request<Availability[]>({
    url: '/companion/availabilities',
    method: 'get',
    params: { startAt, endAt },
  })
}

export function createAvailability(data: {
  startAt: string
  endAt: string
  remark?: string
}): Promise<Availability> {
  return request<Availability>({ url: '/companion/availabilities', method: 'post', data })
}

export function createUnavailable(data: {
  startAt: string
  endAt: string
  remark?: string
}): Promise<Availability> {
  return request<Availability>({ url: '/companion/availabilities/unavailable', method: 'post', data })
}

export function deleteAvailability(id: number): Promise<void> {
  return request<void>({ url: `/companion/availabilities/${id}`, method: 'delete' })
}

// ==================== 收益 ====================

export function earningsOverview(): Promise<Earnings> {
  return request<Earnings>({ url: '/companion/earnings', method: 'get' })
}

export interface Ledger {
  businessNo: string
  orderId: number
  ledgerType: 'PAYMENT' | 'REFUND' | 'SETTLEMENT' | 'ADJUSTMENT'
  direction: 'IN' | 'OUT'
  amountCents: number
  balanceAfterCents: number
  frozenAfterCents: number
  remark: string
  createdAt: string
}

export function earningsLedgers(page = 1, size = 10): Promise<PageResult<Ledger>> {
  return request<PageResult<Ledger>>({
    url: '/companion/earnings/ledgers',
    method: 'get',
    params: { page, size },
  })
}

// ==================== 管理后台：入驻审核（FR-M06） ====================

/** 申请分页查询 */
export function adminApplications(status?: string, page = 1, size = 10): Promise<PageResult<Application>> {
  return request<PageResult<Application>>({
    url: '/admin/companion-applications',
    method: 'get',
    params: { status, page, size },
  })
}

/** 审核通过/驳回 */
export function adminAudit(id: number, approved: boolean, reason: string): Promise<void> {
  return request<void>({
    url: `/admin/companion-applications/${id}/audit`,
    method: 'post',
    data: { approved, reason },
  })
}

/** 服务项目分页（FR-M07） */
export function adminServices(
  auditStatus?: string,
  page = 1,
  size = 10,
): Promise<PageResult<CompanionService>> {
  return request<PageResult<CompanionService>>({
    url: '/admin/companion-services',
    method: 'get',
    params: { auditStatus, page, size },
  })
}

/**
 * 服务项目审核通过/驳回（FR-M07）。
 *
 * <p>注意：后端 `AuditRequest.reason` 为无条件 `@NotBlank`，**通过时也必须传非空 reason**，
 * 否则返回 400 VALIDATION_FAILED。</p>
 */
export function adminAuditService(id: number, approved: boolean, reason: string): Promise<void> {
  return request<void>({
    url: `/admin/companion-services/${id}/audit`,
    method: 'post',
    data: { approved, reason },
  })
}
