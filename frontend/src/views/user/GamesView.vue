<script setup lang="ts">
/**
 * 游戏列表页（FR-U01 游戏分类浏览）。
 *
 * <p>展示已启用的游戏卡片（图标、名称、简介），并展示服务类型筛选条；
 * 点击卡片跳转对应游戏的陪玩师列表。数据来自 catalog 模块公开接口，游客可访问。</p>
 */
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { listGames, listServiceTypes, type Game, type ServiceType } from '@/api/catalog'

const router = useRouter()

/** 游戏列表 */
const games = ref<Game[]>([])
/** 服务类型列表 */
const serviceTypes = ref<ServiceType[]>([])
/** 是否加载中 */
const loading = ref(true)

/** 拉取游戏与服务类型数据（并行请求） */
async function loadData() {
  loading.value = true
  try {
    const [gameList, typeList] = await Promise.all([listGames(), listServiceTypes()])
    games.value = gameList
    serviceTypes.value = typeList
  } catch {
    // 请求失败时由 http.ts 统一弹出错误提示，这里仅清空数据
    games.value = []
  } finally {
    loading.value = false
  }
}

/** 点击游戏卡片：跳转对应游戏的陪玩师列表 */
function goCompanions(game: Game) {
  router.push({ path: '/companions', query: { gameId: String(game.id) } })
}

/** 游戏无图标时展示名称首字符占位 */
function gameInitial(game: Game): string {
  return game.gameName ? game.gameName.charAt(0) : '游'
}

onMounted(loadData)
</script>

<template>
  <div class="games-page">
    <!-- 页头 -->
    <div class="page-header">
      <h1>游戏列表</h1>
      <p class="sub">选择游戏，查找心仪的陪玩师与服务</p>
    </div>

    <!-- 服务类型筛选条（静态展示，陪玩师列表页将按此筛选） -->
    <div v-if="serviceTypes.length" class="type-bar">
      <el-tag
        v-for="t in serviceTypes"
        :key="t.id"
        class="type-tag"
        type="info"
        effect="plain"
      >
        {{ t.typeName }}
      </el-tag>
    </div>

    <!-- 加载中 -->
    <div v-if="loading" v-loading="loading" class="loading-box" />

    <!-- 游戏卡片网格 -->
    <el-row v-else-if="games.length" :gutter="16">
      <el-col v-for="game in games" :key="game.id" :xs="12" :sm="8" :md="6">
        <el-card class="game-card" shadow="hover" @click="goCompanions(game)">
          <div class="game-cover">
            <!-- 有图标展示图标，无图标以名称首字占位 -->
            <el-image
              v-if="game.gameIconUrl"
              :src="game.gameIconUrl"
              fit="cover"
              class="game-img"
            >
              <template #error>
                <div class="img-fallback">{{ gameInitial(game) }}</div>
              </template>
            </el-image>
            <div v-else class="img-fallback">{{ gameInitial(game) }}</div>
          </div>
          <div class="game-info">
            <h3 class="game-name">{{ game.gameName }}</h3>
            <p class="game-intro">{{ game.gameIntro || '暂无简介' }}</p>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 空态 -->
    <el-empty v-else description="暂无游戏，请联系管理员在后台添加" />
  </div>
</template>

<style scoped>
.games-page {
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
/* 服务类型筛选条 */
.type-bar {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 24px;
}
.type-tag {
  font-size: 13px;
}
/* 加载占位 */
.loading-box {
  min-height: 240px;
}
/* 游戏卡片 */
.game-card {
  cursor: pointer;
  margin-bottom: 16px;
  border-radius: 8px;
  overflow: hidden;
}
.game-cover {
  height: 140px;
  background: linear-gradient(135deg, #1f2d3d, #3a5a80);
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
}
.game-img {
  width: 100%;
  height: 100%;
}
.img-fallback {
  color: #fff;
  font-size: 48px;
  font-weight: 600;
  user-select: none;
}
.game-info {
  padding: 12px;
}
.game-name {
  margin: 0 0 6px;
  font-size: 16px;
}
.game-intro {
  margin: 0;
  color: #909399;
  font-size: 13px;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
</style>
