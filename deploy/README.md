# 部署说明（阿里云 ECS）

线上地址：<http://120.27.144.223/>
域名（已绑定但**未备案，公网暂不可访问**）：<https://sknstse.cn/>

## 一、服务器现状

| 项 | 值 |
|---|---|
| 实例 ID | `i-bp1g2jk5s1tq8lsa1r3p`（cn-hangzhou，ecs.e-c1m1.large，Ubuntu 22.04，1.6G 内存） |
| 公网 IP | `120.27.144.223` |
| 应用目录 | `/opt/gamemate` |
| 后端服务 | systemd 单元 `gameplay.service`，端口 8080，堆上限 512M |
| 数据库 | MySQL 库 `game_companion_app`，账号 `gameplay`（仅本机可连） |
| 前端 | nginx 站点 `/etc/nginx/sites-enabled/gamemate`，静态根目录 `/opt/gamemate/frontend` |
| 密钥文件 | `/opt/gamemate/gameplay.env`（权限 600，不入库） |

nginx 职责：`/` 静态前端（SPA 回退 index.html）、`/api/` → 8080、`/ws/cs` → 8080（WebSocket，需 Upgrade 转发）。

## 二、已停止的其他服务（2026-09-16）

本实例原本还跑着另一个独立项目 **skn**。按要求「除本系统外全部停掉」后，当前服务器只服务本系统：

| 服务 | 停止方式 | 恢复方式 |
|---|---|---|
| `skn-api`（node，:3001，PM2 托管） | `pm2 delete skn-api` → `pm2 save` → `pm2 kill` → `systemctl disable --now pm2-root` | `systemctl enable --now pm2-root` 后 `pm2 resurrect` |
| `redis-server`（:6379，已设密码） | `systemctl disable --now redis-server` | `systemctl enable --now redis-server` |

- 两者**开机自启均已关闭**，重启不会复活。本系统不依赖 Redis（pom 与源码 0 引用，java 进程只连 MySQL）。
- `/opt/skn`（572M）文件与 nginx 站点 `sknstse.cn` **均保留未删**，恢复后即可用。
  当前 `https://sknstse.cn/` 仍返回 200（静态前端由 nginx 提供），只有 `/skn-api/` 返回 502。
- **保留未动**：`mysql`、`nginx`（本系统依赖），以及阿里云平台代理（`aliyun` 云助手、
  `cloudmonitor` 云监控、aegis 云盾）——停掉会失去控制台的远程连接与监控能力。

## 三、域名绑定（sknstse.cn）

2026-09-17 已将 `sknstse.cn` / `www.sknstse.cn` 从原 skn 站点改造为**本系统站点**：

- 站点配置：`/etc/nginx/sites-available/gamemate-domain.conf`（模板见 `deploy/nginx-gamemate-domain.conf`）
- 原 skn 站点配置保留在 `/etc/nginx/sites-available/sknstse.cn`，仅从 `sites-enabled` 移除软链
- nginx 配置备份：`/opt/backup-nginx-20260917-123936`
- 已移除 `/skn-api/`、`/skn-uploads/`、`/skn-admin/` 代理与 skn 静态前端（**原 skn 前端服务已停止**）
- 应用 `APP_CORS_ALLOWED_ORIGINS` 已加入两个域名
- 保留了 `/.well-known/acme-challenge/`（证书续期）与微信校验文件

### 公网暂不可访问：该域名未做 ICP 备案

从公网访问会被阿里云拦截，**与本机 nginx 配置无关**：

| 访问方式 | 结果 |
|---|---|
| 公网 `http://sknstse.cn/` | **403**，阿里云备案拦截页（响应头 `Server: Beaver`，标题 `Non-compliance ICP Filing`） |
| 公网 `https://sknstse.cn/` | 连接被重置 |
| 公网 `http://120.27.144.223/` | **200 正常**（IP 访问不受备案限制） |
| 服务器本机带 Host 头访问 | 200 正常（说明站点配置本身完全正确） |

**要对外可用必须先完成 ICP 备案**（阿里云控制台 → 备案，需域名实名认证 + 服务器信息，通常 1–3 周）。
备案通过后本配置**无需任何改动**即可直接对外服务。

### HTTPS 证书 2026-10-12 到期且当前无法自动续期

`certbot renew` 使用 webroot 方式，Let's Encrypt 需通过公网 80 端口回访校验文件，
而该请求被上述备案拦截挡住（返回 403），因此续期必然失败（`certbot renew --dry-run` 已实测失败）。
**根因同样是未备案**，备案完成后会自动恢复。在此之前请勿依赖该域名的 HTTPS。

## 四、日常运维

```bash
systemctl status gameplay        # 查看状态
systemctl restart gameplay       # 重启后端
journalctl -u gameplay -f        # 实时日志
tail -f /opt/gamemate/app.log    # 应用日志
```

登录入口 <http://120.27.144.223/login>，预置管理员 `admin / Admin@123456`（首次登录后请立即修改）。

## 五、重新部署

### 1. 配置密钥（首次）

```powershell
Copy-Item deploy\gameplay.env.example deploy\gameplay.env
# 编辑 deploy/gameplay.env，填入数据库密码、JWT 密钥、模型 API Key
```

`deploy/gameplay.env` 已在 `.gitignore` 中，不会入库。

### 2. 一键部署

```powershell
pwsh -File deploy\deploy.ps1              # 构建 + 上传 + 远端部署
pwsh -File deploy\deploy.ps1 -SkipBuild   # 用现有产物直接部署
```

远端脚本 `remote-deploy.sh` 依次执行：校验产物 → 解包 → 停旧服务 → 备份到 `/opt/backup-<时间戳>` → 切换版本 → 建库导数据 → 安装 systemd 与 nginx → 启动并健康检查。

**回滚**：把 `/opt/backup-<时间戳>/` 中的 `frontend` 与旧 jar 拷回 `/opt/gamemate`，再 `systemctl restart gameplay`。

## 六、连接服务器

### 方式一：SSH（推荐，部署脚本走这条）

本机公钥已写入服务器 `/root/.ssh/authorized_keys`：

```powershell
ssh -i ~\.ssh\gamemate_ecs_ed25519 root@120.27.144.223
```

### 方式二：阿里云 Workbench

```powershell
workbench connect -i i-bp1g2jk5s1tq8lsa1r3p --region cn-hangzhou
```

> **注意**：Workbench 对会话数有服务端配额，短时间大量调用会返回
> `Forbidden.SessionLimit`，且 CLI 无法主动释放（`session list` 显示无会话，
> 但服务端仍计数），只能等其超时回收。**批量操作请走 SSH。**
>
> 若 SSH 通道失效，可用 `deploy/ecs-runcommand.ps1` 经云助手重新下发公钥
> （不消耗 Workbench 会话）：```powershell
> pwsh -File deploy\ecs-runcommand.ps1 -Action DescribeCloudAssistantStatus
> pwsh -File deploy\ecs-runcommand.ps1 -Action RunCommand -Script "systemctl status gameplay"
> ```

## 七、目录说明

| 文件 | 作用 |
|---|---|
| `deploy.ps1` | 本地一键部署编排（构建 → 打包 → 上传 → 远端执行） |
| `remote-deploy.sh` | 服务器端部署脚本（备份、建库、切版本、健康检查） |
| `gameplay.service` | systemd 单元模板 |
| `nginx-gamemate.conf` | IP 站点 nginx 配置（含 `/api/` 与 `/ws/cs` 转发） |
| `nginx-gamemate-domain.conf` | 域名站点 nginx 配置（sknstse.cn / www） |
| `application-prod.yml` | 生产 Profile 覆盖项 |
| `gameplay.env.example` | 环境变量模板（真实文件 `gameplay.env` 不入库） |
| `ecs-runcommand.ps1` | 直连阿里云 ECS OpenAPI，经云助手在实例上执行命令 |

## 八、本次部署的注意事项

1. 服务器上原有的 `/opt/gamemate` 是**另一个更早的项目**（`com.gamemate` 包、JPA、库名 `gamemate`），
   与当前项目（`com.gameplay`、MyBatis-Plus、库名 `game_companion_app`）表结构完全不同，
   因此当前系统使用**全新数据库**，旧库 `gamemate` 原样保留未动。
2. 旧部署文件已备份到 `/opt/backup-20260916-090232`。
3. nginx 站点配置新增了 `/ws/cs` 转发（原配置缺失，会导致客服实时消息在生产环境不可用）。
4. 实例内存仅 1.6G 且无 swap，`gameplay.service` 已限制 `-Xmx512m -XX:MaxMetaspaceSize=192m`。
   若后续再叠加服务，建议先加 swap。
