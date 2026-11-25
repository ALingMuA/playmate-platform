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
     * <p>只用于"状态探测"类请求，例如查询陪玩主页资料来判定是否已是陪玩师；
     * 其它接口不要使用，避免把真实错误吞掉。</p>
     */
    silentError?: boolean
    /**
     * 跳过 401 自动登出跳转：用于路由守卫初始化水合等由调用方自行处理未授权状态的场景。
     */
    skipAuthRedirect?: boolean
  }
}

export type UnauthorizedHandler = () => void | Promise<void>

let unauthorizedHandler: UnauthorizedHandler | null = null
let unauthorizedInFlight: Promise<void> | null = null
let lastUnauthorizedAt = 0

/** 注册全局 401 清理与跳转处理器（由应用入口注入，避免与 router/store 产生循环依赖）。 */
export function setUnauthorizedHandler(handler: UnauthorizedHandler): void {
  unauthorizedHandler = handler
}

/** 重置 401 去重状态（重新登录成功后调用）。 */
export function resetUnauthorizedState(): void {
  lastUnauthorizedAt = 0
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

function isLoginRequest(url?: string): boolean {
  const path = (url || '').split('?')[0]
  return path === '/auth/login' || path.endsWith('/auth/login')
}

async function extractBlobErrorBody(data: unknown): Promise<ApiResult | undefined> {
  if (typeof Blob === 'undefined' || !(data instanceof Blob)) return undefined
  if (!data.type?.includes('application/json') && data.size > 4096) return undefined
  try {
    const parsed = JSON.parse(await data.text()) as Partial<ApiResult>
    if (parsed && typeof parsed.code === 'string') {
      return parsed as ApiResult
    }
  } catch {
    // 非 JSON Blob
  }
  return undefined
}

function triggerUnauthorizedCleanup(message: string, silentError?: boolean, skipAuthRedirect?: boolean): void {
  const now = Date.now()
  if (unauthorizedInFlight || now - lastUnauthorizedAt < 1000) {
    return
  }
  lastUnauthorizedAt = now
  if (!silentError) {
    ElMessage.error(message || '登录状态已失效，请重新登录')
  }
  if (skipAuthRedirect) {
    return
  }
  unauthorizedInFlight = (async () => {
    try {
      if (unauthorizedHandler) {
        await unauthorizedHandler()
      } else {
        localStorage.removeItem('token')
        if (typeof location !== 'undefined' && !location.pathname.startsWith('/login')) {
          const redirect = `${location.pathname}${location.search}${location.hash}`
          const query = redirect && redirect !== '/' && redirect !== '/login'
            ? `?redirect=${encodeURIComponent(redirect)}`
            : ''
          location.href = `/login${query}`
        }
      }
    } finally {
      unauthorizedInFlight = null
    }
  })()
}

// 响应拦截：统一处理业务错误与登录失效
http.interceptors.response.use(
  async (response) => {
    const isBlob = typeof Blob !== 'undefined' && response.data instanceof Blob
    if (response.config.responseType === 'blob' || isBlob) {
      const blobBody = await extractBlobErrorBody(response.data)
      if (blobBody && blobBody.code !== 'SUCCESS') {
        const message = blobBody.message || '请求失败'
        if (!response.config.silentError) {
          ElMessage.error(message)
        }
        return Promise.reject(new ApiError(message, blobBody.code, response.status))
      }
      return response
    }
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
  async (error) => {
    const status = error.response?.status as number | undefined
    const rawData = error.response?.data
    const isBlob = typeof Blob !== 'undefined' && rawData instanceof Blob
    const body = isBlob
      ? await extractBlobErrorBody(rawData)
      : (rawData as ApiResult | undefined)
    const message = body?.message || error.message || '网络异常'

    const tokenExpired = status === 401 && (
      !isLoginRequest(error.config?.url)
      || (!!body?.code && TOKEN_INVALID_CODES.has(body.code))
    )

    if (tokenExpired) {
      // 并发 401 去重：统一清除本地登录态与 Store/Socket 并回跳登录页
      triggerUnauthorizedCleanup(message, error.config?.silentError, error.config?.skipAuthRedirect)
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
