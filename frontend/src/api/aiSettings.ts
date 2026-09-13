import { request } from '@/api/http'

/**
 * 管理端 AI 接口配置（仅 OpenAI 兼容的 Chat Completions 协议）。
 *
 * <p>配置来源两种：LOCAL 表示后端本机配置文件（application.yml / 环境变量），
 * DATABASE 表示管理员在后台保存到数据库的配置。数据库保存成功即时生效，
 * 保存失败保留旧配置；恢复本机配置使用 DELETE 接口。</p>
 *
 * <p>密钥安全：接口只返回 apiKeyConfigured 状态，不回显任何密钥明文；
 * 保存与测试时 apiKey 留空表示保留已有密钥，清除密钥必须显式传 clearApiKey。</p>
 */

/** 鉴权方式：Bearer 令牌 / 自定义请求头 / 不鉴权 */
export type AiAuthMode = 'BEARER' | 'API_KEY' | 'NONE'

/** 输出长度对应的请求体字段名 */
export type AiMaxTokensParameter = 'max_tokens' | 'max_completion_tokens'

/** 配置来源 */
export type AiSettingsSource = 'LOCAL' | 'DATABASE'

/** 管理端 AI 接口配置视图（不含密钥明文） */
export interface AiSettingsView {
  /** 是否启用真实模型调用 */
  enabled: boolean
  /** 基础地址（不含 /chat/completions） */
  baseUrl: string
  /** 完整请求地址，非空时优先于「基础地址 + 接口路径」 */
  endpointUrl: string
  /** 接口路径 */
  chatPath: string
  /** 模型名称 */
  name: string
  authMode: AiAuthMode
  /** 自定义鉴权请求头名称（authMode 为 API_KEY 时生效） */
  authHeaderName: string
  /** 后端是否已保存密钥 */
  apiKeyConfigured: boolean
  /** 单次请求总超时（秒） */
  timeoutSeconds: number
  /** 建立连接超时（秒） */
  connectTimeoutSeconds: number
  /** 最大输出 tokens */
  maxOutputTokens: number
  maxTokensParameter: AiMaxTokensParameter
  /** 随请求发送的历史消息条数 */
  historyMessages: number
  /** 采样温度，null 表示不发送该参数 */
  temperature: number | null
  /** 已保存的附加请求头名称（只给名称，不给值） */
  customHeaderNames: string[]
  /** 额外请求体字段，null 表示不发送 */
  extraBody: Record<string, unknown> | null
  source: AiSettingsSource
  /** 配置版本号，保存与恢复时用于并发校验 */
  version: number
}

/**
 * 保存 / 测试共用的请求体（不含 source、version、apiKeyConfigured 等视图元字段）。
 *
 * <p>customHeaders：值为空或 null 表示保留该请求头已保存的值；删除条目表示移除该请求头。</p>
 */
export interface AiSettingsPayload {
  enabled: boolean
  baseUrl: string
  endpointUrl: string
  chatPath: string
  name: string
  authMode: AiAuthMode
  authHeaderName: string
  /** 新密钥，留空表示保留后端已保存的密钥 */
  apiKey: string
  /** 是否清除后端已保存的密钥 */
  clearApiKey: boolean
  timeoutSeconds: number
  connectTimeoutSeconds: number
  maxOutputTokens: number
  maxTokensParameter: AiMaxTokensParameter
  historyMessages: number
  temperature: number | null
  customHeaders: Record<string, string | null>
  extraBody: Record<string, unknown> | null
}

/** 连接测试结果 */
export interface AiTestResult {
  success: boolean
  errorCode: string | null
  message: string
  elapsedMs: number
  httpStatus: number | null
  model: string
}

/** 查询当前 AI 接口配置 */
export function adminAiSettingsGet(): Promise<AiSettingsView> {
  return request<AiSettingsView>({ url: '/admin/ai/settings', method: 'get' })
}

/** 保存 AI 接口配置（成功后返回最新视图并即时生效） */
export function adminAiSettingsSave(payload: AiSettingsPayload, version: number): Promise<AiSettingsView> {
  return request<AiSettingsView>({
    url: '/admin/ai/settings',
    method: 'put',
    data: { ...payload, version },
  })
}

/**
 * 使用当前草稿测试连接（不会保存配置）。
 *
 * <p>后端会真实发送一句「你好」，消耗少量 token，因此单独放宽超时时间。</p>
 */
export function adminAiSettingsTest(payload: AiSettingsPayload, version: number): Promise<AiTestResult> {
  return request<AiTestResult>({
    url: '/admin/ai/settings/test',
    method: 'post',
    data: { ...payload, version },
    timeout: 130000,
  })
}

/** 删除数据库配置，恢复本机配置文件中的值 */
export function adminAiSettingsRestoreLocal(version: number): Promise<AiSettingsView> {
  return request<AiSettingsView>({
    url: '/admin/ai/settings',
    method: 'delete',
    params: { version },
  })
}
