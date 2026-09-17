<script setup lang="ts">
/**
 * 我的陪玩主页（FR-P05 主页维护 / FR-P06 接单状态）。
 *
 * <p>展示名、主页简介与认证游戏能力在此维护，保存后即时生效于用户端陪玩主页；
 * 接单状态支持"可接单 / 忙碌 / 休息中"，`SUSPENDED`（暂停接单资格）仅管理员可设置，
 * 因此前端不提供该选项。</p>
 */
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { listGames, listTags, type Game, type TagView } from '@/api/catalog'
import {
  myProfile,
  updateProfile,
  updateServiceStatus,
  type CompanionProfile,
  type GameCapability,
} from '@/api/companion'

const router = useRouter()

const games = ref<Game[]>([])
const profile = ref<CompanionProfile | null>(null)
const loading = ref(false)
const saving = ref(false)
const statusSaving = ref(false)

/** 各游戏可选的位置/英雄标签（按 gameId 缓存，避免多行能力互相覆盖选项） */
const tagOptions = reactive<Record<number, TagView[]>>({})

const form = reactive({
  displayName: '',
  profileIntro: '',
  capabilities: [] as GameCapability[],
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

async function load() {
  loading.value = true
  try {
    const data = await myProfile()
    profile.value = data
    form.displayName = data.displayName
    form.profileIntro = data.profileIntro ?? ''
    form.capabilities = (data.capabilities ?? []).map((c) => ({ ...c, positionTagIds: [...(c.positionTagIds ?? [])] }))
    // 预取已认证能力对应的标签，便于编辑时展示与勾选
    for (const cap of form.capabilities) {
      if (cap.gameId && !tagOptions[cap.gameId]) {
        tagOptions[cap.gameId] = await listTags(cap.gameId).catch(() => [])
      }
    }
  } catch {
    profile.value = null
  } finally {
    loading.value = false
  }
}

onMounted(async () => {
  games.value = await listGames().catch(() => [])
  await load()
})

async function onGameChange(cap: GameCapability) {
  cap.positionTagIds = []
  cap.gameName = games.value.find((g) => g.id === cap.gameId)?.gameName
  if (cap.gameId && !tagOptions[cap.gameId]) {
    tagOptions[cap.gameId] = await listTags(cap.gameId).catch(() => [])
  }
}

function addCapability() {
  form.capabilities.push({ gameId: 0, server: '', rank: '', positionTagIds: [] })
}

function removeCapability(index: number) {
  form.capabilities.splice(index, 1)
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
  for (const cap of form.capabilities) {
    if (!cap.gameId || !cap.server?.trim() || !cap.rank?.trim()) {
      ElMessage.warning('请完整填写每项认证能力（游戏、区服、段位）')
      return
    }
    if (cap.server.length > 50 || cap.rank.length > 50) {
      ElMessage.warning('区服与段位长度不能超过 50 个字符')
      return
    }
  }
  saving.value = true
  try {
    profile.value = await updateProfile({
      displayName: form.displayName.trim(),
      profileIntro: form.profileIntro,
      capabilities: form.capabilities,
    })
    ElMessage.success('主页资料已更新，用户端同步展示')
  } finally {
    saving.value = false
  }
}

async function handleStatus(value: string | number | boolean | undefined) {
  const status = String(value)
  if (status === profile.value?.serviceStatus) return
  statusSaving.value = true
  try {
    profile.value = await updateServiceStatus(status)
    ElMessage.success(`接单状态已更新为「${statusOptions.find((s) => s.value === status)?.label ?? status}」`)
  } finally {
    statusSaving.value = false
  }
}
</script>

<template>
  <div class="profile-view">
    <el-card v-loading="loading" class="block">
      <template #header>接单状态（FR-P06）</template>
      <template v-if="profile">
        <div class="status-row">
          <el-radio-group
            :model-value="profile.serviceStatus"
            :disabled="statusSaving"
            @change="handleStatus"
          >
            <el-radio-button v-for="s in statusOptions" :key="s.value" :value="s.value">
              {{ s.label }}
            </el-radio-button>
          </el-radio-group>
          <el-tag
            :type="profile.certificationStatus === 'APPROVED' ? 'success' : 'danger'"
            size="small"
          >
            {{ certificationText[profile.certificationStatus] ?? profile.certificationStatus }}
          </el-tag>
        </div>
        <el-alert
          v-if="profile.serviceStatus !== 'AVAILABLE'"
          class="mt-2"
          type="info"
          :closable="false"
          title="当前不是「可接单」状态，用户端不会产生新的预约"
        />
      </template>
    </el-card>

    <el-row :gutter="16" class="stat-row" v-if="profile">
      <el-col :xs="12" :sm="12" :md="8">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-label">评分</div>
          <div class="stat-value">
            {{ profile.ratingAvg ?? '-' }} <span class="stat-extra">{{ profile.ratingCount }} 条评价</span>
          </div>
        </el-card>
      </el-col>
      <el-col :xs="12" :sm="12" :md="8">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-label">已完成订单</div>
          <div class="stat-value">{{ profile.completedOrderCount }} 单</div>
        </el-card>
      </el-col>
      <el-col :xs="12" :sm="12" :md="8">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-label">最近更新</div>
          <div class="stat-value stat-time">{{ profile.updatedAt ?? '-' }}</div>
        </el-card>
      </el-col>
    </el-row>

    <el-card class="block">
      <template #header>主页资料（FR-P05）</template>

      <el-empty v-if="!profile && !loading" description="陪玩资料加载失败">
        <el-button type="primary" @click="load">重新加载</el-button>
      </el-empty>

      <el-form v-else :model="form" label-width="110px" class="profile-form">
        <el-form-item label="展示名" required>
          <el-input v-model="form.displayName" maxlength="32" show-word-limit placeholder="用户端展示的陪玩昵称" />
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

        <el-form-item label="认证能力">
          <div class="cap-list">
            <div v-for="(cap, index) in form.capabilities" :key="index" class="cap-row">
              <el-select v-model="cap.gameId" placeholder="选择游戏" style="width: 140px" @change="onGameChange(cap)">
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
                <el-option v-for="t in tagOptions[cap.gameId] ?? []" :key="t.id" :label="t.tagName" :value="t.id" />
              </el-select>
              <el-button type="danger" text @click="removeCapability(index)">删除</el-button>
            </div>
            <el-button @click="addCapability">+ 添加一项能力</el-button>
            <div v-if="form.capabilities.length === 0" class="cap-hint">
              建议至少保留一项认证能力，否则用户端主页不展示游戏标签
            </div>
          </div>
        </el-form-item>

        <el-form-item>
          <el-button type="primary" :loading="saving" @click="handleSave">保存主页资料</el-button>
          <el-button @click="router.push('/companion/services')">去管理服务项目</el-button>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<style scoped>
.block {
  margin-bottom: 16px;
}
.status-row {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}
.stat-row {
  margin-bottom: 16px;
}
.stat-card {
  text-align: center;
}
.stat-label {
  color: var(--el-text-color-secondary);
  font-size: 13px;
  margin-bottom: 6px;
}
.stat-value {
  font-size: 20px;
  font-weight: 600;
}
.stat-extra,
.stat-time {
  font-size: 13px;
  font-weight: 400;
  color: var(--el-text-color-secondary);
}
.profile-form {
  max-width: 900px;
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
.cap-hint {
  color: var(--el-text-color-secondary);
  font-size: 12px;
  margin-top: 4px;
}
</style>
