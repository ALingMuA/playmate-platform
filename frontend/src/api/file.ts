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
