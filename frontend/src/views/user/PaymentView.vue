<script setup lang="ts">
/**
 * 订单支付（对齐 frontend-prototype payment.html）。
 *
 * <p>展示订单摘要与虚拟余额，使用余额完成模拟支付（FR-U09），
 * 支付成功后跳转订单列表；余额不足时引导充值提示。</p>
 */
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { payOrder, userOrderDetail, walletMe, type Order } from '@/api/order'

const route = useRoute()
const router = useRouter()

const orderId = computed(() => Number(route.params.id))

const order = ref<Order | null>(null)
const wallet = ref<{ balanceCents: number } | null>(null)
const loading = ref(true)
const paying = ref(false)

const insufficient = computed(() => {
  if (!order.value || !wallet.value) return false
  return wallet.value.balanceCents < order.value.totalAmountCents
})

function fmtMoney(cents: number): string {
  return '¥' + (cents / 100).toFixed(2)
}

function fmtTime(t?: string): string {
  if (!t) return '-'
  return t.length >= 16 ? t.slice(0, 16) : t
}

async function handlePay() {
  if (!order.value) return
  if (insufficient.value) {
    ElMessage.warning('虚拟余额不足，请联系客服或重新下单')
    return
  }
  paying.value = true
  try {
    await payOrder(order.value.id)
    ElMessage.success('支付成功')
    router.replace('/orders')
  } finally {
    paying.value = false
  }
}

onMounted(async () => {
  try {
    const [orderData, walletData] = await Promise.all([
      userOrderDetail(orderId.value),
      walletMe(),
    ])
    order.value = orderData
    wallet.value = walletData
  } finally {
    loading.value = false
  }
})
</script>

<template>
  <div class="page">
    <div class="container" v-loading="loading">
      <!-- 页头 -->
      <div class="page-header">
        <h1 class="page-title">订单支付</h1>
        <p class="page-subtitle">请尽快完成支付，未支付订单将被自动关闭</p>
      </div>

      <template v-if="order">
        <!-- 订单摘要 -->
        <div class="card mb-2">
          <div class="card-body">
            <h3 class="card-title mb-2">订单摘要</h3>
            <div class="order-body">
              <div class="order-thumb">🎮</div>
              <div class="order-info">
                <div class="order-title">{{ order.serviceTitleSnapshot }}</div>
                <div class="order-meta">
                  {{ fmtTime(order.appointmentStartAt) }} · {{ order.durationMinutes }} 分钟
                  <template v-if="order.gameServer"> · {{ order.gameServer }}</template>
                </div>
                <div class="order-meta">订单号：{{ order.orderNo }}</div>
              </div>
              <div class="order-price" style="font-size: 1.5rem; color: var(--destructive)">
                {{ fmtMoney(order.totalAmountCents) }}
              </div>
            </div>
          </div>
        </div>

        <!-- 余额与支付（原型 wallet-grid） -->
        <div class="wallet-grid">
          <div class="wallet-card">
            <div class="wallet-value">{{ wallet ? fmtMoney(wallet.balanceCents) : '-' }}</div>
            <div class="wallet-label">可用余额</div>
          </div>
          <div class="wallet-card">
            <div class="wallet-value" style="color: var(--destructive)">{{ fmtMoney(order.totalAmountCents) }}</div>
            <div class="wallet-label">应付金额</div>
          </div>
          <div class="wallet-card">
            <div class="wallet-value" :style="{ color: insufficient ? 'var(--destructive)' : 'var(--success, #16a34a)' }">
              {{ wallet ? fmtMoney(wallet.balanceCents - order.totalAmountCents) : '-' }}
            </div>
            <div class="wallet-label">支付后余额</div>
          </div>
        </div>

        <div class="card mt-2">
          <div class="card-body">
            <p v-if="insufficient" class="text-destructive" style="margin-bottom: 12px">
              余额不足，暂不支持充值，请取消订单后重新下单或联系在线客服。
            </p>
            <button class="btn btn-primary btn-lg btn-block" :disabled="paying || insufficient" @click="handlePay">
              {{ paying ? '支付中…' : insufficient ? '余额不足' : '确认支付 ' + fmtMoney(order.totalAmountCents) }}
            </button>
            <button class="btn btn-ghost btn-block mt-1" @click="router.push('/orders')">稍后支付，返回订单列表</button>
          </div>
        </div>
      </template>

      <div v-else-if="!loading" class="empty-state">
        <div class="empty-icon">📭</div>
        <p>订单不存在或已失效</p>
        <button class="btn btn-secondary mt-2" @click="router.push('/orders')">返回订单列表</button>
      </div>
    </div>
  </div>
</template>
