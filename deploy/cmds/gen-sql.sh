#!/bin/bash
# 生成 SQL 脚本：结构 + 初始化数据（核心配置数据与演示大数据分开）
set -e
cd /opt/datagov
echo "=== 表分类 ==="
mysql -N dgp -e "SELECT table_name FROM information_schema.tables WHERE table_schema='dgp' ORDER BY table_name" > /tmp/all_tables.txt
grep -c . /tmp/all_tables.txt
echo "演示事实/明细表（ods_/dwd_/dws_/ads_ 前缀）:"
grep -E '^(ods|dwd|dws|ads)_' /tmp/all_tables.txt | wc -l

echo "=== 全量数据 dump（含演示明细） ==="
mysqldump --single-transaction --no-create-info --complete-insert --skip-add-locks \
  --default-character-set=utf8mb4 dgp > /tmp/dgp-data-full.sql 2>/tmp/dump-err.txt || { cat /tmp/dump-err.txt; exit 1; }
ls -lh /tmp/dgp-data-full.sql | awk '{print "  full data:", $5}'

echo "=== 核心数据 dump（排除演示明细表） ==="
IGN=""
for t in $(grep -E '^(ods|dwd|dws|ads)_' /tmp/all_tables.txt); do IGN="$IGN --ignore-table=dgp.$t"; done
mysqldump --single-transaction --no-create-info --complete-insert --skip-add-locks \
  --default-character-set=utf8mb4 $IGN dgp > /tmp/dgp-data-core.sql
ls -lh /tmp/dgp-data-core.sql | awk '{print "  core data:", $5}'

echo "=== 结构 dump（MySQL 8，含全部索引） ==="
mysqldump --single-transaction --no-data --skip-add-locks --default-character-set=utf8mb4 dgp > /tmp/dgp-schema-mysql.sql
ls -lh /tmp/dgp-schema-mysql.sql | awk '{print "  schema:", $5}'

echo "=== 核心数据表行数（用于脚本头部注释） ==="
mysql -N dgp -e "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='dgp'"
