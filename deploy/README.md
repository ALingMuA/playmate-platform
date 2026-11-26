# 部署说明（阿里云 ECS）

访问地址示例：`http://<YOUR_ECS_IP>/`
域名示例：`https://example.com/`（如在境内服务器通过域名对外提供 80/443 访问，需先完成 ICP 备案）

## 一、服务器环境规划

| 项 | 配置说明 |
|---|---|
| 实例 ID | `<YOUR_ECS_INSTANCE_ID>`（如 `cn-hangzhou`，Ubuntu 22.04，1.6G 内存） |
| 公网 IP | `<YOUR_ECS_IP>` |
| 应用目录 | `/opt/gamemate` |
| 后端服务 | `systemd` 单元 `gameplay.service`，端口 8080，堆上限 512M |
| 数据库 | MySQL 库 `game_companion_app`，账号 `gameplay`（仅本机可连） |
| 前端 | nginx 站点 `/etc/nginx/sites-enabled/gamemate`，静态根目录 `/opt/gamemate/frontend` |
| 密钥文件 | `/opt/gamemate/gameplay.env`（权限 600，不入库） |

nginx 职责：`/` 静态前端（SPA 回退 `index.html`）、`/api/` → 8080、`/ws/cs` → 8080（WebSocket，需转发 `Upgrade` 头）。

## 二、域名绑定与 HTTPS（示例：example.com）

- 站点配置模板：`/etc/nginx/sites-available/gamemate-domain.conf`（模板见 `deploy/nginx-gamemate-domain.conf`）
- 启用域名访问前，需在 `deploy/gameplay.env` 的 `APP_CORS_ALLOWED_ORIGINS` 中加入对应域名（如 `https://example.com,https://www.example.com`）
- 模板保留了 `/.well-known/acme-challenge/` 以支持 Let's Encrypt (`certbot`) webroot 方式签发与自动续期

> [!NOTE]
> 若域名未完成 ICP 备案，境内云服务器 80/443 端口的公网域名请求会被云厂商拦截（HTTP 403 / TLS 连接重置），同时会导致 `certbot` 公网校验失败；使用 `<YOUR_ECS_IP>` 直接访问或完成备案后域名访问均不受影响。

## 三、日常运维

```bash
systemctl status gameplay        # 查看状态
systemctl restart gameplay       # 重启后端
journalctl -u gameplay -f        # 实时日志
tail -f /opt/gamemate/app.log    # 应用日志
```

登录入口 `http://<YOUR_ECS_IP>/login`，使用 `sql/data.sql` 种子账号登录并在首次登录后立即改密。

## 四、一键部署

### 1. 配置密钥（首次）

```powershell
Copy-Item deploy\gameplay.env.example deploy\gameplay.env
# 编辑 deploy/gameplay.env，填入数据库密码、JWT 密钥、模型 API Key 与 CORS 来源
```

`deploy/gameplay.env` 已在 `.gitignore` 中，不会入库。

### 2. 执行部署

通过环境变量或命令行参数传入目标服务器与私钥路径：

```powershell
$env:DEPLOY_SERVER = "<YOUR_ECS_IP>"
$env:DEPLOY_SSH_KEY = "<YOUR_SSH_KEY_PATH>"
pwsh -File deploy\deploy.ps1              # 构建 + 上传 + 远端部署
pwsh -File deploy\deploy.ps1 -SkipBuild   # 用现有产物直接部署
```

也可以直接通过参数显式指定：

```powershell
pwsh -File deploy\deploy.ps1 -Server <YOUR_ECS_IP> -SshKey <YOUR_SSH_KEY_PATH>
```

远端脚本 `remote-deploy.sh` 依次执行：校验产物 → 解包 → 停旧服务 → 备份到 `/opt/backup-<时间戳>` → 切换版本 → 建库导数据/执行增量迁移 → 安装 `systemd` 与 `nginx` → 启动并健康检查。

**回滚**：把 `/opt/backup-<时间戳>/` 中的 `frontend` 与旧 jar 拷回 `/opt/gamemate`，再 `systemctl restart gameplay`。

## 五、连接服务器

### 方式一：SSH（推荐，部署脚本走这条）

将本机公钥写入服务器 `/root/.ssh/authorized_keys` 后执行：

```powershell
ssh -i <YOUR_SSH_KEY_PATH> root@<YOUR_ECS_IP>
```

### 方式二：阿里云 Workbench / 云助手

```powershell
workbench connect -i <YOUR_ECS_INSTANCE_ID> --region cn-hangzhou
```

> **注意**：Workbench 对会话数有服务端配额，短时间大量调用可能触发 `Forbidden.SessionLimit`，批量运维操作建议优先走 SSH。
>
> 若 SSH 通道失效，可用 `deploy/ecs-runcommand.ps1` 经云助手执行命令（不消耗 Workbench 会话）：
>
> ```powershell
> $env:ECS_INSTANCE_ID = "<YOUR_ECS_INSTANCE_ID>"
> pwsh -File deploy\ecs-runcommand.ps1 -Action DescribeCloudAssistantStatus
> pwsh -File deploy\ecs-runcommand.ps1 -Action RunCommand -Script "systemctl status gameplay"
> ```

## 六、目录说明

| 文件 | 作用 |
|---|---|
| `deploy.ps1` | 本地一键部署编排（构建 → 打包 → 上传 → 远端执行） |
| `remote-deploy.sh` | 服务器端部署脚本（备份、建库、切版本、健康检查） |
| `gameplay.service` | `systemd` 单元模板 |
| `nginx-gamemate.conf` | IP 站点 nginx 配置模板（含 `/api/` 与 `/ws/cs` 转发） |
| `nginx-gamemate-domain.conf` | 域名站点 nginx 配置模板（`example.com` / `www.example.com`） |
| `application-prod.yml` | 生产 Profile 覆盖项 |
| `gameplay.env.example` | 环境变量模板（真实文件 `gameplay.env` 不入库） |
| `ecs-runcommand.ps1` | 直连阿里云 ECS OpenAPI，经云助手在实例上执行命令 |

## 七、部署注意事项

1. 本系统后端包名为 `com.gameplay`（Spring Boot 3 + MyBatis-Plus），数据库名为 `game_companion_app`，不依赖 Redis，运行仅依赖 MySQL 与 Nginx。
2. 生产首次初始化执行 `schema.sql` + `data.sql`，增量发布通过 `schema_version` 表自动跳过已执行脚本及演示账号迁移，严禁在生产导入 `data-demo.sql`。
3. nginx 站点配置包含 `/ws/cs` 的 `Upgrade` 代理转发（缺失会导致客服实时消息在生产环境不可用），并对 `index.html` 禁缓存、保留旧版本 `/assets/` chunk 防止动态路由 `ChunkLoadError`。
4. 1.6G 小内存实例无 swap，`gameplay.service` 已限制 `-Xms128m -Xmx512m -XX:MaxMetaspaceSize=192m`；若后续再叠加服务，建议先配置 swap。
