#!/usr/bin/env python3
"""数据治理平台部署工具（paramiko，密码登录）。

凭据只从环境变量读取，不落盘：
  DGP_SSH_HOST / DGP_SSH_PORT / DGP_SSH_USER / DGP_SSH_PASS

用法：
  python deploy/remote.py probe                  # 服务器环境探测
  python deploy/remote.py deploy  <jar> <port>    # 上传并安装 systemd 服务
  python deploy/remote.py status  <port>          # 服务与端口状态
  python deploy/remote.py logs    [lines]
  python deploy/remote.py restart
  python deploy/remote.py stop
"""
import os
import sys
import stat
import time

import paramiko

HOST = os.environ.get("DGP_SSH_HOST", "127.0.0.1")
PORT = int(os.environ.get("DGP_SSH_PORT", "23195"))
USER = os.environ.get("DGP_SSH_USER", "root")
PWD = os.environ["DGP_SSH_PASS"]

APP_DIR = "/opt/datagov"
JAR = APP_DIR + "/dgp-boot.jar"
SERVICE = "datagov"
UNIT = "/etc/systemd/system/datagov.service"


def client():
    c = paramiko.SSHClient()
    c.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    c.connect(HOST, PORT, USER, PWD, timeout=20, look_for_keys=False, allow_agent=False)
    return c


def run(c, cmd, timeout=120):
    stdin, stdout, stderr = c.exec_command(cmd, timeout=timeout)
    out = stdout.read().decode("utf-8", "replace")
    err = stderr.read().decode("utf-8", "replace")
    code = stdout.channel.recv_exit_status()
    return code, out, err


def sh(c, cmd, timeout=120, check=True):
    code, out, err = run(c, cmd, timeout)
    print("$ " + cmd)
    if out.strip():
        print(out.rstrip())
    if err.strip():
        print("[stderr] " + err.rstrip())
    if check and code != 0:
        raise SystemExit("command failed (%d): %s" % (code, cmd))
    return out


def probe():
    c = client()
    print("=== connected: %s@%s:%d ===" % (USER, HOST, PORT))
    sh(c, "hostname; uname -m; nproc; grep -m1 PRETTY_NAME /etc/os-release")
    sh(c, "free -m | head -2; df -h / /opt | tail -2")
    sh(c, "java -version 2>&1 | head -3; command -v java; ls /usr/lib/jvm 2>/dev/null")
    sh(c, "cat /etc/systemd/system/*.service 2>/dev/null | grep -E '^(ExecStart|WorkingDirectory)' | head -20")
    sh(c, "ss -lntp 2>/dev/null | awk 'NR==1 || /LISTEN/' | head -40")
    sh(c, "systemctl --version | head -1; command -v nginx >/dev/null && nginx -v 2>&1; ls /etc/nginx/conf.d 2>/dev/null")
    sh(c, "(firewall-cmd --state 2>/dev/null && firewall-cmd --list-all 2>/dev/null) || (ufw status 2>/dev/null | head -8) || echo no-firewall-tool")
    sh(c, "ls -la /opt 2>/dev/null | head -20; df -h | tail -5")
    sh(c, "curl -s -m 5 -o /dev/null -w 'outbound-http:%{http_code}\\n' http://www.baidu.com || echo outbound-http:fail")
    c.close()


def upload(c, local):
    size = os.path.getsize(local)
    print("uploading %s (%.1f MB) -> %s" % (local, size / 1048576.0, JAR))
    sftp = c.open_sftp()
    tmp = JAR + ".new"
    with open(local, "rb") as f, sftp.open(tmp, "wb") as r:
        r.set_pipelined(True)
        sent = 0
        while True:
            chunk = f.read(1024 * 512)
            if not chunk:
                break
            r.write(chunk)
            sent += len(chunk)
            if sent % (1024 * 1024 * 4) < 1024 * 512:
                print("  %.1f / %.1f MB" % (sent / 1048576.0, size / 1048576.0))
    sftp.close()
    return tmp


def deploy(local_jar, port, secrets):
    c = client()
    sh(c, "mkdir -p %s/data %s/logs" % (APP_DIR, APP_DIR))
    tmp = upload(c, local_jar)
    sh(c, "cd %s && if [ -f dgp-boot.jar ]; then cp -f dgp-boot.jar 'dgp-boot.jar.bak.$(date +%%Y%%m%%d%%H%%M)'; fi && mv -f dgp-boot.jar.new dgp-boot.jar && ls -lh dgp-boot.jar" % APP_DIR)
    # 密钥写入独立环境变量文件（600 权限），避免出现在 systemd unit 与 ps 输出中
    env_file = APP_DIR + "/datagov.env"
    sftp = c.open_sftp()
    with sftp.open(env_file, "w") as f:
        f.write("DGP_JWT_SECRET=%s\n" % secrets["jwt"])
        f.write("DGP_MASK_KEY=%s\n" % secrets["mask"])
        f.write("DGP_CORS_ORIGINS=http://%s:%d,http://%s:%d,http://%s\n" % (HOST, port, HOST, int(os.environ.get("DGP_NGINX_PORT", "8093")), HOST))
        f.write("JAVA_OPTS=-Xms256m -Xmx512m -XX:+UseSerialGC -Dfile.encoding=UTF-8\n")
        # MySQL profile：通过环境变量启用（不写入 unit，便于一键切换回 H2）
        if os.environ.get("DGP_DB_PASSWORD"):
            f.write("SPRING_PROFILES_ACTIVE=mysql\n")
            f.write("DGP_DB_HOST=127.0.0.1\n")
            f.write("DGP_DB_PORT=3306\n")
            f.write("DGP_DB_NAME=%s\n" % os.environ.get("DGP_DB_NAME", "dgp"))
            f.write("DGP_DB_USER=%s\n" % os.environ.get("DGP_DB_USER", "dgp"))
            f.write("DGP_DB_PASSWORD=%s\n" % os.environ["DGP_DB_PASSWORD"])
    sftp.chmod(env_file, stat.S_IRUSR | stat.S_IWUSR)
    unit = """[Unit]
Description=DGP Data Governance Platform (demo/test)
After=network.target

[Service]
Type=simple
WorkingDirectory=%s
EnvironmentFile=%s
ExecStart=/usr/bin/java $JAVA_OPTS -jar %s --server.port=%d
Restart=on-failure
RestartSec=5
StandardOutput=append:%s/logs/app.log
StandardError=append:%s/logs/app.err.log

[Install]
WantedBy=multi-user.target
""" % (APP_DIR, env_file, JAR, port, APP_DIR, APP_DIR)
    with sftp.open(UNIT, "w") as f:
        f.write(unit)
    sftp.close()
    sh(c, "chmod 644 %s && systemctl daemon-reload && systemctl enable %s && systemctl restart %s && sleep 1 && systemctl is-active %s" % (UNIT, SERVICE, SERVICE, SERVICE))
    # 等待健康（1 vCPU + 首次建表装载演示数据，给足 120s）
    for i in range(60):
        code, out, err = run(c, "curl -s -m 3 http://127.0.0.1:%d/api/v1/admin/ping" % port)
        if '"status":"UP"' in out or '"status": "UP"' in out:
            print("health OK after %ds: %s" % (i * 2, out.strip()))
            break
        time.sleep(2)
    else:
        print("!! health check failed; tail logs:")
        sh(c, "tail -40 %s/logs/app.err.log; tail -30 %s/logs/app.log" % (APP_DIR, APP_DIR), check=False)
    sh(c, "ss -lntp | grep ':%d ' || true" % port, check=False)
    # 同步 nginx 托管的静态资源（含预压缩 .gz），否则前端会停在旧版本
    sh(c, "rm -rf %s/static && mkdir -p /tmp/dgpstatic && cd /tmp/dgpstatic && "
          "unzip -q -o %s/dgp-boot.jar 'BOOT-INF/classes/static/*' && "
          "cp -r BOOT-INF/classes/static %s/static && rm -rf /tmp/dgpstatic && "
          "cd %s/static && find . -type f \\( -name '*.js' -o -name '*.css' -o -name '*.svg' -o -name '*.html' \\) "
          "| xargs -r gzip -9 -k -f && echo static-synced"
          % (APP_DIR, APP_DIR, APP_DIR, APP_DIR), check=False)
    sh(c, "if systemctl is-active --quiet nginx && [ -f /etc/nginx/conf.d/dgp.conf ]; then nginx -t 2>/dev/null && "
          "systemctl reload nginx && echo 'nginx reloaded'; else echo 'nginx not configured (skip)'; fi", check=False)
    sh(c, "tail -5 %s/logs/app.log" % APP_DIR, check=False)
    c.close()


def status(port):
    c = client()
    sh(c, "systemctl status %s --no-pager -l | head -12" % SERVICE, check=False)
    sh(c, "ss -lntp | grep ':%d ' || echo 'port %d not listening'" % (port, port), check=False)
    sh(c, "curl -s -m 5 -o /dev/null -w 'local-health:%%{http_code}\\n' http://127.0.0.1:%d/api/v1/admin/ping" % port, check=False)
    sh(c, "tail -5 %s/logs/app.log" % APP_DIR, check=False)
    c.close()


def main():
    action = sys.argv[1] if len(sys.argv) > 1 else "probe"
    if action == "probe":
        probe()
    elif action == "deploy":
        jar = sys.argv[2]
        port = int(sys.argv[3])
        secrets = {"jwt": os.environ["DGP_JWT_SECRET"], "mask": os.environ["DGP_MASK_KEY"]}
        deploy(jar, port, secrets)
    elif action == "status":
        status(int(sys.argv[2]))
    elif action == "logs":
        n = sys.argv[2] if len(sys.argv) > 2 else "60"
        c = client()
        sh(c, "journalctl -u %s -n %s --no-pager | tail -40" % (SERVICE, n), check=False)
        sh(c, "tail -%s %s/logs/app.log" % (n, APP_DIR), check=False)
        c.close()
    elif action in ("restart", "stop", "start"):
        c = client()
        sh(c, "systemctl %s %s && sleep 2 && systemctl is-active %s" % (action, SERVICE, SERVICE), check=False)
        c.close()
    else:
        raise SystemExit(__doc__)


if __name__ == "__main__":
    main()
