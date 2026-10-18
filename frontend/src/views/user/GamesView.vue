<script setup lang="ts">
/**
 * 游戏列表（对齐 frontend-prototype games.html：页头 + 服务类型 + 游戏卡片网格）。
 *
 * <p>展示已启用的游戏卡片（图标、名称、简介），并展示服务类型标签；
 * 点击卡片跳转对应游戏的陪玩师列表。数据来自 catalog 模块公开接口，游客可访问。</p>
 */
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { listGames, listServiceTypes, type Game, type ServiceType } from '@/api/catalog'

const router = useRouter()

const games = ref<Game[]>([])
const serviceTypes = ref<ServiceType[]>([])
const loading = ref(true)

async function loadData() {
  loading.value = true
  try {
    const [gameList, typeList] = await Promise.all([listGames(), listServiceTypes()])
    games.value = gameList
    serviceTypes.value = typeList
  } catch {
    games.value = []
  } finally {
    loading.value = false
  }
}

function goCompanions(game: Game) {
  router.push({ path: '/companions', query: { gameId: String(game.id) } })
}

function gameInitial(game: Game): string {
  return game.gameName ? game.gameName.charAt(0) : '游'
}

onMounted(loadData)
</script>

<template>
  <div class="page">
    <div class="container">
      <!-- 页头（原型 page-header） -->
      <div class="page-header">
        <h1 class="page-title">热门游戏</h1>
        <p class="page-subtitle">选择你喜欢的游戏，发现专属陪玩师</p>
      </div>

      <!-- 服务类型标签条 -->
      <div v-if="serviceTypes.length" class="service-types-bar">
        <span class="badge badge-secondary type-label">支持服务类型</span>
        <div class="type-pill-list">
          <span v-for="t in serviceTypes" :key="t.id" class="badge badge-outline type-pill">{{ t.typeName }}</span>
        </div>
      </div>

      <!-- 加载中 -->
      <div v-if="loading" class="empty-state">加载中…</div>

      <!-- 游戏卡片网格（原型 game-grid） -->
      <div v-else-if="games.length" class="game-grid">
        <div v-for="game in games" :key="game.id" class="game-card" @click="goCompanions(game)">
          <div class="game-cover">
            <el-image
              v-if="game.gameIconUrl"
              :src="game.gameIconUrl"
              fit="cover"
              class="game-cover-image"
              loading="lazy"
            >
              <template #error>
                <div class="game-cover-fallback">
                  <span class="game-initial">{{ gameInitial(game) }}</span>
                </div>
              </template>
            </el-image>
            <div v-else class="game-cover-fallback">
              <span class="game-initial">{{ gameInitial(game) }}</span>
            </div>
          </div>
          <div class="game-info">
            <div class="game-name">{{ game.gameName }}</div>
            <div class="game-meta">{{ game.gameIntro || '暂无简介' }}</div>
            <div class="game-card-footer">
              <span class="view-companions-btn">找TA陪玩 →</span>
            </div>
          </div>
        </div>
      </div>

      <!-- 空态 -->
      <div v-else class="empty-state">
        <div class="empty-icon">🎮</div>
        <p>暂无游戏，请联系管理员在后台添加</p>
      </div>
    </div>
  </div>
</template>

<style scoped>
.service-types-bar {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
  margin-bottom: 20px;
  padding: 10px 16px;
  background: var(--card);
  border: 1px solid var(--border);
  border-radius: var(--radius);
}
.type-label {
  font-weight: 600;
  white-space: nowrap;
}
.type-pill-list {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}
.type-pill {
  font-size: 0.8125rem;
  background: var(--card);
}
.game-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 20px;
}
.game-card {
  background: var(--card);
  border: 1px solid var(--border);
  border-radius: var(--radius);
  overflow: hidden;
  cursor: pointer;
  transition: transform 0.25s ease, box-shadow 0.25s ease, border-color 0.25s ease;
  display: flex;
  flex-direction: column;
}
.game-card:hover {
  transform: translateY(-4px);
  border-color: var(--primary);
  box-shadow: 0 10px 25px -5px rgba(0, 0, 0, 0.1), 0 8px 10px -6px rgba(0, 0, 0, 0.05);
}
.game-cover {
  height: 155px;
  position: relative;
  overflow: hidden;
  background: linear-gradient(135deg, var(--secondary), #bfdbfe);
}
.game-cover-image {
  width: 100%;
  height: 100%;
  display: block;
}
.game-cover-image :deep(img) {
  transition: transform 0.35s ease;
}
.game-card:hover .game-cover-image :deep(img) {
  transform: scale(1.06);
}
.game-cover-fallback {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, var(--secondary), #bfdbfe);
  color: var(--primary);
  font-size: 2.75rem;
  font-weight: 700;
  user-select: none;
}
.game-info {
  padding: 16px;
  display: flex;
  flex-direction: column;
  flex: 1;
}
.game-name {
  font-size: 1.125rem;
  font-weight: 700;
  color: var(--foreground);
  margin-bottom: 6px;
}
.game-meta {
  font-size: 0.875rem;
  color: var(--muted-foreground);
  line-height: 1.5;
  margin-bottom: 12px;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  flex: 1;
}
.game-card-footer {
  display: flex;
  justify-content: flex-end;
  align-items: center;
  border-top: 1px dashed var(--border);
  padding-top: 10px;
}
.view-companions-btn {
  font-size: 0.8125rem;
  font-weight: 600;
  color: var(--primary);
  transition: transform 0.2s ease;
}
.game-card:hover .view-companions-btn {
  transform: translateX(3px);
}
</style>
