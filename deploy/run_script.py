#!/usr/bin/env python3
"""上传本地 shell 脚本到服务器并执行（支持多行脚本，避免单行转义问题）。

用法：python deploy/run_script.py deploy/cmds/xxx.sh [解释器]
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
    src = sys.argv[1]
    interp = sys.argv[2] if len(sys.argv) > 2 else "bash"
    remote = "/tmp/dgp_probe_" + os.path.basename(src)
    c = paramiko.SSHClient()
    c.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    c.connect(HOST, PORT, USER, PWD, timeout=20, look_for_keys=False, allow_agent=False)
    sftp = c.open_sftp()
    sftp.put(src, remote)
    sftp.chmod(remote, 0o644)
    sftp.close()
    stdin, stdout, stderr = c.exec_command("%s %s" % (interp, remote), timeout=600)
    out = stdout.read().decode("utf-8", "replace")
    err = stderr.read().decode("utf-8", "replace")
    code = stdout.channel.recv_exit_status()
    print(out.rstrip())
    if err.strip():
        print("[stderr] " + err.strip()[:1500])
    print("[exit %d]" % code)
    c.close()


if __name__ == "__main__":
    main()
