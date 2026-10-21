#!/bin/bash
# 验证 SQL 脚本：在临时库中重建并核对数据，验证完删除
set -e
SCHEMA=/tmp/dgp-schema-mysql.sql
DATA=/tmp/dgp-init-data.sql
DB=dgp_sqltest

mysql -e "DROP DATABASE IF EXISTS $DB; CREATE DATABASE $DB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;"
echo "=== 导入结构 ==="
mysql --default-character-set=utf8mb4 $DB < $SCHEMA && echo "schema imported OK"
echo "=== 导入初始化数据 ==="
mysql --default-character-set=utf8mb4 $DB < $DATA && echo "data imported OK"
echo "=== 核对 ==="
mysql -N $DB -e "SELECT CONCAT('tables=', COUNT(*)) FROM information_schema.tables WHERE table_schema='$DB'"
mysql -N $DB -e "SELECT CONCAT('users=', COUNT(*)) FROM sys_user;
SELECT CONCAT('roles=', COUNT(*)) FROM sys_role;
SELECT CONCAT('md_table=', COUNT(*)) FROM md_table;
SELECT CONCAT('md_column=', COUNT(*)) FROM md_column;
SELECT CONCAT('std_element=', COUNT(*)) FROM std_element;
SELECT CONCAT('qa_rule=', COUNT(*)) FROM qa_rule;
SELECT CONCAT('sec_mask_policy=', COUNT(*)) FROM sec_mask_policy;
SELECT CONCAT('svc_api=', COUNT(*)) FROM svc_api;
SELECT CONCAT('ind_package=', COUNT(*)) FROM ind_package;
SELECT CONCAT('seed_marker=', config_value) FROM sys_config WHERE config_key='seed.completed';"
echo "=== 清理临时库 ==="
mysql -e "DROP DATABASE $DB" && echo "dropped $DB"
