#!/bin/bash
# 清理验收过程中创建的探针数据（保留真实业务数据）
DB=dgp
q() { mysql -N "$DB" -e "$1"; }

echo "=== 清理前 ==="
q "SELECT 'md_table', COUNT(*) FROM md_table UNION ALL SELECT 'md_column', COUNT(*) FROM md_column
   UNION ALL SELECT 'qa_rule', COUNT(*) FROM qa_rule UNION ALL SELECT 'svc_api', COUNT(*) FROM svc_api
   UNION ALL SELECT 'svc_app', COUNT(*) FROM svc_app UNION ALL SELECT 'std_element', COUNT(*) FROM std_element
   UNION ALL SELECT 'int_job', COUNT(*) FROM int_job UNION ALL SELECT 'sec_mask_policy', COUNT(*) FROM sec_mask_policy"

# 元数据探针表（仅元数据行，无物理表）
q "DELETE FROM md_column WHERE table_id IN (SELECT id FROM md_table WHERE name LIKE 'zz_import%' OR name LIKE 'zz_verify%')"
q "DELETE FROM md_table WHERE name LIKE 'zz_import%' OR name LIKE 'zz_verify%'"
# 探针数据源
q "DELETE FROM md_datasource WHERE code LIKE 'DS-PROBE-%'"
# 探针接口/应用/编排/黑名单
q "DELETE FROM svc_api_version WHERE api_id IN (SELECT id FROM svc_api WHERE code LIKE 'API-VERIFY-%' OR code LIKE 'API-OFF-%' OR code LIKE 'API-RL-%')"
q "DELETE FROM svc_api WHERE code LIKE 'API-VERIFY-%' OR code LIKE 'API-OFF-%' OR code LIKE 'API-RL-%'"
q "DELETE FROM svc_app WHERE app_code LIKE 'APP-VERIFY-%'"
q "DELETE FROM svc_orchestration WHERE name LIKE '验收%' OR name = 'verify'"
q "DELETE FROM svc_blacklist WHERE ip = '10.0.0.99'"
# 探针脱敏策略（cust_name，由验收创建）
q "DELETE FROM sec_mask_policy WHERE table_name='ods_customer' AND column_name IN ('cust_name','province','addr','sales_rep')"
# 探针数据元 / 稽核任务 / 规则
q "DELETE FROM std_element WHERE code LIKE 'DE_PROBE%'"
q "DELETE FROM qa_check WHERE name LIKE '验收%'"
q "DELETE FROM qa_rule WHERE code LIKE 'QR-VERIFY%'"
q "DELETE FROM int_job WHERE code LIKE 'JOB-VERIFY%'"
q "DELETE FROM mdl_model WHERE code LIKE 'MDL_PROBE%'"
# 恢复行业包启用态（种子数据为已启用）
q "DELETE FROM ind_enable_scope WHERE package_code='TELECOM_BSS'"
q "INSERT INTO ind_enable_scope(package_code,tenant_id,status,enable_time) VALUES('TELECOM_BSS',1,'ENABLED',NOW())"
q "UPDATE ind_package SET status='ENABLED' WHERE package_code='TELECOM_BSS'"
# 未完成的验收报告任务（保留已生成的报告文件）
q "DELETE FROM rpt_task WHERE title LIKE '验收%'"

echo "=== 清理后 ==="
q "SELECT 'md_table', COUNT(*) FROM md_table UNION ALL SELECT 'md_column', COUNT(*) FROM md_column
   UNION ALL SELECT 'qa_rule', COUNT(*) FROM qa_rule UNION ALL SELECT 'svc_api', COUNT(*) FROM svc_api
   UNION ALL SELECT 'svc_app', COUNT(*) FROM svc_app UNION ALL SELECT 'std_element', COUNT(*) FROM std_element
   UNION ALL SELECT 'int_job', COUNT(*) FROM int_job UNION ALL SELECT 'sec_mask_policy', COUNT(*) FROM sec_mask_policy"
echo "=== 关键业务数据 ==="
q "SELECT 'ods_order', COUNT(*) FROM ods_order UNION ALL SELECT 'dwd_order_fact', COUNT(*) FROM dwd_order_fact
   UNION ALL SELECT 'sys_user', COUNT(*) FROM sys_user UNION ALL SELECT 'qa_issue', COUNT(*) FROM qa_issue"
