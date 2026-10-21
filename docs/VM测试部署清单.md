# 4C8G 测试机部署与压测清单

> 目标：在一台本地 **4 核 / 8G / 256G** 虚拟机上完成「部署 → 功能验收 → 并发压测」全流程。
> 这台配置对应评估中的 **500~1000 并发用户**档位（1 核实测约 2.3k QPS 看板，4 核预期 3.5~4 倍）。

## 一、虚拟机建议

| 项 | 建议 |
|---|---|
| 系统 | Ubuntu 22.04 LTS（与线上测试机一致，脚本已验证） |
| CPU / 内存 / 磁盘 | 4 vCPU / 8 GB / 256 GB |
| 网络 | **桥接模式**（有独立 IP，便于从开发机 SSH 访问）；或在 NAT 下做好 22 / 8093 端口转发 |
| 依赖 | 需要能访问 apt 源（安装 JDK/MySQL/nginx）；**不要求**装 Maven，jar 在开发机构建后上传 |
| 开放端口 | 22（SSH）、8093（nginx 入口）、8092（可选，直连后端排障） |

## 二、执行步骤

### 步骤 1：虚拟机上准备环境（一键）

```bash
cd /path/to/datagov          # 仓库目录（可 git clone，或从开发机 scp 上传）
sudo bash deploy/vm_bootstrap.sh
```

脚本会完成：安装 JDK17 / MySQL8 / nginx / wrk / ab → MySQL 参数按 4C8G 调优（buffer pool 1G、max_connections 300）→ 建库建账号 → 导入 `sql/schema-mysql.sql` + `sql/init-data-mysql.sql` → 准备 `/opt/datagov` → 输出数据库口令与下一步命令。

> 不导入初始化数据时加 `DGP_SKIP_SQL=1`，应用首次启动会自动建表并装载完整演示数据（约 20~60s）。

### 步骤 2：开发机（Windows）构建并推送 jar

```powershell
cd backend
$env:JAVA_HOME="<本机 JDK17>"
mvn -B -DskipTests clean package

$env:DGP_SSH_HOST="<虚拟机IP>"; $env:DGP_SSH_PORT="22"; $env:DGP_SSH_USER="root"; $env:DGP_SSH_PASS="***"
$env:DGP_DB_NAME="dgp"; $env:DGP_DB_USER="dgp"; $env:DGP_DB_PASSWORD="<bootstrap 输出的口令>"
$env:DGP_JAVA_OPTS="-Xms1g -Xmx2g -XX:+UseG1GC -Dfile.encoding=UTF-8"
$env:DGP_DB_POOL="32"            # → SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE
$env:DGP_TOMCAT_THREADS="500"    # → SERVER_TOMCAT_THREADS_MAX
$env:DGP_JWT_SECRET="<48 位随机串>"; $env:DGP_MASK_KEY="<40 位随机串>"
python deploy/remote.py deploy backend/target/dgp-boot.jar 8092
```

`remote.py` 会：上传 jar → 写 systemd unit 与 `/opt/datagov/datagov.env`（600）→ 重启服务 → 健康检查 → 同步 nginx 静态资源（含预压缩 .gz）。

### 步骤 3：配置 nginx 入口（8093）

```powershell
$env:DGP_NGINX_PORT="8093"
python deploy/nginx_setup.py
```

### 步骤 4：功能验收

```powershell
.\verify.ps1 -Base "http://<虚拟机IP>:8093"                      # 73 项冒烟
.\verify\api_matrix.ps1 -Base "http://<虚拟机IP>:8093"           # 123 GET × 6 角色鉴权矩阵
.\verify\flows_core.ps1 -Base "http://<虚拟机IP>:8093"           # A-D 70 项
.\verify\flows_ext.ps1  -Base "http://<虚拟机IP>:8093"           # E-H 70 项
.\verify\flows_rest.ps1 -Base "http://<虚拟机IP>:8093"           # I-N 81 项
```

期望：**294 项全部通过**；浏览器登录 6 个账号，各菜单页无报错。

### 步骤 5：并发压测（在虚拟机上执行，排除网络干扰）

```bash
bash deploy/cmds/loadtest.sh          # 6 类接口 × 并发 10/50/100 + 登录吞吐
bash deploy/cmds/loadtest-nginx.sh    # 静态资源 nginx vs Tomcat、API 直连 vs 反代
bash deploy/cmds/loadtest-final.sh    # 并发 200 极限档 + 登录
```

`wrk`/`ab` 已由 bootstrap 安装；如需更高档位可自行调 `-c`（并发）。

## 三、4C8G 预期指标（1 核实测值 × 理论倍数）

| 场景 | 1 核实测 | 4C8G 预期 | 说明 |
|---|---|---|---|
| 治理概览看板（缓存命中） | 2324 QPS | **8k~9k QPS** | CPU 线性扩展 |
| 元数据列表 | 307 QPS | 1.1k~1.3k QPS | 每请求 4 条 SQL |
| 质量列表 | 142 QPS | 500~600 QPS | 每请求 6 条 SQL |
| 静态资源（nginx + gzip_static） | 2804 QPS | 10k+ QPS | 几乎不耗应用 CPU |
| 登录（PBKDF2 210k） | 2.8 次/秒 | **9~11 次/秒** | 单次约 360ms CPU |
| 混合场景承载 | ~200 并发用户 | **500~1000 并发用户** | p95 < 500ms 目标 |

> 以上为保守估算；实测后把数据补进 `docs/并发容量报告.md`（或新建 `docs/压测报告-<日期>.md`，用 `python deploy/publish_docs.py` 推送）。

## 四、4C8G 关键参数（已内置/可按需覆盖）

| 参数 | 值 | 位置 |
|---|---|---|
| JVM 堆 / GC | `-Xmx2g -XX:+UseG1GC` | 部署环境变量 `DGP_JAVA_OPTS` |
| Tomcat 线程 / 排队 / 连接 | 400 / 1000 / 10000 | `application.yml` |
| Hikari 连接池 | 32 | 环境变量 `DGP_DB_POOL`（默认 24） |
| MySQL buffer pool | 1G | bootstrap 写入 `/etc/mysql/mysql.conf.d/zz-dgp.cnf` |
| MySQL max_connections | 300 | 同上 |
| 聚合接口 TTL 缓存 | 8~20s | 代码 `TtlCache`（写操作即时失效） |
| nginx | 静态 gzip_static + 强缓存；API 反代 | `deploy/nginx_setup.py` |

> 8G 内存下建议 JVM 2G、MySQL 2G、其余留给系统与 nginx；若同时跑其它服务，把 JVM 降到 1.5G。

## 五、常见问题

| 现象 | 处理 |
|---|---|
| 启动日志提示缺少密钥 | 必须注入 `DGP_JWT_SECRET` / `DGP_MASK_KEY`（≥32 位随机串），否则拒绝启动 |
| 8093 打不开 | `ss -lntp \| grep 8093`、`nginx -t`、检查虚拟机防火墙/安全组 |
| 登录 429 | 同一用户 15 分钟内失败过多触发限流，换账号或等待 |
| 压测时 p99 抖动大 | 关闭开发机上的其它负载；确认压测在 VM 本机执行（排除公网 RTT） |
| 想清空演示数据重来 | `python deploy/setup_mysql.py --reset && systemctl restart datagov` |
| 需要 HTTPS | 在 `nginx_setup.py` 的 server 块加 443 + 证书，或前置一层反代 |
