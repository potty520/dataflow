#!/bin/bash
# 生成"配置/主数据"初始化脚本（排除演示明细与运行时日志，避免泄露访问 IP）
set -e
cd /opt/datagov
RUNTIME="sec_audit_log sys_login_log sys_operation_log sys_token_blacklist
int_job_log int_job_instance qa_check_instance qa_score md_collect_instance
svc_call_log ast_visit_log rpt_task rpt_file qa_report_instance"
IGN=""
for t in $(mysql -N dgp -e "SELECT table_name FROM information_schema.tables WHERE table_schema='dgp'"); do
  case "$t" in
    ods_*|dwd_*|dws_*|ads_*) IGN="$IGN --ignore-table=dgp.$t" ;;
  esac
done
for t in $RUNTIME; do IGN="$IGN --ignore-table=dgp.$t"; done

mysqldump --single-transaction --no-create-info --complete-insert --skip-add-locks \
  --skip-triggers --default-character-set=utf8mb4 $IGN dgp > /tmp/dgp-init-data.sql
ls -lh /tmp/dgp-init-data.sql | awk '{print "init-data:", $5}'
echo "=== 脚本内是否含公网访问 IP / 口令 ==="
grep -oE "(42\.89\.[0-9]+\.[0-9]+|103\.85\.25\.169)" /tmp/dgp-init-data.sql | sort -u | head
echo "=== 包含的数据表数量 ==="
grep -c "^INSERT INTO" /tmp/dgp-init-data.sql
echo "=== 行数 top ==="
awk '/^INSERT INTO/{print $3}' /tmp/dgp-init-data.sql | sort | uniq -c | sort -rn | head -8
