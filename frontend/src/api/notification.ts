import { request, type PageResult } from '@/api/http'

/** 站内通知视图（FR-A07） */
export interface NotificationView {
  id: number
  userId?: number
  notificationType: string
  title: string
  content: string
  relatedType?: string | null
  relatedId?: number | null
  eventKey?: string | null
  readStatus: number // 0: 未读, 1: 已读
  readAt?: string | null
  createdAt: string
}

/** 获取当前用户的通知列表（分页） */
export function myNotifications(page = 1, size = 10, readStatus?: 0 | 1): Promise<PageResult<NotificationView>> {
  return request<PageResult<NotificationView>>({
    url: '/notifications',
    method: 'get',
    params: { page, size, readStatus },
  })
}

/** 获取未读通知数 */
export function unreadNotificationCount(): Promise<{ count: number }> {
  return request<{ count: number }>({
    url: '/notifications/unread-count',
    method: 'get',
  })
}

/** 标记单条通知为已读 */
export function markNotificationRead(id: number): Promise<void> {
  return request<void>({
    url: `/notifications/${id}/read`,
    method: 'put',
  })
}

/** 全部标记为已读 */
export function markAllNotificationsRead(): Promise<void> {
  return request<void>({
    url: '/notifications/read-all',
    method: 'put',
  })
}
