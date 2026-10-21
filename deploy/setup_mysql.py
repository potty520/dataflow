#!/usr/bin/env python3
"""在目标服务器创建 MySQL 库/账号并先行校验 schema-mysql.sql。

凭据从环境变量读取：DGP_SSH_*、DGP_MYSQL_PASSWORD（新建 dgp 账号的密码）。
"""
import os
import sys

import paramiko

HOST = os.environ.get("DGP_SSH_HOST", "127.0.0.1")
PORT = int(os.environ.get("DGP_SSH_PORT", "23195"))
USER = os.environ.get("DGP_SSH_USER", "root")
PWD = os.environ["DGP_SSH_PASS"]
DB_NAME = os.environ.get("DGP_DB_NAME", "dgp")
DB_USER = os.environ.get("DGP_DB_USER", "dgp")
DB_PWD = os.environ["DGP_MYSQL_PASSWORD"]
SCHEMA = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
                      "backend", "src", "main", "resources", "schema-mysql.sql")
REMOTE_DIR = "/opt/datagov"


def main():
    c = paramiko.SSHClient()
    c.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    c.connect(HOST, PORT, USER, PWD, timeout=20, look_for_keys=False, allow_agent=False)
    reset = "--reset" in sys.argv

    def sh(cmd, check=True, timeout=300):
        stdin, stdout, stderr = c.exec_command(cmd, timeout=timeout)
        out = stdout.read().decode("utf-8", "replace")
        err = stderr.read().decode("utf-8", "replace")
        code = stdout.channel.recv_exit_status()
        print("$ " + cmd)
        if out.strip():
            print(out.rstrip())
        if err.strip():
            print("[stderr] " + err.rstrip())
        print("")
        if check and code != 0:
            raise SystemExit("failed (%d): %s" % (code, cmd))
        return out

    # 1) 库与账号（幂等；--reset 会先删库重建，用于清掉半装载的演示数据）
    if reset:
        sh('mysql -e "DROP DATABASE IF EXISTS %s;"' % DB_NAME)
        print("[reset] 已删除库 %s" % DB_NAME)
    sh("""mysql -e "
CREATE DATABASE IF NOT EXISTS %s DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
CREATE USER IF NOT EXISTS '%s'@'127.0.0.1' IDENTIFIED BY '%s';
ALTER USER '%s'@'127.0.0.1' IDENTIFIED BY '%s';
GRANT ALL PRIVILEGES ON %s.* TO '%s'@'127.0.0.1';
FLUSH PRIVILEGES;" """ % (DB_NAME, DB_USER, DB_PWD, DB_USER, DB_PWD, DB_NAME, DB_USER))
    sh("mysql -N -e \"SELECT user,host FROM mysql.user WHERE user='%s'; SHOW DATABASES LIKE '%s';\"" % (DB_USER, DB_NAME))

    # 2) 上传并执行 DDL
    sh("mkdir -p %s" % REMOTE_DIR)
    sftp = c.open_sftp()
    sftp.put(SCHEMA, REMOTE_DIR + "/schema-mysql.sql")
    sftp.close()
    sh("wc -l %s/schema-mysql.sql" % REMOTE_DIR)
    # 先清空（仅本库、仅首次验证时可重复执行 DDL；表已存在时 IF NOT EXISTS 跳过）
    out = sh("mysql --default-character-set=utf8mb4 %s < %s/schema-mysql.sql && echo DDL_OK" % (DB_NAME, REMOTE_DIR), check=False)
    if "DDL_OK" not in out:
        raise SystemExit("DDL 执行失败，请检查上方错误")
    sh("mysql -N -e \"SELECT COUNT(*) AS tables_in_%s FROM information_schema.tables WHERE table_schema='%s';\"" % (DB_NAME, DB_NAME))
    sh("mysql -N -e \"SELECT table_name FROM information_schema.tables WHERE table_schema='%s' ORDER BY table_name LIMIT 12;\"" % DB_NAME)

    # 3) 用应用账号连一次（验证 GRANT 与密码）
    sh("mysql -h127.0.0.1 -u%s -p'%s' -N -e \"SELECT DATABASE(), CURRENT_USER();\" %s" % (DB_USER, DB_PWD, DB_NAME))
    c.close()
    print("MySQL 库/账号与 DDL 校验完成：db=%s user=%s@127.0.0.1" % (DB_NAME, DB_USER))


if __name__ == "__main__":
    main()
