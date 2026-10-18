<script setup lang="ts">
import type { VisualSegment } from '@/utils/schedule'

defineProps<{
  segments: VisualSegment[]
  isPast?: boolean
  isToday?: boolean
}>()

const emit = defineEmits<{
  (e: 'segment-click', segment: VisualSegment): void
}>()

function getTooltipContent(seg: VisualSegment): string {
  const kindNames: Record<string, string> = {
    available: '可约时段',
    booked: '已约订单',
    unavailable: '休息 / 不可约',
  }
  let title = `[${kindNames[seg.kind] || '档期'}] ${seg.startTimeText} - ${seg.endTimeText} (${seg.durationMinutes}分钟)`
  if (seg.isExpired) {
    title += ' · 已过期'
  }
  if (seg.booking) {
    title += `\n客户: ${seg.booking.customerNickname || '用户'} | 服务: ${seg.booking.serviceTitle}`
  } else if (seg.remark) {
    title += `\n备注: ${seg.remark}`
  }
  return title
}
</script>

<template>
  <div class="segment-bar-container">
    <div class="segment-bar-track">
      <!-- 24小时刻度参考竖线（06:00, 12:00, 18:00） -->
      <div class="track-tick tick-6"></div>
      <div class="track-tick tick-12"></div>
      <div class="track-tick tick-18"></div>

      <!-- 片段列表 -->
      <template v-for="seg in segments" :key="seg.id">
        <el-tooltip :content="getTooltipContent(seg)" placement="top" :show-after="100">
          <div
            class="segment-block"
            :class="[
              `kind-${seg.kind}`,
              {
                'is-expired': seg.isExpired,
                'is-partial': seg.isPartiallyExpired,
              },
            ]"
            :style="{
              left: `${seg.leftPercent}%`,
              width: `${seg.widthPercent}%`,
            }"
            @click="emit('segment-click', seg)"
          >
            <!-- 文本提示，仅当宽度足够（>= 5%）时显示 -->
            <span v-if="seg.widthPercent >= 6" class="segment-label">
              {{ seg.startTimeText }}
            </span>

            <!-- 部分过期阴影层 -->
            <div
              v-if="seg.isPartiallyExpired && seg.expiredWidthPercent"
              class="partial-expired-overlay"
              :style="{ width: `${seg.expiredWidthPercent}%` }"
            ></div>
          </div>
        </el-tooltip>
      </template>
    </div>
  </div>
</template>

<style scoped>
.segment-bar-container {
  width: 100%;
  position: relative;
}

.segment-bar-track {
  position: relative;
  height: 28px;
  background-color: #f2f3f5;
  border-radius: 4px;
  overflow: hidden;
  box-shadow: inset 0 1px 2px rgba(0, 0, 0, 0.05);
}

.track-tick {
  position: absolute;
  top: 0;
  bottom: 0;
  width: 1px;
  background-color: rgba(0, 0, 0, 0.06);
  pointer-events: none;
  z-index: 1;
}

.tick-6 {
  left: 25%;
}

.tick-12 {
  left: 50%;
  border-left: 1px dashed rgba(0, 0, 0, 0.12);
  width: 0;
}

.tick-18 {
  left: 75%;
}

.segment-block {
  position: absolute;
  top: 2px;
  bottom: 2px;
  border-radius: 3px;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  cursor: pointer;
  z-index: 2;
  transition: filter 0.15s ease, transform 0.15s ease;
  user-select: none;
  box-sizing: border-box;
}

.segment-block:hover {
  filter: brightness(1.08);
  transform: translateY(-1px);
  z-index: 3;
}

/* 三态颜色 */
.segment-block.kind-available {
  background-color: #2ca66f;
  border: 1px solid #238b5c;
  color: #fff;
}

.segment-block.kind-booked {
  background-color: #7659c9;
  border: 1px solid #5e43a9;
  color: #fff;
}

.segment-block.kind-unavailable {
  background-color: #5f6b7a;
  border: 1px solid #4b5563;
  color: #fff;
  background-image: repeating-linear-gradient(
    -45deg,
    rgba(255, 255, 255, 0.15),
    rgba(255, 255, 255, 0.15) 3px,
    transparent 3px,
    transparent 6px
  );
}

/* 过期纹理 */
.segment-block.is-expired {
  opacity: 0.45;
  filter: grayscale(0.6);
  cursor: not-allowed;
  background-image: repeating-linear-gradient(
    -45deg,
    rgba(255, 255, 255, 0.35),
    rgba(255, 255, 255, 0.35) 4px,
    transparent 4px,
    transparent 8px
  );
}

.partial-expired-overlay {
  position: absolute;
  top: 0;
  left: 0;
  bottom: 0;
  background-image: repeating-linear-gradient(
    -45deg,
    rgba(0, 0, 0, 0.2),
    rgba(0, 0, 0, 0.2) 3px,
    transparent 3px,
    transparent 6px
  );
  pointer-events: none;
}

.segment-label {
  font-size: 11px;
  font-weight: 500;
  line-height: 1;
  padding: 0 2px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
</style>
