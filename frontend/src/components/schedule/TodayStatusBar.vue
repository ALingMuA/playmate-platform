<script setup lang="ts">
import { computed } from 'vue'
import type { DaySchedule } from '@/utils/schedule'

const props = defineProps<{
  todaySchedule?: DaySchedule
  serverNowText: string
  hasActiveRoutine: boolean
  saving: boolean
}>()

const emit = defineEmits<{
  (e: 'toggle-today-rest', isOff: boolean): void
  (e: 'restore-today'): void
}>()

const isOff = computed(() => {
  return props.todaySchedule?.isAllDayOff ?? false
})

const hasException = computed(() => {
  return props.todaySchedule?.hasException ?? false
})

const statusTagType = computed(() => {
  if (isOff.value) return 'info'
  if (props.todaySchedule && props.todaySchedule.availableMinutes > 0) return 'success'
  return 'warning'
})

const statusTagText = computed(() => {
  if (isOff.value) return '今日全天休息'
  if (props.todaySchedule && props.todaySchedule.availableMinutes > 0) return '今日接单中'
  return '今日未安排'
})
</script>

<template>
  <el-card class="today-status-bar" shadow="never">
    <div class="bar-content">
      <div class="left-section">
        <div class="title-row">
          <span class="main-title">今日概况</span>
          <el-tag :type="statusTagType" effect="dark" size="small">
            {{ statusTagText }}
          </el-tag>
        </div>
        <div class="desc-row">
          <span v-if="todaySchedule" class="stats-text">
            {{ todaySchedule.summaryText }}
          </span>
          <span class="server-time-text">（系统时间：{{ serverNowText }}）</span>
        </div>
      </div>

      <div class="right-section">
        <el-button
          v-if="!isOff"
          type="warning"
          size="default"
          plain
          :loading="saving"
          @click="emit('toggle-today-rest', true)"
        >
          今日休息
        </el-button>

        <el-button
          v-if="isOff || hasException"
          type="primary"
          size="default"
          :loading="saving"
          @click="emit('restore-today')"
        >
          恢复今天按作息
        </el-button>
      </div>
    </div>
  </el-card>
</template>

<style scoped>
.today-status-bar {
  border-radius: 8px;
  border: 1px solid #d9ecff;
  background: linear-gradient(135deg, #f0f7ff 0%, #ffffff 100%);
  margin-bottom: 16px;
}

:deep(.el-card__body) {
  padding: 16px 20px;
}

.bar-content {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 12px;
}

.left-section {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.title-row {
  display: flex;
  align-items: center;
  gap: 10px;
}

.main-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.desc-row {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
}

.stats-text {
  color: var(--el-text-color-regular);
  font-weight: 500;
}

.server-time-text {
  color: var(--el-text-color-secondary);
}
</style>
