<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { csSocket, type WsConnectionState, type WsPush } from '@/utils/ws'
import {
  conversationDetail,
  conversationMessages,
  createConversation,
  evaluateConversation,
  myConversations,
  requestHuman,
  sendConversationMessage,
  type AiTaskView,
  type ConversationView,
  type MessageView,
} from '@/api/cs'

type DraftState = 'sending' | 'retry'
interface MessageDraft {
  content: string
  clientMsgId: string
  state: DraftState
}

const conversations = ref<ConversationView[]>([])
const current = ref<ConversationView | null>(null)
const messages = ref<MessageView[]>([])
const loading = ref(false)
const input = ref('')
const chatBodyRef = ref<HTMLElement | null>(null)
const drafts = ref<Record<number, MessageDraft>>({})
/** 只记录 REST 按游标完整补拉后的进度，不能由实时消息推进。 */
const messageCursors = new Map<number, number>()
const messageSyncChains = new Map<number, Promise<boolean>>()
const taskPollers = new Map<number, ReturnType<typeof setInterval>>()
const confirmTimers = new Map<number, ReturnType<typeof setTimeout>>()
const shownErrors = new Set<string>()
let selectionVersion = 0
let connectedOnce = false

const statusMap: Record<string, { label: string; type: string }> = {
  AI_PROCESSING: { label: 'AI 处理中', type: 'warning' },
  WAITING_HUMAN: { label: '等待人工', type: 'info' },
  HUMAN_PROCESSING: { label: '人工处理中', type: 'primary' },
  ESCALATED_ADMIN: { label: '已转交管理员', type: 'danger' },
  CLOSED: { label: '已关闭', type: 'info' },
}

const statusLabel = computed(() => statusMap[current.value?.conversationStatus ?? '']?.label ?? '-')
const isClosed = computed(() => current.value?.conversationStatus === 'CLOSED')
const currentDraft = computed(() => (current.value ? drafts.value[current.value.id] : undefined))
const sending = computed(() => currentDraft.value?.state === 'sending')
const canRetry = computed(() => currentDraft.value?.state === 'retry')
const isAiWaiting = computed(() => isTaskPending(current.value?.aiTask))
const canRequestHuman = computed(() => !!current.value && !isClosed.value
  && (current.value.conversationStatus === 'AI_PROCESSING' || isAiWaiting.value))

function fmtTime(t?: string): string {
  if (!t) return ''
  return t.length >= 16 ? t.slice(0, 16) : t
}

function isTaskPending(task?: AiTaskView): boolean {
  return task?.status === 'PENDING' || task?.status === 'RUNNING'
}

function isTaskTerminal(task?: AiTaskView): boolean {
  return !!task && !isTaskPending(task)
}

function showErrorOnce(message: string, key = message) {
  if (shownErrors.has(key)) return
  shownErrors.add(key)
  ElMessage.error(message)
  setTimeout(() => shownErrors.delete(key), 3000)
}

function sortMessages(list: MessageView[]): MessageView[] {
  const unique = new Map<number, MessageView>()
  list.forEach((message) => unique.set(message.messageId, message))
  return [...unique.values()].sort((a, b) => a.messageId - b.messageId)
}

function updateRestCursor(conversationId: number, list: MessageView[]) {
  const lastId = list.reduce((max, message) => Math.max(max, message.messageId), messageCursors.get(conversationId) ?? 0)
  messageCursors.set(conversationId, lastId)
}

/** 消息可能由补拉和推送同时到达，因此统一按 ID 去重排序。 */
function mergeMessage(message: MessageView) {
  if (message.senderType === 'USER' && message.clientMsgId) clearDraft(message.conversationId, message.clientMsgId)
  if (current.value?.id !== message.conversationId) return
  messages.value = sortMessages([...messages.value, message])
  nextTick(scrollBottom)
}

function scrollBottom() {
  chatBodyRef.value?.scrollTo({ top: chatBodyRef.value.scrollHeight })
}

function setDraft(conversationId: number, draft: MessageDraft) {
  drafts.value = { ...drafts.value, [conversationId]: draft }
}

function clearDraft(conversationId: number, clientMsgId?: string) {
  const draft = drafts.value[conversationId]
  if (!draft || (clientMsgId && draft.clientMsgId !== clientMsgId)) return
  const next = { ...drafts.value }
  delete next[conversationId]
  drafts.value = next
  const timer = confirmTimers.get(conversationId)
  if (timer) clearTimeout(timer)
  confirmTimers.delete(conversationId)
  if (current.value?.id === conversationId) input.value = ''
}

function shouldApplyTask(previous: AiTaskView | undefined, next: AiTaskView): boolean {
  if (!previous) return true
  if (next.id !== previous.id) return next.id > previous.id
  if (isTaskTerminal(previous) && isTaskPending(next)) return false
  return !(previous.updatedAt && next.updatedAt && next.updatedAt < previous.updatedAt)
}

function mergeConversation(conversation: ConversationView): ConversationView {
  const listed = conversations.value.find((item) => item.id === conversation.id)
  const selected = current.value?.id === conversation.id && (!listed || current.value.version >= listed.version)
    ? current.value
    : listed
  if (selected && conversation.version < selected.version) return selected
  const aiTask = conversation.aiTask && shouldApplyTask(selected?.aiTask, conversation.aiTask)
    ? conversation.aiTask
    : selected?.aiTask
  const merged = { ...selected, ...conversation, aiTask }
  conversations.value = conversations.value.map((item) => item.id === conversation.id ? merged : item)
  if (!listed) conversations.value.unshift(merged)
  if (current.value?.id === conversation.id) current.value = { ...current.value, ...merged }
  if (merged.aiTask) watchTask(merged.aiTask)
  if (merged.conversationStatus === 'CLOSED') clearTaskPoller(merged.id)
  return merged
}

function applyTask(task?: AiTaskView) {
  if (!task) return
  const conversation = conversations.value.find((item) => item.id === task.conversationId)
    ?? (current.value?.id === task.conversationId ? current.value : undefined)
  const previous = conversation?.aiTask
  if (!shouldApplyTask(previous, task)) return
  if (conversation) mergeConversation({ ...conversation, aiTask: task })
  else if (current.value?.id === task.conversationId) current.value = { ...current.value, aiTask: task }
  const becameTerminal = isTaskTerminal(task) && !isTaskTerminal(previous)
  watchTask(task)
  if (becameTerminal) void syncMessages(task.conversationId)
}

async function loadConversations() {
  try {
    const list = await myConversations()
    const ids = new Set(list.map((conversation) => conversation.id))
    list.forEach(mergeConversation)
    const ordered = list.map((conversation) => conversations.value.find((item) => item.id === conversation.id) ?? conversation)
    conversations.value = [...ordered, ...conversations.value.filter((item) => !ids.has(item.id))]
    conversations.value.forEach((conversation) => conversation.aiTask && watchTask(conversation.aiTask))
  } catch {
    // HTTP 拦截器统一提示。
  }
}

async function syncMessagesNow(conversationId: number, reset = false): Promise<boolean> {
  if (reset) messageCursors.set(conversationId, 0)
  let afterId = messageCursors.get(conversationId) ?? 0
  try {
    while (true) {
      const list = await conversationMessages(conversationId, afterId)
      if (!list.length) break
      updateRestCursor(conversationId, list)
      list.forEach((message) => {
        if (message.senderType === 'USER' && message.clientMsgId) clearDraft(conversationId, message.clientMsgId)
      })
      if (current.value?.id === conversationId) {
        messages.value = sortMessages([...messages.value, ...list])
        nextTick(scrollBottom)
      }
      if (list.length < 50) break
      afterId = messageCursors.get(conversationId) ?? afterId
    }
    return true
  } catch {
    // 重连补拉失败会在下一次连接或轮询继续尝试。
    return false
  }
}

/** 同一会话的补拉串行执行，避免轮询与重连并发跨过消息页。 */
function syncMessages(conversationId: number, reset = false): Promise<boolean> {
  const previous = messageSyncChains.get(conversationId) ?? Promise.resolve(true)
  const next = previous.catch(() => false).then(() => syncMessagesNow(conversationId, reset))
  messageSyncChains.set(conversationId, next)
  void next.finally(() => {
    if (messageSyncChains.get(conversationId) === next) messageSyncChains.delete(conversationId)
  })
  return next
}

async function refreshConversation(conversationId: number) {
  try {
    const detail = await conversationDetail(conversationId)
    mergeConversation(detail)
    await syncMessages(conversationId)
  } catch {
    // HTTP 拦截器统一提示。
  }
}

async function openConversation(conversation: ConversationView) {
  const version = ++selectionVersion
  current.value = conversation
  input.value = drafts.value[conversation.id]?.content ?? ''
  messages.value = []
  loading.value = true
  try {
    const [detail] = await Promise.all([conversationDetail(conversation.id), syncMessages(conversation.id, true)])
    if (version !== selectionVersion || current.value?.id !== conversation.id) return
    const merged = mergeConversation(detail)
    if (current.value?.id === conversation.id && merged.aiTask) watchTask(merged.aiTask)
    nextTick(scrollBottom)
  } finally {
    if (version === selectionVersion) loading.value = false
  }
}

function clearTaskPoller(conversationId: number) {
  const poller = taskPollers.get(conversationId)
  if (poller) clearInterval(poller)
  taskPollers.delete(conversationId)
}

/** 仅在任务仍未结束时补查，避免长时间无意义轮询。 */
function watchTask(task: AiTaskView) {
  if (!isTaskPending(task)) {
    clearTaskPoller(task.conversationId)
    return
  }
  if (taskPollers.has(task.conversationId)) return
  taskPollers.set(task.conversationId, setInterval(() => {
    void refreshConversation(task.conversationId)
  }, 2000))
}

// ==================== 发起会话（FR-C07） ====================

const createVisible = ref(false)
const creating = ref(false)
const createForm = reactive({ firstContent: '' })

async function handleCreate() {
  if (!createForm.firstContent.trim()) {
    ElMessage.warning('请先描述您的问题')
    return
  }
  creating.value = true
  try {
    const conversation = await createConversation({ sourceType: 'HELP_CENTER', firstContent: createForm.firstContent.trim() })
    createVisible.value = false
    createForm.firstContent = ''
    mergeConversation(conversation)
    await openConversation(conversation)
    if (conversation.aiTask) watchTask(conversation.aiTask)
    ElMessage.success('会话已创建，AI 客服为您服务')
  } finally {
    creating.value = false
  }
}

// ==================== 发送消息（FR-C08/C09） ====================

function confirmSentDraft(conversationId: number, clientMsgId: string) {
  const oldTimer = confirmTimers.get(conversationId)
  if (oldTimer) clearTimeout(oldTimer)
  confirmTimers.set(conversationId, setTimeout(async () => {
    const synced = await syncMessages(conversationId)
    const draft = drafts.value[conversationId]
    if (draft?.clientMsgId === clientMsgId) {
      setDraft(conversationId, { ...draft, state: 'retry' })
      if (synced) showErrorOnce('消息尚未确认送达，请重试', `unconfirmed-${conversationId}-${clientMsgId}`)
    }
  }, 2000))
}

async function submitDraft(conversationId: number, draft: MessageDraft) {
  setDraft(conversationId, { ...draft, state: 'sending' })
  if (csSocket.sendMessage({ conversationId, content: draft.content, clientMsgId: draft.clientMsgId })) {
    confirmSentDraft(conversationId, draft.clientMsgId)
    return
  }
  try {
    const result = await sendConversationMessage(conversationId, {
      content: draft.content,
      clientMsgId: draft.clientMsgId,
    })
    if (result.conversationStatus) {
      const conversation = conversations.value.find((item) => item.id === conversationId) ?? current.value
      if (conversation && conversation.id === conversationId) {
        mergeConversation({ ...conversation, conversationStatus: result.conversationStatus })
      }
    }
    applyTask(result.task)
    if (result.userMessageId) clearDraft(conversationId, draft.clientMsgId)
    await syncMessages(conversationId)
  } catch {
    const currentDraftForConversation = drafts.value[conversationId]
    if (currentDraftForConversation?.clientMsgId === draft.clientMsgId) {
      setDraft(conversationId, { ...currentDraftForConversation, state: 'retry' })
    }
    // REST 拦截器已展示服务端错误，保留草稿供用户使用相同幂等键重试。
  }
}

function handleSend() {
  const content = input.value.trim()
  if (!content) return
  if (!current.value) {
    ElMessage.warning('请先选择会话')
    return
  }
  if (isClosed.value) {
    ElMessage.warning('会话已关闭，无法发送消息')
    return
  }
  if (isAiWaiting.value) {
    ElMessage.warning('AI 正在生成回复，请稍候或转人工')
    return
  }
  const saved = drafts.value[current.value.id]
  if (saved?.state === 'sending') return
  const draft = saved?.content === content
    ? saved
    : { content, clientMsgId: crypto.randomUUID(), state: 'retry' as const }
  setDraft(current.value.id, draft)
  void submitDraft(current.value.id, draft)
}

// ==================== 转人工（FR-C10） ====================

async function handleRequestHuman() {
  if (!current.value) return
  const conversationId = current.value.id
  try {
    await ElMessageBox.confirm('转人工后，您的完整会话上下文将转入人工队列，请耐心等待客服接入。', '申请人工客服', {
      type: 'info',
      confirmButtonText: '转人工',
      cancelButtonText: '取消',
    })
  } catch {
    return
  }
  const conversation = await requestHuman(conversationId)
  mergeConversation(conversation)
  clearTaskPoller(conversation.id)
  ElMessage.success('已转入人工队列，请稍候')
}

// ==================== 满意度评价（FR-C15） ====================

const evalVisible = ref(false)
const evalScore = ref(5)
const evalContent = ref('')
const submittingEval = ref(false)

async function handleEvaluate() {
  if (!current.value) return
  submittingEval.value = true
  try {
    await evaluateConversation(current.value.id, { score: evalScore.value, content: evalContent.value.trim() || undefined })
    ElMessage.success('感谢您的评价')
    evalVisible.value = false
  } finally {
    submittingEval.value = false
  }
}

// ==================== WebSocket ====================

function errorData(msg: Extract<WsPush, { type: 'ERROR' }>) {
  if (typeof msg.data === 'string') return { message: msg.data }
  return msg.data ?? { message: msg.message ?? '客服连接发生错误' }
}

function onWsMessage(msg: Parameters<Parameters<typeof csSocket.onMessage>[0]>[0]) {
  if (msg.type === 'MESSAGE_NEW') {
    mergeMessage(msg.data)
  } else if (msg.type === 'AI_STATUS') {
    applyTask(msg.data)
  } else if (msg.type === 'AI_RESPONSE') {
    if (msg.data.aiMessage) mergeMessage(msg.data.aiMessage)
    applyTask(msg.data.task)
    const conversation = conversations.value.find((item) => item.id === msg.data.conversationId)
      ?? (current.value?.id === msg.data.conversationId ? current.value : undefined)
    if (conversation && msg.data.conversationStatus) {
      mergeConversation({ ...conversation, conversationStatus: msg.data.conversationStatus })
    }
    if (msg.data.decision === 'TRANSFER_HUMAN') showErrorOnce('AI 已为您转入人工客服队列', `transfer-${msg.data.conversationId}`)
  } else if (msg.type === 'CONVERSATION_CHANGED') {
    const wasClosed = current.value?.id === msg.data.id && current.value.conversationStatus !== 'CLOSED'
    mergeConversation(msg.data)
    if (wasClosed && msg.data.conversationStatus === 'CLOSED') showErrorOnce('会话已关闭', `closed-${msg.data.id}`)
  } else if (msg.type === 'ERROR') {
    const error = errorData(msg)
    if (error.conversationId) {
      const timer = confirmTimers.get(error.conversationId)
      if (timer) clearTimeout(timer)
      confirmTimers.delete(error.conversationId)
    }
    const draft = error.conversationId ? drafts.value[error.conversationId] : undefined
    if (draft && (!error.clientMsgId || error.clientMsgId === draft.clientMsgId)) {
      setDraft(error.conversationId!, { ...draft, state: 'retry' })
    }
    showErrorOnce(error.message, `ws-${error.conversationId ?? ''}-${error.clientMsgId ?? error.message}`)
  }
}

async function recoverAfterReconnect() {
  await loadConversations()
  await Promise.all(conversations.value.map((conversation) => refreshConversation(conversation.id)))
  Object.entries(drafts.value).forEach(([id, draft]) => {
    if (draft.state === 'retry') void submitDraft(Number(id), draft)
  })
}

function onConnectionState(state: WsConnectionState) {
  if (state === 'OPEN') {
    connectedOnce = true
    void recoverAfterReconnect()
    return
  }
  if (state === 'CLOSED' && connectedOnce) {
    Object.entries(drafts.value).forEach(([id, draft]) => {
      if (draft.state === 'sending') setDraft(Number(id), { ...draft, state: 'retry' })
    })
    showErrorOnce('实时连接已断开，消息草稿已保留', 'connection-closed')
  }
}

let unsubscribeMessage: (() => void) | null = null
let unsubscribeConnection: (() => void) | null = null

onMounted(async () => {
  unsubscribeMessage = csSocket.onMessage(onWsMessage)
  unsubscribeConnection = csSocket.onConnectionState(onConnectionState)
  const token = localStorage.getItem('token')
  if (token) csSocket.connect(token)
  await loadConversations()
})

onUnmounted(() => {
  unsubscribeMessage?.()
  unsubscribeConnection?.()
  taskPollers.forEach((poller) => clearInterval(poller))
  confirmTimers.forEach((timer) => clearTimeout(timer))
  csSocket.close()
})
</script>

<template>
  <div class="page">
    <div class="container">
      <!-- 页头 -->
      <div class="page-header">
        <h1 class="page-title">在线客服</h1>
        <p class="page-subtitle">AI 客服 24 小时在线，也可转接人工</p>
      </div>

      <div class="support-layout">
        <!-- 左侧：会话列表 -->
        <div class="conv-sidebar">
          <div class="conv-sidebar-head">
            <span>我的会话</span>
            <button class="btn btn-primary btn-sm" @click="createVisible = true">发起咨询</button>
          </div>
          <div class="conv-list">
            <div v-if="conversations.length">
              <div v-for="c in conversations" :key="c.id" class="conv-item" :class="{ active: current?.id === c.id }"
                @click="openConversation(c)">
                <div class="conv-item-top">
                  <span class="conv-no">{{ c.conversationNo }}</span>
                  <span class="badge" :class="{
                    'badge-warning': ['AI_PROCESSING', 'WAITING_HUMAN'].includes(c.conversationStatus),
                    'badge-success': c.conversationStatus === 'HUMAN_PROCESSING',
                    'badge-destructive': c.conversationStatus === 'ESCALATED_ADMIN',
                    'badge-outline': c.conversationStatus === 'CLOSED',
                  }">
                    {{ statusMap[c.conversationStatus]?.label ?? c.conversationStatus }}
                  </span>
                </div>
                <div class="conv-meta">{{ fmtTime(c.createdAt) }}<span v-if="c.relatedOrderId"> · #{{ c.relatedOrderId }}</span></div>
              </div>
            </div>
            <p v-else class="text-muted" style="font-size: 0.8125rem; text-align: center; padding: 24px 0">
              暂无会话，点击右上角发起咨询
            </p>
          </div>
        </div>

        <!-- 右侧：聊天窗口（原型 chat-container） -->
        <div class="chat-container">
          <div class="chat-header">
            <div class="flex items-center gap-1">
              <span class="chat-avatar" style="width: 32px; height: 32px">🎮</span>
              <div>
                <div style="font-weight: 600" v-if="current">{{ current.conversationNo }}</div>
                <div class="chat-name" style="margin: 0" v-if="current">{{ statusLabel }}</div>
                <div style="font-weight: 600" v-else>智能客服</div>
              </div>
            </div>
            <button v-if="canRequestHuman" class="btn btn-warning btn-sm"
              @click="handleRequestHuman">转人工</button>
            <button v-else-if="current && isClosed" class="btn btn-secondary btn-sm" @click="evalVisible = true">评价</button>
          </div>

          <div ref="chatBodyRef" class="chat-body" v-loading="loading">
            <template v-if="current">
              <div v-for="m in messages" :key="m.messageId" class="chat-message"
                :class="m.senderType === 'USER' ? 'user' : 'ai'">
                <div class="chat-avatar">{{ m.senderType === 'USER' ? '我' : '🤖' }}</div>
                <div>
                  <div class="chat-name">
                    {{ m.senderType === 'USER' ? '我' : m.senderType === 'AI' ? 'AI 客服' : m.senderType === 'CS' ? '人工客服' : '系统' }}
                  </div>
                  <div class="chat-bubble">{{ m.content }}</div>
                  <div class="chat-name" style="margin-top: 2px">{{ fmtTime(m.createdAt) }}</div>
                </div>
              </div>
              <p v-if="!loading && !messages.length" class="text-muted" style="text-align: center; padding: 24px 0">
                暂无消息，输入内容开始咨询
              </p>
            </template>
            <p v-else class="text-muted" style="text-align: center; padding: 40px 0">选择或发起会话后开始聊天</p>
          </div>

          <div class="chat-footer">
            <div v-if="current && !isClosed">
              <div class="chat-form">
                <input v-model="input" class="form-control" maxlength="4000" :disabled="sending || isAiWaiting"
                  placeholder="输入您的问题，Enter 发送" @keyup.enter="handleSend" />
                <button class="btn btn-primary" :disabled="sending || isAiWaiting" @click="handleSend">
                  {{ sending ? '发送中' : canRetry ? '重试发送' : '发送' }}
                </button>
              </div>
              <p v-if="isAiWaiting" class="ai-waiting">AI 正在生成回复…</p>
            </div>
            <p v-else-if="current && isClosed" class="text-muted" style="font-size: 0.8125rem; text-align: center">
              会话已关闭{{ current.closedAt ? '于 ' + fmtTime(current.closedAt) : '' }}，如需帮助可发起新会话。
            </p>
            <p v-else class="text-muted" style="font-size: 0.8125rem; text-align: center">选择或发起会话后开始聊天</p>
          </div>
        </div>
      </div>
    </div>

    <!-- 发起咨询对话框 -->
    <el-dialog v-model="createVisible" title="发起咨询" width="min(520px, calc(100% - 32px))">
      <el-form label-width="80px">
        <el-form-item label="问题描述" required>
          <el-input v-model="createForm.firstContent" type="textarea" :rows="4" maxlength="4000" show-word-limit
            placeholder="请描述您遇到的问题，AI 客服会先为您解答" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" :loading="creating" @click="handleCreate">开始咨询</el-button>
      </template>
    </el-dialog>

    <!-- 满意度评价对话框 -->
    <el-dialog v-model="evalVisible" title="客服服务评价" width="min(480px, calc(100% - 32px))">
      <el-form label-width="80px">
        <el-form-item label="满意度" required>
          <el-rate v-model="evalScore" :max="5" :texts="['很差', '较差', '一般', '满意', '非常满意']" show-text />
        </el-form-item>
        <el-form-item label="评价内容">
          <el-input v-model="evalContent" type="textarea" :rows="3" maxlength="1000" show-word-limit placeholder="选填" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="evalVisible = false">取消</el-button>
        <el-button type="primary" :loading="submittingEval" @click="handleEvaluate">提交评价</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.support-layout {
  display: grid;
  grid-template-columns: 280px minmax(0, 1fr);
  gap: 24px;
  align-items: start;
}
.conv-sidebar {
  background: var(--card);
  border: 1px solid var(--border);
  border-radius: var(--radius);
  overflow: hidden;
}
.conv-sidebar-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 16px;
  border-bottom: 1px solid var(--border);
  font-weight: 600;
}
.conv-list {
  max-height: 560px;
  overflow-y: auto;
  padding: 8px;
}
.conv-item {
  padding: 10px 12px;
  border-radius: var(--radius);
  cursor: pointer;
  transition: background 0.15s;
}
.conv-item:hover {
  background: var(--muted);
}
.conv-item.active {
  background: var(--secondary);
}
.conv-item-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}
.conv-no {
  font-size: 0.8125rem;
  font-weight: 600;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.conv-meta {
  margin-top: 4px;
  font-size: 0.75rem;
  color: var(--muted-foreground);
}
.btn-warning {
  background: #f59e0b;
  color: #fff;
}
.btn-warning:hover {
  background: #d97706;
}
.chat-bubble {
  max-width: min(640px, 72vw);
  white-space: pre-wrap;
  overflow-wrap: anywhere;
  word-break: break-word;
}
.ai-waiting {
  margin: 8px 0 0;
  font-size: 0.8125rem;
  color: var(--muted-foreground);
  text-align: center;
}
@media (max-width: 768px) {
  .support-layout {
    grid-template-columns: 1fr;
  }
  .chat-container {
    height: 480px;
  }
}
</style>
