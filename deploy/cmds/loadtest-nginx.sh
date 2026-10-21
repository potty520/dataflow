#!/bin/bash
# nginx 托管静态资源 + 反代 API 后的对比压测
NGX=http://127.0.0.1:8093
APP=http://127.0.0.1:8092
TOKEN=$(curl -s -X POST $APP/api/v1/auth/login -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"Dgp@2026"}' | sed -E 's/.*"accessToken":"([^"]+)".*/\1/')
ASSET=$(ls /opt/datagov/static/assets | grep -E '^index-.*\.js$' | head -1)
echo "asset=$ASSET token=${#TOKEN}"

run() {
  local name=$1 url=$2 conc=$3 auth=$4 gzip=$5
  echo ""
  echo "### $name  c=$conc"
  local H=()
  [ "$auth" = "auth" ] && H+=(-H "Authorization: Bearer $TOKEN")
  [ "$gzip" = "gzip" ] && H+=(-H "Accept-Encoding: gzip")
  wrk -t2 -c"$conc" -d15s "${H[@]}" "$url" 2>&1 \
    | grep -E "Requests/sec|Latency |Non-2xx|Socket errors|Transfer/sec"
}

echo "=========== 静态资源：Tomcat(8092) vs nginx(8093) ==========="
run "static(Tomcat, 1.3MB js)" "$APP/assets/$ASSET" 100 noauth nogzip
run "static(nginx, gzip)"      "$NGX/assets/$ASSET" 100 noauth gzip
run "static(nginx, gzip)"      "$NGX/assets/$ASSET" 50 noauth gzip
run "index.html(nginx)"        "$NGX/"              100 noauth gzip

echo ""
echo "=========== API：直连 8092 vs 经 nginx 8093 ==========="
run "api dashboard direct" "$APP/api/v1/overview/dashboard" 100 auth nogzip
run "api dashboard nginx"  "$NGX/api/v1/overview/dashboard" 100 auth nogzip
run "api metadata direct"  "$APP/api/v1/metadata/tables?pageSize=10" 100 auth nogzip
run "api metadata nginx"   "$NGX/api/v1/metadata/tables?pageSize=10" 100 auth nogzip

echo ""
echo "=== 压测结束服务端状态 ==="
top -bn1 | head -4
free -m | head -2
