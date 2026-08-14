import { request } from '@/api/http'

/** 登录用户信息 */
export interface LoginUser {
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
  lastLoginAt?: string
}

/** 登录/注册响应 */
export interface AuthResult {
  token: string
  tokenType: string
  expiresIn: number
  user: LoginUser
}

/** 登录（FR-A02） */
export function login(account: string, password: string): Promise<AuthResult> {
  return request<AuthResult>({
    url: '/auth/login',
    method: 'post',
    data: { account, password },
  })
}

/** 注册（FR-A01） */
export function register(data: {
  username: string
  password: string
  nickname: string
  mobile?: string
  email?: string
}): Promise<LoginUser> {
  return request<LoginUser>({
    url: '/auth/register',
    method: 'post',
    data,
  })
}

/** 当前用户信息（FR-A05） */
export function fetchMe(): Promise<LoginUser> {
  return request<LoginUser>({ url: '/auth/me', method: 'get' })
}

/** 更新个人资料（FR-A05）：昵称、头像、性别、简介，字段可选 */
export function updateProfile(data: {
  nickname?: string
  avatarUrl?: string
  gender?: number
  introduction?: string
}): Promise<LoginUser> {
  return request<LoginUser>({ url: '/accounts/profile', method: 'put', data })
}

/** 修改密码（FR-A04）：成功后旧令牌全部失效，返回新令牌 */
export function changePassword(oldPassword: string, newPassword: string): Promise<AuthResult> {
  return request<AuthResult>({
    url: '/auth/change-password',
    method: 'post',
    data: { oldPassword, newPassword },
  })
}

/** 注销全部会话（FR-A06）：所有旧令牌立即失效 */
export function logoutAll(): Promise<void> {
  return request<void>({ url: '/accounts/logout-all', method: 'post' })
}
