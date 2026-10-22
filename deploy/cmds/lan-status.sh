#!/bin/bash
# 检查 DGP 服务与启动日志
echo "=== 服务状态 ==="
systemctl is-active datagov
systemctl status datagov --no-pager -l | head -12
echo "=== 端口 ==="
ss -lntp | grep -E ':(8092|8093) ' || true
echo "=== 错误日志尾部 ==="
tail -40 /opt/datagov/logs/app.err.log 2>/dev/null
echo "=== 应用日志尾部 ==="
tail -30 /opt/datagov/logs/app.log 2>/dev/null
echo "=== 建表数量 ==="
mysql -N dgp -e "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='dgp'" 2>/dev/null || echo "mysql 查询失败"
