# 游戏陪玩系统

基于《需求分析说明书》《概要设计说明书》《详细设计说明书》实现的毕设项目。

## 技术栈

| 层 | 技术 |
|---|---|
| 前端 | Vue 3、TypeScript、Vite、Vue Router、Pinia、Element Plus |
| 后端 | Java 17、Spring Boot 3、Spring Security、Spring WebSocket |
| 持久层 | MyBatis-Plus、MySQL 8.0 |
| 构建 | Maven、npm、JUnit 5 |

## 目录结构

```
├── 需求分析说明书.md        需求分析
├── 概要设计说明书.md        总体设计（架构、模块、路由分区）
├── 详细设计说明书.md        物理 DDL、接口、类设计、时序图
├── sql/
│   └── schema.sql          数据库初始化脚本（30 张表，由详细设计说明书生成）
├── backend/                Spring Boot 后端（单体，按业务模块分包）
│   ├── pom.xml
│   └── src/main/java/com/gameplay/
│       ├── common          统一响应、错误码、状态机、异常处理
│       ├── auth / user / companion / catalog / order / review
│       ├── customer_service / ai / admin / file
│       └── infrastructure  安全、WebSocket、任务调度、存储、AI 适配
└── frontend/               Vue 3 前端（按路由分区）
    └── src/
        ├── router          用户端 / /companion / /support / /cs / /admin
        ├── stores          Pinia（user 等）
        ├── api             axios 封装
        └── views           各分区页面
```

## 快速启动

### 1. 初始化数据库

```bash
# 需先在本机 MySQL 中创建 game_companion_app 库（schema.sql 含 CREATE DATABASE）
mysql -uroot -p < sql/schema.sql
```

### 2. 后端（:8080）

```bash
cd backend
# 本地数据库密码写在 backend/src/main/resources/application-local.yml（不入库）
mvn spring-boot:run
```

### 3. 前端（:5173，代理 /api 与 /ws/cs 到后端）

```bash
cd frontend
npm install
npm run dev
```

浏览器访问 http://localhost:5173

## 说明

- 金额使用 BIGINT 分值；状态使用 VARCHAR 枚举名；逻辑删除字段 `deleted`。
- 客服 WebSocket 端点为 `/ws/cs`，仅用于客服会话。
- AI 模型服务不是启动强制依赖，未配置时自动使用知识库 + 人工转接（`app.ai.model.enabled`）。
- 本地敏感配置（数据库密码等）放在 `application-local.yml`，已被 .gitignore 忽略。
