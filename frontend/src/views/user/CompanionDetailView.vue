<script setup lang="ts">
/**
 * 陪玩师详情（对齐 frontend-prototype companion-detail.html）。
 *
 * <p>展示陪玩师的服务项目与历史评价（FR-U05），支持选择服务后跳转预约下单（FR-U07）。
 * 数据来自可预约服务公开接口与陪玩师公开评价接口，游客可访问。</p>
 */
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { listBookableServices, type BookableService } from '@/api/order'
import { companionReviews, type Review } from '@/api/review'

const route = useRoute()
const router = useRouter()

const companionUserId = computed(() => Number(route.params.userId))

const services = ref<BookableService[]>([])
const reviews = ref<Review[]>([])
const selectedId = ref<number | null>(null)
const loading = ref(true)

/** 最低价格（元/小时） */
const minPrice = computed(() => {
  if (!services.value.length) return null
  const min = Math.min(...services.value.map((s) => s.priceCents))
  return '¥' + (min / 100).toFixed(min % 100 === 0 ? 0 : 2)
})

/** 覆盖游戏列表 */
const gameNames = computed(() => {
  const set = new Set(services.value.map((s) => s.gameName).filter(Boolean))
  return [...set].join('、')
})

/** 全部能力标签（服务标签聚合去重） */
const allTags = computed(() => {
  const set = new Set<string>()
  services.value.forEach((s) => s.tagNames?.forEach((t) => set.add(t)))
  return [...set].slice(0, 8)
})

const averageScore = computed(() => {
  if (!reviews.value.length) return null
  const sum = reviews.value.reduce((acc, r) => acc + r.score, 0)
  return (sum / reviews.value.length).toFixed(1)
})

function fmtPrice(cents: number): string {
  return '¥' + (cents / 100).toFixed(cents % 100 === 0 ? 0 : 2)
}

function fmtTime(t?: string): string {
  if (!t) return ''
  return t.length >= 10 ? t.slice(0, 10) : t
}

/** 选择服务后跳转预约 */
function goBooking() {
  const service = selectedId.value
    ? services.value.find((s) => s.id === selectedId.value)
    : services.value[0]
  if (!service) return
  router.push({
    path: '/booking',
    query: { companionUserId: String(service.companionUserId), serviceId: String(service.id) },
  })
}

onMounted(async () => {
  try {
    const [svcResult, reviewResult] = await Promise.all([
      listBookableServices(undefined, companionUserId.value, 1, 20),
      companionReviews(companionUserId.value, 1, 10),
    ])
    services.value = svcResult.records
    reviews.value = reviewResult.records
    if (services.value.length) selectedId.value = services.value[0].id
  } finally {
    loading.value = false
  }
})
</script>

<template>
  <div class="page">
    <div class="container" v-loading="loading">
      <!-- 详情头部（原型 detail-header） -->
      <div v-if="services.length" class="detail-header">
        <div class="detail-cover">
          <div class="detail-avatar">🎮</div>
        </div>
        <div class="detail-header-body">
          <h1 class="detail-name">
            陪玩师 #{{ companionUserId }}
            <span class="badge badge-secondary">已认证</span>
          </h1>
          <div class="detail-meta">
            <span>⭐ {{ averageScore ? averageScore + ' 分' : '暂无评分' }}</span>
            <span>服务 {{ services.length }} 项</span>
            <span v-if="minPrice">{{ minPrice }}/小时起</span>
            <span v-if="gameNames">覆盖：{{ gameNames }}</span>
          </div>
          <div v-if="allTags.length" class="companion-tags mt-1">
            <span v-for="t in allTags" :key="t" class="badge badge-outline">{{ t }}</span>
          </div>
        </div>
      </div>

      <div v-if="!services.length && !loading" class="empty-state">
        <div class="empty-icon">😕</div>
        <p>该陪玩师暂无可预约服务</p>
        <button class="btn btn-secondary mt-2" @click="router.push('/companions')">返回找陪玩</button>
      </div>

      <div v-if="services.length" class="two-col">
        <!-- 侧栏（原型 sidebar） -->
        <aside class="sidebar">
          <div class="sidebar-section">
            <h4>服务概况</h4>
            <p class="text-muted" style="font-size: 0.875rem">
              共 {{ services.length }} 个服务项目，价格从 {{ minPrice }}/小时起，支持在线预约。
            </p>
          </div>
          <div class="sidebar-section">
            <h4>服务标签</h4>
            <div class="companion-tags" style="margin: 0">
              <span v-for="t in allTags" :key="t" class="badge badge-outline">{{ t }}</span>
              <span v-if="!allTags.length" class="text-muted" style="font-size: 0.8125rem">暂无</span>
            </div>
          </div>
          <button class="btn btn-primary btn-block btn-lg mt-2" @click="goBooking">立即预约</button>
        </aside>

        <div>
          <!-- 服务项目（原型 service-list） -->
          <div class="card mb-2">
            <div class="card-body">
              <h3 class="card-title">服务项目</h3>
              <div class="service-list">
                <div
                  v-for="s in services"
                  :key="s.id"
                  class="service-item"
                  :class="{ selected: selectedId === s.id }"
                  @click="selectedId = s.id"
                >
                  <div style="flex: 1">
                    <div class="order-title">{{ s.title }}</div>
                    <div class="order-meta">
                      {{ s.gameName }} · {{ s.serviceTypeName }}
                      <template v-if="s.description"> · {{ s.description }}</template>
                    </div>
                    <div v-if="s.tagNames?.length" class="companion-tags mt-1" style="margin-top: 8px">
                      <span v-for="t in s.tagNames" :key="t" class="badge badge-outline">{{ t }}</span>
                    </div>
                  </div>
                  <div class="order-price">{{ fmtPrice(s.priceCents) }}/小时</div>
                </div>
              </div>
            </div>
          </div>

          <!-- 历史评价（原型历史评价区，FR-U16） -->
          <div class="card">
            <div class="card-body">
              <h3 class="card-title">历史评价</h3>
              <div v-if="reviews.length" class="order-list mt-2">
                <div v-for="r in reviews" :key="r.id" class="order-card">
                  <div class="order-body">
                    <div class="order-thumb">👤</div>
                    <div class="order-info">
                      <div class="order-title">{{ r.userNickname || '匿名玩家' }}</div>
                      <div class="order-meta">
                        <span style="color: #f59e0b">★</span>
                        <span v-for="i in 5" :key="i" style="color: #f59e0b; font-size: 0.8125rem"
                          :class="{ 'text-muted': i > r.score }">★</span>
                        <template v-if="r.tags?.length"> · {{ r.tags.join('、') }}</template>
                      </div>
                      <p class="mt-1" style="font-size: 0.875rem; color: var(--foreground)">{{ r.content }}</p>
                    </div>
                    <div class="order-meta">{{ fmtTime(r.createdAt) }}</div>
                  </div>
                </div>
              </div>
              <div v-else class="empty-state" style="padding: 24px">
                <p>暂无评价</p>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.service-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  cursor: pointer;
}
</style>
