<script setup lang="ts">
import { computed, reactive, watch } from 'vue'
import { ElMessage } from 'element-plus'
import dayjs from 'dayjs'
import type { AvailabilityTemplate, AvailabilityTemplatePayload } from '@/api/companion'

const props = defineProps<{
  modelValue: boolean
  template?: AvailabilityTemplate
  saving: boolean
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', val: boolean): void
  (e: 'submit', payload: AvailabilityTemplatePayload, id?: number): void
}>()

const visible = computed({
  get: () => props.modelValue,
  set: (val) => emit('update:modelValue', val),
})

const isEdit = computed(() => !!props.template)

const formData = reactive({
  templateName: '常规接单作息',
  daysOfWeek: [1, 2, 3, 4, 5] as number[],
  allDay: false,
  startTime: '19:00',
  endTime: '23:00',
  effectiveFrom: dayjs().format('YYYY-MM-DD'),
  effectiveUntil: '' as string,
})

const weekOptions = [
  { value: 1, label: '周一' },
  { value: 2, label: '周二' },
  { value: 3, label: '周三' },
  { value: 4, label: '周四' },
  { value: 5, label: '周五' },
  { value: 6, label: '周六' },
  { value: 7, label: '周日' },
]

watch(
  () => props.modelValue,
  (val) => {
    if (val) {
      if (props.template) {
        formData.templateName = props.template.templateName
        formData.daysOfWeek = props.template.dayOfWeek.split(',').map((s) => parseInt(s, 10)).filter(Boolean)
        formData.allDay = props.template.allDay
        formData.startTime = props.template.startTime ? props.template.startTime.substring(0, 5) : '19:00'
        formData.endTime = props.template.endTime ? props.template.endTime.substring(0, 5) : '23:00'
        formData.effectiveFrom = props.template.effectiveFrom
        formData.effectiveUntil = props.template.effectiveUntil || ''
      } else {
        formData.templateName = '常规接单作息'
        formData.daysOfWeek = [1, 2, 3, 4, 5]
        formData.allDay = false
        formData.startTime = '19:00'
        formData.endTime = '23:00'
        formData.effectiveFrom = dayjs().format('YYYY-MM-DD')
        formData.effectiveUntil = ''
      }
    }
  },
  { immediate: true },
)

function disabledStartDate(time: Date) {
  // 新建时不能早于今天；编辑时允许保持原历史日期
  if (!isEdit.value) {
    return dayjs(time).isBefore(dayjs().startOf('day'))
  }
  return false
}

function disabledEndDate(time: Date) {
  if (formData.effectiveFrom) {
    return dayjs(time).isBefore(dayjs(formData.effectiveFrom).startOf('day'))
  }
  return false
}

function handleSelectWorkdays() {
  formData.daysOfWeek = [1, 2, 3, 4, 5]
}

function handleSelectWeekends() {
  formData.daysOfWeek = [6, 7]
}

function handleSelectAllDays() {
  formData.daysOfWeek = [1, 2, 3, 4, 5, 6, 7]
}

async function handleSubmit() {
  if (formData.daysOfWeek.length === 0) {
    ElMessage.warning('请至少选择一天生效星期')
    return
  }

  if (!formData.allDay) {
    if (!formData.startTime || !formData.endTime) {
      ElMessage.warning('请选择每日开始与结束时间')
      return
    }
    if (formData.startTime === formData.endTime) {
      ElMessage.warning('开始时间与结束时间不能相同')
      return
    }
  }

  const payload: AvailabilityTemplatePayload = {
    templateName: formData.templateName.trim(),
    dayOfWeek: [...formData.daysOfWeek].sort((a, b) => a - b).join(','),
    allDay: formData.allDay,
    startTime: formData.allDay ? undefined : `${formData.startTime}:00`,
    endTime: formData.allDay ? undefined : `${formData.endTime}:00`,
    effectiveFrom: formData.effectiveFrom,
    effectiveUntil: formData.effectiveUntil ? formData.effectiveUntil : undefined,
  }

  emit('submit', payload, props.template?.id)
}
</script>

<template>
  <el-dialog
    v-model="visible"
    :title="isEdit ? '编辑常规作息规则' : '新建常规作息规则'"
    width="540px"
    destroy-on-close
  >
    <el-form label-width="110px">
      <el-form-item label="作息名称" required>
        <el-input v-model="formData.templateName" placeholder="例如：常规接单作息" maxlength="30" show-word-limit />
      </el-form-item>

      <el-form-item label="重复星期" required>
        <div class="days-select-box">
          <el-checkbox-group v-model="formData.daysOfWeek">
            <el-checkbox-button
              v-for="opt in weekOptions"
              :key="opt.value"
              :value="opt.value"
            >
              {{ opt.label }}
            </el-checkbox-button>
          </el-checkbox-group>

          <div class="preset-links">
            <el-button link type="primary" size="small" @click="handleSelectWorkdays">工作日</el-button>
            <el-divider direction="vertical" />
            <el-button link type="primary" size="small" @click="handleSelectWeekends">周末</el-button>
            <el-divider direction="vertical" />
            <el-button link type="primary" size="small" @click="handleSelectAllDays">每天</el-button>
          </div>
        </div>
      </el-form-item>

      <el-form-item label="接单时段" required>
        <div class="time-setting-box">
          <el-switch
            v-model="formData.allDay"
            active-text="全天"
            inactive-text="指定时段"
          />

          <div v-if="!formData.allDay" class="time-inputs">
            <el-time-select
              v-model="formData.startTime"
              start="00:00"
              step="00:30"
              end="23:30"
              placeholder="开始时间"
              style="width: 140px"
            />
            <span class="range-separator">至</span>
            <el-time-select
              v-model="formData.endTime"
              start="00:30"
              step="00:30"
              end="24:00"
              placeholder="结束时间"
              style="width: 140px"
            />
            <el-tag
              v-if="formData.startTime && formData.endTime && formData.endTime < formData.startTime"
              type="warning"
              size="small"
              effect="plain"
            >
              次日 {{ formData.endTime }}（跨天）
            </el-tag>
          </div>
        </div>
      </el-form-item>

      <el-form-item label="生效起始日" required>
        <el-date-picker
          v-model="formData.effectiveFrom"
          type="date"
          value-format="YYYY-MM-DD"
          :disabled-date="disabledStartDate"
          placeholder="选择生效起始日期"
          style="width: 200px"
        />
        <span class="form-tip">（系统将从该日或今日起自动生成排期）</span>
      </el-form-item>

      <el-form-item label="截止日期">
        <el-date-picker
          v-model="formData.effectiveUntil"
          type="date"
          value-format="YYYY-MM-DD"
          :disabled-date="disabledEndDate"
          placeholder="留空表示长期生效"
          clearable
          style="width: 200px"
        />
        <span class="form-tip">（长期有效请清空）</span>
      </el-form-item>
    </el-form>

    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="saving" @click="handleSubmit">
        保存并应用
      </el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.days-select-box {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.preset-links {
  display: flex;
  align-items: center;
  gap: 4px;
}

.time-setting-box {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.time-inputs {
  display: flex;
  align-items: center;
  gap: 8px;
}

.range-separator {
  color: var(--el-text-color-secondary);
}

.form-tip {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  margin-left: 8px;
}
</style>
