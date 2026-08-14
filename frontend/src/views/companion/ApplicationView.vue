<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
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

const games = ref<Game[]>([])
const tags = ref<TagView[]>([])
const applications = ref<Application[]>([])
const profile = ref<CompanionProfile | null>(null)
const submitting = ref(false)
const loading = ref(true)

const form = reactive({
  realName: '',
  contactMobile: '',
  introduction: '',
  capabilities: [] as GameCapability[],
  proofUrls: '',
})

async function load() {
  loading.value = true
  try {
    games.value = await listGames()
    // 是否已是陪玩师
    try {
      profile.value = await myProfile()
    } catch {
      profile.value = null
    }
    applications.value = await myApplications()
  } finally {
    loading.value = false
  }
}

onMounted(load)

function latestApplication(): Application | null {
  return applications.value.length > 0 ? applications.value[0] : null
}

async function onGameChange(cap: GameCapability) {
  cap.positionTagIds = []
  const game = games.value.find((g) => g.id === cap.gameId)
  cap.gameName = game?.gameName
  tags.value = await listTags(cap.gameId)
}

function addCapability() {
  form.capabilities.push({ gameId: 0, server: '', rank: '', positionTagIds: [] })
}

function removeCapability(index: number) {
  form.capabilities.splice(index, 1)
}

async function handleSubmit() {
  if (!form.realName || !form.contactMobile || !form.introduction) {
    ElMessage.warning('请完整填写申请信息')
    return
  }
  if (form.capabilities.length === 0) {
    ElMessage.warning('请至少填写一项游戏能力')
    return
  }
  for (const cap of form.capabilities) {
    if (!cap.gameId || !cap.server || !cap.rank) {
      ElMessage.warning('请完整填写每项游戏能力（游戏、区服、段位）')
      return
    }
  }
  submitting.value = true
  try {
    const proofUrls = form.proofUrls
      .split(/[\n,，]/)
      .map((s) => s.trim())
      .filter(Boolean)
    await submitApplication({
      realName: form.realName,
      contactMobile: form.contactMobile,
      introduction: form.introduction,
      capabilities: form.capabilities,
      proofUrls,
    })
    ElMessage.success('申请已提交，请等待管理员审核')
    form.realName = ''
    form.contactMobile = ''
    form.introduction = ''
    form.capabilities = []
    form.proofUrls = ''
    await load()
  } finally {
    submitting.value = false
  }
}

const statusMap: Record<string, { label: string; type: 'info' | 'success' | 'danger' }> = {
  PENDING: { label: '待审核', type: 'info' },
  APPROVED: { label: '已通过', type: 'success' },
  REJECTED: { label: '已驳回', type: 'danger' },
}
</script>

<template>
  <div class="application-view" v-loading="loading">
    <!-- 已通过：展示陪玩主页信息 -->
    <el-card v-if="profile" class="block">
      <template #header>我的陪玩主页</template>
      <el-descriptions :column="2" border>
        <el-descriptions-item label="展示名">{{ profile.displayName }}</el-descriptions-item>
        <el-descriptions-item label="接单状态">
          <el-tag :type="profile.serviceStatus === 'AVAILABLE' ? 'success' : 'info'">
            {{ { AVAILABLE: '可接单', BUSY: '忙碌', RESTING: '休息', SUSPENDED: '已暂停' }[profile.serviceStatus] }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="已完成订单">{{ profile.completedOrderCount }}</el-descriptions-item>
        <el-descriptions-item label="评分">
          {{ profile.ratingAvg ?? '-' }}（{{ profile.ratingCount }} 条评价）
        </el-descriptions-item>
        <el-descriptions-item label="认证能力" :span="2">
          <div v-for="(cap, i) in profile.capabilities" :key="i" class="cap-item">
            <el-tag size="small" type="primary">{{ cap.gameName }}</el-tag>
            {{ cap.server }} · {{ cap.rank }}
            <el-tag v-for="tid in cap.positionTagIds ?? []" :key="tid" size="small" class="tag-mini">
              {{ tags.find((t) => t.id === tid)?.tagName ?? tid }}
            </el-tag>
          </div>
        </el-descriptions-item>
      </el-descriptions>
    </el-card>

    <!-- 申请状态 -->
    <el-card v-if="latestApplication()" class="block">
      <template #header>申请进度</template>
      <el-timeline>
        <el-timeline-item
          v-for="app in applications"
          :key="app.id"
          :type="statusMap[app.auditStatus]?.type"
          :timestamp="app.createdAt"
        >
          <b>第 {{ applications.length - applications.indexOf(app) }} 次申请</b>：
          <el-tag :type="statusMap[app.auditStatus]?.type" size="small">
            {{ statusMap[app.auditStatus]?.label }}
          </el-tag>
          <div v-if="app.auditReason" class="audit-reason">审核意见：{{ app.auditReason }}</div>
        </el-timeline-item>
      </el-timeline>
      <el-alert
        v-if="latestApplication()?.auditStatus === 'PENDING'"
        title="您已提交申请，审核通过前无法使用陪玩师功能，请耐心等待"
        type="info"
        :closable="false"
      />
    </el-card>

    <!-- 申请表单（非陪玩师且无待审核申请时显示） -->
    <el-card v-if="!profile && latestApplication()?.auditStatus !== 'PENDING'" class="block">
      <template #header>入驻申请（FR-P01/P02）</template>
      <el-form :model="form" label-width="110px" class="apply-form">
        <el-form-item label="真实姓名" required>
          <el-input v-model="form.realName" placeholder="用于平台实名核验" maxlength="32" />
        </el-form-item>
        <el-form-item label="联系手机号" required>
          <el-input v-model="form.contactMobile" placeholder="11位手机号" maxlength="11" />
        </el-form-item>
        <el-form-item label="自我介绍" required>
          <el-input v-model="form.introduction" type="textarea" :rows="3" maxlength="1000" show-word-limit
            placeholder="介绍您的游戏经历、段位成就与陪玩风格" />
        </el-form-item>

        <el-form-item label="游戏能力" required>
          <div class="cap-list">
            <div v-for="(cap, index) in form.capabilities" :key="index" class="cap-row">
              <el-select v-model="cap.gameId" placeholder="选择游戏" style="width: 140px" @change="onGameChange(cap)">
                <el-option v-for="g in games" :key="g.id" :label="g.gameName" :value="g.id" />
              </el-select>
              <el-input v-model="cap.server" placeholder="区服，如微信区" style="width: 140px" />
              <el-input v-model="cap.rank" placeholder="段位，如最强王者" style="width: 140px" />
              <el-select v-model="cap.positionTagIds" multiple collapse-tags placeholder="擅长位置/英雄" style="width: 200px">
                <el-option v-for="t in tags" :key="t.id" :label="t.tagName" :value="t.id" />
              </el-select>
              <el-button type="danger" text @click="removeCapability(index)">删除</el-button>
            </div>
            <el-button @click="addCapability">+ 添加一项能力</el-button>
          </div>
        </el-form-item>

        <el-form-item label="证明图片">
          <el-input v-model="form.proofUrls" type="textarea" :rows="2" placeholder="能力证明图片地址，多张用换行或逗号分隔（选填）" />
        </el-form-item>

        <el-form-item>
          <el-button type="primary" :loading="submitting" @click="handleSubmit">提交申请</el-button>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<style scoped>
.application-view {
  min-height: 240px;
}
.block {
  margin-bottom: 16px;
}
.cap-item {
  margin: 4px 0;
}
.tag-mini {
  margin-left: 4px;
}
.audit-reason {
  color: #e6a23c;
  font-size: 13px;
  margin-top: 4px;
}
.cap-list {
  width: 100%;
}
.cap-row {
  display: flex;
  gap: 8px;
  align-items: center;
  margin-bottom: 8px;
}
.apply-form {
  max-width: 820px;
}
</style>
