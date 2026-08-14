<script setup lang="ts">
/**
 * 客服工作台 - 会话详情（FR-C25~C29）。
 *
 * <p>人工回复（FR-C26，WebSocket MESSAGE_SEND，REST 兜底）、
 * 内部备注（FR-C27）、转交管理员（FR-C28）、关闭会话（FR-C29）；
 * 会话处于等待人工且未分配时提供领取入口（FR-C25）。
 * 消息经 WebSocket 实时推送，断线自动重连。</p>
 */
import { computed, nextTick, onMounted, onUnmounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { csSocket } from '@/utils/ws'
import {
  conversationDetail,
  conversationMessages,
  csAddNote,
  csClaim,
  csClose,
  csEscalate,
  csMe,
  csNotes,
  csReply,
  type ConversationView,
  type CsAccountView,
  type InternalNoteView,
  type MessageView,
} from '@/api/cs'

const route = useRoute()
const router = useRouter()

const conversationId = computed(() => Number(route.params.id))

/** 会话与消息 */
const conv = ref<ConversationView | null>(null)
const messages = ref<MessageView[]>([])
const notes = ref<InternalNoteView[]>([])
const me = ref<CsAccountView | null>(null)
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

const sourceMap: Record<string, string> = {
  HELP_CENTER: '帮助中心',
  PROFILE: '个人中心',
  ORDER: '订单详情',
}

const isClosed = computed(() => conv.value?.conversationStatus === 'CLOSED')
/** 是否已分配给我（当前处理人判断） */
const isMine = computed(() => me.value && conv.value?.currentCsAccountId === me.value.id)
/** 等待人工且未分配 → 显示领取按钮 */
const canClaim = computed(() => conv.value?.conversationStatus === 'WAITING_HUMAN' && !isMine.value)

function senderLabel(m: MessageView): string {
  if (m.senderType === 'USER') return '用户'
  if (m.senderType === 'AI') return 'AI 客服'
  if (m.senderType === 'CS') return '客服'
  if (m.senderType === 'SYSTEM') return '系统'
  if (m.senderType === 'ADMIN') return '管理员'
  return m.senderType
}

function fmtTime(t?: string): string {
  if (!t) return ''
  return t.length >= 16 ? t.slice(0, 16) : t
}

function scrollBottom() {
  msgListRef.value?.scrollTo({ top: msgListRef.value.scrollHeight })
}

/** 按 messageId 去重追加 */
function mergeMessage(m: MessageView) {
  if (!messages.value.some((x) => x.messageId === m.messageId)) {
    messages.value.push(m)
    nextTick(scrollBottom)
  }
}

// ==================== 加载 ====================

async function load() {
  loading.value = true
  try {
    const [detail, list, my, noteList] = await Promise.all([
      conversationDetail(conversationId.value),
      conversationMessages(conversationId.value),
      csMe(),
      csNotes(conversationId.value),
    ])
    conv.value = detail
    messages.value = list
    me.value = my
    notes.value = noteList
    nextTick(scrollBottom)
  } finally {
    loading.value = false
  }
}

// ==================== 领取（FR-C25） ====================

async function handleClaim() {
  if (!conv.value) return
  await csClaim(conv.value.id, conv.value.version)
  ElMessage.success('会话已领取')
  await load()
}

// ==================== 发送回复（FR-C26） ====================

function handleSend() {
  const content = input.value.trim()
  if (!content) return
  if (!conv.value || isClosed.value) return
  const ok = csSocket.sendMessage({
    conversationId: conv.value.id,
    content,
    clientMsgId: crypto.randomUUID(),
  })
  if (ok) {
    input.value = ''
    sending.value = true
    setTimeout(() => (sending.value = false), 500)
  } else {
    // WebSocket 不可用时 REST 兜底
    sending.value = true
    csReply(conv.value.id, content)
      .then((m) => {
        input.value = ''
        mergeMessage(m)
      })
      .finally(() => (sending.value = false))
  }
}

// ==================== 内部备注（FR-C27） ====================

const noteVisible = ref(false)
const noteInput = ref('')
const noteSubmitting = ref(false)

async function handleAddNote() {
  if (!noteInput.value.trim()) {
    ElMessage.warning('请输入备注内容')
    return
  }
  noteSubmitting.value = true
  try {
    await csAddNote(conversationId.value, noteInput.value.trim())
    noteInput.value = ''
    notes.value = await csNotes(conversationId.value)
    ElMessage.success('备注已添加')
  } finally {
    noteSubmitting.value = false
  }
}

// ==================== 转交管理员（FR-C28） ====================

const escalateVisible = ref(false)
const escalateReason = ref('')
const escalating = ref(false)

async function handleEscalate() {
  if (!escalateReason.value.trim()) {
    ElMessage.warning('请填写转交原因')
    return
  }
  escalating.value = true
  try {
    conv.value = await csEscalate(conversationId.value, escalateReason.value.trim())
    escalateVisible.value = false
    ElMessage.success('已转交管理员处理')
  } finally {
    escalating.value = false
  }
}

// ==================== 关闭会话（FR-C29） ====================

const closeVisible = ref(false)
const closeForm = reactive({ category: '已解决', result: '' })
const closing = ref(false)
const categoryOptions = ['已解决', '用户取消', '转线下处理', '未解决需跟进', '其他']

async function handleClose() {
  if (!closeForm.result.trim()) {
    ElMessage.warning('请填写处理结果')
    return
  }
  closing.value = true
  try {
    conv.value = await csClose(conversationId.value, closeForm.category, closeForm.result.trim())
    closeVisible.value = false
    ElMessage.success('会话已关闭')
  } finally {
    closing.value = false
  }
}

// ==================== WebSocket ====================

function onWsMessage(msg: Parameters<Parameters<typeof csSocket.onMessage>[0]>[0]) {
  if (msg.type === 'MESSAGE_NEW') {
    if (conv.value && msg.data.conversationId === conv.value.id) {
      mergeMessage(msg.data)
    }
  } else if (msg.type === 'CONVERSATION_CHANGED') {
    if (conv.value && msg.data.id === conv.value.id) {
      conv.value = msg.data
    }
  } else if (msg.type === 'ERROR') {
    ElMessage.error(msg.data)
  }
}

let unsubscribe: (() => void) | null = null

onMounted(() => {
  unsubscribe = csSocket.onMessage(onWsMessage)
  const token = localStorage.getItem('token')
  if (token) csSocket.connect(token)
  load()
})

onUnmounted(() => {
  unsubscribe?.()
  csSocket.close()
})

// 路由参数变化（切换会话）时重新加载
watch(conversationId, () => load())
</script>

<template>
  <div class="conv-page" v-loading="loading">
    <!-- 会话信息栏 -->
    <el-card class="info-bar" shadow="hover" v-if="conv">
      <div class="info-left">
        <el-button text @click="router.push('/cs/queue')">← 返回队列</el-button>
        <b class="conv-no">{{ conv.conversationNo }}</b>
        <el-tag :type="(statusMap[conv.conversationStatus]?.type as any) ?? 'info'" size="small">
          {{ statusMap[conv.conversationStatus]?.label ?? conv.conversationStatus }}
        </el-tag>
        <el-tag size="small" type="info" effect="plain">{{ sourceMap[conv.sourceType] ?? conv.sourceType }}</el-tag>
        <span v-if="conv.relatedOrderId" class="info-meta">关联订单 #{{ conv.relatedOrderId }}</span>
        <span class="info-meta">创建于 {{ fmtTime(conv.createdAt) }}</span>
        <span v-if="conv.transferReason" class="transfer-reason" :title="conv.transferReason">
          转交原因：{{ conv.transferReason }}
        </span>
      </div>
      <div class="info-actions">
        <el-button v-if="canClaim" type="primary" size="small" @click="handleClaim">领取会话</el-button>
        <el-button v-if="isMine && !isClosed" size="small" type="warning" plain @click="noteVisible = true">
          内部备注
        </el-button>
        <el-button v-if="isMine && !isClosed" size="small" type="danger" plain @click="escalateVisible = true">
          转交管理员
        </el-button>
        <el-button v-if="isMine && !isClosed" size="small" type="success" @click="closeVisible = true">
          关闭会话
        </el-button>
      </div>
    </el-card>

    <!-- 消息区 -->
    <el-card class="chat-card" shadow="hover" v-if="conv">
      <div ref="msgListRef" class="msg-list">
        <div
          v-for="m in messages"
          :key="m.messageId"
          class="msg-row"
          :class="m.senderType === 'CS' ? 'mine' : 'theirs'"
        >
          <div class="bubble-wrap">
            <div class="bubble">
              <span v-if="m.senderType === 'AI'" class="ai-badge">AI 客服</span>
              <span v-else class="sender">{{ senderLabel(m) }}</span>
              <div class="bubble-text">{{ m.content }}</div>
            </div>
            <div class="msg-time">{{ fmtTime(m.createdAt) }}</div>
          </div>
        </div>
        <el-empty v-if="!loading && messages.length === 0" description="暂无消息" :image-size="60" />
      </div>

      <!-- 输入区 -->
      <div class="input-bar" v-if="!isClosed">
        <el-input
          v-model="input"
          type="textarea"
          :rows="2"
          maxlength="4000"
          placeholder="输入回复内容，Enter 发送（Shift+Enter 换行）"
          @keydown.enter.exact.prevent="handleSend"
        />
        <el-button type="primary" :disabled="sending" @click="handleSend">发送</el-button>
      </div>
      <div v-else class="closed-tip">会话已关闭{{ conv.closedAt ? '于 ' + fmtTime(conv.closedAt) : '' }}</div>
    </el-card>

    <!-- 内部备注对话框 -->
    <el-dialog v-model="noteVisible" title="内部备注（仅客服/管理员可见）" width="560px">
      <div class="note-list" v-if="notes.length">
        <div v-for="n in notes" :key="n.id" class="note-item">
          <div class="note-head">
            <b>{{ n.authorRole }}</b>
            <span class="note-time">{{ fmtTime(n.createdAt) }}</span>
          </div>
          <div class="note-content">{{ n.content }}</div>
        </div>
      </div>
      <el-empty v-else description="暂无备注" :image-size="60" />
      <div class="note-input">
        <el-input v-model="noteInput" type="textarea" :rows="2" maxlength="2000" placeholder="添加内部备注" />
        <el-button type="primary" :loading="noteSubmitting" @click="handleAddNote" class="note-btn">添加</el-button>
      </div>
    </el-dialog>

    <!-- 转交管理员对话框 -->
    <el-dialog v-model="escalateVisible" title="转交管理员" width="480px">
      <el-alert type="warning" :closable="false" show-icon class="escalate-alert"
        title="退款、投诉仲裁、封禁申诉等超权限事项需转交管理员处理" />
      <el-input v-model="escalateReason" type="textarea" :rows="3" maxlength="500" show-word-limit
        placeholder="请填写转交原因" />
      <template #footer>
        <el-button @click="escalateVisible = false">取消</el-button>
        <el-button type="danger" :loading="escalating" @click="handleEscalate">确认转交</el-button>
      </template>
    </el-dialog>

    <!-- 关闭会话对话框 -->
    <el-dialog v-model="closeVisible" title="关闭会话" width="480px">
      <el-form label-width="90px">
        <el-form-item label="问题分类" required>
          <el-select v-model="closeForm.category" style="width: 100%">
            <el-option v-for="c in categoryOptions" :key="c" :label="c" :value="c" />
          </el-select>
        </el-form-item>
        <el-form-item label="处理结果" required>
          <el-input v-model="closeForm.result" type="textarea" :rows="3" maxlength="1000" show-word-limit
            placeholder="记录处理结果" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="closeVisible = false">取消</el-button>
        <el-button type="primary" :loading="closing" @click="handleClose">确认关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.conv-page {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.info-bar {
  margin-bottom: 0;
}
.info-left {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}
.conv-no {
  font-size: 15px;
}
.info-meta {
  font-size: 12px;
  color: #909399;
}
.transfer-reason {
  font-size: 12px;
  color: #e6a23c;
  max-width: 320px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.info-actions {
  margin-top: 10px;
  display: flex;
  gap: 8px;
}
.chat-card {
  flex: 1;
}
.msg-list {
  height: 480px;
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
.note-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  max-height: 240px;
  overflow-y: auto;
  margin-bottom: 12px;
}
.note-item {
  padding: 8px 10px;
  background: #f7f8fa;
  border-radius: 6px;
}
.note-head {
  display: flex;
  justify-content: space-between;
  font-size: 12px;
  color: #606266;
  margin-bottom: 4px;
}
.note-time {
  color: #909399;
}
.note-content {
  font-size: 13px;
  white-space: pre-wrap;
}
.note-input {
  display: flex;
  gap: 8px;
  align-items: flex-end;
}
.note-input .el-textarea {
  flex: 1;
}
.escalate-alert {
  margin-bottom: 12px;
}
</style>
