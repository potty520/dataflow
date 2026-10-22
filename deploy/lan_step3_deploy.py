#!/usr/bin/env python3
"""步骤3：部署 jar → systemd 服务 → 初始化演示数据 → nginx（8093）。"""
import os
import secrets
import time

import paramiko

HOST = os.environ.get("LAN_HOST", "192.168.1.14")
PORT = int(os.environ.get("LAN_PORT", "22"))
USER = os.environ.get("LAN_USER", "zk")
PWD = os.environ["LAN_PASS"]
APP = "/opt/datagov"
JAR_LOCAL = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "backend", "target", "dgp-boot.jar")
DB_NAME = os.environ.get("DGP_DB_NAME", "dgp")
DB_USER = os.environ.get("DGP_DB_USER", "dgp")
DB_PASS = os.environ["DGP_DB_PASSWORD"]
APP_PORT = int(os.environ.get("DGP_APP_PORT", "8092"))
NGINX_PORT = int(os.environ.get("DGP_NGINX_PORT", "8093"))
RESET_DB = os.environ.get("DGP_RESET_DB", "1") == "1"


def main():
    if not os.path.exists(JAR_LOCAL):
        raise SystemExit("找不到 jar：" + JAR_LOCAL)

    c = paramiko.SSHClient()
    c.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    c.connect(HOST, PORT, USER, PWD, timeout=15, look_for_keys=False, allow_agent=False)

    def sh(cmd, sudo=True, timeout=900, show=True):
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
                print(out.strip()[-2500:])
            if err.strip():
                print("[stderr] " + err.strip()[-1200:])
            print("[exit %d]" % code)
        return code, out, err

    # 0) 可选：重置库，让应用首次启动自动建表 + 装载完整演示数据
    if RESET_DB:
        sh('mysql -e "DROP DATABASE IF EXISTS `%s`; CREATE DATABASE `%s` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci; '
           "GRANT ALL PRIVILEGES ON `%s`.* TO '%s'@'127.0.0.1'; FLUSH PRIVILEGES;\"" % (DB_NAME, DB_NAME, DB_NAME, DB_USER))

    # 1) 上传 jar（先传临时文件再原子替换，避免服务运行中写坏）
    print("上传 jar（%.1f MB）..." % (os.path.getsize(JAR_LOCAL) / 1024 / 1024))
    sftp = c.open_sftp()
    t0 = time.time()
    sftp.put(JAR_LOCAL, APP + "/dgp-boot.jar.new")
    sftp.close()
    print("  上传完成 %.1fs" % (time.time() - t0))

    # 2) 环境变量文件（600）：密钥现场生成，不回显
    jwt = secrets.token_urlsafe(48)[:64]
    mask = secrets.token_urlsafe(32)[:48]
    env = """# DGP 数据治理平台运行环境（由部署脚本生成，权限 600）
SPRING_PROFILES_ACTIVE=mysql
DGP_DB_HOST=127.0.0.1
DGP_DB_PORT=3306
DGP_DB_NAME={db}
DGP_DB_USER={user}
DGP_DB_PASSWORD={pwd}
DGP_JWT_SECRET={jwt}
DGP_MASK_KEY={mask}
DGP_CORS_ORIGINS=http://{host}:{ngx},http://{host}:{app},http://{host}
DGP_JAVA_OPTS_PLACEHOLDER
JAVA_OPTS=-Xms1g -Xmx2g -XX:+UseG1GC -XX:MaxGCPauseMillis=200 -Dfile.encoding=UTF-8
SERVER_TOMCAT_THREADS_MAX=500
SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE=32
""".format(db=DB_NAME, user=DB_USER, pwd=DB_PASS, jwt=jwt, mask=mask,
           host=HOST, ngx=NGINX_PORT, app=APP_PORT).replace("DGP_JAVA_OPTS_PLACEHOLDER\n", "")
    sftp = c.open_sftp()
    with sftp.open("/tmp/datagov.env", "w") as f:
        f.write(env)
    sftp.close()
    sh("install -m 600 -o %s -g %s /tmp/datagov.env %s/datagov.env && rm -f /tmp/datagov.env && ls -l %s/datagov.env" % (USER, USER, APP, APP))

    # 3) systemd unit（以 zk 用户运行，监听 8092）
    unit = """[Unit]
Description=DGP Data Governance Platform (LAN test)
After=network.target mysql.service

[Service]
Type=simple
User={user}
Group={user}
WorkingDirectory={app}
EnvironmentFile={app}/datagov.env
ExecStart=/usr/bin/java $JAVA_OPTS -jar {app}/dgp-boot.jar --server.port={port}
SuccessExitStatus=143
Restart=on-failure
RestartSec=5
LimitNOFILE=65535
StandardOutput=append:{app}/logs/app.log
StandardError=append:{app}/logs/app.err.log

[Install]
WantedBy=multi-user.target
""".format(user=USER, app=APP, port=APP_PORT)
    sftp = c.open_sftp()
    with sftp.open("/tmp/datagov.service", "w") as f:
        f.write(unit)
    sftp.close()
    sh("mv -f %s/dgp-boot.jar.new %s/dgp-boot.jar 2>/dev/null || true" % (APP, APP))
    sh("install -m 644 /tmp/datagov.service /etc/systemd/system/datagov.service && systemctl daemon-reload && systemctl enable datagov >/dev/null 2>&1; systemctl restart datagov && sleep 2 && systemctl is-active datagov")

    # 4) 健康检查（首次启动会建表 + 装载演示数据，留足时间）
    print("\n等待启动与演示数据装载 ...")
    ok = False
    for i in range(90):
        code, out, err = sh("curl -s -m 3 http://127.0.0.1:%d/api/v1/admin/ping" % APP_PORT, sudo=False, show=False)
        if '"UP"' in out:
            print("  健康检查通过（%ds）：%s" % (i * 2, out.strip()[:160]))
            ok = True
            break
        time.sleep(2)
    if not ok:
        sh("tail -30 %s/logs/app.err.log; tail -20 %s/logs/app.log" % (APP, APP), sudo=False)
        raise SystemExit("服务未就绪")

    # 5) 等待演示数据装载完成（md_table 有数据）
    for i in range(60):
        code, out, err = sh('mysql -N %s -e "SELECT COUNT(*) FROM md_table"' % DB_NAME, show=False)
        try:
            n = int(out.strip() or 0)
        except ValueError:
            n = 0
        if n >= 13:
            print("  演示数据就绪：md_table=%d" % n)
            break
        time.sleep(2)
    else:
        print("  !! 演示数据未就绪，请检查日志")

    # 6) 提取静态资源 + 预压缩 + nginx 配置
    sh("rm -rf %s/static && mkdir -p /tmp/dgpstatic && cd /tmp/dgpstatic && "
       "unzip -q -o %s/dgp-boot.jar 'BOOT-INF/classes/static/*' && cp -r BOOT-INF/classes/static %s/static && "
       "rm -rf /tmp/dgpstatic && du -sh %s/static" % (APP, APP, APP, APP))
    sh("cd %s/static && find . -type f \\( -name '*.js' -o -name '*.css' -o -name '*.svg' -o -name '*.html' \\) "
       "| xargs -r gzip -9 -k -f && echo -n 'gz: ' && find . -name '*.gz' | wc -l" % APP)

    nginx_conf = """# DGP 数据治理平台（局域网测试）：nginx 托管静态资源 + 反代 API
upstream dgp_backend_lan {{
    server 127.0.0.1:{port};
    keepalive 32;
}}

server {{
    listen {ngx};
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

    location /assets/ {{
        expires 365d;
        add_header Cache-Control "public, immutable";
        access_log off;
        try_files $uri =404;
    }}

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
""".format(port=APP_PORT, ngx=NGINX_PORT, app=APP)
    sftp = c.open_sftp()
    with sftp.open("/tmp/dgp-lan.conf", "w") as f:
        f.write(nginx_conf)
    sftp.close()
    sh("install -m 644 /tmp/dgp-lan.conf /etc/nginx/conf.d/dgp-lan.conf && rm -f /tmp/dgp-lan.conf")
    sh("rm -f /etc/nginx/sites-enabled/default")
    sh("nginx -t 2>&1 | tail -2 && systemctl enable --now nginx >/dev/null 2>&1; systemctl reload nginx && systemctl is-active nginx")
    sh("ss -lntp | grep -E ':(%d|%d) ' || true" % (APP_PORT, NGINX_PORT))
    sh("curl -s -o /dev/null -w 'index: %%{http_code}\\n' http://127.0.0.1:%d/" % NGINX_PORT)
    sh("curl -s -o /dev/null -w 'api: %%{http_code}\\n' http://127.0.0.1:%d/api/v1/admin/ping" % NGINX_PORT)

    c.close()
    print("\n步骤3 完成：http://%s:%d" % (HOST, NGINX_PORT))


if __name__ == "__main__":
    main()
