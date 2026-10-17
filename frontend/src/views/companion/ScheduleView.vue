<script setup lang="ts">
/**
 * 档期管理（FR-P10）：周视图档期 + 长期周期模板。
 *
 * <p>三种设置方式并存：
 * 1. 单条新增：精确新增某一天的时段（支持全天与跨天，如 20:00-02:00）；
 * 2. 批量设置：日期范围内每日重复同一时段（可全天），冲突日期自动跳过并提示明细；
 * 3. 长期模板：周几集合 + 每日时段（或全天）+ 生效日期范围，创建后自动循环生成
 *    未来 28 天档期并随访问滚动续期——设置一次长期生效，无需每周重复设置。</p>
 *
 * <p>模板生成的档期带「模板」来源标记；修改/停用/删除模板只影响未来未被订单占用的实例，
 * 手动删除某天实例后该天不会回填，长期关闭请停用模板。</p>
 */
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import dayjs from 'dayjs'
import {
  createAvailability,
  createAvailabilityBatch,
  createAvailabilityTemplate,
  createUnavailable,
  deleteAvailability,
  deleteAvailabilityTemplate,
  listAvailabilities,
  listAvailabilityTemplates,
  toggleAvailabilityTemplate,
  updateAvailabilityTemplate,
  type Availability,
  type AvailabilityTemplate,
} from '@/api/companion'

// ==================== 周视图档期 ====================

const items = ref<Availability[]>([])
const loading = ref(false)
const weekStart = ref(dayjs().startOf('week').add(1, 'day')) // 周一
const weekDays = computed(() => Array.from({ length: 7 }, (_, i) => weekStart.value.add(i, 'day')))

async function load() {
  loading.value = true
  try {
    const startAt = weekStart.value.format('YYYY-MM-DD 00:00:00')
    const endAt = weekStart.value.add(7, 'day').format('YYYY-MM-DD 00:00:00')
    items.value = await listAvailabilities(startAt, endAt)
  } finally {
    loading.value = false
  }
}

function itemsOf(day: dayjs.Dayjs): Availability[] {
  const dayStr = day.format('YYYY-MM-DD')
  return items.value.filter((i) => i.startAt.startsWith(dayStr))
}

function fmt(time: string): string {
  return time.length >= 16 ? time.slice(11, 16) : time
}

/** 跨天时段（如 20:00-次日02:00）在时间尾部标注 */
function timeRangeText(item: Availability): string {
  const range = fmt(item.startAt) + ' - ' + fmt(item.endAt)
  return item.startAt.slice(0, 10) === item.endAt.slice(0, 10) ? range : range + '（跨天）'
}

async function handleDelete(item: Availability) {
  await ElMessageBox.confirm(
    item.sourceType === 'RECURRENCE'
      ? '该档期由长期模板生成，删除仅影响这一天；如需长期关闭请停用对应模板。确定删除？'
      : '确定删除该档期记录？',
    '删除确认',
    { type: 'warning' },
  )
  await deleteAvailability(item.id)
  ElMessage.success('已删除')
  await load()
}

function prevWeek() {
  weekStart.value = weekStart.value.subtract(7, 'day')
  load()
}
function nextWeek() {
  weekStart.value = weekStart.value.add(7, 'day')
  load()
}
function thisWeek() {
  weekStart.value = dayjs().startOf('week').add(1, 'day')
  load()
}

// ==================== 单条新增 ====================

const dialogVisible = ref(false)
const mode = ref<'AVAILABLE' | 'UNAVAILABLE'>('AVAILABLE')
const saving = ref(false)
const form = reactive({
  date: '',
  allDay: false,
  startTime: '10:00',
  endTime: '22:00',
  remark: '',
})

function openCreate(type: 'AVAILABLE' | 'UNAVAILABLE') {
  mode.value = type
  form.date = dayjs().format('YYYY-MM-DD')
  form.allDay = false
  form.startTime = '10:00'
  form.endTime = '22:00'
  form.remark = ''
  dialogVisible.value = true
}

/** 校验时间对：相等非法；结束早于开始视为跨天（全天勾选时跳过校验） */
function validateTimePair(startTime: string, endTime: string, allDay: boolean): boolean {
  if (allDay) return true
  if (startTime === endTime) {
    ElMessage.warning('结束时间不能等于开始时间')
    return false
  }
  return true
}

/** 组装某一天的起止时刻：全天=[00:00, 次日00:00)；结束早于开始 → 跨天 */
function buildDayRange(date: string, allDay: boolean, startTime: string, endTime: string) {
  if (allDay) {
    return { startAt: date + ' 00:00:00', endAt: dayjs(date).add(1, 'day').format('YYYY-MM-DD') + ' 00:00:00' }
  }
  const startAt = date + ' ' + startTime + ':00'
  const sameOrNextDay = endTime > startTime ? date : dayjs(date).add(1, 'day').format('YYYY-MM-DD')
  return { startAt, endAt: sameOrNextDay + ' ' + endTime + ':00' }
}

async function handleCreate() {
  if (!form.date) {
    ElMessage.warning('请选择日期')
    return
  }
  if (!validateTimePair(form.startTime, form.endTime, form.allDay)) return
  const { startAt, endAt } = buildDayRange(form.date, form.allDay, form.startTime, form.endTime)
  saving.value = true
  try {
    const payload = { startAt, endAt, remark: form.remark || undefined }
    if (mode.value === 'AVAILABLE') {
      await createAvailability(payload)
      ElMessage.success(form.allDay ? '全天可约已添加' : '可约时段已添加')
    } else {
      await createUnavailable(payload)
      ElMessage.success('临时不可约时段已设置')
    }
    dialogVisible.value = false
    await load()
  } finally {
    saving.value = false
  }
}

// ==================== 批量设置（日期范围） ====================

const batchVisible = ref(false)
const batchSaving = ref(false)
const batchForm = reactive({
  startDate: dayjs().format('YYYY-MM-DD'),
  endDate: '',
  allDay: false,
  startTime: '19:00',
  endTime: '23:00',
  remark: '',
})

function openBatch() {
  batchForm.startDate = dayjs().format('YYYY-MM-DD')
  batchForm.endDate = ''
  batchForm.allDay = false
  batchForm.startTime = '19:00'
  batchForm.endTime = '23:00'
  batchForm.remark = ''
  batchVisible.value = true
}

async function handleBatch() {
  if (!batchForm.startDate) {
    ElMessage.warning('请选择开始日期')
    return
  }
  if (batchForm.endDate && batchForm.endDate < batchForm.startDate) {
    ElMessage.warning('结束日期不能早于开始日期')
    return
  }
  if (!validateTimePair(batchForm.startTime, batchForm.endTime, batchForm.allDay)) return
  batchSaving.value = true
  try {
    const result = await createAvailabilityBatch({
      startDate: batchForm.startDate,
      endDate: batchForm.endDate || undefined,
      allDay: batchForm.allDay,
      startTime: batchForm.allDay ? undefined : batchForm.startTime + ':00',
      endTime: batchForm.allDay ? undefined : batchForm.endTime + ':00',
      remark: batchForm.remark || undefined,
    })
    batchVisible.value = false
    if (result.skippedCount > 0) {
      const detail = result.skippedDetails.slice(0, 5).join('\n')
      const more = result.skippedCount > 5 ? `\n... 共跳过 ${result.skippedCount} 天` : ''
      ElMessageBox.alert(`已生成 ${result.createdCount} 条档期，${result.skippedCount} 天因冲突跳过：\n${detail}${more}`, '批量设置完成')
    } else {
      ElMessage.success(`已生成 ${result.createdCount} 条档期`)
    }
    await load()
  } finally {
    batchSaving.value = false
  }
}

// ==================== 长期模板 ====================

const templates = ref<AvailabilityTemplate[]>([])
const templatesLoading = ref(false)
const templateDialogVisible = ref(false)
const templateSaving = ref(false)
const editingTemplateId = ref<number | null>(null)

const WEEK_OPTIONS = [
  { value: 1, label: '周一' },
  { value: 2, label: '周二' },
  { value: 3, label: '周三' },
  { value: 4, label: '周四' },
  { value: 5, label: '周五' },
  { value: 6, label: '周六' },
  { value: 7, label: '周日' },
]

const templateForm = reactive({
  templateName: '',
  days: [] as number[],
  allDay: false,
  startTime: '19:00',
  endTime: '23:00',
  effectiveFrom: dayjs().format('YYYY-MM-DD'),
  effectiveUntil: '',
})

async function loadTemplates() {
  templatesLoading.value = true
  try {
    templates.value = await listAvailabilityTemplates()
  } finally {
    templatesLoading.value = false
  }
}

function openTemplateDialog(existing?: AvailabilityTemplate) {
  if (existing) {
    editingTemplateId.value = existing.id
    templateForm.templateName = existing.templateName
    templateForm.days = existing.dayOfWeek.split(',').map(Number)
    templateForm.allDay = existing.allDay
    templateForm.startTime = existing.startTime ? existing.startTime.slice(0, 5) : '19:00'
    templateForm.endTime = existing.endTime ? existing.endTime.slice(0, 5) : '23:00'
    templateForm.effectiveFrom = existing.effectiveFrom
    templateForm.effectiveUntil = existing.effectiveUntil || ''
  } else {
    editingTemplateId.value = null
    templateForm.templateName = ''
    templateForm.days = [1, 2, 3, 4, 5]
    templateForm.allDay = false
    templateForm.startTime = '19:00'
    templateForm.endTime = '23:00'
    templateForm.effectiveFrom = dayjs().format('YYYY-MM-DD')
    templateForm.effectiveUntil = ''
  }
  templateDialogVisible.value = true
}

/** 快捷预设：减少逐项勾选成本 */
function applyPreset(preset: 'weekdayEvening' | 'everydayAllDay') {
  if (preset === 'weekdayEvening') {
    templateForm.templateName = '工作日晚上'
    templateForm.days = [1, 2, 3, 4, 5]
    templateForm.allDay = false
    templateForm.startTime = '19:00'
    templateForm.endTime = '23:00'
  } else {
    templateForm.templateName = '每天全天'
    templateForm.days = [1, 2, 3, 4, 5, 6, 7]
    templateForm.allDay = true
  }
}

async function handleTemplateSave() {
  if (!templateForm.templateName.trim()) {
    ElMessage.warning('请填写模板名称')
    return
  }
  if (templateForm.days.length === 0) {
    ElMessage.warning('请至少选择一天生效')
    return
  }
  if (!validateTimePair(templateForm.startTime, templateForm.endTime, templateForm.allDay)) return
  if (templateForm.effectiveUntil && templateForm.effectiveUntil < templateForm.effectiveFrom) {
    ElMessage.warning('生效结束日期不能早于开始日期')
    return
  }
  const payload = {
    templateName: templateForm.templateName.trim(),
    dayOfWeek: [...templateForm.days].sort((a, b) => a - b).join(','),
    allDay: templateForm.allDay,
    startTime: templateForm.allDay ? undefined : templateForm.startTime + ':00',
    endTime: templateForm.allDay ? undefined : templateForm.endTime + ':00',
    effectiveFrom: templateForm.effectiveFrom,
    effectiveUntil: templateForm.effectiveUntil || undefined,
  }
  templateSaving.value = true
  try {
    if (editingTemplateId.value) {
      await updateAvailabilityTemplate(editingTemplateId.value, payload)
      ElMessage.success('模板已更新，未来档期已按新规则重新生成')
    } else {
      await createAvailabilityTemplate(payload)
      ElMessage.success('模板已创建，未来 4 周档期已自动生成')
    }
    templateDialogVisible.value = false
    await Promise.all([load(), loadTemplates()])
  } finally {
    templateSaving.value = false
  }
}

async function handleTemplateToggle(t: AvailabilityTemplate, enabled: boolean) {
  await toggleAvailabilityTemplate(t.id, enabled)
  ElMessage.success(enabled ? '模板已启用，未来档期已生成' : '模板已停用，未来档期已收回（有订单占用的时间保留）')
  await Promise.all([load(), loadTemplates()])
}

async function handleTemplateDelete(t: AvailabilityTemplate) {
  await ElMessageBox.confirm(
    `删除模板「${t.templateName}」将清理其未来未生效的档期，已被订单占用的时间不受影响。确定删除？`,
    '删除模板',
    { type: 'warning' },
  )
  await deleteAvailabilityTemplate(t.id)
  ElMessage.success('模板已删除')
  await Promise.all([load(), loadTemplates()])
}

onMounted(() => {
  load()
  loadTemplates()
})
</script>

<template>
  <div class="schedule-view">
    <div class="toolbar">
      <el-button type="primary" @click="openCreate('AVAILABLE')">新增可约时段</el-button>
      <el-button type="warning" @click="openCreate('UNAVAILABLE')">设置临时不可约</el-button>
      <el-button @click="openBatch">批量设置（日期范围）</el-button>
      <div class="week-nav">
        <el-button text @click="prevWeek">‹ 上一周</el-button>
        <el-button text @click="thisWeek">本周</el-button>
        <el-button text @click="nextWeek">下一周 ›</el-button>
        <span class="week-range">
          {{ weekStart.format('YYYY-MM-DD') }} ~ {{ weekStart.add(6, 'day').format('YYYY-MM-DD') }}
        </span>
      </div>
    </div>

    <!-- 长期周期模板：设置一次自动循环生效 -->
    <el-card v-loading="templatesLoading" class="template-card" shadow="never">
      <template #header>
        <div class="template-header">
          <div>
            <b>长期档期模板</b>
            <span class="template-hint">设置一次自动循环生成未来档期，无需每周重复设置</span>
          </div>
          <el-button type="primary" size="small" @click="openTemplateDialog()">新建模板</el-button>
        </div>
      </template>
      <div v-if="templates.length" class="template-list">
        <div v-for="t in templates" :key="t.id" class="template-item">
          <el-switch
            :model-value="t.enabled"
            @change="(v: boolean | string | number) => handleTemplateToggle(t, Boolean(v))"
          />
          <div class="template-info">
            <b>{{ t.templateName }}</b>
            <span class="template-rule">{{ t.ruleText }}</span>
          </div>
          <el-button size="small" text @click="openTemplateDialog(t)">编辑</el-button>
          <el-button size="small" text type="danger" @click="handleTemplateDelete(t)">删除</el-button>
        </div>
      </div>
      <el-empty
        v-else
        :image-size="40"
        description="还没有长期模板，创建一个即可自动循环生效（如：工作日晚上 19:00-23:00）"
      />
    </el-card>

    <div v-loading="loading" class="week-scroll">
      <div class="week-grid">
        <el-card
          v-for="day in weekDays"
          :key="day.format('YYYY-MM-DD')"
          shadow="hover"
          class="day-card"
          :class="{ today: day.isSame(dayjs(), 'day') }"
        >
          <template #header>
            <div class="day-header">
              <b>{{ ['日', '一', '二', '三', '四', '五', '六'][day.day()] }}</b>
              <span>{{ day.format('MM-DD') }}</span>
            </div>
          </template>
          <div class="slot-list">
            <div v-for="item in itemsOf(day)" :key="item.id" class="slot-item">
              <el-tag size="small" :type="item.availabilityStatus === 'AVAILABLE' ? 'success' : 'danger'">
                {{ item.availabilityStatus === 'AVAILABLE' ? '可约' : '不可约' }}
              </el-tag>
              <el-tag v-if="item.sourceType === 'RECURRENCE'" size="small" type="info" class="source-tag">
                模板
              </el-tag>
              <span class="slot-time">{{ timeRangeText(item) }}</span>
              <el-button size="small" text type="danger" @click="handleDelete(item)">删</el-button>
              <div v-if="item.remark" class="slot-remark">{{ item.remark }}</div>
            </div>
            <el-empty v-if="itemsOf(day).length === 0" description="无档期" :image-size="40" />
          </div>
        </el-card>
      </div>
    </div>

    <!-- 单条新增 -->
    <el-dialog v-model="dialogVisible" :title="mode === 'AVAILABLE' ? '新增可约时段' : '设置临时不可约'" width="440px">
      <el-form :model="form" label-width="90px">
        <el-form-item label="日期" required>
          <el-date-picker v-model="form.date" type="date" value-format="YYYY-MM-DD" placeholder="选择日期" style="width: 100%" />
        </el-form-item>
        <el-form-item v-if="mode === 'AVAILABLE'" label="全天可约">
          <el-switch v-model="form.allDay" />
          <span v-if="form.allDay" class="form-tip">该天 00:00 - 24:00 全程可约</span>
        </el-form-item>
        <template v-if="!form.allDay">
          <el-form-item label="开始时间" required>
            <el-time-picker v-model="form.startTime" value-format="HH:mm" placeholder="开始时间" style="width: 100%" />
          </el-form-item>
          <el-form-item label="结束时间" required>
            <el-time-picker v-model="form.endTime" value-format="HH:mm" placeholder="结束时间" style="width: 100%" />
            <div v-if="form.endTime < form.startTime" class="form-tip">结束时间早于开始时间，视为跨天时段（如 20:00 - 次日 02:00）</div>
          </el-form-item>
        </template>
        <el-form-item label="备注">
          <el-input v-model="form.remark" maxlength="200" placeholder="选填，如：晚上有课" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="handleCreate">确定</el-button>
      </template>
    </el-dialog>

    <!-- 批量设置（日期范围） -->
    <el-dialog v-model="batchVisible" title="批量设置可约档期" width="480px">
      <el-form :model="batchForm" label-width="90px">
        <el-form-item label="开始日期" required>
          <el-date-picker v-model="batchForm.startDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
        <el-form-item label="结束日期">
          <el-date-picker v-model="batchForm.endDate" type="date" value-format="YYYY-MM-DD" style="width: 100%"
            :disabled-date="(d: Date) => d.getTime() < dayjs(batchForm.startDate).valueOf()"
            placeholder="选填，默认同开始日期" />
        </el-form-item>
        <el-form-item label="全天可约">
          <el-switch v-model="batchForm.allDay" />
          <span v-if="batchForm.allDay" class="form-tip">每日 00:00 - 24:00 全程可约</span>
        </el-form-item>
        <template v-if="!batchForm.allDay">
          <el-form-item label="开始时间" required>
            <el-time-picker v-model="batchForm.startTime" value-format="HH:mm" style="width: 100%" />
          </el-form-item>
          <el-form-item label="结束时间" required>
            <el-time-picker v-model="batchForm.endTime" value-format="HH:mm" style="width: 100%" />
            <div v-if="batchForm.endTime < batchForm.startTime" class="form-tip">视为跨天时段（如 20:00 - 次日 02:00）</div>
          </el-form-item>
        </template>
        <el-form-item label="备注">
          <el-input v-model="batchForm.remark" maxlength="200" placeholder="选填，如：国庆假期全天在线" />
        </el-form-item>
      </el-form>
      <div class="form-tip" style="margin-bottom: 8px">
        与已有档期或订单冲突的日期会自动跳过，提交后显示明细；如需每周自动重复，请使用「长期档期模板」。
      </div>
      <template #footer>
        <el-button @click="batchVisible = false">取消</el-button>
        <el-button type="primary" :loading="batchSaving" @click="handleBatch">批量生成</el-button>
      </template>
    </el-dialog>

    <!-- 长期模板编辑 -->
    <el-dialog v-model="templateDialogVisible" :title="editingTemplateId ? '编辑长期模板' : '新建长期模板'" width="520px">
      <el-form :model="templateForm" label-width="90px">
        <el-form-item label="模板名称" required>
          <el-input v-model="templateForm.templateName" maxlength="50" placeholder="如：工作日晚上" />
        </el-form-item>
        <el-form-item label="快捷预设">
          <el-button size="small" @click="applyPreset('weekdayEvening')">工作日晚上 19-23 点</el-button>
          <el-button size="small" @click="applyPreset('everydayAllDay')">每天全天</el-button>
        </el-form-item>
        <el-form-item label="生效星期" required>
          <el-checkbox-group v-model="templateForm.days">
            <el-checkbox v-for="d in WEEK_OPTIONS" :key="d.value" :value="d.value">{{ d.label }}</el-checkbox>
          </el-checkbox-group>
        </el-form-item>
        <el-form-item label="全天可约">
          <el-switch v-model="templateForm.allDay" />
          <span v-if="templateForm.allDay" class="form-tip">每日 00:00 - 24:00 全程可约</span>
        </el-form-item>
        <template v-if="!templateForm.allDay">
          <el-form-item label="开始时间" required>
            <el-time-picker v-model="templateForm.startTime" value-format="HH:mm" style="width: 100%" />
          </el-form-item>
          <el-form-item label="结束时间" required>
            <el-time-picker v-model="templateForm.endTime" value-format="HH:mm" style="width: 100%" />
            <div v-if="templateForm.endTime < templateForm.startTime" class="form-tip">视为跨天时段（如 20:00 - 次日 02:00）</div>
          </el-form-item>
        </template>
        <el-form-item label="生效开始" required>
          <el-date-picker v-model="templateForm.effectiveFrom" type="date" value-format="YYYY-MM-DD"
            :disabled-date="(d: Date) => d.getTime() < dayjs().startOf('day').valueOf()" style="width: 100%" />
        </el-form-item>
        <el-form-item label="生效结束">
          <el-date-picker v-model="templateForm.effectiveUntil" type="date" value-format="YYYY-MM-DD"
            :disabled-date="(d: Date) => d.getTime() < dayjs(templateForm.effectiveFrom).valueOf()"
            style="width: 100%" placeholder="选填，留空表示长期有效" />
        </el-form-item>
      </el-form>
      <div class="form-tip" style="margin-bottom: 8px">
        保存后立即生成未来 4 周档期，之后随访问自动续期；修改模板仅影响未来未被订单占用的档期。
      </div>
      <template #footer>
        <el-button @click="templateDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="templateSaving" @click="handleTemplateSave">保存并生成</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 16px;
  flex-wrap: wrap;
}
.week-nav {
  margin-left: auto;
  display: flex;
  align-items: center;
  flex-wrap: wrap;
}
.week-range {
  color: var(--text-secondary);
  font-size: 13px;
}
.template-card {
  margin-bottom: 16px;
}
.template-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.template-hint {
  margin-left: 8px;
  font-size: 12px;
  color: var(--text-secondary);
  font-weight: normal;
}
.template-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.template-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 8px 4px;
  border-bottom: 1px dashed var(--border);
}
.template-item:last-child {
  border-bottom: none;
}
.template-info {
  flex: 1;
  display: flex;
  flex-direction: column;
}
.template-rule {
  font-size: 12px;
  color: var(--text-secondary);
}
.source-tag {
  margin-left: 4px;
}
.form-tip {
  font-size: 12px;
  color: #909399;
  line-height: 1.5;
  margin-left: 8px;
}
/* 周视图：桌面 7 列等宽，小屏横向滚动（每列固定最小宽度保证可读） */
.week-scroll {
  overflow-x: auto;
  min-height: 200px;
  padding-bottom: 4px;
}
.week-grid {
  display: grid;
  grid-template-columns: repeat(7, minmax(140px, 1fr));
  gap: 12px;
}
.day-card.today {
  border-color: var(--brand-primary);
  box-shadow: 0 0 0 1px var(--brand-primary);
}
.day-header {
  display: flex;
  justify-content: space-between;
}
.slot-list {
  min-height: 120px;
}
.slot-item {
  margin-bottom: 8px;
}
.slot-time {
  font-size: 12px;
  margin-left: 4px;
}
.slot-remark {
  font-size: 12px;
  color: #909399;
}
</style>
