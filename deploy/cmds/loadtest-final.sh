#!/bin/bash
APP=http://127.0.0.1:8092
NGX=http://127.0.0.1:8093
TOKEN=$(curl -s -X POST $APP/api/v1/auth/login -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"Dgp@2026"}' | sed -E 's/.*"accessToken":"([^"]+)".*/\1/')
ASSET=$(ls /opt/datagov/static/assets | grep -E '^index-.*\.js$' | head -1)

run() {
  local name=$1 url=$2 conc=$3 auth=$4 gzip=$5
  echo "### $name  c=$conc"
  local H=()
  [ "$auth" = "auth" ] && H+=(-H "Authorization: Bearer $TOKEN")
  [ "$gzip" = "gzip" ] && H+=(-H "Accept-Encoding: gzip")
  wrk -t2 -c"$conc" -d15s "${H[@]}" "$url" 2>&1 | grep -E "Requests/sec|Latency |Non-2xx|Socket errors"
}

echo "=========== 高并发档位（200） ==========="
run "dashboard  direct" "$APP/api/v1/overview/dashboard" 200 auth nogzip
run "metadata   direct" "$APP/api/v1/metadata/tables?pageSize=10" 200 auth nogzip
run "service    direct" "$APP/api/v1/service/apis?pageSize=10" 200 auth nogzip
run "quality    direct" "$APP/api/v1/quality/checks?pageSize=10" 200 auth nogzip
run "static     nginx"  "$NGX/assets/$ASSET" 200 noauth gzip
run "index      nginx"  "$NGX/" 200 noauth gzip
run "dashboard  nginx"  "$NGX/api/v1/overview/dashboard" 200 auth nogzip
run "metadata   nginx"  "$NGX/api/v1/metadata/tables?pageSize=10" 200 auth nogzip

echo ""
echo "=========== 登录吞吐（PBKDF2 210k 迭代，CPU 密集） ==========="
echo '{"username":"zhangsan","password":"Dgp@2026"}' > /tmp/login.json
for C in 1 10 20; do
  ab -n $((C*10)) -c $C -p /tmp/login.json -T application/json -q "$APP/api/v1/auth/login" 2>&1 \
    | grep -E "Requests per second|Time per request:|Failed requests|Non-2xx" | sed "s/^/  c=$C  /"
done

echo ""
echo "=== 结束状态 ==="
free -m | head -2
ps -o pid,rss,%cpu -p $(systemctl show -p MainPID --value datagov)
