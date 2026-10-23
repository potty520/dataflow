#!/usr/bin/env python3
"""局域网服务器：初始化数据库 + 部署 jar（全部通过"上传脚本再执行"，避免嵌套引号问题）。"""
import os
import secrets
import sys
import time

import paramiko

HOST = os.environ.get("LAN_HOST", "192.168.1.14")
PORT = int(os.environ.get("LAN_PORT", "22"))
USER = os.environ.get("LAN_USER", "zk")
PWD = os.environ["LAN_PASS"]
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
APP = "/opt/datagov"
JAR = os.path.join(ROOT, "backend", "target", "dgp-boot.jar")
DB = os.environ.get("DGP_DB_NAME", "dgp")
DBU = os.environ.get("DGP_DB_USER", "dgp")
DBP = os.environ["DGP_DB_PASSWORD"]
APORT = int(os.environ.get("DGP_APP_PORT", "8092"))
NGX = int(os.environ.get("DGP_NGINX_PORT", "8093"))


class Lan:
    def __init__(self):
        self.c = paramiko.SSHClient()
        self.c.set_missing_host_key_policy(paramiko.AutoAddPolicy())
        self.c.connect(HOST, PORT, USER, PWD, timeout=15, look_for_keys=False, allow_agent=False)

    def sh(self, cmd, sudo=False, timeout=600, show=False):
        real = ("sudo -S -p '' bash -lc " + repr(cmd)) if sudo else cmd
        stdin, stdout, stderr = self.c.exec_command(real, timeout=timeout)
        if sudo:
            stdin.write(PWD + "\n")
            stdin.flush()
        out = stdout.read().decode("utf-8", "replace")
        err = stderr.read().decode("utf-8", "replace")
        code = stdout.channel.recv_exit_status()
        if show:
            print("$ " + cmd[:120])
            if out.strip():
                print(out.strip()[-2000:])
            if err.strip():
                print("[stderr] " + err.strip()[-800:])
            print("[exit %d]" % code)
        return code, out, err

    def script(self, name, body, sudo=True, timeout=900, show=True):
        """上传 shell 脚本到 /tmp 并执行（body 内可自由用引号/反引号）。"""
        self.put_text("/tmp/%s.sh" % name, "#!/bin/bash\nset -e\n" + body, mode=0o755)
        code, out, err = self.sh("bash /tmp/%s.sh" % name, sudo=sudo, timeout=timeout, show=show)
        return code, out, err

    def put_text(self, remote, text, mode=0o644):
        sftp = self.c.open_sftp()
        with sftp.open(remote, "w") as f:
            f.write(text)
        sftp.chmod(remote, mode)
        sftp.close()

    def put_file(self, local, remote):
        sftp = self.c.open_sftp()
        sftp.put(local, remote)
        sftp.close()

    def close(self):
        self.c.close()


def main():
    if not os.path.exists(JAR):
        sys.exit("找不到 jar：" + JAR)
    l = Lan()

    print("=== A. 初始化数据库 ===")
    if os.environ.get("DGP_RESET_DB", "0") == "1":
        l.script("db_reset", "mysql -e \"DROP DATABASE IF EXISTS {db}\"\necho dropped {db}\n".format(db=DB), show=True)
    load_sql = """CREATE DATABASE IF NOT EXISTS {db} DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
CREATE USER IF NOT EXISTS '{u}'@'127.0.0.1' IDENTIFIED BY '{p}';
ALTER USER '{u}'@'127.0.0.1' IDENTIFIED BY '{p}';
GRANT ALL PRIVILEGES ON {db}.* TO '{u}'@'127.0.0.1';
FLUSH PRIVILEGES;
SELECT user, host, plugin FROM mysql.user WHERE user='{u}';
SHOW DATABASES LIKE '{db}';
""".format(db=DB, u=DBU, p=DBP)
    l.put_text("/tmp/db_init.sql", load_sql)
    l.script("db_init", "mysql < /tmp/db_init.sql\n")
    code, out, err = l.sh('mysql -N -e "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=\'%s\'"' % DB, show=True)

    print("=== B. 上传 jar ===")
    size = os.path.getsize(JAR) / 1024 / 1024
    t0 = time.time()
    l.put_file(JAR, APP + "/dgp-boot.jar.new")
    print("  %.1f MB，用时 %.1fs" % (size, time.time() - t0))
    l.script("jar_swap", "chown %s:%s {app}/dgp-boot.jar.new\nmv -f {app}/dgp-boot.jar.new {app}/dgp-boot.jar\nls -lh {app}/dgp-boot.jar\n".format(app=APP) % (USER, USER))

    print("=== C. 环境变量与 systemd unit ===")
    # 重复部署必须复用已有密钥：否则 DGP_MASK_KEY 变化会让已加密数据（远程数据源口令、ENCRYPT 脱敏策略）无法解密
    jwt = secrets.token_urlsafe(48)
    mask = secrets.token_urlsafe(32)
    try:
        code, old_env, err = l.sh("cat %s/datagov.env 2>/dev/null" % APP)
        for line in (old_env or "").splitlines():
            if line.startswith("DGP_JWT_SECRET=") and len(line.split("=", 1)[1]) > 10:
                jwt = line.split("=", 1)[1].strip()
            if line.startswith("DGP_MASK_KEY=") and len(line.split("=", 1)[1]) > 10:
                mask = line.split("=", 1)[1].strip()
        print("  复用已有 JWT/MASK 密钥: %s" % ("是" if jwt and mask else "否"))
    except Exception as e:
        print("  读取已有密钥失败（将生成新密钥）: %s" % e)
    env = "\n".join([
        "# DGP 数据治理平台运行环境（部署脚本生成，权限 600）",
        "SPRING_PROFILES_ACTIVE=mysql",
        "DGP_DB_HOST=127.0.0.1",
        "DGP_DB_PORT=3306",
        "DGP_DB_NAME=%s" % DB,
        "DGP_DB_USER=%s" % DBU,
        "DGP_DB_PASSWORD=%s" % DBP,
        "DGP_JWT_SECRET=%s" % jwt,
        "DGP_MASK_KEY=%s" % mask,
        "DGP_CORS_ORIGINS=http://%s:%d,http://%s:%d,http://%s" % (HOST, NGX, HOST, APORT, HOST),
        "JAVA_OPTS=-Xms1g -Xmx2g -XX:+UseG1GC -XX:MaxGCPauseMillis=200 -Dfile.encoding=UTF-8",
        "SERVER_TOMCAT_THREADS_MAX=500",
        "SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE=32",
        "",
    ])
    l.put_text("/tmp/datagov.env", env, 0o600)
    unit = """[Unit]
Description=DGP Data Governance Platform (LAN test)
After=network.target mysql.service

[Service]
Type=simple
User={u}
Group={u}
WorkingDirectory={app}
EnvironmentFile={app}/datagov.env
ExecStart=/usr/bin/java $JAVA_OPTS -jar {app}/dgp-boot.jar --server.port={p}
SuccessExitStatus=143
Restart=on-failure
RestartSec=5
LimitNOFILE=65535
StandardOutput=append:{app}/logs/app.log
StandardError=append:{app}/logs/app.err.log

[Install]
WantedBy=multi-user.target
""".format(u=USER, app=APP, p=APORT)
    l.put_text("/tmp/datagov.service", unit)
    l.script("svc_install", """
install -m 600 -o {u} -g {u} /tmp/datagov.env {app}/datagov.env
install -m 644 /tmp/datagov.service /etc/systemd/system/datagov.service
systemctl daemon-reload
systemctl enable datagov >/dev/null 2>&1 || true
systemctl restart datagov
sleep 2
systemctl is-active datagov
""".format(u=USER, app=APP) % ())

    print("=== D. 等待启动 + 演示数据装载 ===")
    ok = False
    for i in range(120):
        code, out, err = l.sh("curl -s -m 3 http://127.0.0.1:%d/api/v1/admin/ping" % APORT)
        if '"UP"' in out:
            print("  健康检查通过（%ds）" % (i * 2))
            ok = True
            break
        time.sleep(2)
    if not ok:
        l.sh("tail -40 %s/logs/app.err.log; tail -30 %s/logs/app.log" % (APP, APP), show=True)
        sys.exit("服务未就绪")
    for i in range(90):
        code, out, err = l.sh('mysql -N %s -e "SELECT COUNT(*) FROM md_table"' % DB)
        try:
            n = int(out.strip() or 0)
        except ValueError:
            n = 0
        if n >= 13:
            print("  演示数据就绪：md_table=%d（等待 %ds）" % (n, i * 2))
            break
        time.sleep(2)
    else:
        print("  !! 演示数据可能未就绪")

    print("=== E. 静态资源 + nginx ===")
    l.script("static", """
rm -rf {app}/static
mkdir -p /tmp/dgpstatic
cd /tmp/dgpstatic
unzip -q -o {app}/dgp-boot.jar 'BOOT-INF/classes/static/*'
cp -r BOOT-INF/classes/static {app}/static
rm -rf /tmp/dgpstatic
cd {app}/static
find . -type f \\( -name '*.js' -o -name '*.css' -o -name '*.svg' -o -name '*.html' \\) | xargs -r gzip -9 -k -f
echo "static: $(du -sh . | cut -f1) / gz: $(find . -name '*.gz' | wc -l)"
chown -R {u}:{u} {app}/static
""".format(app=APP, u=USER) % ())
    nginx_conf = """# DGP 数据治理平台（局域网测试）
upstream dgp_backend_lan {{
    server 127.0.0.1:{p};
    keepalive 32;
}}
server {{
    listen {n};
    server_name _;
    root {app}/static;
    index index.html;
    chunked_transfer_encoding off;
    keepalive_timeout 30s;
    gzip on;
    gzip_static on;
    gzip_comp_level 4;
    gzip_min_length 1024;
    gzip_proxied off;
    gzip_types text/plain text/css application/javascript application/json application/xml image/svg+xml;
    location /assets/ {{ expires 365d; add_header Cache-Control "public, immutable"; access_log off; try_files $uri =404; }}
    location = /index.html {{ add_header Cache-Control "no-cache, must-revalidate"; }}
    location / {{ try_files $uri $uri/ /index.html; }}
    location /api/ {{
        proxy_pass http://dgp_backend_lan;
        proxy_http_version 1.1;
        proxy_set_header Connection "";
        proxy_set_header Host $host;
        proxy_set_header Origin "";
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_connect_timeout 5s;
        proxy_read_timeout 120s;
        proxy_buffering on;
    }}
    location /open/ {{
        proxy_pass http://dgp_backend_lan;
        proxy_http_version 1.1;
        proxy_set_header Connection "";
        proxy_set_header Host $host;
        proxy_set_header Origin "";
        proxy_set_header X-Real-IP $remote_addr;
        proxy_read_timeout 60s;
    }}
    access_log /var/log/nginx/dgp-lan-access.log;
    error_log  /var/log/nginx/dgp-lan-error.log warn;
}}
""".format(p=APORT, n=NGX, app=APP)
    l.put_text("/tmp/dgp-lan.conf", nginx_conf)
    l.script("nginx", """
install -m 644 /tmp/dgp-lan.conf /etc/nginx/conf.d/dgp-lan.conf
rm -f /etc/nginx/sites-enabled/default
nginx -t
systemctl enable --now nginx >/dev/null 2>&1 || true
systemctl reload nginx
systemctl is-active nginx
ss -lntp | grep -E ':(%d|%d) ' || true
curl -s -o /dev/null -w 'index: %%{http_code}\\n' http://127.0.0.1:%d/
curl -s -o /dev/null -w 'api: %%{http_code}\\n' http://127.0.0.1:%d/api/v1/admin/ping
""" % (APORT, NGX, NGX, NGX))
    l.close()
    print("\n完成：http://%s:%d" % (HOST, NGX))


if __name__ == "__main__":
    main()
