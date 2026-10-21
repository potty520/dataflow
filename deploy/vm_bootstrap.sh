#!/usr/bin/env bash
# =============================================================================
# DGP 数据治理平台 · 测试机一键准备（Ubuntu 22.04 / 24.04）
# 适用：4C8G 及以上、全新虚拟机
#
# 作用：安装 JDK17 / MySQL8 / nginx / 压测工具，创建库与账号，
#       可选导入仓库 sql/ 下的结构与初始化数据，准备 /opt/datagov 目录。
#       jar 由本机（Windows）用 deploy/remote.py 推送，或手工上传。
#
# 用法（在虚拟机上，sudo 或 root）：
#   sudo bash deploy/vm_bootstrap.sh
#   sudo DGP_DB_PASSWORD='自定义口令' bash deploy/vm_bootstrap.sh   # 指定库口令
#   sudo DGP_SKIP_SQL=1 bash deploy/vm_bootstrap.sh                # 不导入初始化数据
# =============================================================================
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
DB_NAME="${DGP_DB_NAME:-dgp}"
DB_USER="${DGP_DB_USER:-dgp}"
NGINX_PORT="${DGP_NGINX_PORT:-8093}"

echo "=== 1/6 安装系统依赖 ==="
export DEBIAN_FRONTEND=noninteractive
apt-get update -qq
apt-get install -y -qq openjdk-17-jdk-headless mysql-server nginx unzip curl wrk apache2-utils
java -version 2>&1 | head -1
mysql --version
nginx -v 2>&1

echo "=== 2/6 启动并加固 MySQL（仅监听本机） ==="
systemctl enable --now mysql
# 4C8G 测试机建议参数（可按需调整 /etc/mysql/mysql.conf.d/）
cat > /etc/mysql/mysql.conf.d/zz-dgp.cnf <<'EOF'
# DGP 测试机参数（4C8G）
innodb_buffer_pool_size = 1G
max_connections = 300
innodb_flush_log_at_trx_commit = 2
slow_query_log = 1
long_query_time = 1
EOF
systemctl restart mysql
sleep 2

echo "=== 3/6 创建数据库与账号 ==="
if [ -z "${DGP_DB_PASSWORD:-}" ]; then
  DGP_DB_PASSWORD="$(head -c 48 /dev/urandom | base64 | tr -dc 'A-Za-z0-9' | head -c 24)"
  GENERATED=1
else
  GENERATED=0
fi
mysql -e "CREATE DATABASE IF NOT EXISTS \`${DB_NAME}\` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;"
mysql -e "CREATE USER IF NOT EXISTS '${DB_USER}'@'127.0.0.1' IDENTIFIED BY '${DGP_DB_PASSWORD}';"
mysql -e "ALTER USER '${DB_USER}'@'127.0.0.1' IDENTIFIED BY '${DGP_DB_PASSWORD}';"
mysql -e "GRANT ALL PRIVILEGES ON \`${DB_NAME}\`.* TO '${DB_USER}'@'127.0.0.1'; FLUSH PRIVILEGES;"
echo "  库：${DB_NAME}   账号：${DB_USER}@127.0.0.1"
if [ "$GENERATED" = "1" ]; then echo "  ** 自动生成口令：${DGP_DB_PASSWORD} （请记录）**"; fi

echo "=== 4/6 导入结构与初始化数据（可选） ==="
if [ "${DGP_SKIP_SQL:-0}" != "1" ] && [ -f "${ROOT}/sql/schema-mysql.sql" ]; then
  mysql --default-character-set=utf8mb4 "${DB_NAME}" < "${ROOT}/sql/schema-mysql.sql"
  mysql --default-character-set=utf8mb4 "${DB_NAME}" < "${ROOT}/sql/init-data-mysql.sql"
  echo "  已导入；表数：$(mysql -N "${DB_NAME}" -e "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='${DB_NAME}'")"
else
  echo "  跳过（应用首次启动会自动建表并装载演示数据）"
fi

echo "=== 5/6 准备应用目录 ==="
mkdir -p /opt/datagov/data /opt/datagov/logs /opt/datagov/static
chmod 755 /opt/datagov
ls -ld /opt/datagov

echo "=== 6/6 防火墙（如启用 ufw） ==="
if command -v ufw >/dev/null 2>&1 && ufw status | grep -q "Status: active"; then
  ufw allow "${NGINX_PORT}/tcp" || true
  echo "  已放行 ${NGINX_PORT}/tcp"
else
  echo "  ufw 未启用，跳过"
fi

cat <<EOF

==================== 准备完成 ====================
Java:   $(java -version 2>&1 | head -1)
MySQL:  ${DB_NAME} / ${DB_USER}@127.0.0.1
口令:   ${DGP_DB_PASSWORD}$([ "$GENERATED" = "1" ] && echo "  (自动生成，请保存)")
nginx:  已安装，DGP 站点由 nginx_setup.py 配置（端口 ${NGINX_PORT}）
压测:   wrk / ab 已安装

下一步（在 Windows 开发机上执行）：
  \$env:DGP_SSH_HOST="<虚拟机IP>"; \$env:DGP_SSH_PORT="22"; \$env:DGP_SSH_USER="root"; \$env:DGP_SSH_PASS="***"
  \$env:DGP_DB_NAME="${DB_NAME}"; \$env:DGP_DB_USER="${DB_USER}"; \$env:DGP_DB_PASSWORD="${DGP_DB_PASSWORD}"
  \$env:DGP_JAVA_OPTS="-Xms1g -Xmx2g -XX:+UseG1GC -Dfile.encoding=UTF-8"
  export DGP_DB_POOL=32
  python deploy/remote.py deploy backend/target/dgp-boot.jar 8092
  \$env:DGP_NGINX_PORT="${NGINX_PORT}"; python deploy/nginx_setup.py
  .\\verify.ps1 -Base "http://<虚拟机IP>:${NGINX_PORT}"
  bash deploy/cmds/loadtest.sh          # 压测（在虚拟机上执行）
==================================================
EOF
