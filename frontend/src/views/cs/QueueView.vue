<script setup lang="ts">
/**
 * 客服工作台 - 会话队列（FR-C06/C24/C25）。
 *
 * <p>展示等待人工的会话队列与本人已分配会话，支持领取会话（乐观锁）；
 * 客服可切换在线/忙碌/离线工作状态；首次登录或密码重置后强制改密（FR-C04）：
 * forceChangePassword=1 时先完成改密才能使用工作台。</p>
 */
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/stores/user'
import { changePassword } from '@/api/auth'
import {
  csAssigned,
  csClaim,
  csMe,
  csQueue,
  csSetWorkStatus,
  type ConversationView,
  type CsAccountView,
  type QueueItemView,
} from '@/api/cs'

const router = useRouter()
const userStore = useUserStore()

/** 当前客服账号 */
const me = ref<CsAccountView | null>(null)
/** 等待人工队列 */
const queue = ref<QueueItemView[]>([])
/** 我的已分配会话 */
const assigned = ref<ConversationView[]>([])
const loading = ref(false)
const claiming = ref(false)
const activeTab = ref('queue')

/** 工作状态选项（FR-C06） */
const workOptions = [
  { value: 'ONLINE', label: '在线' },
  { value: 'BUSY', label: '忙碌' },
  { value: 'OFFLINE', label: '离线' },
]

const workStatusLabel = computed(() => workOptions.find((o) => o.value === me.value?.workStatus)?.label ?? '-')

/** 队列排队时长格式化 */
function fmtQueueSeconds(seconds: number): string {
  if (seconds < 60) return seconds + ' 秒'
  if (seconds < 3600) return Math.floor(seconds / 60) + ' 分钟'
  return Math.floor(seconds / 3600) + ' 小时'
}

function fmtTime(t?: string): string {
  if (!t) return '-'
  return t.length >= 16 ? t.slice(0, 16) : t
}

const sourceMap: Record<string, string> = {
  HELP_CENTER: '帮助中心',
  PROFILE: '个人中心',
  ORDER: '订单详情',
}

// ==================== 数据加载 ====================

async function loadAll() {
  loading.value = true
  try {
    const [meView, queueList, assignedList] = await Promise.all([csMe(), csQueue(), csAssigned()])
    me.value = meView
    queue.value = queueList
    assigned.value = assignedList
    // 强制改密标志：弹出改密流程
    if (meView.forceChangePassword === 1) {
      forcePwdVisible.value = true
    }
  } finally {
    loading.value = false
  }
}

// ==================== 工作状态切换（FR-C06） ====================

async function handleWorkStatusChange(value: string) {
  try {
    me.value = await csSetWorkStatus(value)
    ElMessage.success('工作状态已更新')
  } catch {
    // 失败恢复原状态
    if (me.value) me.value = { ...me.value }
  }
}

// ==================== 领取会话（FR-C25） ====================

async function handleClaim(item: QueueItemView) {
  claiming.value = true
  try {
    await csClaim(item.conversationId, item.version)
    ElMessage.success('会话领取成功')
    router.push(`/cs/conversation/${item.conversationId}`)
  } finally {
    claiming.value = false
  }
}

// ==================== 强制改密（FR-C04） ====================

const forcePwdVisible = ref(false)
const changingPwd = ref(false)
const pwdForm = reactive({ oldPassword: '', newPassword: '', confirmPassword: '' })

async function handleForceChangePassword() {
  if (!pwdForm.oldPassword || !pwdForm.newPassword) {
    ElMessage.warning('请填写完整')
    return
  }
  if (pwdForm.newPassword !== pwdForm.confirmPassword) {
    ElMessage.warning('两次输入的新密码不一致')
    return
  }
  changingPwd.value = true
  try {
    const result = await changePassword(pwdForm.oldPassword, pwdForm.newPassword)
    // 改密成功：旧令牌失效，更新为新令牌
    userStore.setToken(result.token)
    userStore.user = result.user
    forcePwdVisible.value = false
    ElMessage.success('密码修改成功，现在可以正常接待了')
    await loadAll()
  } finally {
    changingPwd.value = false
  }
}

onMounted(loadAll)
</script>

<template>
  <div class="queue-page">
    <!-- 顶部：账号与工作状态 -->
    <el-card class="top-card" shadow="hover" v-loading="loading">
      <div class="top-row">
        <div class="me-info">
          <b>{{ me?.csName ?? '-' }}</b>
          <span class="me-account">@{{ me?.csAccount ?? '-' }}</span>
          <el-tag size="small" :type="me?.workStatus === 'ONLINE' ? 'success' : 'info'" effect="plain">
            {{ workStatusLabel }}
          </el-tag>
        </div>
        <div class="work-switch">
          <span class="switch-label">工作状态：</span>
          <el-radio-group :model-value="me?.workStatus" @change="handleWorkStatusChange">
            <el-radio-button v-for="o in workOptions" :key="o.value" :value="o.value">
              {{ o.label }}
            </el-radio-button>
          </el-radio-group>
        </div>
      </div>
    </el-card>

    <!-- 队列与已分配 -->
    <el-tabs v-model="activeTab" class="tabs">
      <el-tab-pane :label="`等待人工 (${queue.length})`" name="queue">
        <div v-if="queue.length" class="queue-list">
          <el-card v-for="item in queue" :key="item.conversationId" class="queue-item" shadow="hover">
            <div class="q-head">
              <span class="q-no">{{ item.conversationNo }}</span>
              <el-tag size="small" type="danger" effect="plain">排队 {{ fmtQueueSeconds(item.queueSeconds) }}</el-tag>
            </div>
            <div class="q-topic">{{ item.topic || '（无主题）' }}</div>
            <div class="q-meta">
              <el-tag size="small" type="info" effect="plain">{{ sourceMap[item.sourceType] ?? item.sourceType }}</el-tag>
              <span v-if="item.relatedOrderId">关联订单 #{{ item.relatedOrderId }}</span>
              <span v-if="item.transferReason" class="transfer-reason" :title="item.transferReason">
                转交原因：{{ item.transferReason }}
              </span>
              <span>{{ fmtTime(item.createdAt) }}</span>
            </div>
            <div class="q-actions">
              <el-button type="primary" size="small" :loading="claiming" @click="handleClaim(item)">
                领取并处理
              </el-button>
            </div>
          </el-card>
        </div>
        <el-empty v-else description="队列为空，暂时没有等待人工的会话" />
      </el-tab-pane>

      <el-tab-pane :label="`我的会话 (${assigned.length})`" name="assigned">
        <div v-if="assigned.length" class="queue-list">
          <el-card v-for="c in assigned" :key="c.id" class="queue-item" shadow="hover" @click="router.push(`/cs/conversation/${c.id}`)">
            <div class="q-head">
              <span class="q-no">{{ c.conversationNo }}</span>
              <el-tag size="small" type="primary" effect="plain">{{ c.conversationStatus }}</el-tag>
            </div>
            <div class="q-meta">
              <span>来源：{{ sourceMap[c.sourceType] ?? c.sourceType }}</span>
              <span v-if="c.relatedOrderId">关联订单 #{{ c.relatedOrderId }}</span>
              <span>创建于 {{ fmtTime(c.createdAt) }}</span>
            </div>
          </el-card>
        </div>
        <el-empty v-else description="暂无已分配会话" />
      </el-tab-pane>
    </el-tabs>

    <!-- 强制改密对话框（FR-C04） -->
    <el-dialog v-model="forcePwdVisible" title="首次登录需修改密码" width="480px" :close-on-click-modal="false"
      :close-on-press-escape="false" :show-close="false">
      <el-alert type="warning" :closable="false" show-icon
        title="当前为初始密码或已重置密码，完成修改后才能上线接待" class="pwd-alert" />
      <el-form :model="pwdForm" label-width="90px" class="pwd-form">
        <el-form-item label="当前密码" required>
          <el-input v-model="pwdForm.oldPassword" type="password" show-password placeholder="输入初始密码" />
        </el-form-item>
        <el-form-item label="新密码" required>
          <el-input v-model="pwdForm.newPassword" type="password" show-password
            placeholder="8~32 位，须同时包含字母和数字" />
        </el-form-item>
        <el-form-item label="确认新密码" required>
          <el-input v-model="pwdForm.confirmPassword" type="password" show-password placeholder="再次输入新密码" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button type="primary" :loading="changingPwd" @click="handleForceChangePassword">确认修改</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.top-card {
  margin-bottom: 16px;
}
.top-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 12px;
}
.me-info {
  display: flex;
  align-items: center;
  gap: 10px;
}
.me-account {
  color: #909399;
  font-size: 13px;
}
.switch-label {
  font-size: 13px;
  color: #606266;
  margin-right: 8px;
}
.queue-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.queue-item {
  cursor: pointer;
}
.q-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 6px;
}
.q-no {
  font-weight: 600;
}
.q-topic {
  font-size: 14px;
  color: #303133;
  margin-bottom: 6px;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.q-meta {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 12px;
  color: #909399;
  flex-wrap: wrap;
}
.transfer-reason {
  max-width: 300px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: #e6a23c;
}
.q-actions {
  margin-top: 10px;
  text-align: right;
}
.pwd-alert {
  margin-bottom: 16px;
}
.pwd-form {
  margin-top: 4px;
}
</style>
