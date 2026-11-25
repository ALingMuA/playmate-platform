<script setup lang="ts">
/**
 * 订单支付（对齐 docs/06-原型与架构图/frontend-prototype/payment.html）。
 *
 * <p>展示订单摘要与虚拟余额，使用余额完成模拟支付（FR-U09），
 * 支付成功后跳转订单列表；余额不足时引导充值提示。</p>
 */
import { computed, onMounted, ref, watch } from 'vue'
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
const orderError = ref('')
const walletError = ref('')
const orderNotFound = ref(false)

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

let loadSeq = 0

async function handlePay() {
  if (!order.value || paying.value) return
  if (insufficient.value) {
    ElMessage.warning('虚拟余额不足，请联系客服或重新下单')
    return
  }
  paying.value = true
  try {
    await payOrder(order.value.id)
    ElMessage.success('支付成功')
    await router.replace('/orders')
  } catch {
    // http.ts 已统一提示错误
  } finally {
    paying.value = false
  }
}

async function loadPaymentData() {
  const id = orderId.value
  const seq = ++loadSeq
  loading.value = true
  order.value = null
  wallet.value = null
  orderError.value = ''
  walletError.value = ''
  orderNotFound.value = false
  if (!Number.isSafeInteger(id) || id <= 0) {
    orderError.value = '订单链接无效，请从订单列表重新进入支付。'
    loading.value = false
    return
  }
  try {
    const [orderResult, walletResult] = await Promise.allSettled([
      userOrderDetail(id),
      walletMe(),
    ])
    if (seq !== loadSeq || orderId.value !== id) return
    if (orderResult.status === 'fulfilled') {
      order.value = orderResult.value
    } else {
      const err = orderResult.reason as { status?: number; code?: string }
      orderNotFound.value = err.status === 404 || err.code === 'ORDER_NOT_FOUND'
      orderError.value = orderNotFound.value ? '订单不存在或已失效' : '订单信息加载失败，请重试。'
    }
    if (walletResult.status === 'fulfilled') {
      wallet.value = walletResult.value
    } else {
      walletError.value = '余额加载失败，请重试后再支付。'
    }
  } finally {
    if (seq === loadSeq) loading.value = false
  }
}

onMounted(loadPaymentData)
watch(orderId, () => {
  void loadPaymentData()
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
                <div v-if="order.discountAmountCents && order.discountAmountCents > 0" class="order-meta" style="color: #ef4444">
                  优惠券：{{ order.couponNameSnapshot || '平台优惠券' }}（-{{ fmtMoney(order.discountAmountCents) }}）
                </div>
              </div>
              <div class="order-price-wrap" style="text-align: right">
                <div v-if="order.discountAmountCents && order.discountAmountCents > 0" style="font-size: 0.875rem; text-decoration: line-through; color: var(--muted-foreground)">
                  原价 {{ fmtMoney(order.originalAmountCents || order.totalAmountCents) }}
                </div>
                <div class="order-price" style="font-size: 1.5rem; color: var(--destructive)">
                  {{ fmtMoney(order.totalAmountCents) }}
                </div>
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
            <div v-if="walletError" class="text-destructive" role="alert" style="margin-bottom: 12px">
              {{ walletError }}
              <button class="btn btn-outline btn-sm" @click="loadPaymentData">重试</button>
            </div>
            <p v-if="insufficient" class="text-destructive" style="margin-bottom: 12px">
              当前为演示环境虚拟余额。请由管理员在用户管理中为本账号发放演示余额；准备完成后可返回本订单继续支付。
            </p>
            <button class="btn btn-primary btn-lg btn-block" :disabled="paying || insufficient || !wallet" @click="handlePay">
              {{ paying ? '支付中…' : insufficient ? '余额不足' : '确认支付 ' + fmtMoney(order.totalAmountCents) }}
            </button>
            <button class="btn btn-ghost btn-block mt-1" @click="router.push('/orders')">稍后支付，返回订单列表</button>
          </div>
        </div>
      </template>

      <div v-else-if="!loading && orderNotFound" class="empty-state">
        <div class="empty-icon">📭</div>
        <p>{{ orderError }}</p>
        <button class="btn btn-secondary mt-2" @click="router.push('/orders')">返回订单列表</button>
      </div>
      <div v-else-if="!loading && orderError" class="empty-state" role="alert">
        <p>{{ orderError }}</p>
        <button class="btn btn-outline mt-2" @click="loadPaymentData">重试</button>
        <button class="btn btn-secondary mt-2" @click="router.push('/orders')">返回订单列表</button>
      </div>
    </div>
  </div>
</template>
