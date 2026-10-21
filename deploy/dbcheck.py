#!/usr/bin/env python3
"""MySQL 侧快速体检：种子数据计数、相关表行数估算、最近的 SQL 报错摘要。

用法：python deploy/dbcheck.py
凭据：DGP_SSH_*
"""
import os

import paramiko

HOST = os.environ.get("DGP_SSH_HOST", "127.0.0.1")
PORT = int(os.environ.get("DGP_SSH_PORT", "23195"))
USER = os.environ.get("DGP_SSH_USER", "root")
PWD = os.environ["DGP_SSH_PASS"]
DB = os.environ.get("DGP_DB_NAME", "dgp")

QUERIES = [
    ("core counts", """
SELECT 'users' k, COUNT(*) v FROM sys_user
UNION ALL SELECT 'tables', COUNT(*) FROM md_table
UNION ALL SELECT 'columns', COUNT(*) FROM md_column
UNION ALL SELECT 'rules', COUNT(*) FROM qa_rule
UNION ALL SELECT 'jobs', COUNT(*) FROM int_job
UNION ALL SELECT 'apis', COUNT(*) FROM svc_api
UNION ALL SELECT 'issues', COUNT(*) FROM qa_issue
UNION ALL SELECT 'ods_customer', COUNT(*) FROM ods_customer
UNION ALL SELECT 'ods_order', COUNT(*) FROM ods_order
UNION ALL SELECT 'ods_device_metric', COUNT(*) FROM ods_device_metric
UNION ALL SELECT 'audit_log', COUNT(*) FROM sec_audit_log;
"""),
    ("seed marker", "SELECT config_key, config_value FROM sys_config WHERE config_key IN ('seed.completed','platform.version');"),
    ("table row estimates", """
SELECT table_name, table_rows FROM information_schema.tables
WHERE table_schema = DATABASE() ORDER BY table_rows DESC LIMIT 8;
"""),
]


def main():
    c = paramiko.SSHClient()
    c.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    c.connect(HOST, PORT, USER, PWD, timeout=20, look_for_keys=False, allow_agent=False)

    for title, sql in QUERIES:
        # 通过 stdin 传 SQL，避免 shell 引号问题
        cmd = "mysql -N --table --default-character-set=utf8mb4 %s" % DB
        stdin, stdout, stderr = c.exec_command(cmd, timeout=120)
        stdin.write(sql)
        stdin.channel.shutdown_write()
        out = stdout.read().decode("utf-8", "replace")
        err = stderr.read().decode("utf-8", "replace")
        print("=== %s ===" % title)
        print(out.rstrip() or "(empty)")
        if err.strip():
            print("[stderr] " + err.strip())
        print()

    stdin, stdout, stderr = c.exec_command(
        "grep -c 'SQLSyntaxErrorException' /opt/datagov/logs/app.log 2>/dev/null; "
        "grep -o 'bad SQL grammar \\[[^]]\\{0,120\\}' /opt/datagov/logs/app.log 2>/dev/null | tail -3", timeout=60)
    print("=== recent bad SQL ===")
    print(stdout.read().decode("utf-8", "replace").strip() or "(none)")
    c.close()


if __name__ == "__main__":
    main()
