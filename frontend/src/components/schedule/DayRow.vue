<script setup lang="ts">
import { computed } from 'vue'
import type { DaySchedule, VisualSegment } from '@/utils/schedule'
import SegmentBar from './SegmentBar.vue'

const props = defineProps<{
  daySchedule: DaySchedule
}>()

const emit = defineEmits<{
  (e: 'toggle-off', dateStr: string, isOff: boolean): void
  (e: 'edit-day', dateStr: string): void
  (e: 'restore-day', dateStr: string): void
  (e: 'segment-click', segment: VisualSegment): void
}>()

const dateDisplay = computed(() => {
  return props.daySchedule.dayjsObj.format('MM/DD')
})
</script>

<template>
  <div
    class="day-row"
    :class="{
      'is-today': daySchedule.isToday,
      'is-past': daySchedule.isPast,
      'is-off': daySchedule.isAllDayOff,
    }"
  >
    <!-- 左侧日期信息 -->
    <div class="date-col">
      <div class="day-name-box">
        <span class="day-name">{{ daySchedule.dayName }}</span>
        <el-tag v-if="daySchedule.isToday" size="small" type="primary" effect="dark" class="today-tag">今日</el-tag>
        <el-tag v-else-if="daySchedule.isPast" size="small" type="info" effect="plain" class="past-tag">已过</el-tag>
      </div>
      <div class="date-num">{{ dateDisplay }}</div>
    </div>

    <!-- 中间 24小时可视化进度条 -->
    <div class="bar-col">
      <SegmentBar
        :segments="daySchedule.segments"
        :is-past="daySchedule.isPast"
        :is-today="daySchedule.isToday"
        @segment-click="emit('segment-click', $event)"
      />
    </div>

    <!-- 右侧状态统计与快捷操作 -->
    <div class="summary-col">
      <div class="summary-info">
        <span class="summary-text" :class="{ 'text-muted': daySchedule.isPast }">
          {{ daySchedule.summaryText }}
        </span>
        <el-tag
          v-if="daySchedule.hasException && !daySchedule.isPast"
          size="small"
          type="warning"
          effect="light"
          class="exception-tag"
        >
          临时调整
        </el-tag>
      </div>

      <!-- 操作按钮（仅未来/今日可用） -->
      <div v-if="!daySchedule.isPast" class="actions-group">
        <el-popconfirm
          v-if="daySchedule.isAllDayOff"
          title="确定恢复按常规作息？将清除该日休息标记。"
          confirm-button-text="恢复"
          cancel-button-text="取消"
          @confirm="emit('restore-day', daySchedule.date)"
        >
          <template #reference>
            <el-button type="success" link size="small">
              恢复作息
            </el-button>
          </template>
        </el-popconfirm>
        <el-button
          v-else
          type="info"
          link
          size="small"
          @click="emit('toggle-off', daySchedule.date, true)"
        >
          全天休息
        </el-button>

        <el-button type="primary" link size="small" @click="emit('edit-day', daySchedule.date)">
          微调/加档
        </el-button>

        <el-popconfirm
          v-if="daySchedule.hasException && !daySchedule.isAllDayOff"
          title="确定恢复按常规作息？将清除当天所有临时调整和加档。"
          confirm-button-text="恢复"
          cancel-button-text="取消"
          @confirm="emit('restore-day', daySchedule.date)"
        >
          <template #reference>
            <el-button type="warning" link size="small">
              恢复常规
            </el-button>
          </template>
        </el-popconfirm>
      </div>
      <div v-else class="past-actions-placeholder">
        <span class="past-text">已结束</span>
      </div>
    </div>
  </div>
</template>

<style scoped>
.day-row {
  display: flex;
  align-items: center;
  padding: 12px 16px;
  background-color: #fff;
  border-bottom: 1px solid #ebeef5;
  transition: background-color 0.15s ease;
}

.day-row:hover {
  background-color: #fafbfc;
}

.day-row.is-today {
  background-color: #f0f7ff;
}

.day-row.is-past {
  opacity: 0.72;
}

.day-row.is-off {
  background-color: #fafafa;
}

/* 左侧日期列 */
.date-col {
  width: 90px;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  justify-content: center;
}

.day-name-box {
  display: flex;
  align-items: center;
  gap: 6px;
}

.day-name {
  font-size: 15px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.today-tag,
.past-tag {
  height: 18px;
  line-height: 16px;
  padding: 0 4px;
  font-size: 10px;
}

.date-num {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  margin-top: 2px;
}

/* 中间进度条 */
.bar-col {
  flex: 1;
  min-width: 0;
  padding: 0 16px;
}

/* 右侧汇总与操作 */
.summary-col {
  width: 250px;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  justify-content: center;
  gap: 4px;
}

.summary-info {
  display: flex;
  align-items: center;
  gap: 8px;
}

.summary-text {
  font-size: 13px;
  font-weight: 500;
  color: var(--el-text-color-regular);
}

.summary-text.text-muted {
  color: var(--el-text-color-placeholder);
}

.exception-tag {
  font-size: 11px;
}

.actions-group {
  display: flex;
  align-items: center;
  gap: 8px;
}

.past-actions-placeholder {
  font-size: 12px;
  color: var(--el-text-color-placeholder);
}
</style>
