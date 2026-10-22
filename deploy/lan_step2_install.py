#!/usr/bin/env python3
"""步骤2：安装 MySQL 8 / JDK17 / nginx / 工具，并初始化数据库。"""
import os
import sys
import time

import paramiko

HOST = os.environ.get("LAN_HOST", "192.168.1.14")
PORT = int(os.environ.get("LAN_PORT", "22"))
USER = os.environ.get("LAN_USER", "zk")
PWD = os.environ["LAN_PASS"]
DB_NAME = os.environ.get("DGP_DB_NAME", "dgp")
DB_USER = os.environ.get("DGP_DB_USER", "dgp")
DB_PASS = os.environ.get("DGP_DB_PASSWORD", "")
SCHEMA = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
                      "sql", "schema-mysql.sql")
INIT = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
                    "sql", "init-data-mysql.sql")


def main():
    c = paramiko.SSHClient()
    c.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    c.connect(HOST, PORT, USER, PWD, timeout=15, look_for_keys=False, allow_agent=False)

    def sh(cmd, sudo=True, timeout=1800, show=True):
        real = ("sudo -S -p '' bash -lc " + repr(cmd)) if sudo else cmd
        stdin, stdout, stderr = c.exec_command(real, timeout=timeout, get_pty=False)
        if sudo:
            stdin.write(PWD + "\n")
            stdin.flush()
        out = stdout.read().decode("utf-8", "replace")
        err = stderr.read().decode("utf-8", "replace")
        code = stdout.channel.recv_exit_status()
        if show:
            print("$ " + cmd[:140])
            if out.strip():
                print(out.strip()[-3000:])
            if err.strip():
                print("[stderr] " + err.strip()[-1500:])
            print("[exit %d]" % code)
        return code, out, err

    def sftp_put(local, remote):
        sftp = c.open_sftp()
        sftp.put(local, remote)
        sftp.close()
        print("uploaded -> %s (%.1f KB)" % (remote, os.path.getsize(local) / 1024))

    # 1) apt 源确认 + 安装
    sh("apt-get update 2>&1 | tail -3")
    sh("apt-cache policy mysql-server openjdk-17-jre-headless nginx | grep -E 'Candidate' ")
    code, out, err = sh("export DEBIAN_FRONTEND=noninteractive; "
                        "apt-get install -y --no-install-recommends mysql-server openjdk-17-jre-headless nginx curl unzip ca-certificates apache2-utils 2>&1 | tail -15",
                        timeout=2400)
    sh("echo '---版本---'; mysqld --version; java -version 2>&1 | head -1; nginx -v 2>&1; ab -V 2>&1 | head -1")

    # 2) MySQL 就绪判断
    sh("systemctl enable --now mysql 2>&1 | tail -2; systemctl is-active mysql; "
       "for i in $(seq 1 30); do mysqladmin ping 2>/dev/null && break; sleep 1; done")

    # 3) 4C8G 调参与建库
    conf = """[mysqld]
# DGP 4C8G 测试机参数
innodb_buffer_pool_size = 1G
max_connections = 300
innodb_flush_log_at_trx_commit = 2
slow_query_log = 1
long_query_time = 1
character-set-server = utf8mb4
collation-server = utf8mb4_general_ci
"""
    sh("mkdir -p /etc/mysql/mysql.conf.d")
    sftp = c.open_sftp()
    with sftp.open("/tmp/zz-dgp.cnf", "w") as f:
        f.write(conf)
    sftp.close()
    sh("cp /tmp/zz-dgp.cnf /etc/mysql/mysql.conf.d/zz-dgp.cnf && chmod 644 /etc/mysql/mysql.conf.d/zz-dgp.cnf && systemctl restart mysql && sleep 3 && systemctl is-active mysql")

    if not DB_PASS:
        raise SystemExit("请通过环境变量 DGP_DB_PASSWORD 指定数据库口令")

    sh('mysql -e "CREATE DATABASE IF NOT EXISTS `%s` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;"' % DB_NAME)
    sh("mysql -e \"CREATE USER IF NOT EXISTS '%s'@'127.0.0.1' IDENTIFIED BY '%s'; "
       "ALTER USER '%s'@'127.0.0.1' IDENTIFIED BY '%s'; "
       "GRANT ALL PRIVILEGES ON `%s`.* TO '%s'@'127.0.0.1'; FLUSH PRIVILEGES;\""
       % (DB_USER, DB_PASS, DB_USER, DB_PASS, DB_NAME, DB_USER))
    sh("mysql -N -e \"SELECT user,host,plugin FROM mysql.user WHERE user='%s'; SHOW DATABASES LIKE '%s';\"" % (DB_USER, DB_NAME))

    # 4) 上传并导入 SQL
    sftp_put(SCHEMA, "/tmp/schema-mysql.sql")
    sftp_put(INIT, "/tmp/init-data-mysql.sql")
    sh("mysql --default-character-set=utf8mb4 %s < /tmp/schema-mysql.sql && echo SCHEMA_OK" % DB_NAME, timeout=600)
    sh("mysql --default-character-set=utf8mb4 %s < /tmp/init-data-mysql.sql && echo DATA_OK" % DB_NAME, timeout=900)
    sh("mysql -N %s -e \"SELECT CONCAT('tables=', COUNT(*)) FROM information_schema.tables WHERE table_schema='%s'; "
       "SELECT CONCAT('users=', COUNT(*)) FROM sys_user; SELECT CONCAT('seed=', config_value) FROM sys_config WHERE config_key='seed.completed';\"" % (DB_NAME, DB_NAME))

    # 5) 应用目录
    sh("mkdir -p /opt/datagov/data /opt/datagov/logs /opt/datagov/static && chown -R %s:%s /opt/datagov && ls -ld /opt/datagov" % (USER, USER))

    c.close()
    print("\n步骤2 完成")


if __name__ == "__main__":
    main()
