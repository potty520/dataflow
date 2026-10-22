#!/bin/bash
# 修复 FTP 登录：pam_shells 要求 shell 合法 / nologin 在 /etc/shells 中
set -e
echo "=== pam.d/vsftpd ==="
grep -v '^#' /etc/pam.d/vsftpd | sed '/^$/d' || true
echo "=== user shell ==="
getent passwd dgpftp
echo "=== 修复 ==="
usermod -s /bin/bash dgpftp
grep -q '/usr/sbin/nologin' /etc/shells || echo '/usr/sbin/nologin' >> /etc/shells
getent passwd dgpftp
echo "=== vsftpd 日志 ==="
tail -5 /var/log/vsftpd.log 2>/dev/null || journalctl -u vsftpd -n 10 --no-pager 2>/dev/null | tail -10
echo "=== 重启并自检 ==="
systemctl restart vsftpd
sleep 1
python3 - <<'PY'
import ftplib, io
try:
    f = ftplib.FTP()
    f.connect("127.0.0.1", 21, timeout=10)
    print("welcome:", f.getwelcome())
    print("login:", f.login("dgpftp", "DgpFtp2026!"))
    print("pwd:", f.pwd())
    f.storbinary("STOR /upload/probe.txt", io.BytesIO(b"dgp-ftp-probe-2"))
    print("upload OK; size:", f.size("/upload/probe.txt"))
    f.quit()
except Exception as e:
    print("FTP 自检失败:", repr(e))
PY
