<script setup lang="ts">
/**
 * 陪玩师列表（FR-U02/U03：查找可预约陪玩服务）。
 *
 * <p>展示审核通过且已上架的可预约服务，支持按游戏筛选与分页；
 * 从游戏列表页（GamesView）携带 gameId 进入时自动预选对应游戏。
 * 点击卡片可跳转订单页发起预约（FR-U07 创建预约订单）。</p>
 */
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { listGames, type Game } from '@/api/catalog'
import { listBookableServices, type BookableService } from '@/api/order'

const route = useRoute()
const router = useRouter()

/** 游戏下拉选项（全部游戏 + 各游戏） */
const games = ref<Game[]>([])
/** 当前筛选的游戏 ID（undefined 表示全部） */
const gameId = ref<number | undefined>(undefined)
/** 服务列表 */
const services = ref<BookableService[]>([])
const total = ref(0)
const page = ref(1)
const pageSize = 10
const loading = ref(false)

/** 当前筛选的游戏名（页头展示） */
const currentGameName = computed(() => games.value.find((g) => g.id === gameId.value)?.gameName ?? '全部游戏')

/** 拉取服务列表（按游戏过滤 + 分页） */
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

/** 跳转订单页并发起预约（携带服务与游戏参数，OrdersView 预选） */
function goBook(service: BookableService) {
  router.push({
    path: '/orders',
    query: { serviceId: String(service.id), gameId: String(service.gameId) },
  })
}

function fmtPrice(cents: number): string {
  return '¥' + (cents / 100).toFixed(cents % 100 === 0 ? 0 : 2)
}

onMounted(async () => {
  // 拉取游戏列表（筛选下拉）
  try {
    games.value = await listGames()
  } catch {
    games.value = []
  }
  // 从游戏列表页跳转进入时预选游戏
  const qGameId = Number(route.query.gameId)
  if (qGameId && games.value.some((g) => g.id === qGameId)) {
    gameId.value = qGameId
  }
  await load()
})

// 地址栏 gameId 变化（如从 GamesView 再次跳转）时同步筛选
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
  <div class="companions-page">
    <!-- 页头 -->
    <div class="page-header">
      <h1>陪玩师列表</h1>
      <p class="sub">当前浏览：{{ currentGameName }}，共 {{ total }} 个可预约服务</p>
    </div>

    <!-- 筛选栏 -->
    <div class="filter-bar">
      <el-select v-model="gameId" placeholder="全部游戏" clearable style="width: 220px" @change="onGameChange">
        <el-option v-for="g in games" :key="g.id" :label="g.gameName" :value="g.id" />
      </el-select>
      <el-button @click="onGameChange">查询</el-button>
    </div>

    <!-- 服务卡片网格 -->
    <div v-loading="loading" class="card-grid">
      <el-card v-for="s in services" :key="s.id" class="service-card" shadow="hover">
        <div class="card-head">
          <h3 class="title" :title="s.title">{{ s.title }}</h3>
          <el-tag size="small" type="primary" effect="plain">{{ s.gameName }}</el-tag>
          <el-tag size="small" type="info" effect="plain">{{ s.serviceTypeName }}</el-tag>
        </div>
        <p class="desc" v-if="s.description">{{ s.description }}</p>
        <p class="desc empty" v-else>该服务暂无介绍</p>
        <div class="tags" v-if="s.tagNames?.length">
          <el-tag v-for="t in s.tagNames" :key="t" size="small" type="success" effect="plain">{{ t }}</el-tag>
        </div>
        <div class="card-foot">
          <div class="price">
            <span class="price-num">{{ fmtPrice(s.priceCents) }}</span>
            <span class="price-unit">/小时</span>
          </div>
          <span class="duration">最短 {{ s.minDurationMinutes }} 分钟</span>
          <el-button type="primary" size="small" @click="goBook(s)">立即预约</el-button>
        </div>
      </el-card>
    </div>

    <!-- 空态与分页 -->
    <el-empty v-if="!loading && services.length === 0" description="暂无符合条件的陪玩服务，换个游戏看看吧" />
    <el-pagination
      v-if="total > pageSize"
      class="pager"
      layout="total, prev, pager, next"
      :total="total"
      :page-size="pageSize"
      :current-page="page"
      @current-change="(p: number) => { page = p; load() }"
    />
  </div>
</template>

<style scoped>
.companions-page {
  max-width: 960px;
  margin: 0 auto;
  padding: 24px 16px 48px;
}
.page-header h1 {
  margin: 0 0 8px;
}
.sub {
  color: #909399;
  margin: 0 0 20px;
}
.filter-bar {
  display: flex;
  gap: 12px;
  margin-bottom: 20px;
}
.card-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 16px;
  min-height: 200px;
}
.service-card {
  border-radius: 8px;
  overflow: hidden;
}
.card-head {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
}
.title {
  margin: 0 8px 0 0;
  font-size: 16px;
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.desc {
  margin: 10px 0 0;
  color: #606266;
  font-size: 13px;
  min-height: 20px;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.desc.empty {
  color: #c0c4cc;
}
.tags {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
  margin-top: 8px;
}
.card-foot {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-top: 12px;
  padding-top: 12px;
  border-top: 1px solid #f0f2f5;
}
.price {
  color: #f56c6c;
}
.price-num {
  font-size: 20px;
  font-weight: 700;
}
.price-unit {
  font-size: 12px;
  color: #909399;
}
.duration {
  flex: 1;
  font-size: 12px;
  color: #909399;
}
.pager {
  margin-top: 16px;
  justify-content: flex-end;
}
</style>
