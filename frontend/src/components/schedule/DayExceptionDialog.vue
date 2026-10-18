<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import dayjs from 'dayjs'
import type { DaySchedule } from '@/utils/schedule'

const props = defineProps<{
  modelValue: boolean
  daySchedule?: DaySchedule
  saving: boolean
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', val: boolean): void
  (e: 'toggle-day-off', dateStr: string, isOff: boolean): void
  (e: 'restore-day', dateStr: string): void
  (e: 'add-available', startAt: string, endAt: string, remark?: string): void
  (e: 'add-unavailable', startAt: string, endAt: string, remark?: string): void
  (e: 'delete-slot', id: number): void
}>()

const visible = computed({
  get: () => props.modelValue,
  set: (val) => emit('update:modelValue', val),
})

const activeTab = ref<'available' | 'unavailable' | 'list'>('available')

const form = reactive({
  availStart: '10:00',
  availEnd: '12:00',
  availRemark: '临时加档',
  unavailStart: '14:00',
  unavailEnd: '16:00',
  unavailRemark: '临时有事',
})

const dialogTitle = computed(() => {
  if (!props.daySchedule) return '日程调整'
  return `${props.daySchedule.date} (${props.daySchedule.dayName}) 日程微调与加档`
})

function handleAddAvailable() {
  if (!props.daySchedule) return
  if (!form.availStart || !form.availEnd) {
    ElMessage.warning('请选择开始和结束时间')
    return
  }
  if (form.availStart === form.availEnd) {
    ElMessage.warning('开始时间与结束时间不能相同')
    return
  }
  const date = props.daySchedule.date
  const startAt = `${date} ${form.availStart}:00`
  const isCrossDay = form.availEnd <= form.availStart
  const nextDate = dayjs(date).add(1, 'day').format('YYYY-MM-DD')
  const endAt = isCrossDay ? `${nextDate} ${form.availEnd}:00` : `${date} ${form.availEnd}:00`
  emit('add-available', startAt, endAt, form.availRemark.trim() || undefined)
}

function handleAddUnavailable() {
  if (!props.daySchedule) return
  if (!form.unavailStart || !form.unavailEnd) {
    ElMessage.warning('请选择开始和结束时间')
    return
  }
  if (form.unavailStart === form.unavailEnd) {
    ElMessage.warning('开始时间与结束时间不能相同')
    return
  }
  const date = props.daySchedule.date
  const startAt = `${date} ${form.unavailStart}:00`
  const isCrossDay = form.unavailEnd <= form.unavailStart
  const nextDate = dayjs(date).add(1, 'day').format('YYYY-MM-DD')
  const endAt = isCrossDay ? `${nextDate} ${form.unavailEnd}:00` : `${date} ${form.unavailEnd}:00`
  emit('add-unavailable', startAt, endAt, form.unavailRemark.trim() || undefined)
}
</script>

<template>
  <el-dialog
    v-model="visible"
    :title="dialogTitle"
    width="580px"
    destroy-on-close
  >
    <div v-if="daySchedule" class="dialog-body">
      <!-- 快捷整天操作栏 -->
      <div class="quick-bar">
        <span class="quick-title">一键调整：</span>
        <div class="quick-btns">
          <el-button
            v-if="daySchedule.isAllDayOff"
            size="small"
            type="success"
            :loading="saving"
            @click="emit('toggle-day-off', daySchedule.date, false)"
          >
            设为全天可约
          </el-button>
          <el-button
            v-else
            size="small"
            type="info"
            :loading="saving"
            @click="emit('toggle-day-off', daySchedule.date, true)"
          >
            设为全天休息
          </el-button>

          <el-popconfirm
            v-if="daySchedule.hasException"
            title="确定恢复按常规作息？将清除当天所有临时调整。"
            confirm-button-text="恢复"
            cancel-button-text="取消"
            @confirm="emit('restore-day', daySchedule.date)"
          >
            <template #reference>
              <el-button size="small" type="warning" plain :loading="saving">
                恢复常规作息
              </el-button>
            </template>
          </el-popconfirm>
        </div>
      </div>

      <el-divider style="margin: 12px 0 16px" />

      <!-- 时段调整 Tabs -->
      <el-tabs v-model="activeTab" type="border-card">
        <!-- Tab 1: 临时加档 -->
        <el-tab-pane label="临时加档（可约）" name="available">
          <div class="tab-content">
            <p class="tab-tip">添加仅在今天生效的额外可约时段（例如休息日临时接单，或延长接单时间）。</p>
            <el-form label-width="90px">
              <el-form-item label="可约时间" required>
                <div class="time-range-row">
                  <el-time-select
                    v-model="form.availStart"
                    start="00:00"
                    step="00:30"
                    end="23:30"
                    placeholder="开始时间"
                    style="width: 130px"
                  />
                  <span>至</span>
                  <el-time-select
                    v-model="form.availEnd"
                    start="00:30"
                    step="00:30"
                    end="24:00"
                    placeholder="结束时间"
                    style="width: 130px"
                  />
                  <el-tag
                    v-if="form.availStart && form.availEnd && form.availEnd <= form.availStart"
                    type="warning"
                    size="small"
                    effect="plain"
                  >
                    次日 {{ form.availEnd }}（跨天）
                  </el-tag>
                </div>
              </el-form-item>
              <el-form-item label="备注说明">
                <el-input v-model="form.availRemark" placeholder="选填，如：晚上加档" maxlength="20" />
              </el-form-item>
              <el-form-item>
                <el-button type="success" :loading="saving" @click="handleAddAvailable">
                  确认添加可约时段
                </el-button>
              </el-form-item>
            </el-form>
          </div>
        </el-tab-pane>

        <!-- Tab 2: 临时请假 -->
        <el-tab-pane label="临时请假（不可约）" name="unavailable">
          <div class="tab-content">
            <p class="tab-tip">标记临时不可约时段（系统将自动在原有常规作息中扣减并挖空该时间段）。</p>
            <el-form label-width="90px">
              <el-form-item label="请假时间" required>
                <div class="time-range-row">
                  <el-time-select
                    v-model="form.unavailStart"
                    start="00:00"
                    step="00:30"
                    end="23:30"
                    placeholder="开始时间"
                    style="width: 130px"
                  />
                  <span>至</span>
                  <el-time-select
                    v-model="form.unavailEnd"
                    start="00:30"
                    step="00:30"
                    end="24:00"
                    placeholder="结束时间"
                    style="width: 130px"
                  />
                  <el-tag
                    v-if="form.unavailStart && form.unavailEnd && form.unavailEnd <= form.unavailStart"
                    type="warning"
                    size="small"
                    effect="plain"
                  >
                    次日 {{ form.unavailEnd }}（跨天）
                  </el-tag>
                </div>
              </el-form-item>
              <el-form-item label="请假原因">
                <el-input v-model="form.unavailRemark" placeholder="选填，如：临时有事" maxlength="20" />
              </el-form-item>
              <el-form-item>
                <el-button type="warning" :loading="saving" @click="handleAddUnavailable">
                  确认标记不可约
                </el-button>
              </el-form-item>
            </el-form>
          </div>
        </el-tab-pane>

        <!-- Tab 3: 当天现有明细 -->
        <el-tab-pane label="当天档期明细" name="list">
          <div class="tab-content">
            <el-table :data="daySchedule.segments" size="small" border style="width: 100%">
              <el-table-column prop="kind" label="类型" width="90">
                <template #default="{ row }">
                  <el-tag
                    :type="row.kind === 'available' ? 'success' : row.kind === 'booked' ? 'primary' : 'info'"
                    size="small"
                  >
                    {{ row.kind === 'available' ? '可约' : row.kind === 'booked' ? '已约' : '休息' }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column label="时段" min-width="120">
                <template #default="{ row }">
                  {{ row.startTimeText }} - {{ row.endTimeText }}
                </template>
              </el-table-column>
              <el-table-column label="来源/说明" min-width="120">
                <template #default="{ row }">
                  <span v-if="row.booking">订单: {{ row.booking.serviceTitle }}</span>
                  <span v-else-if="row.availability?.sourceType === 'MANUAL'">手动调整</span>
                  <span v-else-if="row.availability?.sourceType === 'RECURRENCE'">常规生成</span>
                  <span v-else>{{ row.remark || '-' }}</span>
                </template>
              </el-table-column>
              <el-table-column label="操作" width="70" align="center">
                <template #default="{ row }">
                  <el-button
                    v-if="row.availability?.sourceType === 'MANUAL'"
                    type="danger"
                    link
                    size="small"
                    :loading="saving"
                    @click="emit('delete-slot', row.availability.id)"
                  >
                    删除
                  </el-button>
                  <span v-else class="text-muted">-</span>
                </template>
              </el-table-column>
            </el-table>
          </div>
        </el-tab-pane>
      </el-tabs>
    </div>

    <template #footer>
      <el-button @click="visible = false">关闭</el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.dialog-body {
  display: flex;
  flex-direction: column;
}

.quick-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 8px;
}

.quick-title {
  font-size: 14px;
  font-weight: 500;
  color: var(--el-text-color-primary);
}

.quick-btns {
  display: flex;
  align-items: center;
  gap: 8px;
}

.tab-content {
  padding: 8px 0;
}

.tab-tip {
  font-size: 13px;
  color: var(--el-text-color-secondary);
  margin-bottom: 16px;
}

.time-range-row {
  display: flex;
  align-items: center;
  gap: 8px;
}

.text-muted {
  color: var(--el-text-color-placeholder);
}
</style>
