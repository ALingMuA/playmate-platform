import { computed, ref } from 'vue'
import { ElMessage } from 'element-plus'
import dayjs, { type Dayjs } from 'dayjs'
import {
  createAvailability,
  createAvailabilityTemplate,
  createUnavailable,
  deleteAvailability,
  deleteAvailabilityTemplate,
  getScheduleOverview,
  listAvailabilityTemplates,
  restoreDay,
  toggleAvailabilityTemplate,
  toggleDayOff,
  updateAvailabilityTemplate,
  type AvailabilityTemplate,
  type AvailabilityTemplatePayload,
  type RestoreDayResult,
  type ScheduleOverview,
} from '@/api/companion'
import {
  buildDaySchedule,
  clampAnchorDate,
  formatWeekTitle,
  getWeekDays,
  type DaySchedule,
} from '@/utils/schedule'

export function useSchedule() {
  const today = dayjs().startOf('day')
  const anchorDate = ref<Dayjs>(today)
  const loading = ref(false)
  const templatesLoading = ref(false)
  const saving = ref(false)

  const overview = ref<ScheduleOverview>({
    availabilities: [],
    bookedSlots: [],
  })
  const templates = ref<AvailabilityTemplate[]>([])

  // 服务器时间或本地时间
  const serverNow = computed<Dayjs>(() => {
    return overview.value.serverNow ? dayjs(overview.value.serverNow) : dayjs()
  })

  // 已滚动安排截止日期
  const generatedUntil = computed<string | undefined>(() => {
    return overview.value.generatedUntil || undefined
  })

  // 从 anchorDate 起始的 7 天
  const weekDays = computed<Dayjs[]>(() => getWeekDays(anchorDate.value))

  // 标题文本
  const weekTitle = computed<string>(() => {
    const days = weekDays.value
    if (days.length < 7) return ''
    return formatWeekTitle(days[0]!, days[6]!)
  })

  // 7 天全景计算列表
  const daySchedules = computed<DaySchedule[]>(() => {
    return weekDays.value.map((d) => buildDaySchedule(d, overview.value, serverNow.value, templates.value))
  })

  // 常规作息模板（单模板优先）
  const activeTemplate = computed<AvailabilityTemplate | undefined>(() => {
    return templates.value.find((t) => t.enabled) || templates.value[0]
  })

  // 导航限制：范围限定在 [今日, 今日 + 28天]
  const canPrev = computed<boolean>(() => {
    return anchorDate.value.isAfter(today)
  })

  const canNext = computed<boolean>(() => {
    // 保证 anchorDate + 6 不超过 today + 28
    return anchorDate.value.isBefore(today.add(28 - 7, 'day'))
  })

  const isCurrentWeek = computed<boolean>(() => {
    return anchorDate.value.isSame(today, 'day')
  })

  // 加载 7 天概览
  async function fetchSchedule() {
    loading.value = true
    try {
      const days = weekDays.value
      const startAt = days[0]!.startOf('day').format('YYYY-MM-DD HH:mm:ss')
      const endAt = days[6]!.endOf('day').format('YYYY-MM-DD HH:mm:ss')
      const res = await getScheduleOverview(startAt, endAt)
      overview.value = res
    } catch (e: any) {
      ElMessage.error(e?.message || '加载周日程失败')
    } finally {
      loading.value = false
    }
  }

  // 加载周期模板
  async function fetchTemplates() {
    templatesLoading.value = true
    try {
      templates.value = await listAvailabilityTemplates()
    } catch (e: any) {
      ElMessage.error(e?.message || '加载作息模板失败')
    } finally {
      templatesLoading.value = false
    }
  }

  // 刷新所有数据
  async function refresh() {
    await Promise.all([fetchSchedule(), fetchTemplates()])
  }

  // 导航
  function prevWeek() {
    if (!canPrev.value) return
    anchorDate.value = clampAnchorDate(anchorDate.value.subtract(7, 'day'), today)
    fetchSchedule()
  }

  function nextWeek() {
    if (!canNext.value) return
    anchorDate.value = clampAnchorDate(anchorDate.value.add(7, 'day'), today)
    fetchSchedule()
  }

  function jumpToToday() {
    anchorDate.value = today
    fetchSchedule()
  }

  // 一键切换整天休息/可约
  async function setDayOff(dateStr: string, isOff: boolean, remark?: string) {
    saving.value = true
    try {
      await toggleDayOff({ date: dateStr, isOff, remark })
      ElMessage.success(isOff ? '已设为整天休息' : '已设为整天可约')
      await fetchSchedule()
      return true
    } catch (e: any) {
      ElMessage.error(e?.message || '设置失败')
      return false
    } finally {
      saving.value = false
    }
  }

  // 单日恢复常规作息
  async function restoreSingleDay(dateStr: string): Promise<RestoreDayResult | null> {
    saving.value = true
    try {
      const res = await restoreDay(dateStr)
      ElMessage.success('已恢复常规作息')
      await fetchSchedule()
      return res
    } catch (e: any) {
      ElMessage.error(e?.message || '恢复失败')
      return null
    } finally {
      saving.value = false
    }
  }

  // 新增临时可约时段
  async function addSlot(startAt: string, endAt: string, remark?: string) {
    saving.value = true
    try {
      await createAvailability({ startAt, endAt, remark })
      ElMessage.success('已添加可约时段')
      await fetchSchedule()
      return true
    } catch (e: any) {
      ElMessage.error(e?.message || '添加失败')
      return false
    } finally {
      saving.value = false
    }
  }

  // 新增临时不可约时段
  async function addUnavailable(startAt: string, endAt: string, remark?: string) {
    saving.value = true
    try {
      await createUnavailable({ startAt, endAt, remark })
      ElMessage.success('已标记不可约时段')
      await fetchSchedule()
      return true
    } catch (e: any) {
      ElMessage.error(e?.message || '标记失败')
      return false
    } finally {
      saving.value = false
    }
  }

  // 删除档期
  async function removeSlot(id: number) {
    saving.value = true
    try {
      await deleteAvailability(id)
      ElMessage.success('已删除档期')
      await fetchSchedule()
      return true
    } catch (e: any) {
      ElMessage.error(e?.message || '删除失败')
      return false
    } finally {
      saving.value = false
    }
  }

  // 保存作息模板（创建或编辑）
  async function saveTemplate(payload: AvailabilityTemplatePayload, id?: number) {
    saving.value = true
    try {
      if (id) {
        await updateAvailabilityTemplate(id, payload)
        ElMessage.success('作息模板已更新')
      } else {
        await createAvailabilityTemplate(payload)
        ElMessage.success('作息模板创建成功')
      }
      await refresh()
      return true
    } catch (e: any) {
      ElMessage.error(e?.message || '保存失败')
      return false
    } finally {
      saving.value = false
    }
  }

  // 启停作息模板
  async function toggleTemplate(id: number, enabled: boolean) {
    saving.value = true
    try {
      await toggleAvailabilityTemplate(id, enabled)
      ElMessage.success(enabled ? '已启用作息模板' : '已暂停作息模板')
      await refresh()
      return true
    } catch (e: any) {
      ElMessage.error(e?.message || '操作失败')
      return false
    } finally {
      saving.value = false
    }
  }

  // 删除模板
  async function removeTemplate(id: number) {
    saving.value = true
    try {
      await deleteAvailabilityTemplate(id)
      ElMessage.success('作息模板已删除')
      await refresh()
      return true
    } catch (e: any) {
      ElMessage.error(e?.message || '删除失败')
      return false
    } finally {
      saving.value = false
    }
  }

  return {
    today,
    anchorDate,
    loading,
    templatesLoading,
    saving,
    overview,
    templates,
    serverNow,
    generatedUntil,
    weekDays,
    weekTitle,
    daySchedules,
    activeTemplate,
    canPrev,
    canNext,
    isCurrentWeek,
    fetchSchedule,
    fetchTemplates,
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
  }
}
