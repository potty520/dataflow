#!/bin/bash
# 在局域网 4C8G 机器上准备 v1.4 验证环境：明文 FTP(vsftpd) + SOCKS5 代理（用于“不同网络”场景模拟）
set -e

echo "=== 1. 安装 vsftpd ==="
if ! command -v vsftpd >/dev/null 2>&1; then
  export DEBIAN_FRONTEND=noninteractive
  apt-get update -qq
  apt-get install -y -qq vsftpd
fi
vsftpd -v 2>&1 | head -1 || true

echo "=== 2. 配置 vsftpd（本地用户 + 被动模式 + chroot） ==="
cp -n /etc/vsftpd.conf /etc/vsftpd.conf.bak 2>/dev/null || true
cat > /etc/vsftpd.conf <<'EOF'
listen=YES
listen_ipv6=NO
anonymous_enable=NO
local_enable=YES
write_enable=YES
local_umask=022
dirmessage_enable=YES
use_localtime=YES
xferlog_enable=YES
connect_from_port_20=YES
chroot_local_user=YES
allow_writeable_chroot=YES
pam_service_name=vsftpd
secure_chroot_dir=/var/run/vsftpd/empty
pasv_enable=YES
pasv_min_port=30000
pasv_max_port=30010
pasv_address=127.0.0.1
local_root=/srv/dgpftp
EOF

echo "=== 3. 创建 FTP 用户与目录 ==="
id dgpftp >/dev/null 2>&1 || useradd -m -d /srv/dgpftp -s /usr/sbin/nologin dgpftp
echo 'dgpftp:DgpFtp2026!' | chpasswd
mkdir -p /srv/dgpftp/upload
chown -R dgpftp:dgpftp /srv/dgpftp
chmod 755 /srv/dgpftp /srv/dgpftp/upload
systemctl enable vsftpd >/dev/null 2>&1
systemctl restart vsftpd
sleep 1
systemctl is-active vsftpd

echo "=== 4. 启动 SOCKS5 代理（127.0.0.1:1080，模拟跨网代理） ==="
mkdir -p /opt/datagov/tools
cat > /opt/datagov/tools/socks5.py <<'EOF'
#!/usr/bin/env python3
"""最小 SOCKS5 服务器（无认证，CONNECT，支持 IPv4/域名），仅用于验证代理链路。"""
import socket, struct, threading, sys

def pipe(a, b):
    try:
        while True:
            d = a.recv(65536)
            if not d:
                break
            b.sendall(d)
    except Exception:
        pass
    finally:
        try: a.close()
        except Exception: pass
        try: b.close()
        except Exception: pass

def handle(c):
    try:
        c.recv(2)
        nm = c.recv(1)[0]
        c.recv(nm)
        c.sendall(b"\x05\x00")
        hdr = c.recv(4)
        atyp = hdr[3]
        if atyp == 1:
            host = socket.inet_ntoa(c.recv(4))
        elif atyp == 3:
            n = c.recv(1)[0]
            host = c.recv(n).decode()
        else:
            port = struct.unpack("!H", c.recv(2))[0]
            host = socket.inet_ntop(socket.AF_INET6, c.recv(16))
        port = struct.unpack("!H", c.recv(2))[0]
        try:
            r = socket.create_connection((host, port), timeout=10)
        except Exception:
            c.sendall(b"\x05\x05\x00\x01" + b"\x00" * 6)
            c.close()
            return
        c.sendall(b"\x05\x00\x00\x01" + b"\x00" * 6)
        t = threading.Thread(target=pipe, args=(c, r), daemon=True)
        t.start()
        pipe(r, c)
    except Exception:
        try: c.close()
        except Exception: pass

def main():
    port = int(sys.argv[1]) if len(sys.argv) > 1 else 1080
    s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    s.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    s.bind(("127.0.0.1", port))
    s.listen(100)
    print("socks5 listening on 127.0.0.1:%d" % port, flush=True)
    while True:
        c, _ = s.accept()
        threading.Thread(target=handle, args=(c,), daemon=True).start()

main()
EOF
pkill -f "tools/socks5.py" 2>/dev/null || true
nohup python3 /opt/datagov/tools/socks5.py 1080 > /opt/datagov/tools/socks5.log 2>&1 &
sleep 1
ss -lntp | grep ':1080 ' || echo "SOCKS5 未监听"

echo "=== 5. 连通性自检（FTP + SOCKS5） ==="
python3 - <<'PY'
import ftplib
try:
    f = ftplib.FTP()
    f.connect("127.0.0.1", 21, timeout=10)
    f.login("dgpftp", "DgpFtp2026!")
    print("FTP login OK, dir:", f.pwd())
    import io
    f.storbinary("STOR /upload/probe.txt", io.BytesIO(b"dgp-ftp-probe"))
    print("FTP upload OK")
    f.quit()
except Exception as e:
    print("FTP 自检失败:", e)

import socket, struct
try:
    s = socket.create_connection(("127.0.0.1", 1080), timeout=5)
    s.sendall(b"\x05\x01\x00")
    print("SOCKS5 handshake:", s.recv(2).hex())
    host = b"127.0.0.1"
    s.sendall(b"\x05\x01\x00\x01" + socket.inet_aton("127.0.0.1") + struct.pack("!H", 3306))
    print("SOCKS5 connect mysql:", s.recv(10).hex())
    s.close()
except Exception as e:
    print("SOCKS5 自检失败:", e)
PY
echo "=== 完成 ==="
