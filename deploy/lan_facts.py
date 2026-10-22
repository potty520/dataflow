#!/usr/bin/env python3
"""收集 4C8G 部署环境事实，用于压测报告。"""
import os

import paramiko

HOST = os.environ.get("LAN_HOST", "192.168.1.14")
PORT = int(os.environ.get("LAN_PORT", "22"))
USER = os.environ.get("LAN_USER", "zk")
PWD = os.environ["LAN_PASS"]

SCRIPT = r"""
echo "=== 系统 ==="
cat /etc/os-release | grep PRETTY
nproc; free -m | sed -n 2p; df -h / | tail -1
echo "=== 组件版本 ==="
mysqld --version
java -version 2>&1 | head -1
nginx -v 2>&1
echo "=== 服务状态 ==="
for s in datagov mysql nginx; do printf "%-9s %s\n" "$s" "$(systemctl is-active $s)"; done
echo "=== 应用进程 ==="
ps -o pid,rss,%cpu,args -p $(systemctl show -p MainPID --value datagov) | cut -c1-160
echo "=== 环境变量（脱敏） ==="
grep -vE "SECRET|MASK_KEY|PASSWORD" /opt/datagov/datagov.env
echo "=== MySQL 关键参数 ==="
mysql -N -e "SHOW VARIABLES WHERE Variable_name IN ('innodb_buffer_pool_size','max_connections','innodb_flush_log_at_trx_commit','version')"
echo "=== 数据量 ==="
mysql -N dgp -e "SELECT CONCAT('tables=', COUNT(*)) FROM information_schema.tables WHERE table_schema='dgp'"
mysql -N dgp -e "SELECT CONCAT('md_table=', COUNT(*), ' users=', (SELECT COUNT(*) FROM sys_user), ' ods_order=', (SELECT COUNT(*) FROM ods_order), ' dwd_order_fact=', (SELECT COUNT(*) FROM dwd_order_fact)) FROM md_table"
echo "=== 占用 ==="
du -sh /opt/datagov /var/lib/mysql/dgp 2>/dev/null
echo "=== 端口 ==="
ss -lntp | grep -E ':(8092|8093) ' | awk '{print $4}'
"""


def main():
    c = paramiko.SSHClient()
    c.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    c.connect(HOST, PORT, USER, PWD, timeout=15, look_for_keys=False, allow_agent=False)
    sftp = c.open_sftp()
    with sftp.open("/tmp/facts.sh", "w") as f:
        f.write(SCRIPT)
    sftp.close()
    stdin, stdout, stderr = c.exec_command("sudo -S -p '' bash /tmp/facts.sh", timeout=120)
    stdin.write(PWD + "\n")
    stdin.flush()
    print(stdout.read().decode("utf-8", "replace"))
    err = stderr.read().decode("utf-8", "replace")
    if err.strip():
        print("[stderr]", err.strip()[:500])
    c.close()


if __name__ == "__main__":
    main()
