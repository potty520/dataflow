#!/usr/bin/env python3
"""在局域网服务器上执行本地脚本（上传→执行→打印输出）。

用法：python deploy/lan_exec.py deploy/cmds/loadtest-final.sh [--sudo] [--timeout 1800]
"""
import os
import sys

import paramiko

HOST = os.environ.get("LAN_HOST", "192.168.1.14")
PORT = int(os.environ.get("LAN_PORT", "22"))
USER = os.environ.get("LAN_USER", "zk")
PWD = os.environ["LAN_PASS"]


def main():
    args = [a for a in sys.argv[1:] if not a.startswith("--")]
    if not args:
        sys.exit("用法：lan_exec.py <本地脚本> [--sudo] [--timeout N]")
    local = args[0]
    sudo = "--sudo" in sys.argv
    timeout = 1800
    for i, a in enumerate(sys.argv):
        if a == "--timeout" and i + 1 < len(sys.argv):
            timeout = int(sys.argv[i + 1])

    c = paramiko.SSHClient()
    c.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    c.connect(HOST, PORT, USER, PWD, timeout=15, look_for_keys=False, allow_agent=False)
    remote = "/tmp/" + os.path.basename(local)
    sftp = c.open_sftp()
    sftp.put(local, remote)
    sftp.chmod(remote, 0o755)
    sftp.close()

    cmd = "bash " + remote
    if sudo:
        cmd = "sudo -S -p '' bash " + remote
    stdin, stdout, stderr = c.exec_command(cmd, timeout=timeout, get_pty=True)
    if sudo:
        stdin.write(PWD + "\n")
        stdin.flush()
    for line in iter(stdout.readline, ""):
        sys.stdout.write(line)
        sys.stdout.flush()
    err = stderr.read().decode("utf-8", "replace")
    if err.strip():
        print("[stderr] " + err.strip()[-1000:])
    code = stdout.channel.recv_exit_status()
    print("[exit %d]" % code)
    c.close()


if __name__ == "__main__":
    main()
