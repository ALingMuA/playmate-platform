<script setup lang="ts">
/**
 * 游戏列表（对齐 docs/06-原型与架构图/frontend-prototype/games.html：页头 + 服务类型 + 游戏卡片网格）。
 *
 * <p>展示已启用的游戏卡片（图标、名称、简介），并展示服务类型标签；
 * 点击卡片跳转对应游戏的陪玩师列表。数据来自 catalog 模块公开接口，游客可访问。</p>
 */
import { onMounted, ref } from 'vue'
import { listGames, listServiceTypes, type Game, type ServiceType } from '@/api/catalog'

const games = ref<Game[]>([])
const serviceTypes = ref<ServiceType[]>([])
const loading = ref(true)
const failed = ref(false)
const errorMessage = ref('')

async function loadData() {
  loading.value = true
  failed.value = false
  errorMessage.value = ''
  const [gamesResult, typesResult] = await Promise.allSettled([listGames(), listServiceTypes()])
  if (gamesResult.status === 'fulfilled') {
    games.value = gamesResult.value
  } else {
    failed.value = true
    errorMessage.value = gamesResult.reason instanceof Error ? gamesResult.reason.message : '游戏目录加载失败，请重试。'
  }
  if (typesResult.status === 'fulfilled') {
    serviceTypes.value = typesResult.value
  } else if (!failed.value) {
    failed.value = true
    errorMessage.value = typesResult.reason instanceof Error ? typesResult.reason.message : '服务类型加载失败，请重试。'
  }
  if (failed.value) {
    // 保留任一成功请求的数据，页面错误态只覆盖受影响的列表状态。
  }
  loading.value = false
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

      <div v-if="failed && games.length" class="error-banner" role="alert">
        {{ errorMessage }}
        <button class="btn btn-outline btn-sm" @click="loadData">重试</button>
      </div>

      <!-- 加载中 -->
      <div v-if="loading" class="empty-state">加载中…</div>

      <div v-else-if="failed && games.length === 0" class="empty-state" role="alert">
        <p>{{ errorMessage }}</p>
        <button class="btn btn-primary" @click="loadData">重新加载</button>
      </div>

      <!-- 游戏卡片网格（原型 game-grid） -->
      <div v-else-if="games.length" class="game-grid">
        <router-link v-for="game in games" :key="game.id" :to="`/games/${game.id}`" class="game-card">
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
              <span class="view-companions-btn">查看服务 →</span>
            </div>
          </div>
        </router-link>
      </div>

      <!-- 空态 -->
      <div v-else-if="!failed" class="empty-state">
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
  display: block;
  color: inherit;
  text-decoration: none;
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
