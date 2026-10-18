<script setup lang="ts">
import type { DaySchedule, VisualSegment } from '@/utils/schedule'
import DayRow from './DayRow.vue'
import ScheduleLegend from './ScheduleLegend.vue'

defineProps<{
  weekTitle: string
  daySchedules: DaySchedule[]
  canPrev: boolean
  canNext: boolean
  isCurrentWeek: boolean
  hasActiveRoutine: boolean
  generatedUntil?: string
}>()

const emit = defineEmits<{
  (e: 'prev-week'): void
  (e: 'next-week'): void
  (e: 'jump-today'): void
  (e: 'toggle-off', dateStr: string, isOff: boolean): void
  (e: 'edit-day', dateStr: string): void
  (e: 'restore-day', dateStr: string): void
  (e: 'segment-click', segment: VisualSegment): void
}>()
</script>

<template>
  <el-card class="week-board-card" shadow="never">
    <!-- 看板头部导航与状态 -->
    <template #header>
      <div class="board-header">
        <div class="nav-controls">
          <div class="btn-group">
            <el-button
              size="small"
              :disabled="!canPrev"
              @click="emit('prev-week')"
            >
              &lt; 上一周
            </el-button>
            <el-button
              size="small"
              :disabled="isCurrentWeek"
              @click="emit('jump-today')"
            >
              今天
            </el-button>
            <el-button
              size="small"
              :disabled="!canNext"
              @click="emit('next-week')"
            >
              下一周 &gt;
            </el-button>
          </div>
          <span class="week-title">{{ weekTitle }}</span>
          <el-tag v-if="hasActiveRoutine" type="success" effect="light" size="small">
            常规作息已生效
          </el-tag>
          <el-tag v-else type="info" effect="light" size="small">
            未启用常规作息
          </el-tag>
          <span v-if="generatedUntil" class="generated-until-tip">
            已安排至 {{ generatedUntil }}（每日自动续期）
          </span>
        </div>

        <ScheduleLegend />
      </div>
    </template>

    <!-- 24小时刻度标尺轴 -->
    <div class="timeline-axis-header">
      <div class="axis-left-space"></div>
      <div class="axis-track">
        <span class="axis-label tick-0">00:00</span>
        <span class="axis-label tick-6">06:00</span>
        <span class="axis-label tick-12">12:00</span>
        <span class="axis-label tick-18">18:00</span>
        <span class="axis-label tick-24">24:00</span>
      </div>
      <div class="axis-right-space"></div>
    </div>

    <!-- 7天列表 -->
    <div class="days-list">
      <DayRow
        v-for="day in daySchedules"
        :key="day.date"
        :day-schedule="day"
        @toggle-off="(dateStr, isOff) => emit('toggle-off', dateStr, isOff)"
        @edit-day="emit('edit-day', $event)"
        @restore-day="emit('restore-day', $event)"
        @segment-click="emit('segment-click', $event)"
      />
    </div>
  </el-card>
</template>

<style scoped>
.week-board-card {
  border-radius: 8px;
  border: 1px solid #ebeef5;
  background-color: #fff;
}

:deep(.el-card__header) {
  padding: 14px 20px;
  background-color: #fafbfc;
}

:deep(.el-card__body) {
  padding: 0;
}

.board-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 12px;
}

.nav-controls {
  display: flex;
  align-items: center;
  gap: 12px;
}

.week-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.generated-until-tip {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

/* 时间轴刻度 */
.timeline-axis-header {
  display: flex;
  align-items: center;
  height: 28px;
  background-color: #f7f8fa;
  border-bottom: 1px solid #ebeef5;
  padding: 0 16px;
}

.axis-left-space {
  width: 90px;
  flex-shrink: 0;
}

.axis-track {
  flex: 1;
  position: relative;
  height: 100%;
}

.axis-label {
  position: absolute;
  top: 50%;
  transform: translateY(-50%);
  font-size: 11px;
  color: var(--el-text-color-secondary);
}

.axis-label.tick-0 {
  left: 0;
}

.axis-label.tick-6 {
  left: 25%;
  transform: translate(-50%, -50%);
}

.axis-label.tick-12 {
  left: 50%;
  transform: translate(-50%, -50%);
}

.axis-label.tick-18 {
  left: 75%;
  transform: translate(-50%, -50%);
}

.axis-label.tick-24 {
  right: 0;
}

.axis-right-space {
  width: 250px;
  flex-shrink: 0;
}

.days-list {
  display: flex;
  flex-direction: column;
}
</style>
