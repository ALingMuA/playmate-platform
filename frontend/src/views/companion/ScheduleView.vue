<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useSchedule } from '@/composables/useSchedule'
import TodayStatusBar from '@/components/schedule/TodayStatusBar.vue'
import RoutineCard from '@/components/schedule/RoutineCard.vue'
import WeekBoard from '@/components/schedule/WeekBoard.vue'
import TemplateManageDrawer from '@/components/schedule/TemplateManageDrawer.vue'
import TemplateEditorDialog from '@/components/schedule/TemplateEditorDialog.vue'
import DayExceptionDialog from '@/components/schedule/DayExceptionDialog.vue'
import type { AvailabilityTemplate, AvailabilityTemplatePayload } from '@/api/companion'
import type { DaySchedule, VisualSegment } from '@/utils/schedule'

const {
  loading,
  templatesLoading,
  saving,
  templates,
  serverNow,
  generatedUntil,
  weekTitle,
  daySchedules,
  canPrev,
  canNext,
  isCurrentWeek,
  refresh,
  prevWeek,
  nextWeek,
  jumpToToday,
  setDayOff,
  restoreSingleDay,
  addSlot,
  addUnavailable,
  removeSlot,
  saveTemplate,
  toggleTemplate,
  removeTemplate,
} = useSchedule()

// 弹窗与抽屉状态
const manageDrawerVisible = ref(false)
const editorDialogVisible = ref(false)
const exceptionDialogVisible = ref(false)
const selectedDayDate = ref<string>('')
const editingTemplate = ref<AvailabilityTemplate | undefined>(undefined)

onMounted(() => {
  refresh()
})

const serverNowText = computed(() => {
  return serverNow.value.format('YYYY-MM-DD HH:mm')
})

const todaySchedule = computed<DaySchedule | undefined>(() => {
  return daySchedules.value.find((d) => d.isToday)
})

const selectedDaySchedule = computed<DaySchedule | undefined>(() => {
  if (!selectedDayDate.value) return undefined
  return daySchedules.value.find((d) => d.date === selectedDayDate.value)
})

const hasActiveRoutine = computed(() => {
  return templates.value.some((t) => t.enabled)
})

// 抽屉与模板编辑
function handleOpenManage() {
  manageDrawerVisible.value = true
}

function handleCreateRoutine() {
  editingTemplate.value = undefined
  editorDialogVisible.value = true
}

function handleEditTemplate(tpl: AvailabilityTemplate) {
  editingTemplate.value = tpl
  editorDialogVisible.value = true
}

async function handleSubmitTemplate(payload: AvailabilityTemplatePayload, id?: number) {
  const success = await saveTemplate(payload, id)
  if (success) {
    editorDialogVisible.value = false
  }
}

async function handleDeleteTemplate(id: number) {
  await removeTemplate(id)
}

// 单日微调弹窗
function handleEditDay(dateStr: string) {
  selectedDayDate.value = dateStr
  exceptionDialogVisible.value = true
}

function handleSegmentClick(seg: VisualSegment) {
  // 点击片段提取日期并打开对应日期调整弹窗
  const dateStr = seg.id.split('_')[0]
  if (dateStr) {
    handleEditDay(dateStr)
  }
}

async function handleToggleDayOff(dateStr: string, isOff: boolean) {
  await setDayOff(dateStr, isOff)
}

async function handleRestoreDay(dateStr: string) {
  await restoreSingleDay(dateStr)
}

async function handleAddAvailable(startAt: string, endAt: string, remark?: string) {
  await addSlot(startAt, endAt, remark)
}

async function handleAddUnavailable(startAt: string, endAt: string, remark?: string) {
  await addUnavailable(startAt, endAt, remark)
}

async function handleDeleteSlot(id: number) {
  await removeSlot(id)
}
</script>

<template>
  <div class="schedule-view">
    <div class="page-header">
      <div class="header-titles">
        <h2 class="title">接单时间设置</h2>
        <p class="subtitle">
          配置常规接单作息后，系统将自动每日滚动补齐未来 28 天接单时间。若临时有变动，直接在看板对应日期微调或请假即可。
        </p>
      </div>
      <div class="header-actions">
        <el-button :loading="loading || templatesLoading" @click="refresh">
          刷新日程
        </el-button>
      </div>
    </div>

    <!-- 1. 今日状态快捷条 -->
    <TodayStatusBar
      :today-schedule="todaySchedule"
      :server-now-text="serverNowText"
      :has-active-routine="hasActiveRoutine"
      :saving="saving"
      @toggle-today-rest="handleToggleDayOff(todaySchedule?.date || '', $event)"
      @restore-today="handleRestoreDay(todaySchedule?.date || '')"
    />

    <!-- 2. 常规作息卡片 -->
    <RoutineCard
      :templates="templates"
      :loading="templatesLoading || saving"
      @open-manage="handleOpenManage"
      @create-routine="handleCreateRoutine"
      @edit-template="handleEditTemplate"
      @delete-template="handleDeleteTemplate"
      @toggle-status="toggleTemplate"
    />

    <!-- 3. 周日程 7 天全景看板 -->
    <div v-loading="loading">
      <WeekBoard
        :week-title="weekTitle"
        :day-schedules="daySchedules"
        :can-prev="canPrev"
        :can-next="canNext"
        :is-current-week="isCurrentWeek"
        :has-active-routine="hasActiveRoutine"
        :generated-until="generatedUntil"
        @prev-week="prevWeek"
        @next-week="nextWeek"
        @jump-today="jumpToToday"
        @toggle-off="handleToggleDayOff"
        @edit-day="handleEditDay"
        @restore-day="handleRestoreDay"
        @segment-click="handleSegmentClick"
      />
    </div>

    <!-- 4. 常规作息管理抽屉 -->
    <TemplateManageDrawer
      v-model="manageDrawerVisible"
      :templates="templates"
      :loading="saving || templatesLoading"
      @edit-template="handleEditTemplate"
      @create-template="handleCreateRoutine"
      @toggle-status="toggleTemplate"
      @delete-template="handleDeleteTemplate"
    />

    <!-- 5. 常规作息规则编辑弹窗 -->
    <TemplateEditorDialog
      v-model="editorDialogVisible"
      :template="editingTemplate"
      :saving="saving"
      @submit="handleSubmitTemplate"
    />

    <!-- 6. 单日微调/加档/请假弹窗 -->
    <DayExceptionDialog
      v-model="exceptionDialogVisible"
      :day-schedule="selectedDaySchedule"
      :saving="saving"
      @toggle-day-off="handleToggleDayOff"
      @restore-day="handleRestoreDay"
      @add-available="handleAddAvailable"
      @add-unavailable="handleAddUnavailable"
      @delete-slot="handleDeleteSlot"
    />
  </div>
</template>

<style scoped>
.schedule-view {
  max-width: 1200px;
  margin: 0 auto;
  padding: 24px;
}

.page-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  margin-bottom: 20px;
  gap: 16px;
}

.header-titles .title {
  margin: 0 0 6px 0;
  font-size: 22px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.header-titles .subtitle {
  margin: 0;
  font-size: 13px;
  color: var(--el-text-color-secondary);
  line-height: 1.5;
}

.header-actions {
  flex-shrink: 0;
}
</style>
