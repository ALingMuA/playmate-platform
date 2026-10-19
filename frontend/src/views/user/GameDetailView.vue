<script setup lang="ts">
/**
 * 游戏详情页（FR-U01/U02：按游戏维度浏览服务）。
 *
 * <p>展示游戏基本信息与该游戏下的公开可预约服务列表（仅审核通过且已上架）；
 * 服务卡片标注提供者（陪玩师名），支持立即预约或跳转陪玩师公开主页。</p>
 */
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getGame, type Game } from '@/api/catalog'
import { listBookableServices, type BookableService } from '@/api/order'

const route = useRoute()
const router = useRouter()

const gameId = computed(() => Number(route.params.gameId))

const game = ref<Game | null>(null)
const gameLoading = ref(true)

const services = ref<BookableService[]>([])
const total = ref(0)
const page = ref(1)
const pageSize = 8
const loading = ref(false)

const totalPages = computed(() => Math.max(1, Math.ceil(total.value / pageSize)))

async function loadGame() {
  if (!gameId.value) {
    game.value = null
    gameLoading.value = false
    return
  }
  gameLoading.value = true
  try {
    game.value = await getGame(gameId.value)
  } catch {
    game.value = null
  } finally {
    gameLoading.value = false
  }
}

async function loadServices() {
  if (!gameId.value) {
    services.value = []
    return
  }
  loading.value = true
  try {
    const result = await listBookableServices(gameId.value, undefined, page.value, pageSize)
    services.value = result.records
    total.value = result.total
  } finally {
    loading.value = false
  }
}

function onPageChange() {
  loadServices()
}

function goBook(service: BookableService) {
  router.push({
    path: '/booking',
    query: { companionUserId: String(service.companionUserId), serviceId: String(service.id) },
  })
}

/** 查看服务提供者的公开主页，直达"服务"Tab */
function goCompanion(service: BookableService) {
  router.push(`/companions/${service.companionUserId}#tab=services`)
}

function fmtPrice(cents: number): string {
  return '¥' + (cents / 100).toFixed(cents % 100 === 0 ? 0 : 2)
}

function gameInitial(g: Game | null): string {
  return g?.gameName ? g.gameName.charAt(0) : '游'
}

onMounted(() => {
  loadGame()
  loadServices()
})

watch(
  () => route.params.gameId,
  () => {
    page.value = 1
    loadGame()
    loadServices()
  },
)
</script>

<template>
  <div class="page">
    <div class="container">
      <!-- 返回游戏列表 -->
      <router-link to="/games" class="game-back-link">‹ 返回游戏列表</router-link>

      <!-- 加载中 -->
      <div v-if="gameLoading" class="empty-state">加载中…</div>

      <!-- 游戏不存在或已停用 -->
      <div v-else-if="!game" class="empty-state">
        <div class="empty-icon">🎮</div>
        <p>游戏不存在或已停用</p>
      </div>

      <template v-else>
        <!-- 游戏信息头 -->
        <div class="game-hero">
          <div class="game-hero-cover">
            <el-image v-if="game.gameIconUrl" :src="game.gameIconUrl" fit="cover" class="game-hero-image" loading="lazy">
              <template #error>
                <span class="game-hero-initial">{{ gameInitial(game) }}</span>
              </template>
            </el-image>
            <span v-else class="game-hero-initial">{{ gameInitial(game) }}</span>
          </div>
          <div class="game-hero-info">
            <h1 class="game-hero-name">{{ game.gameName }}</h1>
            <p class="game-hero-intro">{{ game.gameIntro || '暂无简介' }}</p>
            <p class="game-hero-meta">
              共 {{ total }} 个在架服务
            </p>
          </div>
        </div>

        <!-- 服务列表 -->
        <h2 class="section-title">该游戏的陪玩服务</h2>

        <!-- 加载中 -->
        <div v-if="loading" class="empty-state">加载中…</div>

        <div v-else-if="services.length" class="companion-grid">
          <div v-for="s in services" :key="s.id" class="companion-card">
            <div class="companion-header">
              <div class="companion-avatar" @click="goCompanion(s)" title="点击查看陪玩主页">
                <span>{{ s.companionDisplayName ? s.companionDisplayName.charAt(0) : '伴' }}</span>
              </div>
              <div class="companion-meta">
                <div class="companion-name">{{ s.title }}</div>
                <div class="companion-level">{{ s.companionDisplayName || '陪玩师' }} · {{ s.serviceTypeName }}</div>
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
              <div class="companion-actions">
                <button class="btn btn-outline btn-sm" @click.stop="goCompanion(s)">查看陪玩师</button>
                <button class="btn btn-primary btn-sm" @click.stop="goBook(s)">立即预约</button>
              </div>
            </div>
          </div>
        </div>

        <!-- 空态 -->
        <div v-else class="empty-state">
          <div class="empty-icon">🔍</div>
          <p>该游戏暂无在架服务，换个游戏看看吧</p>
        </div>

        <!-- 分页 -->
        <div v-if="totalPages > 1" class="pagination">
          <button class="page-link" :disabled="page <= 1" @click="page > 1 && (page -= 1, onPageChange())">‹</button>
          <button v-for="p in totalPages" :key="p" class="page-link" :class="{ active: p === page }" @click="page = p; onPageChange()">
            {{ p }}
          </button>
          <button class="page-link" :disabled="page >= totalPages" @click="page < totalPages && (page += 1, onPageChange())">›</button>
        </div>
      </template>
    </div>
  </div>
</template>

<style scoped>
.game-back-link {
  display: inline-block;
  font-size: 0.875rem;
  color: var(--muted-foreground);
  text-decoration: none;
  margin-bottom: 12px;
}
.game-back-link:hover {
  color: var(--primary);
}
.game-hero {
  display: flex;
  gap: 20px;
  align-items: center;
  background: var(--card);
  border: 1px solid var(--border);
  border-radius: var(--radius);
  padding: 20px;
  margin-bottom: 24px;
}
.game-hero-cover {
  width: 96px;
  height: 96px;
  border-radius: var(--radius);
  overflow: hidden;
  flex-shrink: 0;
  display: grid;
  place-items: center;
  background: linear-gradient(135deg, var(--secondary), #bfdbfe);
}
.game-hero-image {
  width: 100%;
  height: 100%;
}
.game-hero-initial {
  font-size: 2.25rem;
  font-weight: 700;
  color: var(--primary);
  user-select: none;
}
.game-hero-info {
  flex: 1;
  min-width: 0;
}
.game-hero-name {
  font-size: 1.375rem;
  font-weight: 700;
  color: var(--foreground);
  margin: 0 0 6px;
}
.game-hero-intro {
  font-size: 0.875rem;
  color: var(--muted-foreground);
  line-height: 1.6;
  margin: 0 0 6px;
}
.game-hero-meta {
  font-size: 0.8125rem;
  color: var(--primary);
  font-weight: 600;
  margin: 0;
}
.section-title {
  font-size: 1.125rem;
  font-weight: 700;
  color: var(--foreground);
  margin: 0 0 14px;
}
</style>
