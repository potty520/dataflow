#!/bin/bash
# 确认 DGP 已停止且其它服务不受影响
systemctl reset-failed datagov 2>/dev/null
echo "datagov: $(systemctl is-active datagov 2>&1) / $(systemctl is-enabled datagov 2>&1)"
echo "端口 8092/8093: $(ss -lntp | grep -E ':(8092|8093) ' || echo '已释放')"
echo "nginx 配置: $(ls /etc/nginx/conf.d/ | grep dgp | tr '\n' ' ')"
echo ""
echo "其它站点抽查:"
for p in 80 8090 8091; do
  code=$(curl -s -m 3 -o /dev/null -w '%{http_code}' "http://127.0.0.1:$p/" || echo "-")
  echo "  nginx:$p -> $code"
done
code=$(curl -s -m 3 -o /dev/null -w '%{http_code}' "http://127.0.0.1:8093/" 2>/dev/null || true)
echo "  dgp:8093 -> ${code:-已关闭}"
echo ""
echo "资源: $(free -m | sed -n 2p)"
echo "Java 进程:"; ps -o pid,rss,comm -C java | sed 's/^/  /'
echo ""
echo "数据保留: $(du -sh /opt/datagov 2>/dev/null | cut -f1) / MySQL dgp $(du -sh /var/lib/mysql/dgp 2>/dev/null | cut -f1)，表数 $(mysql -N -e "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='dgp'" 2>/dev/null)"
