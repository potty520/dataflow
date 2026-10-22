# 功能说明：FTP 文件迁移 + 跨网络数据迁移（v1.4）

> 回答两个现实问题：**库在不同网络怎么迁**（代理/隧道）、**能不能走 FTP 摆渡**（文件通道）。
> 验收脚本：`verify/flows_migration_v14.ps1`（29 项断言，本次实测全部通过）。

---

## 一、能力总览

| 能力 | 实现方式 | 适用场景 |
|---|---|---|
| **FTP 文件迁移** | 表 → **CSV / TXT**（可 gzip）→ 上传 FTP；或 FTP 下载 → 批量写入目标表 | 两侧网络互不可达、需经文件服务器摆渡；老系统只提供 FTP |
| **跨网络数据库迁移** | 远程数据源 + 本地端口转发隧道：直连 / **SOCKS5** / **HTTP CONNECT** 代理 → JDBC 连 `127.0.0.1:随机端口` | 远端 MySQL/PostgreSQL 在另一网段，只允许经代理访问 |
| **跨库迁移执行** | 源连接流式读 → 目标连接批量写（500/批），支持全量/增量、分批、断点续传、回滚 | 远程库 → 本地，或本地 → 远程，或远程↔远程（经本地中转） |
| 凭据安全 | 密码用 `DGP_MASK_KEY` 派生的 AES-256-GCM 加密存储（`enc:` 前缀），接口只回显掩码 | 合规要求不在库中存明文口令 |

当前限制：仅支持 **明文 FTP**（你确认的选择）；跨库 JDBC 支持 MySQL / PostgreSQL；代理支持 SOCKS5 与 HTTP CONNECT，均支持账号口令认证。

---

## 二、FTP 文件迁移

### 2.1 数据模型

| 表 | 说明 |
|---|---|
| `mig_file_task` | 任务：方向（EXPORT/IMPORT）、可选迁移项目、源/目标表、远端 FTP（主机/端口/账号/加密口令/目录/文件名）、代理配置、是否 gzip、状态 |
| `mig_file_run` | 批次：方向、状态、起止时间、行数、字节数、**SHA-256**、说明、执行人 |

### 2.2 流程

```
导出：源表(本地或远程数据源) --SELECT--> 本地临时 CSV(gzip?) --FTP STOR--> 远端文件
导入：远端文件 --FTP RETR--> 本地临时 CSV(gzip?) --解析表头--> INSERT 批量(500/批) --> 目标表
```

- **文件格式三选一**：`CSV`（逗号+引号）、`TXT`（制表符 `\t` 分隔）、`TXT_PIPE`（竖线 `|` 分隔）；
  TXT 采用反斜杠转义（`\\`、`\t`、`\n`、`\r`），首行为表头，空值写空串、导入时空串转 NULL；
- CSV 用 UTF-8，处理引号/逗号/换行（RFC4180 子集）；
- 若任务绑定了**迁移项目**，导出列名取项目映射的目标列名、取数表达式复用映射（DIRECT/EXPR/CONST/DEFAULT），因此导出文件可原样导入同项目目标表；
- 每次执行记录行数、字节数、SHA-256，便于对账与防篡改校验。

### 2.3 接口

| 方法 | 路径 | 权限 | 说明 |
|---|---|---|---|
| GET/POST | `/api/v1/migration/files/tasks` | migration:file:view/edit | 任务列表/新建 |
| GET/PUT/DELETE | `/api/v1/migration/files/tasks/{id}` | view/edit | 详情（含最近 10 次记录）/更新/删除 |
| POST | `/api/v1/migration/files/tasks/{id}/run` | migration:file:exec | 执行导出或导入 |
| GET | `/api/v1/migration/files/tasks/{id}/runs` | view | 执行记录 |
| POST | `/api/v1/migration/files/test` | view | FTP 连通性测试（不保存配置） |

### 2.4 使用步骤

1. 数据集成 → **远程与文件迁移** → FTP 文件迁移 → 新建任务；
2. 选方向、迁移项目（可选）、FTP 地址/账号/目录/文件名，需要跨网时选代理并填代理地址；
3. 保存后点「执行」，在「记录」里看行数/字节/SHA-256 与错误信息。

---

## 三、跨网络数据库迁移

### 3.1 原理：为什么需要本地端口转发

JDBC 驱动与 FTP 客户端都不支持 SOCKS/HTTP 代理，标准做法是先建一条「本地端口 → 代理 → 远端 host:port」的隧道，再让驱动连 `127.0.0.1:本地随机端口`：

```
应用 ──JDBC──> 127.0.0.1:34567 ──SOCKS5/HTTP CONNECT──> 代理 ──> 远端 MySQL:3306
```

实现在 `ProxyTunnel`（零外部依赖，手工实现 SOCKS5 RFC1928 与 HTTP CONNECT 握手，支持账号口令认证），FTP 侧通过自定义 `SocketFactory` 让控制连接与数据连接都走同一条隧道。

### 3.2 数据模型

| 表 | 说明 |
|---|---|
| `mig_datasource` | 远程数据源：类型（MYSQL/POSTGRESQL）、主机/端口/库/账号/加密口令、代理类型（DIRECT/SOCKS5/HTTP）与代理主机/端口/账号/加密口令、最近测试结果 |
| `mig_project.source_ds_id / target_ds_id` | 迁移项目可分别指定源/目标数据源，`0` 表示本库 |

### 3.3 接口

| 方法 | 路径 | 权限 | 说明 |
|---|---|---|---|
| GET/POST | `/api/v1/migration/datasources` | migration:ds:view/edit | 列表/新建 |
| GET/PUT/DELETE | `/api/v1/migration/datasources/{id}` | view/edit | 详情/更新（密码留空表示不改）/删除 |
| POST | `/api/v1/migration/datasources/{id}/test` | migration:ds:test | 连接测试（返回耗时与数据库版本） |
| POST | `/api/v1/migration/datasources/test` | migration:ds:test | 未保存配置的临时测试 |
| GET | `/api/v1/migration/datasources/{id}/columns?table=` | view | 远程表字段探测（MySQL/PG 兼容） |

迁移项目接口不变，只是 `POST/PUT /migration/projects` 多接受 `sourceDsId`、`targetDsId`。

### 3.4 跨库执行流程

1. 新建远程数据源并「测试连接」（可先用临时测试验证代理是否通）；
2. 新建迁移项目，源数据源选远程、目标留空（本库）或选另一个远程；
3. 「自动推荐映射」会读取**远程表的真实字段**做匹配；「预检」样例也来自远程；
4. 目标表不存在时「一键建表」，然后「执行」：
   - 全量：源连接 `SELECT` 游标读取 → 目标连接 `INSERT` 批量提交（500/批）；
   - 增量：按水位字段分片（`batchSize`）循环读取写入，每批推进项目 `last_watermark`，中断后可续传；
   - 失败且项目开启「失败自动回滚」：按目标端水位列删除本批次写入，记录 `ROLLED_BACK`；
5. 「对账」与「回滚」对跨库批次同样可用（回滚要求映射里包含水位字段）。

### 3.5 实测数据（4C8G 局域网）

| 场景 | 结果 |
|---|---|
| 远程数据源直连测试 | 13 ms，版本 8.0.46 |
| 远程数据源经 SOCKS5 测试 | 131 ms |
| 跨库全量迁移（6000 行，经直连） | 写入 6000 / 源 6000，耗时秒级 |
| 跨库全量迁移（6000 行，经 SOCKS5 代理） | 写入 6000 / 源 6000 |
| FTP 导出（6000 行，CSV） | 139,646 字节，SHA-256 记录，秒级 |
| FTP 导出（6000 行，TXT 制表符） | 139,646 字节，`cid<TAB>cname<TAB>clevel` 表头，导入 6000 行一致 |
| FTP 导出/导入（TXT 竖线 \|） | 6000 行往返一致 |
| FTP 导入（6000 行） | 导入 6000，与导出一致 |
| FTP 经 SOCKS5 代理导出 | 6000 行成功 |
| 分析师越权 | 数据源/文件迁移均 403 |

---

## 四、部署与验证

### 4.1 依赖

`commons-net`（3.11.1，FTP 客户端）、MySQL Connector/J、PostgreSQL 驱动，均已在 `backend/pom.xml` 声明，`mvn package` 自动拉取。

已有环境升级（v1.3 → v1.4）：

```bash
mysql -u root -p dgp < sql/upgrade-v1.3-to-v1.4.sql
```

### 4.2 搭建验证环境（可选，仅测试机）

```bash
sudo bash deploy/cmds/lan_setup_ftp_proxy.sh
```

脚本会安装并配置 vsftpd（本地用户 `dgpftp` / 目录 `/srv/dgpftp/upload`、被动模式 30000-30010），并启动一个最小 SOCKS5 代理（`127.0.0.1:1080`）用于模拟「不同网络」。

### 4.3 验收脚本

```powershell
$env:DGP_DB_PASSWORD="<本机 dgp 库口令>"
$env:DGP_FTP_PASS="<FTP 口令>"
$env:DGP_FTP_HOST="127.0.0.1"
$env:DGP_SOCKS5_HOST="127.0.0.1"; $env:DGP_SOCKS5_PORT="1080"
.\verify\flows_migration_v14.ps1 -Base http://127.0.0.1:8080
```

不设置环境变量时，相关段落自动 SKIP，不影响其他环境跑通。

覆盖：远程数据源 CRUD/测试/字段探测（含错误口令 4xx）、SOCKS5 代理连接、跨库推荐/预检/建表/执行/行数一致/全量回滚 400、经代理跨库执行、FTP 导出（校验和）/导入（行数一致）/经代理导出、**TXT（制表符）与 TXT_PIPE（竖线）导出导入往返**、执行记录、权限 403（用临时分析师账号，避免演示账号状态干扰）。

---

## 五、安全与已知限制

- **FTP 是明文协议**：建议仅在内网或经 VPN/代理的可信链路上使用；跨公网请改用 SFTP/FTPS（Roadmap）或先把 CSV 用 gzip 之外的方式加密。
- 口令以 AES-256-GCM 加密存储（`enc:` 前缀，密钥取自 `DGP_MASK_KEY`）；接口不返回密文/明文，编辑时留空表示不修改。
- 跨库迁移为「读到本地再写」，受应用侧内存与网络带宽限制；建议大表用增量模式 + 合理 `batchSize`（默认 5000，实测 6000 行分 2 批）。
- 回滚依赖目标表包含映射后的水位字段；全量批次无起始水位，不支持回滚（返回 400 并说明原因）。
- 代理仅覆盖应用发起的 JDBC/FTP 出站流量；平台自身数据库连接仍由 `DGP_DB_*` 配置。

## 六、Roadmap

1. SFTP / FTPS / S3 兼容对象存储；
2. 文件加密（AES-GCM）与分片断点续传；
3. 迁移限速、失败重试策略、抽样逐行差异报告；
4. 代理健康检查与自动重连、连接池复用。
