<script setup lang="ts">
/** 陪玩师接单时间：长期作息优先，周预览用于核对，临时变化按单日处理。 */
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import dayjs, { type Dayjs } from 'dayjs'
import { ApiError } from '@/api/http'
import {
  createAvailability,
  createAvailabilityTemplate,
  deleteAvailabilityTemplate,
  getScheduleOverview,
  listAvailabilityTemplates,
  toggleAvailabilityTemplate,
  toggleDayOff,
  updateAvailabilityTemplate,
  type AvailabilityTemplate,
  type BookedSlot,
  type ScheduleOverview,
} from '@/api/companion'

const WEEKDAY_LABELS = ['一', '二', '三', '四', '五', '六', '日']
type ExceptionKind = 'rest' | 'extra'

interface PreviewSegment {
  kind: 'available' | 'booked'
  start: Dayjs
  end: Dayjs
  sourceType?: 'MANUAL' | 'RECURRENCE'
  booking?: BookedSlot
}

function mondayOf(value = dayjs()): Dayjs {
  const day = value.day()
  return value.startOf('day').add(day === 0 ? -6 : 1 - day, 'day')
}

const weekStart = ref(mondayOf())
const weekDays = computed(() => Array.from({ length: 7 }, (_, index) => weekStart.value.add(index, 'day')))
const weekRangeText = computed(
  () => weekStart.value.format('MM月DD日') + ' – ' + weekStart.value.add(6, 'day').format('MM月DD日'),
)
const overview = ref<ScheduleOverview>({ availabilities: [], bookedSlots: [] })
const templates = ref<AvailabilityTemplate[]>([])
const loading = ref(false)
const templatesLoading = ref(false)
const rulesDrawerVisible = ref(false)
const templateDialogVisible = ref(false)
const templateSaving = ref(false)
const exceptionDialogVisible = ref(false)
const exceptionSaving = ref(false)

const WEEK_OPTIONS = WEEKDAY_LABELS.map((label, index) => ({ value: index + 1, label: '周' + label }))
const routineForm = reactive({
  weekdays: [1, 2, 3, 4, 5] as number[],
  weekends: [6, 7] as number[],
  weekdayStart: '19:00',
  weekdayEnd: '23:00',
  weekendStart: '14:00',
  weekendEnd: '22:00',
  effectiveFrom: dayjs().format('YYYY-MM-DD'),
})
const exceptionForm = reactive({
  date: dayjs().format('YYYY-MM-DD'),
  kind: 'rest' as ExceptionKind,
  startTime: '10:00',
  endTime: '13:00',
})

const activeTemplates = computed(() => templates.value.filter((template) => template.enabled))
const pausedTemplateCount = computed(() => templates.value.length - activeTemplates.value.length)
const dayPreviews = computed(() => weekDays.value.map((day, dayIndex) => buildDayPreview(day, dayIndex)))

function overlaps(startAt: string, endAt: string, rangeStart: Dayjs, rangeEnd: Dayjs): boolean {
  const start = dayjs(startAt)
  const end = dayjs(endAt)
  return start.isBefore(rangeEnd) && end.isAfter(rangeStart)
}

function clippedInterval(startAt: string, endAt: string, dayStart: Dayjs, dayEnd: Dayjs) {
  const start = dayjs(startAt).isBefore(dayStart) ? dayStart : dayjs(startAt)
  const end = dayjs(endAt).isAfter(dayEnd) ? dayEnd : dayjs(endAt)
  return { start, end }
}

function buildDayPreview(day: Dayjs, dayIndex: number) {
  const dayStart = day.startOf('day')
  const dayEnd = dayStart.add(1, 'day')
  const availabilities = overview.value.availabilities.filter(
    (item) => item.availabilityStatus === 'AVAILABLE' && overlaps(item.startAt, item.endAt, dayStart, dayEnd),
  )
  const unavailable = overview.value.availabilities.some(
    (item) => item.availabilityStatus === 'UNAVAILABLE' && overlaps(item.startAt, item.endAt, dayStart, dayEnd),
  )
  const bookings = overview.value.bookedSlots
    .filter((item) => overlaps(item.startAt, item.endAt, dayStart, dayEnd))
    .sort((left, right) => dayjs(left.startAt).valueOf() - dayjs(right.startAt).valueOf())
  const segments: PreviewSegment[] = []

  for (const availability of availabilities) {
    const clipped = clippedInterval(availability.startAt, availability.endAt, dayStart, dayEnd)
    let cursor = clipped.start
    for (const booking of bookings) {
      const booked = clippedInterval(booking.startAt, booking.endAt, dayStart, dayEnd)
      if (!booked.start.isBefore(clipped.end) || !booked.end.isAfter(cursor)) continue
      if (booked.start.isAfter(cursor)) {
        segments.push({ kind: 'available', start: cursor, end: booked.start, sourceType: availability.sourceType })
      }
      if (booked.end.isAfter(cursor)) cursor = booked.end
    }
    if (cursor.isBefore(clipped.end)) {
      segments.push({ kind: 'available', start: cursor, end: clipped.end, sourceType: availability.sourceType })
    }
  }

  const seenOrders = new Set<number>()
  for (const booking of bookings) {
    if (seenOrders.has(booking.orderId)) continue
    seenOrders.add(booking.orderId)
    const clipped = clippedInterval(booking.startAt, booking.endAt, dayStart, dayEnd)
    segments.push({ kind: 'booked', start: clipped.start, end: clipped.end, booking })
  }

  segments.sort((left, right) => left.start.valueOf() - right.start.valueOf())
  const merged: PreviewSegment[] = []
  for (const segment of segments) {
    const previous = merged[merged.length - 1]
    if (
      previous &&
      segment.kind === 'available' &&
      previous.kind === 'available' &&
      segment.sourceType === previous.sourceType &&
      !segment.start.isAfter(previous.end)
    ) {
      if (segment.end.isAfter(previous.end)) previous.end = segment.end
    } else {
      merged.push(segment)
    }
  }

  return {
    day,
    dayIndex,
    segments: merged,
    unavailable,
    isToday: day.isSame(dayjs(), 'day'),
  }
}

function formatSegmentRange(segment: PreviewSegment, day: Dayjs): string {
  const end = segment.end.isSame(day.add(1, 'day'), 'minute') ? '24:00' : segment.end.format('HH:mm')
  return segment.start.format('HH:mm') + ' – ' + end
}

function segmentLabel(segment: PreviewSegment): string {
  if (segment.kind === 'booked') {
    return segment.booking?.serviceTitle || segment.booking?.customerNickname || '已有订单'
  }
  return segment.sourceType === 'RECURRENCE' ? '固定作息' : '单日接单'
}

function templateDays(dayOfWeek: string): string {
  const days = [...new Set(dayOfWeek.split(',').map(Number).filter((day) => day >= 1 && day <= 7))].sort()
  if (days.length === 5 && days.join(',') === '1,2,3,4,5') return '周一至周五'
  if (days.length === 2 && days.join(',') === '6,7') return '周六、周日'
  return days.map((day) => '周' + WEEKDAY_LABELS[day - 1]).join('、')
}

function templateTime(template: AvailabilityTemplate): string {
  if (template.allDay) return '全天可接单'
  return (template.startTime || '').slice(0, 5) + ' – ' + (template.endTime || '').slice(0, 5)
}

function templateValidity(template: AvailabilityTemplate): string {
  if (template.effectiveUntil) return '截至 ' + dayjs(template.effectiveUntil).format('YYYY年MM月DD日')
  if (dayjs(template.effectiveFrom).isAfter(dayjs(), 'day')) {
    return dayjs(template.effectiveFrom).format('MM月DD日') + '起长期有效'
  }
  return '长期有效'
}

async function load() {
  loading.value = true
  try {
    const data = await getScheduleOverview(
      weekStart.value.format('YYYY-MM-DD 00:00:00'),
      weekStart.value.add(7, 'day').format('YYYY-MM-DD 00:00:00'),
    )
    overview.value = {
      availabilities: data.availabilities || [],
      bookedSlots: data.bookedSlots || [],
    }
  } finally {
    loading.value = false
  }
}

async function loadTemplates() {
  templatesLoading.value = true
  try {
    templates.value = await listAvailabilityTemplates()
  } finally {
    templatesLoading.value = false
  }
}

async function changeWeek(offset: number) {
  weekStart.value = weekStart.value.add(offset, 'day')
  await load()
}

async function thisWeek() {
  weekStart.value = mondayOf()
  await load()
}

function openExceptionDialog(day?: Dayjs) {
  exceptionForm.date = (day || dayjs()).format('YYYY-MM-DD')
  exceptionForm.kind = 'rest'
  exceptionForm.startTime = '10:00'
  exceptionForm.endTime = '13:00'
  exceptionDialogVisible.value = true
}

function validateTimePair(startTime: string, endTime: string, allDay: boolean): boolean {
  if (allDay || startTime !== endTime) return true
  ElMessage.warning('结束时间不能等于开始时间')
  return false
}

async function saveException() {
  if (!exceptionForm.date) {
    ElMessage.warning('请选择日期')
    return
  }
  if (dayjs(exceptionForm.date).isBefore(dayjs().startOf('day'))) {
    ElMessage.warning('只能调整今天及之后的接单时间')
    return
  }
  if (exceptionForm.kind === 'extra' && !validateTimePair(exceptionForm.startTime, exceptionForm.endTime, false)) return

  exceptionSaving.value = true
  try {
    if (exceptionForm.kind === 'rest') {
      await ElMessageBox.confirm(
        dayjs(exceptionForm.date).format('MM月DD日') + '将设为休息；若当天已有待履约订单，系统会阻止这次调整。',
        '确认这天休息',
        { type: 'warning', confirmButtonText: '确认休息', cancelButtonText: '取消' },
      )
      await toggleDayOff({ date: exceptionForm.date, isOff: true, remark: '单日临时休息' })
    } else {
      const startAt = exceptionForm.date + ' ' + exceptionForm.startTime + ':00'
      const startDate = dayjs(exceptionForm.date)
      const endDate = exceptionForm.endTime <= exceptionForm.startTime
        ? startDate.add(1, 'day').format('YYYY-MM-DD')
        : exceptionForm.date
      await createAvailability({
        startAt,
        endAt: endDate + ' ' + exceptionForm.endTime + ':00',
        remark: '单日额外接单时间',
      })
    }

    const selectedDate = dayjs(exceptionForm.date)
    weekStart.value = mondayOf(selectedDate)
    exceptionDialogVisible.value = false
    await Promise.all([load(), loadTemplates()])
    ElMessage.success(exceptionForm.kind === 'rest' ? '这一天已设为休息，长期作息保持不变' : '这段接单时间已添加')
  } catch (error) {
    if (error === 'cancel' || error === 'close') return
    if (!(error instanceof ApiError)) {
      ElMessage.error('保存单日调整失败，请检查时间与已有订单后重试')
    }
  } finally {
    exceptionSaving.value = false
  }
}

function templateForDays(days: number[], fallbackName?: string): AvailabilityTemplate | undefined {
  const target = [...days].sort((left, right) => left - right).join(',')
  return templates.value.find((template) => template.dayOfWeek.split(',').sort().join(',') === target)
    || (fallbackName ? templates.value.find((template) => template.templateName === fallbackName) : undefined)
}

function openTemplateDialog(_existing?: AvailabilityTemplate) {
  const weekdayTemplate = templateForDays([1, 2, 3, 4, 5], '工作日晚上')
  const weekendTemplate = templateForDays([6, 7], '周末白天')
  const weekdayDays = weekdayTemplate?.dayOfWeek.split(',').map(Number) || [1, 2, 3, 4, 5]
  const weekendDays = weekendTemplate?.dayOfWeek.split(',').map(Number) || [6, 7]

  routineForm.weekdays = weekdayDays
  routineForm.weekends = weekendDays
  routineForm.weekdayStart = weekdayTemplate?.startTime?.slice(0, 5) || '19:00'
  routineForm.weekdayEnd = weekdayTemplate?.endTime?.slice(0, 5) || '23:00'
  routineForm.weekendStart = weekendTemplate?.startTime?.slice(0, 5) || '14:00'
  routineForm.weekendEnd = weekendTemplate?.endTime?.slice(0, 5) || '22:00'

  const todayStr = dayjs().format('YYYY-MM-DD')
  const candidateDate = weekdayTemplate?.effectiveFrom || weekendTemplate?.effectiveFrom || todayStr
  routineForm.effectiveFrom = dayjs(candidateDate).isBefore(dayjs().startOf('day')) ? todayStr : candidateDate
  templateDialogVisible.value = true
}

function toggleRoutineDay(group: 'weekdays' | 'weekends', day: number) {
  const days = routineForm[group]
  const index = days.indexOf(day)
  if (index >= 0) {
    days.splice(index, 1)
  } else {
    days.push(day)
  }
  days.sort((left, right) => left - right)
}

async function saveRoutine() {
  if (routineForm.weekdays.length && !validateTimePair(routineForm.weekdayStart, routineForm.weekdayEnd, false)) return
  if (routineForm.weekends.length && !validateTimePair(routineForm.weekendStart, routineForm.weekendEnd, false)) return
  if (!routineForm.weekdays.length && !routineForm.weekends.length) {
    ElMessage.warning('请至少保留一个可接单时段')
    return
  }

  const groups = [
    {
      days: routineForm.weekdays,
      template: templateForDays([1, 2, 3, 4, 5], '工作日晚上'),
      templateName: '工作日晚上',
      startTime: routineForm.weekdayStart,
      endTime: routineForm.weekdayEnd,
    },
    {
      days: routineForm.weekends,
      template: templateForDays([6, 7], '周末白天'),
      templateName: '周末白天',
      startTime: routineForm.weekendStart,
      endTime: routineForm.weekendEnd,
    },
  ]

  const todayStr = dayjs().format('YYYY-MM-DD')
  const effectiveFrom = dayjs(routineForm.effectiveFrom).isBefore(dayjs().startOf('day'))
    ? todayStr
    : (routineForm.effectiveFrom || todayStr)

  templateSaving.value = true
  try {
    for (const group of groups) {
      if (!group.days.length) {
        if (group.template) await deleteAvailabilityTemplate(group.template.id)
        continue
      }

      const payload = {
        templateName: group.templateName,
        dayOfWeek: [...group.days].sort((left, right) => left - right).join(','),
        allDay: false,
        startTime: group.startTime + ':00',
        endTime: group.endTime + ':00',
        effectiveFrom,
        effectiveUntil: undefined,
      }
      if (group.template) {
        await updateAvailabilityTemplate(group.template.id, payload)
      } else {
        await createAvailabilityTemplate(payload)
      }
    }
    templateDialogVisible.value = false
    await Promise.all([load(), loadTemplates()])
    ElMessage.success('长期作息已保存，之后无需每周重复设置')
  } catch (error) {
    if (!(error instanceof ApiError)) {
      ElMessage.error('保存长期作息失败，请检查设置后重试')
    }
  } finally {
    templateSaving.value = false
  }
}

async function toggleTemplate(template: AvailabilityTemplate, enabled: boolean) {
  try {
    await toggleAvailabilityTemplate(template.id, enabled)
    await Promise.all([load(), loadTemplates()])
    ElMessage.success(enabled ? '长期规则已启用' : '长期规则已暂停')
  } catch (error) {
    if (!(error instanceof ApiError)) {
      ElMessage.error('更新长期规则失败')
    }
  }
}

async function deleteTemplate(template: AvailabilityTemplate) {
  try {
    await ElMessageBox.confirm(
      '删除「' + template.templateName + '」会清理未来未被订单占用的接单时间。',
      '删除长期规则',
      { type: 'warning', confirmButtonText: '删除规则', cancelButtonText: '保留规则' },
    )
    await deleteAvailabilityTemplate(template.id)
    await Promise.all([load(), loadTemplates()])
    ElMessage.success('长期规则已删除')
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') {
      if (!(error instanceof ApiError)) {
        ElMessage.error('删除长期规则失败')
      }
    }
  }
}

onMounted(() => {
  void Promise.all([load(), loadTemplates()]).catch(() => {})
})
</script>

<template>
  <div class="availability-page">
    <header class="page-heading">
      <div>
        <h1>接单时间</h1>
        <p>设置一次长期作息，系统会持续安排后续时间；临时变化只调整对应日期。</p>
      </div>
    </header>

    <el-card class="routine-card" shadow="never">
      <div class="section-heading">
        <div>
          <div class="section-title-row">
            <h2>我的固定作息</h2>
            <el-tag v-if="activeTemplates.length" type="success" effect="light" round>长期生效</el-tag>
            <el-tag v-else type="info" effect="light" round>尚未设置</el-tag>
          </div>
        </div>
        <el-button v-if="activeTemplates.length" @click="openTemplateDialog()">编辑长期作息</el-button>
        <el-button v-else type="primary" @click="openTemplateDialog()">设置长期作息</el-button>
      </div>

      <div v-if="activeTemplates.length" class="routine-list">
        <div v-for="template in activeTemplates" :key="template.id" class="routine-row">
          <span class="routine-dot"></span>
          <span class="routine-days">{{ templateDays(template.dayOfWeek) }}</span>
          <div class="routine-time">
            <strong>{{ templateTime(template) }}</strong>
            <span>{{ template.templateName }}</span>
          </div>
          <span class="routine-validity">{{ templateValidity(template) }}</span>
        </div>
      </div>
      <el-empty v-else :image-size="56" description="设置后，系统会按固定作息自动安排接单时间" />

      <div v-if="pausedTemplateCount" class="paused-note">
        另有 {{ pausedTemplateCount }} 条已暂停规则，可在“管理长期作息”中恢复。
      </div>
      <div class="routine-footnote">长期作息会自动向后安排；每周预览仅供查看，不需要逐周修改。</div>
    </el-card>

    <div class="exception-tip">
      <div class="exception-copy"><span class="exception-icon" aria-hidden="true">!</span><span><strong>临时有事或想额外接单？</strong><span>修改某一天不会改变固定作息。</span></span></div>
      <el-button type="primary" plain @click="openExceptionDialog()">调整某一天</el-button>
    </div>

    <section class="preview-section" v-loading="loading">
      <div class="preview-heading">
        <div>
          <h2>未来一周预览</h2>
          <p>核对固定作息、临时调整和已有订单。</p>
        </div>
        <div class="week-navigation">
          <el-button circle aria-label="上一周" @click="changeWeek(-7)">‹</el-button>
          <strong>{{ weekRangeText }}</strong>
          <el-button circle aria-label="下一周" @click="changeWeek(7)">›</el-button>
          <el-button text @click="thisWeek">回到本周</el-button>
        </div>
      </div>

      <div class="day-list">
        <div v-for="item in dayPreviews" :key="item.day.format('YYYY-MM-DD')" class="day-row">
          <div class="day-date" :class="{ today: item.isToday }">
            <strong>周{{ WEEKDAY_LABELS[item.dayIndex] }}</strong>
            <span>{{ item.day.format('MM月DD日') }}</span>
            <el-tag v-if="item.isToday" size="small" type="primary" effect="plain">今天</el-tag>
          </div>
          <div class="day-content">
            <template v-if="item.segments.length">
              <el-tag
                v-for="(segment, index) in item.segments"
                :key="index + '-' + segment.start.format('HH:mm')"
                :type="segment.kind === 'booked' ? 'warning' : 'success'"
                effect="light"
                :class="['time-segment', { booked: segment.kind === 'booked' }]"
              >
                <strong>{{ formatSegmentRange(segment, item.day) }}</strong>
                <span>{{ segmentLabel(segment) }}</span>
              </el-tag>
            </template>
            <el-tag v-else type="info" effect="light">{{ item.unavailable ? '临时休息' : '暂无可接单时间' }}</el-tag>
          </div>
          <div class="day-action">
            <el-button text type="primary" @click="openExceptionDialog(item.day)">调整这一天</el-button>
          </div>
        </div>
      </div>

      <div class="preview-legend">
        <span><i class="legend-dot available"></i>按固定作息可约</span>
        <span><i class="legend-dot booked"></i>已有订单</span>
        <span><i class="legend-dot unavailable"></i>临时休息</span>
      </div>
    </section>

    <el-drawer v-model="rulesDrawerVisible" title="管理长期作息" size="460px">
      <div class="drawer-intro">
        设置一次后会持续生效，系统自动安排后续接单时间。修改规则只会更新未来未被订单占用的时间。
      </div>
      <div class="drawer-actions">
        <el-button type="primary" @click="openTemplateDialog()">添加作息规则</el-button>
      </div>
      <div v-loading="templatesLoading" class="drawer-rules">
        <div v-for="template in templates" :key="template.id" class="drawer-rule">
          <el-switch
            :model-value="template.enabled"
            :aria-label="(template.enabled ? '暂停' : '启用') + template.templateName"
            @change="(value: boolean | string | number) => toggleTemplate(template, Boolean(value))"
          />
          <div class="drawer-rule-main">
            <strong>{{ template.templateName }}</strong>
            <span>{{ templateDays(template.dayOfWeek) }} · {{ templateTime(template) }} · {{ templateValidity(template) }}</span>
          </div>
          <el-button text size="small" @click="openTemplateDialog(template)">编辑</el-button>
          <el-button text type="danger" size="small" @click="deleteTemplate(template)">删除</el-button>
        </div>
        <el-empty v-if="!templates.length" :image-size="56" description="还没有长期作息" />
      </div>
    </el-drawer>

    <el-dialog v-model="templateDialogVisible" class="routine-dialog" width="590px" :close-on-click-modal="false">
      <template #header>
        <div class="routine-dialog-heading">
          <h3>编辑长期作息</h3>
          <p>选择常规可接单的日期和时间，保存后会持续沿用</p>
        </div>
      </template>
      <div class="routine-dialog-groups">
        <section class="routine-group">
          <h4>工作日时段</h4>
          <div class="routine-week-buttons" role="group" aria-label="工作日选择">
            <button
              v-for="day in WEEK_OPTIONS"
              :key="'weekday-' + day.value"
              type="button"
              class="routine-week-button"
              :class="{ selected: routineForm.weekdays.includes(day.value) }"
              :aria-pressed="routineForm.weekdays.includes(day.value)"
              @click="toggleRoutineDay('weekdays', day.value)"
            >
              {{ day.label }}
            </button>
          </div>
          <div class="routine-time-row">
            <el-time-picker v-model="routineForm.weekdayStart" format="HH:mm" value-format="HH:mm" class="routine-time-picker" aria-label="工作日开始时间" />
            <span>至</span>
            <el-time-picker v-model="routineForm.weekdayEnd" format="HH:mm" value-format="HH:mm" class="routine-time-picker" aria-label="工作日结束时间" />
          </div>
        </section>

        <section class="routine-group">
          <h4>周末时段</h4>
          <div class="routine-week-buttons" role="group" aria-label="周末选择">
            <button
              v-for="day in WEEK_OPTIONS"
              :key="'weekend-' + day.value"
              type="button"
              class="routine-week-button"
              :class="{ selected: routineForm.weekends.includes(day.value) }"
              :aria-pressed="routineForm.weekends.includes(day.value)"
              @click="toggleRoutineDay('weekends', day.value)"
            >
              {{ day.label }}
            </button>
          </div>
          <div class="routine-time-row">
            <el-time-picker v-model="routineForm.weekendStart" format="HH:mm" value-format="HH:mm" class="routine-time-picker" aria-label="周末开始时间" />
            <span>至</span>
            <el-time-picker v-model="routineForm.weekendEnd" format="HH:mm" value-format="HH:mm" class="routine-time-picker" aria-label="周末结束时间" />
          </div>
        </section>
      </div>
      <div class="routine-dialog-note">不设置结束日期即长期有效。已确认的订单不会被作息修改覆盖。</div>
      <template #footer>
        <el-button @click="templateDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="templateSaving" @click="saveRoutine">保存作息</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="exceptionDialogVisible" title="临时调整某一天" width="470px">
      <el-form :model="exceptionForm" label-width="84px">
        <el-form-item label="选择日期" required>
          <el-date-picker
            v-model="exceptionForm.date"
            type="date"
            value-format="YYYY-MM-DD"
            :disabled-date="(date: Date) => date.getTime() < dayjs().startOf('day').valueOf()"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="当天安排">
          <el-radio-group v-model="exceptionForm.kind">
            <el-radio value="rest">这天休息</el-radio>
            <el-radio value="extra">额外有空</el-radio>
          </el-radio-group>
        </el-form-item>
        <template v-if="exceptionForm.kind === 'extra'">
          <el-form-item label="开始时间" required>
            <el-time-picker v-model="exceptionForm.startTime" value-format="HH:mm" style="width: 100%" />
          </el-form-item>
          <el-form-item label="结束时间" required>
            <el-time-picker v-model="exceptionForm.endTime" value-format="HH:mm" style="width: 100%" />
            <div v-if="exceptionForm.endTime < exceptionForm.startTime" class="form-tip">结束时间早于开始时间时，按跨天时段处理。</div>
          </el-form-item>
        </template>
      </el-form>
      <div class="form-tip">只影响所选日期，不更改固定作息；有订单的时段会受到保护。</div>
      <template #footer>
        <el-button @click="exceptionDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="exceptionSaving" @click="saveException">保存这一天</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.availability-page {
  --schedule-green: #2ca66f;
  --schedule-green-soft: rgba(44, 166, 111, 0.12);
  --schedule-purple: #7659c9;
  --schedule-purple-soft: rgba(118, 89, 201, 0.12);
  --schedule-orange: #a96a20;
  --schedule-orange-soft: rgba(245, 158, 11, 0.12);
  width: min(100%, 1000px);
  margin: 0 auto;
  color: var(--foreground);
}

.page-heading { display: flex; align-items: flex-end; gap: 18px; margin-bottom: 21px; }
.page-heading h1 { margin: 0; font-size: 24px; font-weight: 500; line-height: 1.25; letter-spacing: -0.3px; }
.page-heading p { margin: 6px 0 0; color: var(--text-secondary); }
.routine-card { overflow: hidden; border-radius: 12px; }
.routine-card :deep(.el-card__body) { padding: 19px 21px 15px; }
.section-heading { display: flex; align-items: center; justify-content: space-between; gap: 16px; }
.section-title-row { display: flex; align-items: center; flex-wrap: wrap; gap: 9px; }
.section-title-row h2, .preview-heading h2 { margin: 0; font-size: 16px; font-weight: 500; }
.routine-list { margin-top: 15px; border-top: 1px solid var(--border); }
.routine-row { display: flex; align-items: center; gap: 10px; min-height: 54px; border-bottom: 1px solid var(--border); }
.routine-dot { width: 8px; height: 8px; flex: 0 0 auto; border-radius: 50%; background: var(--schedule-green); }
.routine-days { width: 145px; flex: 0 0 145px; color: var(--text-secondary); font-size: 13px; }
.routine-time { display: flex; min-width: 0; flex: 1; align-items: baseline; gap: 12px; }
.routine-time strong { font-size: 14px; font-weight: 500; font-variant-numeric: tabular-nums; }
.routine-time span, .routine-validity { color: var(--text-secondary); font-size: 12px; }
.routine-validity { white-space: nowrap; }
.routine-footnote, .paused-note { margin-top: 12px; color: var(--text-secondary); font-size: 12px; }
.paused-note { color: var(--brand-primary); }
.exception-tip { display: flex; align-items: center; justify-content: space-between; gap: 14px; margin: 13px 0 25px; padding: 12px 15px; border-radius: 9px; color: var(--schedule-orange); background: var(--schedule-orange-soft); }
.exception-copy { display: flex; min-width: 0; align-items: center; gap: 9px; }
.exception-copy > span:last-child { display: flex; flex-wrap: wrap; align-items: baseline; gap: 4px 9px; }
.exception-icon { display: grid; width: 17px; height: 17px; flex: 0 0 auto; place-items: center; border: 1px solid currentColor; border-radius: 50%; font-size: 11px; font-weight: 600; }
.exception-tip strong { color: var(--foreground); font-size: 13px; font-weight: 500; }
.exception-copy > span:last-child > span { color: var(--text-secondary); font-size: 12px; }
.preview-section { min-width: 0; }
.preview-heading { display: flex; align-items: center; justify-content: space-between; gap: 16px; margin-bottom: 12px; }
.preview-heading p { margin: 4px 0 0; color: var(--text-secondary); font-size: 12px; }
.week-navigation { display: flex; align-items: center; gap: 7px; }
.week-navigation strong { min-width: 150px; color: var(--text-secondary); font-size: 12px; font-weight: 400; text-align: center; font-variant-numeric: tabular-nums; }
.preview-legend { display: flex; flex-wrap: wrap; gap: 17px; margin-top: 12px; color: var(--text-secondary); font-size: 12px; }
.preview-legend span { display: inline-flex; align-items: center; gap: 6px; }
.legend-dot { width: 8px; height: 8px; border-radius: 3px; }
.legend-dot.available { background: var(--schedule-green); }
.legend-dot.booked { background: var(--schedule-purple); }
.legend-dot.unavailable { background: #aab3c0; }
.day-list { border-top: 1px solid var(--border); }
.day-row { display: grid; grid-template-columns: 145px minmax(0, 1fr) 100px; align-items: center; gap: 14px; min-height: 63px; padding: 8px 2px; border-bottom: 1px solid var(--border); }
.day-date { display: flex; align-items: baseline; gap: 7px; min-width: 0; }
.day-date strong { min-width: 30px; font-size: 13px; font-weight: 500; white-space: nowrap; }
.day-date > span { color: var(--text-secondary); font-size: 12px; white-space: nowrap; }
.day-date.today strong { color: var(--brand-primary); }
.day-content { display: flex; align-items: center; flex-wrap: wrap; gap: 7px; min-width: 0; }
.time-segment { max-width: 100%; height: auto; min-height: 30px; padding: 0 10px; border: 0 !important; border-radius: 6px !important; color: var(--schedule-green) !important; background: var(--schedule-green-soft) !important; font-size: 12px; font-variant-numeric: tabular-nums; }
.time-segment.booked { color: var(--schedule-purple) !important; background: var(--schedule-purple-soft) !important; }
.time-segment strong { margin-right: 6px; font-weight: 500; }
.time-segment span { font-size: 11px; }
.day-action { text-align: right; white-space: nowrap; }
.drawer-intro { margin-bottom: 14px; padding: 11px 12px; border-radius: 8px; color: var(--text-secondary); background: var(--muted); font-size: 12px; line-height: 1.6; }
.drawer-actions { margin-bottom: 12px; }
.drawer-rules { display: flex; flex-direction: column; }
.drawer-rule { display: flex; align-items: center; gap: 8px; padding: 12px 0; border-bottom: 1px solid var(--border); }
.drawer-rule-main { display: flex; min-width: 0; flex: 1; flex-direction: column; gap: 4px; }
.drawer-rule-main strong { font-size: 13px; font-weight: 600; }
.drawer-rule-main span { color: var(--text-secondary); font-size: 12px; line-height: 1.5; }
.form-tip { color: var(--text-secondary); font-size: 12px; line-height: 1.5; }
.routine-dialog :deep(.el-dialog) { overflow: hidden; border-radius: 14px; }
.routine-dialog :deep(.el-dialog__header) { margin-right: 0; padding: 25px 22px 13px; }
.routine-dialog :deep(.el-dialog__body) { padding: 0 22px 20px; }
.routine-dialog :deep(.el-dialog__footer) { padding: 14px 22px 22px; border-top: 1px solid var(--border); }
.routine-dialog-heading h3 { margin: 0; color: var(--foreground); font-size: 18px; font-weight: 600; line-height: 1.35; }
.routine-dialog-heading p { margin: 6px 0 0; color: var(--text-secondary); font-size: 12px; line-height: 1.5; }
.routine-dialog-groups { display: flex; flex-direction: column; gap: 12px; }
.routine-group { padding: 14px 13px 13px; border: 1px solid var(--border); border-radius: 9px; }
.routine-group h4 { margin: 0 0 11px; color: var(--foreground); font-size: 13px; font-weight: 500; }
.routine-week-buttons { display: flex; flex-wrap: wrap; gap: 6px; }
.routine-week-button { min-width: 38px; height: 32px; padding: 0 8px; border: 1px solid var(--border); border-radius: 7px; color: var(--text-secondary); background: transparent; font-size: 12px; cursor: pointer; transition: color .15s ease, border-color .15s ease, background-color .15s ease; }
.routine-week-button:hover { border-color: var(--schedule-purple); color: var(--schedule-purple); }
.routine-week-button.selected { border-color: var(--schedule-purple); color: var(--schedule-purple); background: var(--schedule-purple-soft); }
.routine-time-row { display: flex; align-items: center; gap: 9px; margin-top: 12px; color: var(--text-secondary); font-size: 13px; }
:deep(.routine-time-picker) { width: 90px !important; flex: 0 0 90px; }
:deep(.routine-time-picker .el-input__wrapper) { min-height: 36px; padding: 1px 9px; box-shadow: 0 0 0 1px var(--border) inset; }
.routine-dialog-note { margin-top: 13px; color: var(--text-secondary); font-size: 12px; line-height: 1.5; }

@media (max-width: 900px) {
  .page-heading, .preview-heading { align-items: flex-start; flex-direction: column; }
}
@media (max-width: 640px) {
  .routine-card :deep(.el-card__body) { padding: 15px; }
  .section-heading { align-items: flex-start; flex-direction: column; }
  .routine-days { width: 120px; flex-basis: 120px; }
  .routine-validity { margin-left: 18px; }
  .exception-tip { align-items: flex-start; flex-direction: column; }
  .preview-heading { gap: 12px; }
  .week-navigation { flex-wrap: wrap; }
  .day-row { grid-template-columns: minmax(0, 1fr) auto; gap: 8px 10px; padding: 11px 2px; }
  .day-date { grid-column: 1; flex-wrap: wrap; }
  .day-content { grid-column: 1 / -1; grid-row: 2; }
  .day-action { grid-column: 2; grid-row: 1; }
  .routine-row { align-items: flex-start; flex-wrap: wrap; padding: 11px 0; }
  .routine-dot { margin-top: 5px; }
  .routine-days { width: auto; flex: 0 0 auto; }
  .routine-time { flex-basis: calc(100% - 42px); flex-wrap: wrap; gap: 4px 10px; }
  .routine-validity { width: 100%; margin-left: 18px; }
}
@media (max-width: 560px) {
  .routine-dialog { width: calc(100% - 24px) !important; }
  .routine-dialog :deep(.el-dialog__header) { padding: 20px 17px 12px; }
  .routine-dialog :deep(.el-dialog__body) { padding: 0 17px 17px; }
  .routine-dialog :deep(.el-dialog__footer) { padding: 12px 17px 17px; }
  :deep(.routine-time-picker) { width: 86px !important; flex-basis: 86px; }
}
</style>
