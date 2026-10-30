# Project: Unified Message Center (/messages) Integration

## Architecture
- **Framework**: Vue 3.5 + TypeScript + Vite + Element Plus + Pinia + Vue Router 4.
- **Frontend Architecture**:
  - `frontend/src/views/user/MessagesCenterView.vue`: Shell container for the unified Message Center (`/messages`), managing URL query synchronisation (`tab=notifications` | `tab=announcements`), login CTA banner for guest visitors, breadcrumb header, and tab view caching.
  - `frontend/src/components/user/NotificationListPanel.vue`: Embeddable panel managing personal in-site notifications, status filters (ALL/UNREAD/READ), pagination, mark-read / mark-all-read operations, empty/error banners, and business detail whitelist navigation.
  - `frontend/src/components/user/AnnouncementListPanel.vue`: Embeddable panel managing public platform announcements, pagination, empty/error banners, and card clicks to `/announcements/:id`.
  - `frontend/src/stores/notification.ts`: Pinia store providing reactive unread count state synchronized between top layout badges (bell & dropdown) and notification mark-read actions.
  - `frontend/src/views/user/NotificationsRedirectView.vue`: Compatibility view redirecting authenticated users to `/messages?tab=notifications`.
- **Backend Architecture**:
  - Unaltered backend API semantics: `/api/notifications/**` (authenticated user-isolated notifications) and `/api/announcements/**` (public published announcements).

## Feature Inventory
| # | Feature Code | Description | Milestone | Source |
|---|---|---|---|---|
| 1 | `F-RTE-01` | Unified Message Center Route `/messages` | M1 | survey / Spec §4.1 |
| 2 | `F-RTE-02` | Tab Query Parameter Synchronization (`tab=notifications` / `tab=announcements`) | M1 | survey / Spec §4.1, §4.2 |
| 3 | `F-RTE-03` | Query Parameter Normalization (`router.replace` on invalid/missing tab) | M1 | survey / Spec §4.2 |
| 4 | `F-AUTH-01` | Guest Default Tab (`announcements`) | M1 | survey / Spec §4.2 |
| 5 | `F-AUTH-02` | Authenticated Default Tab (`notifications`) | M1 | survey / Spec §4.2 |
| 6 | `F-AUTH-03` | Guest Notifications Tab Login CTA Banner & Button | M1 | survey / Spec §4.2, §5.1 |
| 7 | `F-AUTH-04` | Post-Login Redirect Restoration (`/login?redirect=...`) | M1 | survey / Spec §4.2 |
| 8 | `F-BCP-01` | Old Route Redirect `/announcements` -> `/messages?tab=announcements` | M1 | survey / Spec §5.2 |
| 9 | `F-BCP-02` | Old Route Guard & Redirect `/notifications` -> auth check -> `/messages?tab=notifications` | M1 | survey / Spec §5.2 |
| 10 | `F-BCP-03` | Announcement Detail Back Button Navigation -> `/messages?tab=announcements` | M1 | survey / Spec §4.5 |
| 11 | `F-BCP-04` | Notification Business Detail Whitelist Navigation | M1 | survey / Spec §4.4 |
| 12 | `F-PNL-01` | Notification Panel Embedding (`NotificationListPanel.vue`) | M1 | survey / Spec §4.4 |
| 13 | `F-PNL-02` | Announcement Panel Embedding (`AnnouncementListPanel.vue`) | M1 | survey / Spec §4.5 |
| 14 | `F-ISO-01` | Independent Pagination State (Notifications vs Announcements) | M1 | survey / Spec §4.6 |
| 15 | `F-ISO-02` | Independent Loading States (Notifications vs Announcements) | M1 | survey / Spec §4.6 |
| 16 | `F-ISO-03` | Distinct Empty States ("暂无站内通知" vs "暂无已发布的平台公告") | M1 | survey / Spec §4.6 |
| 17 | `F-ISO-04` | Distinct Error States & Inline Retry Button | M1 | survey / Spec §4.6 |
| 18 | `F-ISO-05` | Zero Side-Effect Tab Switching (no write actions or re-fetches) | M1 | survey / Spec §4.6 |
| 19 | `F-NOT-01` | Notification Status Filter Toggle (ALL / UNREAD / READ) | M1 | survey / Spec §4.4 |
| 20 | `F-NOT-02` | Single Notification Mark Read & Store Sync | M1 | survey / Spec §4.4 |
| 21 | `F-NOT-03` | One-Click Mark All Read & Store Sync | M1 | survey / Spec §4.4 |
| 22 | `F-NAV-01` | Header Bell Icon Route Update -> `/messages?tab=notifications` | M1 | survey / Spec §4.3 |
| 23 | `F-NAV-02` | Bell Badge Semantics Isolation (strictly notifications, excludes announcements) | M1 | survey / Spec §1, §4.3 |
| 24 | `F-NAV-03` | User Dropdown Menu Entry -> "消息中心" (`/messages?tab=notifications`) | M1 | survey / Spec §4.3 |
| 25 | `F-NAV-04` | Footer & Home Link Updates -> `/messages?tab=announcements` | M1 | survey / Spec §4.3 |

## Milestones
| # | Name | Scope | Dependencies | Status |
|---|------|-------|-------------|--------|
| M1 | Implementation Track: Unified Message Center Integration | Implement R1 (/messages route, query sync, guest defaults, backward compat), R2 (panel embedding, state isolation, empty/error states), and R3 (navigation entries, bell badge semantics) | none | IN_PROGRESS |
| M2 | E2E Testing Track: Opaque-Box E2E Test Suite | Design E2E test infra (`TEST_INFRA.md`), create 4-tier test cases (Tier 1-4), provide test runner, and publish `TEST_READY.md` | none | IN_PROGRESS |
| M3 | Final Milestone: 100% E2E Pass, Hardening & Audit | Phase 1: Pass 100% E2E tests (Tiers 1-4). Phase 2: Tier 5 adversarial coverage hardening. Forensic Audit & completion | M1, M2 (`TEST_READY.md`) | PLANNED |

## Interface Contracts

### Notification Store (`frontend/src/stores/notification.ts`)
```typescript
export const useNotificationStore = defineStore('notification', () => {
  const unreadCount = ref<number>(0)
  const loading = ref<boolean>(false)

  async function fetchUnread(): Promise<void>
  function decrementUnread(amount?: number): void
  function clearUnread(): void
  function setUnread(count: number): void

  return { unreadCount, loading, fetchUnread, decrementUnread, clearUnread, setUnread }
})
```

### Route Contract (`frontend/src/router/index.ts`)
- `/messages`: Name `'user-messages'`, component `MessagesCenterView.vue`, meta `{ title: '消息中心' }` (no `requiresAuth`, public).
- `/announcements`: Redirect to `{ path: '/messages', query: { tab: 'announcements' } }`.
- `/notifications`: Name `'user-notifications'`, meta `{ title: '通知中心', requiresAuth: true }`, redirect to `{ path: '/messages', query: { tab: 'notifications' } }`.
- `/announcements/:id`: Name `'user-announcement-detail'`, component `AnnouncementDetailView.vue`, meta `{ title: '公告详情' }`.

## Code Layout
- `frontend/src/views/user/MessagesCenterView.vue`: Shell container for Message Center.
- `frontend/src/components/user/NotificationListPanel.vue`: Notification list panel component.
- `frontend/src/components/user/AnnouncementListPanel.vue`: Announcement list panel component.
- `frontend/src/stores/notification.ts`: Notification Pinia store.
- `frontend/src/views/user/NotificationsRedirectView.vue`: Compatibility redirect view.
- `frontend/src/router/index.ts`: Route definitions and guards.
- `frontend/src/views/user/UserLayout.vue`: Header bell, user dropdown, footer links.
- `frontend/src/views/user/HomeView.vue`: Homepage announcement link.
- `frontend/src/views/user/AnnouncementDetailView.vue`: Announcement detail back navigation.
- `tests/e2e/`: E2E test suite directory (owned by E2E Testing Track).
