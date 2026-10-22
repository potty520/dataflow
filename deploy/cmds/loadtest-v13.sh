#!/bin/bash
# v1.3 新增接口压测（服务器本机，排除网络 RTT）
BASE=http://127.0.0.1:8092
TOKEN=$(curl -s -X POST $BASE/api/v1/auth/login -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"Dgp@2026"}' | sed -E 's/.*"accessToken":"([^"]+)".*/\1/')
run() {
  local name=$1 url=$2 conc=$3
  echo "### $name  c=$conc"
  wrk -t2 -c"$conc" -d15s -H "Authorization: Bearer $TOKEN" "$url" 2>&1 | grep -E "Requests/sec|Latency |Non-2xx|Socket errors"
}
echo "=========== v1.3 新接口 ==========="
run "流式质检概览"     "$BASE/api/v1/quality/stream/overview" 100
run "流式质检概览"     "$BASE/api/v1/quality/stream/overview" 200
run "流式流源列表"     "$BASE/api/v1/quality/stream/sources?pageSize=10" 100
run "流式结果列表"     "$BASE/api/v1/quality/stream/results?pageSize=10" 100
run "迁移概览"         "$BASE/api/v1/migration/overview" 100
run "迁移概览"         "$BASE/api/v1/migration/overview" 200
run "迁移项目列表"     "$BASE/api/v1/migration/projects?pageSize=10" 100
echo ""
echo "=== 推送接入吞吐（事件级：每条含 3 条规则判定） ==="
TS=$(date '+%Y-%m-%d %H:%M:%S')
for i in 1 2 3; do
  CODE=$(curl -s -o /tmp/push.json -w '%{http_code}' -X POST "$BASE/api/v1/quality/stream/push/SS-DEVICE-METRIC" \
    -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
    -d "{\"id\":$((800000+i)),\"deviceId\":\"DEV-LOAD-$i\",\"deviceName\":\"压测\",\"workshop\":\"W\",\"metricName\":\"m\",\"value\":150,\"unit\":\"u\",\"collectTime\":\"$TS\"}")
  echo "  push#$i http=$CODE resp=$(head -c 120 /tmp/push.json)"
done
echo ""
echo "=== 结束状态 ==="
free -m | sed -n 2p
