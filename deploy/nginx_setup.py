#!/usr/bin/env python3
"""配置 nginx 反向代理：静态资源由 nginx 直接托管（sendfile + gzip + 强缓存），API 代理到 8092。

- 从 jar 中解出 static 到 /opt/datagov/static
- 写入 /etc/nginx/conf.d/dgp.conf（监听 8093，不影响服务器上已有站点）
- nginx -t 通过后 reload
凭据：DGP_SSH_*
"""
import os
import stat

import paramiko

HOST = os.environ.get("DGP_SSH_HOST", "127.0.0.1")
PORT = int(os.environ.get("DGP_SSH_PORT", "23195"))
USER = os.environ.get("DGP_SSH_USER", "root")
PWD = os.environ["DGP_SSH_PASS"]
APP_DIR = "/opt/datagov"
STATIC_DIR = APP_DIR + "/static"
NGINX_PORT = os.environ.get("DGP_NGINX_PORT", "8093")
UPSTREAM = os.environ.get("DGP_UPSTREAM", "127.0.0.1:8092")

CONF = """# DGP 数据治理平台：静态资源由 nginx 托管，API 反向代理到后端
upstream dgp_backend {{
    server {upstream};
    keepalive 64;
}}

server {{
    listen {port};
    server_name _;

    root {static_dir};
    index index.html;

    # 公网链路上部分代理/网关对 chunked 处理不规范（浏览器 ERR_INVALID_CHUNKED_ENCODING）；
    # 关闭分块传输后 nginx 会用 Content-Length 发送（与后端 ContentLengthFilter 的双保险）
    chunked_transfer_encoding off;
    keepalive_timeout 30s;

    # 压缩：只压缩本地静态文件（deploy 时预生成 .gz，nginx 直接发预压缩文件，几乎不耗 CPU）
    # 代理响应（API JSON）不压缩：1 核机器上实时压缩会显著降低并发能力
    gzip on;
    gzip_static on;
    gzip_comp_level 4;
    gzip_min_length 1024;
    gzip_proxied off;
    gzip_types text/plain text/css application/javascript application/json application/xml image/svg+xml;

    # 带内容指纹的构建产物：强缓存一年
    location /assets/ {{
        expires 365d;
        add_header Cache-Control "public, immutable";
        access_log off;
        try_files $uri =404;
    }}

    location = /index.html {{
        add_header Cache-Control "no-cache, must-revalidate";
    }}

    location / {{
        try_files $uri $uri/ /index.html;
    }}

    location /api/ {{
        proxy_pass http://dgp_backend;
        proxy_http_version 1.1;
        proxy_set_header Connection "";
        proxy_set_header Host $host;
        # 同源部署：剥离浏览器 Origin，避免后端 CORS 白名单与代理端口不一致导致 "Invalid CORS request"
        proxy_set_header Origin "";
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_connect_timeout 5s;
        proxy_read_timeout 120s;
        proxy_buffering on;
    }}

    location /open/ {{
        proxy_pass http://dgp_backend;
        proxy_http_version 1.1;
        proxy_set_header Connection "";
        proxy_set_header Host $host;
        proxy_set_header Origin "";
        proxy_set_header X-Real-IP $remote_addr;
        proxy_read_timeout 60s;
    }}

    access_log /var/log/nginx/dgp-access.log;
    error_log  /var/log/nginx/dgp-error.log warn;
}}
"""


def main():
    c = paramiko.SSHClient()
    c.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    c.connect(HOST, PORT, USER, PWD, timeout=20, look_for_keys=False, allow_agent=False)

    def sh(cmd, check=True, timeout=300):
        stdin, stdout, stderr = c.exec_command(cmd, timeout=timeout)
        out = stdout.read().decode("utf-8", "replace")
        err = stderr.read().decode("utf-8", "replace")
        code = stdout.channel.recv_exit_status()
        print("$ " + cmd)
        if out.strip():
            print(out.rstrip())
        if err.strip():
            print("[stderr] " + err.strip())
        print("")
        if check and code != 0:
            raise SystemExit("failed (%d): %s" % (code, cmd))
        return out

    sh("which nginx || (apt-get update -qq && apt-get install -y -qq nginx)", check=False)
    sh("nginx -v 2>&1")

    # 从 jar 解出静态资源
    sh("rm -rf %s && mkdir -p /tmp/dgpstatic && cd /tmp/dgpstatic && "
       "unzip -q -o %s/dgp-boot.jar 'BOOT-INF/classes/static/*' && "
       "cp -r BOOT-INF/classes/static %s && rm -rf /tmp/dgpstatic && ls %s | head"
       % (STATIC_DIR, APP_DIR, STATIC_DIR, STATIC_DIR))
    # 预压缩静态资源：nginx gzip_static 直接发送 .gz，避免 1 核 CPU 实时压缩
    sh(r"""cd %s && find . -type f -name '*.js' -o -type f -name '*.css' -o -type f -name '*.svg' -o -type f -name '*.html' | xargs -r gzip -9 -k -f; echo precompressed""" % STATIC_DIR)
    sh("du -sh %s && echo -n 'gz files: ' && find %s -name '*.gz' | wc -l" % (STATIC_DIR, STATIC_DIR))
    sftp = c.open_sftp()
    with sftp.open("/etc/nginx/conf.d/dgp.conf", "w") as f:
        f.write(CONF.format(upstream=UPSTREAM, port=NGINX_PORT, static_dir=STATIC_DIR))
    sftp.chmod("/etc/nginx/conf.d/dgp.conf", stat.S_IRUSR | stat.S_IWUSR | stat.S_IRGRP | stat.S_IROTH)
    sftp.close()

    sh("nginx -t")
    sh("systemctl reload nginx && sleep 1 && systemctl is-active nginx")
    sh("ss -lntp | grep ':%s ' || true" % NGINX_PORT)
    sh("curl -s -o /dev/null -w 'index via nginx: %%{http_code} size=%%{size_download}\\n' http://127.0.0.1:%s/" % NGINX_PORT)
    sh("curl -s -o /dev/null -w 'api via nginx: %%{http_code}\\n' http://127.0.0.1:%s/api/v1/admin/ping" % NGINX_PORT)
    c.close()
    print("nginx 配置完成：http://%s:%s" % (HOST, NGINX_PORT))


if __name__ == "__main__":
    main()
