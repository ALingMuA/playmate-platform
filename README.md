# 游戏陪玩系统

基于《需求分析说明书》《概要设计说明书》《详细设计说明书》实现的毕设项目（前后端分离 + 模块化单体后端）。

## 技术栈

| 层 | 技术 |
|---|---|
| 前端 | Vue 3、TypeScript、Vite、Vue Router、Pinia、Element Plus |
| 后端 | Java 17、Spring Boot 3.5、Spring Security(JWT)、Spring WebSocket |
| 持久层 | MyBatis-Plus、MySQL 8.0 |
| 构建 | Maven、npm、JUnit 5 |

## 目录结构

```text
├── 需求分析说明书.md            需求分析（FR 编号）
├── 概要设计说明书.md            总体设计（架构、模块、路由分区、接口分组）
├── 详细设计说明书.md            物理 DDL、接口、类设计、时序图、错误码
├── sql/
│   ├── schema.sql              30 张表 DDL（含建库语句，来自详细设计 1.1）
│   └── data.sql                种子数据：4 个角色 + 预置管理员（幂等）
├── backend/                    Spring Boot 后端（模块化单体）
│   └── src/main/java/com/gameplay/
│       ├── common              统一响应、错误码、状态机、全局异常处理
│       ├── auth                注册、登录、JWT、令牌版本、修改密码  ✅ 已完成
│       ├── user                个人资料、账号安全            ✅ 已完成
│       ├── catalog             游戏、服务类型、标签           ✅ 已完成
│       └── companion / order / review / customer_service /
│           ai / admin / file / infrastructure               规划中
└── frontend/                   Vue 3 前端（按路由分区）
    └── src/
        ├── router              用户端 / /companion / /support / /cs / /admin
        ├── stores              Pinia（user 等）
        ├── api                 axios 封装
        └── views               各分区页面
```

## 快速启动

### 1. 初始化数据库（本机 MySQL）

```bash
mysql -uroot -p < sql/schema.sql
mysql -uroot -p < sql/data.sql
```

### 2. 后端（:8080）

```bash
cd backend
# 复制 application-local.example.yml 为 application-local.yml，
# 填写本机 MySQL 密码与随机 JWT 密钥（openssl rand -base64 48），该文件已被 gitignore
mvn spring-boot:run
```

### 3. 前端（规划）

```bash
cd frontend && npm install && npm run dev
```

### 4. 测试

```bash
cd backend && mvn test
# 集成测试（AuthFlowTest 等）依赖本机 MySQL 与 application-local.yml；
# 测试数据在事务中回滚，不污染数据库。
```

## 初始账号（演示环境）

| 账号 | 密码 | 角色 |
|---|---|---|
| `admin` | `Admin@123456` | 管理员（sql/data.sql 预置） |

## 后端接口（已实现）

### 认证与账号 `/api/auth`

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/auth/register` | 注册（用户名 + 手机号/邮箱 + 密码） |
| POST | `/api/auth/login` | 登录（用户名/手机号/邮箱）→ JWT |
| POST | `/api/auth/logout` | 退出（无状态，客户端丢弃令牌） |
| POST | `/api/auth/change-password` | 修改密码（旧令牌全部失效，返回新令牌） |
| GET | `/api/auth/me` | 当前用户信息与角色 |

统一响应：`{"code":"SUCCESS","message":"操作成功","data":{}}`；错误码见《详细设计说明书》6.1 与 `common.exception.ErrorCode`。

### 目录基础数据（catalog 模块）

公开接口（游客可访问，SecurityConfig 已放行）：

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/games` | 已启用游戏列表（FR-U01） |
| GET | `/api/games/{id}` | 已启用游戏详情（FR-U01） |
| GET | `/api/service-types` | 已启用服务类型列表（FR-M11 展示） |
| GET | `/api/tags?gameId=` | 标签列表，指定游戏时含通用标签（FR-M12 选择） |

管理接口（仅 ADMIN，`@PreAuthorize` 控制）：

| 方法 | 路径 | 说明 |
|---|---|---|
| GET/POST | `/api/admin/games`、`/api/admin/games/{id}`（PUT/DELETE） | 游戏增删改查（FR-M10） |
| GET/POST | `/api/admin/service-types`、`/api/admin/service-types/{id}`（PUT/DELETE） | 服务类型增删改查（FR-M11） |
| GET/POST | `/api/admin/tags`、`/api/admin/tags/{id}`（PUT/DELETE） | 标签增删改查（FR-M12） |

种子数据：`sql/data.sql` 追加王者荣耀/英雄联盟/和平精英、3 个服务类型与 26 个标签（幂等）。

## 开发约定

- 密码仅存 BCrypt 哈希；`application-local.yml` 等敏感文件已 gitignore，勿提交。
- JWT 声明：`uid`（账号ID）、`roles`、`tv`（令牌版本）；禁用/改密/注销后递增 `token_version`，旧令牌立即失效。
- 金额以 `BIGINT` 存分值（100 = 1.00 元）；逻辑删除统一 `deleted` 字段（`@TableLogic`）。
- 接口前缀 `/api`；WebSocket 端点 `/ws/cs`（客服模块）。
