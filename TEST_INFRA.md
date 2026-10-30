# E2E Test Infrastructure Specification: Unified Message Center (/messages)

> **Document Version**: 1.0.0  
> **Author**: `test_writer_1` (E2E Testing Track)  
> **Target System**: Game Companion System Frontend (`frontend/src/`)  
> **Authoritative Specifications**:
> - `d:\bishe\游戏陪玩系统\.agents\teamwork\ORIGINAL_REQUEST.md`
> - `d:\bishe\游戏陪玩系统\PROJECT.md`
> - `docs/站内通知与平台公告整合详细设计方案.md`
> - `d:\bishe\游戏陪玩系统\.agents\teamwork\spec_miner_0\handoff.md`

---

## 1. Philosophy & Testing Principles

1. **Opaque-Box Testing**:
   Tests are written strictly against requirements, URL routes, user observable DOM elements, reactive store state, and HTTP interface contracts. Tests do not assert on private component internals or implementation details.
2. **Dual Track Isolation**:
   The E2E test suite is developed independently from the implementation track (`worker_m1`), providing an objective, unbiased verification oracle.
3. **Requirement Traceability**:
   Every test case maps directly to a requirement (`R1`, `R2`, `R3`), acceptance criterion (`AC`), or edge case specified in the design document.
4. **Self-Contained & Deterministic**:
   Each test case sets up its own isolated context (stores, router location, and API mocks), executes deterministically, and cleans up after itself without cross-test state leakage.
5. **Fast & Portable Execution**:
   The test runner executes cleanly in any environment without external network latency, using Node.js native execution with TypeScript type-stripping / `tsx`.

---

## 2. Test Architecture & Runner Setup

### 2.1 Technology Stack
- **Runtime**: Node.js v24 (ESM + native TypeScript type stripping / `tsx`)
- **Test Engine**: Native Node.js test harness (`node:test`, `node:assert/strict`)
- **Routing Engine**: `vue-router` v4 Memory History (`createMemoryHistory`) exercising real route declarations and navigation guards
- **State Store**: `pinia` v2 store instances (`createPinia`, `setActivePinia`) exercising real reactive state
- **Mock Interceptor Layer**: In-memory deterministic API response dispatchers for `/api/notifications/**` and `/api/announcements/**`

### 2.2 Directory Layout
```text
frontend/tests/e2e/
├── run-all.mjs                     # Root executable runner script
├── runner.ts                       # Test harness runner engine & reporter
├── harness.ts                      # Shared test environment, router simulator, mocks & helpers
├── tier1-feature-coverage.test.ts  # Tier 1: Feature Coverage (R1, R2, R3)
├── tier2-boundary-corner.test.ts   # Tier 2: Boundary & Corner Cases (R1, R2, R3)
├── tier3-cross-feature.test.ts     # Tier 3: Cross-Feature Combinations
└── tier4-real-world-scenarios.test.ts # Tier 4: Real-World User Journeys & Scenarios

tests/e2e/
└── run-all.mjs                     # Root convenience launcher forwarding to frontend test runner
```

### 2.3 Execution Command
To run the entire E2E test suite (Tiers 1–4):
```bash
# Direct runner execution from project root:
node frontend/tests/e2e/run-all.mjs

# Or from frontend directory:
node tests/e2e/run-all.mjs

# Or via npm script:
npm --prefix frontend run test:e2e
```

To run individual tiers:
```bash
node frontend/tests/e2e/run-all.mjs --tier=1
node frontend/tests/e2e/run-all.mjs --tier=2
node frontend/tests/e2e/run-all.mjs --tier=3
node frontend/tests/e2e/run-all.mjs --tier=4
```

---

## 3. Four-Tier Test Suite Specification

### Tier 1: Feature Coverage (>=5 test cases per feature area)
Covers baseline happy-path behaviors for R1 (Route Architecture), R2 (Panel Embedding & State Isolation), and R3 (Navigation Entries & Badge Semantics).

| Test Case ID | Feature Area | Description | Input / Trigger | Expected Output / Assertion | Source |
|---|---|---|---|---|---|
| `TC-T1-R1-01` | R1 Route | Public access to `/messages?tab=announcements` | Guest navigates to `/messages?tab=announcements` | Route resolves without auth redirect; active tab is `announcements` | Spec §4.1, §5.1 |
| `TC-T1-R1-02` | R1 Route | Guest visiting `/messages` defaults to announcements | Guest navigates to `/messages` (no query) | URL query normalizes to `tab=announcements`; active tab is `announcements` | Spec §4.2 |
| `TC-T1-R1-03` | R1 Route | Authenticated user visiting `/messages` defaults to notifications | Authenticated user navigates to `/messages` | URL query normalizes to `tab=notifications`; active tab is `notifications` | Spec §4.2 |
| `TC-T1-R1-04` | R1 Route | Guest on notifications tab displays Login CTA | Guest navigates to `/messages?tab=notifications` | Login CTA banner displayed; protected notification API NOT called | Spec §4.2, §5.1 |
| `TC-T1-R1-05` | R1 Route | Guest Login CTA navigation | Click "去登录" button on login CTA | Router navigates to `/login?redirect=%2Fmessages%3Ftab%3Dnotifications` | Spec §4.2 |
| `TC-T1-R1-06` | R1 Route | Legacy route redirect `/announcements` | User navigates to `/announcements` | Router redirects to `/messages?tab=announcements` | Spec §5.2 |
| `TC-T1-R1-07` | R1 Route | Legacy route redirect `/notifications` with auth check | Guest vs Auth user navigates to `/notifications` | Guest redirects to `/login?redirect=...`; Auth redirects to `/messages?tab=notifications` | Spec §5.2 |
| `TC-T1-R1-08` | R1 Route | Announcement detail back navigation | User clicks "返回" on `/announcements/:id` | Navigates back to `/messages?tab=announcements` | Spec §4.5 |
| `TC-T1-R2-01` | R2 Panel | Notification panel renders list & status filters | Authenticated user loads notifications tab | Renders notification items; filter options `ALL`, `UNREAD`, `READ` available | Spec §4.4 |
| `TC-T1-R2-02` | R2 Panel | Announcement panel renders published list & time | User loads announcements tab | Renders published announcement cards with timestamp | Spec §4.5 |
| `TC-T1-R2-03` | R2 Panel | Independent pagination between panels | User sets page 2 on notifications, switches to announcements | Announcements pagination is page 1; returning to notifications retains page 2 | Spec §4.6 |
| `TC-T1-R2-04` | R2 Panel | Distinct empty state messages | Empty datasets loaded for both panels | Notifications: "暂无站内通知"; Announcements: "暂无已发布的平台公告" | Spec §4.6 |
| `TC-T1-R2-05` | R2 Panel | Single notification mark read | Click "标为已读" on unread notification | Item `readStatus` becomes 1; notification store unread count decrements | Spec §4.4 |
| `TC-T1-R2-06` | R2 Panel | One-click mark all read | Click "一键全部已读" | All loaded items `readStatus` become 1; unread count becomes 0 | Spec §4.4 |
| `TC-T1-R2-07` | R2 Panel | Error banner with inline retry | API returns failure during panel load | Inline error message displayed; clicking "重试" re-triggers fetch | Spec §4.6 |
| `TC-T1-R2-08` | R2 Panel | Notification detail whitelist navigation | Click notification with `relatedType: 'ORDER'` | Navigates to `/orders/${relatedId}` | Spec §4.4 |
| `TC-T1-R3-01` | R3 Nav | Header bell icon routes to notifications tab | Click header bell icon | Navigates to `/messages?tab=notifications` | Spec §4.3 |
| `TC-T1-R3-02` | R3 Nav | Bell badge strictly counts notifications | Notification store loads unread count = 5 | Bell badge value is 5; excludes announcements | Spec §1, §4.3 |
| `TC-T1-R3-03` | R3 Nav | Reading announcements does not alter badge | User browses announcements | Bell badge unread count remains unchanged | Spec §4.3, §4.5 |
| `TC-T1-R3-04` | R3 Nav | User dropdown menu item routes to notifications | Click user dropdown "消息中心" | Navigates to `/messages?tab=notifications` | Spec §4.3 |
| `TC-T1-R3-05` | R3 Nav | Footer "平台公告" link routes to announcements | Click footer "平台公告" link | Navigates to `/messages?tab=announcements` | Spec §4.3 |
| `TC-T1-R3-06` | R3 Nav | Home "更多公告 →" link routes to announcements | Click Home "更多公告 →" link | Navigates to `/messages?tab=announcements` | Spec §4.3 |
| `TC-T1-R3-07` | R3 Nav | Badge hidden when count is 0 | Unread count becomes 0 | Bell badge is hidden (`unreadCount === 0`) | Spec §4.3 |

---

### Tier 2: Boundary & Corner Cases (>=5 test cases per feature area)
Covers invalid query inputs, boundary values, empty datasets, session resets, and error handling.

| Test Case ID | Feature Area | Description | Input / Trigger | Expected Output / Assertion | Source |
|---|---|---|---|---|---|
| `TC-T2-R1-01` | R1 Boundary | Missing query parameter normalization | Navigate to `/messages` | Normalizes to default tab via `router.replace` without history stacking | Spec §4.2 |
| `TC-T2-R1-02` | R1 Boundary | Invalid query string parameter | Navigate to `/messages?tab=invalid_tab` | Normalizes to default tab via `router.replace` | Spec §4.2 |
| `TC-T2-R1-03` | R1 Boundary | Empty query parameter value | Navigate to `/messages?tab=` | Normalizes to default tab via `router.replace` | Spec §4.2 |
| `TC-T2-R1-04` | R1 Boundary | Malformed / special character tab value | Navigate to `/messages?tab=%3Cscript%3E` | Sanitized and normalized to default tab | Spec §4.2 |
| `TC-T2-R1-05` | R1 Boundary | Router history cleanliness during normalization | Normalization triggered on load | History length does NOT increment (uses replace) | Spec §4.2 |
| `TC-T2-R1-06` | R1 Boundary | Preservation of auxiliary query parameters | Navigate to `/messages?ref=footer` | Resulting URL is `/messages?ref=footer&tab=...` | Spec §4.2 |
| `TC-T2-R2-01` | R2 Boundary | Zero notification records empty state | API returns empty records `[]` | Renders "暂无站内通知" empty container | Spec §4.6 |
| `TC-T2-R2-02` | R2 Boundary | Zero announcement records empty state | API returns empty records `[]` | Renders "暂无已发布的平台公告" empty container | Spec §4.6 |
| `TC-T2-R2-03` | R2 Boundary | Empty state under UNREAD filter | All notifications read, switch to UNREAD | Renders empty state; does not crash | Spec §4.4, §4.6 |
| `TC-T2-R2-04` | R2 Boundary | Single mark read API 500 failure | Mark read API throws 500 error | Item `readStatus` remains 0 (pessimistic update); error toast shown | Spec §4.4 |
| `TC-T2-R2-05` | R2 Boundary | Mark all read API 500 failure | Mark all read API throws 500 error | Items retain unread status; unread count untouched | Spec §4.4 |
| `TC-T2-R2-06` | R2 Boundary | Unknown `relatedType` safety | Click notification with `relatedType: 'CUSTOM_UNKNOWN'` | No invalid route navigation triggered; readStatus updated | Spec §4.4 |
| `TC-T2-R3-01` | R3 Boundary | Bell badge hidden when count is 0 | Store `unreadCount = 0` | Badge is hidden in DOM | Spec §4.3 |
| `TC-T2-R3-02` | R3 Boundary | Bell badge overflow boundary (>99) | Store `unreadCount = 150` | Badge respects `:max="99"` displaying "99+" | Spec §4.3 |
| `TC-T2-R3-03` | R3 Boundary | User logout clears badge count | User logs out while badge is 5 | Store `unreadCount` resets to 0 immediately | Spec §4.3 |
| `TC-T2-R3-04` | R3 Boundary | Decrement floor boundary | `decrementUnread()` called when count is 0 | `unreadCount` remains 0 (never negative) | Contract §5.2 |
| `TC-T2-R3-05` | R3 Boundary | Guest never fetches notification unread count | Guest session initialization | `fetchUnread()` returns immediately without API call | Spec §4.3 |
| `TC-T2-R3-06` | R3 Boundary | Token expiry during active session | API returns 401 on refresh | Gracefully handled without unhandled promise rejection | Spec §5.1 |

---

### Tier 3: Cross-Feature Combinations (Pairwise Interactions)
Covers multi-feature workflows and state isolation across concurrent interactions.

| Test Case ID | Combination | Description | Input / Trigger | Expected Output / Assertion | Source |
|---|---|---|---|---|---|
| `TC-T3-PAIR-01` | Tab Switch & Pagination | Independent pagination persistence | Change page on notifications, switch to announcements, change page, switch back | Both panels retain their respective page numbers | Spec §4.6 |
| `TC-T3-PAIR-02` | Announcements & Bell Badge | Reading announcements isolation | Open announcement list and detail pages | Notification unread count in store and bell badge unchanged | Spec §4.3, §4.5 |
| `TC-T3-PAIR-03` | Mark Read & Announcements | Single mark read isolation | Mark notification as read | Notification badge decrements; announcement data completely untouched | Spec §4.4 |
| `TC-T3-PAIR-04` | Mark All Read & Announcements | Mark all read isolation | Click "一键全部已读" | Notification badge zeroes; announcement data completely untouched | Spec §4.4 |
| `TC-T3-PAIR-05` | Guest Login & Tab Preservation | Guest login restoration loop | Guest visits `tab=notifications`, clicks login CTA, completes login | Navigates to `/login?redirect=...` and post-login restores `tab=notifications` | Spec §4.2 |
| `TC-T3-PAIR-06` | Session Switch & Panel State | In-place logout while on notifications | User logs out while viewing `tab=notifications` | Panel immediately swaps to Guest Login CTA without 401 errors | Spec §4.2, §5.1 |
| `TC-T3-PAIR-07` | Legacy Route & Session State | Authenticated user visits `/announcements` | Authenticated user opens `/announcements` | Redirects to `/messages?tab=announcements` while keeping auth state | Spec §5.2 |
| `TC-T3-PAIR-08` | Notification Whitelist & Back Navigation | Notification announcement link loop | Click notification with `relatedType: 'ANNOUNCEMENT'`, then click back | Opens `/announcements/:id`, clicking back navigates to `/messages?tab=announcements` | Spec §4.5, §5.2 |
| `TC-T3-PAIR-09` | Fast Tab Flipping | Concurrent tab switching stress test | Rapidly alternate between tabs 10 times | Final URL and panel match; no unhandled promise rejections | Spec §4.6 |
| `TC-T3-PAIR-10` | Error State Isolation | Error in one panel does not affect other | Force error on notifications panel, switch to announcements | Announcements panel loads successfully; notifications retains retry state | Spec §4.6 |

---

### Tier 4: Real-World Application Scenarios (End-to-End User Journeys)
Covers end-to-end user journeys simulating real browser usage patterns.

| Test Case ID | Scenario | Description | Workflow Steps | Expected Final Outcome | Source |
|---|---|---|---|---|---|
| `TC-T4-SCEN-01` | Complete Guest Journey | Guest browses announcements and discovers notification login prompt | 1. Visit `/`<br>2. Click footer "平台公告"<br>3. View announcement list on `/messages?tab=announcements`<br>4. Click announcement to view detail `/announcements/1`<br>5. Click "返回公告列表"<br>6. Click "站内通知" tab<br>7. Verify Guest Login CTA<br>8. Click "去登录" | Lands on `/login` with `redirect=/messages?tab=notifications` | AC 1, AC 2, Spec §4.2 |
| `TC-T4-SCEN-02` | Authenticated User Journey | Logged-in user checks and marks notifications | 1. Authenticate user (unreadCount=3)<br>2. Click header bell icon<br>3. Lands on `/messages?tab=notifications`<br>4. Switch filter to `UNREAD`<br>5. Click notification to read<br>6. Store unreadCount decrements to 2<br>7. Whitelist jump to business entity | Target business page opened; bell badge displays 2 | AC 2, AC 8, Spec §4.3 |
| `TC-T4-SCEN-03` | Mark All Read Flow | Bulk notification clearance | 1. Authenticate user with 10 unread notifications<br>2. Navigate to `/messages?tab=notifications`<br>3. Click "一键全部已读"<br>4. Verify store unreadCount = 0<br>5. Verify all cards show read style<br>6. Bell badge hidden | Badge completely hidden; unread count zeroed | AC 8, Spec §4.4 |
| `TC-T4-SCEN-04` | Revoked Announcement Detail Handling | User opens link to revoked announcement | 1. Navigate to `/announcements/999` (revoked/404)<br>2. Error state renders "该公告不存在或已被撤回"<br>3. Click "返回列表" button | Cleanly navigates to `/messages?tab=announcements` | Spec §4.5, Line 123 |
| `TC-T4-SCEN-05` | Legacy External Bookmarks | Compatibility with external and saved links | 1. Guest visits `/announcements`<br>2. Authenticated user visits `/notifications`<br>3. Guest visits `/notifications` | 1. Redirects to `/messages?tab=announcements`<br>2. Redirects to `/messages?tab=notifications`<br>3. Intercepted by auth guard to `/login?redirect=...` | AC 3, AC 4, Spec §5.2 |
| `TC-T4-SCEN-06` | Network Error Recovery | Inline error retry workflow | 1. Open `/messages?tab=notifications` with network failure<br>2. Error banner displayed with "重试"<br>3. Restore network and click "重试" | Error banner disappears and notification list loads successfully | Spec §4.6 |
| `TC-T4-SCEN-07` | Cross-Page Tab Persistence | In-app navigation away and back | 1. User on `/messages?tab=notifications`<br>2. Navigate to `/games`<br>3. Click bell icon in header | Returns to `/messages?tab=notifications` with state intact | Spec §4.3 |
| `TC-T4-SCEN-08` | Adversarial Input Resistance | Malicious / fuzzed query parameter injection | 1. Visit `/messages?tab=%3Cscript%3Ealert(1)%3C/script%3E`<br>2. Visit `/messages?tab=../../admin`<br>3. Visit `/messages?tab=null` | All queries cleanly sanitized and normalized to legitimate default tabs | Spec §4.2 |

---

## 4. Total Test Case Metrics

| Tier | Category | Minimum Required | Actual Implemented |
|---|---|---|---|
| **Tier 1** | Feature Coverage (R1, R2, R3) | >= 15 (>=5 per area) | **23** |
| **Tier 2** | Boundary & Corner Cases (R1, R2, R3) | >= 15 (>=5 per area) | **18** |
| **Tier 3** | Cross-Feature Combinations | >= 10 | **10** |
| **Tier 4** | Real-World Application Scenarios | >= 8 | **8** |
| **Total** | **All Tiers Combined** | **>= 48** | **59** |

---

## 5. Verification & Acceptance Criteria

1. **Clean Test Run**:
   Running `node frontend/tests/e2e/run-all.mjs` must execute all 59 test cases and report 0 failures.
2. **Opaque-Box Compliance**:
   No assertions rely on component private properties. All assertions verify observable contracts (URL query parameters, route destinations, Pinia reactive store state, and DOM text/selectors).
3. **Requirement Satisfaction**:
   All acceptance criteria from `ORIGINAL_REQUEST.md` (AC 1 through AC 8) and architectural specifications from `docs/站内通知与平台公告整合详细设计方案.md` are completely covered.
