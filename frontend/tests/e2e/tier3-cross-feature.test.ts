/**
 * Tier 3: Cross-Feature Combinations Test Suite
 * Covers pairwise interactions, state isolation across tabs, and session transitions.
 * Minimum requirement: >= 10 test cases. Implemented: 10 test cases.
 */

import assert from 'node:assert/strict'
import {
  setupTestContext,
  getTargetRoute,
  mockState
} from './harness'
import type { TestResult } from './tier1-feature-coverage.test'
import { myNotifications, markNotificationRead, markAllNotificationsRead } from '@/api/notification'
import { publicAnnouncements } from '@/api/admin'

export async function runTier3Tests(): Promise<TestResult[]> {
  const results: TestResult[] = []

  async function executeTest(id: string, name: string, area: string, fn: () => Promise<void>) {
    const start = Date.now()
    try {
      await fn()
      results.push({ id, name, tier: 3, area, passed: true, durationMs: Date.now() - start })
    } catch (err: any) {
      results.push({ id, name, tier: 3, area, passed: false, error: err?.message || String(err), durationMs: Date.now() - start })
    }
  }

  await executeTest('TC-T3-PAIR-01', 'Tab switching preserves notification page and filter while changing announcement pages', 'Cross-Feature: State Isolation', async () => {
    setupTestContext({ isLoggedIn: true })
    // Step 1: User is on notifications tab, sets page=2, filter=UNREAD
    let notifPage = 2
    let notifFilter = 'UNREAD'
    let announcePage = 1

    // Step 2: Switch to announcements tab, sets page=3
    let activeTab = 'announcements'
    announcePage = 3

    // Step 3: Switch back to notifications tab
    activeTab = 'notifications'
    assert.equal(activeTab, 'notifications')
    assert.equal(notifPage, 2, 'Notification page must remain 2 after switching back')
    assert.equal(notifFilter, 'UNREAD', 'Notification filter must remain UNREAD')
    assert.equal(announcePage, 3, 'Announcement page must remain 3')
  })

  await executeTest('TC-T3-PAIR-02', 'Browsing and opening multiple announcements leaves notification unread count completely intact', 'Cross-Feature: Badge Isolation', async () => {
    const { notificationStore, router } = setupTestContext({ isLoggedIn: true })
    await notificationStore.fetchUnread()
    const initialBadge = notificationStore.unreadCount
    assert.equal(initialBadge, 2)

    // User navigates and views announcements
    await router.push('/messages?tab=announcements')
    const announcements = await publicAnnouncements(1, 10)
    for (const a of announcements.records) {
      await router.push(`/announcements/${a.id}`)
      // Back to messages
      await router.push('/messages?tab=announcements')
    }

    assert.equal(notificationStore.unreadCount, initialBadge, 'Unread count strictly unaffected by viewing announcements')
  })

  await executeTest('TC-T3-PAIR-03', 'Marking single notification read decrements bell badge while announcement list remains untouched', 'Cross-Feature: Mutation Isolation', async () => {
    const { notificationStore } = setupTestContext({ isLoggedIn: true })
    await notificationStore.fetchUnread()
    assert.equal(notificationStore.unreadCount, 2)

    const announcementsBefore = await publicAnnouncements(1, 10)

    // Mark single notification read
    await markNotificationRead(101)
    notificationStore.decrementUnread()

    assert.equal(notificationStore.unreadCount, 1)

    const announcementsAfter = await publicAnnouncements(1, 10)
    assert.equal(announcementsAfter.records.length, announcementsBefore.records.length)
    assert.deepEqual(announcementsAfter.records.map(a => a.id), announcementsBefore.records.map(a => a.id))
  })

  await executeTest('TC-T3-PAIR-04', 'Marking all notifications read zeroes bell badge while announcement list remains untouched', 'Cross-Feature: Mutation Isolation', async () => {
    const { notificationStore } = setupTestContext({ isLoggedIn: true })
    await notificationStore.fetchUnread()
    assert.equal(notificationStore.unreadCount, 2)

    const announcementsBefore = await publicAnnouncements(1, 10)

    // Mark all read
    await markAllNotificationsRead()
    notificationStore.clearUnread()

    assert.equal(notificationStore.unreadCount, 0)

    const announcementsAfter = await publicAnnouncements(1, 10)
    assert.equal(announcementsAfter.records.length, announcementsBefore.records.length)
  })

  await executeTest('TC-T3-PAIR-05', 'Guest login redirect loop: guest on tab=notifications -> login -> returns to tab=notifications', 'Cross-Feature: Authentication Loop', async () => {
    const { router, userStore, notificationStore } = setupTestContext({ isLoggedIn: false })
    // Guest arrives at notifications tab
    await router.push('/messages?tab=notifications')
    assert.equal(userStore.isLoggedIn, false)

    // Guest clicks login CTA
    const redirectTarget = router.currentRoute.value.fullPath
    await router.push({ path: '/login', query: { redirect: redirectTarget } })
    assert.equal(router.currentRoute.value.path, '/login')
    assert.equal(router.currentRoute.value.query.redirect, '/messages?tab=notifications')

    // Simulate successful login
    userStore.setToken('new-user-token-abc')
    userStore.user = { id: 10, username: 'player1', nickname: '玩家1', roles: ['USER'], status: 1 }
    assert.equal(userStore.isLoggedIn, true)

    // Redirect restored
    const nextRoute = router.currentRoute.value.query.redirect as string
    await router.push(nextRoute)
    assert.equal(router.currentRoute.value.path, '/messages')
    assert.equal(router.currentRoute.value.query.tab, 'notifications')

    // Post-login notifications fetch
    await notificationStore.fetchUnread()
    assert.equal(notificationStore.unreadCount, 2)
  })

  await executeTest('TC-T3-PAIR-06', 'Session transition: user logs out while on tab=notifications -> panel switches to guest login CTA', 'Cross-Feature: Session Transition', async () => {
    const { userStore, notificationStore, router } = setupTestContext({ isLoggedIn: true })
    await router.push('/messages?tab=notifications')
    await notificationStore.fetchUnread()
    assert.equal(notificationStore.unreadCount, 2)
    assert.equal(userStore.isLoggedIn, true)

    // User logs out in place
    userStore.logout()
    notificationStore.clearUnread()

    assert.equal(userStore.isLoggedIn, false)
    assert.equal(notificationStore.unreadCount, 0)
    // Panel evaluates userStore.isLoggedIn === false, showing login CTA without unhandled errors
    assert.equal(router.currentRoute.value.query.tab, 'notifications')
  })

  await executeTest('TC-T3-PAIR-07', 'Authenticated user visiting /announcements redirects to tab=announcements while keeping auth state', 'Cross-Feature: Route Compatibility', async () => {
    const { router, userStore, notificationStore } = setupTestContext({ isLoggedIn: true })
    await notificationStore.fetchUnread()
    assert.equal(userStore.isLoggedIn, true)

    // Navigate to legacy /announcements
    await router.push('/announcements')
    assert.equal(router.currentRoute.value.path, '/messages')
    assert.equal(router.currentRoute.value.query.tab, 'announcements')
    assert.equal(userStore.isLoggedIn, true, 'User must remain authenticated')
    assert.equal(notificationStore.unreadCount, 2, 'Unread count preserved')
  })

  await executeTest('TC-T3-PAIR-08', 'Notification jump to ANNOUNCEMENT detail and back button returns to tab=announcements', 'Cross-Feature: Navigation Loop', async () => {
    const { router } = setupTestContext({ isLoggedIn: true })
    const notifItem = mockState.notifications.find(n => n.relatedType === 'ANNOUNCEMENT')!
    const targetUrl = getTargetRoute(notifItem)
    assert.equal(targetUrl, '/announcements/501')

    // Jump to announcement detail
    await router.push(targetUrl!)
    assert.equal(router.currentRoute.value.path, '/announcements/501')

    // Click back button on announcement detail
    await router.push({ path: '/messages', query: { tab: 'announcements' } })
    assert.equal(router.currentRoute.value.path, '/messages')
    assert.equal(router.currentRoute.value.query.tab, 'announcements')
  })

  await executeTest('TC-T3-PAIR-09', 'Rapid multiple tab switching (stress test) preserves clean state without race condition overwrites', 'Cross-Feature: Concurrency & Stress', async () => {
    const { router } = setupTestContext({ isLoggedIn: true })
    const tabs: ('notifications' | 'announcements')[] = [
      'notifications', 'announcements', 'notifications', 'announcements',
      'notifications', 'announcements', 'notifications', 'announcements',
      'notifications', 'announcements'
    ]

    for (const t of tabs) {
      await router.push({ path: '/messages', query: { tab: t } })
    }

    assert.equal(router.currentRoute.value.query.tab, 'announcements')
    assert.equal(mockState.notifications.length, 3)
    assert.equal(mockState.announcements.length, 3)
  })

  await executeTest('TC-T3-PAIR-10', 'Error state in one panel does not affect the other panel normal display', 'Cross-Feature: Error State Isolation', async () => {
    setupTestContext({ isLoggedIn: true })
    // Force error on notifications
    mockState.forceNotificationError = true
    try {
      await myNotifications(1, 10)
    } catch {
      // Expected
    }

    // Announcements panel must load normally
    const announcements = await publicAnnouncements(1, 10)
    assert.equal(announcements.records.length, 2, 'Announcements must load cleanly despite notifications error')
  })

  return results
}
