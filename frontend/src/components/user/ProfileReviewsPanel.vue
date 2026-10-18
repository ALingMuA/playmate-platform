<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { myReviews, type Review } from '@/api/review'

const router = useRouter()
const rows = ref<Review[]>([])
const total = ref(0)
const page = ref(1)
const pageSize = 10
const loading = ref(false)
const failed = ref(false)

async function load() {
  loading.value = true
  failed.value = false
  try {
    const result = await myReviews(page.value, pageSize)
    rows.value = result.records
    total.value = result.total
  } catch {
    failed.value = true
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<template>
  <section v-loading="loading" aria-label="我的评价">
    <el-empty v-if="failed" description="评价暂时无法加载">
      <el-button type="primary" @click="load">重试</el-button>
    </el-empty>
    <div v-else-if="rows.length" class="profile-review-list">
      <article v-for="review in rows" :key="review.id" class="card profile-review-card">
        <div class="card-body">
          <div class="flex items-center justify-between flex-wrap gap-1">
            <strong>{{ review.score }} / 5 分</strong>
            <time class="text-muted">{{ review.createdAt?.slice(0, 10) }}</time>
          </div>
          <span v-if="review.displayStatus === 'HIDDEN'" class="badge badge-warning mt-1">仅自己可见</span>
          <p class="mt-1">{{ review.content || '未填写评价内容' }}</p>
          <div v-if="review.tags?.length" class="companion-tags mt-1">
            <span v-for="tag in review.tags" :key="tag" class="badge badge-outline">{{ tag }}</span>
          </div>
          <button class="btn btn-ghost btn-sm mt-1" @click="router.push(`/orders/${review.orderId}`)">
            查看关联订单
          </button>
        </div>
      </article>
    </div>
    <el-empty v-else-if="!loading" description="还没有评价" />
    <div v-if="total > pageSize" class="pagination">
      <button class="page-link" :disabled="page <= 1" @click="page--; load()">‹</button>
      <span class="page-link">{{ page }} / {{ Math.ceil(total / pageSize) }}</span>
      <button class="page-link" :disabled="page >= Math.ceil(total / pageSize)" @click="page++; load()">›</button>
    </div>
  </section>
</template>

<style scoped>
.profile-review-list { display: grid; gap: 12px; }
.profile-review-card { margin: 0; }
</style>
