import { request, type PageResult } from '@/api/http'

export interface ActivityView {
  id: number
  title: string
  subtitle: string
  description: string
  bannerImage: string
  bgGradient: string
  icon: string
  tag: string
  tagColor: string
  actionType: 'COUPON_CLAIM' | 'COMPANION_LIST' | 'PAGE_NAV'
  actionRoute: string
  couponTemplateId?: number
  couponTemplateName?: string
  sortOrder: number
  status: 'DRAFT' | 'PUBLISHED' | 'OFFLINE'
  startTime?: string
  endTime?: string
  createdBy: number
  createdAt: string
}

export interface ActivityForm {
  title: string
  subtitle: string
  description: string
  bannerImage: string
  bgGradient: string
  icon: string
  tag: string
  tagColor: string
  actionType: string
  actionRoute: string
  couponTemplateId?: number
  sortOrder: number
  startTime?: string
  endTime?: string
}

// ==================== 用户端公开接口 ====================

export function publicActivities(): Promise<ActivityView[]> {
  return request<ActivityView[]>({ url: '/activities' })
}

export function publicActivityDetail(id: number): Promise<ActivityView> {
  return request<ActivityView>({ url: `/activities/${id}` })
}

// ==================== 管理端接口 ====================

export function adminActivities(status?: string, page = 1, size = 10): Promise<PageResult<ActivityView>> {
  return request<PageResult<ActivityView>>({
    url: '/admin/activities',
    params: { status: status || undefined, page, size },
  })
}

export function adminCreateActivity(data: ActivityForm): Promise<ActivityView> {
  return request<ActivityView>({ url: '/admin/activities', method: 'post', data })
}

export function adminUpdateActivity(id: number, data: ActivityForm): Promise<ActivityView> {
  return request<ActivityView>({ url: `/admin/activities/${id}`, method: 'put', data })
}

export function adminPublishActivity(id: number): Promise<ActivityView> {
  return request<ActivityView>({ url: `/admin/activities/${id}/publish`, method: 'post' })
}

export function adminOfflineActivity(id: number): Promise<ActivityView> {
  return request<ActivityView>({ url: `/admin/activities/${id}/offline`, method: 'post' })
}

export function adminDeleteActivity(id: number): Promise<void> {
  return request<void>({ url: `/admin/activities/${id}`, method: 'delete' })
}
