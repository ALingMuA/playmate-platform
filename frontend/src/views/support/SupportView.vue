<script setup lang="ts">
/**
 * 在线客服（对齐 frontend-prototype support.html 聊天界面）。
 *
 * <p>左侧我的会话列表，右侧聊天窗口（FR-C07~C15）：
 * 新会话由 AI 自动应答（FR-C09），可主动转人工（FR-C10），
 * AI 回复带"AI 客服"标识（FR-C21），会话关闭后可提交满意度评价（FR-C15）。
 * 消息经 WebSocket 实时推送，断线时 REST 补拉兜底。</p>
 */
import { computed, nextTick, onMounted, onUnmounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { csSocket } from '@/utils/ws'
import {
  conversationDetail,
  conversationMessages,
  createConversation,
  evaluateConversation,
  myConversations,
  requestHuman,
  type ConversationView,
  type MessageView,
} from '@/api/cs'

const conversations = ref<ConversationView[]>([])
const current = ref<ConversationView | null>(null)
const messages = ref<MessageView[]>([])
const loading = ref(false)
const sending = ref(false)
const input = ref('')
const chatBodyRef = ref<HTMLElement | null>(null)

const statusMap: Record<string, { label: string; type: string }> = {
  AI_PROCESSING: { label: 'AI 处理中', type: 'warning' },
  WAITING_HUMAN: { label: '等待人工', type: 'info' },
  HUMAN_PROCESSING: { label: '人工处理中', type: 'primary' },
  ESCALATED_ADMIN: { label: '已转交管理员', type: 'danger' },
  CLOSED: { label: '已关闭', type: 'info' },
}

const statusLabel = computed(() => statusMap[current.value?.conversationStatus ?? '']?.label ?? '-')
const isClosed = computed(() => current.value?.conversationStatus === 'CLOSED')

function fmtTime(t?: string): string {
  if (!t) return ''
  return t.length >= 16 ? t.slice(0, 16) : t
}

/** 按 messageId 去重追加 */
function mergeMessage(m: MessageView) {
  if (!messages.value.some((x) => x.messageId === m.messageId)) {
    messages.value.push(m)
    nextTick(scrollBottom)
  }
}

function scrollBottom() {
  chatBodyRef.value?.scrollTo({ top: chatBodyRef.value.scrollHeight })
}

async function loadConversations() {
  try {
    conversations.value = await myConversations()
    if (current.value) {
      const fresh = conversations.value.find((c) => c.id === current.value?.id)
      if (fresh) current.value = fresh
    }
  } catch {
    // 拦截器已提示
  }
}

async function openConversation(c: ConversationView) {
  current.value = c
  loading.value = true
  try {
    const [detail, list] = await Promise.all([conversationDetail(c.id), conversationMessages(c.id)])
    current.value = detail
    messages.value = list
    nextTick(scrollBottom)
  } finally {
    loading.value = false
  }
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
    const conv = await createConversation({
      sourceType: 'HELP_CENTER',
      firstContent: createForm.firstContent.trim(),
    })
    createVisible.value = false
    createForm.firstContent = ''
    conversations.value.unshift(conv)
    await openConversation(conv)
    ElMessage.success('会话已创建，AI 客服为您服务')
  } finally {
    creating.value = false
  }
}

// ==================== 发送消息（FR-C08/C09） ====================

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
  const ok = csSocket.sendMessage({
    conversationId: current.value.id,
    content,
    clientMsgId: crypto.randomUUID(),
  })
  if (!ok) {
    ElMessage.error('实时连接不可用，请刷新页面重试')
    return
  }
  input.value = ''
  sending.value = true
  setTimeout(() => (sending.value = false), 500)
}

// ==================== 转人工（FR-C10） ====================

async function handleRequestHuman() {
  if (!current.value) return
  try {
    await ElMessageBox.confirm('转人工后，您的完整会话上下文将转入人工队列，请耐心等待客服接入。', '申请人工客服', {
      type: 'info',
      confirmButtonText: '转人工',
      cancelButtonText: '取消',
    })
  } catch {
    return
  }
  const conv = await requestHuman(current.value.id)
  current.value = conv
  await loadConversations()
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

function onWsMessage(msg: Parameters<Parameters<typeof csSocket.onMessage>[0]>[0]) {
  if (msg.type === 'MESSAGE_NEW') {
    if (current.value && msg.data.conversationId === current.value.id) mergeMessage(msg.data)
  } else if (msg.type === 'AI_RESPONSE') {
    if (current.value && msg.data.conversationId === current.value.id) {
      if (msg.data.aiMessage) mergeMessage(msg.data.aiMessage)
      if (msg.data.conversationStatus) {
        current.value = { ...current.value, conversationStatus: msg.data.conversationStatus }
      }
      if (msg.data.decision === 'TRANSFER_HUMAN') {
        ElMessage.info('AI 已为您转入人工客服队列')
        loadConversations()
      }
    }
  } else if (msg.type === 'CONVERSATION_CHANGED') {
    if (current.value && msg.data.id === current.value.id) {
      current.value = msg.data
      if (msg.data.conversationStatus === 'CLOSED') ElMessage.info('会话已关闭')
    }
    loadConversations()
  } else if (msg.type === 'ERROR') {
    ElMessage.error(msg.data)
  }
}

let unsubscribe: (() => void) | null = null

onMounted(async () => {
  unsubscribe = csSocket.onMessage(onWsMessage)
  const token = localStorage.getItem('token')
  if (token) csSocket.connect(token)
  await loadConversations()
})

onUnmounted(() => {
  unsubscribe?.()
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
            <button v-if="current && current.conversationStatus === 'AI_PROCESSING'" class="btn btn-warning btn-sm"
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
            <div v-if="current && !isClosed" class="chat-form">
              <input v-model="input" class="form-control" maxlength="4000" placeholder="输入您的问题，Enter 发送"
                @keyup.enter="handleSend" />
              <button class="btn btn-primary" :disabled="sending" @click="handleSend">发送</button>
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
    <el-dialog v-model="createVisible" title="发起咨询" width="520px">
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
    <el-dialog v-model="evalVisible" title="客服服务评价" width="480px">
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
  grid-template-columns: 280px 1fr;
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
@media (max-width: 768px) {
  .support-layout {
    grid-template-columns: 1fr;
  }
  .chat-container {
    height: 480px;
  }
}
</style>
