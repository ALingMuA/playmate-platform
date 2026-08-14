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
      ElMessage.error(body.message || '请求失败')
      return Promise.reject(new Error(body.message))
    }
    return response
  },
  (error) => {
    const status = error.response?.status as number | undefined
    const body = error.response?.data as ApiResult | undefined
    const message = body?.message || error.message || '网络异常'

    if (status === 401 && body?.code && TOKEN_INVALID_CODES.has(body.code)) {
      // 令牌缺失/无效/版本失效：清除本地登录态并跳转登录页
      localStorage.removeItem('token')
      ElMessage.error(message || '登录状态已失效，请重新登录')
      if (!location.pathname.startsWith('/login')) {
        location.href = '/login'
      }
    } else {
      // 其余错误（含登录凭据错误等业务 401）：只提示后端返回的错误信息
      ElMessage.error(message)
    }
    return Promise.reject(error)
  },
)

/** 泛型请求方法：直接返回业务数据 */
export async function request<T>(config: AxiosRequestConfig): Promise<T> {
  const response = await http.request<ApiResult<T>>(config)
  return response.data.data
}

export default http
