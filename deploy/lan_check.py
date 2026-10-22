#!/usr/bin/env python3
"""部署后检查：演示数据、账号、接口冒烟。"""
import json
import os
import time
import urllib.request

import paramiko

HOST = os.environ.get("LAN_HOST", "192.168.1.14")
BASE = "http://%s:%d" % (HOST, int(os.environ.get("DGP_NGINX_PORT", "8093")))
PORT = int(os.environ.get("LAN_PORT", "22"))
USER = os.environ.get("LAN_USER", "zk")
PWD = os.environ["LAN_PASS"]
DB = os.environ.get("DGP_DB_NAME", "dgp")


def http(path, method="GET", body=None, token=None, timeout=30):
    req = urllib.request.Request(BASE + path, method=method)
    req.add_header("Content-Type", "application/json")
    if token:
        req.add_header("Authorization", "Bearer " + token)
    data = json.dumps(body).encode() if body is not None else None
    try:
        with urllib.request.urlopen(req, data=data, timeout=timeout) as r:
            return r.status, json.loads(r.read().decode())
    except urllib.error.HTTPError as e:
        try:
            return e.code, json.loads(e.read().decode())
        except Exception:
            return e.code, {}


def main():
    print("=== 接口冒烟 ===")
    print("  ping:", http("/api/v1/admin/ping")[0])
    code, body = http("/api/v1/auth/login", "POST", {"username": "admin", "password": "Dgp@2026"})
    print("  login:", code)
    if code != 200:
        print("  -> 演示数据可能未装载完成，等待 20s 重试 ...")
        time.sleep(20)
        code, body = http("/api/v1/auth/login", "POST", {"username": "admin", "password": "Dgp@2026"})
        print("  login(重试):", code)
    token = (body.get("data") or {}).get("accessToken") if code == 200 else None
    if token:
        for path in ["/api/v1/metadata/stats/overview", "/api/v1/overview/dashboard",
                     "/api/v1/quality/stats/overview", "/api/v1/service/stats/overview"]:
            c, b = http(path, token=token)
            d = b.get("data") or {}
            brief = {k: d.get(k) for k in list(d.keys())[:4]}
            print("  %-38s HTTP %s %s" % (path, c, brief))

    print("\n=== 数据库核对 ===")
    c = paramiko.SSHClient()
    c.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    c.connect(HOST, PORT, USER, PWD, timeout=15, look_for_keys=False, allow_agent=False)

    def sudo(cmd, timeout=120):
        real = "sudo -S -p '' bash -lc " + repr(cmd)
        stdin, stdout, stderr = c.exec_command(real, timeout=timeout)
        stdin.write(PWD + "\n")
        stdin.flush()
        return stdout.read().decode("utf-8", "replace").strip(), stderr.read().decode("utf-8", "replace").strip()

    sql = ("SELECT CONCAT('tables=', COUNT(*)) FROM information_schema.tables WHERE table_schema='%s';"
           "SELECT CONCAT('users=', COUNT(*)) FROM sys_user;"
           "SELECT CONCAT('md_table=', COUNT(*)) FROM md_table;"
           "SELECT CONCAT('ods_order=', COUNT(*)) FROM ods_order;"
           "SELECT CONCAT('qa_rule=', COUNT(*)) FROM qa_rule;"
           "SELECT CONCAT('seed=', config_value) FROM sys_config WHERE config_key='seed.completed';" % DB)
    out, err = sudo('mysql -N %s -e "%s"' % (DB, sql))
    print(out or err)
    out, err = sudo("systemctl is-active datagov; systemctl is-active mysql; systemctl is-active nginx; free -m | sed -n 2p; "
                    "ps -o pid,rss,%%cpu -p $(systemctl show -p MainPID --value datagov)")
    print(out or err)
    c.close()


if __name__ == "__main__":
    main()
