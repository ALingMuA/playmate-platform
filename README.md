<div align="center">

# 🎮 游戏陪玩系统 (Playmate Platform)

<p align="center">
  <b>全流程闭环 · 模块化架构 · 智能客服支持 · 毕业设计工程化示范项目</b>
</p>

<p align="center">
  <a href="https://github.com/ALingMuA/playmate-platform">
    <img src="https://img.shields.io/badge/GitHub-playmate--platform-181717?style=for-the-badge&logo=github&logoColor=white" alt="GitHub Repository" />
  </a>
  <a href="https://github.com/ALingMuA/playmate-platform/stargazers">
    <img src="https://img.shields.io/github/stars/ALingMuA/playmate-platform?style=for-the-badge&logo=apachespark&color=f59e0b" alt="GitHub Stars" />
  </a>
  <a href="https://github.com/ALingMuA/playmate-platform/network/members">
    <img src="https://img.shields.io/github/forks/ALingMuA/playmate-platform?style=for-the-badge&color=3b82f6" alt="GitHub Forks" />
  </a>
  <a href="https://github.com/ALingMuA/playmate-platform/blob/main/LICENSE">
    <img src="https://img.shields.io/badge/License-MIT-green.svg?style=for-the-badge" alt="MIT License" />
  </a>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Java-17-ED8B00?style=flat-square&logo=openjdk&logoColor=white" alt="Java 17" />
  <img src="https://img.shields.io/badge/Spring%20Boot-3.5-6DB33F?style=flat-square&logo=springboot&logoColor=white" alt="Spring Boot 3.5" />
  <img src="https://img.shields.io/badge/Spring%20Security-JWT-6DB33F?style=flat-square&logo=springsecurity&logoColor=white" alt="Spring Security" />
  <img src="https://img.shields.io/badge/MyBatis--Plus-3.5-blue?style=flat-square" alt="MyBatis-Plus" />
  <img src="https://img.shields.io/badge/MySQL-8.0-4479A1?style=flat-square&logo=mysql&logoColor=white" alt="MySQL 8.0" />
  <img src="https://img.shields.io/badge/Vue-3.x-4FC08D?style=flat-square&logo=vuedotjs&logoColor=white" alt="Vue 3" />
  <img src="https://img.shields.io/badge/TypeScript-5.x-3178C6?style=flat-square&logo=typescript&logoColor=white" alt="TypeScript" />
  <img src="https://img.shields.io/badge/Vite-6.x-646CFF?style=flat-square&logo=vite&logoColor=white" alt="Vite" />
  <img src="https://img.shields.io/badge/Element%20Plus-2.x-409EFF?style=flat-square&logo=elementplus&logoColor=white" alt="Element Plus" />
  <img src="https://img.shields.io/badge/Pinia-Store-FFE57F?style=flat-square&logo=pinia&logoColor=black" alt="Pinia" />
</p>

<p align="center">
  <a href="#-快速启动">🚀 快速启动</a> •
  <a href="#-技术栈">🛠 技术栈</a> •
  <a href="#-核心特性">✨ 核心特性</a> •
  <a href="#-陪玩师入驻与管理链路">🔄 业务链路</a> •
  <a href="#-目录结构">📁 目录结构</a> •
  <a href="#-初始账号演示环境">🔑 演示账号</a> •
  <a href="#-后端接口已实现">🔌 接口说明</a>
</p>

</div>

---

基于《需求分析说明书》《概要设计说明书》《详细设计说明书》严格设计与实现的本科毕业设计项目（前后端分离 + 模块化单体架构）。涵盖前台用户找陪玩/下单、陪玩师端接单履约与档期管理、客服工作台与 AI 知识库分流、管理后台综合运维仲裁四大核心业务场景。

## ✨ 核心特性

- 🎮 **全流程业务闭环**：普通用户 → 资质申请 → 后台审核授权 → 陪玩主页维护 → 服务上架审核 → 档期预约接单 → 履约评价投诉全闭环。
- 🤖 **智能客服与实时协作**：接入大模型智能客服知识库，支持自动答复、关键词兜底、意图识别转人工，结合 WebSocket（`/ws/cs`）实现低延迟客服会话分流。
- 🛡 **严密安全与状态机防护**：Spring Security + JWT 鉴权，支持令牌版本机制（修改密码/封禁即时失效）；订单状态机单向跃迁保障资金安全；金额以分计数值精确存储。
- 📐 **工程化与规范化落地**：模块化单体分包，统一响应格式与错误码体系；前端 Vue 3 + TypeScript 严格类型约束，按路由分区独立解耦。

---

## 🛠 技术栈

| 分层 | 技术 / 框架 | 版本与说明 |
|---|---|---|
| **前端应用** | Vue 3、TypeScript、Vite 6 | 响应式组件化架构、严格类型约束与极致构建速度 |
| **状态与路由** | Pinia、Vue Router | 全局用户态与权限守卫、分区路由管理 |
| **UI 组件库** | Element Plus、Tailwind-friendly CSS | 统一现代化设计令牌（Design Tokens）与响应式布局 |
| **后端架构** | Java 17、Spring Boot 3.5 | 模块化单体分包、RESTful API 规范设计 |
| **安全认证** | Spring Security、JWT (JSON Web Token) | 无状态身份认证、多角色 RBAC 鉴权与令牌版本控制 |
| **实时通信** | Spring WebSocket | 客服长连接队列、在线状态心跳与双向即时通讯 |
| **持久层 & 数据** | MyBatis-Plus、MySQL 8.0 | 高效 ORM、逻辑删除、自增流水、30 张核心业务表 |
| **项目构建** | Maven、npm、JUnit 5 | 自动化依赖管理与单元/集成测试保障 |

---

## 📁 目录结构

```text
├── 需求分析说明书.md            需求分析（FR 编号与业务用例）
├── 概要设计说明书.md            总体设计（架构、模块、路由分区、接口分组）
├── 详细设计说明书.md            物理 DDL、接口契约、时序图、错误码定义
├── sql/
│   ├── schema.sql              30 张表完整 DDL（含建库语句）
│   └── data.sql                系统种子数据：角色定义、初始管理员、基础游戏与标签（幂等）
├── backend/                    Spring Boot 后端（模块化单体工程）
│   └── src/main/java/com/gameplay/
│       ├── common              统一响应封装、错误码、状态机模型、全局异常处理
│       ├── auth                用户注册、登录、JWT 签发与校验、令牌版本、密码管理   ✅
│       ├── user                个人资料维护、头像上传、账号安全                    ✅
│       ├── catalog             游戏类目、服务分类、技能标签（三级目录）             ✅
│       ├── companion           陪玩师入驻申请、主页维护、服务项管理、档期与收益结算   ✅
│       ├── order               订单创建、模拟支付、履约交付、自动确认与超时取消      ✅
│       ├── wallet              用户虚拟钱包、收支流水记录                          ✅
│       ├── review              双向订单评价、服务投诉与平台仲裁                    ✅
│       ├── file                本地安全文件存储与访问                             ✅
│       ├── customer_service    客服排班账号、会话分配、消息流转、会话队列           ✅
│       ├── ai                  智能知识库检索、大模型应答、意图分析转人工           ✅
│       ├── announcement        平台全员公告广播                                   ✅
│       ├── notification        站内系统通知与消息提醒                             ✅
│       ├── favorite            陪玩师关注与收藏夹                                 ✅
│       ├── audit               操作审计追踪日志                                   ✅
│       ├── admin               综合管理后台（资质审核、用户管控、平台数据看板）      ✅
│       └── infrastructure/websocket 客服实时通讯端点（/ws/cs）                     ✅
└── frontend/                   Vue 3 现代化前端工程
    └── src/
        ├── router              用户端 / 陪玩师端(/companion) / 客服(/cs) / 管理后台(/admin)
        ├── stores              Pinia 状态仓库（登录凭证、角色权限、全局配置）
        ├── api                 Axios 统一拦截封装与业务接口定义
        ├── components          工作台公共布局（WorkbenchLayout）与公用组件
        └── views               各分区业务页面（用户端全站 + 陪玩师五页 + 客服 + 后台）
```

---

## 🔄 陪玩师入驻与管理链路

普通用户申请成为陪玩师的完整业务链路（对应《需求分析说明书》FR-P01 ~ P09、FR-M06、FR-M07）：

| 步骤 | 页面 / 路由 | 核心业务说明 |
|:---:|---|---|
| **0. 维护服务目录** | `/admin/catalog` | 游戏、服务类型、标签由管理员统一维护（FR-M10~M12）：新增/编辑/排序/启用停用/删除。**陪玩师只能选择，不能自由定义**；被服务或资质引用的类目项禁止直接删除（返回 409 `CATALOG_IN_USE`），应标记停用 |
| **1. 提交入驻申请** | 用户端 `/become-companion` | 五态自适应（未申请 / 审核中 / 已通过 / 已驳回 / 已是陪玩师）；用户菜单、个人中心、找陪玩页均有常驻入口 |
| **2. 管理员审核** | `/admin/audit` → 入驻申请 Tab | 审核通过后自动赋予 `COMPANION` 角色并初始化陪玩主页；**通过与驳回均强制要求填写审核意见**（后端 `reason` 字段无条件必填） |
| **3. 维护陪玩主页** | `/companion/profile` | 维护展示昵称、个人简介、游戏认证资质（FR-P05）与接单服务状态（FR-P06） |
| **4. 上架服务项目** | `/companion/services` | 新增/编辑具体服务项目后进入待审状态，过审后方可上架；标签仅展示**所选游戏关联标签 + 平台通用标签** |
| **5. 服务项目审核** | `/admin/audit` → 服务项目 Tab | 管理员审核通过后陪玩师可自行切换上架状态，上架项目对前台用户公开可见（FR-M07） |
| **6. 档期与履约交付** | `/companion/schedule`、`/companion/orders`、`/companion/earnings` | 灵活配置可约时间段、接单响应、完成履约确认、查看收益明细（FR-P10~P19） |

> [!NOTE]
> - `/companion` 分区强制要求具备 `COMPANION` 角色权限，普通用户访问将触发 `meta.roleFallback` 自动兜底重定向至 `/become-companion` 引导入驻页。
> - **类目停用生效范围**：游戏类目停用后其下属已上架服务对前台用户自动隐藏且禁止发起新订单；服务类型或标签停用后不再出现在新建选择列表中，已有历史服务保留名称并打上「已停用」标签。

---

## 🚀 快速启动

### 1. 初始化数据库（MySQL 8.0）

连接本地 MySQL 并依次导入数据库表结构与预置种子数据：

```bash
mysql -uroot -p < sql/schema.sql
mysql -uroot -p < sql/data.sql
```

### 2. 启动后端工程（端口 :8080）

```bash
cd backend
# 复制示例配置文件 application-local.example.yml 为 application-local.yml
# 填入本机 MySQL 密码与随机 JWT 密钥（例如 openssl rand -base64 48），该文件已配置 gitignore 防止泄漏
mvn spring-boot:run
```

**演示环境无配置启动（支持环境变量传参）：**

```powershell
$env:SPRING_PROFILES_ACTIVE="demo"
$env:DB_PASSWORD="本机数据库密码"
$env:JWT_SECRET="至少 32 字节的安全随机密钥字符串"
$env:APP_CORS_ALLOWED_ORIGINS="http://localhost:5173"
mvn spring-boot:run
```

### 3. 启动前端工程（端口 :5173）

```bash
cd frontend
npm install
npm run dev
```

### 4. 运行质量与集成测试

```bash
cd backend
mvn test
```

> [!TIP]
> - 后端集成测试（如 `AuthFlowTest`）依托本地真实 MySQL 数据库运行，测试数据在事务完成后全部自动回滚，不污染生产/演示数据。
> - 前端已配置完整 TypeScript 类型检查，部署前可执行 `npm run build` 进行生产静态校验与代码分包打包。

---

## 🔑 初始账号（演示环境）

| 账号 | 初始密码 | 角色身份 | 场景权限与说明 |
|---|---|---|---|
| `admin` | `Admin@123456` | **系统管理员** | 平台全功能权限，类目维护、资质入驻审核、服务审核、用户管控与仲裁 |

> [!TIP]
> **演示全流程体验推荐**：
> 1. 前台注册一个新用户（如 `player01`）；
> 2. 点击右上角或个人中心「成为陪玩师」，填写技能资料并提交入驻；
> 3. 使用 `admin` 账号登录后台 `/admin/audit`，审核通过申请；
> 4. `player01` 刷新即可进入「陪玩师端」发布游戏服务、管理日程与履约接单。

---

## 🔌 后端接口

系统完整涵盖客服会话与实时消息（`/api/customer-service`）、大模型知识库（`/api/admin/ai`）、评价管理（`/api/reviews`）、投诉仲裁（`/api/complaints`）、文件存储（`/api/files`）、公告中心（`/api/announcements`）、系统通知（`/api/notifications`）及后台运营管理端。

所有接口权限均由 Spring Security 注解 `@PreAuthorize` 严格鉴权。

### 认证与账号模块 `/api/auth`

| HTTP 方法 | 请求路径 | 接口说明 |
|:---:|---|---|
| `POST` | `/api/auth/register` | 用户注册（用户名 + 手机号/邮箱 + BCrypt 加密密码） |
| `POST` | `/api/auth/login` | 统一登录验证（支持用户名/手机号/邮箱多方式识别）→ 颁发 JWT |
| `POST` | `/api/auth/logout` | 退出登录（客户端清除 Token 凭证） |
| `POST` | `/api/auth/change-password` | 修改登录密码（递增令牌版本，旧 Token 立即全部失效） |
| `GET` | `/api/auth/me` | 获取当前会话登录用户信息与角色列表 |

统一响应格式：
```json
{
  "code": "SUCCESS",
  "message": "操作成功",
  "data": {}
}
```

### 目录与类目公开数据 `/api/games` 等

游客免登录即可查阅的基础字典与目录接口：

| HTTP 方法 | 请求路径 | 接口说明 |
|:---:|---|---|
| `GET` | `/api/games` | 获取所有已启用的游戏分类列表（FR-U01） |
| `GET` | `/api/games/{id}` | 获取指定游戏详情及规格介绍 |
| `GET` | `/api/service-types` | 获取平台已启用的服务类型（如排位上分、语音连麦等） |
| `GET` | `/api/tags?gameId=` | 查询可用标签列表（指定游戏时自动附带平台通用标签） |

---

## 📌 开发与架构约定

1. **密码安全**：敏感密码仅以加盐 BCrypt 散列入库；本地差异化配置文件 `application-local.yml` 严格隔离并列入 `.gitignore`。
2. **鉴权机制**：JWT Payload 携带 `uid`（账号 ID）、`roles`（角色权限集）、`tv`（令牌版本号）。账号发生封禁、修改密码或注销操作时，数据库递增 `token_version`，已发行的历史令牌即刻失效。
3. **资金与计量**：所有涉及钱包与订单的金额字段均采用 `BIGINT` 存储**分值**（100 代表 1.00 元整），避免浮点计算精度损失。
4. **软删除规范**：核心业务数据统一采用 `deleted` 标志位逻辑删除（结合 MyBatis-Plus `@TableLogic` 自动过滤）。
5. **接口与长连接**：统一 REST 接口前缀为 `/api`；客服实时长连接端点为 `/ws/cs`。

---

<div align="center">

**⭐ 欢迎给本项目点个 Star 支持一下！**

[GitHub 仓库地址: ALingMuA/playmate-platform](https://github.com/ALingMuA/playmate-platform)

</div>
