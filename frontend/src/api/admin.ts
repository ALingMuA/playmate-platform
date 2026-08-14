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
