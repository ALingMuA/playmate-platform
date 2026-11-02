import http from '@/api/http'
import type { ApiResult } from '@/api/http'

/** 文件上传结果（概要设计 4.7） */
export interface FileView {
  url: string
  fileName: string
  sizeBytes: number
}

/** 上传图片（头像、能力证明、投诉证据等），返回可访问 URL */
export async function uploadFile(file: File, category = 'general'): Promise<FileView> {
  const formData = new FormData()
  formData.append('file', file)
  formData.append('category', category)
  // 上传走 axios 实例：请求拦截自动附带 JWT，响应拦截统一处理错误
  const response = await http.post<ApiResult<FileView>>('/files/upload', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  })
  return response.data.data
}

/** 通过带登录态的请求读取受保护图片，返回可直接用于 img 的 data URL。 */
export async function loadPrivateImage(fileUrl: string): Promise<string> {
  const token = localStorage.getItem('token')
  const response = await fetch(fileUrl, {
    headers: token ? { Authorization: `Bearer ${token}` } : {},
  })
  if (!response.ok) throw new Error('图片读取失败')
  const blob = await response.blob()
  return await new Promise<string>((resolve, reject) => {
    const reader = new FileReader()
    reader.onload = () => typeof reader.result === 'string' ? resolve(reader.result) : reject(new Error('图片解析失败'))
    reader.onerror = () => reject(new Error('图片解析失败'))
    reader.readAsDataURL(blob)
  })
}
