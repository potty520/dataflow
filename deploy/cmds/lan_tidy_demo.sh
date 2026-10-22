#!/bin/bash
# 清理基准/验收遗留数据，保持演示库整洁（用于操作手册截图）
DB=dgp
echo "=== 清理前 ==="
mysql -N $DB -e "SELECT 'ds', COUNT(*) FROM mig_datasource WHERE code LIKE 'BENCH-%' UNION ALL SELECT 'task', COUNT(*) FROM mig_file_task WHERE code LIKE 'BENCH-%' UNION ALL SELECT 'proj', COUNT(*) FROM mig_project WHERE code LIKE 'BENCH-%' UNION ALL SELECT 'dict', COUNT(*) FROM sys_dict WHERE dict_code LIKE 'probe_dict_%'"
mysql $DB -e "DELETE FROM mig_datasource WHERE code LIKE 'BENCH-%' OR code LIKE 'DS-VERIFY-%' OR code LIKE 'DS-PROXY-%' OR code LIKE 'DS-BAD-%';
              DELETE FROM mig_file_task WHERE code LIKE 'BENCH-%' OR code LIKE 'FTP-%';
              DELETE FROM mig_project WHERE code LIKE 'BENCH-%' OR code LIKE 'MIG-CROSS%' OR code LIKE 'MIG-FTP%' OR code LIKE 'MIG-CUST-DIM%';
              DELETE FROM sys_dict WHERE dict_code LIKE 'probe_dict_%';"
rm -f /srv/dgpftp/upload/bench_*.csv /srv/dgpftp/upload/proxy_*.csv /srv/dgpftp/upload/dgp_verify_* 2>/dev/null || true
echo "=== 清理后 ==="
mysql -N $DB -e "SELECT 'ds', COUNT(*) FROM mig_datasource UNION ALL SELECT 'task', COUNT(*) FROM mig_file_task UNION ALL SELECT 'proj', COUNT(*) FROM mig_project UNION ALL SELECT 'dicts', COUNT(*) FROM sys_dict"
