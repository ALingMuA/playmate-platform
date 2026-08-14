<script setup lang="ts">
/**
 * 管理后台 - 数据概览（FR-M02）。
 *
 * <p>展示用户数、陪玩师数、服务数、订单数、模拟交易额等核心指标，
 * 以及待审核申请、待处理投诉、等待人工会话等待办事项，可点击跳转处理。</p>
 */
import { onMounted, ref } from 'vue'
import { Collection, Document, Money, Trophy, UserFilled, User } from '@element-plus/icons-vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { adminStatsOverview, type StatsOverview } from '@/api/admin'
import { useUserStore } from '@/stores/user'

const router = useRouter()
const userStore = useUserStore()
const stats = ref<StatsOverview | null>(null)
const loading = ref(true)

function fmtMoney(cents: number): string {
  return '¥' + (cents / 100).toFixed(2)
}

/** 核心指标卡片 */
const metricCards = [
  { key: 'userCount', label: '用户总数', icon: UserFilled },
  { key: 'companionCount', label: '陪玩师数', icon: User },
  { key: 'serviceCount', label: '服务项目', icon: Collection },
  { key: 'orderCount', label: '订单总数', icon: Document },
  { key: 'todayOrderCount', label: '今日订单', icon: Trophy },
  { key: 'totalAmountCents', label: '模拟交易额', icon: Money, money: true },
] as const

/** 待办事项 */
const todoItems = [
  { key: 'pendingApplications', label: '待审核入驻申请', path: '/admin/audit' },
  { key: 'pendingComplaints', label: '待处理投诉', path: '/admin/complaints' },
  { key: 'waitingHumanConversations', label: '等待人工客服会话', path: '/cs/queue' },
] as const

function metricValue(key: (typeof metricCards)[number]['key']): string {
  if (!stats.value) return '-'
  const card = metricCards.find((c) => c.key === key)
  const raw = stats.value[key]
  if (card && 'money' in card) return fmtMoney(raw)
  return String(raw ?? 0)
}

/** 待办跳转：客服队列需 CUSTOMER_SERVICE 角色，避免被路由守卫弹回首页 */
function goTodo(path: string) {
  if (path.startsWith('/cs') && !userStore.hasRole('CUSTOMER_SERVICE')) {
    ElMessage.info('会话队列需在客服工作台处理，请使用客服账号登录')
    return
  }
  router.push(path)
}

onMounted(async () => {
  try {
    stats.value = await adminStatsOverview()
  } finally {
    loading.value = false
  }
})
</script>

<template>
  <div class="dashboard-page" v-loading="loading">
    <div class="page-header">
      <h1>数据概览</h1>
      <p class="sub">平台核心指标与待办事项</p>
    </div>

    <!-- 指标卡片 -->
    <el-row :gutter="16">
      <el-col v-for="card in metricCards" :key="card.key" :xs="12" :sm="8" :md="4">
        <el-card class="metric-card" shadow="hover">
          <div class="metric-icon"><el-icon><component :is="card.icon" /></el-icon></div>
          <div class="metric-value">{{ metricValue(card.key) }}</div>
          <div class="metric-label">{{ card.label }}</div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 待办事项 -->
    <el-card class="todo-card" shadow="hover">
      <template #header>待办事项</template>
      <div class="todo-list">
        <div v-for="item in todoItems" :key="item.key" class="todo-item" @click="goTodo(item.path)">
          <span class="todo-label">{{ item.label }}</span>
          <el-badge :value="stats?.[item.key] ?? 0" :max="999" :type="(stats?.[item.key] ?? 0) > 0 ? 'danger' : 'info'" />
          <el-button size="small" text type="primary">去处理 →</el-button>
        </div>
      </div>
    </el-card>
  </div>
</template>

<style scoped>
.page-header h1 {
  margin: 0 0 8px;
}
.sub {
  color: #909399;
  margin: 0 0 20px;
}
.metric-card {
  text-align: center;
  margin-bottom: 16px;
  border-radius: 8px;
}
.metric-icon {
  display: grid;
  width: 40px;
  height: 40px;
  margin: 0 auto 10px;
  color: var(--brand-primary);
  background: var(--brand-primary-lighter);
  border-radius: var(--radius-small);
  font-size: 20px;
  place-items: center;
}
.metric-value {
  font-size: 22px;
  font-weight: 700;
  color: #303133;
}
.metric-label {
  font-size: 13px;
  color: #909399;
  margin-top: 4px;
}
.todo-card {
  margin-top: 4px;
}
.todo-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.todo-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 12px;
  border: 1px solid #ebeef5;
  border-radius: 6px;
  cursor: pointer;
  transition: border-color 0.2s;
}
.todo-item:hover {
  border-color: #409eff;
}
.todo-label {
  flex: 1;
  font-size: 14px;
}
</style>
