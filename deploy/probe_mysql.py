#!/usr/bin/env python3
"""MySQL 环境探测（只读）：版本、root 访问方式、库/用户、sql_mode、字符集。"""
import os

import paramiko

HOST = os.environ.get("DGP_SSH_HOST", "127.0.0.1")
PORT = int(os.environ.get("DGP_SSH_PORT", "23195"))
USER = os.environ.get("DGP_SSH_USER", "root")
PWD = os.environ["DGP_SSH_PASS"]

CMDS = [
    "mysql --version; mysqld --version 2>/dev/null | head -1",
    "systemctl is-active mysql 2>/dev/null; systemctl is-active mariadb 2>/dev/null",
    # root 通过 auth_socket 通常免密；失败则说明需要密码
    "mysql -N -e \"SELECT VERSION(), @@sql_mode, @@character_set_server, @@default_storage_engine;\" 2>&1 | head -3",
    "mysql -N -e \"SELECT user,host,plugin FROM mysql.user;\" 2>&1 | head -20",
    "mysql -N -e \"SHOW DATABASES;\" 2>&1 | head -20",
    "mysql -N -e \"SELECT COUNT(*) FROM information_schema.tables WHERE table_schema NOT IN ('mysql','information_schema','performance_schema','sys');\" 2>&1",
    "free -m | head -2; df -h / | tail -1",
]


def main():
    c = paramiko.SSHClient()
    c.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    c.connect(HOST, PORT, USER, PWD, timeout=20, look_for_keys=False, allow_agent=False)
    for cmd in CMDS:
        stdin, stdout, stderr = c.exec_command(cmd, timeout=60)
        out = stdout.read().decode("utf-8", "replace").strip()
        err = stderr.read().decode("utf-8", "replace").strip()
        print("$ " + cmd)
        if out:
            print(out)
        if err:
            print("[stderr] " + err)
        print("")
    c.close()


if __name__ == "__main__":
    main()
