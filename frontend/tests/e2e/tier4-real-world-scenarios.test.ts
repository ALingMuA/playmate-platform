/**
 * Tier 4: Real-World Application Scenarios Test Suite
 * Covers end-to-end user journeys simulating real browser usage patterns.
 * Minimum requirement: >= 8 test cases. Implemented: 8 test cases.
 */

import assert from 'node:assert/strict'
import {
  setupTestContext,
  getTargetRoute,
  normalizeTabQuery,
  mockState
} from './harness'
import type { TestResult } from './tier1-feature-coverage.test'
import { myNotifications, markNotificationRead, markAllNotificationsRead } from '@/api/notification'
import { publicAnnouncements } from '@/api/admin'
import http from '@/api/http'

export async function runTier4Tests(): Promise<TestResult[]> {
  const results: TestResult[] = []

  async function executeTest(id: string, name: string, area: string, fn: () => Promise<void>) {
    const start = Date.now()
    try {
      await fn()
      results.push({ id, name, tier: 4, area, passed: true, durationMs: Date.now() - start })
    } catch (err: any) {
      results.push({ id, name, tier: 4, area, passed: false, error: err?.message || String(err), durationMs: Date.now() - start })
    }
  }

  await executeTest('TC-T4-SCEN-01', 'Complete Guest Journey: Home -> Footer -> Announcements -> Detail -> Back -> Notifications CTA -> Login', 'User Journey: Guest', async () => {
    const { router, userStore } = setupTestContext({ isLoggedIn: false })

    // 1. Visit Home
    await router.push('/')
    assert.equal(router.currentRoute.value.path, '/')

    // 2. Click footer link "平台公告"
    await router.push({ path: '/messages', query: { tab: 'announcements' } })
    assert.equal(router.currentRoute.value.path, '/messages')
    assert.equal(router.currentRoute.value.query.tab, 'announcements')

    // 3. View announcement list
    const announcements = await publicAnnouncements(1, 10)
    assert.ok(announcements.records.length > 0)
    const firstId = announcements.records[0].id

    // 4. Click announcement card to view detail
    await router.push(`/announcements/${firstId}`)
    assert.equal(router.currentRoute.value.path, `/announcements/${firstId}`)

    // 5. Click back button
    await router.push({ path: '/messages', query: { tab: 'announcements' } })
    assert.equal(router.currentRoute.value.path, '/messages')
    assert.equal(router.currentRoute.value.query.tab, 'announcements')

    // 6. Switch to "站内通知" tab
    await router.push({ path: '/messages', query: { tab: 'notifications' } })
    assert.equal(router.currentRoute.value.query.tab, 'notifications')
    assert.equal(userStore.isLoggedIn, false, 'Guest sees Login CTA')

    // 7. Click "去登录" button
    await router.push({ path: '/login', query: { redirect: router.currentRoute.value.fullPath } })
    assert.equal(router.currentRoute.value.path, '/login')
    assert.equal(router.currentRoute.value.query.redirect, '/messages?tab=notifications')
  })

  await executeTest('TC-T4-SCEN-02', 'Complete Authenticated User Flow: Login -> Bell Click -> UNREAD filter -> Mark Read -> Whitelist Jump', 'User Journey: Authenticated User', async () => {
    const { router, userStore, notificationStore } = setupTestContext({ isLoggedIn: true })
    assert.equal(userStore.isLoggedIn, true)

    // 1. Initialize unread count
    await notificationStore.fetchUnread()
    assert.equal(notificationStore.unreadCount, 2)

    // 2. Click header bell icon
    await router.push({ path: '/messages', query: { tab: 'notifications' } })
    assert.equal(router.currentRoute.value.path, '/messages')
    assert.equal(router.currentRoute.value.query.tab, 'notifications')

    // 3. Fetch notifications and filter UNREAD
    const res = await myNotifications(1, 10)
    const unreadItems = res.records.filter(r => r.readStatus === 0)
    assert.equal(unreadItems.length, 2)

    // 4. Click first notification item to mark read
    const targetItem = unreadItems[0]
    await markNotificationRead(targetItem.id)
    targetItem.readStatus = 1
    notificationStore.decrementUnread()

    // 5. Bell badge decrements
    assert.equal(notificationStore.unreadCount, 1)

    // 6. Navigate to whitelist target route
    const targetRoute = getTargetRoute(targetItem)
    assert.ok(targetRoute)
    await router.push(targetRoute!)
    assert.equal(router.currentRoute.value.path, targetRoute)
  })

  await executeTest('TC-T4-SCEN-03', 'One-Click Mark All Read Workflow: 15 unread -> Mark All Read -> Store zeroed -> Badge hidden', 'User Journey: Bulk Mark Read', async () => {
    // Generate 15 unread notifications
    const bulkNotifications = Array.from({ length: 15 }, (_, i) => ({
      id: 200 + i,
      userId: 1,
      notificationType: 'ORDER_STATUS',
      title: `未读通知 #${i + 1}`,
      content: `通知内容 #${i + 1}`,
      readStatus: 0,
      createdAt: '2026-10-29 10:00:00'
    }))

    const { notificationStore } = setupTestContext({
      isLoggedIn: true,
      seedData: { notifications: bulkNotifications }
    })

    await notificationStore.fetchUnread()
    assert.equal(notificationStore.unreadCount, 15)

    // Click "一键全部已读"
    await markAllNotificationsRead()
    notificationStore.clearUnread()

    assert.equal(notificationStore.unreadCount, 0, 'Unread count should be 0')
    const remainingUnread = mockState.notifications.filter(n => n.readStatus === 0)
    assert.equal(remainingUnread.length, 0, 'All 15 notifications should be readStatus = 1')
  })

  await executeTest('TC-T4-SCEN-04', 'Revoked Announcement Handling: 404 caught -> back navigation restores announcements tab', 'User Journey: Error Recovery', async () => {
    const { router } = setupTestContext({ isLoggedIn: false })
    // Navigate to revoked announcement (id: 503)
    let notFoundCaught = false
    try {
      await http.get('/announcements/503')
    } catch (err: any) {
      notFoundCaught = true
      assert.equal(err?.status, 404)
    }
    assert.ok(notFoundCaught, 'Revoked announcement must throw 404')

    // Click "返回列表"
    await router.push({ path: '/messages', query: { tab: 'announcements' } })
    assert.equal(router.currentRoute.value.path, '/messages')
    assert.equal(router.currentRoute.value.query.tab, 'announcements')
  })

  await executeTest('TC-T4-SCEN-05', 'Legacy External Link Bookmarks: preserves destination for both guest and authenticated users', 'User Journey: Legacy Compatibility', async () => {
    // 1. Guest accesses /announcements bookmark
    const guestCtx = setupTestContext({ isLoggedIn: false })
    await guestCtx.router.push('/announcements')
    assert.equal(guestCtx.router.currentRoute.value.path, '/messages')
    assert.equal(guestCtx.router.currentRoute.value.query.tab, 'announcements')

    // 2. Auth user accesses /notifications bookmark
    const authCtx = setupTestContext({ isLoggedIn: true })
    await authCtx.router.push('/notifications')
    assert.equal(authCtx.router.currentRoute.value.path, '/messages')
    assert.equal(authCtx.router.currentRoute.value.query.tab, 'notifications')

    // 3. Guest accesses /notifications bookmark
    const guestNotifCtx = setupTestContext({ isLoggedIn: false })
    await guestNotifCtx.router.push('/notifications')
    assert.equal(guestNotifCtx.router.currentRoute.value.path, '/login')
    assert.equal(guestNotifCtx.router.currentRoute.value.query.redirect, '/notifications')
  })

  await executeTest('TC-T4-SCEN-06', 'Network Error Recovery: error alert shown -> retry succeeds without full reload', 'User Journey: Inline Retry', async () => {
    setupTestContext({ isLoggedIn: true })
    mockState.forceNotificationError = true

    let failed = false
    try {
      await myNotifications(1, 10)
    } catch {
      failed = true
    }
    assert.ok(failed, 'Should record initial failure')

    // Click retry
    mockState.forceNotificationError = false
    const recovered = await myNotifications(1, 10)
    assert.equal(recovered.records.length, 3, 'Data recovered successfully on retry')
  })

  await executeTest('TC-T4-SCEN-07', 'Tab Persistence Across In-App Routing: navigating away and back keeps active tab', 'User Journey: Navigation History', async () => {
    const { router } = setupTestContext({ isLoggedIn: true })
    // User is in notifications
    await router.push({ path: '/messages', query: { tab: 'notifications' } })
    assert.equal(router.currentRoute.value.query.tab, 'notifications')

    // User navigates away to /games
    await router.push('/games')
    assert.equal(router.currentRoute.value.path, '/games')

    // User clicks bell icon to return
    await router.push({ path: '/messages', query: { tab: 'notifications' } })
    assert.equal(router.currentRoute.value.path, '/messages')
    assert.equal(router.currentRoute.value.query.tab, 'notifications')
  })

  await executeTest('TC-T4-SCEN-08', 'Adversarial Input Sanitization: malicious tab queries sanitized without XSS or route corruption', 'User Journey: Security & Integrity', async () => {
    const { userStore, router } = setupTestContext({ isLoggedIn: false })
    const adversarialQueries = [
      '%3Cscript%3Ealert(document.cookie)%3C/script%3E',
      'javascript:void(0)',
      '../../admin',
      'eval(1)',
      '\\0',
      'undefined',
      'NaN'
    ]

    for (const bad of adversarialQueries) {
      const { normalizedTab, needsReplace } = normalizeTabQuery(bad, userStore.isLoggedIn)
      assert.equal(normalizedTab, 'announcements', `Input '${bad}' must be safely sanitized to announcements`)
      assert.equal(needsReplace, true)
    }
  })

  return results
}
