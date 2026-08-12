import axios from 'axios'
import type { AxiosRequestConfig } from 'axios'
import { ElMessage } from 'element-plus'

/** 后端统一响应结构（与后端 Result<T> 对应） */
export interface ApiResult<T = unknown> {
  httpStatus: number
  code: string
  message: string
  data: T
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

// 响应拦截：统一处理业务错误
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
    const message = error.response?.data?.message || error.message || '网络异常'
    ElMessage.error(message)
    if (error.response?.status === 401) {
      // TODO: 清理登录态并跳转登录页
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
