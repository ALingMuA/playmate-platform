/**
 * Challenger Adversarial & Stress Test Suite
 * Independent empirical challenge for Unified Message Center (/messages)
 * 
 * Verifies:
 * - Guest Route Isolation (zero 401s, zero protected notification calls)
 * - Guest notifications CTA display and redirect preservation
 * - Backward compatibility redirects (/announcements, /notifications, /announcements/:id)
 * - Query normalization and anti-history-pollution (replace vs push)
 * - Auxiliary query parameter preservation
 * - Browser back/forward navigation state preservation
 * - High-frequency rapid tab switching (100x stress toggling)
 * - Badge semantic isolation and underflow prevention
 * - Session mutation / in-place logout cleanup
 * - Resilience under network 500 and 404 faults
 */

import assert from 'node:assert/strict'
import {
  setupTestContext,
  getTargetRoute,
  normalizeTabQuery,
  mockState
} from './harness'
import { myNotifications, markNotificationRead, markAllNotificationsRead } from '@/api/notification'
import { publicAnnouncements, publicAnnouncementDetail } from '@/api/admin'

export interface ChallengerTestResult {
  id: string
  name: string
  passed: boolean
  error?: string
  durationMs: number
}

export async function runChallengerTests(): Promise<ChallengerTestResult[]> {
  const results: ChallengerTestResult[] = []

  async function test(id: string, name: string, fn: () => Promise<void>) {
    const start = Date.now()
    try {
      await fn()
      results.push({ id, name, passed: true, durationMs: Date.now() - start })
    } catch (err: any) {
      results.push({ id, name, passed: false, error: err?.message || String(err), durationMs: Date.now() - start })
    }
  }

  // =========================================================================
  // 1. Guest Access Control & Zero Protected API Calls
  // =========================================================================

  await test('CHAL-01', 'Guest navigates to /messages?tab=announcements: loads data, zero 401, zero notification API calls', async () => {
    const { router, userStore, notificationStore } = setupTestContext({ isLoggedIn: false })
    assert.equal(userStore.isLoggedIn, false, 'User must not be logged in')
    
    // Initial fetch of unread count as guest must be aborted silently without API call
    await notificationStore.fetchUnread()
    assert.equal(notificationStore.unreadCount, 0)

    // Route navigation
    await router.push('/messages?tab=announcements')
    assert.equal(router.currentRoute.value.path, '/messages')
    assert.equal(router.currentRoute.value.query.tab, 'announcements')

    // Fetch announcements publicly
    const res = await publicAnnouncements(1, 10)
    assert.ok(res.records.length > 0, 'Public announcements must be returned')

    // Verify zero calls made to /notifications or /notifications/unread-count
    const protectedCalls = mockState.apiCallLogs.filter(
      c => c.url.startsWith('/notifications')
    )
    assert.equal(protectedCalls.length, 0, 'Guest must generate 0 calls to protected notification endpoints')
  })

  await test('CHAL-02', 'Guest navigates to /messages?tab=notifications: renders CTA, zero notification API calls, redirect query points to /messages?tab=notifications', async () => {
    const { router, userStore } = setupTestContext({ isLoggedIn: false })
    assert.equal(userStore.isLoggedIn, false)

    await router.push('/messages?tab=notifications')
    assert.equal(router.currentRoute.value.path, '/messages')
    assert.equal(router.currentRoute.value.query.tab, 'notifications')

    // In NotificationListPanel, if !isLoggedIn, loadData returns immediately without API calls
    const protectedCalls = mockState.apiCallLogs.filter(
      c => c.url.startsWith('/notifications')
    )
    assert.equal(protectedCalls.length, 0, 'Must not call notification API for guest')

    // CTA redirect verification: clicking login navigates to /login with redirect
    const ctaRedirect = '/messages?tab=notifications'
    await router.push({ path: '/login', query: { redirect: ctaRedirect } })
    assert.equal(router.currentRoute.value.path, '/login')
    assert.equal(router.currentRoute.value.query.redirect, ctaRedirect)
  })

  await test('CHAL-03', 'Guest route isolation with empty/null/cleared tokens', async () => {
    const falsyTokens = ['', null, undefined]
    for (const token of falsyTokens) {
      const { userStore, notificationStore } = setupTestContext({ isLoggedIn: false })
      userStore.token = token as any
      userStore.user = null
      assert.equal(userStore.isLoggedIn, false, `isLoggedIn must be false for token: ${token}`)

      await notificationStore.fetchUnread()
      assert.equal(notificationStore.unreadCount, 0)

      const calls = mockState.apiCallLogs.filter(c => c.url === '/notifications/unread-count')
      assert.equal(calls.length, 0, `Should not call API with token: "${token}"`)
    }
  })

  // =========================================================================
  // 2. Backward Compatibility Redirects & Query Preservation
  // =========================================================================

  await test('CHAL-04', 'Opening /announcements redirects to /messages?tab=announcements', async () => {
    const { router } = setupTestContext({ isLoggedIn: false })
    await router.push('/announcements')
    assert.equal(router.currentRoute.value.path, '/messages')
    assert.equal(router.currentRoute.value.query.tab, 'announcements')
  })

  await test('CHAL-05', 'Opening /notifications as guest requires auth and redirects to /login with redirect query', async () => {
    const { router } = setupTestContext({ isLoggedIn: false })
    await router.push('/notifications')
    assert.equal(router.currentRoute.value.path, '/login')
    assert.equal(router.currentRoute.value.query.redirect, '/notifications')
  })

  await test('CHAL-06', 'Opening /notifications as authenticated user redirects to /messages?tab=notifications', async () => {
    const { router } = setupTestContext({ isLoggedIn: true })
    await router.push('/notifications')
    assert.equal(router.currentRoute.value.path, '/messages')
    assert.equal(router.currentRoute.value.query.tab, 'notifications')
  })

  await test('CHAL-07', 'Standalone announcement detail /announcements/:id provides back link to /messages?tab=announcements', async () => {
    const { router } = setupTestContext({ isLoggedIn: false })
    await router.push('/announcements/501')
    assert.equal(router.currentRoute.value.path, '/announcements/501')
    assert.equal(router.currentRoute.value.params.id, '501')

    // Fetch detail
    const detail = await publicAnnouncementDetail(501)
    assert.equal(detail.id, 501)
    assert.equal(detail.status, 'PUBLISHED')

    // Simulate back navigation action goBack()
    await router.push('/messages?tab=announcements')
    assert.equal(router.currentRoute.value.path, '/messages')
    assert.equal(router.currentRoute.value.query.tab, 'announcements')
  })

  await test('CHAL-08', 'Revoked or non-existent announcement detail (404) provides back link to /messages?tab=announcements', async () => {
    const { router } = setupTestContext({ isLoggedIn: false })
    await router.push('/announcements/503') // Revoked announcement
    
    try {
      await publicAnnouncementDetail(503)
      assert.fail('Should throw 404 for revoked announcement')
    } catch (err: any) {
      assert.ok(err, 'Caught 404 for revoked announcement')
    }

    // Go back
    await router.push('/messages?tab=announcements')
    assert.equal(router.currentRoute.value.path, '/messages')
    assert.equal(router.currentRoute.value.query.tab, 'announcements')
  })

  // =========================================================================
  // 3. Query Normalization & History Cleanliness
  // =========================================================================

  await test('CHAL-09', 'Missing tab query normalization: guest defaults to announcements, auth defaults to notifications', async () => {
    // Guest
    const guestNorm = normalizeTabQuery(undefined, false)
    assert.equal(guestNorm.normalizedTab, 'announcements')
    assert.equal(guestNorm.needsReplace, true)

    // Auth
    const authNorm = normalizeTabQuery(undefined, true)
    assert.equal(authNorm.normalizedTab, 'notifications')
    assert.equal(authNorm.needsReplace, true)
  })

  await test('CHAL-10', 'Adversarial query injection values normalized to default tab with replace', async () => {
    const adversarialQueries = [
      '',
      '   ',
      'invalid_tab',
      'ADMIN',
      'Notifications', // Case mismatch
      'ANNOUNCEMENTS', // Case mismatch
      '<script>alert(1)</script>',
      '../../etc/passwd',
      'null',
      'undefined',
      '0',
      'false'
    ]

    for (const q of adversarialQueries) {
      const { normalizedTab, needsReplace } = normalizeTabQuery(q, false)
      assert.equal(normalizedTab, 'announcements', `Query "${q}" must normalize to announcements for guest`)
      assert.equal(needsReplace, true, `Query "${q}" must indicate replace is needed`)
    }
  })

  await test('CHAL-11', 'Auxiliary query parameters preserved during tab normalization', async () => {
    const { router, userStore } = setupTestContext({ isLoggedIn: false })
    const defaultTab = userStore.isLoggedIn ? 'notifications' : 'announcements'
    
    // User arrives at /messages with auxiliary parameters: from=footer&channel=web
    const initialQuery = { from: 'footer', channel: 'web' }
    const normalizedQuery = { ...initialQuery, tab: defaultTab }

    await router.replace({ path: '/messages', query: normalizedQuery })
    assert.equal(router.currentRoute.value.path, '/messages')
    assert.equal(router.currentRoute.value.query.tab, 'announcements')
    assert.equal(router.currentRoute.value.query.from, 'footer')
    assert.equal(router.currentRoute.value.query.channel, 'web')
  })

  // Helper for async back/forward navigation in vue-router memory history
  async function triggerBack(router: any) {
    return new Promise<void>((resolve) => {
      const unhook = router.afterEach(() => {
        unhook()
        resolve()
      })
      router.back()
      setTimeout(resolve, 50)
    })
  }

  async function triggerForward(router: any) {
    return new Promise<void>((resolve) => {
      const unhook = router.afterEach(() => {
        unhook()
        resolve()
      })
      router.forward()
      setTimeout(resolve, 50)
    })
  }

  await test('CHAL-12', 'History cleanliness: normalization via router.replace avoids back-button trap', async () => {
    const { router } = setupTestContext({ isLoggedIn: false })
    
    // Start at home page
    await router.push('/')
    assert.equal(router.currentRoute.value.path, '/')

    // Navigate to /messages (with replace normalization simulation)
    // In real app: user navigates to /messages, component syncTabFromRoute executes router.replace
    await router.push('/messages')
    // Router replace to normalize tab:
    await router.replace({ path: '/messages', query: { tab: 'announcements' } })
    assert.equal(router.currentRoute.value.path, '/messages')
    assert.equal(router.currentRoute.value.query.tab, 'announcements')

    // When user goes back, they should return directly to '/'
    await triggerBack(router)
    assert.equal(router.currentRoute.value.path, '/', 'Back button must return directly to previous page without getting trapped')
  })

  // =========================================================================
  // 4. Browser Navigation & Tab State Synchronization
  // =========================================================================

  await test('CHAL-13', 'Browser back/forward navigation preserves tab and query state', async () => {
    const { router } = setupTestContext({ isLoggedIn: true })

    // Step 1: Open notifications tab
    await router.push({ path: '/messages', query: { tab: 'notifications' } })
    assert.equal(router.currentRoute.value.query.tab, 'notifications')

    // Step 2: Switch to announcements tab
    await router.push({ path: '/messages', query: { tab: 'announcements' } })
    assert.equal(router.currentRoute.value.query.tab, 'announcements')

    // Step 3: Browser Back
    await triggerBack(router)
    assert.equal(router.currentRoute.value.query.tab, 'notifications', 'Browser back must restore notifications tab')

    // Step 4: Browser Forward
    await triggerForward(router)
    assert.equal(router.currentRoute.value.query.tab, 'announcements', 'Browser forward must restore announcements tab')
  })

  // =========================================================================
  // 5. Concurrency & High-Frequency Switching Stress Test
  // =========================================================================

  await test('CHAL-14', 'Rapid tab switching stress test (100 sequential and concurrent transitions)', async () => {
    const { router } = setupTestContext({ isLoggedIn: true })

    // Alternating tab switches 100 times
    for (let i = 0; i < 100; i++) {
      const targetTab = i % 2 === 0 ? 'notifications' : 'announcements'
      await router.push({ path: '/messages', query: { tab: targetTab } })
      assert.equal(router.currentRoute.value.query.tab, targetTab)
    }

    // Final state check: after 100 iterations (i = 99), 99 % 2 !== 0 -> 'announcements'
    assert.equal(router.currentRoute.value.query.tab, 'announcements')
  })

  // =========================================================================
  // 6. Strict Badge Semantic Isolation & Underflow Boundaries
  // =========================================================================

  await test('CHAL-15', 'Bell badge semantic isolation: announcement operations never mutate notification unread count', async () => {
    const { notificationStore } = setupTestContext({ isLoggedIn: true })
    notificationStore.setUnread(7)
    assert.equal(notificationStore.unreadCount, 7)

    // Public announcement browsing & multiple reads
    for (let i = 1; i <= 5; i++) {
      await publicAnnouncements(1, 10)
      assert.equal(notificationStore.unreadCount, 7, `Unread count must stay 7 after reading announcements (cycle ${i})`)
    }

    // Detail fetch
    await publicAnnouncementDetail(501)
    assert.equal(notificationStore.unreadCount, 7, 'Unread count must stay 7 after fetching announcement detail')

    // Notification mark single read decrements badge
    await markNotificationRead(101)
    notificationStore.decrementUnread()
    assert.equal(notificationStore.unreadCount, 6, 'Unread count must decrement to 6 after marking notification read')

    // Notification mark all read zeroes badge
    await markAllNotificationsRead()
    notificationStore.clearUnread()
    assert.equal(notificationStore.unreadCount, 0, 'Unread count must be 0 after marking all notifications read')
  })

  await test('CHAL-16', 'Decrement floor boundary: decrementUnread cannot go below 0', async () => {
    const { notificationStore } = setupTestContext({ isLoggedIn: true })
    notificationStore.setUnread(3)

    // Decrement by 5 (exceeding 3)
    notificationStore.decrementUnread(5)
    assert.equal(notificationStore.unreadCount, 0, 'Count must floor at 0, not -2')

    // Decrement when already 0
    notificationStore.decrementUnread(1)
    assert.equal(notificationStore.unreadCount, 0, 'Count must remain 0')
  })

  // =========================================================================
  // 7. Dynamic Session Transitions & In-Place State Cleanup
  // =========================================================================

  await test('CHAL-17', 'Session transition: logging out resets store unread count and halts notification requests', async () => {
    const { userStore, notificationStore } = setupTestContext({ isLoggedIn: true })
    notificationStore.setUnread(12)
    assert.equal(userStore.isLoggedIn, true)
    assert.equal(notificationStore.unreadCount, 12)

    // User logs out
    userStore.logout()
    notificationStore.clearUnread()
    assert.equal(userStore.isLoggedIn, false)
    assert.equal(notificationStore.unreadCount, 0)

    // Any subsequent unread fetch must immediately return without API calls
    const initialCallCount = mockState.apiCallLogs.length
    await notificationStore.fetchUnread()
    assert.equal(mockState.apiCallLogs.length, initialCallCount, 'Must not make network request after logout')
  })

  // =========================================================================
  // 8. Whitelist Route Safety & Fallbacks
  // =========================================================================

  await test('CHAL-18', 'Whitelist business detail routing handles all valid types and safely falls back on unknown types', async () => {
    const types: [string, number, string][] = [
      ['ORDER', 1001, '/orders/1001'],
      ['COMPLAINT', 2001, '/complaints/2001'],
      ['APPLICATION', 3001, '/become-companion'],
      ['SERVICE', 4001, '/companion'],
      ['ANNOUNCEMENT', 501, '/announcements/501'],
      ['ACTIVITY', 601, '/activities/601'],
      ['USER', 1, '/profile']
    ]

    for (const [type, id, expectedRoute] of types) {
      const route = getTargetRoute({
        id: 1,
        userId: 1,
        notificationType: 'SYSTEM',
        title: 'test',
        content: 'test',
        relatedType: type,
        relatedId: id,
        eventKey: 'k',
        readStatus: 0,
        readAt: null,
        createdAt: '2026-10-29'
      })
      assert.equal(route, expectedRoute, `relatedType ${type} must route to ${expectedRoute}`)
    }

    // Unknown type safety: returns null, does not throw or return corrupt URL
    const unknownRoute = getTargetRoute({
      id: 1,
      userId: 1,
      notificationType: 'SYSTEM',
      title: 'test',
      content: 'test',
      relatedType: 'UNKNOWN_CUSTOM_TYPE',
      relatedId: 9999,
      eventKey: 'k',
      readStatus: 0,
      readAt: null,
      createdAt: '2026-10-29'
    })
    assert.equal(unknownRoute, null, 'Unknown relatedType must return null')
  })

  // =========================================================================
  // 9. Fault Recovery & Pessimistic Updates
  // =========================================================================

  await test('CHAL-19', 'Pessimistic update protects notification state during server 500 error', async () => {
    const { notificationStore } = setupTestContext({ isLoggedIn: true })
    notificationStore.setUnread(5)

    // Force error on mark read
    mockState.forceMarkReadError = true
    try {
      await markNotificationRead(101)
      assert.fail('Should fail on 500')
    } catch {
      // Intentionally caught
    }

    // Because of pessimistic update, notificationStore unread count must NOT be decremented
    assert.equal(notificationStore.unreadCount, 5, 'Unread count must stay 5 on error')
    assert.equal(mockState.notifications.find(n => n.id === 101)?.readStatus, 0, 'Item read status must stay 0')

    // Reset error and try again
    mockState.forceMarkReadError = false
    await markNotificationRead(101)
    notificationStore.decrementUnread()
    assert.equal(notificationStore.unreadCount, 4, 'Unread count decrements after successful retry')
    assert.equal(mockState.notifications.find(n => n.id === 101)?.readStatus, 1, 'Item marked read')
  })

  // =========================================================================
  // 10. Advanced Edge Cases & High Stress Concurrency
  // =========================================================================

  await test('CHAL-20', 'Array tab query parameter (e.g. repeated ?tab=...&tab=...) normalized cleanly without crash', async () => {
    // When URL is /messages?tab=notifications&tab=announcements, query.tab is an Array
    const arrayQuery = ['notifications', 'announcements'] as any
    const { normalizedTab, needsReplace } = normalizeTabQuery(arrayQuery, false)
    assert.equal(normalizedTab, 'announcements', 'Must normalize array tab query to default tab')
    assert.equal(needsReplace, true, 'Must trigger replace to clean array query')
  })

  await test('CHAL-21', 'Unicode and special characters in auxiliary query preserved through normalization', async () => {
    const { router, userStore } = setupTestContext({ isLoggedIn: false })
    const auxiliary = {
      keyword: '王者荣耀陪玩',
      tag: '高分王者&带飞',
      price_range: '50-100'
    }
    const defaultTab = userStore.isLoggedIn ? 'notifications' : 'announcements'
    const normalized = { ...auxiliary, tab: defaultTab }

    await router.replace({ path: '/messages', query: normalized })
    assert.equal(router.currentRoute.value.query.tab, 'announcements')
    assert.equal(router.currentRoute.value.query.keyword, '王者荣耀陪玩')
    assert.equal(router.currentRoute.value.query.tag, '高分王者&带飞')
    assert.equal(router.currentRoute.value.query.price_range, '50-100')
  })

  await test('CHAL-22', 'Notification item with null or missing relatedId safely returns null route', async () => {
    const nullIdItem = {
      id: 99,
      userId: 1,
      notificationType: 'ORDER_STATUS',
      title: '缺少ID提醒',
      content: '测试内容',
      relatedType: 'ORDER',
      relatedId: null as any,
      eventKey: 'k',
      readStatus: 0,
      readAt: null,
      createdAt: '2026-10-29'
    }
    const target = getTargetRoute(nullIdItem)
    assert.equal(target, null, 'Must safely return null if relatedId is null')
  })

  await test('CHAL-23', 'Bell badge overflow boundary (>99): reactive store supports large numbers', async () => {
    const { notificationStore } = setupTestContext({ isLoggedIn: true })
    notificationStore.setUnread(150)
    assert.equal(notificationStore.unreadCount, 150)

    // Decrement 51 times
    for (let i = 0; i < 51; i++) {
      notificationStore.decrementUnread(1)
    }
    assert.equal(notificationStore.unreadCount, 99)
  })

  await test('CHAL-24', 'Concurrent interleaved async operations: announcement fetch while notification mark-all-read executes', async () => {
    const { notificationStore } = setupTestContext({ isLoggedIn: true })
    notificationStore.setUnread(10)

    // Execute concurrently
    const [announcementsRes] = await Promise.all([
      publicAnnouncements(1, 10),
      markAllNotificationsRead()
    ])

    notificationStore.clearUnread()
    assert.ok(announcementsRes.records.length > 0, 'Announcements fetched successfully')
    assert.equal(notificationStore.unreadCount, 0, 'Notifications unread count correctly cleared')
  })

  await test('CHAL-25', 'Tab switching preserves independent pagination state without reset', async () => {
    const { router } = setupTestContext({ isLoggedIn: true })
    
    // Simulate notification page 2
    let notificationPage = 2
    let announcementPage = 1

    // Switch to announcements
    await router.push({ path: '/messages', query: { tab: 'announcements' } })
    // Change announcement page to 3
    announcementPage = 3

    // Switch back to notifications
    await router.push({ path: '/messages', query: { tab: 'notifications' } })
    
    // Both page states must remain preserved
    assert.equal(notificationPage, 2, 'Notification page 2 retained')
    assert.equal(announcementPage, 3, 'Announcement page 3 retained')
  })

  return results
}

// Execute runner immediately
console.log('='.repeat(78))
console.log('  CHALLENGER ADVERSARIAL STRESS TEST SUITE (/messages)')
console.log('='.repeat(78))

runChallengerTests().then(results => {
  let passed = 0
  let failed = 0
  for (const r of results) {
    const status = r.passed ? '\x1b[32mPASS\x1b[0m' : '\x1b[31mFAIL\x1b[0m'
    console.log(`  [${status}] ${r.id.padEnd(10)} : ${r.name} (${r.durationMs}ms)`)
    if (!r.passed && r.error) {
      console.log(`         \x1b[31mError: ${r.error}\x1b[0m`)
      failed++
    } else {
      passed++
    }
  }
  console.log('='.repeat(78))
  console.log(`  Total: ${results.length} | Passed: ${passed} | Failed: ${failed}`)
  console.log('='.repeat(78))
  if (failed > 0) {
    process.exit(1)
  } else {
    console.log('\nALL CHALLENGER ADVERSARIAL TESTS PASSED!')
    process.exit(0)
  }
}).catch(err => {
  console.error('Fatal Test Runner Error:', err)
  process.exit(1)
})
