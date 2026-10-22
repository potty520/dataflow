#!/usr/bin/env python3
"""步骤1：检查 apt 源与网络，执行 apt update。"""
import os

import paramiko

HOST = os.environ.get("LAN_HOST", "192.168.1.14")
PORT = int(os.environ.get("LAN_PORT", "22"))
USER = os.environ.get("LAN_USER", "zk")
PWD = os.environ["LAN_PASS"]


def main():
    c = paramiko.SSHClient()
    c.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    c.connect(HOST, PORT, USER, PWD, timeout=15, look_for_keys=False, allow_agent=False)

    def sh(cmd, sudo=False, timeout=600, show=True):
        real = ("sudo -S -p '' bash -lc " + repr(cmd)) if sudo else cmd
        stdin, stdout, stderr = c.exec_command(real, timeout=timeout, get_pty=False)
        if sudo:
            stdin.write(PWD + "\n")
            stdin.flush()
        out = stdout.read().decode("utf-8", "replace")
        err = stderr.read().decode("utf-8", "replace")
        code = stdout.channel.recv_exit_status()
        if show:
            print("$ " + cmd[:120])
            if out.strip():
                print(out.strip()[:4000])
            if err.strip():
                print("[stderr] " + err.strip()[:2000])
            print("[exit %d]\n" % code)
        return code, out, err

    sh("ls /etc/apt/sources.list.d/ 2>/dev/null; echo '---'; head -20 /etc/apt/sources.list.d/*.sources 2>/dev/null; echo '---'; head -5 /etc/apt/sources.list 2>/dev/null")
    sh("echo 'TCP 443 测试:'; (exec 3<>/dev/tcp/mirrors.aliyun.com/443 && echo '  mirrors.aliyun.com OK' && exec 3<&-) 2>/dev/null || echo '  mirrors.aliyun.com FAIL'; "
       "(exec 3<>/dev/tcp/archive.ubuntu.com/443 && echo '  archive.ubuntu.com OK' && exec 3<&-) 2>/dev/null || echo '  archive.ubuntu.com FAIL'; "
       "(exec 3<>/dev/tcp/192.168.1.1/80 && echo '  gateway 80 OK' && exec 3<&-) 2>/dev/null || echo '  gateway 80 FAIL'")
    sh("export DEBIAN_FRONTEND=noninteractive; sudo -n true 2>/dev/null || true; apt-get update -qq 2>&1 | tail -8", sudo=True, timeout=900)
    sh("apt-cache policy mysql-server openjdk-17-jre-headless nginx 2>/dev/null | grep -E '^(mysql|openjdk|nginx)|Candidate' | head -12")

    c.close()


if __name__ == "__main__":
    main()
