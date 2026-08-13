# 游戏陪玩系统

> 毕设项目。设计文档见仓库根目录:《需求分析说明书》《概要设计说明书》《详细设计说明书》。

前后端分离 + 模块化单体后端：

| 层 | 技术 |
|---|---|
| 后端 | Java 17、Spring Boot 3.5、Spring Security(JWT)、MyBatis-Plus、MySQL 8.0 |
| 前端（规划中） | Vue 3、TypeScript、Vite、Pinia |
| 其他 | WebSocket(`/ws/cs` 客服会话)、Ollama/外部大模型（可选，未配置时降级知识库） |

## 目录结构

```text
backend/
├── pom.xml
└── src/main
    ├── java/com/gameplay
    │   ├── GameplayApplication.java   # 启动入口
    │   ├── common/        # 统一响应、错误码、全局异常
    │   ├── config/        # Security、MyBatis-Plus 配置
    │   ├── auth/          # 注册、登录、JWT、令牌版本、修改密码  ← 已完成
    │   └── (user / companion / catalog / order / review /
    │       customer_service / ai / admin / file / infrastructure)  # 规划中
    └── resources
        ├── application.yml                # 通用配置（可提交）
        ├── application-local.yml          # 本机密码与 JWT 密钥（gitignore）
        ├── application-local.example.yml  # 本机配置模板
        └── db/
            ├── schema.sql                 # 30 张表 DDL（来自详细设计 1.1）
            └── data.sql                   # 角色 + 预置管理员（幂等）
```

## 快速开始

1. 初始化数据库（本机 MySQL，root）：

   ```bash
   mysql -uroot -p < backend/src/main/resources/db/schema.sql
   mysql -uroot -p < backend/src/main/resources/db/data.sql
   ```

2. 配置本机密钥：复制 `application-local.example.yml` 为 `application-local.yml`，填写数据库密码与随机 JWT 密钥（`openssl rand -base64 48`）。

3. 启动：

   ```bash
   cd backend && mvn spring-boot:run
   ```

   默认端口 8080，Swagger/接口文档后续补充。

## 初始账号（演示环境，生产勿用）

| 账号 | 密码 | 角色 |
|---|---|---|
| `admin` | `Admin@123456` | 管理员（由 data.sql 预置） |

## 已实现接口（auth 模块）

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/auth/register` | 注册（用户名 + 手机号/邮箱 + 密码） |
| POST | `/api/auth/login` | 登录（用户名/手机号/邮箱 + 密码）→ JWT |
| POST | `/api/auth/logout` | 退出（无状态，客户端丢弃令牌） |
| POST | `/api/auth/change-password` | 修改密码（旧令牌全部失效，返回新令牌） |
| GET | `/api/auth/me` | 当前用户信息与角色 |

统一响应：`{"code":"SUCCESS","message":"操作成功","data":{}}`；错误码见《详细设计说明书》6.1 与 `common.exception.ErrorCode`。

## 开发注意

- 密码仅存 BCrypt 哈希；`.env`、`application-local.yml` 等敏感文件已 gitignore，勿提交。
- JWT 声明：`uid`（账号ID）、`roles`、`tv`（令牌版本）；禁用账号/改密/注销后递增 `token_version`，旧令牌立即失效。
- 金额以 `BIGINT` 存分值（100 = 1.00 元）。
- 逻辑删除统一使用 `deleted` 字段（MyBatis-Plus `@TableLogic`）。
