<script setup lang="ts">
/**
 * 陪玩师公开主页的未来七日可约时间卡。
 *
 * <p>点击时段即携带开始时间进入预约页（服务在预约页选择），
 * 无需回到服务区先做选择。</p>
 */
import type { PublicAvailability } from '@/api/companion'

defineProps<{
  slots: PublicAvailability[]
  loading: boolean
  error: string
  /** 陪玩师可接单时才展示档期 */
  serviceAvailable: boolean
  statusText: string
}>()

const emit = defineEmits<{
  load: []
  select: [startAt: string]
}>()
</script>

<template>
  <section class="card shop-block" aria-labelledby="companion-availability-heading">
    <div class="card-body">
      <div class="flex items-center justify-between flex-wrap gap-1">
        <h2 id="companion-availability-heading" class="card-title">未来七日可约时间</h2>
        <span v-if="!serviceAvailable" class="badge badge-outline">{{ statusText }}</span>
      </div>

      <div v-if="!serviceAvailable" class="block-empty">
        <p>陪玩师当前{{ statusText }}，暂不展示可约时间。</p>
      </div>
      <div v-else-if="loading && !slots.length" class="block-loading" aria-live="polite">
        <el-skeleton :rows="2" animated />
      </div>
      <div v-else-if="error" class="block-error" role="alert">
        <p>{{ error }}</p>
        <button type="button" class="btn btn-outline btn-sm" @click="emit('load')">重新加载</button>
      </div>
      <div v-else-if="slots.length" class="slot-grid">
        <button
          v-for="slot in slots"
          :key="slot.startAt"
          type="button"
          class="slot-item"
          @click="emit('select', slot.startAt)"
        >
          <span class="slot-date">{{ slot.startAt.slice(0, 10) }}</span>
          <strong class="slot-time">{{ slot.startAt.slice(11, 16) }} - {{ slot.endAt.slice(11, 16) }}</strong>
          <span class="slot-action">选这个时间</span>
        </button>
      </div>
      <div v-else class="block-empty">
        <p>未来七日暂未设置可约时间。</p>
      </div>
    </div>
  </section>
</template>

<style scoped>
.shop-block { margin: 0; }
.block-loading { padding: 6px 0; }
.block-error { display: grid; gap: 10px; padding: 18px 0; color: var(--muted-foreground); }
.block-error p { margin: 0; }
.block-empty { padding: 26px 0; text-align: center; color: var(--muted-foreground); }

.slot-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 10px; margin-top: 12px; }
.slot-item { display: grid; gap: 4px; padding: 12px 14px; border: 1px solid var(--border); border-radius: var(--radius); background: var(--card); text-align: left; cursor: pointer; transition: border-color 150ms ease, background-color 150ms ease; }
.slot-item:hover, .slot-item:focus-visible { border-color: var(--primary); background: color-mix(in srgb, var(--primary) 5%, var(--card)); }
.slot-date { font-size: 12px; color: var(--muted-foreground); }
.slot-time { font-size: 15px; }
.slot-action { font-size: 12px; color: var(--primary); }

@media (max-width: 640px) {
  .slot-grid { grid-template-columns: 1fr; }
}
</style>
