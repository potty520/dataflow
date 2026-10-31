# DGP 部署与运维（多环境：日本服务器 / NAS）

> 更新：2026-10-31。适用版本：单 jar（Spring Boot 3.2 + Vue 3），支持 H2 / MySQL 8 / MariaDB 10.11。

## 1. 环境一览

| 环境 | 地址 | 应用目录 | 数据库 | 服务 | 备注 |
|---|---|---|---|---|---|
| 日本服务器 | http://103.85.25.169:8092 | `/opt/datagov` | MySQL 8，库 `dgp_new`，127.0.0.1:3306 | `datagov.service` | 2GB 内存，首次登录约 9 秒 |
| NAS | http://192.168.1.41:8092 | `/home/wnvf0rest/dgp` | MariaDB 10.11，库 `dgp`，127.0.0.1:**3307** | `dgp.service` | 3306 被 Podman 端口映射占用 |

演示账号：`admin / Dgp@2026`。

## 2. 日本服务器（MySQL 8）

### 2.1 目录与配置

```
/opt/datagov/
├── dgp-boot.jar
├── datagov.env            # JWT/DB/CORS 等环境变量，600
├── logs/app.log
├── static/                # 旧静态目录（当前从 jar 内提供）
└── backup/20261031/       # 旧 jar、旧 env、旧库 dump、旧文件归档
```

`datagov.env` 关键项：

```ini
DGP_JWT_SECRET=...            # ≥32 字节
DGP_MASK_KEY=...
DGP_CORS_ORIGINS=http://103.85.25.169:8092,http://103.85.25.169
SPRING_PROFILES_ACTIVE=mysql
DGP_DB_HOST=127.0.0.1
DGP_DB_PORT=3306
DGP_DB_NAME=dgp_new
DGP_DB_USER=dgp_new
DGP_DB_PASSWORD=...
```

### 2.2 启停与日志

```bash
sudo systemctl status datagov
sudo systemctl restart datagov
sudo journalctl -u datagov -f
curl -o /dev/null -w '%{http_code}\n' -X POST http://127.0.0.1:8092/api/v1/auth/login \
  -H 'Content-Type: application/json' -d '{"username":"admin","password":"Dgp@2026"}'
```

### 2.3 已做修复

- MySQL 8 保留字：`int_incr_config.last_value` 改为 `current_value`，查询别名改为 `lastValue`。
- MySQL 不支持 `ALTER TABLE ... ADD COLUMN IF NOT EXISTS`：从 `schema-mysql.sql` 移除，升级用 `sql/upgrade-v1.16-to-v1.17.sql`。
- 登录后首屏空白：路由守卫不再阻塞等待菜单接口；登录统一走 `router.replace`。
- 前端 `静态资源` 已打进 jar，避免文件系统静态目录与 jar 双份不一致。

## 3. NAS（MariaDB 10.11）

### 3.1 为什么用 3307

NAS 上存在一个 **Podman `mysql8` 容器（Created 状态）**，其端口映射在 `CNI-HOSTPORT-DNAT` 中把 `0.0.0.0:3306` DNAT 到不存在的容器，导致本机 MariaDB 监听 3306 但 TCP 连接全部超时。

处理：新增 `/etc/mysql/mariadb.conf.d/99-port.cnf`：

```ini
[mysqld]
port = 3307
```

重启 MariaDB；应用连接 `127.0.0.1:3307`。若确认不再需要该容器，可 `podman rm mysql8` 后再把端口改回 3306。

### 3.2 应用配置

`/home/wnvf0rest/dgp/datagov.env`（600）：

```ini
DGP_JWT_SECRET=...
DGP_MASK_KEY=...
SPRING_PROFILES_ACTIVE=mysql
DGP_DB_HOST=127.0.0.1
DGP_DB_PORT=3307
DGP_DB_NAME=dgp
DGP_DB_USER=dgp
DGP_DB_PASSWORD=...
```

`/etc/systemd/system/dgp.service` 通过 `EnvironmentFile=/home/wnvf0rest/dgp/datagov.env` 加载；H2 时期 unit 备份为 `dgp.service.h2.bak`。

### 3.3 H2 数据备份

切换 MariaDB 前，原 H2 数据目录已备份：

```
/home/wnvf0rest/dgp/data.h2.bak.20261031/
```

如需回滚 H2：停服务 → 把 `data` 重命名/恢复为 `data.h2.bak.20261031` → 用 `dgp.service.h2.bak` 覆盖 unit → `daemon-reload` → 启动。

### 3.4 常用命令

```bash
sudo systemctl status dgp
sudo systemctl restart dgp
sudo journalctl -u dgp -n 100 --no-pager
mysql -udgp -p -h127.0.0.1 -P3307 dgp
```

## 4. 升级流程（单 jar）

1. 本机构建：`cd backend && mvn -DskipTests clean package`（前端先 `npm run build` 输出到 `backend/src/main/resources/static`）。
2. 备份远端：jar、`datagov.env`、数据库 dump、当前 unit。
3. 停服务，替换 jar，启动，观察日志。
4. 数据库变更：按 `sql/upgrade-vX-to-vY.sql` 顺序执行；MySQL 的 `schema-mysql.sql` 只负责建新表，不加旧表字段。
5. 冒烟：登录、概览、集成配置、运维监控、审批中心、CDC、标准基础库。

## 5. 故障排查

| 现象 | 排查 |
|---|---|
| 页面报“数据访问异常”，带 traceId | `grep '<traceId>' logs/app.log 或 journalctl`，看 SQL 异常；常见为保留字/字段缺失 |
| 登录成功但页面空白/停在登录页 | 看浏览器 Console/Network；本版本已修路由守卫阻塞，若旧 jar 仍存在需升级 |
| NAS MySQL 连接超时 | 确认连的是 3307，不是被 Podman 映射的 3306；`ss -lntp \| grep 330[67]` |
| MariaDB 启动失败 | 看 `journalctl -u mariadb`；确认初始化 SQL 不含 `max_execution_time` |
| 首次登录很慢 | 日本 2GB 服务器 + 首次加载前端 chunk；后续正常 |

## 6. 验收基线（2026-10-31）

- 登录 200；`profile=mysql`；Hikari 连接为 MySQL。
- 元数据 13 表/7 数据源；模型 5；数据元 30；质量规则 24/问题 19/稽核 3；集成作业 8；用户 9/角色 6/菜单 31。
- BPM：定义→发布→发起→待办→通过，实例 `APPROVED`；重启后仍存在。
- CDC：`ods_customer → cdc_mysql_verify` 全量 6000 行；重启后任务与运行记录仍在。
- 页面：概览、集成配置、运维监控、审批中心、CDC、质量配置均正常，无数据访问异常。
