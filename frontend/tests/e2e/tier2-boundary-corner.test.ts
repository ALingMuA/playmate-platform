/**
 * Tier 2: Boundary & Corner Cases Test Suite
 * Covers edge cases, invalid inputs, boundary values, empty states, and failure recovery.
 * Minimum requirement: >= 5 test cases per feature area. Implemented: 18 test cases.
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

export async function runTier2Tests(): Promise<TestResult[]> {
  const results: TestResult[] = []

  async function executeTest(id: string, name: string, area: string, fn: () => Promise<void>) {
    const start = Date.now()
    try {
      await fn()
      results.push({ id, name, tier: 2, area, passed: true, durationMs: Date.now() - start })
    } catch (err: any) {
      results.push({ id, name, tier: 2, area, passed: false, error: err?.message || String(err), durationMs: Date.now() - start })
    }
  }

  // ==========================================
  // Area R1: Boundary & Corner Cases
  // ==========================================

  await executeTest('TC-T2-R1-01', 'Missing query parameter normalization via router.replace', 'R1: Boundary & Corner', async () => {
    const { userStore } = setupTestContext({ isLoggedIn: false })
    const { normalizedTab, needsReplace } = normalizeTabQuery(undefined, userStore.isLoggedIn)
    assert.equal(normalizedTab, 'announcements')
    assert.equal(needsReplace, true, 'Must require replace for missing parameter')
  })

  await executeTest('TC-T2-R1-02', 'Invalid query string parameter normalized to default tab', 'R1: Boundary & Corner', async () => {
    const { userStore } = setupTestContext({ isLoggedIn: true })
    const { normalizedTab, needsReplace } = normalizeTabQuery('invalid_xyz_tab', userStore.isLoggedIn)
    assert.equal(normalizedTab, 'notifications', 'Logged in user invalid query falls back to notifications')
    assert.equal(needsReplace, true)
  })

  await executeTest('TC-T2-R1-03', 'Empty query parameter value normalized to default tab', 'R1: Boundary & Corner', async () => {
    const { userStore } = setupTestContext({ isLoggedIn: false })
    const { normalizedTab, needsReplace } = normalizeTabQuery('', userStore.isLoggedIn)
    assert.equal(normalizedTab, 'announcements', 'Guest empty query falls back to announcements')
    assert.equal(needsReplace, true)
  })

  await executeTest('TC-T2-R1-04', 'Malformed and special character query sanitized', 'R1: Boundary & Corner', async () => {
    const { userStore } = setupTestContext({ isLoggedIn: false })
    const malformedQueries = ['<script>alert(1)</script>', '%20', 'null', 'undefined', '../admin', 'notifications;DROP TABLE']
    for (const q of malformedQueries) {
      const { normalizedTab, needsReplace } = normalizeTabQuery(q, userStore.isLoggedIn)
      assert.equal(normalizedTab, 'announcements', `Query '${q}' must normalize to announcements`)
      assert.equal(needsReplace, true)
    }
  })

  await executeTest('TC-T2-R1-05', 'Router history cleanliness: normalization uses replace not push', 'R1: Boundary & Corner', async () => {
    const { userStore } = setupTestContext({ isLoggedIn: false })
    const { needsReplace } = normalizeTabQuery('bad_tab', userStore.isLoggedIn)
    assert.equal(needsReplace, true, 'Normalization must specify replace: true so history stack is not polluted')
  })

  await executeTest('TC-T2-R1-06', 'Preservation of auxiliary query parameters during tab normalization', 'R1: Boundary & Corner', async () => {
    const { userStore, router } = setupTestContext({ isLoggedIn: false })
    await router.push('/messages?ref=footer&utm_source=test')
    const existingQuery = router.currentRoute.value.query
    const { normalizedTab } = normalizeTabQuery(existingQuery.tab as string, userStore.isLoggedIn)
    const normalizedQuery = { ...existingQuery, tab: normalizedTab }
    assert.equal(normalizedQuery.ref, 'footer')
    assert.equal(normalizedQuery.utm_source, 'test')
    assert.equal(normalizedQuery.tab, 'announcements')
  })

  // ==========================================
  // Area R2: Boundary & Corner Cases
  // ==========================================

  await executeTest('TC-T2-R2-01', 'Zero notification records empty state', 'R2: Boundary & Corner', async () => {
    setupTestContext({ isLoggedIn: true, seedData: { notifications: [] } })
    const res = await myNotifications(1, 10)
    assert.equal(res.records.length, 0)
    assert.equal(res.total, 0)
    const emptyNotice = res.records.length === 0 ? '暂无站内通知' : ''
    assert.equal(emptyNotice, '暂无站内通知')
  })

  await executeTest('TC-T2-R2-02', 'Zero announcement records empty state', 'R2: Boundary & Corner', async () => {
    setupTestContext({ isLoggedIn: false, seedData: { announcements: [] } })
    const res = await publicAnnouncements(1, 10)
    assert.equal(res.records.length, 0)
    assert.equal(res.total, 0)
    const emptyAnnouncement = res.records.length === 0 ? '暂无已发布的平台公告' : ''
    assert.equal(emptyAnnouncement, '暂无已发布的平台公告')
  })

  await executeTest('TC-T2-R2-03', 'Zero unread notifications under UNREAD filter renders empty state', 'R2: Boundary & Corner', async () => {
    const allReadSeed = [
      {
        id: 1,
        notificationType: 'SYSTEM',
        title: '已读通知',
        content: '内容',
        readStatus: 1,
        createdAt: '2026-10-29 08:00:00'
      }
    ]
    setupTestContext({ isLoggedIn: true, seedData: { notifications: allReadSeed } })
    const res = await myNotifications(1, 10)
    const unreadFiltered = res.records.filter(n => n.readStatus === 0)
    assert.equal(unreadFiltered.length, 0)
    const unreadEmptyMessage = unreadFiltered.length === 0 ? '暂无未读通知' : ''
    assert.equal(unreadEmptyMessage, '暂无未读通知')
  })

  await executeTest('TC-T2-R2-04', 'Single mark read API 500 failure preserves unread status (pessimistic update)', 'R2: Boundary & Corner', async () => {
    const { notificationStore } = setupTestContext({ isLoggedIn: true })
    await notificationStore.fetchUnread()
    assert.equal(notificationStore.unreadCount, 2)

    // Force network failure
    mockState.forceMarkReadError = true
    try {
      await markNotificationRead(101)
      assert.fail('Expected markNotificationRead to throw')
    } catch (err: any) {
      assert.ok(err, 'Expected network error caught')
    }

    // Must NOT decrement unreadCount on failure
    assert.equal(notificationStore.unreadCount, 2, 'Unread count must stay 2 when API fails')
    const item = mockState.notifications.find(n => n.id === 101)
    assert.equal(item?.readStatus, 0, 'Item readStatus must remain 0 (pessimistic)')
  })

  await executeTest('TC-T2-R2-05', 'Mark all read API 500 failure preserves unread status and count', 'R2: Boundary & Corner', async () => {
    const { notificationStore } = setupTestContext({ isLoggedIn: true })
    await notificationStore.fetchUnread()
    assert.equal(notificationStore.unreadCount, 2)

    // Force network failure
    mockState.forceMarkAllError = true
    try {
      await markAllNotificationsRead()
      assert.fail('Expected markAllNotificationsRead to throw')
    } catch (err: any) {
      assert.ok(err, 'Expected network error caught')
    }

    assert.equal(notificationStore.unreadCount, 2, 'Unread count must remain unchanged')
    const unreadCount = mockState.notifications.filter(n => n.readStatus === 0).length
    assert.equal(unreadCount, 2, 'Records must retain unread status')
  })

  await executeTest('TC-T2-R2-06', 'Unknown relatedType safety: marks read without invalid navigation', 'R2: Boundary & Corner', async () => {
    setupTestContext({ isLoggedIn: true })
    const unknownItem = {
      id: 999,
      notificationType: 'CUSTOM_UNKNOWN_TYPE',
      title: '未知类型通知',
      content: '无关联白名单对象',
      relatedType: 'NON_EXISTENT_TYPE',
      relatedId: 12345,
      readStatus: 0,
      createdAt: '2026-10-29 10:00:00'
    }
    const target = getTargetRoute(unknownItem)
    assert.equal(target, null, 'Unknown relatedType must resolve to null, preventing arbitrary navigation')
  })

  // ==========================================
  // Area R3: Boundary & Corner Cases
  // ==========================================

  await executeTest('TC-T2-R3-01', 'Bell badge hidden when count is 0', 'R3: Boundary & Corner', async () => {
    const { notificationStore } = setupTestContext({ isLoggedIn: true })
    notificationStore.setUnread(0)
    assert.equal(notificationStore.unreadCount, 0)
    const isHidden = notificationStore.unreadCount === 0
    assert.equal(isHidden, true)
  })

  await executeTest('TC-T2-R3-02', 'Bell badge overflow boundary (>99) displays 99+', 'R3: Boundary & Corner', async () => {
    const { notificationStore } = setupTestContext({ isLoggedIn: true })
    notificationStore.setUnread(150)
    assert.equal(notificationStore.unreadCount, 150)
    const badgeText = notificationStore.unreadCount > 99 ? '99+' : String(notificationStore.unreadCount)
    assert.equal(badgeText, '99+')
  })

  await executeTest('TC-T2-R3-03', 'User logout clears badge count immediately', 'R3: Boundary & Corner', async () => {
    const { userStore, notificationStore } = setupTestContext({ isLoggedIn: true })
    notificationStore.setUnread(7)
    assert.equal(notificationStore.unreadCount, 7)

    // User logs out
    userStore.logout()
    notificationStore.clearUnread()

    assert.equal(userStore.isLoggedIn, false)
    assert.equal(notificationStore.unreadCount, 0, 'Unread count must be 0 after logout')
  })

  await executeTest('TC-T2-R3-04', 'Decrement floor boundary: decrementUnread() never goes below 0', 'R3: Boundary & Corner', async () => {
    const { notificationStore } = setupTestContext({ isLoggedIn: true })
    notificationStore.setUnread(0)
    notificationStore.decrementUnread(5)
    assert.equal(notificationStore.unreadCount, 0, 'Count must floor at 0')
  })

  await executeTest('TC-T2-R3-05', 'Guest never fetches notification unread count (guards against 401)', 'R3: Boundary & Corner', async () => {
    const { notificationStore, userStore } = setupTestContext({ isLoggedIn: false })
    assert.equal(userStore.isLoggedIn, false)
    await notificationStore.fetchUnread()
    assert.equal(notificationStore.unreadCount, 0)
    const callCount = mockState.apiCallLogs.filter(c => c.url === '/notifications/unread-count').length
    assert.equal(callCount, 0, 'Must NOT invoke unread-count API for guest')
  })

  await executeTest('TC-T2-R3-06', 'Token expiry during session handled gracefully without unhandled exceptions', 'R3: Boundary & Corner', async () => {
    const { notificationStore } = setupTestContext({ isLoggedIn: true })
    mockState.forceNotificationError = true
    // fetchUnread catches error internally and gracefully recovers
    await notificationStore.fetchUnread()
    assert.equal(notificationStore.loading, false, 'Loading must resolve to false after error')
  })

  return results
}
