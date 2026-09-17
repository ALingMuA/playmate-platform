#!/usr/bin/env bash
# ============================================================
# 游戏陪玩系统 —— ECS 部署脚本（在服务器上以 root 执行）
# 由 deploy.ps1 上传到 /tmp/gameplay-deploy 后调用，幂等可重跑
# ============================================================
set -euo pipefail

APP_DIR=/opt/gamemate
STAGE=/tmp/gameplay-deploy
DB_NAME=game_companion_app
DB_USER=gameplay
SERVICE=gameplay
JAR=gameplay-backend.jar
STAMP=$(date +%Y%m%d-%H%M%S)
BACKUP_DIR=/opt/backup-$STAMP

log() { printf '\n[%s] %s\n' "$(date +%H:%M:%S)" "$*"; }
die() { printf '\n[失败] %s\n' "$*" >&2; exit 1; }

[ "$(id -u)" -eq 0 ] || die "请以 root 运行"

log "0/7 校验上传产物"
for f in $JAR frontend.tar.gz gameplay.env schema.sql data.sql gameplay.service application-prod.yml nginx-gamemate.conf; do
  [ -s "$STAGE/$f" ] || die "缺少上传文件: $STAGE/$f"
done
ls -lh "$STAGE"

log "1/7 解包新产物到暂存位置"
rm -rf "$APP_DIR/frontend.new"
mkdir -p "$APP_DIR/frontend.new"
tar -xzf "$STAGE/frontend.tar.gz" -C "$APP_DIR/frontend.new"
[ -f "$APP_DIR/frontend.new/index.html" ] || die "前端包内缺少 index.html"
cp "$STAGE/$JAR" "$APP_DIR/$JAR.new"
echo "前端文件数: $(find "$APP_DIR/frontend.new" -type f | wc -l)"

log "2/7 停止旧服务"
systemctl stop "$SERVICE" 2>/dev/null || true
pkill -f 'gamemate-server.jar' 2>/dev/null || true
pkill -f "$JAR" 2>/dev/null || true
sleep 2
for i in $(seq 1 20); do
  if ! ss -lnt | grep -q ':8080 '; then break; fi
  sleep 1
done
if ss -lnt | grep -q ':8080 '; then die "8080 端口仍被占用"; fi
echo "8080 端口已释放"

log "3/7 备份现有部署 -> $BACKUP_DIR"
mkdir -p "$BACKUP_DIR"
for f in gamemate-server.jar application-prod.yml gamemate.pid app.log spring.log; do
  if [ -e "$APP_DIR/$f" ]; then mv "$APP_DIR/$f" "$BACKUP_DIR/"; fi
done
for d in frontend dist frontend-old; do
  if [ -d "$APP_DIR/$d" ]; then mv "$APP_DIR/$d" "$BACKUP_DIR/"; fi
done
ls -la "$BACKUP_DIR"

log "4/7 切换到新版本"
mv "$APP_DIR/frontend.new" "$APP_DIR/frontend"
mv "$APP_DIR/$JAR.new" "$APP_DIR/$JAR"
install -m 600 "$STAGE/gameplay.env" "$APP_DIR/gameplay.env"
install -m 644 "$STAGE/application-prod.yml" "$APP_DIR/application-prod.yml"
mkdir -p "$APP_DIR/uploads"
chown -R root:root "$APP_DIR/frontend" "$APP_DIR/$JAR" "$APP_DIR/uploads"
# tar 解包可能带出 777 权限，Web 根目录必须收紧
find "$APP_DIR/frontend" -type d -exec chmod 755 {} +
find "$APP_DIR/frontend" -type f -exec chmod 644 {} +

log "5/7 检查数据库 $DB_NAME"
set -a; . "$STAGE/gameplay.env"; set +a
# schema.sql 使用 CREATE TABLE（非 IF NOT EXISTS），重复执行会报错并因 set -e 中断部署，
# 因此仅在库不存在时初始化，已有库保持不变（结构变更需另写迁移脚本）。
if mysql -uroot -N -e "select 1 from information_schema.tables where table_schema='$DB_NAME' and table_name='user';" | grep -q 1; then
  echo "  数据库已存在，跳过 schema/种子数据导入（保留线上数据）"
else
  echo "  首次部署，执行 schema.sql 与 data.sql"
  mysql -uroot < "$STAGE/schema.sql"
  mysql -uroot < "$STAGE/data.sql"
fi
mysql -uroot -e "CREATE USER IF NOT EXISTS '$DB_USER'@'localhost' IDENTIFIED BY '$DB_PASSWORD';"
mysql -uroot -e "ALTER USER '$DB_USER'@'localhost' IDENTIFIED BY '$DB_PASSWORD';"
mysql -uroot -e "GRANT ALL PRIVILEGES ON $DB_NAME.* TO '$DB_USER'@'localhost'; FLUSH PRIVILEGES;"
TABLES=$(mysql -uroot -N -e "select count(*) from information_schema.tables where table_schema='$DB_NAME';")
USERS=$(mysql -uroot -N -e "select count(*) from $DB_NAME.user;")
echo "  $DB_NAME 表数量: $TABLES, 初始用户数: $USERS"

log "6/7 安装 systemd 服务与 nginx 站点"
install -m 644 "$STAGE/gameplay.service" "/etc/systemd/system/$SERVICE.service"
systemctl daemon-reload
systemctl enable "$SERVICE" >/dev/null 2>&1

cp -a /etc/nginx/sites-enabled/gamemate "$BACKUP_DIR/nginx-gamemate.conf.bak"
install -m 644 "$STAGE/nginx-gamemate.conf" /etc/nginx/sites-enabled/gamemate
if ! nginx -t 2>/dev/null; then
  cp -a "$BACKUP_DIR/nginx-gamemate.conf.bak" /etc/nginx/sites-enabled/gamemate
  die "nginx 配置校验失败，已回滚原配置"
fi
systemctl reload nginx
echo "nginx 配置校验通过并已重载"

log "7/7 启动服务并健康检查"
systemctl start "$SERVICE"
READY=0
for i in $(seq 1 60); do
  if curl -sf -o /dev/null "http://127.0.0.1:8080/api/announcements"; then READY=1; echo "  后端就绪（第 $i 次探测）"; break; fi
  sleep 2
done
if [ "$READY" -ne 1 ]; then
  journalctl -u "$SERVICE" -n 40 --no-pager
  die "后端启动超时"
fi

echo
echo "=== 健康检查 ==="
printf '  首页        : '; curl -s -o /dev/null -w 'HTTP %{http_code}\n' http://127.0.0.1/
printf '  后端直连    : '; curl -s -o /dev/null -w 'HTTP %{http_code}\n' http://127.0.0.1:8080/api/announcements
printf '  登录(admin) : '; curl -s -o /dev/null -w 'HTTP %{http_code}\n' -X POST http://127.0.0.1/api/auth/login -H 'Content-Type: application/json' -d '{"account":"admin","password":"Admin@123456"}'
TOKEN=$(curl -s -X POST http://127.0.0.1:8080/api/auth/login -H 'Content-Type: application/json' -d '{"account":"admin","password":"Admin@123456"}' | sed -n 's/.*"token":"\([^"]*\)".*/\1/p')
printf '  受保护接口  : '; curl -s -o /dev/null -w 'HTTP %{http_code}\n' -H "Authorization: Bearer $TOKEN" http://127.0.0.1:8080/api/admin/stats/overview
printf '  WebSocket   : '; curl -s -o /dev/null --max-time 3 -w 'HTTP %{http_code}\n' -H 'Connection: Upgrade' -H 'Upgrade: websocket' -H 'Sec-WebSocket-Version: 13' -H 'Sec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==' "http://127.0.0.1/ws/cs?token=$TOKEN"
# 只保留最近 5 份备份，避免磁盘堆积（每份含 jar 与前端，约 40M）
KEEP=5
OLD_BACKUPS=$(ls -1dt /opt/backup-* 2>/dev/null | tail -n +$((KEEP + 1)) || true)
if [ -n "$OLD_BACKUPS" ]; then
  echo "$OLD_BACKUPS" | while read -r d; do echo "  清理旧备份: $d"; rm -rf "$d"; done
fi

echo
echo "备份目录: $BACKUP_DIR"
echo "查看日志: journalctl -u $SERVICE -f"
