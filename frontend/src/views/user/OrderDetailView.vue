<script setup lang="ts">
/**
 * 订单详情（对齐 frontend-prototype order-detail.html：时间线 + 服务信息 + 操作）。
 *
 * <p>展示订单状态轨迹（FR-U11），按状态提供支付/取消/确认完成/评价/投诉入口，
 * 分别跳转支付页、评价页、投诉页。</p>
 */
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { cancelOrder, confirmOrder, userOrderDetail, type Order } from '@/api/order'

const route = useRoute()
const router = useRouter()

const orderId = computed(() => Number(route.params.id))
const order = ref<Order | null>(null)
const loading = ref(true)
const acting = ref(false)

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
    order.value = await userOrderDetail(orderId.value)
  } finally {
    loading.value = false
  }
}

async function handlePay() {
  if (order.value) router.push(`/payment/${order.value.id}`)
}

async function handleCancel() {
  if (!order.value) return
  let reason = ''
  try {
    const result = await ElMessageBox.prompt(
      order.value.orderStatus === 'PENDING_PAYMENT' ? '订单未支付，取消后将直接关闭。' : '订单已支付，取消后将全额退回虚拟余额。',
      `取消订单 ${order.value.orderNo}`,
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
    await cancelOrder(order.value.id, reason)
    ElMessage.success('订单已取消')
    await load()
  } finally {
    acting.value = false
  }
}

async function handleConfirm() {
  if (!order.value) return
  try {
    await ElMessageBox.confirm('确认服务已完成？确认后陪玩师将收到收益。', '确认完成', { type: 'info' })
  } catch {
    return
  }
  acting.value = true
  try {
    await confirmOrder(order.value.id)
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
    <div class="container" v-loading="loading">
      <!-- 页头 -->
      <div class="page-header" v-if="order">
        <div>
          <h1 class="page-title">订单详情</h1>
          <p class="page-subtitle">订单号：{{ order.orderNo }}</p>
        </div>
        <el-tag :type="(statusMap[order.orderStatus]?.type as any) ?? 'info'">
          {{ statusMap[order.orderStatus]?.label ?? order.orderStatus }}
        </el-tag>
      </div>

      <template v-if="order">
        <div class="two-col">
          <!-- 左侧：状态轨迹 + 服务信息 -->
          <div>
            <!-- 状态时间线（原型 timeline） -->
            <div class="card mb-2">
              <div class="card-body">
                <h3 class="card-title mb-2">订单状态</h3>
                <div class="timeline">
                  <div v-for="(h, i) in order.statusHistories ?? []" :key="i" class="timeline-item active">
                    <div class="timeline-time">{{ fmtTime(h.createdAt) }}</div>
                    <div class="timeline-title">
                      {{ h.fromStatus || '创建' }} → {{ h.toStatus }}
                      <span class="badge badge-secondary" style="margin-left: 6px">{{ h.actionCode }}</span>
                    </div>
                    <div v-if="h.reason" class="timeline-time">{{ h.reason }}</div>
                  </div>
                  <div v-if="!order.statusHistories?.length" class="text-muted" style="font-size: 0.875rem">
                    暂无状态记录
                  </div>
                </div>
              </div>
            </div>

            <!-- 服务信息 -->
            <div class="card">
              <div class="card-body">
                <h3 class="card-title mb-2">服务信息</h3>
                <div class="order-body">
                  <div class="order-thumb">🎮</div>
                  <div class="order-info">
                    <div class="order-title">{{ order.serviceTitleSnapshot }}</div>
                    <div class="order-meta">陪玩师：{{ order.companionNameSnapshot }}</div>
                    <div class="order-meta">
                      预约：{{ fmtTime(order.appointmentStartAt) }} - {{ fmtTime(order.appointmentEndAt) }}（{{ order.durationMinutes }}分钟）
                    </div>
                    <template v-if="order.gameServer">
                      <div class="order-meta">区服：{{ order.gameServer }}</div>
                    </template>
                    <template v-if="order.gameNickname">
                      <div class="order-meta">游戏昵称：{{ order.gameNickname }}</div>
                    </template>
                    <template v-if="order.userRemark">
                      <div class="order-meta">需求备注：{{ order.userRemark }}</div>
                    </template>
                    <template v-if="order.closedReason">
                      <div class="order-meta text-destructive">关闭原因：{{ order.closedReason }}</div>
                    </template>
                  </div>
                </div>
              </div>
            </div>
          </div>

          <!-- 右侧：金额与操作（原型 sidebar） -->
          <aside class="sidebar">
            <h3 class="sidebar-title">订单金额</h3>
            <div class="sidebar-section">
              <div class="flex justify-between">
                <span class="text-muted">单价</span>
                <span>{{ fmtMoney(order.unitPriceCents) }}/小时</span>
              </div>
              <div class="flex justify-between mt-1">
                <span class="text-muted">时长</span>
                <span>{{ order.durationMinutes }} 分钟</span>
              </div>
              <div class="flex justify-between items-center mt-2" style="border-top: 1px solid var(--border); padding-top: 10px">
                <span>应付金额</span>
                <span class="companion-price" style="font-size: 1.5rem">{{ fmtMoney(order.totalAmountCents) }}</span>
              </div>
            </div>
            <div class="sidebar-section">
              <button v-if="order.orderStatus === 'PENDING_PAYMENT'" class="btn btn-primary btn-block"
                @click="handlePay">
                去支付
              </button>
              <button
                v-if="['PENDING_PAYMENT', 'WAITING_ACCEPTANCE', 'WAITING_SERVICE'].includes(order.orderStatus)"
                class="btn btn-outline btn-block mt-1" :disabled="acting" @click="handleCancel"
              >
                取消订单
              </button>
              <button v-if="order.orderStatus === 'WAITING_CONFIRMATION'" class="btn btn-primary btn-block"
                :disabled="acting" @click="handleConfirm">
                确认完成
              </button>
              <button v-if="order.orderStatus === 'COMPLETED'" class="btn btn-secondary btn-block"
                @click="router.push(`/review/${order.id}`)">
                评价订单
              </button>
              <button
                v-if="['WAITING_CONFIRMATION', 'COMPLETED', 'AFTER_SALES'].includes(order.orderStatus)"
                class="btn btn-destructive btn-block mt-1"
                @click="router.push(`/complaint/${order.id}`)"
              >
                发起投诉
              </button>
              <button class="btn btn-ghost btn-block mt-1" @click="router.push('/orders')">返回订单列表</button>
            </div>
          </aside>
        </div>
      </template>

      <div v-else-if="!loading" class="empty-state">
        <div class="empty-icon">📭</div>
        <p>订单不存在</p>
        <button class="btn btn-secondary mt-2" @click="router.push('/orders')">返回订单列表</button>
      </div>
    </div>
  </div>
</template>
