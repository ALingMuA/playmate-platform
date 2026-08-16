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
      <div v-if="serviceTypes.length" class="tabs" style="border-bottom: none; padding-bottom: 0; margin-bottom: 8px">
        <span class="badge badge-secondary" style="align-self: center">服务类型</span>
        <span v-for="t in serviceTypes" :key="t.id" class="badge badge-outline">{{ t.typeName }}</span>
      </div>

      <!-- 加载中 -->
      <div v-if="loading" class="empty-state">加载中…</div>

      <!-- 游戏卡片网格（原型 game-grid） -->
      <div v-else-if="games.length" class="game-grid">
        <div v-for="game in games" :key="game.id" class="game-card" @click="goCompanions(game)">
          <div class="game-cover">
            <el-image v-if="game.gameIconUrl" :src="game.gameIconUrl" fit="cover">
              <template #error>
                <span>{{ gameInitial(game) }}</span>
              </template>
            </el-image>
            <span v-else>{{ gameInitial(game) }}</span>
          </div>
          <div class="game-info">
            <div class="game-name">{{ game.gameName }}</div>
            <div class="game-meta">{{ game.gameIntro || '暂无简介' }}</div>
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
