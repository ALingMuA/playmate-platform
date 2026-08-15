# 游戏陪玩系统 Code Wiki

> 本文档基于对仓库源码的静态分析整理，目标读者为首次接触本仓库的开发者。
> 系统配套设计文档：`需求分析说明书.md`、`概要设计说明书.md`、`详细设计说明书.md`（毕设三件套，代码中的 FR 编号均可在其中回溯）。
> 生成日期：2026-08-15

---

## 1. 项目概览

| 项 | 内容 |
|---|---|
| 项目名称 | 游戏陪玩系统（Game Companion Platform） |
| 产品形态 | Web 端 B/S 系统，C2C 多游戏陪玩平台 |
| 架构风格 | 前后端分离 + 后端模块化单体 |
| 系统角色 | 游客、普通用户（USER）、陪玩师（COMPANION）、客服（CUSTOMER_SERVICE）、管理员（ADMIN） |
| 核心业务 | 陪玩师入驻与审核、服务项目与档期、预约下单与模拟支付、履约、评价投诉仲裁、AI+人工客服 |
| 首期边界 | 模拟支付 + 虚拟余额（无真实支付/提现）、无语音房/直播/私信 |

### 1.1 技术栈

**后端**（`backend/`）
- Java 17 + Spring Boot 3.5（`spring-boot-starter-parent:3.5.16`）
- Spring Security（无状态 JWT + RBAC）、Spring WebSocket（客服实时消息）
- MyBatis-Plus 3.5.17（含分页插件 jsqlparser）、MySQL 8.0
- JWT：jjwt 0.13.0（HMAC-SHA256）
- 构建：Maven；测试：JUnit 5 + spring-boot-starter-test

**前端**（`frontend/`）
- Vue 3 + TypeScript + Vite + Vue Router + Pinia + Element Plus + axios

**数据库**
- MySQL 8.0，库名 `game_companion_app`，共 30 张表（`sql/schema.sql`）

---

## 2. 整体架构

```
┌─────────────────────────────────────────────────────────────┐
│ 前端 Vue3 (frontend/, :5173)                                 │
│   /            用户端      /companion 陪玩师端                 │
│   /support     在线客服    /cs       客服工作台               │
│   /admin       管理后台                                       │
│   axios 封装 + WebSocket 客户端(CsSocket)                     │
└──────────────┬──────────────────────────────────────────────┘
               │ /api (REST)          │ /ws/cs (WebSocket, 带 JWT 握手)
               ▼                      ▼
┌─────────────────────────────────────────────────────────────┐
│ 后端 Spring Boot 3 模块化单体 (backend/, :8080)               │
│                                                             │
│  ┌─ 接入层 ─────────────────────────────────────────────┐   │
│  │ JwtAuthFilter(OncePerRequestFilter) + SecurityConfig │   │
│  │ GlobalExceptionHandler + ApiResponse 统一响应         │   │
│  └──────────────────────────────────────────────────────┘   │
│  ┌─ 业务模块(模块化单体，按业务分包)──────────────────────┐    │
│  │ auth user catalog companion order wallet review       │    │
│  │ favorite announcement notification file audit admin   │    │
│  │ customer_service ai                                   │    │
│  └──────────────────────────────────────────────────────┘   │
│  ┌─ 基础设施(infrastructure)─────────────────────────────┐   │
│  │ websocket(客服实时推送) schedule(订单超时) storage     │   │
│  └──────────────────────────────────────────────────────┘   │
│  持久层: MyBatis-Plus Mapper → MySQL 8.0 (game_companion_app)│
└─────────────────────────────────────────────────────────────┘
```

分层约定：每个业务模块内部按 `controller → dto → service → domain(entity) → mapper → enums` 分包，Controller 只做参数解析与鉴权，业务逻辑收敛在 Service，数据库访问走 Mapper（MyBatis-Plus 条件构造器或自定义 SQL）。

---

## 3. 仓库目录结构

```
/workspace
├── 需求分析说明书.md / 概要设计说明书.md / 详细设计说明书.md   # 毕设设计文档（FR 编号来源）
├── README.md            # 快速启动 + 已实现接口清单
├── CODE_WIKI.md         # 本文档
├── sql/
│   ├── schema.sql       # 30 张表 DDL（含建库语句）
│   └── data.sql         # 种子数据：4 角色 + 预置 admin + 目录基础数据（幂等）
├── backend/             # Spring Boot 后端
│   ├── pom.xml
│   └── src/main/java/com/gameplay/
│       ├── GameCompanionApplication.java   # 启动类
│       ├── config/       # SecurityConfig、MybatisPlusConfig
│       ├── common/       # api/exception/enums（统一响应、错误码、状态机）
│       ├── auth/ user/ catalog/ companion/ order/ wallet/
│       ├── review/ favorite/ announcement/ notification/ file/ audit/
│       ├── customer_service/ ai/ admin/
│       └── infrastructure/  # websocket、schedule、storage、security
│   └── src/test/java/com/gameplay/         # 各模块集成测试
└── frontend/            # Vue 3 前端
    └── src/
        ├── main.ts / App.vue
        ├── router/      # 五段式路由分区 + 守卫
        ├── stores/      # Pinia user store
        ├── api/         # axios 封装 + 各模块接口
        ├── components/  # WorkbenchLayout、PlaceholderView
        ├── utils/       # ws.ts（WebSocket）、progress.ts（进度条）
        ├── views/       # 按 user/companion/cs/support/admin 分目录
        └── styles/      # global.css
```

---

## 4. 后端核心机制

### 4.1 启动与配置

**[GameCompanionApplication.java](file:///workspace/backend/src/main/java/com/gameplay/GameCompanionApplication.java)**
- `@SpringBootApplication` + `@EnableScheduling`（启用定时任务）+ `@MapperScan("com.gameplay.**.mapper")` 扫描全部 Mapper。

**[SecurityConfig.java](file:///workspace/backend/src/main/java/com/gameplay/config/SecurityConfig.java)**
- 无状态会话（`STATELESS`），关闭 CSRF，开启 CORS（开发期全放开）。
- 放行规则：注册/登录、`/api/games/**`、`/api/service-types/**`、`/api/tags/**`、`/api/companion-services/**`、`/api/reviews/companion/**`（GET）、`/api/files/**`（GET）、`/api/announcements/**`（GET）、`/ws/cs`、OPTIONS；其余需认证。
- 方法级 RBAC：`@EnableMethodSecurity` + `@PreAuthorize("hasRole('ADMIN')")` 等。
- `PasswordEncoder` = BCrypt。

**[MybatisPlusConfig.java](file:///workspace/backend/src/main/java/com/gameplay/config/MybatisPlusConfig.java)**
- 注册 MyBatis-Plus 分页插件（MySQL）。

**[application.yml](file:///workspace/backend/src/main/resources/application.yml)**
- `spring.profiles.active: local`（敏感配置在已 gitignore 的 `application-local.yml`）。
- 全局逻辑删除字段 `deleted`（0 否 / 1 是）。
- JWT 配置 `jwt.secret/expire-seconds`；AI 配置 `ai.model.enabled`；文件存储 `file.storage.dir/max-size-mb/allowed-extensions`。

### 4.2 统一响应与异常

**[ApiResponse.java](file:///workspace/backend/src/main/java/com/gameplay/common/api/ApiResponse.java)**
- 统一响应体 `{code, message, data}`；成功 `code=SUCCESS`。静态工厂 `ok(data)` / `ok()` / `error(code, message)`。

**[ErrorCode.java](file:///workspace/backend/src/main/java/com/gameplay/common/exception/ErrorCode.java)**
- 全局错误码枚举，每个错误码含 `httpStatus / code / message`。覆盖认证、订单、钱包、客服、AI、目录、陪玩师、评价投诉、文件、通知公告、收藏等全部业务域。

**[GlobalExceptionHandler.java](file:///workspace/backend/src/main/java/com/gameplay/common/exception/GlobalExceptionHandler.java)**
- `@RestControllerAdvice`，统一处理：`BusinessException`、DTO 校验失败、参数校验、JSON 解析失败、方法级权限不足（AccessDeniedException）、未知异常兜底（不泄露堆栈）。

### 4.3 JWT 认证与令牌版本机制

**链路**：`SecurityConfig` 把 `JwtAuthFilter` 挂在 `UsernamePasswordAuthenticationFilter` 之前。

**[JwtTokenService.java](file:///workspace/backend/src/main/java/com/gameplay/auth/security/JwtTokenService.java)**
- 签发（`createToken`）与解析（`parse`）JWT，HMAC-SHA256，密钥来自 `jwt.secret`。
- 令牌声明：`uid`（账号ID）、`username`、`roles`（角色编码列表）、`tv`（令牌版本）、`iat`、`exp`（默认 24h）。

**[JwtAuthFilter.java](file:///workspace/backend/src/main/java/com/gameplay/auth/security/JwtAuthFilter.java)**
- 从 `Authorization: Bearer <JWT>` 取令牌 → `jwtTokenService.parse` → `accountAuthService.assertEnabledAndTokenVersion`（数据库复核账号状态 + 令牌版本）→ 构建 `ROLE_xxx` 授权写入 SecurityContext。

**[AccountAuthService.java](file:///workspace/backend/src/main/java/com/gameplay/auth/service/AccountAuthService.java)**
- REST 过滤器与 WebSocket 握手共用：校验账号存在、`ENABLED`、`tokenVersion` 一致。令牌版本机制实现"禁用/改密/注销后旧令牌立即失效"。
- `findByAccount`：用户名/手机号/邮箱三合一登录。

**[AuthService.java](file:///workspace/backend/src/main/java/com/gameplay/auth/service/AuthService.java)**
- `register`：唯一性校验（用户名/手机号/邮箱，空值存 NULL 避免唯一索引冲突）+ BCrypt 哈希 + 绑定默认 USER 角色。
- `login`：账号+密码 → 签发 JWT，更新 `last_login_at`。账号不存在与密码错误返回同一错误码防枚举。
- `changePassword`：校验原密码 → 递增 `token_version` 使旧令牌全部失效 → 发布 `PasswordChangedEvent`（客服账号据此清除"强制改密"标志）→ 返回新令牌。

### 4.4 订单状态机（核心）

**[OrderStatus.java](file:///workspace/backend/src/main/java/com/gameplay/common/enums/OrderStatus.java)**

```
PENDING_PAYMENT ─支付→ WAITING_ACCEPTANCE ─接单→ WAITING_SERVICE ─开始→ IN_SERVICE
   │                    │                      │                     │
   └─超时关闭→CLOSED     └─超时/拒绝→CLOSED       └─取消→CLOSED       └─结束→ WAITING_CONFIRMATION
                                                                          │
                                  COMPLETED ←用户确认/24h自动确认──────────┤
                                     │                                     │
                                     └─投诉→ AFTER_SALES ─仲裁→ COMPLETED/CLOSED
```

**[OrderService.java](file:///workspace/backend/src/main/java/com/gameplay/order/service/OrderService.java)** —— 订单唯一状态迁移入口，Controller/客服/AI 不得直接改 `order_status`：
- `createOrder`：校验服务审核通过+上架、禁止自约、陪玩师可接、时长与时间合法性、档期覆盖与冲突（临时档期 `TEMPORARY`）→ 按分钟比例计费 → 落库。
- `pay`：`selectByIdForUpdate` 行锁 → 检查支付超时 → `walletService.pay` 扣款 → 状态转 `WAITING_ACCEPTANCE`，档期 `TEMPORARY→EFFECTIVE`。
- `accept` / `reject`：接单（校验接单超时）或拒绝（退款 + 释放档期 + 关闭）。
- `startService` / `endService`：允许提前 15 分钟开始；结束进入 `WAITING_CONFIRMATION`。
- `confirmCompleted`：用户确认 → `walletService.settle` 结算陪玩师收益 → 累计陪玩师已完成订单数。
- `cancel`：按状态判断是否退款（已支付则全额退款 + 释放档期）。
- `enterAfterSales` / `resolveAfterSales`：投诉入售后、管理员仲裁（维持/部分退款→`COMPLETED`，全额退款→`CLOSED`）。
- `closeExpiredPendingPayment` / `closeExpiredWaitingAcceptance` / `autoConfirmCompleted`：供定时任务调用。
- `transition`：内部状态机校验 + `updateStatusIfCurrent`（乐观条件更新防并发）+ 写 `order_status_history`。

相关实体：`PlayOrder`（含 `version` 乐观锁、`*_snapshot` 快照字段）、`OrderTimeSlot`（档期占用）、`OrderStatusHistory`（状态轨迹）。

### 4.5 钱包与资金幂等（核心）

**[WalletService.java](file:///workspace/backend/src/main/java/com/gameplay/wallet/service/WalletService.java)**
- 所有资金变动以 **业务流水号**（`uk_wl_business_no`）幂等；余额用**乐观锁条件更新**（`decreaseBalanceWithVersion` 等），禁止直接改 `wallet_account`。
- `pay`：扣用户余额（`PAY:订单号`）、`refund`：退用户（`REFUND:订单号`）、`settle`：陪玩师收益入账（可用余额+累计收益，`SETTLE:订单号`）。
- `refundAmount`：投诉部分退款（`REFUND:订单号:PARTIAL`）；`deductSettledIncome`：全额退款时从陪玩师扣回已结算收益（`SETTLE_BACK:订单号`，余额不足按可用扣）。
- `ensureWalletForUpdate`：行锁读取/创建钱包，`DuplicateKeyException` 兜底并发创建。

---

## 5. 后端业务模块职责与关键类

> 各模块目录：`backend/src/main/java/com/gameplay/<module>/`。

### 5.1 auth（认证授权）
| 关键类 | 职责 |
|---|---|
| `AuthController` | `/api/auth/register`、`/login`、`/logout`、`/change-password`、`/me` |
| `AuthService` / `AccountAuthService` | 注册登录/令牌签发、账号状态与令牌版本复核（见 4.3） |
| `JwtTokenService` / `JwtAuthFilter` / `JwtPrincipal` | JWT 签发解析、请求过滤、`UserDetails` 载体 |
| `User` / `Role` / `UserRole` | 用户、角色、用户角色关联实体（RBAC） |
| `PasswordChangedEvent` | 改密事件（客服模块监听，清除强制改密标志） |

### 5.2 user（个人资料）
- `AccountController`（`/api/accounts`）：`GET /profile`、`PUT /profile`、`POST /logout-all`。
- `AccountService.updateProfile` 更新昵称/头像/性别/简介等；`Gender` 枚举（0 未知/1 男/2 女）。

### 5.3 catalog（目录基础数据）
- `CatalogController`（公开）：`/api/games`、`/api/games/{id}`、`/api/service-types`、`/api/tags?gameId=`（指定游戏时含 `game_id=0` 通用标签）。
- `AdminCatalogController`（`/api/admin/games|service-types|tags` CRUD，`@PreAuthorize ADMIN`）。
- 实体：`Game`、`ServiceType`、`Tag`（`tag_category`：POSITION/STYLE/HERO/OTHER）、`TagCategory`。

### 5.4 companion（陪玩师业务）
| 服务 | 职责 / 关键方法 |
|---|---|
| `ApplicationService` | 入驻申请：提交（去重待审核申请）、管理员审核通过→创建 `CompanionProfile` 并授予 `COMPANION` 角色 |
| `CompanionProfileService` | 主页：展示名/简介/能力/评分/接单状态 |
| `CompanionServiceService` | 服务项目：发布、改价、提交审核（审核通过才可上架） |
| `AvailabilityService` | 档期管理：手动/周期来源，被订单占用不可删 |
| `EarningsService` | 收益查询（复用 wallet 流水） |

关键枚举：
- `ApplicationAuditStatus`：PENDING / APPROVED / REJECTED（入驻申请）
- `ServiceAuditStatus`：PENDING / APPROVED / REJECTED（服务审核）
- `ShelfStatus`：ON_SHELF / OFF_SHELF（上下架）
- `AvailabilityStatus`：AVAILABLE / UNAVAILABLE；`CompanionServiceStatus`：AVAILABLE / BUSY / RESTING / SUSPENDED（接单状态）

Controller 分组：`CompanionApplicationController`、`CompanionProfileController`、`CompanionServiceController`、`CompanionAvailabilityController`、`CompanionOrderController`（陪玩师接单履约）、`EarningsController`、`PublicCompanionServiceController`（游客浏览可预约服务）。

### 5.5 order（订单）
见 4.4。Controller：`PlayOrderController`（`/api/play-orders`：创建/支付/我的/详情/取消/确认）、`CompanionOrderController`（`/api/companion/orders`：接单/拒绝/开始/结束）。

### 5.6 wallet（钱包）
见 4.5。`WalletController`（`/api/wallet/me`、`/me/ledgers`）；实体 `WalletAccount`（balance/frozen/total_income + version）、`WalletLedger`（business_no 幂等 + 前后快照）；枚举 `LedgerType`（PAYMENT/REFUND/SETTLEMENT/ADJUSTMENT）、`LedgerDirection`（IN/OUT）。

### 5.7 review（评价与投诉仲裁）
- `ReviewService`：订单完成后评价（每订单一次）、评价展示 `display_status` VISIBLE/HIDDEN，管理员可隐藏/删除；评价联动陪玩师 `rating_avg / rating_count`。
- `ComplaintService`：投诉创建（校验订单状态）→ PENDING → PROCESSING → RESOLVED；管理员 `ComplaintHandleRequest` 处理（KEEP / FULL_REFUND / PARTIAL_REFUND，联动 `OrderService.resolveAfterSales` + `WalletService` 退款/扣回）；证据 `ComplaintEvidence`（走 file 模块上传）。
- Controller：`ReviewController`、`ComplaintController`、`AdminReviewController`、`AdminComplaintController`。

### 5.8 favorite / announcement / notification / file / audit（轻量模块）
- `FavoriteService`：收藏/取消收藏陪玩师（唯一索引防重）。
- `AnnouncementService`：公告 DRAFT / PUBLISHED / REVOKED 上下架，公开 GET 仅已发布。
- `NotificationService`：站内通知创建、未读数、标记已读（`NotificationController` `/api/notifications`）。
- 文件存储：接口 `FileStorageService` + 实现 `LocalFileStorageService`（本地磁盘 `./uploads`，扩展名白名单 `jpg,jpeg,png,gif,webp`，单文件 ≤5MB，按 `category/date/fileName` 三级目录存储，URL 含 UUID 防枚举）；`FileController` `/api/files/upload`（登录）、`/api/files/{category}/{date}/{fileName}`（GET 公开）。
- `OperationLogService`：管理员操作审计（操作者/目标/变更前后脱敏 JSON/IP），`AdminOperationLogController` 查询。

### 5.9 admin（后台管理）
- `AdminUserService` + `AdminUserController`：用户列表、禁用/启用（禁用同步使令牌失效）。
- `AdminStatsService` + `AdminStatsMapper`（自定义统计 SQL）+ `AdminStatsController`：`/api/admin/stats/overview`（用户/订单/陪玩师/收益等概览）。
- `CompanionApplicationAdminController`：入驻审核（通过→授角色）。
- `CompanionServiceAdminController`：服务审核。
- `AdminAiKnowledgeController`：知识库管理（见 5.11）。
- `AdminAnnouncementController` / `AdminReviewController` / `AdminComplaintController` / `AdminOperationLogController` / `AdminCsAccountController`：公告、评价、投诉、日志、客服账号管理。

### 5.10 customer_service（人工客服）
| 关键类 | 职责 |
|---|---|
| `CustomerConversationService` | 会话生命周期：创建、AI 应答、转人工、领取、回复、关闭、转管理员、评价、队列（见 4.6 状态机） |
| `CustomerMessageService` | 消息落库（`client_msg_id` 幂等）、内部备注、游标补拉 |
| `CsAccountService` | 客服账号 CRUD、工作状态、容量校验（`max_active_conversations`） |
| `CsMessageNotifier`（接口） | 消息/会话变更推送抽象（WebSocket 实现见 6.2） |
| `CustomerServiceController` | 用户侧会话入口（`/api/customer-service` 会话创建/消息/AI） |
| `CsWorkbenchController` | 客服工作台（`@PreAuthorize CUSTOMER_SERVICE`） |
| `AdminCsAccountController` | 管理员创建/重置密码/禁用客服账号 |

枚举：`CsAccountStatus`（ENABLED/DISABLED）、`CsWorkStatus`（ONLINE/BUSY/OFFLINE）、`ReceptionMode`（AI/HUMAN/ADMIN）、`SenderType`（USER/AI/CS/SYSTEM/ADMIN）、`AssignmentType`（AI_TRANSFER/CLAIM/ASSIGN/TRANSFER/ESCALATE_ADMIN）、`ConversationSourceType`（HELP_CENTER/PROFILE/ORDER）。

### 5.11 ai（AI 客服，可插拔）
| 关键类 | 职责 |
|---|---|
| `AiKnowledgeBaseService` | 知识库 CRUD + `match(content)` 关键词检索并计算置信度（返回 `MatchResult`） |
| `AiResponseFacade` | 应答门面：知识库优先 → 可选模型增强 → 统一转人工决策 + 写 `ai_call_log`；任何异常降级不阻塞会话 |
| `TransferDecisionService` | 转人工决策：用户主动要求 / 命中敏感业务词（退款、投诉、封禁等） / 置信度 <0.70 / 连续未解决 ≥2 |
| `AiResponder`（接口） | 可插拔策略接口：`respond(AiRequest)` + `provider()` |
| `KnowledgeBaseResponder` | 默认实现（离线可用），回答加"【AI客服】"前缀，置信度 ≥0.70 视为可靠 |
| `ModelEnhancedResponder` | 模型增强实现（按 `ai.model.enabled` 条件启用，演示默认关闭） |

决策枚举 `AiDecision`：CONTINUE_AI / TRANSFER_HUMAN / FALLBACK_FAQ。
处理链（`AiResponseFacade.process`）：知识库应答 → 置信度不足时尝试模型增强 → 无可靠回答给固定引导 → `TransferDecisionService` 决策 → 写日志。

### 5.12 infrastructure（基础设施）
- **websocket**：见 6.2。
- **schedule**：`OrderTimeoutScheduler`（见 6.3）。
- security/storage/ai/config/mapper：空占位（`.gitkeep`），预留扩展边界。

---

## 6. 关键流程与时序

### 6.1 客服会话状态机
**[ConversationStatus.java](file:///workspace/backend/src/main/java/com/gameplay/common/enums/ConversationStatus.java)**

```
AI_PROCESSING ─转人工/用户要求→ WAITING_HUMAN ─客服领取→ HUMAN_PROCESSING ─转管理员→ ESCALATED_ADMIN
      │                                 │                     │                     │
      └──── 关闭 ───────────────────────┴─────────────────────┴─────────────────────┴→ CLOSED
```

会话全流程（`CustomerConversationService`）：
1. 用户 `create` → 状态 `AI_PROCESSING`（接待模式 AI）。
2. 用户发消息（WebSocket `MESSAGE_SEND` 或 REST）→ `aiRespond`：AI 处理中则走 AI 应答链路；人工接待中则只保存并推送。
3. AI 决策 `TRANSFER_HUMAN` → `transferToHuman`：状态 → `WAITING_HUMAN`，写 `conversation_assignment` 轨迹 + 系统消息。
4. 客服看 `queue` → `claim`（乐观锁 version + 容量校验）→ `HUMAN_PROCESSING`。
5. 客服 `reply` / 加 `internal_note`（用户不可见）/ `escalate`（转管理员 `ESCALATED_ADMIN`）。
6. `close`：写 `closed_category/result`，用户可对已关闭会话 `evaluate` 满意度（每会话一次）。

### 6.2 WebSocket 客服实时通信
- **配置**：[WebSocketConfig.java](file:///workspace/backend/src/main/java/com/gameplay/infrastructure/websocket/WebSocketConfig.java) 注册 `/ws/cs`。
- **握手鉴权**：[JwtHandshakeInterceptor.java](file:///workspace/backend/src/main/java/com/gameplay/infrastructure/websocket/JwtHandshakeInterceptor.java) 从 URL query `token` 取 JWT，复用 `JwtTokenService` + `AccountAuthService` 校验，结果写入 session attributes（Handler 不信任客户端自报身份）。
- **处理器**：[CustomerServiceWsHandler.java](file:///workspace/backend/src/main/java/com/gameplay/infrastructure/websocket/CustomerServiceWsHandler.java) 收到 `MESSAGE_SEND` 帧：客服身份 → `conversationService.reply`；用户身份 → `conversationService.aiRespond`；异常回 `ERROR` 帧。
- **会话注册表**：[WsSessionRegistry.java](file:///workspace/backend/src/main/java/com/gameplay/infrastructure/websocket/WsSessionRegistry.java) 按 userId 维护多端连接，推送失败静默移除。
- **推送**：[CsWsNotifier.java](file:///workspace/backend/src/main/java/com/gameplay/infrastructure/websocket/CsWsNotifier.java) 推送 `MESSAGE_NEW` / `CONVERSATION_CHANGED` / `AI_RESPONSE` 给会话双方（按 userId 路由，客服账号先映射回内部 userId）；离线方由 REST 补拉兜底。
- **前端**：`frontend/src/utils/ws.ts` 封装 `CsSocket`（单例、断线 3s 自动重连、`onMessage` 订阅分发）。

### 6.3 订单超时定时任务
**[OrderTimeoutScheduler.java](file:///workspace/backend/src/main/java/com/gameplay/infrastructure/schedule/OrderTimeoutScheduler.java)**（`@Scheduled`，每分钟，批 200）
1. 支付超时：`PENDING_PAYMENT` 且 `pay_expire_at <= now` → `closeExpiredPendingPayment`（关闭 + 释放档期）。
2. 接单超时：`WAITING_ACCEPTANCE` 且 `accept_expire_at <= now` → `closeExpiredWaitingAcceptance`（退款 + 关闭）。
3. 自动确认：`WAITING_CONFIRMATION` 结束满 24h → `autoConfirmCompleted`（结算 + 完成）。
处理时再次行锁确认当前状态，防止与用户操作并发误关闭/重复结算。

---

## 7. 模块依赖关系

**后端模块依赖（核心）**
```
auth ──→ common, user
companion ──→ auth(身份/角色), catalog(游戏/服务类型/标签), order(订单联动), wallet(收益)
order ──→ companion(服务/资料/档期), wallet(支付/退款/结算), auth(用户)
wallet ──→ order(订单快照), auth(用户)
review ──→ order(售后状态迁移), wallet(退款/扣回), file(证据上传), notification(结果通知), companion(评分联动)
admin ──→ companion(入驻/服务审核), auth(用户禁用), ai(知识库), review(评价/投诉), audit(日志), notification
customer_service ──→ ai(应答链路), order(订单摘要), auth(身份), infrastructure/websocket(推送)
ai ──→ customer_service 无反向耦合（由 CS 模块调用 facade）
infrastructure/websocket ──→ auth(握手鉴权), customer_service(会话服务)
infrastructure/schedule ──→ order
file ──→ 无业务依赖（被 review/companion 等调用）
```
**数据流**：`companion` 与 `order` 通过快照字段解耦；`wallet` 是资金唯一出入口；`review` 仲裁时回调 `OrderService.resolveAfterSales` 驱动订单状态机。

**Maven 依赖**（`pom.xml`）：spring-boot-starter-{web, security, websocket, validation}、mybatis-plus-spring-boot3-starter、mybatis-plus-jsqlparser（分页）、mysql-connector-j、jjwt（api/impl/jackson）、lombok、spring-boot-starter-test、spring-security-test。

**前端依赖**：vue、vue-router、pinia、element-plus、axios、vite、typescript、vue-tsc 等（`frontend/package.json`）。

---

## 8. 数据库设计（30 张表）

库 `game_companion_app`，统一约定：金额 `BIGINT` 分（100=1.00 元）、状态用 VARCHAR 枚举名、逻辑删除 `deleted`、乐观锁 `version`。

| 分组 | 表 |
|---|---|
| 账号与权限 | `user`、`role`、`user_role` |
| 陪玩师 | `companion_application`、`companion_profile`、`companion_service`、`companion_availability` |
| 目录 | `game`、`service_type`、`tag` |
| 订单与档期 | `play_order`、`order_time_slot`、`order_status_history` |
| 资金 | `wallet_account`、`wallet_ledger` |
| 评价投诉 | `review`、`complaint`、`complaint_evidence` |
| 收藏/通知/公告 | `favorite`、`notification`、`announcement` |
| 客服 | `customer_service_account`、`customer_conversation`、`customer_service_message`、`conversation_assignment`、`internal_note`、`service_evaluation` |
| AI | `ai_knowledge_base`、`ai_call_log` |
| 审计 | `operation_log` |

关键索引/约束：
- 唯一键：`user.username/mobile/email`、`wallet_ledger.business_no`（幂等）、`play_order.order_no`、`order_time_slot.order_id`、`customer_service_message.client_msg_id`（消息幂等）、`review.order_id`、`service_evaluation.conversation_id`、`favorite(user_id,companion_user_id)` 等。
- 时间类超时索引：`play_order(order_status, pay_expire_at, accept_expire_at, confirmed_at)`、`order_time_slot(slot_status, expire_at)`。
- 种子数据（`sql/data.sql`）：4 个角色、预置管理员 `admin/Admin@123456`、3 款游戏、3 个服务类型、26 个标签（幂等 INSERT IGNORE）。

---

## 9. 前端结构

### 9.1 工程配置
- `vite.config.ts`：端口 5173，`@` 别名指向 `src`，开发代理 `/api → http://localhost:8080`、`/ws/cs → ws://localhost:8080`。
- 构建：`vue-tsc && vite build`；npm 使用淘宝镜像（`.npmrc`）。

### 9.2 路由分区与权限（`router/index.ts`）
| 分区 | 前缀 | 角色 | 页面 |
|---|---|---|---|
| 用户端 | `/` | 游客/登录 | Home、Games、Companions、Orders、Profile、Support |
| 陪玩师端 | `/companion` | COMPANION | Application、Services、Schedule、Orders(履约)、Earnings |
| 客服端 | `/cs` | CUSTOMER_SERVICE | Queue、Conversation |
| 管理后台 | `/admin` | ADMIN | Dashboard、Audit、Users、Reviews、Complaints、Announcements、AiKnowledge、CsAccounts、OperationLogs |
| 登录/404 | `/login`、`/*` | - | LoginView、NotFoundView |

路由守卫：校验登录态（无 token → /login 带 redirect）、刷新后恢复用户信息（`refreshUser`）、角色校验。**前端守卫仅为体验优化，真实权限由后端 Spring Security 与数据范围校验兜底。**

### 9.3 状态管理与 API 层
- **Pinia** `stores/user.ts`：`token`（localStorage 持久化）、`user`、`roles`；`login/refreshUser/logout/hasRole`。
- **axios** `api/http.ts`：`baseURL:'/api'`，请求拦截注入 `Authorization: Bearer <token>`；响应拦截统一处理 `code!=='SUCCESS'` 业务错误与 401（清 token 跳登录）；`request<T>()` 泛型方法直接返回 `data`；`PageResult<T>` 对应 MyBatis-Plus 分页结构。
- **API 模块文件**：`auth.ts / catalog.ts / companion.ts / order.ts / review.ts / cs.ts / admin.ts / file.ts`。
- **WebSocket** `utils/ws.ts`：`CsSocket` 单例，上行 `MESSAGE_SEND`（含 `clientMsgId` 幂等），下行 `MESSAGE_NEW / CONVERSATION_CHANGED / AI_RESPONSE / ERROR`，断线自动重连。

### 9.4 视图职责速查
| 端 | 视图 | 职责 |
|---|---|---|
| 用户 | HomeView | 首页：平台简介、推荐游戏/陪玩师 |
| 用户 | GamesView / CompanionsView | 游戏列表 / 陪玩师浏览筛选（游戏、价格、评分） |
| 用户 | OrdersView | 我的订单列表与状态、取消 |
| 用户 | ProfileView | 个人资料、钱包余额、修改 |
| 用户 | SupportView | 在线客服入口（发起会话/消息） |
| 陪玩师 | ApplicationView | 提交入驻申请 |
| 陪玩师 | ServiceManageView / ScheduleView | 服务项目配置 / 档期管理 |
| 陪玩师 | OrderFulfillView | 接单/拒绝/开始/结束履约 |
| 陪玩师 | EarningsView | 收益明细 |
| 客服 | QueueView / ConversationView | 会话队列 / 会话详情（收发消息、转交、关闭） |
| 管理 | DashboardView | 数据概览统计 |
| 管理 | AuditView / UsersView / ReviewsManageView / ComplaintsManageView | 审核、用户、评价、投诉管理 |
| 管理 | AiKnowledgeManageView / AnnouncementsManageView / CsAccountsManageView / OperationLogsView | AI 知识库、公告、客服账号、操作日志 |
| 通用 | WorkbenchLayout | 各端统一工作台布局（菜单+内容区） |

---

## 10. API 概览（主要分组）

| 分组 | 前缀 | 说明 |
|---|---|---|
| 认证 | `/api/auth` | register/login/logout/change-password/me |
| 账户 | `/api/accounts` | profile 读写、logout-all |
| 目录 | `/api/games` `/api/service-types` `/api/tags` | 公开浏览 |
| 目录管理 | `/api/admin/games|service-types|tags` | ADMIN CRUD |
| 陪玩师 | `/api/companion/...` | 申请、主页、服务、档期、收益、订单履约 |
| 公开服务 | `/api/companion-services/**` | 游客可浏览可预约服务 |
| 订单 | `/api/play-orders` | 创建/支付/我的/详情/取消/确认 |
| 钱包 | `/api/wallet` | me、ledgers |
| 评价投诉 | `/api/reviews` `/api/complaints` + `/api/admin/reviews|complaints` | 评价、投诉与后台处理 |
| 客服 | `/api/customer-service` | 用户会话 + 客服工作台（queue/claim/reply/notes/escalate/close） |
| 客服管理 | `/api/admin/cs-accounts` | 客服账号管理 |
| AI | `/api/admin/ai-knowledge` | 知识库管理 |
| 收藏 | `/api/favorites` | 收藏/取消 |
| 公告 | `/api/announcements`（公开 GET） + `/api/admin/announcements` | 查看/管理 |
| 通知 | `/api/notifications` | 列表/未读数/标记已读 |
| 文件 | `/api/files` | upload（登录）/ 读取（公开 GET） |
| 审计 | `/api/admin/operation-logs` | 操作日志查询 |
| 统计 | `/api/admin/stats/overview` | 数据概览 |
| 用户管理 | `/api/admin/users` | 用户列表/禁用 |
| WebSocket | `/ws/cs?token=` | 客服实时会话 |

统一响应：`{"code":"SUCCESS","message":"操作成功","data":{}}`。

---

## 11. 运行方式

### 11.1 环境要求
- JDK 17、Maven 3.x、Node.js（含 npm）、MySQL 8.0（本机）。

### 11.2 初始化数据库
```bash
mysql -uroot -p < sql/schema.sql
mysql -uroot -p < sql/data.sql
```

### 11.3 启动后端（:8080）
```bash
cd backend
# 创建 application-local.yml（仓库中无该文件，需按 application.yml 的配置项手工创建）：
#   1) spring.datasource.password 填本机 MySQL 密码
#   2) jwt.secret 填随机密钥（≥256 bit，可用 openssl rand -base64 48 生成）
#   3) 若启用 AI 模型增强可设 ai.model.enabled=true
# 该文件已被 gitignore，勿提交。
mvn spring-boot:run
```

### 11.4 启动前端（:5173）
```bash
cd frontend && npm install && npm run dev
```

### 11.5 运行测试
```bash
cd backend && mvn test
```
集成测试（`AuthFlowTest`、`OrderModuleTest`、`CustomerServiceFlowTest` 等 8 个模块测试）依赖本机 MySQL 与 `application-local.yml`，测试数据在事务中回滚。

### 11.6 演示账号
| 账号 | 密码 | 角色 |
|---|---|---|
| `admin` | `Admin@123456` | 管理员 |

> 演示陪玩师流程：注册普通用户 → 陪玩师端提交入驻申请 → 管理员后台审核通过（自动授予 COMPANION 角色）→ 重新登录后管理服务、档期并接单。

### 11.7 关键配置说明（application.yml / application-local.yml）
- `spring.datasource.*`：MySQL 连接（默认 `game_companion_app`）。
- `jwt.secret`：≥256 bit 随机密钥（生产必须更换）。
- `ai.model.enabled`：是否启用模型增强应答（演示默认 false，仅知识库）。
- `file.storage.dir / max-size-mb / allowed-extensions`：本地文件存储。

---

## 12. 开发约定与注意事项

1. **状态机唯一入口**：订单状态只能通过 `OrderService.transition` 迁移；客服会话状态只能通过 `CustomerConversationService` 迁移。禁止直接 `update` 状态字段。
2. **资金安全**：一切余额变动走 `WalletService`（业务号幂等 + 乐观锁）；金额以分存储。
3. **令牌失效**：禁用账号 / 修改密码 / 注销后递增 `token_version`，旧 JWT 立即失效（REST 与 WebSocket 共用 `AccountAuthService`）。
4. **数据范围**：除 RBAC 角色外，涉及他人数据必须二次校验（如订单归属、会话发起人/当前客服/管理员）。
5. **逻辑删除**：统一 `deleted` 字段 + `@TableLogic`；MyBatis-Plus 自动过滤。
6. **快照字段**：订单保存服务标题/单价/陪玩师昵称等快照，与源数据解耦。
7. **AI 边界**：AI 只能解释规则，不得执行退款/改单/封禁等写操作；命中敏感词或低置信度即转人工（FR-C20）。
8. **敏感配置**：`application-local.yml`、JWT 密钥等已 gitignore，勿提交。
