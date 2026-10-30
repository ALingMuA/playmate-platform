/**
 * E2E Test Harness for Unified Message Center (/messages)
 * Provides deterministic browser polyfills, in-memory router, Pinia store management,
 * and HTTP mock adapter for end-to-end testing across Tiers 1-4.
 */

// 1. Browser environment polyfills
const storageMap = new Map<string, string>()

export const mockLocalStorage = {
  getItem: (key: string) => storageMap.get(key) ?? null,
  setItem: (key: string, value: string) => storageMap.set(key, String(value)),
  removeItem: (key: string) => storageMap.delete(key),
  clear: () => storageMap.clear(),
  get length() { return storageMap.size },
  key: (index: number) => Array.from(storageMap.keys())[index] ?? null
}

if (!globalThis.localStorage) {
  Object.defineProperty(globalThis, 'localStorage', {
    value: mockLocalStorage,
    writable: true,
    configurable: true
  })
}

if (!globalThis.document) {
  Object.defineProperty(globalThis, 'document', {
    value: {
      title: '',
      head: { appendChild() {} },
      createElement: () => ({})
    },
    writable: true,
    configurable: true
  })
}

if (!globalThis.window) {
  Object.defineProperty(globalThis, 'window', {
    value: globalThis,
    writable: true,
    configurable: true
  })
}

import { createPinia, setActivePinia } from 'pinia'
import { createRouter, createMemoryHistory, type RouteRecordRaw, type Router } from 'vue-router'
import http from '@/api/http'
import type { NotificationView } from '@/api/notification'
import type { AnnouncementView } from '@/api/admin'
import { useUserStore } from '@/stores/user'
import { useNotificationStore } from '@/stores/notification'

// 2. Mock Data State Store
export interface MockDataState {
  notifications: NotificationView[]
  announcements: AnnouncementView[]
  forceNotificationError: boolean
  forceMarkReadError: boolean
  forceMarkAllError: boolean
  forceAnnouncementError: boolean
  apiCallLogs: { url: string; method: string; params?: any; data?: any }[]
}

export const mockState: MockDataState = {
  notifications: [],
  announcements: [],
  forceNotificationError: false,
  forceMarkReadError: false,
  forceMarkAllError: false,
  forceAnnouncementError: false,
  apiCallLogs: []
}

// 3. HTTP Mock Adapter registration
http.defaults.adapter = async (config) => {
  const url = (config.url || '').replace(/^\/api/, '')
  const method = (config.method || 'get').toLowerCase()
  mockState.apiCallLogs.push({ url, method, params: config.params, data: config.data })

  // Notifications List
  if (url === '/notifications' && method === 'get') {
    if (mockState.forceNotificationError) {
      return {
        data: { code: 'INTERNAL_ERROR', message: 'Failed to fetch notifications', data: null },
        status: 500,
        statusText: 'Internal Server Error',
        headers: {},
        config
      }
    }
    const page = Number(config.params?.page ?? 1)
    const size = Number(config.params?.size ?? 10)
    const start = (page - 1) * size
    const pagedRecords = mockState.notifications.slice(start, start + size)
    return {
      data: {
        code: 'SUCCESS',
        message: 'ok',
        data: {
          records: pagedRecords,
          total: mockState.notifications.length,
          size,
          current: page
        }
      },
      status: 200,
      statusText: 'OK',
      headers: {},
      config
    }
  }

  // Unread count
  if (url === '/notifications/unread-count' && method === 'get') {
    const unread = mockState.notifications.filter(n => n.readStatus === 0).length
    return {
      data: {
        code: 'SUCCESS',
        message: 'ok',
        data: { count: unread }
      },
      status: 200,
      statusText: 'OK',
      headers: {},
      config
    }
  }

  // Mark single read
  const markReadMatch = url.match(/^\/notifications\/(\d+)\/read$/)
  if (markReadMatch && method === 'put') {
    if (mockState.forceMarkReadError) {
      return {
        data: { code: 'INTERNAL_ERROR', message: 'Failed to mark read', data: null },
        status: 500,
        statusText: 'Internal Server Error',
        headers: {},
        config
      }
    }
    const id = Number(markReadMatch[1])
    const item = mockState.notifications.find(n => n.id === id)
    if (item) {
      item.readStatus = 1
      item.readAt = new Date().toISOString()
    }
    return {
      data: { code: 'SUCCESS', message: 'ok', data: null },
      status: 200,
      statusText: 'OK',
      headers: {},
      config
    }
  }

  // Mark all read
  if (url === '/notifications/read-all' && method === 'put') {
    if (mockState.forceMarkAllError) {
      return {
        data: { code: 'INTERNAL_ERROR', message: 'Failed to mark all read', data: null },
        status: 500,
        statusText: 'Internal Server Error',
        headers: {},
        config
      }
    }
    const now = new Date().toISOString()
    for (const item of mockState.notifications) {
      item.readStatus = 1
      item.readAt = now
    }
    return {
      data: { code: 'SUCCESS', message: 'ok', data: null },
      status: 200,
      statusText: 'OK',
      headers: {},
      config
    }
  }

  // Announcements List (Public)
  if (url === '/announcements' && method === 'get') {
    if (mockState.forceAnnouncementError) {
      return {
        data: { code: 'INTERNAL_ERROR', message: 'Failed to fetch announcements', data: null },
        status: 500,
        statusText: 'Internal Server Error',
        headers: {},
        config
      }
    }
    const page = Number(config.params?.page ?? 1)
    const size = Number(config.params?.size ?? 10)
    const published = mockState.announcements.filter(a => a.status === 'PUBLISHED')
    const start = (page - 1) * size
    const pagedRecords = published.slice(start, start + size)
    return {
      data: {
        code: 'SUCCESS',
        message: 'ok',
        data: {
          records: pagedRecords,
          total: published.length,
          size,
          current: page
        }
      },
      status: 200,
      statusText: 'OK',
      headers: {},
      config
    }
  }

  // Announcement Detail (Public)
  const detailMatch = url.match(/^\/announcements\/(\d+)$/)
  if (detailMatch && method === 'get') {
    const id = Number(detailMatch[1])
    const found = mockState.announcements.find(a => a.id === id && a.status === 'PUBLISHED')
    if (!found) {
      return {
        data: { code: 'ANNOUNCEMENT_NOT_FOUND', message: '该公告不存在或已被撤回', data: null },
        status: 404,
        statusText: 'Not Found',
        headers: {},
        config
      }
    }
    return {
      data: { code: 'SUCCESS', message: 'ok', data: found },
      status: 200,
      statusText: 'OK',
      headers: {},
      config
    }
  }

  // Default fallback 200
  return {
    data: { code: 'SUCCESS', message: 'ok', data: null },
    status: 200,
    statusText: 'OK',
    headers: {},
    config
  }
}

// 4. Default Seed Generator
export function createDefaultSeedData() {
  const notifications: NotificationView[] = [
    {
      id: 101,
      userId: 1,
      notificationType: 'ORDER_STATUS',
      title: '订单完成提醒',
      content: '您的陪玩订单 #1001 已完成，请对陪玩师进行评价。',
      relatedType: 'ORDER',
      relatedId: 1001,
      eventKey: 'order_1001',
      readStatus: 0,
      readAt: null,
      createdAt: '2026-10-29 10:00:00'
    },
    {
      id: 102,
      userId: 1,
      notificationType: 'COMPLAINT_RESULT',
      title: '投诉处理进度通知',
      content: '您提交的投诉 #2001 客服已做出仲裁，请查阅详情。',
      relatedType: 'COMPLAINT',
      relatedId: 2001,
      eventKey: 'complaint_2001',
      readStatus: 0,
      readAt: null,
      createdAt: '2026-10-29 09:30:00'
    },
    {
      id: 103,
      userId: 1,
      notificationType: 'SYSTEM',
      title: '新活动公告发布',
      content: '平台已发布全新周末狂欢公告，快来看看吧！',
      relatedType: 'ANNOUNCEMENT',
      relatedId: 501,
      eventKey: 'announcement_501',
      readStatus: 1,
      readAt: '2026-10-29 08:45:00',
      createdAt: '2026-10-29 08:30:00'
    }
  ]

  const announcements: AnnouncementView[] = [
    {
      id: 501,
      title: '游戏陪玩平台 2.0 全新版本上线公告',
      content: '亲爱的用户：平台统一消息中心正式整合上线，欢迎体验！',
      status: 'PUBLISHED',
      publisherId: 99,
      publishedAt: '2026-10-29 08:00:00',
      revokedAt: null,
      createdAt: '2026-10-29 07:50:00',
      updatedAt: '2026-10-29 08:00:00'
    },
    {
      id: 502,
      title: '关于服务器维护升级的温馨提示',
      content: '本周五凌晨 02:00-04:00 进行例行维护，请各位玩家提前下线。',
      status: 'PUBLISHED',
      publisherId: 99,
      publishedAt: '2026-10-28 15:00:00',
      revokedAt: null,
      createdAt: '2026-10-28 14:00:00',
      updatedAt: '2026-10-28 15:00:00'
    },
    {
      id: 503,
      title: '已撤回的历史公告（内部测试）',
      content: '该公告已被撤回，普通用户不可见。',
      status: 'REVOKED',
      publisherId: 99,
      publishedAt: '2026-10-20 10:00:00',
      revokedAt: '2026-10-21 10:00:00',
      createdAt: '2026-10-20 09:00:00',
      updatedAt: '2026-10-21 10:00:00'
    }
  ]

  return { notifications, announcements }
}

// 5. Test Context Setup
export interface TestContextOptions {
  isLoggedIn?: boolean
  token?: string
  seedData?: { notifications?: NotificationView[]; announcements?: AnnouncementView[] }
}

export function createTestRouter(): Router {
  const routes: RouteRecordRaw[] = [
    {
      path: '/',
      name: 'user-home',
      component: { template: '<div>Home</div>' },
      meta: { title: '首页' }
    },
    {
      path: '/login',
      name: 'login',
      component: { template: '<div>Login</div>' },
      meta: { title: '登录' }
    },
    {
      path: '/messages',
      name: 'user-messages',
      component: { template: '<div>MessagesCenter</div>' },
      meta: { title: '消息中心' }
    },
    {
      path: '/notifications',
      name: 'user-notifications',
      component: { template: '<div>NotificationsRedirect</div>' },
      meta: { title: '通知中心', requiresAuth: true },
      beforeEnter: () => ({ path: '/messages', query: { tab: 'notifications' }, replace: true })
    },
    {
      path: '/announcements',
      name: 'user-announcements',
      redirect: { path: '/messages', query: { tab: 'announcements' } }
    },
    {
      path: '/announcements/:id',
      name: 'user-announcement-detail',
      component: { template: '<div>AnnouncementDetail</div>' },
      meta: { title: '公告详情' }
    },
    {
      path: '/orders/:id',
      name: 'order-detail',
      component: { template: '<div>OrderDetail</div>' },
      meta: { title: '订单详情', requiresAuth: true }
    },
    {
      path: '/complaints/:id',
      name: 'complaint-detail',
      component: { template: '<div>ComplaintDetail</div>' },
      meta: { title: '投诉详情', requiresAuth: true }
    },
    {
      path: '/become-companion',
      name: 'become-companion',
      component: { template: '<div>BecomeCompanion</div>' },
      meta: { title: '申请入驻' }
    },
    {
      path: '/companion',
      name: 'companion-workbench',
      component: { template: '<div>CompanionWorkbench</div>' },
      meta: { title: '陪玩工作台' }
    },
    {
      path: '/activities/:id',
      name: 'activity-detail',
      component: { template: '<div>ActivityDetail</div>' },
      meta: { title: '活动详情' }
    },
    {
      path: '/profile',
      name: 'user-profile',
      component: { template: '<div>Profile</div>' },
      meta: { title: '个人中心', requiresAuth: true }
    },
    {
      path: '/games',
      name: 'user-games',
      component: { template: '<div>Games</div>' },
      meta: { title: '游戏' }
    }
  ]

  const router = createRouter({
    history: createMemoryHistory(),
    routes
  })

  // Auth navigation guard matching frontend/src/router/index.ts
  router.beforeEach(async (to) => {
    const userStore = useUserStore()
    const requiresAuth = to.matched.some(r => r.meta.requiresAuth)

    if (requiresAuth && !userStore.token) {
      return { path: '/login', query: { redirect: to.fullPath } }
    }
    return true
  })

  return router
}

export function setupTestContext(options: TestContextOptions = {}) {
  // Clear local storage
  mockLocalStorage.clear()

  // Reset mock state
  const { notifications, announcements } = createDefaultSeedData()
  mockState.notifications = options.seedData?.notifications ?? notifications
  mockState.announcements = options.seedData?.announcements ?? announcements
  mockState.forceNotificationError = false
  mockState.forceMarkReadError = false
  mockState.forceMarkAllError = false
  mockState.forceAnnouncementError = false
  mockState.apiCallLogs = []

  // Initialize Pinia
  const pinia = createPinia()
  setActivePinia(pinia)

  const userStore = useUserStore()
  const notificationStore = useNotificationStore()

  if (options.isLoggedIn ?? false) {
    const token = options.token ?? 'test-jwt-token-12345'
    userStore.setToken(token)
    userStore.user = {
      id: 1,
      username: 'gamer_alice',
      nickname: '爱丽丝',
      phone: '13800000000',
      email: 'alice@example.com',
      avatarUrl: '',
      roles: ['USER'],
      status: 1
    }
  } else {
    userStore.logout()
  }

  // Setup router
  const router = createTestRouter()

  return {
    pinia,
    userStore,
    notificationStore,
    router,
    mockState
  }
}

// 6. Whitelist Routing Oracle
export function getTargetRoute(item: NotificationView): string | null {
  if (!item.relatedType || !item.relatedId) {
    return null
  }
  switch (item.relatedType) {
    case 'ORDER':
      return `/orders/${item.relatedId}`
    case 'COMPLAINT':
      return `/complaints/${item.relatedId}`
    case 'APPLICATION':
      return '/become-companion'
    case 'SERVICE':
      return '/companion'
    case 'ANNOUNCEMENT':
      return `/announcements/${item.relatedId}`
    case 'ACTIVITY':
      return `/activities/${item.relatedId}`
    case 'USER':
      return '/profile'
    default:
      return null
  }
}

// 7. Query Normalizer Oracle (Spec §4.2)
export function normalizeTabQuery(queryTab: string | undefined, isLoggedIn: boolean): {
  normalizedTab: 'notifications' | 'announcements'
  needsReplace: boolean
} {
  const defaultTab: 'notifications' | 'announcements' = isLoggedIn ? 'notifications' : 'announcements'
  if (queryTab === 'notifications' || queryTab === 'announcements') {
    return { normalizedTab: queryTab, needsReplace: false }
  }
  return { normalizedTab: defaultTab, needsReplace: true }
}
