#!/usr/bin/env python3
"""从服务器取回 SQL dump，生成仓库 sql/ 目录（含说明头、H2/PG 结构、README）。"""
import io
import os
import time

import paramiko

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SQL_DIR = os.path.join(ROOT, "sql")
HOST = os.environ.get("DGP_SSH_HOST", "127.0.0.1")
PORT = int(os.environ.get("DGP_SSH_PORT", "22"))
USER = os.environ.get("DGP_SSH_USER", "root")
PWD = os.environ["DGP_SSH_PASS"]

HEADER = """-- =============================================================================
-- DGP 数据治理平台 · {title}
-- 生成时间：{date}　数据库：MySQL 8.0（utf8mb4 / InnoDB）
-- 来源：运行实例 mysqldump，已剔除运行时日志与访问 IP、已排除演示明细数据
-- 配套文件：sql/schema-mysql.sql（结构）、sql/init-data-mysql.sql（初始化数据）
--
-- 导入方式：
--   mysql -u root -p -e "CREATE DATABASE dgp DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci"
--   mysql -u root -p dgp < sql/schema-mysql.sql
--   mysql -u root -p dgp < sql/init-data-mysql.sql
-- 演示账号：admin / zhangsan / liuqin / wangwu / secadmin / consultant，口令均为 Dgp@2026（仅演示用）
-- =============================================================================

"""

README = """# SQL 脚本 · Database Scripts

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
"""


def main():
    os.makedirs(SQL_DIR, exist_ok=True)
    c = paramiko.SSHClient()
    c.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    c.connect(HOST, PORT, USER, PWD, timeout=20, look_for_keys=False, allow_agent=False)
    sftp = c.open_sftp()
    date = time.strftime("%Y-%m-%d")

    def pull(remote, local, title):
        with sftp.open(remote) as f:
            body = f.read().decode("utf-8")
        text = HEADER.format(title=title, date=date) + body
        with io.open(os.path.join(SQL_DIR, local), "w", encoding="utf-8", newline="\n") as f:
            f.write(text)
        print("%s -> sql/%s (%.0f KB)" % (remote, local, len(text.encode()) / 1024))

    pull("/tmp/dgp-schema-mysql.sql", "schema-mysql.sql", "数据结构（MySQL 8）")
    pull("/tmp/dgp-init-data.sql", "init-data-mysql.sql", "初始化数据（配置/主数据）")
    sftp.close()
    c.close()

    # H2 / PostgreSQL 结构：直接取仓库内 backend 的 schema.sql
    src = os.path.join(ROOT, "backend", "src", "main", "resources", "schema.sql")
    with io.open(src, encoding="utf-8") as f:
        body = f.read()
    h2 = ("-- =============================================================================\n"
          "-- DGP 数据治理平台 · 数据结构（H2 / PostgreSQL，77 张表）\n"
          "-- 说明：H2 默认由应用启动时自动执行 backend/src/main/resources/schema.sql；\n"
          "--       PostgreSQL（prod profile）需手工导入本文件。\n"
          "-- =============================================================================\n\n") + body
    with io.open(os.path.join(SQL_DIR, "schema-h2-pg.sql"), "w", encoding="utf-8", newline="\n") as f:
        f.write(h2)
    print("backend schema.sql -> sql/schema-h2-pg.sql (%.0f KB)" % (len(h2.encode()) / 1024))

    with io.open(os.path.join(SQL_DIR, "README.md"), "w", encoding="utf-8", newline="\n") as f:
        f.write(README)
    print("sql/README.md written")


if __name__ == "__main__":
    main()
