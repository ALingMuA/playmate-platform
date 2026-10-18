import dayjs, { type Dayjs } from 'dayjs'
import type { Availability, AvailabilityTemplate, BookedSlot, ScheduleOverview } from '@/api/companion'

export type SegmentKind = 'available' | 'booked' | 'unavailable'

export interface VisualSegment {
  id: string
  kind: SegmentKind
  startMinute: number // 0 - 1440
  endMinute: number // 0 - 1440
  startTimeText: string // 'HH:mm'
  endTimeText: string // 'HH:mm'
  durationMinutes: number
  leftPercent: number // 0 - 100
  widthPercent: number // 0 - 100
  isExpired: boolean
  isPartiallyExpired: boolean
  expiredWidthPercent?: number
  booking?: BookedSlot
  availability?: Availability
  remark?: string
}

export interface DaySchedule {
  date: string // 'YYYY-MM-DD'
  dayjsObj: Dayjs
  dayOfWeek: number // 1 - 7
  dayName: string // '周一' .. '周日'
  isToday: boolean
  isPast: boolean
  isAllDayOff: boolean
  hasException: boolean
  segments: VisualSegment[]
  availableMinutes: number
  bookedMinutes: number
  unavailableMinutes: number
  summaryText: string
}

/** 辅助区间接口 */
interface RawInterval {
  start: number
  end: number
  kind: SegmentKind
  booking?: BookedSlot
  availability?: Availability
  remark?: string
}

/** 将 HH:mm 或 HH:mm:ss 转换为自 00:00 起的分钟数 (0-1440) */
export function timeStringToMinutes(timeStr: string): number {
  const parts = timeStr.split(':')
  const h = parseInt(parts[0] || '0', 10)
  const m = parseInt(parts[1] || '0', 10)
  return Math.min(1440, Math.max(0, h * 60 + m))
}

/** 将分钟数 (0-1440) 格式化为 HH:mm */
export function minutesToTimeString(minutes: number): string {
  if (minutes >= 1440) return '24:00'
  const clamped = Math.max(0, minutes)
  const h = Math.floor(clamped / 60)
  const m = clamped % 60
  return `${h.toString().padStart(2, '0')}:${m.toString().padStart(2, '0')}`
}

/** 格式化时长为易读文案（例如：2小时、1.5小时、45分钟） */
export function formatMinutes(minutes: number): string {
  if (minutes <= 0) return '0分钟'
  const hours = minutes / 60
  if (Number.isInteger(hours)) {
    return `${hours}小时`
  }
  if (hours >= 1) {
    return `${hours.toFixed(1).replace(/\.0$/, '')}小时`
  }
  return `${minutes}分钟`
}

/** 计算日期与区间的重叠分钟范围 [0, 1440] */
export function clipIntervalToDay(
  startAtStr: string,
  endAtStr: string,
  dayDate: Dayjs,
): { startMinute: number; endMinute: number } | null {
  const dayStart = dayDate.startOf('day')
  const dayEnd = dayDate.add(1, 'day').startOf('day')

  const startAt = dayjs(startAtStr)
  const endAt = dayjs(endAtStr)

  if (!startAt.isBefore(dayEnd) || !endAt.isAfter(dayStart)) {
    return null
  }

  const effectiveStart = startAt.isBefore(dayStart) ? dayStart : startAt
  const effectiveEnd = endAt.isAfter(dayEnd) ? dayEnd : endAt

  const startMin = Math.max(0, effectiveStart.diff(dayStart, 'minute'))
  const endMin = Math.min(1440, effectiveEnd.diff(dayStart, 'minute'))

  if (endMin <= startMin) return null
  return { startMinute: startMin, endMinute: endMin }
}

/**
 * 核心：计算一天的三态排期片段
 * 规则：
 * 1. BookedSlot（已约）最高优先级，直接占据对应分钟区间；
 * 2. Unavailable（不可约/休息）次高，占据对应区间；
 * 3. Available（可约）被已约和不可约扣减（挖空），剩余有效可约区间；
 * 4. 结合 serverNow 判定是否已过期（今日过去的时刻或过去的天）。
 */
export function buildDaySchedule(
  dayDate: Dayjs,
  overview: ScheduleOverview,
  serverNow: Dayjs = dayjs(),
  _templates: AvailabilityTemplate[] = [],
): DaySchedule {
  const dateStr = dayDate.format('YYYY-MM-DD')
  const dayOfWeek = dayDate.day() === 0 ? 7 : dayDate.day()
  const weekNames = ['一', '二', '三', '四', '五', '六', '日']
  const dayName = `周${weekNames[dayOfWeek - 1]}`

  const isToday = dayDate.isSame(serverNow, 'day')
  const isPast = dayDate.isBefore(serverNow, 'day')
  const currentMinute = isToday ? serverNow.hour() * 60 + serverNow.minute() : isPast ? 1440 : 0

  // 1. 搜集该日的 bookedSlots
  const bookedIntervals: RawInterval[] = []
  for (const b of overview.bookedSlots || []) {
    const clipped = clipIntervalToDay(b.startAt, b.endAt, dayDate)
    if (clipped) {
      bookedIntervals.push({
        start: clipped.startMinute,
        end: clipped.endMinute,
        kind: 'booked',
        booking: b,
        remark: b.serviceTitle,
      })
    }
  }

  // 2. 搜集该日的 availabilities (AVAILABLE & UNAVAILABLE)
  const availableIntervals: RawInterval[] = []
  const unavailableIntervals: RawInterval[] = []
  let hasException = false
  let isAllDayOff = false

  for (const a of overview.availabilities || []) {
    const clipped = clipIntervalToDay(a.startAt, a.endAt, dayDate)
    if (!clipped) continue

    if (a.sourceType === 'MANUAL') {
      hasException = true
    }

    if (a.availabilityStatus === 'UNAVAILABLE') {
      unavailableIntervals.push({
        start: clipped.startMinute,
        end: clipped.endMinute,
        kind: 'unavailable',
        availability: a,
        remark: a.remark || '休息',
      })
      if (clipped.startMinute <= 30 && clipped.endMinute >= 1410) {
        isAllDayOff = true
      }
    } else {
      availableIntervals.push({
        start: clipped.startMinute,
        end: clipped.endMinute,
        kind: 'available',
        availability: a,
        remark: a.remark,
      })
    }
  }

  // 3. 挖空算法：将 availableIntervals 被 bookedIntervals 和 unavailableIntervals 挖掉
  const blockerIntervals = [...bookedIntervals, ...unavailableIntervals].sort((a, b) => a.start - b.start)
  const carvedAvailable: RawInterval[] = []

  for (const av of availableIntervals) {
    let currentRanges: Array<{ start: number; end: number }> = [{ start: av.start, end: av.end }]
    for (const blocker of blockerIntervals) {
      const nextRanges: Array<{ start: number; end: number }> = []
      for (const range of currentRanges) {
        if (blocker.end <= range.start || blocker.start >= range.end) {
          // 无交集
          nextRanges.push(range)
        } else {
          // 有交集，挖空
          if (range.start < blocker.start) {
            nextRanges.push({ start: range.start, end: blocker.start })
          }
          if (range.end > blocker.end) {
            nextRanges.push({ start: blocker.end, end: range.end })
          }
        }
      }
      currentRanges = nextRanges
    }

    for (const r of currentRanges) {
      if (r.end > r.start) {
        carvedAvailable.push({
          start: r.start,
          end: r.end,
          kind: 'available',
          availability: av.availability,
          remark: av.remark,
        })
      }
    }
  }

  // 4. 汇总所有片段并排序
  const allRaw = [...bookedIntervals, ...unavailableIntervals, ...carvedAvailable].sort((a, b) => a.start - b.start)

  // 5. 构建渲染用 VisualSegment
  const segments: VisualSegment[] = []
  let totalAvailableMins = 0
  let totalBookedMins = 0
  let totalUnavailableMins = 0

  for (let i = 0; i < allRaw.length; i++) {
    const raw = allRaw[i]!
    const dur = raw.end - raw.start
    if (dur <= 0) continue

    if (raw.kind === 'available') totalAvailableMins += dur
    if (raw.kind === 'booked') totalBookedMins += dur
    if (raw.kind === 'unavailable') totalUnavailableMins += dur

    const isExpiredSeg = isPast || (isToday && raw.end <= currentMinute)
    const isPartial = isToday && raw.start < currentMinute && raw.end > currentMinute
    let expiredWidthPercent: number | undefined

    if (isPartial) {
      const expiredMins = currentMinute - raw.start
      expiredWidthPercent = Math.min(100, (expiredMins / dur) * 100)
    }

    segments.push({
      id: `${dateStr}_${raw.kind}_${raw.start}_${raw.end}_${i}`,
      kind: raw.kind,
      startMinute: raw.start,
      endMinute: raw.end,
      startTimeText: minutesToTimeString(raw.start),
      endTimeText: minutesToTimeString(raw.end),
      durationMinutes: dur,
      leftPercent: (raw.start / 1440) * 100,
      widthPercent: (dur / 1440) * 100,
      isExpired: isExpiredSeg,
      isPartiallyExpired: isPartial,
      expiredWidthPercent,
      booking: raw.booking,
      availability: raw.availability,
      remark: raw.remark,
    })
  }

  // 6. 生成汇总文案
  let summaryText = '未安排'
  if (isAllDayOff) {
    summaryText = '全天休息'
  } else {
    const parts: string[] = []
    if (totalBookedMins > 0) parts.push(`已约 ${formatMinutes(totalBookedMins)}`)
    if (totalAvailableMins > 0) parts.push(`可约 ${formatMinutes(totalAvailableMins)}`)
    if (totalUnavailableMins > 0) parts.push(`休息 ${formatMinutes(totalUnavailableMins)}`)
    summaryText = parts.length > 0 ? parts.join(' · ') : '未安排'
  }

  return {
    date: dateStr,
    dayjsObj: dayDate,
    dayOfWeek,
    dayName,
    isToday,
    isPast,
    isAllDayOff,
    hasException,
    segments,
    availableMinutes: totalAvailableMins,
    bookedMinutes: totalBookedMins,
    unavailableMinutes: totalUnavailableMins,
    summaryText,
  }
}

/** 规范化日期区间，防止超出窗口（从今日起最多 28 天） */
export function clampAnchorDate(anchor: Dayjs, today: Dayjs = dayjs()): Dayjs {
  const minDate = today.startOf('day')
  const maxDate = today.add(28 - 7, 'day').startOf('day') // 保证 7 天窗口在 28 天内

  if (anchor.isBefore(minDate)) return minDate
  if (anchor.isAfter(maxDate)) return maxDate
  return anchor.startOf('day')
}

/** 获取从 anchorDate 起始的 7 天 Dayjs 数组 */
export function getWeekDays(anchorDate: Dayjs): Dayjs[] {
  return Array.from({ length: 7 }, (_, i) => anchorDate.add(i, 'day').startOf('day'))
}

/** 格式化周导航标题，例如：2026年10月18日 – 10月24日 */
export function formatWeekTitle(startDay: Dayjs, endDay: Dayjs): string {
  if (startDay.year() === endDay.year()) {
    return `${startDay.format('YYYY年MM月DD日')} – ${endDay.format('MM月DD日')}`
  }
  return `${startDay.format('YYYY年MM月DD日')} – ${endDay.format('YYYY年MM月DD日')}`
}
