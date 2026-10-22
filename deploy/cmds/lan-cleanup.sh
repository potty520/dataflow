#!/bin/bash
# 清理验收过程中产生的探针数据（保留演示数据）
DB=dgp
q() { mysql -N "$DB" -e "$1"; }

echo "=== 清理前 ==="
q "SELECT CONCAT('md_table=', COUNT(*)) FROM md_table"

q "DELETE FROM md_column WHERE table_id IN (SELECT id FROM md_table WHERE name LIKE 'zz_import%' OR name LIKE 'zz_verify%')"
q "DELETE FROM md_table WHERE name LIKE 'zz_import%' OR name LIKE 'zz_verify%'"
q "DELETE FROM md_datasource WHERE code LIKE 'DS-PROBE-%'"
q "DELETE FROM svc_api_version WHERE api_id IN (SELECT id FROM svc_api WHERE code LIKE 'API-VERIFY-%' OR code LIKE 'API-OFF-%' OR code LIKE 'API-RL-%')"
q "DELETE FROM svc_api WHERE code LIKE 'API-VERIFY-%' OR code LIKE 'API-OFF-%' OR code LIKE 'API-RL-%'"
q "DELETE FROM svc_app WHERE app_code LIKE 'APP-VERIFY-%'"
q "DELETE FROM svc_orchestration WHERE name LIKE '验收%'"
q "DELETE FROM svc_blacklist WHERE ip = '10.0.0.99'"
q "DELETE FROM sec_mask_policy WHERE table_name='ods_customer' AND column_name IN ('cust_name')"
q "DELETE FROM std_element WHERE code LIKE 'DE_PROBE%'"
q "DELETE FROM qa_check WHERE name LIKE '验收%'"
q "DELETE FROM qa_rule WHERE code LIKE 'QR-VERIFY%'"
q "DELETE FROM int_job WHERE code LIKE 'JOB-VERIFY%'"
q "DELETE FROM mdl_model WHERE code LIKE 'MDL_PROBE%'"
q "DELETE FROM rpt_task WHERE title LIKE '验收%'"

echo "=== 清理后 ==="
q "SELECT CONCAT('md_table=', COUNT(*)) FROM md_table"
q "SELECT CONCAT('users=', COUNT(*), ' rules=', (SELECT COUNT(*) FROM qa_rule), ' apis=', (SELECT COUNT(*) FROM svc_api), ' jobs=', (SELECT COUNT(*) FROM int_job)) FROM sys_user"
q "SELECT CONCAT('ods_order=', COUNT(*), ' dwd_order_fact=', (SELECT COUNT(*) FROM dwd_order_fact), ' ods_customer=', (SELECT COUNT(*) FROM ods_customer)) FROM ods_order"

echo "=== 清理 v1.3 验收残留 ==="
for t in $(mysql -N -e "SELECT table_name FROM information_schema.tables WHERE table_schema='$DB' AND table_name LIKE 'zz\_mig\_verify%'"); do
  mysql -e "DROP TABLE IF EXISTS $DB.$t"
  echo "  dropped $t"
done
mysql $DB -e "DELETE FROM ods_device_metric WHERE id >= 900000"
mysql $DB -e "DELETE FROM mig_project WHERE code LIKE 'MIG-BATCH-%' OR code LIKE 'MIG-VERIFY-%'"
mysql $DB -e "DELETE FROM qa_stream_rule WHERE code LIKE 'SR-VERIFY-%'"
mysql $DB -e "DELETE FROM qa_stream_push WHERE source_id NOT IN (SELECT id FROM qa_stream_source)"
echo "=== v1.3 残留清理完成 ==="

echo "=== 清理 v1.4 验收临时表（zz_*） ==="
for t in $(mysql -N -e "SELECT table_name FROM information_schema.tables WHERE table_schema='$DB' AND table_name LIKE 'zz\_%'"); do
  mysql -e "DROP TABLE IF EXISTS $DB.$t"
  echo "  dropped $t"
done
mysql $DB -e "DELETE FROM mig_datasource WHERE code LIKE 'DS-%'"
mysql $DB -e "DELETE FROM mig_file_task WHERE code LIKE 'FTP-%'"
mysql $DB -e "DELETE FROM mig_project WHERE code LIKE 'MIG-CROSS%' OR code LIKE 'MIG-FTP%'"
echo "=== v1.4 残留清理完成 ==="
