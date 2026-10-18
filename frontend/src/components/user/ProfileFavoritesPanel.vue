<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { listFavorites, removeFavorite, type FavoriteView } from '@/api/favorite'

const router = useRouter()
const rows = ref<FavoriteView[]>([])
const total = ref(0)
const page = ref(1)
const pageSize = 10
const loading = ref(false)
const removing = ref<number | null>(null)
const failed = ref(false)

async function load() {
  loading.value = true
  failed.value = false
  try {
    const result = await listFavorites(page.value, pageSize)
    rows.value = result.records
    total.value = result.total
  } catch {
    failed.value = true
  } finally {
    loading.value = false
  }
}

async function cancelFavorite(row: FavoriteView) {
  if (removing.value !== null) return
  const previousRows = [...rows.value]
  const previousTotal = total.value
  const previousPage = page.value
  rows.value = rows.value.filter((item) => item.companionUserId !== row.companionUserId)
  total.value = Math.max(0, total.value - 1)
  removing.value = row.companionUserId
  try {
    await removeFavorite(row.companionUserId)
    ElMessage.success('已取消收藏')
    if (!rows.value.length && page.value > 1) page.value -= 1
    await load()
  } catch {
    rows.value = previousRows
    total.value = previousTotal
    page.value = previousPage
  } finally {
    removing.value = null
  }
}

function statusText(status: FavoriteView['serviceStatus']) {
  return ({
    AVAILABLE: '可接单',
    BUSY: '忙碌',
    RESTING: '休息中',
    SUSPENDED: '暂停接单',
  } as Record<string, string>)[status ?? ''] ?? '状态未知'
}

onMounted(load)
</script>

<template>
  <section v-loading="loading" aria-label="我的收藏">
    <el-empty v-if="failed" description="收藏暂时无法加载">
      <el-button type="primary" @click="load">重试</el-button>
    </el-empty>
    <div v-else-if="rows.length" class="profile-favorite-grid">
      <article v-for="row in rows" :key="row.companionUserId" class="card profile-favorite-card">
        <div class="card-body">
          <div class="flex items-center justify-between gap-1">
            <h3 class="card-title">{{ row.displayName || `陪玩师 #${row.companionUserId}` }}</h3>
            <span class="badge badge-outline">{{ statusText(row.serviceStatus) }}</span>
          </div>
          <p class="text-muted mt-1 profile-favorite-intro">{{ row.profileIntro || '暂无主页简介' }}</p>
          <p class="text-muted mt-1">
            评分 {{ row.ratingAvg ?? '暂无' }} · {{ row.ratingCount ?? 0 }} 条评价
          </p>
          <div class="flex gap-1 mt-2">
            <button class="btn btn-primary btn-sm" @click="router.push(`/companions/${row.companionUserId}`)">
              查看主页
            </button>
            <button class="btn btn-outline btn-sm" :disabled="removing === row.companionUserId"
              @click="cancelFavorite(row)">
              取消收藏
            </button>
          </div>
        </div>
      </article>
    </div>
    <el-empty v-else-if="!loading" description="还没有收藏的陪玩师">
      <el-button type="primary" @click="router.push('/companions')">去找陪玩</el-button>
    </el-empty>
    <div v-if="total > pageSize" class="pagination">
      <button class="page-link" :disabled="page <= 1" @click="page--; load()">‹</button>
      <span class="page-link">{{ page }} / {{ Math.ceil(total / pageSize) }}</span>
      <button class="page-link" :disabled="page >= Math.ceil(total / pageSize)" @click="page++; load()">›</button>
    </div>
  </section>
</template>

<style scoped>
.profile-favorite-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 12px; }
.profile-favorite-card { margin: 0; min-width: 0; }
.profile-favorite-intro { min-height: 2.7em; overflow-wrap: anywhere; }
@media (max-width: 640px) { .profile-favorite-grid { grid-template-columns: 1fr; } }
</style>
