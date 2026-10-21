#!/bin/bash
# 基线/复测压测：localhost 打点，排除公网网络影响
BASE=http://127.0.0.1:8092
OUT=${1:-/tmp/loadtest.txt}
: > "$OUT"

TOKEN=$(curl -s -X POST $BASE/api/v1/auth/login -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"Dgp@2026"}' | sed -E 's/.*"accessToken":"([^"]+)".*/\1/')
ASSET=$(curl -s $BASE/ | grep -o '/assets/index-[A-Za-z0-9_-]*\.js' | head -1)
echo "token=${#TOKEN} asset=$ASSET" | tee -a "$OUT"

run() {
  local name=$1 url=$2 conc=$3 auth=$4
  echo "" | tee -a "$OUT"
  echo "### $name  concurrency=$conc" | tee -a "$OUT"
  if [ "$auth" = "auth" ]; then
    wrk -t2 -c"$conc" -d15s -H "Authorization: Bearer $TOKEN" "$url" 2>&1 \
      | grep -E "Requests/sec|Latency|Non-2xx|Socket errors|Transfer/sec" | tee -a "$OUT"
  else
    wrk -t2 -c"$conc" -d15s "$url" 2>&1 \
      | grep -E "Requests/sec|Latency|Non-2xx|Socket errors|Transfer/sec" | tee -a "$OUT"
  fi
}

for C in 10 50 100; do
  run "public-ping"        "$BASE/api/v1/admin/ping"                         "$C" noauth
  run "metadata-tables"    "$BASE/api/v1/metadata/tables?pageSize=10"        "$C" auth
  run "overview-dashboard" "$BASE/api/v1/overview/dashboard"                 "$C" auth
  run "quality-checks"     "$BASE/api/v1/quality/checks?pageSize=10"         "$C" auth
  run "service-apis"       "$BASE/api/v1/service/apis?pageSize=10"           "$C" auth
  run "static-asset"       "$BASE$ASSET"                                     "$C" noauth
done

echo "" | tee -a "$OUT"
echo "### login (PBKDF2, POST) —— ab 测 CPU 密集登录" | tee -a "$OUT"
echo '{"username":"zhangsan","password":"Dgp@2026"}' > /tmp/login.json
for C in 5 10 20; do
  echo "--- login c=$C" | tee -a "$OUT"
  ab -n $((C*10)) -c $C -p /tmp/login.json -T application/json -q "$BASE/api/v1/auth/login" 2>&1 \
    | grep -E "Requests per second|Time per request|Failed requests|Non-2xx" | tee -a "$OUT"
done

echo "" | tee -a "$OUT"
echo "=== 压测结束时服务端状态 ===" | tee -a "$OUT"
top -bn1 | head -5 | tee -a "$OUT"
mysql -N -e "SHOW STATUS WHERE Variable_name IN ('Threads_connected','Threads_running','Max_used_connections')" | tee -a "$OUT"
