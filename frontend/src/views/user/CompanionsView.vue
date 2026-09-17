<script setup lang="ts">
/**
 * 找陪玩（对齐 frontend-prototype companions.html：筛选栏 + 陪玩师卡片 + 分页）。
 *
 * <p>展示审核通过且已上架的可预约服务，支持按游戏筛选与分页；
 * 从游戏列表页携带 gameId 进入时自动预选。点击卡片跳转订单页预约。</p>
 */
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { listGames, type Game } from '@/api/catalog'
import { listBookableServices, type BookableService } from '@/api/order'
import { useUserStore } from '@/stores/user'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

/** 入驻申请引导：非陪玩师（含游客）都能看到，点击后由路由守卫引导登录 */
const showApplyCta = computed(() => !userStore.hasRole('COMPANION'))

const games = ref<Game[]>([])
const gameId = ref<number | undefined>(undefined)
const services = ref<BookableService[]>([])
const total = ref(0)
const page = ref(1)
const pageSize = 8
const loading = ref(false)

const currentGameName = computed(() => games.value.find((g) => g.id === gameId.value)?.gameName ?? '全部游戏')

const totalPages = computed(() => Math.max(1, Math.ceil(total.value / pageSize)))

async function load() {
  loading.value = true
  try {
    const result = await listBookableServices(gameId.value, undefined, page.value, pageSize)
    services.value = result.records
    total.value = result.total
  } finally {
    loading.value = false
  }
}

function onGameChange() {
  page.value = 1
  load()
}

function goBook(service: BookableService) {
  router.push({
    path: '/booking',
    query: { companionUserId: String(service.companionUserId), serviceId: String(service.id) },
  })
}

function fmtPrice(cents: number): string {
  return '¥' + (cents / 100).toFixed(cents % 100 === 0 ? 0 : 2)
}

onMounted(async () => {
  try {
    games.value = await listGames()
  } catch {
    games.value = []
  }
  const qGameId = Number(route.query.gameId)
  if (qGameId && games.value.some((g) => g.id === qGameId)) {
    gameId.value = qGameId
  }
  await load()
})

watch(
  () => route.query.gameId,
  (val) => {
    const qGameId = Number(val)
    if (qGameId && qGameId !== gameId.value) {
      gameId.value = qGameId
      page.value = 1
      load()
    }
  },
)
</script>

<template>
  <div class="page">
    <div class="container">
      <!-- 页头 -->
      <div class="page-header">
        <h1 class="page-title">找陪玩师</h1>
        <p class="page-subtitle">筛选心仪的陪玩伙伴 · 当前：{{ currentGameName }}（共 {{ total }} 个服务）</p>
      </div>

      <!-- 筛选栏（原型 filter-bar） -->
      <div class="filter-bar">
        <div class="filter-row">
          <div class="filter-item">
            <label>游戏</label>
            <select v-model="gameId" class="form-control" @change="onGameChange">
              <option :value="undefined">全部游戏</option>
              <option v-for="g in games" :key="g.id" :value="g.id">{{ g.gameName }}</option>
            </select>
          </div>
          <div class="filter-item">
            <label>&nbsp;</label>
            <button class="btn btn-secondary" @click="onGameChange">查询</button>
          </div>
        </div>
      </div>

      <!-- 加载中 -->
      <div v-if="loading" class="empty-state">加载中…</div>

      <!-- 陪玩师卡片网格（原型 companion-grid） -->
      <div v-else-if="services.length" class="companion-grid">
        <div v-for="s in services" :key="s.id" class="companion-card"
          @click="router.push(`/companions/${s.companionUserId}`)">
          <div class="companion-header">
            <div class="companion-avatar">🎮</div>
            <div class="companion-meta">
              <div class="companion-name">{{ s.title }}</div>
              <div class="companion-level">{{ s.gameName }} · {{ s.serviceTypeName }}</div>
              <div class="companion-rating">
                <span style="color: #f59e0b">★</span>
                <span>{{ fmtPrice(s.priceCents) }}/小时</span>
                <span class="text-muted">· 最短 {{ s.minDurationMinutes }} 分钟</span>
              </div>
            </div>
          </div>
          <div class="companion-tags" v-if="s.tagNames?.length">
            <span v-for="t in s.tagNames" :key="t" class="tag">{{ t }}</span>
          </div>
          <p v-else-if="s.description" class="text-muted" style="font-size: 0.8125rem; margin-bottom: 12px">
            {{ s.description }}
          </p>
          <div class="companion-footer">
            <div class="companion-price">{{ fmtPrice(s.priceCents) }}<span>/小时</span></div>
            <button class="btn btn-primary btn-sm" @click.stop="goBook(s)">立即预约</button>
          </div>
        </div>
      </div>

      <!-- 空态 -->
      <div v-else class="empty-state">
        <div class="empty-icon">🔍</div>
        <p>暂无符合条件的陪玩服务，换个游戏看看吧</p>
      </div>

      <!-- 入驻引导（FR-P01）：陪玩师本人不展示 -->
      <div v-if="showApplyCta" class="card mt-2">
        <div class="card-body flex items-center justify-between flex-wrap gap-1">
          <div>
            <h3 class="card-title">你也想接单？</h3>
            <p class="text-muted" style="font-size: 0.875rem; margin: 0">
              成为平台陪玩师，上架你的陪玩服务，让更多玩家找到你
            </p>
          </div>
          <button class="btn btn-primary btn-sm" @click="router.push('/become-companion')">成为陪玩师</button>
        </div>
      </div>

      <!-- 分页（原型 pagination） -->
      <div v-if="totalPages > 1" class="pagination">
        <button class="page-link" :disabled="page <= 1" @click="page > 1 && ((page -= 1), load())">‹</button>
        <button v-for="p in totalPages" :key="p" class="page-link" :class="{ active: p === page }" @click="page = p; load()">
          {{ p }}
        </button>
        <button class="page-link" :disabled="page >= totalPages" @click="page < totalPages && ((page += 1), load())">›</button>
      </div>
    </div>
  </div>
</template>
