#!/usr/bin/env python3
"""把 docs/ 下的报告（以及 README）发布到 GitHub 仓库。

背景：本机访问 github.com:443 时有不稳定的情况，而 api.github.com 通常可用。
因此脚本采用两条通道，自动降级，保证"报告一定能推上去"：

  1) 快通道：git add/commit + git push（3 次重试）
  2) 备用通道：GitHub REST API（Git Data）——直接创建 blob/tree/commit 并更新引用，
     只上传与远端内容不一致的文件（按 git blob sha 比对），不依赖 git push

用法：
    python deploy/publish_docs.py "docs: 新增 XX 报告"
    python deploy/publish_docs.py                 # 默认提交说明
    python deploy/publish_docs.py --dry-run       # 只列出将要上传的文件

凭据：复用本机 git 凭据管理器里 github.com 的 token（git credential fill），不落盘、不打印。
"""
import base64
import json
import os
import subprocess
import sys
import time
import urllib.error
import urllib.request

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
REPO = os.environ.get("DGP_REPO", "potty520/dataflow")
API = "https://api.github.com"
DEFAULT_MSG = "docs: 更新验收/测试报告"


def run(cmd, cwd=ROOT, stdin_text=None):
    # Windows 默认用 GBK 解码子进程输出，git 路径是 UTF-8，必须显式指定
    return subprocess.run(cmd, cwd=cwd, capture_output=True, text=True,
                          encoding="utf-8", errors="replace", input=stdin_text)


def git(*args, stdin_text=None):
    return run(["git", *args], stdin_text=stdin_text)


def token():
    p = run(["git", "credential", "fill"], stdin_text="protocol=https\nhost=github.com\n\n")
    for line in p.stdout.splitlines():
        if line.startswith("password="):
            return line.split("=", 1)[1].strip()
    raise SystemExit("未找到 GitHub 凭据（git credential fill 失败），请先登录 GitHub")


TOKEN = token()


def api(method, path, body=None):
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(API + path, data=data, method=method)
    req.add_header("Authorization", "Bearer " + TOKEN)
    req.add_header("User-Agent", "dgp-publish-docs")
    req.add_header("Accept", "application/vnd.github+json")
    if data:
        req.add_header("Content-Type", "application/json")
    try:
        with urllib.request.urlopen(req, timeout=60) as r:
            return json.loads(r.read().decode())
    except urllib.error.HTTPError as e:
        raise SystemExit("GitHub API %s %s 失败: %s %s" % (method, path, e.code, e.read().decode()[:300]))


# ---------------- 收集要发布的文件 ----------------
# 中文文件名必须关闭 git 的路径转义（core.quotepath=false），否则会得到 "\346\270\205..." 这样的串
PUBLISH_PATHS = ["README.md", "docs", "deploy/publish_docs.py"]


def tracked_docs():
    out = git("-c", "core.quotepath=false", "ls-files", *PUBLISH_PATHS)
    return [line.strip() for line in out.stdout.splitlines() if line.strip()]


def local_path(rel):
    return os.path.join(ROOT, rel.replace("/", os.sep))


def blob_sha(path):
    return git("hash-object", path).stdout.strip()


def remote_files_and_head():
    ref = api("GET", "/repos/%s/git/ref/heads/main" % REPO)
    head = ref["object"]["sha"]
    commit = api("GET", "/repos/%s/git/commits/%s" % (REPO, head))
    tree = api("GET", "/repos/%s/git/trees/%s?recursive=1" % (REPO, commit["tree"]["sha"]))
    files = {}
    for e in tree.get("tree", []):
        if e.get("type") == "blob":
            files[e["path"]] = e["sha"]
    return files, head, commit["message"].splitlines()[0]


def main():
    args = [a for a in sys.argv[1:] if not a.startswith("--")]
    dry = "--dry-run" in sys.argv
    msg = args[0] if args else DEFAULT_MSG

    files = tracked_docs()
    print("待发布文件数：%d（README + docs/** + 发布脚本）" % len(files))

    # 1) 本地提交（让工作区干净，便于 git push 与 API 比对）
    git("add", "-A", *PUBLISH_PATHS)
    dirty = git("status", "--porcelain").stdout.strip()
    if dirty:
        c = git("-c", "user.email=potty520@users.noreply.github.com", "-c", "user.name=potty520",
                "commit", "-m", msg)
        print("本地提交：" + (c.stdout.strip().splitlines()[0] if c.stdout.strip() else msg))
    else:
        print("工作区无新增改动")

    # 与远端按内容比对，找出需要上传的文件
    remote, head, head_msg = remote_files_and_head()
    changed = [(f, blob_sha(f)) for f in files if remote.get(f) != blob_sha(f)]
    print("远端 HEAD：%s %s" % (head[:7], head_msg))
    print("内容有差异、需要上传的文件：%d" % len(changed))
    for f, _ in changed:
        print("  - " + f)
    if dry or not changed:
        print("（dry-run 或无差异，结束）")
        return

    # 2) 快通道：git push
    for i in range(1, 4):
        p = git("push", "origin", "main")
        if p.returncode == 0:
            print("git push 成功（第 %d 次尝试）" % i)
            return
        print("git push 第 %d 次失败：%s" % (i, (p.stderr or p.stdout).strip().splitlines()[-1][:160]))
        time.sleep(5)

    # 3) 备用通道：GitHub REST API（Git Data）
    print("主站不可达，改用 GitHub API 提交 ...")
    blobs = []
    for f, _ in changed:
        with open(local_path(f), "rb") as fh:
            content = base64.b64encode(fh.read()).decode()
        b = api("POST", "/repos/%s/git/blobs" % REPO, {"content": content, "encoding": "base64"})
        blobs.append({"path": f, "mode": "100644", "type": "blob", "sha": b["sha"]})
    # 关键：必须带 base_tree，否则新树会覆盖掉仓库里其它所有文件（曾导致一次误删）
    head_commit = api("GET", "/repos/%s/git/commits/%s" % (REPO, head))
    tree = api("POST", "/repos/%s/git/trees" % REPO,
               {"base_tree": head_commit["tree"]["sha"], "tree": blobs})
    commit = api("POST", "/repos/%s/git/commits" % REPO, {"message": msg, "tree": tree["sha"], "parents": [head]})
    api("PATCH", "/repos/%s/git/refs/heads/main" % REPO, {"sha": commit["sha"], "force": False})
    print("API 提交成功：%s" % commit["sha"][:7])
    print("注意：本地 git 与远端提交号不同（内容一致），网络恢复后可 git fetch origin && git reset --hard origin/main 对齐")


if __name__ == "__main__":
    main()
