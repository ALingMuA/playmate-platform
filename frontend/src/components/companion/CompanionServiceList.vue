<script setup lang="ts">
/**
 * 陪玩师公开主页的可预约服务列表。
 *
 * <p>每张服务卡直接携带「立即预约」入口，点击即携带服务参数进入预约页，
 * 取代旧的"先选中卡片再经工具条预约"两步动线。</p>
 */
import type { BookableService } from '@/api/order'

defineProps<{
  services: BookableService[]
  total: number
  page: number
  pageSize: number
  loading: boolean
  error: string
  /** 陪玩师处于可接单状态时才允许发起预约 */
  bookable: boolean
  /** 接单状态文案（不可预约时用于提示） */
  statusText: string
}>()

const emit = defineEmits<{
  load: [page: number]
  book: [service: BookableService]
}>()

function fmtPrice(cents: number): string {
  return '¥' + (cents / 100).toFixed(cents % 100 === 0 ? 0 : 2)
}
</script>

<template>
  <section class="card shop-block" aria-labelledby="companion-services-heading">
    <div class="card-body">
      <div class="flex items-center justify-between flex-wrap gap-1">
        <h2 id="companion-services-heading" class="card-title">可预约服务</h2>
        <span class="text-muted">共 {{ total }} 项</span>
      </div>
      <p class="text-muted block-hint">
        {{ bookable ? '选择服务直接进入预约，服务时间由预约页与服务端再次确认。' : `陪玩师当前${statusText}，暂不可发起预约。` }}
      </p>

      <div v-if="loading && !services.length" class="block-loading" aria-live="polite">
        <el-skeleton :rows="4" animated />
      </div>
      <div v-else-if="error" class="block-error" role="alert">
        <p>{{ error }}</p>
        <button type="button" class="btn btn-outline btn-sm" @click="emit('load', page)">重新加载</button>
      </div>
      <div v-else-if="services.length" class="service-list" :aria-busy="loading">
        <article v-for="service in services" :key="service.id" class="service-item">
          <div class="service-icon" aria-hidden="true">{{ service.gameName?.trim().charAt(0) || '游' }}</div>
          <div class="service-info">
            <span class="service-meta">{{ service.gameName }} · {{ service.serviceTypeName }}</span>
            <h3>{{ service.title }}</h3>
            <p v-if="service.description" class="service-desc">{{ service.description }}</p>
            <div v-if="service.tagNames?.length" class="service-tags">
              <span v-for="tag in service.tagNames" :key="tag" class="badge badge-outline">{{ tag }}</span>
            </div>
          </div>
          <div class="service-aside">
            <strong class="service-price">{{ fmtPrice(service.priceCents) }}<small> / 小时</small></strong>
            <span class="service-duration">{{ service.minDurationMinutes }} 分钟起</span>
            <button
              type="button"
              class="btn btn-primary btn-sm"
              :disabled="!bookable"
              @click="emit('book', service)"
            >
              {{ bookable ? '立即预约' : '暂不可约' }}
            </button>
          </div>
        </article>
      </div>
      <div v-else class="block-empty">
        <p>暂时没有可预约服务，仍可浏览下方可约时间与公开评价。</p>
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
.block-hint { margin: 6px 0 14px; font-size: 13px; }
.block-loading { padding: 6px 0; }
.block-error { display: grid; gap: 10px; padding: 18px 0; color: var(--muted-foreground); }
.block-error p { margin: 0; }
.block-empty { padding: 26px 0; text-align: center; color: var(--muted-foreground); }
.block-pagination { margin-top: 16px; justify-content: center; }

.service-list { display: grid; gap: 12px; }
.service-item { display: flex; align-items: center; gap: 14px; padding: 14px; border: 1px solid var(--border); border-radius: var(--radius); background: var(--card); transition: border-color 150ms ease, box-shadow 150ms ease; }
.service-item:hover { border-color: color-mix(in srgb, var(--primary) 45%, var(--border)); box-shadow: var(--shadow-hover); }
.service-icon { width: 46px; height: 46px; flex: 0 0 auto; display: grid; place-items: center; border-radius: 12px; background: var(--secondary); color: var(--secondary-foreground); font-size: 19px; font-weight: 700; }
.service-info { flex: 1 1 auto; min-width: 0; display: grid; gap: 5px; }
.service-meta { font-size: 12px; color: var(--muted-foreground); }
.service-info h3 { margin: 0; font-size: 15px; line-height: 1.35; }
.service-desc { margin: 0; font-size: 13px; color: var(--muted-foreground); display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden; }
.service-tags { display: flex; gap: 6px; flex-wrap: wrap; }
.service-aside { flex: 0 0 auto; display: grid; gap: 5px; justify-items: end; align-content: center; }
.service-price { font-size: 18px; color: var(--primary); }
.service-price small { font-size: 12px; font-weight: 400; color: var(--muted-foreground); }
.service-duration { font-size: 12px; color: var(--muted-foreground); }

@media (max-width: 640px) {
  .service-item { flex-wrap: wrap; }
  .service-aside { width: 100%; flex-direction: row; display: flex; align-items: center; justify-content: space-between; justify-items: start; }
}
</style>
