# -*- coding: utf-8 -*-
"""清理仓库：基准脚本改为从环境变量读取口令（不提交内网口令），删除临时提交信息文件。"""
import io
import os

ROOT = r"C:\Users\Administrator\Documents\1\datagov"
p = os.path.join(ROOT, "deploy", "cmds", "lan_bench_migration.sh")
t = io.open(p, encoding="utf-8").read()

# 顶部增加环境变量
if "DGP_DB_PASSWORD" not in t:
    t = t.replace(
        'RUNID=$$',
        'RUNID=$$\n# 凭据从环境变量读取（脚本本身不保存任何口令）\nDS_DB_PASS=${DGP_DB_PASSWORD:?请设置 DGP_DB_PASSWORD}\nFTP_PASS=${DGP_FTP_PASS:?请设置 DGP_FTP_PASS}\nFTP_HOST=${DGP_FTP_HOST:-127.0.0.1}\nFTP_USER=${DGP_FTP_USER:-dgpftp}')
    t = t.replace('"password":"DgpLan2026Test"', '"password":"'"$DS_DB_PASS"'"')
    t = t.replace('\\"remotePassword\\":\\"DgpFtp2026!\\"', '\\"remotePassword\\":\\"'"$FTP_PASS"'\\"')
    t = t.replace('"remoteHost":"127.0.0.1"', '"remoteHost":"'"$FTP_HOST"'"')
    t = t.replace('\\"remoteHost\\":\\"127.0.0.1\\"', '\\"remoteHost\\":\\"'"$FTP_HOST"'\\"')
    t = t.replace('\\"remoteUser\\":\\"dgpftp\\"', '\\"remoteUser\\":\\"'"$FTP_USER"'\\"')
    io.open(p, "w", encoding="utf-8", newline="\n").write(t)
    print("bench 脚本已改为环境变量凭据")
else:
    print("已处理")

# 确认无硬编码口令
bad = []
for root, dirs, files in os.walk(ROOT):
    if any(x in root for x in (".git", "node_modules", "target", "data", "screenshots")):
        continue
    for f in files:
        if f.endswith((".sh", ".ps1", ".py", ".md", ".java", ".sql", ".txt")):
            fp = os.path.join(root, f)
            try:
                s = io.open(fp, encoding="utf-8", errors="ignore").read()
            except Exception:
                continue
            for secret in ("DgpLan2026Test", "DgpFtp2026", "zkzj102426"):
                if secret in s:
                    bad.append((os.path.relpath(fp, ROOT), secret))
print("残留敏感串:", bad or "无")

# 删除临时提交信息文件
cm = os.path.join(ROOT, "deploy", "cmds", "commit-msg-bench.txt")
if os.path.exists(cm):
    os.remove(cm)
    print("已删除 commit-msg-bench.txt")
