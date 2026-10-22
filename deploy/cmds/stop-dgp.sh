#!/bin/bash
# 停止 DGP 治理平台（保留数据、不影响服务器上的其它服务）
echo "=== 停止前状态 ==="
systemctl is-active datagov || true
ss -lntp | grep -E ':(8092|8093) ' || true
free -m | sed -n 2p
echo ""

echo "=== 1) 停止并禁用 datagov 服务（保留 /opt/datagov 与数据库） ==="
systemctl stop datagov
systemctl disable datagov 2>&1 | tail -1
sleep 1
echo "  datagov: $(systemctl is-active datagov 2>&1) / enabled: $(systemctl is-enabled datagov 2>&1)"
echo ""

echo "=== 2) 停用 nginx 上的 DGP 站点（8093），其余站点不动 ==="
if [ -f /etc/nginx/conf.d/dgp.conf ]; then
  mv /etc/nginx/conf.d/dgp.conf /etc/nginx/conf.d/dgp.conf.disabled
  nginx -t 2>&1 | tail -1
  systemctl reload nginx
  echo "  已把 dgp.conf 改为 dgp.conf.disabled 并 reload nginx"
else
  echo "  未找到 dgp.conf（可能已停用）"
fi
sleep 1
echo ""

echo "=== 停止后状态 ==="
echo "8092/8093 端口："
ss -lntp | grep -E ':(8092|8093) ' || echo "  已释放（无监听）"
echo "nginx 仍监听（其它站点）："
ss -lntp | grep nginx | awk '{print "  " $4}' | sort -u
echo "其它服务："
for s in auction attendance qingzhang report datagov nginx mysql; do
  printf "  %-12s %s\n" "$s" "$(systemctl is-active $s 2>/dev/null || echo -)"
done
echo ""
echo "=== 资源对比 ==="
free -m | sed -n 1,2p
ps -o pid,rss,%cpu,comm -C java 2>/dev/null || true
echo ""
echo "=== 数据保留确认 ==="
du -sh /opt/datagov /var/lib/mysql/dgp 2>/dev/null
mysql -N -e "SELECT CONCAT('dgp 库表数=', COUNT(*)) FROM information_schema.tables WHERE table_schema='dgp'" 2>/dev/null
