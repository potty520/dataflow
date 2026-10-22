# SQL 脚本 · Database Scripts

本目录提供数据库初始化的 SQL 脚本，便于不启动应用也能建库、或用 DBA 流程预置数据。

| 文件 | 说明 | 大小 |
|---|---|---|
| `schema-mysql.sql` | MySQL 8 结构（90 张表，含索引/字符集，utf8mb4） | ~85 KB |
| `init-data-mysql.sql` | 初始化数据（用户/角色/权限/租户/字典/标准/模型/规则/脱敏策略/服务/行业包等配置与主数据，不含演示明细与运行时日志） | ~340 KB |
| `schema-h2-pg.sql` | H2 / PostgreSQL 结构（77 张基础表；H2 默认由应用自动建表，一般无需手工执行） | ~70 KB |

## 导入（MySQL）

```bash
mysql -u root -p -e "CREATE DATABASE dgp DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci"
mysql -u root -p dgp < sql/schema-mysql.sql
mysql -u root -p dgp < sql/init-data-mysql.sql
```

导入后以 `--spring.profiles.active=mysql` 启动应用即可（`sys_config.seed.completed=1` 已置位，应用不会重复装载种子数据）。

演示账号：`admin` / `zhangsan` / `liuqin` / `wangwu` / `secadmin` / `consultant`，口令统一 `Dgp@2026`。

## 演示明细数据（可选）

`init-data-mysql.sql` 不含 ODS/DWD/DWS/ADS 演示明细（约 11 万行）。两种获取方式：

1. 让应用自动生成：清空 `sys_config` 中 `seed.completed` 后重启（会重新装载完整演示数据）；
2. 从已有实例导出：

```bash
mysqldump --single-transaction --no-create-info --complete-insert --default-character-set=utf8mb4 dgp > demo-data.sql
```

## 重新生成脚本

```bash
# MySQL 结构由 schema.sql 自动生成（IDENTITY→AUTO_INCREMENT、索引内联、TIMESTAMP→DATETIME）
python deploy/gen_mysql_schema.py

# 结构与初始化数据直接来自运行实例（需先配置 DGP_SSH_* / DGP_DB_* 环境变量）
bash deploy/cmds/gen-sql.sh
```

> 注意：`init-data-mysql.sql` 已剔除审计/登录/操作日志、任务实例、调用日志等运行时数据，避免携带访问 IP 等环境信息。

## 已有数据库升级（v1.2 → v1.3）

已按 v1.2 建库的环境，执行一次升级脚本即可（新增流式推送配置表，以及迁移分批/回滚、流源推送所需的列）：

```bash
mysql -u root -p dgp < sql/upgrade-v1.2-to-v1.3.sql
```

新建库直接使用上面的 `schema-mysql.sql`（已包含全部 87 张表）。

## 已有数据库升级（v1.3 → v1.4）

v1.4 新增远程数据源、FTP 文件任务与批次表，并为迁移项目增加源/目标数据源字段：

```bash
mysql -u root -p dgp < sql/upgrade-v1.3-to-v1.4.sql
```
