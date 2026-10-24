<script setup lang="ts">
/**
 * 陪玩师公开主页的评价区：评分概况（含当前页样本星级分布）+ 公开评价列表。
 *
 * <p>评分展示遵循三级降级：平台汇总 → 当前加载样本 → 暂无；
 * 直方图仅为当前页样本分布，不代表全量。</p>
 */
import { computed } from 'vue'
import type { Review } from '@/api/review'

const props = defineProps<{
  reviews: Review[]
  total: number
  page: number
  pageSize: number
  loading: boolean
  error: string
  ratingValue: string | null
  ratingScope: string
  ratingCount: number
}>()

const emit = defineEmits<{ load: [page: number] }>()

/** 当前加载评价的星级样本分布（非全量统计） */
const sampleDistribution = computed(() => {
  const counts = [0, 0, 0, 0, 0]
  props.reviews.forEach((review) => {
    if (review.score >= 1 && review.score <= 5) counts[review.score - 1] += 1
  })
  return counts.map((count, index) => ({ score: index + 1, count }))
})

function fmtTime(value?: string): string {
  if (!value) return ''
  return value.length >= 10 ? value.slice(0, 10) : value
}
</script>

<template>
  <section class="card shop-block" aria-labelledby="companion-reviews-heading">
    <div class="card-body">
      <div class="flex items-center justify-between flex-wrap gap-1">
        <h2 id="companion-reviews-heading" class="card-title">玩家评价</h2>
        <span class="text-muted">共 {{ ratingCount }} 条</span>
      </div>

      <div class="rating-summary">
        <div class="rating-score">
          <strong>{{ ratingValue ?? '—' }}</strong>
          <span class="rating-stars" aria-hidden="true">★★★★★</span>
          <span class="rating-scope">{{ ratingScope }}</span>
        </div>
        <div v-if="reviews.length" class="rating-histogram" aria-label="当前页评价星级分布（样本）">
          <div v-for="item in [...sampleDistribution].reverse()" :key="item.score" class="histogram-row">
            <span>{{ item.score }} 星</span>
            <span class="histogram-track"><i :style="{ width: `${(item.count / reviews.length) * 100}%` }" /></span>
            <b>{{ item.count }}</b>
          </div>
          <p class="histogram-note">本页样本分布，非全量统计</p>
        </div>
      </div>

      <div v-if="loading && !reviews.length" class="block-loading" aria-live="polite">
        <el-skeleton :rows="4" animated />
      </div>
      <div v-else-if="error" class="block-error" role="alert">
        <p>{{ error }}</p>
        <button type="button" class="btn btn-outline btn-sm" @click="emit('load', page)">重新加载</button>
      </div>
      <div v-else-if="reviews.length" class="review-list" :aria-busy="loading">
        <article v-for="review in reviews" :key="review.id" class="review-item">
          <div class="review-avatar" aria-hidden="true">{{ review.userNickname?.trim().charAt(0) || '匿' }}</div>
          <div class="review-content">
            <div class="review-topline">
              <strong>{{ review.userNickname || '匿名玩家' }}</strong>
              <time :datetime="review.createdAt">{{ fmtTime(review.createdAt) }}</time>
            </div>
            <div class="review-score" :aria-label="`${review.score} 星`">
              <span v-for="star in 5" :key="star" :class="{ muted: star > review.score }">★</span>
              <span v-if="review.tags?.length" class="review-tags">{{ review.tags.join(' · ') }}</span>
            </div>
            <p v-if="review.content" class="review-text">{{ review.content }}</p>
            <p v-else class="review-text is-muted">未填写文字评价</p>
          </div>
        </article>
      </div>
      <div v-else class="block-empty">
        <p>还没有公开评价，完成服务并提交的公开评价会显示在这里。</p>
      </div>

      <el-pagination
        v-if="total > pageSize"
        class="block-pagination"
        background
        layout="prev, pager, next"
        :total="total"
        :page-size="pageSize"
        :current-page="page"
        :disabled="loading"
        @current-change="(value: number) => emit('load', value)"
      />
    </div>
  </section>
</template>

<style scoped>
.shop-block { margin: 0; }
.block-loading { padding: 6px 0; }
.block-error { display: grid; gap: 10px; padding: 18px 0; color: var(--muted-foreground); }
.block-error p { margin: 0; }
.block-empty { padding: 26px 0; text-align: center; color: var(--muted-foreground); }
.block-pagination { margin-top: 16px; justify-content: center; }

.rating-summary { display: flex; gap: 22px; align-items: center; flex-wrap: wrap; padding: 14px; margin: 12px 0 16px; border-radius: var(--radius); background: var(--muted); }
.rating-score { display: grid; gap: 4px; min-width: 130px; }
.rating-score strong { font-size: 30px; line-height: 1; }
.rating-stars { color: #f5a623; letter-spacing: 2px; font-size: 13px; }
.rating-scope { font-size: 12px; color: var(--muted-foreground); }
.rating-histogram { flex: 1 1 240px; display: grid; gap: 4px; }
.histogram-row { display: grid; grid-template-columns: 32px 1fr 18px; align-items: center; gap: 8px; font-size: 12px; color: var(--muted-foreground); }
.histogram-track { height: 6px; border-radius: 999px; background: var(--border); overflow: hidden; }
.histogram-track i { display: block; height: 100%; border-radius: inherit; background: #f5a623; }
.histogram-note { margin: 2px 0 0; font-size: 11px; color: var(--muted-foreground); }

.review-list { display: grid; }
.review-item { display: flex; gap: 12px; padding: 14px 0; border-bottom: 1px solid var(--border); }
.review-item:last-of-type { border-bottom: 0; }
.review-avatar { width: 38px; height: 38px; flex: 0 0 auto; display: grid; place-items: center; border-radius: 50%; background: var(--secondary); color: var(--secondary-foreground); font-weight: 700; }
.review-content { flex: 1 1 auto; min-width: 0; display: grid; gap: 5px; }
.review-topline { display: flex; justify-content: space-between; align-items: baseline; gap: 10px; }
.review-topline time { font-size: 12px; color: var(--muted-foreground); }
.review-score { display: flex; align-items: center; gap: 2px; color: #f5a623; font-size: 13px; }
.review-score .muted { color: var(--border); }
.review-tags { margin-left: 8px; color: var(--muted-foreground); font-size: 12px; }
.review-text { margin: 0; font-size: 14px; line-height: 1.6; }
.review-text.is-muted { color: var(--muted-foreground); }
</style>
