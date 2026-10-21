#!/usr/bin/env python3
"""在目标服务器上顺序执行一批 shell 命令（命令从文件读取，避免 shell 引号转义问题）。

用法：python deploy/exec.py <命令文件>
命令文件：UTF-8，每行一条命令，空行与 # 开头行忽略。
凭据：DGP_SSH_*
"""
import os
import sys

import paramiko

HOST = os.environ.get("DGP_SSH_HOST", "127.0.0.1")
PORT = int(os.environ.get("DGP_SSH_PORT", "23195"))
USER = os.environ.get("DGP_SSH_USER", "root")
PWD = os.environ["DGP_SSH_PASS"]


def main():
    path = sys.argv[1]
    with open(path, encoding="utf-8") as f:
        commands = [ln.rstrip("\r\n") for ln in f if ln.strip() and not ln.lstrip().startswith("#")]

    c = paramiko.SSHClient()
    c.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    c.connect(HOST, PORT, USER, PWD, timeout=20, look_for_keys=False, allow_agent=False)

    for cmd in commands:
        stdin, stdout, stderr = c.exec_command(cmd, timeout=300)
        out = stdout.read().decode("utf-8", "replace")
        err = stderr.read().decode("utf-8", "replace")
        code = stdout.channel.recv_exit_status()
        print("$ " + cmd)
        if out.strip():
            print(out.rstrip())
        if err.strip():
            print("[stderr] " + err.strip()[:800])
        if code != 0:
            print("[exit %d]" % code)
        print()
    c.close()


if __name__ == "__main__":
    main()
