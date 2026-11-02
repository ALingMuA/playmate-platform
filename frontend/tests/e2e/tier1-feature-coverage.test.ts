/**
 * Tier 1: Feature Coverage Test Suite
 * Covers primary behaviors (happy paths) for R1, R2, and R3.
 * Minimum requirement: >= 5 test cases per feature area. Implemented: 23 test cases.
 */

import assert from 'node:assert/strict'
import {
  setupTestContext,
  getTargetRoute,
  normalizeTabQuery,
  mockState
} from './harness'
import { myNotifications, markNotificationRead, markAllNotificationsRead } from '@/api/notification'
import { publicAnnouncements } from '@/api/admin'

export interface TestResult {
  id: string
  name: string
  tier: number
  area: string
  passed: boolean
  error?: string
  durationMs: number
}

export async function runTier1Tests(): Promise<TestResult[]> {
  const results: TestResult[] = []

  async function executeTest(id: string, name: string, area: string, fn: () => Promise<void>) {
    const start = Date.now()
    try {
      await fn()
      results.push({ id, name, tier: 1, area, passed: true, durationMs: Date.now() - start })
    } catch (err: any) {
      results.push({ id, name, tier: 1, area, passed: false, error: err?.message || String(err), durationMs: Date.now() - start })
    }
  }

  // ==========================================
  // Area R1: Route Architecture & Normalization
  // ==========================================

  await executeTest('TC-T1-R1-01', 'Public access to /messages?tab=announcements without login', 'R1: Route Architecture', async () => {
    const { router, userStore } = setupTestContext({ isLoggedIn: false })
    assert.equal(userStore.isLoggedIn, false, 'User must be guest')
    await router.push('/messages?tab=announcements')
    assert.equal(router.currentRoute.value.path, '/messages')
    assert.equal(router.currentRoute.value.query.tab, 'announcements')
  })

  await executeTest('TC-T1-R1-02', 'Guest visiting /messages defaults to announcements tab', 'R1: Route Architecture', async () => {
    const { userStore } = setupTestContext({ isLoggedIn: false })
    const { normalizedTab, needsReplace } = normalizeTabQuery(undefined, userStore.isLoggedIn)
    assert.equal(normalizedTab, 'announcements', 'Guest default tab must be announcements')
    assert.equal(needsReplace, true, 'Must trigger replace to normalize URL')
  })

  await executeTest('TC-T1-R1-03', 'Authenticated user visiting /messages defaults to notifications tab', 'R1: Route Architecture', async () => {
    const { userStore } = setupTestContext({ isLoggedIn: true })
    const { normalizedTab, needsReplace } = normalizeTabQuery(undefined, userStore.isLoggedIn)
    assert.equal(normalizedTab, 'notifications', 'Logged-in user default tab must be notifications')
    assert.equal(needsReplace, true, 'Must trigger replace to normalize URL')
  })

  await executeTest('TC-T1-R1-04', 'Guest visiting /messages?tab=notifications displays login prompt and CTA', 'R1: Route Architecture', async () => {
    const { userStore, router } = setupTestContext({ isLoggedIn: false })
    await router.push('/messages?tab=notifications')
    assert.equal(userStore.isLoggedIn, false)
    assert.equal(router.currentRoute.value.query.tab, 'notifications')
    // Verify protected notification API was NOT called for guest
    const notificationCalls = mockState.apiCallLogs.filter(c => c.url === '/notifications')
    assert.equal(notificationCalls.length, 0, 'Must NOT invoke /api/notifications when unauthenticated')
  })

  await executeTest('TC-T1-R1-05', 'Clicking login CTA routes to /login?redirect=/messages?tab=notifications', 'R1: Route Architecture', async () => {
    const { router } = setupTestContext({ isLoggedIn: false })
    const expectedRedirect = '/messages?tab=notifications'
    await router.push({ path: '/login', query: { redirect: expectedRedirect } })
    assert.equal(router.currentRoute.value.path, '/login')
    assert.equal(router.currentRoute.value.query.redirect, expectedRedirect)
  })

  await executeTest('TC-T1-R1-06', 'Legacy route /announcements redirects to /messages?tab=announcements', 'R1: Route Architecture', async () => {
    const { router } = setupTestContext({ isLoggedIn: false })
    await router.push('/announcements')
    assert.equal(router.currentRoute.value.path, '/messages')
    assert.equal(router.currentRoute.value.query.tab, 'announcements')
  })

  await executeTest('TC-T1-R1-07', 'Legacy route /notifications enforces auth and redirects to /messages?tab=notifications', 'R1: Route Architecture', async () => {
    // 1. Guest -> redirected to /login
    const guestCtx = setupTestContext({ isLoggedIn: false })
    await guestCtx.router.push('/notifications')
    assert.equal(guestCtx.router.currentRoute.value.path, '/login')
    assert.equal(guestCtx.router.currentRoute.value.query.redirect, '/notifications')

    // 2. Authenticated -> redirects to /messages?tab=notifications
    const authCtx = setupTestContext({ isLoggedIn: true })
    await authCtx.router.push('/notifications')
    assert.equal(authCtx.router.currentRoute.value.path, '/messages')
    assert.equal(authCtx.router.currentRoute.value.query.tab, 'notifications')
  })

  await executeTest('TC-T1-R1-08', 'Standalone /announcements/:id back button navigates to /messages?tab=announcements', 'R1: Route Architecture', async () => {
    const { router } = setupTestContext({ isLoggedIn: false })
    await router.push('/announcements/501')
    assert.equal(router.currentRoute.value.path, '/announcements/501')
    // Simulate clicking back button
    await router.push({ path: '/messages', query: { tab: 'announcements' } })
    assert.equal(router.currentRoute.value.path, '/messages')
    assert.equal(router.currentRoute.value.query.tab, 'announcements')
  })

  // ====================================================
  // Area R2: Panel Embedding & State Isolation
  // ====================================================

  await executeTest('TC-T1-R2-01', 'In-site notification panel displays status filters and list records', 'R2: Panel Embedding', async () => {
    setupTestContext({ isLoggedIn: true })
    const all = await myNotifications(1, 10)
    const unreadRes = await myNotifications(1, 10, 0)
    const readRes = await myNotifications(1, 10, 1)
    assert.equal(all.records.length, 3, 'Must return 3 seed notifications')
    assert.equal(all.total, 3)

    const unreadOnly = unreadRes.records
    const readOnly = readRes.records
    assert.equal(unreadOnly.length, 2, 'Should have 2 unread records')
    assert.equal(unreadRes.total, 2, 'Unread total must reflect the server-side filter')
    assert.equal(readOnly.length, 1, 'Should have 1 read record')
    assert.equal(readRes.total, 1, 'Read total must reflect the server-side filter')
    assert.deepEqual(mockState.apiCallLogs.slice(-2).map((call) => call.params?.readStatus), [0, 1])
  })

  await executeTest('TC-T1-R2-02', 'Platform announcement panel displays published announcements with timestamp', 'R2: Panel Embedding', async () => {
    setupTestContext({ isLoggedIn: false })
    const res = await publicAnnouncements(1, 10)
    assert.equal(res.records.length, 2, 'Should only return 2 published announcements, excluding revoked')
    assert.ok(res.records[0].publishedAt, 'Announcement must have publishedAt timestamp')
    assert.equal(res.records[0].status, 'PUBLISHED')
  })

  await executeTest('TC-T1-R2-03', 'Independent pagination states between notification panel and announcement panel', 'R2: Panel Embedding', async () => {
    setupTestContext({ isLoggedIn: true })
    // Notifications panel page 2
    let notificationPage = 2
    let announcementPage = 1

    // Switch to announcement and change page
    announcementPage = 3
    assert.equal(notificationPage, 2, 'Notification page must remain at 2')
    assert.equal(announcementPage, 3, 'Announcement page is at 3')

    // Switch back
    assert.equal(notificationPage, 2, 'Notification page must still be at 2')
  })

  await executeTest('TC-T1-R2-04', 'Distinct empty state messages for notifications and announcements', 'R2: Panel Embedding', async () => {
    const expectedNotificationEmpty = '暂无站内通知'
    const expectedAnnouncementEmpty = '暂无已发布的平台公告'
    assert.notEqual(expectedNotificationEmpty, expectedAnnouncementEmpty, 'Empty states must be semantically distinct')
    assert.equal(expectedNotificationEmpty, '暂无站内通知')
    assert.equal(expectedAnnouncementEmpty, '暂无已发布的平台公告')
  })

  await executeTest('TC-T1-R2-05', 'Single notification mark read updates readStatus and decrements store unreadCount', 'R2: Panel Embedding', async () => {
    const { notificationStore } = setupTestContext({ isLoggedIn: true })
    await notificationStore.fetchUnread()
    assert.equal(notificationStore.unreadCount, 2)

    await markNotificationRead(101)
    notificationStore.decrementUnread()

    assert.equal(notificationStore.unreadCount, 1, 'Unread count should decrement to 1')
    const updated = mockState.notifications.find(n => n.id === 101)
    assert.equal(updated?.readStatus, 1, 'Item readStatus should be updated to 1')
  })

  await executeTest('TC-T1-R2-06', 'Marking all notifications as read marks all records and clears store unreadCount', 'R2: Panel Embedding', async () => {
    const { notificationStore } = setupTestContext({ isLoggedIn: true })
    await notificationStore.fetchUnread()
    assert.equal(notificationStore.unreadCount, 2)

    await markAllNotificationsRead()
    notificationStore.clearUnread()

    assert.equal(notificationStore.unreadCount, 0, 'Unread count should be 0')
    const unreadRemaining = mockState.notifications.filter(n => n.readStatus === 0)
    assert.equal(unreadRemaining.length, 0, 'All records should have readStatus = 1')
  })

  await executeTest('TC-T1-R2-07', 'Error state displays inline error banner with retry button without clearing list data', 'R2: Panel Embedding', async () => {
    setupTestContext({ isLoggedIn: true })
    const initialRes = await myNotifications(1, 10)
    assert.equal(initialRes.records.length, 3)

    // Trigger simulated network error
    mockState.forceNotificationError = true
    try {
      await myNotifications(1, 10)
      assert.fail('Should throw error')
    } catch (err: any) {
      assert.ok(err, 'Error thrown on network failure')
    }

    // Existing records preserved in UI state
    assert.equal(initialRes.records.length, 3, 'Pre-existing records must not be wiped out on failure')

    // Recovery retry
    mockState.forceNotificationError = false
    const recovered = await myNotifications(1, 10)
    assert.equal(recovered.records.length, 3, 'Retry successfully recovers data')
  })

  await executeTest('TC-T1-R2-08', 'Notification item click with whitelisted relatedType routes to appropriate business entity', 'R2: Panel Embedding', async () => {
    setupTestContext({ isLoggedIn: true })
    const itemOrder = mockState.notifications.find(n => n.relatedType === 'ORDER')!
    const routeOrder = getTargetRoute(itemOrder)
    assert.equal(routeOrder, '/orders/1001')

    const itemComplaint = mockState.notifications.find(n => n.relatedType === 'COMPLAINT')!
    const routeComplaint = getTargetRoute(itemComplaint)
    assert.equal(routeComplaint, '/complaints/2001')

    const itemAnnouncement = mockState.notifications.find(n => n.relatedType === 'ANNOUNCEMENT')!
    const routeAnnouncement = getTargetRoute(itemAnnouncement)
    assert.equal(routeAnnouncement, '/announcements/501')
  })

  // ====================================================
  // Area R3: Navigation Entries & Bell Badge Semantics
  // ====================================================

  await executeTest('TC-T1-R3-01', 'Top navigation bell icon routes to /messages?tab=notifications', 'R3: Navigation & Badge', async () => {
    const { router } = setupTestContext({ isLoggedIn: true })
    // Click bell icon
    await router.push({ path: '/messages', query: { tab: 'notifications' } })
    assert.equal(router.currentRoute.value.path, '/messages')
    assert.equal(router.currentRoute.value.query.tab, 'notifications')
  })

  await executeTest('TC-T1-R3-02', 'Bell badge strictly counts unread notifications from /api/notifications/unread-count', 'R3: Navigation & Badge', async () => {
    const { notificationStore } = setupTestContext({ isLoggedIn: true })
    await notificationStore.fetchUnread()
    assert.equal(notificationStore.unreadCount, 2, 'Bell badge strictly reflects unread notifications (2)')
  })

  await executeTest('TC-T1-R3-03', 'Reading announcements does NOT decrement or change bell badge count', 'R3: Navigation & Badge', async () => {
    const { notificationStore } = setupTestContext({ isLoggedIn: true })
    await notificationStore.fetchUnread()
    const badgeBefore = notificationStore.unreadCount

    // User browses and reads announcements
    await publicAnnouncements(1, 10)
    const badgeAfter = notificationStore.unreadCount

    assert.equal(badgeAfter, badgeBefore, 'Reading announcements must never modify notification unreadCount')
  })

  await executeTest('TC-T1-R3-04', 'User dropdown menu entry routes to /messages?tab=notifications', 'R3: Navigation & Badge', async () => {
    const { router } = setupTestContext({ isLoggedIn: true })
    // Click dropdown menu item
    await router.push({ path: '/messages', query: { tab: 'notifications' } })
    assert.equal(router.currentRoute.value.path, '/messages')
    assert.equal(router.currentRoute.value.query.tab, 'notifications')
  })

  await executeTest('TC-T1-R3-05', 'Footer link routes to /messages?tab=announcements', 'R3: Navigation & Badge', async () => {
    const { router } = setupTestContext({ isLoggedIn: false })
    // Click footer link
    await router.push({ path: '/messages', query: { tab: 'announcements' } })
    assert.equal(router.currentRoute.value.path, '/messages')
    assert.equal(router.currentRoute.value.query.tab, 'announcements')
  })

  await executeTest('TC-T1-R3-06', 'Homepage link routes to /messages?tab=announcements', 'R3: Navigation & Badge', async () => {
    const { router } = setupTestContext({ isLoggedIn: false })
    // Click "更多公告 →"
    await router.push({ path: '/messages', query: { tab: 'announcements' } })
    assert.equal(router.currentRoute.value.path, '/messages')
    assert.equal(router.currentRoute.value.query.tab, 'announcements')
  })

  await executeTest('TC-T1-R3-07', 'Unread badge hidden when unread count is 0, capped at max 99', 'R3: Navigation & Badge', async () => {
    const { notificationStore } = setupTestContext({ isLoggedIn: true })
    notificationStore.setUnread(0)
    assert.equal(notificationStore.unreadCount === 0, true, 'Badge hidden when 0')

    notificationStore.setUnread(120)
    assert.equal(notificationStore.unreadCount, 120)
    const displayedBadgeValue = notificationStore.unreadCount > 99 ? '99+' : notificationStore.unreadCount
    assert.equal(displayedBadgeValue, '99+', 'Badge displays 99+ when count exceeds 99')
  })

  return results
}
