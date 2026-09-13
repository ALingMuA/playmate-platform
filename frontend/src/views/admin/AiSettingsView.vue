<script setup lang="ts">
/**
 * 管理后台 - AI 接口配置（仅 OpenAI 兼容的 Chat Completions 协议）。
 *
 * <p>分四个分区维护：基本连接（地址与最终地址预览）、认证（鉴权方式与密钥）、
 * 请求参数（模型、超时、输出长度、历史、温度）、高级配置（附加请求头、额外请求体）。</p>
 *
 * <p>安全约定：密钥密码框 autocomplete=off，只提交给后端，不写 localStorage、不回显；
 * 已保存的附加请求头只显示名称与「值已保存」状态，留空表示保留，删除条目才移除。</p>
 *
 * <p>测试与保存分离：测试使用当前草稿且不保存，修改草稿后过期结果自动清除；
 * 保存成功即时生效并清空密钥与请求头输入，保存失败保留草稿。</p>
 */
import { computed, onMounted, onUnmounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { Delete, MagicStick, Plus, Refresh } from '@element-plus/icons-vue'
import {
  adminAiSettingsGet,
  adminAiSettingsRestoreLocal,
  adminAiSettingsSave,
  adminAiSettingsTest,
  type AiAuthMode,
  type AiMaxTokensParameter,
  type AiSettingsPayload,
  type AiSettingsSource,
  type AiSettingsView,
  type AiTestResult,
} from '@/api/aiSettings'

// ==================== 常量 ====================

/** SenseNova 推荐配置：一键填入只改地址与模型名，绝不改动已输入的密钥 */
const RECOMMENDED = {
  baseUrl: 'https://token.sensenova.cn/v1',
  chatPath: '/chat/completions',
  name: 'sensenova-6.8-flash-lite',
}

/** 由平台统一生成的请求体字段，禁止在额外请求体中覆盖（与后端校验保持一致） */
const RESERVED_BODY_KEYS = [
  'model',
  'messages',
  'stream',
  'max_tokens',
  'max_completion_tokens',
  'temperature',
  'tools',
  'tool_choice',
  'functions',
  'function_call',
  'n',
]

/** 额外请求体中禁止出现的凭据类字段名 */
const CREDENTIAL_KEY_PATTERN = /^(api.?key|access.?token|auth.?token|secret|password|authorization|credentials?)$/i

/** 地址查询参数中禁止出现的凭据类字段名 */
const SENSITIVE_QUERY_KEY_PATTERN = /(key|token|secret|password|authorization|signature|credential)/i

/** 由系统统一发送的请求头，附加请求头与自定义认证头都不能使用 */
const BLOCKED_HEADER_NAMES = new Set([
  'authorization',
  'host',
  'content-length',
  'connection',
  'content-type',
  'transfer-encoding',
  'upgrade',
  'expect',
  'cookie',
  'proxy-authorization',
])

/** 与后端一致的附加请求头数量与长度上限 */
const MAX_CUSTOM_HEADERS = 20
const MAX_HEADER_VALUE_LENGTH = 8192
const MAX_ADDRESS_LENGTH = 2000
const MAX_EXTRA_BODY_LENGTH = 16000

/** 示例仅展示 JSON 格式，具体参数须以所选模型的文档为准。 */
const EXTRA_BODY_EXAMPLE = '{\n  "reasoning_effort": "low"\n}'

/** 请求头名称合法字符（RFC 7230 token，与后端校验一致） */
const HEADER_NAME_PATTERN = /^[A-Za-z0-9!#$%&'*+\-.^_`|~]+$/

/** 允许使用明文 HTTP 的本机地址 */
const LOCAL_HOSTS = new Set(['localhost', '127.0.0.1', '[::1]', '::1'])

/** 字段所属分区：整体校验失败时自动切到对应页签 */
const FIELD_TAB: Record<string, string> = {
  baseUrl: 'basic',
  endpointUrl: 'basic',
  chatPath: 'basic',
  authHeaderName: 'auth',
  name: 'params',
}

/** 鉴权方式展示文案 */
const AUTH_MODE_TEXT: Record<string, string> = {
  BEARER: 'Bearer 令牌',
  API_KEY: '自定义请求头',
  NONE: '无需鉴权',
}

/** 附加请求头行：removed 表示保存时删除该条目（不放进 customHeaders） */
interface HeaderRow {
  name: string
  value: string
  removed: boolean
}

// ==================== 状态 ====================

const formRef = ref<FormInstance>()
const activeTab = ref('basic')
const isNarrow = ref(false)

const loading = ref(false)
const saving = ref(false)
const testing = ref(false)
const restoring = ref(false)
const loadFailed = ref(false)

const version = ref(0)
const source = ref<AiSettingsSource>('LOCAL')
const apiKeyConfigured = ref(false)
const loadedView = ref<AiSettingsView | null>(null)

const testResult = ref<AiTestResult | null>(null)
const testDiscarded = ref(false)
const testedFingerprint = ref('')

/** 是否发送 temperature（不勾选时后端收到 null，请求体不含该字段） */
const sendTemperature = ref(true)
/** 后端已保存的附加请求头（只显示名称与保存状态） */
const savedHeaders = ref<HeaderRow[]>([])
/** 本次新增的附加请求头 */
const newHeaders = ref<HeaderRow[]>([])

const form = reactive({
  enabled: false,
  baseUrl: '',
  endpointUrl: '',
  chatPath: '',
  name: '',
  authMode: 'BEARER',
  authHeaderName: '',
  apiKey: '',
  clearApiKey: false,
  timeoutSeconds: 25,
  connectTimeoutSeconds: 5,
  maxOutputTokens: 800,
  maxTokensParameter: 'max_tokens',
  historyMessages: 12,
  temperature: 0.2,
  extraBodyText: '',
})

const busy = computed(() => loading.value || saving.value || testing.value || restoring.value)
// 配置未加载成功时禁止测试与保存，避免用空表单覆盖后端配置
const testDisabled = computed(() => loading.value || saving.value || restoring.value || loadFailed.value)
const saveDisabled = computed(() => loading.value || testing.value || restoring.value || loadFailed.value)

// ==================== 地址计算与校验 ====================

/** 拼接基础地址与接口路径（去掉重复斜杠） */
function joinUrl(base: string, path: string): string {
  const left = base.trim().replace(/\/+$/, '')
  const right = path.trim().replace(/^\/+/, '')
  if (!left) return right
  if (!right) return left
  return `${left}/${right}`
}

/** 是否使用完整地址覆盖「基础地址 + 接口路径」 */
const usingEndpoint = computed(() => form.endpointUrl.trim().length > 0)

/** 最终请求地址预览 */
const finalUrl = computed(() =>
  form.endpointUrl.trim() ? form.endpointUrl.trim() : joinUrl(form.baseUrl, form.chatPath),
)

/** 地址合规校验：HTTPS 或本机 HTTP，不允许携带账号、锚点与密钥类查询参数 */
function addressError(raw: string, label: string): string {
  const value = raw.trim()
  if (!value) return ''
  if (value.length > MAX_ADDRESS_LENGTH) return `${label}过长（上限 ${MAX_ADDRESS_LENGTH} 字符）`
  let parsed: URL
  try {
    parsed = new URL(value)
  } catch {
    return `${label}不是合法的地址，需要以 http:// 或 https:// 开头`
  }
  if (parsed.username || parsed.password) return `${label}不能包含账号密码，密钥请填在「认证」分区`
  if (parsed.hash) return `${label}不能包含 # 锚点`
  for (const key of parsed.searchParams.keys()) {
    if (SENSITIVE_QUERY_KEY_PATTERN.test(key)) {
      return `${label}的查询参数中不能携带密钥，密钥请填在「认证」分区`
    }
  }
  if (parsed.protocol === 'https:') return ''
  if (parsed.protocol === 'http:') {
    return LOCAL_HOSTS.has(parsed.hostname.toLowerCase())
      ? ''
      : `${label}使用 HTTP 时只允许本机地址（localhost / 127.0.0.1），其它地址必须使用 HTTPS`
  }
  return `${label}只支持 http 或 https 协议`
}

/** 请求头名称校验（自定义认证头与附加请求头共用） */
function headerNameError(raw: string, label: string): string {
  const value = raw.trim()
  if (!value) return `${label}不能为空`
  if (value.length > 80) return `${label}不能超过 80 个字符`
  if (!HEADER_NAME_PATTERN.test(value)) return `${label}只能包含字母、数字与 - _ . 等符号`
  if (BLOCKED_HEADER_NAMES.has(value.toLowerCase())) return `${label}「${value}」由系统统一发送，不能自定义`
  return ''
}

const previewWarning = computed(() => addressError(finalUrl.value, '最终请求地址'))

const previewHint = computed(() => {
  if (previewWarning.value) return previewWarning.value
  if (!form.baseUrl.trim() && !form.endpointUrl.trim()) {
    return '填写地址后这里会显示实际请求的完整地址；仅支持 OpenAI 兼容的 Chat Completions 接口，请使用 HTTPS（本机调试可用 http://localhost）'
  }
  return '仅支持 OpenAI 兼容的 Chat Completions 接口，请使用 HTTPS（本机调试可用 http://localhost 或 http://127.0.0.1）'
})

// ==================== 附加请求头 ====================

function addNewHeader() {
  newHeaders.value.push({ name: '', value: '', removed: false })
}

function removeNewHeader(index: number) {
  newHeaders.value.splice(index, 1)
}

/** 校核新增请求头：数量、名称合法、不重复、必须填值 */
function validateHeaders(): string {
  const names = new Set<string>()
  let count = 0
  for (const row of savedHeaders.value) {
    if (row.removed) continue
    count += 1
    names.add(row.name.toLowerCase())
    if (row.value.length > MAX_HEADER_VALUE_LENGTH) return `请求头「${row.name}」的值过长`
    if (/[\u0000-\u001f\u007f]/.test(row.value)) return `请求头「${row.name}」的值包含换行或控制字符`
  }
  for (const row of newHeaders.value) {
    const name = row.name.trim()
    if (!name && !row.value.trim()) continue
    if (!name) return '请填写新增请求头的名称'
    const nameError = headerNameError(name, '请求头名称')
    if (nameError) return nameError
    if (names.has(name.toLowerCase())) return `请求头「${name}」重复，请修改名称或移除已有条目`
    names.add(name.toLowerCase())
    if (!row.value.trim()) return `新增请求头「${name}」必须填写值，留空无法新增`
    if (row.value.length > MAX_HEADER_VALUE_LENGTH) return `请求头「${name}」的值过长`
    if (/[\u0000-\u001f\u007f]/.test(row.value)) return `请求头「${name}」的值包含换行或控制字符`
    count += 1
  }
  if (count > MAX_CUSTOM_HEADERS) return `附加请求头最多 ${MAX_CUSTOM_HEADERS} 项，当前 ${count} 项`
  if (form.authMode === 'API_KEY' && names.has(form.authHeaderName.trim().toLowerCase())) {
    return '附加请求头不能与认证请求头同名，请修改名称或认证请求头'
  }
  return ''
}

/** 组装 customHeaders：null 表示保留旧值，删除条目表示移除 */
function buildCustomHeaders(): Record<string, string | null> {
  const result: Record<string, string | null> = {}
  for (const row of savedHeaders.value) {
    if (row.removed) continue
    const value = row.value.trim()
    result[row.name] = value ? value : null
  }
  for (const row of newHeaders.value) {
    const name = row.name.trim()
    if (name) result[name] = row.value.trim()
  }
  return result
}

// ==================== 额外请求体（JSON 对象） ====================

interface ParsedExtraBody {
  value: Record<string, unknown> | null
  error: string
}

/** 递归查找凭据类字段名（与后端一致：扩展参数里不允许写密钥） */
function findCredentialKey(node: unknown): string {
  if (Array.isArray(node)) {
    for (const item of node) {
      const hit = findCredentialKey(item)
      if (hit) return hit
    }
    return ''
  }
  if (node && typeof node === 'object') {
    for (const [key, value] of Object.entries(node as Record<string, unknown>)) {
      if (CREDENTIAL_KEY_PATTERN.test(key)) return key
      const hit = findCredentialKey(value)
      if (hit) return hit
    }
  }
  return ''
}

function parseExtraBodyText(text: string): ParsedExtraBody {
  const trimmed = text.trim()
  if (!trimmed) return { value: null, error: '' }
  if (trimmed.length > MAX_EXTRA_BODY_LENGTH) {
    return { value: null, error: `额外请求体过长（上限 ${MAX_EXTRA_BODY_LENGTH} 字符）` }
  }
  let parsed: unknown
  try {
    parsed = JSON.parse(trimmed)
  } catch {
    return { value: null, error: '额外请求体不是合法的 JSON 文本，请检查括号与逗号' }
  }
  if (parsed === null || typeof parsed !== 'object' || Array.isArray(parsed)) {
    return { value: null, error: '额外请求体必须是 JSON 对象（{...}），不能是数组或其它类型' }
  }
  const hit = RESERVED_BODY_KEYS.filter((key) => Object.prototype.hasOwnProperty.call(parsed, key))
  if (hit.length > 0) {
    return { value: null, error: `以下字段由平台统一生成，不能写入额外请求体：${hit.join('、')}` }
  }
  const credentialKey = findCredentialKey(parsed)
  if (credentialKey) {
    return { value: null, error: `额外请求体中出现凭据类字段「${credentialKey}」，密钥请填在「认证」分区` }
  }
  return { value: parsed as Record<string, unknown>, error: '' }
}

const parsedExtraBody = computed(() => parseExtraBodyText(form.extraBodyText))
const extraBodyError = computed(() => parsedExtraBody.value.error)

function formatExtraBody() {
  if (parsedExtraBody.value.error) {
    ElMessage.warning(parsedExtraBody.value.error)
    return
  }
  if (!parsedExtraBody.value.value) {
    ElMessage.warning('额外请求体为空，无需格式化')
    return
  }
  form.extraBodyText = JSON.stringify(parsedExtraBody.value.value, null, 2)
  ElMessage.success('已格式化为两空格缩进的 JSON')
}

function fillExtraBodyExample() {
  form.extraBodyText = EXTRA_BODY_EXAMPLE
  ElMessage.success('已填入示例，请确认所选模型支持此参数后再测试')
}

// ==================== 草稿与视图 ====================

/** 把后端视图填充到草稿（同时清空密钥与请求头输入） */
function applyView(view: AiSettingsView) {
  loadedView.value = view
  version.value = view.version ?? 0
  source.value = view.source ?? 'LOCAL'
  apiKeyConfigured.value = Boolean(view.apiKeyConfigured)

  form.enabled = Boolean(view.enabled)
  form.baseUrl = view.baseUrl ?? ''
  form.endpointUrl = view.endpointUrl ?? ''
  form.chatPath = view.chatPath ?? ''
  form.name = view.name ?? ''
  form.authMode = view.authMode ?? 'BEARER'
  form.authHeaderName = view.authHeaderName ?? ''
  form.timeoutSeconds = view.timeoutSeconds ?? 25
  form.connectTimeoutSeconds = view.connectTimeoutSeconds ?? 5
  form.maxOutputTokens = view.maxOutputTokens ?? 800
  form.maxTokensParameter = view.maxTokensParameter ?? 'max_tokens'
  form.historyMessages = view.historyMessages ?? 12
  form.temperature = view.temperature ?? 0.2
  sendTemperature.value = view.temperature !== null && view.temperature !== undefined
  form.extraBodyText =
    view.extraBody && Object.keys(view.extraBody).length > 0 ? JSON.stringify(view.extraBody, null, 2) : ''

  // 密钥与请求头值都不回显：保存成功后重新填充视图时同样清空
  form.apiKey = ''
  form.clearApiKey = false
  savedHeaders.value = (view.customHeaderNames ?? []).map((name) => ({
    name,
    value: '',
    removed: false,
  }))
  newHeaders.value = []

  testResult.value = null
  testDiscarded.value = false
  testedFingerprint.value = ''
}

async function loadSettings() {
  loading.value = true
  loadFailed.value = false
  try {
    applyView(await adminAiSettingsGet())
  } catch {
    // 错误提示由请求拦截器统一给出
    loadFailed.value = true
  } finally {
    loading.value = false
  }
}

/** 重置草稿：回到最近一次加载/保存后的配置 */
function resetDraft() {
  if (!loadedView.value) return
  applyView(loadedView.value)
  ElMessage.success('已重置为最近一次生效的配置')
}

async function refreshSettings() {
  await loadSettings()
  if (!loadFailed.value) ElMessage.success('已加载最新配置')
}

/** 组装请求体（保存与测试共用，version 单独传参） */
function buildPayload(): AiSettingsPayload {
  return {
    enabled: form.enabled,
    baseUrl: form.baseUrl.trim(),
    endpointUrl: form.endpointUrl.trim(),
    chatPath: form.chatPath.trim(),
    name: form.name.trim(),
    authMode: form.authMode as AiAuthMode,
    // 非 API_KEY 模式后端会忽略该字段，仍原样回传便于切回自定义请求头时保留输入
    authHeaderName: form.authHeaderName.trim(),
    apiKey: form.clearApiKey ? '' : form.apiKey.trim(),
    clearApiKey: form.clearApiKey,
    timeoutSeconds: form.timeoutSeconds,
    connectTimeoutSeconds: form.connectTimeoutSeconds,
    maxOutputTokens: form.maxOutputTokens,
    maxTokensParameter: form.maxTokensParameter as AiMaxTokensParameter,
    historyMessages: form.historyMessages,
    temperature: sendTemperature.value ? form.temperature : null,
    customHeaders: buildCustomHeaders(),
    extraBody: parsedExtraBody.value.value,
  }
}

/** 草稿指纹：用于判断测试结果是否对应当前草稿（仅内存使用，不落盘） */
const draftFingerprint = computed(() =>
  JSON.stringify({
    ...form,
    sendTemperature: sendTemperature.value,
    savedHeaders: savedHeaders.value,
    newHeaders: newHeaders.value,
  }),
)

/** 密钥草稿校验（与后端一致：只填密钥本身、不含换行控制符） */
function apiKeyDraftError(): string {
  if (form.clearApiKey || !form.apiKey.trim()) return ''
  if (form.apiKey.length > 8192) return 'API Key 过长，请确认复制内容是否完整'
  if (/[\u0000-\u001f\u007f]/.test(form.apiKey)) return 'API Key 中包含换行或控制字符，请重新复制'
  if (form.authMode === 'BEARER' && /^Bearer\s/i.test(form.apiKey.trim())) {
    return 'Bearer 模式的 API Key 只填写密钥本身，不要包含 Bearer 前缀'
  }
  return ''
}

/** 后端在启用或测试时要求已配置密钥（鉴权方式为「无需鉴权」时除外） */
function missingApiKeyError(requireKey: boolean): string {
  if (!requireKey || form.authMode === 'NONE') return ''
  if (form.clearApiKey) return '已勾选清除密钥，请先填写新的 API Key 再启用或测试'
  if (!form.apiKey.trim() && !apiKeyConfigured.value) return '请填写 API Key，或将鉴权方式改为「无需鉴权」'
  return ''
}

/** 校验并返回请求体，校验不通过返回 null（同时切到出错分区） */
async function validateDraft(requireKey: boolean): Promise<AiSettingsPayload | null> {
  if (extraBodyError.value) {
    activeTab.value = 'advanced'
    ElMessage.warning(extraBodyError.value)
    return null
  }
  const headerError = validateHeaders()
  if (headerError) {
    activeTab.value = 'advanced'
    ElMessage.warning(headerError)
    return null
  }
  if (!finalUrl.value) {
    activeTab.value = 'basic'
    ElMessage.warning('请填写基础地址与接口路径，或直接填写完整地址')
    return null
  }
  if (previewWarning.value) {
    activeTab.value = 'basic'
    ElMessage.warning(previewWarning.value)
    return null
  }
  const keyError = apiKeyDraftError() || missingApiKeyError(requireKey)
  if (keyError) {
    activeTab.value = 'auth'
    ElMessage.warning(keyError)
    return null
  }
  try {
    await formRef.value?.validate()
  } catch (error) {
    const fields = Object.keys((error ?? {}) as Record<string, unknown>)
    const tab = fields.map((field) => FIELD_TAB[field]).find((item) => Boolean(item))
    if (tab) activeTab.value = tab
    ElMessage.warning('请先修正标红的配置项')
    return null
  }
  return buildPayload()
}

// ==================== 保存 / 测试 / 恢复 ====================

async function handleSave() {
  if (busy.value) return
  // 启用真实模型调用时后端要求必须已配置密钥
  const payload = await validateDraft(form.enabled)
  if (!payload) return
  saving.value = true
  try {
    const view = await adminAiSettingsSave(payload, version.value)
    // 保存成功：清空密钥与请求头输入，重新填充最新视图
    applyView(view)
    ElMessage.success('配置已保存，数据库配置即时生效')
  } catch {
    // 保存失败：保留当前草稿，错误提示由请求拦截器统一给出
  } finally {
    saving.value = false
  }
}

async function handleTest() {
  if (busy.value) return
  // 测试一定会真实调用模型，后端要求必须已配置密钥
  const payload = await validateDraft(true)
  if (!payload) return
  const snapshot = draftFingerprint.value
  testing.value = true
  testDiscarded.value = false
  try {
    const result = await adminAiSettingsTest(payload, version.value)
    if (draftFingerprint.value !== snapshot) {
      // 测试期间草稿被修改：结果已过期，不作展示
      testDiscarded.value = true
      ElMessage.warning('测试期间草稿已修改，本次结果已作废，请重新测试')
      return
    }
    testResult.value = result
    testedFingerprint.value = snapshot
    if (result.success) {
      ElMessage.success(`连接测试成功，耗时 ${result.elapsedMs} ms`)
    } else {
      ElMessage.warning(`连接测试失败：${result.message || result.errorCode || '未知原因'}`)
    }
  } catch {
    // 错误提示由请求拦截器统一给出；测试不会保存任何配置
  } finally {
    testing.value = false
  }
}

async function handleRestoreLocal() {
  if (busy.value || loadFailed.value) return
  const alreadyLocal = source.value === 'LOCAL'
  try {
    await ElMessageBox.confirm(
      alreadyLocal
        ? '当前已经是本机配置，继续执行会清理数据库中可能存在的配置记录，并重新读取 application.yml / 环境变量中的值。'
        : '将删除数据库中保存的 AI 接口配置（含密钥），恢复为后端 application.yml / 环境变量中的值，恢复后立即生效。',
      '恢复本机配置',
      { type: 'warning', confirmButtonText: '确认恢复', cancelButtonText: '取消' },
    )
  } catch {
    return
  }
  restoring.value = true
  try {
    const view = await adminAiSettingsRestoreLocal(version.value)
    applyView(view)
    ElMessage.success('已恢复本机配置')
  } catch {
    // 错误提示由请求拦截器统一给出
  } finally {
    restoring.value = false
  }
}

// ==================== 推荐配置 ====================

function fillRecommended() {
  form.baseUrl = RECOMMENDED.baseUrl
  form.chatPath = RECOMMENDED.chatPath
  form.name = RECOMMENDED.name
  // 清空完整地址覆盖项，保证推荐地址真正生效；不触碰已输入的密钥与其它参数
  form.endpointUrl = ''
  ElMessage.success('已填入 SenseNova 推荐地址与模型名，未修改已输入的 API Key')
}

function fillRecommendedName() {
  form.name = RECOMMENDED.name
  ElMessage.success(`已填入推荐模型 ${RECOMMENDED.name}`)
}

// ==================== 状态展示 ====================

const sourceText = computed(() => (source.value === 'DATABASE' ? '数据库配置' : '本机配置文件'))
const sourceTip = computed(() =>
  source.value === 'DATABASE'
    ? '当前生效的是数据库中保存的配置，保存成功后即时生效，无需重启后端；保存失败会保留原有配置。'
    : '当前生效的是后端本机配置文件（application.yml / 环境变量）中的值，保存成功后将切换为数据库配置。',
)
const apiKeyPlaceholder = computed(() =>
  apiKeyConfigured.value ? '留空表示保留后端已保存的密钥' : '请填写 API Key，仅提交给后端保存',
)

// ==================== 生命周期 ====================

watch(
  () => form.clearApiKey,
  (checked) => {
    if (checked) form.apiKey = ''
  },
)

// 编辑任一配置项后，之前针对旧草稿的测试结果立即作废
watch(draftFingerprint, () => {
  if (testResult.value && testedFingerprint.value !== draftFingerprint.value) {
    testResult.value = null
  }
})

function onResize() {
  isNarrow.value = window.innerWidth <= 768
}

onMounted(() => {
  onResize()
  window.addEventListener('resize', onResize)
  loadSettings()
})

onUnmounted(() => window.removeEventListener('resize', onResize))

// ==================== 表单校验规则 ====================

const rules: FormRules = {
  baseUrl: [
    {
      validator: (_rule, value, callback) => {
        if (usingEndpoint.value) {
          callback()
          return
        }
        const text = String(value ?? '').trim()
        if (!text) {
          callback(new Error('请填写基础地址，或改用完整地址'))
          return
        }
        const error = addressError(text, '基础地址')
        callback(error ? new Error(error) : undefined)
      },
      trigger: 'blur',
    },
  ],
  chatPath: [
    {
      validator: (_rule, value, callback) => {
        if (usingEndpoint.value) {
          callback()
          return
        }
        const text = String(value ?? '').trim()
        if (!text) {
          callback(new Error('请填写接口路径，如 /chat/completions；或改用完整地址'))
          return
        }
        if (!text.startsWith('/') || text.startsWith('//')) {
          callback(new Error('接口路径必须以单个 / 开头，如 /chat/completions'))
          return
        }
        if (text.includes('..') || text.includes('?') || text.includes('#')) {
          callback(new Error('接口路径不能包含 ..、? 或 #；需要查询参数时请填写「完整地址」'))
          return
        }
        callback()
      },
      trigger: 'blur',
    },
  ],
  endpointUrl: [
    {
      validator: (_rule, value, callback) => {
        const text = String(value ?? '').trim()
        if (!text) {
          callback()
          return
        }
        const error = addressError(text, '完整地址')
        callback(error ? new Error(error) : undefined)
      },
      trigger: 'blur',
    },
  ],
  name: [{ required: true, message: '请填写模型名称', trigger: 'blur' }],
  authHeaderName: [
    {
      validator: (_rule, value, callback) => {
        if (form.authMode !== 'API_KEY') {
          callback()
          return
        }
        const error = headerNameError(String(value ?? ''), '认证请求头名称')
        callback(error ? new Error(error) : undefined)
      },
      trigger: 'blur',
    },
  ],
}
</script>

<template>
  <div class="ai-settings-page">
    <div class="page-header">
      <h1>AI 接口配置</h1>
      <p class="sub">配置 AI 客服调用的模型接口，仅支持 OpenAI 兼容的 Chat Completions 协议</p>
    </div>

    <!-- 状态概览 -->
    <el-card class="status-card" shadow="hover">
      <div class="status-row">
        <div class="status-item">
          <span class="status-label">配置来源</span>
          <el-tag :type="source === 'DATABASE' ? 'success' : 'info'" size="small" effect="plain">
            {{ sourceText }}
          </el-tag>
        </div>
        <div class="status-item">
          <span class="status-label">AI 客服</span>
          <el-tag :type="loadedView?.enabled ? 'success' : 'info'" size="small">
            {{ loadedView?.enabled ? '已启用' : '未启用' }}
          </el-tag>
        </div>
        <div class="status-item">
          <span class="status-label">鉴权方式</span>
          <el-tag size="small" effect="plain">{{ AUTH_MODE_TEXT[loadedView?.authMode ?? ''] ?? '待加载' }}</el-tag>
        </div>
        <div class="status-item">
          <span class="status-label">API Key</span>
          <el-tag :type="apiKeyConfigured ? 'success' : 'warning'" size="small">
            {{ apiKeyConfigured ? '已配置' : '未配置' }}
          </el-tag>
        </div>
        <div class="status-item">
          <span class="status-label">配置版本</span>
          <el-tag size="small" effect="plain">v{{ version }}</el-tag>
        </div>
        <div class="status-actions">
          <el-button size="small" :icon="Refresh" :loading="loading" :disabled="busy" @click="refreshSettings">
            刷新
          </el-button>
        </div>
      </div>
      <p class="status-tip">{{ sourceTip }}</p>
    </el-card>

    <el-alert
      v-if="loadFailed"
      class="load-error"
      type="error"
      :closable="false"
      show-icon
      title="配置加载失败，已暂停保存与测试"
      description="请确认后端服务可用、已登录管理员账号，且已执行 AI 配置的数据库迁移脚本，然后点击上方状态栏的「刷新」重新加载。"
    />

    <!-- 配置表单 -->
    <el-card class="form-card" shadow="hover">
      <el-form
        ref="formRef"
        v-loading="loading"
        :model="form"
        :disabled="loading || saving || restoring || loadFailed"
        :rules="rules"
        :label-position="isNarrow ? 'top' : 'right'"
        :label-width="isNarrow ? undefined : '124px'"
        @submit.prevent
      >
        <el-tabs v-model="activeTab" class="settings-tabs">
          <!-- ==================== 基本连接 ==================== -->
          <el-tab-pane label="基本连接" name="basic">
            <el-form-item label="启用 AI 客服">
              <el-switch v-model="form.enabled" inline-prompt active-text="启用" inactive-text="停用" />
              <span class="field-tip">停用后不请求外部模型，AI 回复回退到本地知识库规则</span>
            </el-form-item>

            <el-form-item label="基础地址" prop="baseUrl">
              <el-input v-model="form.baseUrl" placeholder="如 https://token.sensenova.cn/v1" clearable />
              <span class="field-tip">只填到版本号为止，不含 /chat/completions 等接口路径</span>
            </el-form-item>

            <el-form-item label="接口路径" prop="chatPath">
              <el-input v-model="form.chatPath" placeholder="如 /chat/completions" clearable />
              <span class="field-tip">以 / 开头，与基础地址拼接；仅支持 OpenAI 兼容的 Chat Completions 路径，地址需要查询参数时请填写「完整地址」</span>
            </el-form-item>

            <el-form-item label="完整地址" prop="endpointUrl">
              <el-input
                v-model="form.endpointUrl"
                placeholder="可留空；填写后优先于「基础地址 + 接口路径」"
                clearable
              />
              <span class="field-tip">接口地址与常见服务不同时使用，填写后上面的基础地址与接口路径不再生效</span>
            </el-form-item>

            <el-form-item label="推荐配置">
              <el-button :icon="MagicStick" @click="fillRecommended">填入 SenseNova 推荐地址与模型</el-button>
              <span class="field-tip">
                填入 {{ RECOMMENDED.baseUrl }} + {{ RECOMMENDED.chatPath }}、模型 {{ RECOMMENDED.name }}，并清空「完整地址」覆盖项；不会修改已输入的 API Key
              </span>
            </el-form-item>

            <el-form-item label="最终地址">
              <div class="url-preview">
                <div class="url-line">
                  <el-tag size="small" effect="plain" :type="usingEndpoint ? 'warning' : 'info'">
                    {{ usingEndpoint ? '完整地址优先' : '基础地址 + 接口路径' }}
                  </el-tag>
                  <span class="url-text">{{ finalUrl || '（尚未填写地址）' }}</span>
                </div>
                <p class="url-hint" :class="{ 'url-hint-error': !!previewWarning }">{{ previewHint }}</p>
              </div>
            </el-form-item>

            <el-alert
              type="info"
              :closable="false"
              show-icon
              title="只支持 OpenAI 兼容接口"
              description="后端按 Chat Completions 协议发送请求（messages + model 等字段），不支持旧版 Access Key 换取令牌的接口，也不做流式展示。"
            />
          </el-tab-pane>

          <!-- ==================== 认证 ==================== -->
          <el-tab-pane label="认证" name="auth">
            <el-alert
              type="info"
              :closable="false"
              show-icon
              title="密钥安全"
              description="密钥只提交给本项目后端保存，不写入浏览器本地存储，也不会从后端回显；请勿在公共电脑上填写或保存密钥。"
            />

            <el-form-item label="鉴权方式">
              <el-radio-group v-model="form.authMode" class="auth-mode-group">
                <el-radio value="BEARER">Bearer 令牌：Authorization 请求头带 Bearer 前缀（多数 OpenAI 兼容服务）</el-radio>
                <el-radio value="API_KEY">自定义请求头：密钥放在指定名称的请求头中，不带前缀</el-radio>
                <el-radio value="NONE">无需鉴权：不发送任何鉴权请求头（仅本机或内网服务）</el-radio>
              </el-radio-group>
            </el-form-item>

            <el-form-item v-if="form.authMode === 'API_KEY'" label="请求头名称" prop="authHeaderName">
              <el-input v-model="form.authHeaderName" placeholder="如 api-key 或 X-Api-Key" clearable />
              <span class="field-tip">密钥将放入该请求头，不添加 Bearer 前缀</span>
            </el-form-item>
            <el-form-item v-else label="请求头名称">
              <el-input :model-value="form.authMode === 'BEARER' ? 'Authorization' : '（不发送鉴权请求头）'" disabled />
              <span class="field-tip">
                {{ form.authMode === 'BEARER' ? 'Bearer 模式固定使用 Authorization: Bearer 加 API Key' : '无需鉴权模式不发送任何鉴权请求头' }}
              </span>
            </el-form-item>

            <el-form-item label="API Key">
              <el-input
                v-model="form.apiKey"
                type="password"
                show-password
                autocomplete="off"
                name="aiSettingsApiKey"
                :disabled="form.clearApiKey"
                :placeholder="apiKeyPlaceholder"
              />
              <div class="key-state">
                <el-tag size="small" effect="plain" :type="apiKeyConfigured ? 'success' : 'warning'">
                  {{ apiKeyConfigured ? '后端已保存密钥' : '后端尚未保存密钥' }}
                </el-tag>
                <span class="field-tip">
                  已保存的密钥不回显；留空表示保留现有密钥，填写新值将在保存后覆盖，输入内容不会被页面存储。
                  只填写密钥本身，不要带 Bearer 前缀或引号；启用 AI 客服或测试时会校验密钥是否已配置
                </span>              </div>
            </el-form-item>

            <el-form-item label="清除密钥">
              <el-checkbox v-model="form.clearApiKey">清除后端已保存的密钥</el-checkbox>
              <span class="field-tip">
                勾选后保存会删除已保存的密钥（其它配置保留），输入框同时被禁用；清除后需重新填写密钥才能调用模型
              </span>
            </el-form-item>
          </el-tab-pane>

          <!-- ==================== 请求参数 ==================== -->
          <el-tab-pane label="请求参数" name="params">
            <el-form-item label="模型名称" prop="name">
              <el-input v-model="form.name" placeholder="如 sensenova-6.8-flash-lite" clearable />
              <div class="inline-actions">
                <span class="field-tip">自由填写，必须是该服务当前账号已开通的模型 ID</span>
                <el-button link type="primary" @click="fillRecommendedName">填入推荐模型</el-button>
              </div>
            </el-form-item>

            <el-form-item label="请求超时">
              <el-input-number v-model="form.timeoutSeconds" :min="1" :max="120" controls-position="right" />
              <span class="unit">秒</span>
              <span class="field-tip">单次请求的总时限（含生成内容），范围 1~120 秒</span>
            </el-form-item>

            <el-form-item label="连接超时">
              <el-input-number v-model="form.connectTimeoutSeconds" :min="1" :max="30" controls-position="right" />
              <span class="unit">秒</span>
              <span class="field-tip">建立连接的最长等待时间，范围 1~30 秒</span>
            </el-form-item>

            <el-form-item label="最大输出">
              <el-input-number v-model="form.maxOutputTokens" :min="64" :max="32768" :step="64" controls-position="right" />
              <span class="unit">tokens</span>
              <span class="field-tip">单次回复的最大生成长度，范围 64~32768</span>
            </el-form-item>

            <el-form-item label="长度参数名">
              <el-select v-model="form.maxTokensParameter" class="narrow-select">
                <el-option label="max_tokens（多数 OpenAI 兼容服务）" value="max_tokens" />
                <el-option label="max_completion_tokens（部分新模型）" value="max_completion_tokens" />
              </el-select>
              <span class="field-tip">「最大输出」在请求体中使用的字段名，需与模型要求一致，选错会被服务端拒绝</span>
            </el-form-item>

            <el-form-item label="历史消息">
              <el-input-number v-model="form.historyMessages" :min="0" :max="50" controls-position="right" />
              <span class="unit">条</span>
              <span class="field-tip">随当前提问一起发送的最近会话消息条数，0 表示不发送历史（范围 0~50）</span>
            </el-form-item>

            <el-form-item label="温度">
              <el-input-number
                v-model="form.temperature"
                :min="0"
                :max="2"
                :step="0.1"
                :precision="2"
                :disabled="!sendTemperature"
                controls-position="right"
              />
              <el-checkbox v-model="sendTemperature" class="inline-check">发送 temperature 参数</el-checkbox>
              <span class="field-tip">范围 0~2；取消勾选后请求体不含 temperature，由服务端使用默认值</span>
            </el-form-item>
          </el-tab-pane>

          <!-- ==================== 高级配置 ==================== -->
          <el-tab-pane label="高级配置" name="advanced">
            <el-form-item label="附加请求头">
              <div class="header-list">
                <div v-for="row in savedHeaders" :key="`saved-${row.name}`" class="header-row">
                  <span class="header-name">{{ row.name }}</span>
                  <el-tag size="small" effect="plain" :type="row.removed ? 'info' : row.value.trim() ? 'warning' : 'success'">
                    {{ row.removed ? '保存后移除' : row.value.trim() ? '保存后覆盖' : '值已保存' }}
                  </el-tag>
                  <el-input
                    v-model="row.value"
                    class="header-value"
                    :disabled="row.removed"
                    placeholder="留空表示保留已保存的值"
                  />
                  <el-button v-if="!row.removed" link type="danger" @click="row.removed = true">移除</el-button>
                  <el-button v-else link type="primary" @click="row.removed = false">撤销</el-button>
                </div>

                <div v-for="(row, index) in newHeaders" :key="`new-${index}`" class="header-row">
                  <el-input v-model="row.name" class="header-name-input" placeholder="请求头名称，如 X-Api-Version" />
                  <el-input v-model="row.value" class="header-value" placeholder="请求头值（必填）" />
                  <el-button link type="danger" :icon="Delete" @click="removeNewHeader(index)">删除</el-button>
                </div>

                <el-button link type="primary" :icon="Plus" @click="addNewHeader">添加请求头</el-button>

                <p class="field-tip">
                  已保存的请求头只显示名称、不回显值：值留空表示保留原值，点「移除」并保存才会删除该请求头。
                  最多 {{ MAX_CUSTOM_HEADERS }} 项；Content-Type、Authorization、Cookie 等由系统统一发送，不能在此重复或覆盖。
                </p>
              </div>
            </el-form-item>

            <el-form-item label="额外请求体">
              <el-input
                v-model="form.extraBodyText"
                type="textarea"
                :rows="8"
                class="json-textarea"
                placeholder="可留空；填写 JSON 对象后随请求一起发送"
              />
              <div class="inline-actions">
                <el-button link type="primary" @click="formatExtraBody">格式化</el-button>
                <el-button link type="primary" @click="fillExtraBodyExample">填入示例</el-button>
                <el-button link @click="form.extraBodyText = ''">清空</el-button>
              </div>
              <p v-if="extraBodyError" class="field-error">{{ extraBodyError }}</p>
              <p class="field-tip">
                必须是 JSON 对象（{...}），留空表示不发送额外字段。可填写厂商扩展参数，例如
                可填写所选模型支持的 thinking、reasoning_effort、top_p 等参数；各模型支持范围不同，请以提供商文档为准，先测试再保存。
                以下字段由平台统一生成，禁止覆盖：model、messages、stream、max_tokens、max_completion_tokens、temperature
                以及 tools、tool_choice、functions、function_call、n 等工具调用字段（输出长度字段名请在「请求参数」中选择）；
                请勿在此填写任何密钥、令牌或密码。
              </p>
            </el-form-item>
          </el-tab-pane>
        </el-tabs>

        <!-- 操作区：测试与保存分开 -->
        <div class="action-bar">
          <div class="action-side">
            <el-button :disabled="busy" @click="resetDraft">重置草稿</el-button>
            <el-button type="danger" plain :loading="restoring" :disabled="busy && !restoring" @click="handleRestoreLocal">
              恢复本机配置
            </el-button>
          </div>
          <div class="action-main">
            <el-button
              type="primary"
              plain
              :loading="testing"
              :disabled="testDisabled"
              @click="handleTest"
            >
              测试当前草稿
            </el-button>
            <el-button type="primary" :loading="saving" :disabled="saveDisabled" @click="handleSave">
              保存配置
            </el-button>
          </div>
        </div>

        <p class="action-tip">
          测试使用当前草稿直接请求模型服务，不保存配置，会真实发送一句「你好」，消耗少量 token；
          已转人工或已关闭的客服会话不会再触发 AI 回复，需要新建会话后才能继续测试 AI。保存成功后数据库配置即时生效，失败则保留原有配置；
          若提示「配置已被其他管理员更新」，请点「刷新」重新加载后再修改。
        </p>
      </el-form>
    </el-card>

    <!-- 测试结果 -->
    <el-card v-if="testResult || testDiscarded" class="result-card" shadow="hover">
      <template #header>
        <div class="result-header">
          <span>连接测试结果</span>
          <el-tag v-if="testResult" :type="testResult?.success ? 'success' : 'danger'" size="small">
            {{ testResult?.success ? '测试成功' : '测试失败' }}
          </el-tag>
          <el-tag v-else type="info" size="small">已作废</el-tag>
        </div>
      </template>

      <el-alert
        v-if="testDiscarded"
        type="warning"
        :closable="false"
        show-icon
        title="测试期间草稿已被修改，本次结果已作废"
        description="测试结果只对发起测试时的草稿有效，请基于当前配置重新测试。"
      />

      <template v-else-if="testResult">
        <el-alert
          v-if="testResult?.success"
          type="success"
          :closable="false"
          show-icon
          :title="`模型 ${testResult?.model || form.name} 响应正常，耗时 ${testResult?.elapsedMs ?? 0} ms`"
        />
        <el-alert
          v-else
          type="error"
          :closable="false"
          show-icon
          :title="testResult?.message || '连接测试失败'"
          :description="testResult?.errorCode ? `错误码：${testResult?.errorCode}` : ''"
        />

        <el-descriptions class="result-detail" :column="isNarrow ? 1 : 2" border size="small">
          <el-descriptions-item label="测试结果">{{ testResult?.success ? '成功' : '失败' }}</el-descriptions-item>
          <el-descriptions-item label="耗时">{{ testResult?.elapsedMs ?? 0 }} ms</el-descriptions-item>
          <el-descriptions-item label="HTTP 状态">
            {{ testResult?.httpStatus === null ? '无响应状态' : testResult?.httpStatus }}
          </el-descriptions-item>
          <el-descriptions-item label="模型">{{ testResult?.model || form.name || '-' }}</el-descriptions-item>
          <el-descriptions-item label="错误码">{{ testResult?.errorCode || '无' }}</el-descriptions-item>
          <el-descriptions-item label="返回信息" :span="isNarrow ? 1 : 2">
            <span class="result-message">{{ testResult?.message || '-' }}</span>
          </el-descriptions-item>
        </el-descriptions>

        <p class="result-tip">
          本次测试未保存任何配置；与后端已保存的配置不一致时，请用「保存配置」写入。修改任一配置项后该结果会自动清除。
          已转人工或已关闭的客服会话不会再触发 AI 回复，需要新建会话后才能继续测试 AI。
        </p>
      </template>
    </el-card>
  </div>
</template>

<style scoped>
.page-header h1 {
  margin: 0 0 8px;
}
.sub {
  margin: 0 0 20px;
  color: var(--muted-foreground);
  font-size: 0.875rem;
}

/* ==================== 状态概览 ==================== */
.status-card {
  margin-bottom: 16px;
}
.status-row {
  display: flex;
  flex-wrap: wrap;
  gap: 10px 20px;
  align-items: center;
  min-width: 0;
}
.status-item {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}
.status-label {
  color: var(--muted-foreground);
  font-size: 0.8125rem;
  white-space: nowrap;
}
.status-actions {
  margin-left: auto;
}
.status-tip {
  margin: 12px 0 0;
  color: var(--muted-foreground);
  font-size: 0.8125rem;
  line-height: 1.6;
}
.load-error {
  margin-bottom: 16px;
}

/* ==================== 表单 ==================== */
.form-card {
  margin-bottom: 16px;
}
.settings-tabs :deep(.el-tabs__item) {
  font-weight: 600;
}
.field-tip {
  flex: 0 0 100%;
  margin-top: 4px;
  color: var(--muted-foreground);
  font-size: 0.75rem;
  line-height: 1.6;
  word-break: break-word;
}
.field-error {
  flex: 0 0 100%;
  margin: 4px 0 0;
  color: var(--el-color-danger);
  font-size: 0.75rem;
  line-height: 1.6;
  word-break: break-word;
}
.unit {
  margin-left: 8px;
  color: var(--muted-foreground);
  font-size: 0.8125rem;
}
.inline-actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 4px 12px;
  width: 100%;
  margin-top: 2px;
}
.inline-actions .field-tip {
  flex: 1 1 auto;
  margin-top: 0;
}
.inline-check {
  margin-left: 12px;
}
.auth-mode-group {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 6px;
  width: 100%;
  line-height: 1.6;
}
.auth-mode-group :deep(.el-radio) {
  display: flex;
  align-items: flex-start;
  height: auto;
  margin-right: 0;
  white-space: normal;
}
.auth-mode-group :deep(.el-radio__label) {
  font-size: 0.8125rem;
  line-height: 1.6;
  white-space: normal;
  word-break: break-word;
}
.key-state {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 4px 10px;
  width: 100%;
  margin-top: 6px;
}
.key-state .field-tip {
  flex: 1 1 220px;
  margin-top: 0;
}
.narrow-select {
  width: 100%;
  max-width: 320px;
}
.json-textarea :deep(.el-textarea__inner) {
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 0.8125rem;
  line-height: 1.6;
}

/* 最终地址预览 */
.url-preview {
  width: 100%;
  padding: 10px 12px;
  border: 1px solid var(--border-color);
  border-radius: 8px;
  background: var(--bg-page);
}
.url-line {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  min-width: 0;
}
.url-text {
  flex: 1 1 200px;
  min-width: 0;
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 0.8125rem;
  word-break: break-all;
  line-height: 1.6;
}
.url-hint {
  margin: 6px 0 0;
  color: var(--muted-foreground);
  font-size: 0.75rem;
  line-height: 1.6;
  word-break: break-word;
}
.url-hint-error {
  color: var(--el-color-danger);
}

/* 附加请求头 */
.header-list {
  width: 100%;
}
.header-row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  padding: 8px 10px;
  margin-bottom: 8px;
  border: 1px dashed var(--border-color);
  border-radius: 8px;
}
.header-name {
  min-width: 0;
  font-weight: 600;
  font-size: 0.8125rem;
  word-break: break-all;
}
.header-name-input,
.header-value {
  flex: 1 1 180px;
  min-width: 0;
}

/* ==================== 操作区 ==================== */
.action-bar {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  align-items: center;
  justify-content: space-between;
  margin-top: 8px;
  padding-top: 16px;
  border-top: 1px solid var(--border-color);
}
.action-side,
.action-main {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  min-width: 0;
}
.action-bar :deep(.el-button + .el-button) {
  margin-left: 0;
}
.action-tip {
  margin: 12px 0 0;
  color: var(--muted-foreground);
  font-size: 0.75rem;
  line-height: 1.6;
  word-break: break-word;
}

/* ==================== 测试结果 ==================== */
.result-header {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px;
}
.result-detail {
  margin-top: 12px;
}
.result-message {
  word-break: break-word;
}
.result-tip {
  margin: 12px 0 0;
  color: var(--muted-foreground);
  font-size: 0.75rem;
  line-height: 1.6;
}

/* ==================== 窄屏适配（320 / 390） ==================== */
@media (max-width: 768px) {
  .status-actions {
    margin-left: 0;
  }
  .action-bar {
    flex-direction: column;
    align-items: stretch;
  }
  .action-side,
  .action-main {
    flex-direction: column;
    align-items: stretch;
  }
  .action-side :deep(.el-button),
  .action-main :deep(.el-button) {
    width: 100%;
  }
  .inline-check {
    margin-left: 0;
  }
  .narrow-select {
    max-width: 100%;
  }
}
@media (max-width: 480px) {
  .settings-tabs :deep(.el-tabs__item) {
    padding: 0 6px;
    font-size: 12px;
  }
  .header-row {
    padding: 8px;
  }
}
</style>
