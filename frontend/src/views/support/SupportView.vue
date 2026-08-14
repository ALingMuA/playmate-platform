<script setup lang="ts">
/**
 * 用户端在线客服（FR-C07~C15）。
 *
 * <p>我的会话列表 + 聊天窗口；新会话由 AI 客服自动应答（FR-C09），
 * 可主动申请转人工（FR-C10）；AI 回复带"AI 客服"标识（FR-C21）；
 * 会话关闭后可提交满意度评价（FR-C15）。消息经 WebSocket 实时推送，
 * 断线时 REST 补拉兜底。</p>
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

/** 我的会话列表 */
const conversations = ref<ConversationView[]>([])
/** 当前会话 */
const current = ref<ConversationView | null>(null)
/** 当前会话消息 */
const messages = ref<MessageView[]>([])
const loading = ref(false)
const sending = ref(false)
const input = ref('')
const msgListRef = ref<HTMLElement | null>(null)

/** 会话状态展示映射 */
const statusMap: Record<string, { label: string; type: string }> = {
  AI_PROCESSING: { label: 'AI 处理中', type: 'warning' },
  WAITING_HUMAN: { label: '等待人工', type: 'info' },
  HUMAN_PROCESSING: { label: '人工处理中', type: 'primary' },
  ESCALATED_ADMIN: { label: '已转交管理员', type: 'danger' },
  CLOSED: { label: '已关闭', type: 'info' },
}

const statusLabel = computed(() => statusMap[current.value?.conversationStatus ?? '']?.label ?? '-')
const statusType = computed(() => statusMap[current.value?.conversationStatus ?? '']?.type ?? 'info')

/** 会话是否已关闭（关闭后不能再发消息） */
const isClosed = computed(() => current.value?.conversationStatus === 'CLOSED')

/** 发消息者展示名 */
function senderName(m: MessageView): string {
  if (m.senderType === 'USER') return '我'
  if (m.senderType === 'AI') return 'AI 客服'
  if (m.senderType === 'CS') return '人工客服'
  if (m.senderType === 'SYSTEM') return '系统'
  return m.senderType
}

function fmtTime(t?: string): string {
  if (!t) return ''
  return t.length >= 16 ? t.slice(0, 16) : t
}

/** 按 messageId 去重追加消息 */
function mergeMessage(m: MessageView) {
  if (!messages.value.some((x) => x.messageId === m.messageId)) {
    messages.value.push(m)
    nextTick(scrollBottom)
  }
}

function scrollBottom() {
  msgListRef.value?.scrollTo({ top: msgListRef.value.scrollHeight })
}

// ==================== 数据加载 ====================

async function loadConversations() {
  try {
    conversations.value = await myConversations()
    // 当前会话状态以服务端为准
    if (current.value) {
      const fresh = conversations.value.find((c) => c.id === current.value?.id)
      if (fresh) current.value = fresh
    }
  } catch {
    // 提示已由拦截器处理
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
  // 消息由 MESSAGE_NEW 推送带回；短暂标记发送中状态
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
    await evaluateConversation(current.value.id, {
      score: evalScore.value,
      content: evalContent.value.trim() || undefined,
    })
    ElMessage.success('感谢您的评价')
    evalVisible.value = false
  } finally {
    submittingEval.value = false
  }
}

// ==================== WebSocket ====================

function onWsMessage(msg: Parameters<Parameters<typeof csSocket.onMessage>[0]>[0]) {
  if (msg.type === 'MESSAGE_NEW') {
    if (current.value && msg.data.conversationId === current.value.id) {
      mergeMessage(msg.data)
    }
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
      if (msg.data.conversationStatus === 'CLOSED') {
        ElMessage.info('会话已关闭')
      }
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
  <div class="support-page">
    <div class="page-header">
      <h1>在线客服</h1>
      <p class="sub">常见问题可先咨询 AI 客服，需要人工时随时可转接</p>
    </div>

    <div class="support-body">
      <!-- 左侧：会话列表 -->
      <el-card class="conv-list" shadow="hover">
        <template #header>
          <div class="list-head">
            <span>我的会话</span>
            <el-button type="primary" size="small" @click="createVisible = true">发起咨询</el-button>
          </div>
        </template>
        <div v-if="conversations.length" class="conv-items">
          <div
            v-for="c in conversations"
            :key="c.id"
            class="conv-item"
            :class="{ active: current?.id === c.id }"
            @click="openConversation(c)"
          >
            <div class="conv-item-top">
              <span class="conv-no">{{ c.conversationNo }}</span>
              <el-tag :type="(statusMap[c.conversationStatus]?.type as any) ?? 'info'" size="small">
                {{ statusMap[c.conversationStatus]?.label ?? c.conversationStatus }}
              </el-tag>
            </div>
            <div class="conv-item-meta">
              {{ fmtTime(c.createdAt) }}<span v-if="c.relatedOrderId"> · 关联订单 #{{ c.relatedOrderId }}</span>
            </div>
          </div>
        </div>
        <el-empty v-else description="暂无会话，点击右上角发起咨询" :image-size="80" />
      </el-card>

      <!-- 右侧：聊天窗口 -->
      <el-card class="chat-card" shadow="hover">
        <template #header>
          <div class="chat-head" v-if="current">
            <span class="chat-title">
              {{ current.conversationNo }}
              <el-tag :type="statusType" size="small" class="status-tag">{{ statusLabel }}</el-tag>
            </span>
            <el-button
              v-if="current.conversationStatus === 'AI_PROCESSING'"
              size="small"
              type="warning"
              plain
              @click="handleRequestHuman"
            >
              转人工
            </el-button>
            <el-button
              v-if="isClosed"
              size="small"
              type="success"
              plain
              @click="evalVisible = true"
            >
              评价
            </el-button>
          </div>
          <span v-else>选择一个会话开始咨询</span>
        </template>

        <!-- 消息区 -->
        <div v-loading="loading" ref="msgListRef" class="msg-list">
          <template v-if="current">
            <div
              v-for="m in messages"
              :key="m.messageId"
              class="msg-row"
              :class="m.senderType === 'USER' ? 'mine' : 'theirs'"
            >
              <div class="bubble-wrap">
                <div class="bubble">
                  <span v-if="m.senderType === 'AI'" class="ai-badge">AI 客服</span>
                  <span v-else class="sender">{{ senderName(m) }}</span>
                  <div class="bubble-text">{{ m.content }}</div>
                </div>
                <div class="msg-time">{{ fmtTime(m.createdAt) }}</div>
              </div>
            </div>
            <el-empty v-if="!loading && messages.length === 0" description="暂无消息" :image-size="60" />
          </template>
          <el-empty v-else description="选择或发起会话后开始聊天" :image-size="80" />
        </div>

        <!-- 输入区 -->
        <div class="input-bar" v-if="current && !isClosed">
          <el-input
            v-model="input"
            type="textarea"
            :rows="2"
            maxlength="4000"
            placeholder="输入您的问题，Enter 发送（Shift+Enter 换行）"
            @keydown.enter.exact.prevent="handleSend"
          />
          <el-button type="primary" :disabled="sending" @click="handleSend">发送</el-button>
        </div>
        <div v-else-if="current && isClosed" class="closed-tip">
          会话已关闭{{ current.closedAt ? '于 ' + fmtTime(current.closedAt) : '' }}。如需帮助可发起新会话。
        </div>
      </el-card>
    </div>

    <!-- 发起咨询对话框 -->
    <el-dialog v-model="createVisible" title="发起咨询" width="520px">
      <el-form label-width="80px">
        <el-form-item label="问题描述" required>
          <el-input
            v-model="createForm.firstContent"
            type="textarea"
            :rows="4"
            maxlength="4000"
            show-word-limit
            placeholder="请描述您遇到的问题，AI 客服会先为您解答"
          />
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
          <el-input v-model="evalContent" type="textarea" :rows="3" maxlength="1000" show-word-limit
            placeholder="选填" />
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
.support-page {
  max-width: 1100px;
  margin: 0 auto;
  padding: 24px 16px 48px;
}
.page-header h1 {
  margin: 0 0 8px;
}
.sub {
  color: #909399;
  margin: 0 0 20px;
}
.support-body {
  display: flex;
  gap: 16px;
  align-items: stretch;
}
.conv-list {
  width: 300px;
  flex-shrink: 0;
}
.list-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.conv-items {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.conv-item {
  padding: 10px 12px;
  border: 1px solid #e4e7ed;
  border-radius: 6px;
  cursor: pointer;
  transition: all 0.2s;
}
.conv-item:hover {
  border-color: #409eff;
}
.conv-item.active {
  border-color: #409eff;
  background: #ecf5ff;
}
.conv-item-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}
.conv-no {
  font-size: 13px;
  font-weight: 600;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.conv-item-meta {
  margin-top: 4px;
  font-size: 12px;
  color: #909399;
}
.chat-card {
  flex: 1;
  display: flex;
  flex-direction: column;
}
.chat-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.chat-title {
  font-weight: 600;
}
.status-tag {
  margin-left: 8px;
}
.msg-list {
  height: 440px;
  overflow-y: auto;
  padding: 4px 8px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.msg-row {
  display: flex;
}
.msg-row.mine {
  justify-content: flex-end;
}
.bubble-wrap {
  max-width: 72%;
  display: flex;
  flex-direction: column;
}
.msg-row.mine .bubble-wrap {
  align-items: flex-end;
}
.bubble {
  padding: 8px 12px;
  border-radius: 8px;
  background: #f4f4f5;
  border: 1px solid #ebeef5;
}
.msg-row.mine .bubble {
  background: #409eff;
  border-color: #409eff;
  color: #fff;
}
.ai-badge {
  display: inline-block;
  font-size: 11px;
  color: #e6a23c;
  background: #fdf6ec;
  border: 1px solid #f3d19e;
  border-radius: 3px;
  padding: 0 4px;
  margin-bottom: 4px;
}
.sender {
  display: block;
  font-size: 11px;
  color: #909399;
  margin-bottom: 2px;
}
.msg-row.mine .sender {
  color: #dcdfe6;
}
.bubble-text {
  font-size: 14px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-word;
}
.msg-time {
  font-size: 11px;
  color: #c0c4cc;
  margin-top: 2px;
}
.input-bar {
  display: flex;
  gap: 8px;
  padding-top: 12px;
  border-top: 1px solid #f0f2f5;
  align-items: flex-end;
}
.input-bar .el-textarea {
  flex: 1;
}
.closed-tip {
  margin-top: 12px;
  padding-top: 12px;
  border-top: 1px solid #f0f2f5;
  color: #909399;
  font-size: 13px;
  text-align: center;
}
@media (max-width: 768px) {
  .support-body {
    flex-direction: column;
  }
  .conv-list {
    width: 100%;
  }
}
</style>
