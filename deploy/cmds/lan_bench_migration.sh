#!/bin/bash
# 大数据量迁移基准 v3：100 万行（同库 / 跨库 / FTP），带 RSS 采样
set -u
BASE=http://127.0.0.1:8092
ROWS=${ROWS:-1000000}
DB=dgp
RUNID=$$
# 凭据从环境变量读取（脚本本身不保存任何口令）
DS_DB_PASS=${DGP_DB_PASSWORD:?请设置 DGP_DB_PASSWORD}
FTP_PASS=${DGP_FTP_PASS:?请设置 DGP_FTP_PASS}
FTP_HOST=${DGP_FTP_HOST:-127.0.0.1}
FTP_USER=${DGP_FTP_USER:-dgpftp}
say() { echo; echo "########## $* ##########"; }
ms() { date +%s%3N; }

systemctl restart datagov >/dev/null 2>&1
for i in $(seq 1 90); do code=$(curl -s -o /dev/null -w '%{http_code}' $BASE/api/v1/admin/ping || true); [ "$code" = "200" ] && break; sleep 1; done
TOKEN=$(curl -s -X POST $BASE/api/v1/auth/login -H 'Content-Type: application/json' -d '{"username":"admin","password":"Dgp@2026"}' | python3 -c "import sys,json;print(json.load(sys.stdin)['data']['accessToken'])")
AUTH="Authorization: Bearer $TOKEN"
api() { local m=$1 p=$2 b=${3:-}; if [ -n "$b" ]; then curl -s -X "$m" "$BASE$p" -H "$AUTH" -H 'Content-Type: application/json' -d "$b"; else curl -s -X "$m" "$BASE$p" -H "$AUTH"; fi; }
jget() { python3 -c 'import sys,json; d=json.load(sys.stdin); print(d'"$1"')' 2>/dev/null; }
sample_rss() { local out=$1 pid; pid=$(pgrep -f dgp-boot | head -1); : > "$out"; while sleep 1; do [ -d "/proc/$pid" ] || break; awk '/VmRSS/{print $2}' /proc/$pid/status >> "$out"; done; }
peak() { [ -s "$1" ] && sort -n "$1" | tail -1 || echo 0; }

echo "=== 准备 $ROWS 行测试表（run=$RUNID） ==="
mysql $DB -e "DROP TABLE IF EXISTS zz_big, zz_big_copy, zz_big_copy2, zz_big_copy3;
  CREATE TABLE zz_big (id BIGINT PRIMARY KEY, amount DECIMAL(12,2), order_time DATETIME, customer_id VARCHAR(32));
  CREATE TABLE zz_big_copy LIKE zz_big; CREATE TABLE zz_big_copy2 LIKE zz_big; CREATE TABLE zz_big_copy3 LIKE zz_big;"
t0=$(ms)
mysql $DB -e "SET SESSION cte_max_recursion_depth=$((ROWS+10));
  INSERT INTO zz_big WITH RECURSIVE seq(n) AS (SELECT 1 UNION ALL SELECT n+1 FROM seq WHERE n<$ROWS)
  SELECT n, ROUND(RAND()*1000,2), NOW() - INTERVAL (n%1440) MINUTE, CONCAT('C', LPAD(n%6000,7,'0')) FROM seq;"
t1=$(ms)
echo "  rows=$(mysql -N $DB -e 'SELECT COUNT(*) FROM zz_big') prep_sec=$(( (t1-t0)/1000 ))"

MAP='{"mappings":[{"sourceColumn":"id","targetColumn":"id"},{"sourceColumn":"amount","targetColumn":"amount"},{"sourceColumn":"order_time","targetColumn":"order_time"},{"sourceColumn":"customer_id","targetColumn":"customer_id"}]}'

say "1) 同库 INSERT…SELECT ($ROWS 行)"
P1=$(api POST /api/v1/migration/projects "{\"code\":\"BENCH-SAME-$RUNID\",\"name\":\"基准-同库\",\"sourceTable\":\"zz_big\",\"targetTable\":\"zz_big_copy\",\"mode\":\"FULL\"}" | jget "['data']['id']")
[ -z "$P1" ] && echo "  project create failed: $(api POST /api/v1/migration/projects "{\"code\":\"BENCH-SAME-B-$RUNID\",\"name\":\"x\",\"sourceTable\":\"zz_big\",\"targetTable\":\"zz_big_copy\"}")"
api POST /api/v1/migration/projects/$P1/mappings "$MAP" >/dev/null
sample_rss /tmp/rss_same.txt & SPID=$!
t0=$(ms); R=$(api POST /api/v1/migration/projects/$P1/run '{"replace":1}'); t1=$(ms)
kill $SPID 2>/dev/null
INS=$(echo "$R" | jget "['data']['insertedRows']")
echo "  inserted=${INS:-NA} ms=$(( t1-t0 )) rows_per_sec=$(( ${INS:-0}*1000/(t1-t0+1) )) peakRSS_KB=$(peak /tmp/rss_same.txt)"
[ -z "$INS" ] && echo "  resp: $R"

say "2) 跨库迁移（远程读 → 本地写，$ROWS 行）"
DS=$(api POST /api/v1/migration/datasources "{\"code\":\"BENCH-DS-$RUNID\",\"name\":\"基准远程\",\"dbType\":\"MYSQL\",\"host\":\"127.0.0.1\",\"port\":3306,\"dbName\":\"dgp\",\"username\":\"dgp\",\"password\":\"'"$DS_DB_PASS"'\",\"proxyType\":\"DIRECT\"}" | jget "['data']['id']")
P2=$(api POST /api/v1/migration/projects "{\"code\":\"BENCH-CROSS-$RUNID\",\"name\":\"基准-跨库\",\"sourceDsId\":$DS,\"targetDsId\":0,\"sourceTable\":\"zz_big\",\"targetTable\":\"zz_big_copy2\",\"mode\":\"FULL\"}" | jget "['data']['id']")
api POST /api/v1/migration/projects/$P2/mappings "$MAP" >/dev/null
sample_rss /tmp/rss_cross.txt & SPID=$!
t0=$(ms); R=$(api POST /api/v1/migration/projects/$P2/run '{"replace":1,"batchSize":5000}'); t1=$(ms)
kill $SPID 2>/dev/null
INS=$(echo "$R" | jget "['data']['insertedRows']")
echo "  inserted=${INS:-NA} ms=$(( t1-t0 )) rows_per_sec=$(( ${INS:-0}*1000/(t1-t0+1) )) peakRSS_KB=$(peak /tmp/rss_cross.txt)"
[ -z "$INS" ] && echo "  resp: $R"

say "3) FTP 导出/导入（$ROWS 行 CSV）"
P3=$(api POST /api/v1/migration/projects "{\"code\":\"BENCH-FTP-$RUNID\",\"name\":\"基准-FTP\",\"sourceTable\":\"zz_big\",\"targetTable\":\"zz_big_copy3\",\"mode\":\"FULL\"}" | jget "['data']['id']")
api POST /api/v1/migration/projects/$P3/mappings "$MAP" >/dev/null
T1=$(api POST /api/v1/migration/files/tasks "{\"code\":\"BENCH-EXP-$RUNID\",\"name\":\"基准导出\",\"direction\":\"EXPORT\",\"projectId\":$P3,\"remoteHost\":\"$FTP_HOST\",\"remotePort\":21,\"remoteUser\":\"$FTP_USER\",\"remotePassword\":\"$FTP_PASS\",\"remoteDir\":\"/upload\",\"remoteFile\":\"bench_$RUNID.csv\",\"proxyType\":\"DIRECT\"}" | jget "['data']['id']")
sample_rss /tmp/rss_ftp.txt & SPID=$!
t0=$(ms); R=$(api POST /api/v1/migration/files/tasks/$T1/run); t1=$(ms)
kill $SPID 2>/dev/null
echo "  export rows=$(echo "$R"|jget "['data']['rows']") bytes=$(echo "$R"|jget "['data']['bytes']") ms=$(( t1-t0 )) peakRSS_KB=$(peak /tmp/rss_ftp.txt)"
T2=$(api POST /api/v1/migration/files/tasks "{\"code\":\"BENCH-IMP-$RUNID\",\"name\":\"基准导入\",\"direction\":\"IMPORT\",\"projectId\":$P3,\"remoteHost\":\"$FTP_HOST\",\"remotePort\":21,\"remoteUser\":\"$FTP_USER\",\"remotePassword\":\"$FTP_PASS\",\"remoteDir\":\"/upload\",\"remoteFile\":\"bench_$RUNID.csv\",\"proxyType\":\"DIRECT\"}" | jget "['data']['id']")
sample_rss /tmp/rss_ftpi.txt & SPID=$!
t0=$(ms); R=$(api POST /api/v1/migration/files/tasks/$T2/run); t1=$(ms)
kill $SPID 2>/dev/null
INS=$(echo "$R" | jget "['data']['rows']")
echo "  import rows=${INS:-NA} ms=$(( t1-t0 )) rows_per_sec=$(( ${INS:-0}*1000/(t1-t0+1) )) peakRSS_KB=$(peak /tmp/rss_ftpi.txt)"
[ -z "$INS" ] && echo "  resp: $R"

echo; echo "=== 清理 ==="
for p in ${P1:-} ${P2:-} ${P3:-}; do [ -n "$p" ] && api DELETE /api/v1/migration/projects/$p >/dev/null; done
for t in ${T1:-} ${T2:-}; do [ -n "$t" ] && api DELETE /api/v1/migration/files/tasks/$t >/dev/null; done
[ -n "${DS:-}" ] && api DELETE /api/v1/migration/datasources/$DS >/dev/null
mysql $DB -e "DROP TABLE IF EXISTS zz_big, zz_big_copy, zz_big_copy2, zz_big_copy3"
rm -f /tmp/rss_*.txt
echo done
