#!/bin/bash
# 重启 DGP 服务（同时重置进程内登录限流计数），等待健康检查
set -e
systemctl restart datagov
for i in $(seq 1 60); do
  code=$(curl -s -o /dev/null -w '%{http_code}' http://127.0.0.1:8092/api/v1/admin/ping || true)
  if [ "$code" = "200" ]; then echo "healthy after ${i}s"; exit 0; fi
  sleep 1
done
echo "health check timeout"
systemctl status datagov --no-pager -l | tail -20
exit 1
