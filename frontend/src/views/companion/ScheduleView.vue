<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import dayjs from 'dayjs'
import {
  createAvailability,
  createUnavailable,
  deleteAvailability,
  listAvailabilities,
  type Availability,
} from '@/api/companion'

const items = ref<Availability[]>([])
const loading = ref(false)
const weekStart = ref(dayjs().startOf('week').add(1, 'day')) // 周一
const weekDays = computed(() => Array.from({ length: 7 }, (_, i) => weekStart.value.add(i, 'day')))

const dialogVisible = ref(false)
const mode = ref<'AVAILABLE' | 'UNAVAILABLE'>('AVAILABLE')
const saving = ref(false)
const form = reactive({
  date: '',
  startTime: '10:00',
  endTime: '22:00',
  remark: '',
})

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

function openCreate(type: 'AVAILABLE' | 'UNAVAILABLE') {
  mode.value = type
  form.date = dayjs().format('YYYY-MM-DD')
  form.startTime = '10:00'
  form.endTime = '22:00'
  form.remark = ''
  dialogVisible.value = true
}

async function handleCreate() {
  if (!form.date || !form.startTime || !form.endTime) {
    ElMessage.warning('请选择日期与时间范围')
    return
  }
  if (form.startTime >= form.endTime) {
    ElMessage.warning('结束时间必须晚于开始时间')
    return
  }
  const payload = {
    startAt: dayjs(form.date + ' ' + form.startTime + ':00').format('YYYY-MM-DD HH:mm:ss'),
    endAt: dayjs(form.date + ' ' + form.endTime + ':00').format('YYYY-MM-DD HH:mm:ss'),
    remark: form.remark || undefined,
  }
  saving.value = true
  try {
    if (mode.value === 'AVAILABLE') {
      await createAvailability(payload)
      ElMessage.success('可约时段已添加')
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

async function handleDelete(item: Availability) {
  await ElMessageBox.confirm('确定删除该档期记录？', '删除确认', { type: 'warning' })
  await deleteAvailability(item.id)
  ElMessage.success('已删除')
  await load()
}

function itemsOf(day: dayjs.Dayjs): Availability[] {
  const dayStr = day.format('YYYY-MM-DD')
  return items.value.filter((i) => i.startAt.startsWith(dayStr))
}

function fmt(time: string): string {
  return time.length >= 16 ? time.slice(11, 16) : time
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

onMounted(load)
</script>

<template>
  <div class="schedule-view">
    <div class="toolbar">
      <el-button type="primary" @click="openCreate('AVAILABLE')">新增可约时段</el-button>
      <el-button type="warning" @click="openCreate('UNAVAILABLE')">设置临时不可约</el-button>
      <div class="week-nav">
        <el-button text @click="prevWeek">‹ 上一周</el-button>
        <el-button text @click="thisWeek">本周</el-button>
        <el-button text @click="nextWeek">下一周 ›</el-button>
        <span class="week-range">
          {{ weekStart.format('YYYY-MM-DD') }} ~ {{ weekStart.add(6, 'day').format('YYYY-MM-DD') }}
        </span>
      </div>
    </div>

    <el-row :gutter="12" v-loading="loading">
      <el-col v-for="day in weekDays" :key="day.format('YYYY-MM-DD')" :span="24 / 7" class="day-col">
        <el-card shadow="hover" class="day-card" :class="{ today: day.isSame(dayjs(), 'day') }">
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
              <span class="slot-time">{{ fmt(item.startAt) }} - {{ fmt(item.endAt) }}</span>
              <el-button size="small" text type="danger" @click="handleDelete(item)">删</el-button>
              <div v-if="item.remark" class="slot-remark">{{ item.remark }}</div>
            </div>
            <el-empty v-if="itemsOf(day).length === 0" description="无档期" :image-size="40" />
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-dialog v-model="dialogVisible" :title="mode === 'AVAILABLE' ? '新增可约时段' : '设置临时不可约'" width="440px">
      <el-form :model="form" label-width="90px">
        <el-form-item label="日期" required>
          <el-date-picker v-model="form.date" type="date" value-format="YYYY-MM-DD" placeholder="选择日期" style="width: 100%" />
        </el-form-item>
        <el-form-item label="开始时间" required>
          <el-time-picker v-model="form.startTime" value-format="HH:mm" placeholder="开始时间" style="width: 100%" />
        </el-form-item>
        <el-form-item label="结束时间" required>
          <el-time-picker v-model="form.endTime" value-format="HH:mm" placeholder="结束时间" style="width: 100%" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" maxlength="200" placeholder="选填，如：晚上有课" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="handleCreate">确定</el-button>
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
}
.week-nav {
  margin-left: auto;
  display: flex;
  align-items: center;
}
.week-range {
  color: #909399;
  font-size: 13px;
}
.day-col {
  min-width: 130px;
}
.day-card.today {
  border-color: #409eff;
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
