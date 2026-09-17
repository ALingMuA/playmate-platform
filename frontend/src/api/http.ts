import axios from 'axios'
import type { AxiosRequestConfig } from 'axios'
import { ElMessage } from 'element-plus'

/** 后端统一响应结构（与后端 ApiResponse 对应） */
export interface ApiResult<T = unknown> {
  code: string
  message: string
  data: T
}

/** MyBatis-Plus 分页结构 */
export interface PageResult<T> {
  records: T[]
  total: number
  size: number
  current: number
}

/**
 * 接口错误：保留后端业务错误码与 HTTP 状态，便于调用方按码分支处理。
 *
 * <p>典型用法：入驻申请页依据 `COMPANION_NOT_APPROVED` 判定"当前用户还不是陪玩师"，
 * 该情形属于正常状态而非故障。</p>
 */
export class ApiError extends Error {
  readonly code?: string
  readonly status?: number

  constructor(message: string, code?: string, status?: number) {
    super(message)
    this.name = 'ApiError'
    this.code = code
    this.status = status
  }
}

declare module 'axios' {
  export interface AxiosRequestConfig {
    /**
     * 静默失败：跳过全局 ElMessage 错误提示（错误仍以 Promise reject 抛出，调用方必须处理）。
     *
     * <p>只用于"状态探测"类请求，例如查询我的陪玩主页来判定是否已是陪玩师；
     * 其它接口不要使用，避免把真实错误吞掉。</p>
     */
    silentError?: boolean
  }
}

const http = axios.create({
  baseURL: '/api',
  timeout: 15000,
})

// 请求拦截：附加 JWT
http.interceptors.request.use((config) => {
  const token = localStorage.getItem('token')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

/** 令牌失效类错误码：需要清除本地登录态并跳转登录页 */
const TOKEN_INVALID_CODES = new Set([
  'AUTH_TOKEN_MISSING',
  'AUTH_TOKEN_INVALID',
  'AUTH_TOKEN_VERSION_MISMATCH',
])

// 响应拦截：统一处理业务错误与登录失效
http.interceptors.response.use(
  (response) => {
    const body = response.data as ApiResult
    if (body.code !== 'SUCCESS') {
      const message = body.message || '请求失败'
      if (!response.config.silentError) {
        ElMessage.error(message)
      }
      return Promise.reject(new ApiError(message, body.code, response.status))
    }
    return response
  },
  (error) => {
    const status = error.response?.status as number | undefined
    const body = error.response?.data as ApiResult | undefined
    const message = body?.message || error.message || '网络异常'

    if (status === 401 && body?.code && TOKEN_INVALID_CODES.has(body.code)) {
      // 令牌缺失/无效/版本失效：清除本地登录态并跳转登录页
      // 该提示不静默：登录态失效属于必须让用户知道的状态变化
      localStorage.removeItem('token')
      ElMessage.error(message || '登录状态已失效，请重新登录')
      if (!location.pathname.startsWith('/login')) {
        location.href = '/login'
      }
    } else if (!error.config?.silentError) {
      // 其余错误（含登录凭据错误等业务 401）：只提示后端返回的错误信息
      ElMessage.error(message)
    }
    return Promise.reject(new ApiError(message, body?.code, status))
  },
)

/** 泛型请求方法：直接返回业务数据 */
export async function request<T>(config: AxiosRequestConfig): Promise<T> {
  const response = await http.request<ApiResult<T>>(config)
  return response.data.data
}

export default http
