<script setup lang="ts">
/**
 * 成为陪玩师（用户端）：入驻申请 FR-P01/P02、进度查询 FR-P03、驳回重提 FR-P04。
 *
 * <p>页面形态按判定顺序取第一个命中（五态互斥）：
 * ① 已是陪玩师 → 陪玩主页摘要 + 进入工作台；
 * ② 申请审核中 → 进度时间线 + 重新检测，不显示表单；
 * ③ 已通过但资料未取到（两次探测横跨审核事务提交时点，或 profile 探测失败）→ 按已通过处理并引导进工作台，
 *    禁止再次提交（否则只能靠后端 409 COMPANION_ALREADY_APPROVED 兜底）；
 * ④ 已驳回 → 驳回原因 + 表单（可再次提交，新记录，历史只增不改）；
 * ⑤ 从未申请 → 表单。</p>
 *
 * <p>本路由不设角色要求，普通用户登录后即可访问；"是否已是陪玩师"用静默请求探测，
 * 避免非陪玩师一进页面就弹出 403 的错误提示。</p>
 */
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { listGames, listTags, type Game, type TagView } from '@/api/catalog'
import {
  myApplications,
  myProfile,
  submitApplication,
  type Application,
  type CompanionProfile,
  type GameCapability,
} from '@/api/companion'
import { useUserStore } from '@/stores/user'

type PageState = 'COMPANION' | 'PENDING' | 'APPROVED_NO_PROFILE' | 'REJECTED' | 'NEW'

const router = useRouter()
const userStore = useUserStore()

const loading = ref(false)
const submitting = ref(false)
const games = ref<Game[]>([])
const applications = ref<Application[]>([])
const profile = ref<CompanionProfile | null>(null)

const form = reactive({
  realName: '',
  contactMobile: '',
  introduction: '',
  capabilities: [] as GameCapability[],
  proofUrls: '',
})

/** 各游戏可选的位置/英雄标签（按 gameId 缓存，避免多行能力互相覆盖选项） */
const tagOptions = reactive<Record<number, TagView[]>>({})

const latestApplication = computed<Application | null>(() => applications.value[0] ?? null)

/** 五态判定：顺序即优先级 */
const pageState = computed<PageState>(() => {
  if (profile.value) return 'COMPANION'
  const latest = latestApplication.value
  if (!latest) return 'NEW'
  if (latest.auditStatus === 'PENDING') return 'PENDING'
  if (latest.auditStatus === 'APPROVED') return 'APPROVED_NO_PROFILE'
  return 'REJECTED'
})

const statusMap: Record<string, { label: string; cls: string }> = {
  PENDING: { label: '待审核', cls: 'badge-warning' },
  APPROVED: { label: '已通过', cls: 'badge-success' },
  REJECTED: { label: '已驳回', cls: 'badge-destructive' },
}

/** 能力展示名：优先用申请快照里的 gameName，缺失时按 gameId 回查游戏列表 */
function capabilityText(app: Application): string {
  const names = (app.capabilities ?? []).map(
    (c) => c.gameName || games.value.find((g) => g.id === c.gameId)?.gameName || `游戏#${c.gameId}`,
  )
  return names.length ? names.join('、') : '-'
}

const serviceStatusMap: Record<string, string> = {
  AVAILABLE: '可接单',
  BUSY: '忙碌',
  RESTING: '休息中',
  SUSPENDED: '已暂停',
}

async function load() {
  loading.value = true
  try {
    // 两次探测并行：申请列表（判定②④⑤）与陪玩主页（判定①）
    const [apps, prof] = await Promise.all([
      myApplications(),
      // 静默探测：非陪玩师返回 403 COMPANION_NOT_APPROVED，属正常状态而非错误
      myProfile(true).catch(() => null),
    ])
    applications.value = apps
    profile.value = prof

    // 已是陪玩师（含③的"已通过但资料未取到"）时刷新一次角色：
    // 本路由不要求角色，守卫不会自动刷新，不补这一步则顶部导航看不到"陪玩师工作台"入口
    if (prof || apps[0]?.auditStatus === 'APPROVED') {
      await userStore.refreshUser().catch(() => null)
    }
  } finally {
    loading.value = false
  }
}

onMounted(async () => {
  try {
    games.value = await listGames()
  } catch {
    games.value = []
  }
  await load()
})

// ==================== 表单操作 ====================

async function onGameChange(cap: GameCapability) {
  cap.positionTagIds = []
  cap.gameName = games.value.find((g) => g.id === cap.gameId)?.gameName
  if (cap.gameId && !tagOptions[cap.gameId]) {
    try {
      tagOptions[cap.gameId] = await listTags(cap.gameId)
    } catch {
      tagOptions[cap.gameId] = []
    }
  }
}

function addCapability() {
  form.capabilities.push({ gameId: 0, server: '', rank: '', positionTagIds: [] })
}

function removeCapability(index: number) {
  form.capabilities.splice(index, 1)
}

function resetForm() {
  form.realName = ''
  form.contactMobile = ''
  form.introduction = ''
  form.capabilities = []
  form.proofUrls = ''
}

function validate(): boolean {
  if (!form.realName.trim()) {
    ElMessage.warning('请填写真实姓名')
    return false
  }
  if (!/^1\d{10}$/.test(form.contactMobile.trim())) {
    ElMessage.warning('请填写正确的 11 位手机号')
    return false
  }
  if (!form.introduction.trim()) {
    ElMessage.warning('请填写自我介绍')
    return false
  }
  if (form.capabilities.length === 0) {
    ElMessage.warning('请至少填写一项游戏能力')
    return false
  }
  for (const cap of form.capabilities) {
    if (!cap.gameId || !cap.server.trim() || !cap.rank.trim()) {
      ElMessage.warning('请完整填写每项游戏能力（游戏、区服、段位）')
      return false
    }
    if (cap.server.length > 50 || cap.rank.length > 50) {
      ElMessage.warning('区服与段位长度不能超过 50 个字符')
      return false
    }
  }
  return true
}

async function handleSubmit() {
  if (!validate()) return
  submitting.value = true
  try {
    await submitApplication({
      realName: form.realName.trim(),
      contactMobile: form.contactMobile.trim(),
      introduction: form.introduction.trim(),
      capabilities: form.capabilities,
      proofUrls: form.proofUrls
        .split(/[\n,，]/)
        .map((s) => s.trim())
        .filter(Boolean),
    })
    ElMessage.success('申请已提交，请等待管理员审核')
    resetForm()
    await load()
  } finally {
    submitting.value = false
  }
}

function goWorkbench() {
  router.push('/companion')
}
</script>

<template>
  <div class="page">
    <div class="container" v-loading="loading">
      <div class="page-header">
        <h1 class="page-title">成为陪玩师</h1>
        <p class="page-subtitle">提交入驻申请，通过审核后即可上架陪玩服务并开始接单</p>
      </div>

      <!-- ① 已是陪玩师 -->
      <div v-if="pageState === 'COMPANION' && profile" class="card mb-2">
        <div class="card-body">
          <div class="flex items-center justify-between flex-wrap gap-1">
            <h3 class="card-title">您已是陪玩师</h3>
            <span class="badge badge-success">认证通过</span>
          </div>
          <div class="state-grid mt-2">
            <div>
              <div class="text-muted" style="font-size: 0.8125rem">展示名</div>
              <div class="state-value">{{ profile.displayName }}</div>
            </div>
            <div>
              <div class="text-muted" style="font-size: 0.8125rem">接单状态</div>
              <div class="state-value">{{ serviceStatusMap[profile.serviceStatus] ?? profile.serviceStatus }}</div>
            </div>
            <div>
              <div class="text-muted" style="font-size: 0.8125rem">评分</div>
              <div class="state-value">
                {{ profile.ratingAvg ?? '-' }}
                <span class="text-muted" style="font-size: 0.8125rem">（{{ profile.ratingCount }} 条评价）</span>
              </div>
            </div>
            <div>
              <div class="text-muted" style="font-size: 0.8125rem">已完成订单</div>
              <div class="state-value">{{ profile.completedOrderCount }}</div>
            </div>
          </div>
          <div class="mt-2 flex gap-1 flex-wrap">
            <button class="btn btn-primary btn-sm" @click="goWorkbench">进入陪玩师工作台</button>
            <button class="btn btn-outline btn-sm" @click="router.push('/companion/profile')">
              维护我的陪玩主页
            </button>
          </div>
        </div>
      </div>

      <!-- ③ 已通过但主页资料未取到（竞态窗口 / 探测失败） -->
      <div v-else-if="pageState === 'APPROVED_NO_PROFILE'" class="card mb-2">
        <div class="card-body">
          <div class="flex items-center justify-between flex-wrap gap-1">
            <h3 class="card-title">入驻审核已通过</h3>
            <span class="badge badge-success">已通过</span>
          </div>
          <p class="text-muted mt-2" style="font-size: 0.875rem">
            您的陪玩师资格已开通。若刚刚通过审核，陪玩主页资料可能还在生成中，稍后重新检测即可进入工作台。
          </p>
          <div class="mt-2 flex gap-1 flex-wrap">
            <button class="btn btn-primary btn-sm" @click="goWorkbench">进入陪玩师工作台</button>
            <button class="btn btn-outline btn-sm" @click="load">重新检测</button>
          </div>
        </div>
      </div>

      <!-- ② 申请审核中 -->
      <div v-else-if="pageState === 'PENDING'" class="card mb-2">
        <div class="card-body">
          <div class="flex items-center justify-between flex-wrap gap-1">
            <h3 class="card-title">申请审核中</h3>
            <span class="badge badge-warning">待审核</span>
          </div>
          <p class="text-muted mt-2" style="font-size: 0.875rem">
            您已提交入驻申请，审核通过前无法使用陪玩师功能，请耐心等待。管理员已通过时可点"重新检测"立即刷新。
          </p>
          <div class="mt-2">
            <button class="btn btn-outline btn-sm" @click="load">重新检测</button>
          </div>
        </div>
      </div>

      <!-- ④ 已驳回 -->
      <div v-else-if="pageState === 'REJECTED'" class="card mb-2">
        <div class="card-body">
          <div class="flex items-center justify-between flex-wrap gap-1">
            <h3 class="card-title">申请未通过</h3>
            <span class="badge badge-destructive">已驳回</span>
          </div>
          <p class="mt-2" style="font-size: 0.875rem; color: var(--destructive)">
            驳回原因：{{ latestApplication?.auditReason || '未填写原因' }}
          </p>
          <p class="text-muted" style="font-size: 0.875rem">
            您可以修改资料后重新提交，历史审核记录会保留。
          </p>
        </div>
      </div>

      <!-- 历史申请记录 -->
      <div v-if="applications.length" class="card mb-2">
        <div class="card-body">
          <h3 class="card-title mb-2">申请记录</h3>
          <div class="timeline">
            <div
              v-for="(app, index) in applications"
              :key="app.id"
              class="timeline-item"
              :class="{ active: index === 0 }"
            >
              <div class="timeline-time">{{ app.createdAt }}</div>
              <div class="timeline-title">
                第 {{ applications.length - index }} 次申请
                <span class="badge" :class="statusMap[app.auditStatus]?.cls">
                  {{ statusMap[app.auditStatus]?.label }}
                </span>
              </div>
              <div v-if="app.auditReason" class="text-muted" style="font-size: 0.8125rem">
                审核意见：{{ app.auditReason }}
              </div>
              <div class="text-muted" style="font-size: 0.8125rem">
                游戏能力：{{ capabilityText(app) }}
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- ④/⑤ 申请表单（已通过/审核中不显示） -->
      <div v-if="pageState === 'NEW' || pageState === 'REJECTED'" class="card">
        <div class="card-body">
          <h3 class="card-title mb-2">入驻申请（FR-P01/P02）</h3>
          <el-form :model="form" label-width="110px" class="apply-form">
            <el-form-item label="真实姓名" required>
              <el-input v-model="form.realName" placeholder="用于平台实名核验" maxlength="32" />
            </el-form-item>
            <el-form-item label="联系手机号" required>
              <el-input v-model="form.contactMobile" placeholder="11位手机号" maxlength="11" />
            </el-form-item>
            <el-form-item label="自我介绍" required>
              <el-input
                v-model="form.introduction"
                type="textarea"
                :rows="3"
                maxlength="1000"
                show-word-limit
                placeholder="介绍您的游戏经历、段位成就与陪玩风格"
              />
            </el-form-item>

            <el-form-item label="游戏能力" required>
              <div class="cap-list">
                <div v-for="(cap, index) in form.capabilities" :key="index" class="cap-row">
                  <el-select
                    v-model="cap.gameId"
                    placeholder="选择游戏"
                    style="width: 140px"
                    @change="onGameChange(cap)"
                  >
                    <el-option v-for="g in games" :key="g.id" :label="g.gameName" :value="g.id" />
                  </el-select>
                  <el-input v-model="cap.server" placeholder="区服，如微信区" style="width: 140px" maxlength="50" />
                  <el-input v-model="cap.rank" placeholder="段位，如最强王者" style="width: 140px" maxlength="50" />
                  <el-select
                    v-model="cap.positionTagIds"
                    multiple
                    collapse-tags
                    placeholder="擅长位置/英雄"
                    style="width: 200px"
                  >
                    <el-option
                      v-for="t in tagOptions[cap.gameId] ?? []"
                      :key="t.id"
                      :label="t.tagName"
                      :value="t.id"
                    />
                  </el-select>
                  <el-button type="danger" text @click="removeCapability(index)">删除</el-button>
                </div>
                <el-button @click="addCapability">+ 添加一项能力</el-button>
              </div>
            </el-form-item>

            <el-form-item label="证明图片">
              <el-input
                v-model="form.proofUrls"
                type="textarea"
                :rows="2"
                placeholder="能力证明图片地址，多张用换行或逗号分隔（选填）"
              />
            </el-form-item>

            <el-form-item>
              <el-button type="primary" :loading="submitting" @click="handleSubmit">提交申请</el-button>
            </el-form-item>
          </el-form>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.apply-form {
  max-width: 820px;
}
.cap-list {
  width: 100%;
}
.cap-row {
  display: flex;
  gap: 8px;
  align-items: center;
  margin-bottom: 8px;
  flex-wrap: wrap;
}
.state-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(160px, 1fr));
  gap: 12px;
}
.state-value {
  font-weight: 600;
  font-size: 1rem;
}
.timeline-title .badge {
  margin-left: 6px;
  vertical-align: middle;
}
</style>
