#!/usr/bin/env python3
"""局域网服务器探测：系统、资源、sudo、外网、已装组件、端口。"""
import os
import sys

import paramiko

HOST = os.environ.get("LAN_HOST", "192.168.1.14")
PORT = int(os.environ.get("LAN_PORT", "22"))
USER = os.environ.get("LAN_USER", "zk")
PWD = os.environ["LAN_PASS"]


def main():
    c = paramiko.SSHClient()
    c.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    c.connect(HOST, PORT, USER, PWD, timeout=15, look_for_keys=False, allow_agent=False)

    def run(cmd, sudo=False, timeout=30):
        if sudo:
            cmd = "sudo -S -p '' bash -lc " + repr(cmd)
        stdin, stdout, stderr = c.exec_command(cmd, timeout=timeout)
        if sudo:
            stdin.write(PWD + "\n")
            stdin.flush()
        out = stdout.read().decode("utf-8", "replace").strip()
        err = stderr.read().decode("utf-8", "replace").strip()
        code = stdout.channel.recv_exit_status()
        return code, out, err

    print("=== 当前用户 ===")
    print(run("whoami; id")[1])

    print("\n=== 系统 ===")
    print(run("cat /etc/os-release | head -3; uname -r; lsb_release -ds 2>/dev/null")[1])

    print("\n=== 资源 ===")
    print(run("nproc; free -m | head -2; df -h / | tail -1; uptime")[1])

    print("\n=== sudo 能力 ===")
    code, out, err = run("sudo -n true 2>&1 && echo PASSWORDLESS_SUDO || echo NEED_PASSWORD", sudo=False)
    print(out)
    code, out, err = run("id -u; echo OK_SUDO", sudo=True)
    print(out or err)

    print("\n=== 外网可达性（apt/镜像） ===")
    print(run("timeout 8 curl -sI https://mirrors.aliyun.com 2>&1 | head -1; timeout 8 curl -sI https://registry.npmmirror.com 2>&1 | head -1")[1])

    print("\n=== 已装组件 ===")
    print(run("for x in mysql mysqld mariadbd java javac nginx unzip tar curl git; do printf '%-10s %s\\n' $x \"$(command -v $x || echo -)\"; done")[1])
    print(run("java -version 2>&1 | head -2")[1])

    print("\n=== 已运行的数据库/服务 ===")
    print(run("systemctl list-units --type=service --state=running --no-pager --no-legend | awk '{print $1}' | head -20")[1])
    print(run("ss -lntp 2>/dev/null | awk 'NR>1{print $4}' | sed 's/.*://' | sort -n -u | tr '\\n' ' '")[1])

    print("\n=== apt 源 ===")
    print(run("grep -rhE '^deb ' /etc/apt/sources.list /etc/apt/sources.list.d/ 2>/dev/null | head -4")[1])

    c.close()


if __name__ == "__main__":
    main()
