<script setup lang="ts">
/**
 * 我的订单（对齐 frontend-prototype orders.html：胶囊 Tab + 订单卡片）。
 *
 * <p>按状态筛选订单（FR-U10）；点击卡片进入订单详情（FR-U11），
 * 支付/评价/投诉跳转独立页面，取消与确认完成就地确认。</p>
 */
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { cancelOrder, confirmOrder, myOrders, type Order } from '@/api/order'

const router = useRouter()

const activeTab = ref('ALL')
const list = ref<Order[]>([])
const total = ref(0)
const page = ref(1)
const pageSize = 10
const loading = ref(false)
const acting = ref(false)

const tabs = [
  { value: 'ALL', label: '全部' },
  { value: 'PENDING_PAYMENT', label: '待支付' },
  { value: 'WAITING_ACCEPTANCE', label: '待接单' },
  { value: 'WAITING_SERVICE', label: '待服务' },
  { value: 'IN_SERVICE', label: '服务中' },
  { value: 'WAITING_CONFIRMATION', label: '待确认' },
  { value: 'COMPLETED', label: '已完成' },
  { value: 'AFTER_SALES', label: '售后中' },
  { value: 'CLOSED', label: '已关闭' },
]

const statusMap: Record<string, { label: string; type: string }> = {
  PENDING_PAYMENT: { label: '待支付', type: 'warning' },
  WAITING_ACCEPTANCE: { label: '待接单', type: 'info' },
  WAITING_SERVICE: { label: '待服务', type: 'primary' },
  IN_SERVICE: { label: '服务中', type: 'success' },
  WAITING_CONFIRMATION: { label: '待确认', type: 'warning' },
  COMPLETED: { label: '已完成', type: 'success' },
  AFTER_SALES: { label: '售后中', type: 'danger' },
  CLOSED: { label: '已关闭', type: 'info' },
}

function fmtTime(t?: string): string {
  if (!t) return '-'
  return t.length >= 16 ? t.slice(0, 16) : t
}

function fmtMoney(cents: number): string {
  return '¥' + (cents / 100).toFixed(2)
}

async function load() {
  loading.value = true
  try {
    const status = activeTab.value === 'ALL' ? undefined : activeTab.value
    const result = await myOrders(status, page.value, pageSize)
    list.value = result.records
    total.value = result.total
  } finally {
    loading.value = false
  }
}

function onTabChange(tab: string) {
  activeTab.value = tab
  page.value = 1
  load()
}

function goDetail(row: Order) {
  router.push(`/orders/${row.id}`)
}

async function handleCancel(row: Order) {
  let reason = ''
  try {
    const result = await ElMessageBox.prompt(
      row.orderStatus === 'PENDING_PAYMENT' ? '订单未支付，取消后将直接关闭。' : '订单已支付，取消后将全额退回虚拟余额。',
      `取消订单 ${row.orderNo}`,
      {
        confirmButtonText: '确认取消',
        cancelButtonText: '再想想',
        inputPlaceholder: '取消原因（选填）',
      },
    )
    reason = result.value
  } catch {
    return
  }
  acting.value = true
  try {
    await cancelOrder(row.id, reason)
    ElMessage.success('订单已取消')
    await load()
  } finally {
    acting.value = false
  }
}

async function handleConfirm(row: Order) {
  try {
    await ElMessageBox.confirm('确认服务已完成？确认后陪玩师将收到收益。', '确认完成', { type: 'info' })
  } catch {
    return
  }
  acting.value = true
  try {
    await confirmOrder(row.id)
    ElMessage.success('已完成')
    await load()
  } finally {
    acting.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="page">
    <div class="container">
      <!-- 页头 -->
      <div class="page-header">
        <h1 class="page-title">我的订单</h1>
        <p class="page-subtitle">管理你的全部预约订单</p>
      </div>

      <!-- 胶囊 Tab（原型 tabs） -->
      <div class="tabs">
        <button v-for="t in tabs" :key="t.value" class="tab" :class="{ active: activeTab === t.value }"
          @click="onTabChange(t.value)">
          {{ t.label }}
        </button>
      </div>

      <!-- 订单卡片列表（原型 order-list） -->
      <div v-if="list.length" class="order-list">
        <div v-for="row in list" :key="row.id" class="order-card" @click="goDetail(row)">
          <div class="order-card-header">
            <span class="order-id">订单号：{{ row.orderNo }}</span>
            <span class="badge" :class="{
              'badge-warning': ['PENDING_PAYMENT', 'WAITING_CONFIRMATION'].includes(row.orderStatus),
              'badge-success': ['IN_SERVICE', 'COMPLETED'].includes(row.orderStatus),
              'badge-destructive': row.orderStatus === 'AFTER_SALES',
              'badge-outline': ['WAITING_ACCEPTANCE', 'WAITING_SERVICE', 'CLOSED'].includes(row.orderStatus),
            }">
              {{ statusMap[row.orderStatus]?.label ?? row.orderStatus }}
            </span>
          </div>
          <div class="order-body">
            <div class="order-thumb">🎮</div>
            <div class="order-info">
              <div class="order-title">{{ row.serviceTitleSnapshot }}</div>
              <div class="order-meta">陪玩师：{{ row.companionNameSnapshot }}</div>
              <div class="order-meta">
                {{ fmtTime(row.appointmentStartAt) }} - {{ fmtTime(row.appointmentEndAt) }}（{{ row.durationMinutes }}分钟）
              </div>
              <div v-if="row.closedReason" class="order-meta text-destructive">关闭原因：{{ row.closedReason }}</div>
            </div>
            <div class="order-price">{{ fmtMoney(row.totalAmountCents) }}</div>
          </div>
          <div class="order-actions" @click.stop>
            <button v-if="row.orderStatus === 'PENDING_PAYMENT'" class="btn btn-primary btn-sm"
              @click="router.push(`/payment/${row.id}`)">
              去支付
            </button>
            <button
              v-if="['PENDING_PAYMENT', 'WAITING_ACCEPTANCE', 'WAITING_SERVICE'].includes(row.orderStatus)"
              class="btn btn-outline btn-sm" :disabled="acting" @click="handleCancel(row)"
            >
              取消订单
            </button>
            <button v-if="row.orderStatus === 'WAITING_CONFIRMATION'" class="btn btn-primary btn-sm"
              :disabled="acting" @click="handleConfirm(row)">
              确认完成
            </button>
            <button v-if="row.orderStatus === 'COMPLETED'" class="btn btn-secondary btn-sm"
              @click="router.push(`/review/${row.id}`)">
              评价
            </button>
            <button
              v-if="['WAITING_CONFIRMATION', 'COMPLETED', 'AFTER_SALES'].includes(row.orderStatus)"
              class="btn btn-destructive btn-sm" @click="router.push(`/complaint/${row.id}`)"
            >
              投诉
            </button>
            <button class="btn btn-ghost btn-sm" @click="goDetail(row)">详情</button>
          </div>
        </div>
      </div>

      <!-- 空态 -->
      <div v-else-if="!loading" class="empty-state">
        <div class="empty-icon">📋</div>
        <p>暂无订单，去找一位心仪的陪玩师吧</p>
        <button class="btn btn-primary mt-2" @click="router.push('/companions')">去找陪玩</button>
      </div>

      <!-- 分页 -->
      <div v-if="total > pageSize" class="pagination">
        <button class="page-link" :disabled="page <= 1" @click="page > 1 && ((page -= 1), load())">‹</button>
        <button v-for="p in Math.ceil(total / pageSize)" :key="p" class="page-link"
          :class="{ active: p === page }" @click="page = p; load()">
          {{ p }}
        </button>
        <button class="page-link" :disabled="page >= Math.ceil(total / pageSize)"
          @click="page < Math.ceil(total / pageSize) && ((page += 1), load())">›</button>
      </div>
    </div>
  </div>
</template>
