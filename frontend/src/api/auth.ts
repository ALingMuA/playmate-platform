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
