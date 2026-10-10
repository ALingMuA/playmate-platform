<script setup lang="ts">
/**
 * 用户端 - 我的优惠券（/coupons）
 */
import { onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { myCoupons, type UserCouponView } from '@/api/coupon'

const router = useRouter()

const activeTab = ref<string>('AVAILABLE')
const list = ref<UserCouponView[]>([])
const total = ref(0)
const page = ref(1)
const pageSize = 9
const loading = ref(false)

const tabs = [
  { label: '可使用', value: 'AVAILABLE' },
  { label: '锁定中', value: 'LOCKED' },
  { label: '已使用', value: 'USED' },
  { label: '已过期', value: 'EXPIRED' },
  { label: '全部', value: '' },
]

function fmtTime(t?: string): string {
  if (!t) return '-'
  return t.length >= 10 ? t.slice(0, 10) : t
}

function fmtMoney(cents: number): string {
  return (cents / 100).toFixed(2)
}

function formatDiscount(item: UserCouponView): { main: string; unit: string } {
  if (item.couponType === 'FIXED') {
    return { main: `${item.discountCents / 100}`, unit: '元' }
  } else {
    const discount = (item.discountRateBp / 1000).toFixed(1).replace(/\.0$/, '')
    return { main: discount, unit: '折' }
  }
}

function formatScope(item: UserCouponView): string {
  switch (item.scopeType) {
    case 'ALL':
      return '全场通用'
    case 'GAME':
      return '指定游戏适用'
    case 'SERVICE':
      return '指定品类适用'
    case 'COMPANION':
      return '指定陪玩师适用'
    default:
      return '通用优惠券'
  }
}

async function loadData() {
  loading.value = true
  try {
    const res = await myCoupons(activeTab.value || undefined, page.value, pageSize)
    list.value = res.records
    total.value = res.total
  } finally {
    loading.value = false
  }
}

watch(activeTab, () => {
  page.value = 1
  loadData()
})

function handleGoUse() {
  router.push('/companions')
}

function handleGoOrder(orderId?: number) {
  if (orderId) {
    router.push(`/orders/${orderId}`)
  } else {
    router.push('/orders')
  }
}

onMounted(() => {
  loadData()
})
</script>

<template>
  <div class="page coupons-page">
    <div class="container" v-loading="loading">
      <div class="page-header flex justify-between items-center">
        <div>
          <h1 class="page-title">我的优惠券</h1>
          <p class="page-subtitle">管理您所领取的各类平台优惠券与抵扣权益</p>
        </div>
        <div>
          <el-button type="primary" plain round @click="router.push('/activities')">
            去活动广场领券 →
          </el-button>
        </div>
      </div>

      <!-- Tab 切换 -->
      <div class="tabs-bar">
        <div
          v-for="tab in tabs"
          :key="tab.value"
          class="tab-item"
          :class="{ active: activeTab === tab.value }"
          @click="activeTab = tab.value"
        >
          {{ tab.label }}
        </div>
      </div>

      <!-- 优惠券列表 -->
      <div v-if="list.length === 0 && !loading" class="empty-box card">
        <div class="empty-icon">🎟️</div>
        <div class="empty-text">当前分类下没有优惠券</div>
        <el-button type="primary" class="mt-4" round @click="router.push('/activities')">
          去活动中心看看
        </el-button>
      </div>

      <div v-else class="coupons-grid">
        <div
          v-for="item in list"
          :key="item.id"
          class="coupon-card"
          :class="[item.status.toLowerCase(), { 'is-expired': item.status === 'EXPIRED' || item.status === 'USED' }]"
        >
          <!-- 左侧优惠展示区 -->
          <div class="coupon-left">
            <div class="discount-value">
              <span class="currency" v-if="item.couponType === 'FIXED'">¥</span>
              <span class="amount">{{ formatDiscount(item).main }}</span>
              <span class="unit">{{ formatDiscount(item).unit }}</span>
            </div>
            <div class="threshold">
              {{ item.thresholdCents > 0 ? `满 ¥${fmtMoney(item.thresholdCents)} 可用` : '无门槛' }}
            </div>
          </div>

          <!-- 中间详情区 -->
          <div class="coupon-body">
            <div class="body-top">
              <span class="type-tag" :class="item.couponType.toLowerCase()">
                {{ item.couponType === 'FIXED' ? '满减券' : '折扣券' }}
              </span>
              <h3 class="template-name">{{ item.templateName }}</h3>
            </div>
            <div class="body-scope">
              {{ formatScope(item) }}
              <span v-if="item.couponType === 'DISCOUNT' && item.maxDiscountCents > 0" class="max-cap">
                (最高减 ¥{{ fmtMoney(item.maxDiscountCents) }})
              </span>
            </div>
            <div class="body-validity">
              有效期：{{ fmtTime(item.validFrom) }} ~ {{ fmtTime(item.validUntil) }}
            </div>
          </div>

          <!-- 右侧操作与状态区 -->
          <div class="coupon-right">
            <div v-if="item.status === 'AVAILABLE'" class="right-action">
              <el-button type="primary" size="small" round @click="handleGoUse">
                去使用
              </el-button>
            </div>
            <div v-else-if="item.status === 'LOCKED'" class="status-box status-locked">
              <span class="status-label">订单锁定中</span>
              <el-button link size="small" @click="handleGoOrder(item.lockedOrderId)">
                查看订单
              </el-button>
            </div>
            <div v-else-if="item.status === 'USED'" class="status-box status-used">
              <span class="status-stamp">已使用</span>
              <el-button link size="small" @click="handleGoOrder(item.usedOrderId)">
                相关订单
              </el-button>
            </div>
            <div v-else class="status-box status-expired">
              <span class="status-stamp">已过期</span>
            </div>
          </div>
        </div>
      </div>

      <!-- 分页栏 -->
      <div v-if="total > pageSize" class="pagination-bar">
        <el-pagination
          v-model:current-page="page"
          :page-size="pageSize"
          :total="total"
          layout="prev, pager, next"
          @current-change="loadData"
        />
      </div>
    </div>
  </div>
</template>

<style scoped>
.coupons-page {
  padding: 32px 0 64px;
}
.flex {
  display: flex;
}
.justify-between {
  justify-content: space-between;
}
.items-center {
  align-items: center;
}
.tabs-bar {
  display: flex;
  gap: 12px;
  margin-bottom: 24px;
  border-bottom: 1px solid var(--border);
  padding-bottom: 8px;
}
.tab-item {
  padding: 8px 18px;
  border-radius: 9999px;
  font-size: 0.9375rem;
  font-weight: 500;
  color: var(--muted-foreground);
  cursor: pointer;
  transition: all 0.2s ease;
}
.tab-item:hover {
  color: var(--foreground);
}
.tab-item.active {
  background: var(--primary);
  color: #fff;
  font-weight: 600;
}
.coupons-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(360px, 1fr));
  gap: 20px;
}
.coupon-card {
  display: flex;
  background: var(--card);
  border: 1px solid var(--border);
  border-radius: var(--radius-lg);
  overflow: hidden;
  box-shadow: var(--shadow-sm);
  position: relative;
  transition: transform 0.2s ease, box-shadow 0.2s ease;
}
.coupon-card:hover {
  transform: translateY(-2px);
  box-shadow: var(--shadow-md);
}
.coupon-card.is-expired {
  filter: grayscale(0.85);
  opacity: 0.8;
}
.coupon-left {
  background: linear-gradient(135deg, #ef4444 0%, #f97316 100%);
  color: #fff;
  width: 110px;
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
  padding: 16px 12px;
  text-align: center;
  flex-shrink: 0;
  position: relative;
}
.coupon-card.discount .coupon-left {
  background: linear-gradient(135deg, #8b5cf6 0%, #ec4899 100%);
}
.coupon-card.is-expired .coupon-left {
  background: #9ca3af;
}
.discount-value {
  display: flex;
  align-items: baseline;
  justify-content: center;
  line-height: 1;
  margin-bottom: 6px;
}
.currency {
  font-size: 1rem;
  font-weight: 600;
  margin-right: 2px;
}
.amount {
  font-size: 2rem;
  font-weight: 800;
}
.unit {
  font-size: 0.875rem;
  font-weight: 600;
  margin-left: 2px;
}
.threshold {
  font-size: 0.75rem;
  opacity: 0.9;
}
.coupon-body {
  flex: 1;
  padding: 16px 16px;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  overflow: hidden;
}
.body-top {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 6px;
}
.type-tag {
  font-size: 0.6875rem;
  font-weight: 600;
  padding: 2px 6px;
  border-radius: 4px;
  background: rgba(239, 68, 68, 0.1);
  color: #ef4444;
}
.type-tag.discount {
  background: rgba(139, 92, 246, 0.1);
  color: #8b5cf6;
}
.template-name {
  font-size: 1rem;
  font-weight: 600;
  color: var(--foreground);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.body-scope {
  font-size: 0.8125rem;
  color: var(--muted-foreground);
  margin-bottom: 6px;
}
.max-cap {
  font-size: 0.75rem;
  color: #ef4444;
}
.body-validity {
  font-size: 0.75rem;
  color: var(--muted-foreground);
}
.coupon-right {
  width: 90px;
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
  border-left: 1px dashed var(--border);
  padding: 12px 8px;
  flex-shrink: 0;
  background: rgba(0, 0, 0, 0.01);
}
.status-box {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
}
.status-label {
  font-size: 0.75rem;
  color: #f59e0b;
  font-weight: 600;
}
.status-stamp {
  font-size: 0.8125rem;
  font-weight: 700;
  color: #9ca3af;
  border: 1px solid #9ca3af;
  padding: 2px 6px;
  border-radius: 4px;
  transform: rotate(-10deg);
}
.empty-box {
  text-align: center;
  padding: 64px 0;
}
.empty-icon {
  font-size: 3rem;
  margin-bottom: 12px;
}
.empty-text {
  color: var(--muted-foreground);
}
.mt-4 {
  margin-top: 16px;
}
.pagination-bar {
  margin-top: 32px;
  display: flex;
  justify-content: center;
}
</style>
