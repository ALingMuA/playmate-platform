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

/** 陪玩师公开主页资料。仅包含游客可见字段，与本人工作台资料接口分离。 */
export interface PublicCompanionProfile {
  userId: number
  displayName: string
  avatarUrl: string | null
  profileIntro: string | null
  serviceStatus: 'AVAILABLE' | 'BUSY' | 'RESTING' | 'SUSPENDED'
  /** 符合公开可预约条件服务的最低价，单位分；无可预约服务时为空。 */
  startingPriceCents: number | null
  ratingAvg: number | null
  ratingCount: number
  completedOrderCount: number
  capabilities: GameCapability[]
  /** 平台陪玩资格状态，不代表每项能力证明均已审核通过。 */
  certificationStatus: 'APPROVED' | 'SUSPENDED'
}

/** 公开档期摘要，不包含本人管理页备注与来源字段。 */
export interface PublicAvailability {
  startAt: string
  endAt: string
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
  /** 生成来源模板ID（0=手动创建） */
  templateId: number
  remark: string
}

/** 档期周期模板（FR-P10 周期设置） */
export interface AvailabilityTemplate {
  id: number
  templateName: string
  /** 生效周几集合，逗号分隔（1=周一..7=周日） */
  dayOfWeek: string
  allDay: boolean
  /** "HH:mm:ss"（allDay=false 时有值） */
  startTime?: string
  endTime?: string
  /** "yyyy-MM-dd" */
  effectiveFrom: string
  /** 生效结束日期，空表示长期有效 */
  effectiveUntil?: string
  enabled: boolean
  lastGeneratedUntil: string
  /** 展示用规则描述，如"周一至周五 19:00-23:00（10-17 起长期）" */
  ruleText: string
}

/** 批量/周期生成结果 */
export interface AvailabilityBatchResult {
  createdCount: number
  skippedCount: number
  skippedDetails: string[]
}

/** 订单档期锁定占用信息（周看板） */
export interface BookedSlot {
  orderId: number
  orderNo: string
  companionUserId: number
  customerUserId: number
  customerNickname: string
  serviceTitle: string
  gameServer?: string
  gameNickname?: string
  startAt: string
  endAt: string
  orderStatus: string
}

/** 周日程全景概览数据 */
export interface ScheduleOverview {
  availabilities: Availability[]
  bookedSlots: BookedSlot[]
  serverNow?: string
  generatedUntil?: string
}

/** 单日恢复常规作息结果（FR-P11） */
export interface RestoreDayResult {
  removedManual: number
  removedGenerated: number
  regenerated: number
  skipped?: string[]
}

/** 周日程批量变更 */
export interface AvailabilityChange {
  startAt: string
  endAt: string
  status: 'AVAILABLE' | 'UNAVAILABLE'
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

/** 读取游客可见的陪玩师资料，不复用受角色保护的本人资料接口。 */
export function getPublicCompanionProfile(userId: number): Promise<PublicCompanionProfile> {
  return request<PublicCompanionProfile>({ url: `/companion-profiles/${userId}`, method: 'get' })
}

export function listPublicAvailabilities(userId: number, days = 7): Promise<PublicAvailability[]> {
  return request<PublicAvailability[]>({
    url: `/companion-profiles/${userId}/availabilities`,
    method: 'get',
    params: { days },
  })
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

/** 周日程全景概览（含可用档期 + 订单锁定区间） */
export function getScheduleOverview(startAt?: string, endAt?: string): Promise<ScheduleOverview> {
  return request<ScheduleOverview>({
    url: '/companion/availabilities/overview',
    method: 'get',
    params: { startAt, endAt },
  })
}

/** 一键切换整天状态（全天休息 或 全天可约） */
export function toggleDayOff(data: { date: string; isOff: boolean; remark?: string }): Promise<void> {
  return request<void>({
    url: '/companion/availabilities/toggle-day-off',
    method: 'post',
    data,
  })
}

/** 单日恢复常规作息（FR-P11） */
export function restoreDay(date: string): Promise<RestoreDayResult> {
  return request<RestoreDayResult>({
    url: '/companion/availabilities/restore-day',
    method: 'post',
    data: { date },
  })
}

/** 周日程批量保存：多个区间由后端在同一事务内处理 */
export function applyAvailabilityChanges(changes: AvailabilityChange[]): Promise<void> {
  return request<void>({
    url: '/companion/availabilities/changes',
    method: 'post',
    data: { changes },
  })
}

/** 批量创建可约档期：日期范围内每日同一时段或全天，冲突日期自动跳过并返回明细（FR-P10 日期范围设置） */
export function createAvailabilityBatch(data: {
  startDate: string
  endDate?: string
  allDay: boolean
  startTime?: string
  endTime?: string
  remark?: string
}): Promise<AvailabilityBatchResult> {
  return request<AvailabilityBatchResult>({ url: '/companion/availabilities/batch', method: 'post', data })
}

// ==================== 档期周期模板（FR-P10 周期设置：设置一次自动循环生效） ====================

export function listAvailabilityTemplates(): Promise<AvailabilityTemplate[]> {
  return request<AvailabilityTemplate[]>({ url: '/companion/availability-templates', method: 'get' })
}

export interface AvailabilityTemplatePayload {
  templateName: string
  dayOfWeek: string
  allDay: boolean
  startTime?: string
  endTime?: string
  effectiveFrom: string
  effectiveUntil?: string
}

export function createAvailabilityTemplate(data: AvailabilityTemplatePayload): Promise<AvailabilityTemplate> {
  return request<AvailabilityTemplate>({ url: '/companion/availability-templates', method: 'post', data })
}

export function updateAvailabilityTemplate(
  id: number,
  data: AvailabilityTemplatePayload,
): Promise<AvailabilityTemplate> {
  return request<AvailabilityTemplate>({ url: `/companion/availability-templates/${id}`, method: 'put', data })
}

/** 启用/停用模板：停用清理未来实例，启用重新生成 */
export function toggleAvailabilityTemplate(id: number, enabled: boolean): Promise<AvailabilityTemplate> {
  return request<AvailabilityTemplate>({
    url: `/companion/availability-templates/${id}/status`,
    method: 'put',
    params: { enabled },
  })
}

export function deleteAvailabilityTemplate(id: number): Promise<void> {
  return request<void>({ url: `/companion/availability-templates/${id}`, method: 'delete' })
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
