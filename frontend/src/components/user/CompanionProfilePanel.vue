<script setup lang="ts">
/**
 * 个人主页的「陪玩服务」面板（仅陪玩师角色可见）。
 *
 * <p>经营速览横幅（接单状态开关 + 评分/完成单/在架服务/更新时间）与陪玩主页资料
 * （展示名、简介）直接在个人主页维护，无需跳转陪玩师工作台。游戏能力认证为平台
 * 审核制：能力卡只读，变更须提交能力变更申请，审核通过后覆盖主页能力。</p>
 */
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { listGames, listTags, type Game, type TagView } from '@/api/catalog'
import {
  myApplications,
  myProfile,
  submitCapabilityChange,
  updateProfile,
  updateServiceStatus,
  type Application,
  type CompanionProfile,
  type GameCapability,
} from '@/api/companion'
import { listBookableServices } from '@/api/order'
import { uploadFile } from '@/api/file'

const router = useRouter()
/** 资料或状态变更后通知父页面同步概览入口卡 */
const emit = defineEmits<{ updated: [profile: CompanionProfile] }>()

const games = ref<Game[]>([])
const profile = ref<CompanionProfile | null>(null)
const loading = ref(true)
const failed = ref(false)
const saving = ref(false)
const statusSaving = ref(false)
/** 在架可预约服务数（公开口径） */
const onShelfServiceCount = ref<number | null>(null)
/** 待审核的能力变更申请（无则为 null） */
const pendingChange = ref<Application | null>(null)

/** 各游戏可选的位置/英雄标签（按 gameId 缓存，用于名称映射与变更弹窗选项） */
const tagOptions = reactive<Record<number, TagView[]>>({})
const form = reactive({
  displayName: '',
  profileIntro: '',
})

const statusOptions = [
  { value: 'AVAILABLE', label: '可接单' },
  { value: 'BUSY', label: '忙碌' },
  { value: 'RESTING', label: '休息中' },
]

const certificationText: Record<string, string> = {
  APPROVED: '认证通过',
  SUSPENDED: '资格已暂停',
}

const updatedAtText = computed(() => {
  const value = profile.value?.updatedAt
  return value ? value.slice(0, 16) : '—'
})

/** 位置标签 ID → 名称（未加载到时显示占位） */
function positionTagName(gameId: number, tagId: number): string {
  return tagOptions[gameId]?.find((tag) => tag.id === tagId)?.tagName ?? '…'
}

async function load() {
  loading.value = true
  failed.value = false
  try {
    const data = await myProfile()
    profile.value = data
    form.displayName = data.displayName
    form.profileIntro = data.profileIntro ?? ''
    // 预取已认证能力对应游戏的标签，用于只读卡的位置标签名展示
    const gameIds = [...new Set((data.capabilities ?? []).map((c) => c.gameId))]
    const tagRequests = gameIds.map(async (gameId) => {
      if (!tagOptions[gameId]) tagOptions[gameId] = await listTags(gameId).catch(() => [])
    })
    await Promise.all([
      ...tagRequests,
      // 在架服务数走公开可预约口径
      listBookableServices(undefined, data.userId, 1, 1)
        .then((page) => { onShelfServiceCount.value = page.total })
        .catch(() => { onShelfServiceCount.value = null }),
      // 待审核的能力变更申请（用于展示"待审核"卡）
      myApplications()
        .then((list) => {
          pendingChange.value = list.find(
            (item) => item.applicationType === 'CAPABILITY_UPDATE' && item.auditStatus === 'PENDING',
          ) ?? null
        })
        .catch(() => { pendingChange.value = null }),
    ])
  } catch {
    profile.value = null
    failed.value = true
  } finally {
    loading.value = false
  }
}

onMounted(async () => {
  games.value = await listGames().catch(() => [])
  await load()
})

async function handleStatus(value: string | number | boolean | undefined) {
  const status = String(value)
  if (status === profile.value?.serviceStatus) return
  statusSaving.value = true
  try {
    profile.value = await updateServiceStatus(status)
    emit('updated', profile.value)
    ElMessage.success('接单状态已更新')
  } finally {
    statusSaving.value = false
  }
}

async function handleSave() {
  if (!form.displayName.trim()) {
    ElMessage.warning('请填写展示名')
    return
  }
  if (form.displayName.length > 32) {
    ElMessage.warning('展示名长度不能超过 32 个字符')
    return
  }
  saving.value = true
  try {
    profile.value = await updateProfile({
      displayName: form.displayName.trim(),
      profileIntro: form.profileIntro,
    })
    emit('updated', profile.value)
    ElMessage.success('陪玩主页资料已更新，公开主页同步展示')
  } finally {
    saving.value = false
  }
}

// ==================== 能力变更申请（平台审核制） ====================

const changeVisible = ref(false)
const changeSaving = ref(false)
const changeForm = reactive({
  capabilities: [] as GameCapability[],
  proofUrls: [] as string[],
})

/** 打开弹窗：以当前已认证能力为底稿，陪玩师在其上增删改后整单提交 */
function openChangeDialog() {
  changeForm.capabilities = (profile.value?.capabilities ?? []).map((c) => ({
    ...c,
    positionTagIds: [...(c.positionTagIds ?? [])],
  }))
  changeForm.proofUrls = []
  changeVisible.value = true
}

async function onGameChange(cap: GameCapability) {
  cap.positionTagIds = []
  cap.gameName = games.value.find((g) => g.id === cap.gameId)?.gameName
  if (cap.gameId && !tagOptions[cap.gameId]) {
    tagOptions[cap.gameId] = await listTags(cap.gameId).catch(() => [])
  }
}

function addCapability() {
  changeForm.capabilities.push({ gameId: 0, server: '', rank: '', positionTagIds: [] })
}

function removeCapability(index: number) {
  changeForm.capabilities.splice(index, 1)
}

async function handleProofUpload(file: File) {
  try {
    const result = await uploadFile(file, 'proof')
    changeForm.proofUrls.push(result.url)
  } catch {
    // 拦截器已提示
  }
  return false
}

async function handleSubmitChange() {
  if (changeForm.capabilities.length === 0) {
    ElMessage.warning('至少保留一项游戏能力')
    return
  }
  for (const cap of changeForm.capabilities) {
    if (!cap.gameId || !cap.server?.trim() || !cap.rank?.trim()) {
      ElMessage.warning('请完整填写每项能力（游戏、区服、段位）')
      return
    }
    if (cap.server.length > 50 || cap.rank.length > 50) {
      ElMessage.warning('区服与段位长度不能超过 50 个字符')
      return
    }
  }
  changeSaving.value = true
  try {
    pendingChange.value = await submitCapabilityChange({
      capabilities: changeForm.capabilities,
      proofUrls: changeForm.proofUrls,
    })
    changeVisible.value = false
    ElMessage.success('能力变更申请已提交，等待平台审核')
  } finally {
    changeSaving.value = false
  }
}
</script>

<template>
  <section class="companion-panel" aria-label="陪玩服务管理">
    <div v-if="loading" v-loading="true" class="panel-loading" aria-live="polite">加载陪玩资料…</div>

    <div v-else-if="failed" class="card">
      <div class="card-body panel-failed">
        <p class="text-muted">陪玩资料暂时无法加载。</p>
        <button class="btn btn-outline btn-sm" @click="load">重新加载</button>
      </div>
    </div>

    <template v-else-if="profile">
      <!-- 经营速览横幅：接单状态开关 + 只读指标 -->
      <section class="card mb-2">
        <div class="card-body banner">
          <div class="banner-status">
            <span class="banner-label">接单状态</span>
            <el-radio-group
              :model-value="profile.serviceStatus"
              :disabled="statusSaving || profile.serviceStatus === 'SUSPENDED'"
              @change="handleStatus"
            >
              <el-radio-button v-for="s in statusOptions" :key="s.value" :value="s.value">
                {{ s.label }}
              </el-radio-button>
            </el-radio-group>
            <span v-if="profile.serviceStatus === 'SUSPENDED'" class="badge badge-warning">
              接单资格已被平台暂停，解除后可再开启
            </span>
          </div>
          <div class="banner-divider" aria-hidden="true" />
          <div class="banner-kpis">
            <div class="kpi">
              <span>评分</span><b>{{ profile.ratingAvg ?? '—' }}</b><small>{{ profile.ratingCount }} 条评价</small>
            </div>
            <div class="kpi"><span>完成订单</span><b>{{ profile.completedOrderCount }} 单</b></div>
            <div class="kpi"><span>在架服务</span><b>{{ onShelfServiceCount ?? '—' }} 项</b></div>
            <div class="kpi"><span>主页更新</span><b class="kpi-time">{{ updatedAtText }}</b></div>
          </div>
        </div>
      </section>

      <!-- 主页资料：基本资料 + 游戏能力认证 -->
      <section class="card">
        <div class="card-body">
          <h2 class="card-title">陪玩主页资料</h2>
          <p class="text-muted mt-1">展示名与简介保存后实时展示在你的公开主页，与访客看到的一致。</p>

          <div class="section-block">
            <div class="section-title"><h3>基本资料</h3><span class="section-hint">展示名与简介</span></div>
            <el-form label-width="80px" class="mt-1">
              <el-form-item label="展示名" required>
                <div class="name-row">
                  <el-input v-model="form.displayName" maxlength="32" show-word-limit placeholder="用户端展示的陪玩昵称" />
                </div>
              </el-form-item>
              <el-form-item label="主页简介">
                <el-input
                  v-model="form.profileIntro"
                  type="textarea"
                  :rows="4"
                  maxlength="1000"
                  show-word-limit
                  placeholder="介绍您的游戏经历、擅长位置与陪玩风格"
                />
              </el-form-item>
            </el-form>
          </div>

          <div class="section-block">
            <div class="section-title">
              <h3>游戏能力认证</h3>
              <span class="section-hint">由平台审核后生效，不可自行修改</span>
              <span class="badge" :class="profile.certificationStatus === 'APPROVED' ? 'badge-success' : 'badge-destructive'">
                {{ certificationText[profile.certificationStatus] ?? profile.certificationStatus }}
              </span>
            </div>
            <div v-if="profile.capabilities?.length || pendingChange" class="capability-grid">
              <div v-for="(cap, index) in profile.capabilities" :key="`${cap.gameId}-${index}`" class="capability-card">
                <div class="capability-icon" aria-hidden="true">{{ cap.gameName?.trim().charAt(0) || '游' }}</div>
                <div class="capability-copy">
                  <h4> {{ cap.gameName || `游戏 #${cap.gameId}` }} <span class="badge badge-success">已认证</span></h4>
                  <p>{{ cap.server }} · {{ cap.rank }}</p>
                  <div v-if="cap.positionTagIds?.length" class="capability-tags">
                    <span v-for="tagId in cap.positionTagIds" :key="tagId">#{{ positionTagName(cap.gameId, tagId) }}</span>
                  </div>
                </div>
              </div>
              <!-- 待审核变更 -->
              <div v-if="pendingChange" class="capability-card capability-pending">
                <div class="capability-icon capability-icon-pending" aria-hidden="true">审</div>
                <div class="capability-copy">
                  <h4>能力变更 <span class="badge badge-warning">待审核</span></h4>
                  <p>{{ pendingChange.createdAt?.slice(0, 16) }} 提交 · {{ pendingChange.capabilities?.length ?? 0 }} 项能力 · 证明材料 {{ pendingChange.proofUrls?.length ?? 0 }} 份</p>
                  <div class="capability-tags">
                    <span v-for="cap in pendingChange.capabilities" :key="cap.gameId">#{{ cap.gameName }}</span>
                  </div>
                </div>
              </div>
            </div>
            <div v-else class="capability-empty text-muted">暂无已认证的游戏能力。</div>
            <div class="flex gap-1 mt-1 flex-wrap">
              <button class="btn btn-outline btn-sm" :disabled="!!pendingChange" @click="openChangeDialog">
                {{ pendingChange ? '变更申请审核中…' : '申请变更能力' }}
              </button>
            </div>
            <p class="text-muted capability-note">
              提交变更申请后进入平台审核；审核通过前，公开主页仍展示当前已认证能力。
            </p>
          </div>

          <div class="form-footer">
            <button class="btn btn-primary" :disabled="saving" @click="handleSave">
              {{ saving ? '保存中…' : '保存陪玩资料' }}
            </button>
            <button class="btn btn-outline" @click="router.push(`/companions/${profile.userId}`)">预览公开主页</button>
          </div>
        </div>
      </section>
    </template>

    <!-- 能力变更申请弹窗 -->
    <el-dialog v-model="changeVisible" title="申请变更游戏能力" width="640px">
      <p class="text-muted dialog-hint">
        以当前认证能力为底稿修改，提交后整体送审；平台审核通过前公开展示不变。建议附上段位 / 战绩截图作为证明材料。
      </p>
      <div class="cap-editor">
        <div v-for="(cap, index) in changeForm.capabilities" :key="index" class="cap-row">
          <el-select v-model="cap.gameId" placeholder="选择游戏" class="cap-game" @change="onGameChange(cap)">
            <el-option v-for="g in games" :key="g.id" :label="g.gameName" :value="g.id" />
          </el-select>
          <el-input v-model="cap.server" placeholder="区服，如微信区" maxlength="50" class="cap-input" />
          <el-input v-model="cap.rank" placeholder="段位，如最强王者" maxlength="50" class="cap-input" />
          <el-select v-model="cap.positionTagIds" multiple collapse-tags placeholder="擅长位置" class="cap-tags">
            <el-option v-for="t in tagOptions[cap.gameId] ?? []" :key="t.id" :label="t.tagName" :value="t.id" />
          </el-select>
          <button class="btn btn-ghost btn-sm cap-remove" @click="removeCapability(index)">删除</button>
        </div>
        <button class="btn btn-outline btn-sm" @click="addCapability">+ 添加一项能力</button>
      </div>
      <div class="proof-block">
        <span class="proof-label">证明材料</span>
        <el-upload
          :show-file-list="false"
          accept="image/*"
          multiple
          :before-upload="handleProofUpload"
        >
          <el-button size="small">上传图片</el-button>
        </el-upload>
        <div v-if="changeForm.proofUrls.length" class="proof-list">
          <el-tag v-for="(url, index) in changeForm.proofUrls" :key="url" closable @close="changeForm.proofUrls.splice(index, 1)">
            材料 {{ index + 1 }}
          </el-tag>
        </div>
      </div>
      <template #footer>
        <el-button @click="changeVisible = false">取消</el-button>
        <el-button type="primary" :loading="changeSaving" @click="handleSubmitChange">提交审核</el-button>
      </template>
    </el-dialog>
  </section>
</template>

<style scoped>
.companion-panel { display: grid; gap: 0; }
.panel-loading { min-height: 200px; display: grid; place-items: center; color: var(--muted-foreground); }
.panel-failed { display: grid; gap: 10px; justify-items: start; }
.panel-failed p { margin: 0; }

.banner { display: flex; align-items: center; gap: 22px; flex-wrap: wrap; }
.banner-status { display: grid; gap: 7px; }
.banner-label { font-size: 12px; color: var(--muted-foreground); }
.banner-divider { align-self: stretch; width: 1px; background: var(--border); }
.banner-kpis { display: flex; gap: 30px; flex-wrap: wrap; flex: 1; }
.kpi { display: grid; gap: 2px; min-width: 84px; }
.kpi span { font-size: 12px; color: var(--muted-foreground); }
.kpi b { font-size: 17px; }
.kpi small { font-size: 11px; color: var(--muted-foreground); font-weight: 400; }
.kpi-time { font-size: 13px !important; font-weight: 500 !important; line-height: 26px; }

.section-block { margin-top: 18px; }
.section-block:first-of-type { margin-top: 14px; }
.section-title { display: flex; align-items: center; gap: 8px; margin-bottom: 6px; }
.section-title h3 { margin: 0; font-size: 14px; }
.section-hint { font-size: 12px; color: var(--muted-foreground); }
.name-row { max-width: 420px; }

.capability-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(200px, 1fr)); gap: 10px; }
.capability-card { display: flex; gap: 10px; padding: 12px 13px; border: 1px solid var(--border); border-radius: 9px; background: var(--card); align-items: flex-start; }
.capability-pending { border-style: dashed; background: var(--muted); }
.capability-icon { width: 36px; height: 36px; border-radius: 10px; background: var(--secondary); color: var(--secondary-foreground); display: grid; place-items: center; font-weight: 700; flex: 0 0 auto; }
.capability-icon-pending { background: #fdf0e3; color: #b45309; }
.capability-copy { min-width: 0; display: grid; gap: 3px; }
.capability-copy h4 { margin: 0; font-size: 13px; display: flex; align-items: center; gap: 5px; flex-wrap: wrap; }
.capability-copy p { margin: 0; font-size: 12px; color: var(--muted-foreground); }
.capability-tags { display: flex; gap: 4px; flex-wrap: wrap; }
.capability-tags span { font-size: 11px; padding: 0 7px; border-radius: 999px; background: var(--muted); color: #5c6c84; }
.capability-pending .capability-tags span { background: var(--card); }
.capability-empty { padding: 16px 0; font-size: 13px; }
.capability-note { font-size: 12px; margin: 8px 0 0; }

.form-footer { display: flex; gap: 10px; flex-wrap: wrap; margin-top: 20px; padding-top: 16px; border-top: 1px solid var(--border); }

.dialog-hint { margin: 0 0 12px; font-size: 13px; }
.cap-editor { display: grid; gap: 8px; }
.cap-row { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
.cap-game { width: 140px; }
.cap-input { width: 140px; }
.cap-tags { width: 190px; }
.cap-remove { color: var(--destructive); }
.proof-block { margin-top: 14px; display: grid; gap: 8px; }
.proof-label { font-size: 13px; color: var(--muted-foreground); }
.proof-list { display: flex; gap: 6px; flex-wrap: wrap; }

@media (max-width: 768px) {
  .banner { gap: 14px; }
  .banner-divider { display: none; }
  .banner-kpis { gap: 18px; }
  .cap-game, .cap-input, .cap-tags { width: 100%; }
}
</style>
