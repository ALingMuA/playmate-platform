<script setup lang="ts">
/**
 * 找陪玩（对齐 frontend-prototype companions.html：筛选栏 + 陪玩师卡片 + 分页）。
 *
 * <p>列表展示陪玩师维度（FR-U02）：只出现认证通过且存在在架可预约服务的陪玩师；
 * 支持按游戏筛选（该游戏下有在架服务）与分页，从游戏列表页携带 gameId 进入时自动预选。
 * 点击卡片跳转陪玩师公开主页并直达"服务"Tab，由用户选择具体服务发起预约。</p>
 */
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { listGames, type Game } from '@/api/catalog'
import { listPublicCompanions, type PublicCompanionCard } from '@/api/companion'
import { useUserStore } from '@/stores/user'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

/** 入驻申请引导：非陪玩师（含游客）都能看到，点击后由路由守卫引导登录 */
const showApplyCta = computed(() => !userStore.hasRole('COMPANION'))

const games = ref<Game[]>([])
const gameId = ref<number | undefined>(undefined)
const companions = ref<PublicCompanionCard[]>([])
const total = ref(0)
const page = ref(1)
const pageSize = 8
const loading = ref(false)

const currentGameName = computed(() => games.value.find((g) => g.id === gameId.value)?.gameName ?? '全部游戏')

const totalPages = computed(() => Math.max(1, Math.ceil(total.value / pageSize)))

async function load() {
  loading.value = true
  try {
    const result = await listPublicCompanions(gameId.value, page.value, pageSize)
    companions.value = result.records
    total.value = result.total
  } finally {
    loading.value = false
  }
}

function onGameChange() {
  page.value = 1
  load()
}

/** 直达陪玩师公开主页（服务列表在主页首屏即可预约） */
function goServices(c: PublicCompanionCard) {
  router.push(`/companions/${c.userId}`)
}

function fmtPrice(cents: number): string {
  return '¥' + (cents / 100).toFixed(cents % 100 === 0 ? 0 : 2)
}

function initial(c: PublicCompanionCard): string {
  return c.displayName ? c.displayName.charAt(0) : '伴'
}

const SERVICE_STATUS: Record<PublicCompanionCard['serviceStatus'], { text: string; badge: string }> = {
  AVAILABLE: { text: '接单中', badge: 'badge-success' },
  BUSY: { text: '忙碌中', badge: 'badge-warning' },
  RESTING: { text: '休息中', badge: 'badge-secondary' },
  SUSPENDED: { text: '已暂停', badge: 'badge-destructive' },
}

function statusText(status: PublicCompanionCard['serviceStatus']): string {
  return SERVICE_STATUS[status]?.text ?? '休息中'
}

function statusBadge(status: PublicCompanionCard['serviceStatus']): string {
  return SERVICE_STATUS[status]?.badge ?? 'badge-secondary'
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
        <p class="page-subtitle">筛选心仪的陪玩伙伴 · 当前：{{ currentGameName }}（共 {{ total }} 位陪玩师）</p>
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
      <div v-else-if="companions.length" class="companion-grid">
        <div v-for="c in companions" :key="c.userId" class="companion-card" @click="goServices(c)">
          <div class="companion-header">
            <div class="companion-avatar">
              <el-image v-if="c.avatarUrl" :src="c.avatarUrl" fit="cover" loading="lazy">
                <template #error>
                  <span>{{ initial(c) }}</span>
                </template>
              </el-image>
              <span v-else>{{ initial(c) }}</span>
            </div>
            <div class="companion-meta">
              <div class="companion-name">
                {{ c.displayName }}
                <span class="badge" :class="statusBadge(c.serviceStatus)">{{ statusText(c.serviceStatus) }}</span>
              </div>
              <div class="companion-level">
                {{ c.gameNames.length ? c.gameNames.join(' · ') : '暂无在架服务' }}
              </div>
              <div class="companion-rating">
                <span style="color: #f59e0b">★</span>
                <span v-if="c.ratingCount">{{ c.ratingAvg?.toFixed(1) ?? '5.0' }}</span>
                <span v-else>暂无评分</span>
                <span class="text-muted">（{{ c.ratingCount }} 条评价）</span>
                <span class="text-muted">· 已完成 {{ c.completedOrderCount }} 单</span>
              </div>
            </div>
          </div>
          <div class="companion-footer">
            <div class="companion-price">
              <template v-if="c.startingPriceCents != null">{{ fmtPrice(c.startingPriceCents) }}<span>起/小时</span></template>
              <template v-else>暂无报价</template>
            </div>
            <div class="companion-actions">
              <span class="text-muted" style="font-size: 0.8125rem">{{ c.serviceCount }} 个在架服务</span>
              <button class="btn btn-primary btn-sm" @click.stop="goServices(c)">查看服务</button>
            </div>
          </div>
        </div>
      </div>

      <!-- 空态 -->
      <div v-else class="empty-state">
        <div class="empty-icon">🔍</div>
        <p>暂无符合条件的陪玩师，换个游戏看看吧</p>
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
