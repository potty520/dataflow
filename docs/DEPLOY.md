# 大数据基座平台 (DataFlow) — 操作文档

> **版本:** v1.0  |  **日期:** 2026-06-30  |  **负责人:** 铁壳  

---

## 1. 项目概述

大数据基座平台是一个面向数字政府的大数据全生命周期管理平台，包含 **7 大子系统**、**20 个后端 Controller**、**49 条前端路由**、**65 张数据库表**。

### 技术栈

| 层级 | 技术 | 版本/说明 |
|------|------|-----------|
| 后端框架 | Spring Boot 2.7 + MyBatis-Plus | Java 17 |
| 安全认证 | Spring Security + JWT | RBAC 权限模型 |
| 前端框架 | Vue 3 + Element Plus | Vite 构建 |
| 数据库 | MySQL 8.0 | utf8mb4 |
| 中间件 | Elasticsearch / Kafka / Redis | Docker 部署 |
| 对象存储 | MinIO | Docker 部署 |
| 邮件测试 | Mailpit | Docker 部署 |

### 7 大子系统

```
统一门户 ── 用户/角色/菜单/部门/租户/日志 (RBAC 权限体系)
数据汇聚 ── 数据源管理 → 数据流 → 全量同步/CDC → 汇聚监控
数据开发 ── 项目 → 脚本 → 工作流 → 调度 → 质量规则 → HDFS/UDF
数据服务 ── API 管理 → 应用管理 → 流控策略 → 服务目录 → 工单
数据资产 ── 资产目录 → 血缘追踪 → 数据地图 → 收藏/关注
数据治理 ── 数据标准 → 代码集 → 数据元 → 数仓分层 → 指标管理 → 模型
基础支撑 ── 集群/主机/组件 → 资源组 → 存储配额 → 监控
```

**完整数据链路:** 数据源注册 → 数据采集同步 → 脚本加工 → 工作流编排 → 定时调度 → API 输出 → 资产目录 + 血缘关系

---

## 2. 环境要求

| 组件 | 最低版本 | 验证命令 |
|------|----------|----------|
| JDK | 17+ | `java -version` |
| Node.js | 18+ | `node -v` |
| Maven | 3.8+ | `mvn -v` |
| MySQL | 8.0+ | 远程 103.236.96.82:13306 |
| Docker | 20.10+ | `docker --version` |

---

## 3. 快速部署指南

### 3.1 数据库初始化

**连接信息:**
```
主机: 103.236.96.82
端口: 13306
用户: remote
密码: (联系管理员)
数据库: dataflow
```

**初始化步骤:**

```bash
# 1. 建表
mysql -h 103.236.96.82 -P 13306 -u remote -p \
  --default-character-set=utf8mb4 \
  dataflow < sql/schema-mysql.sql

# 2. 导入初始数据（包含管理员账号、菜单、种子数据）
mysql -h 103.236.96.82 -P 13306 -u remote -p \
  --default-character-set=utf8mb4 \
  dataflow < sql/init-data-mysql.sql

# 3. 验证
mysql -h 103.236.96.82 -P 13306 -u remote -p -e \
  "SELECT COUNT(*) AS tables FROM information_schema.tables WHERE table_schema='dataflow';"
# 应返回: 65
```

> **⚠️ 重要:** 必须使用 `--default-character-set=utf8mb4` 参数，否则中文会乱码！

### 3.2 Docker 外部依赖启动

```bash
cd docker/
docker compose up -d
```

| 服务 | 端口 | 用户名 | 密码 | 验证命令 |
|------|------|--------|------|----------|
| Elasticsearch | 9200 | — | — | `curl http://localhost:9200` |
| Kafka | 9092 | — | — | — |
| Zookeeper | 2181 | — | — | `echo ruok \| nc localhost 2181` → imok |
| MinIO | 9000 | minioadmin | minioadmin123 | `curl http://localhost:9000/minio/health/live` |
| Redis | 6380 | — | — | `redis-cli -p 6380 ping` → PONG |
| Mailpit | 1025/8025 | — | — | `curl http://localhost:8025` |

### 3.3 后端启动

```bash
cd backend/

# 编译
mvn clean compile -DskipTests

# 启动（MySQL profile）
mvn spring-boot:run -Dspring-boot.run.profiles=mysql

# 后台运行
nohup mvn spring-boot:run -Dspring-boot.run.profiles=mysql > /tmp/dataflow.log 2>&1 &
```

**验证:**
```bash
# 健康检查
curl -s -X POST http://localhost:8088/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}'
# 返回: {"code":200,...}

# 默认端口: 8088
```

**配置文件:** `backend/src/main/resources/application.yml`

```yaml
# MySQL profile 关键配置
spring:
  config:
    activate:
      on-profile: mysql
  datasource:
    driver-class-name: com.mysql.cj.jdbc.Driver
    url: jdbc:mysql://103.236.96.82:13306/dataflow?useUnicode=true&characterEncoding=UTF-8&connectionCollation=utf8mb4_unicode_ci&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&useSSL=false&characterSetResults=utf8
    username: remote
    password: (数据库密码)
```

> **⚠️ 注意:** 首次部署需将 `password` 字段设为真实数据库密码。

### 3.4 前端启动

```bash
cd frontend/

# 安装依赖（首次）
npm install

# 开发模式运行
npm run dev
# 输出: http://0.0.0.0:4000/

# 生产构建
npm run build
# 产出: dist/
```

**Vite 配置关键项** (`vite.config.js`):
```javascript
server: {
  host: '0.0.0.0',   // 局域网可访问
  port: 4000,
  proxy: {
    '/api': {
      target: 'http://localhost:8088',  // 代理到后端
      changeOrigin: true
    }
  }
}
```

---

## 4. 系统配置

### 默认管理员

| 字段 | 值 |
|------|-----|
| 用户名 | `admin` |
| 密码 | `admin123` |

### 修改密码
1. 登录后点击右上角 **系统管理员** → **个人中心**
2. 在"修改密码"区域输入原密码和新密码

### 系统配置项
访问 **安全管理** 页面或通过 API:
```bash
# 获取所有配置
curl -H "Authorization: Bearer <token>" http://localhost:8088/api/platform/config
```

---

## 5. 功能模块说明

### 5.1 统一门户 (Portal)

| 功能 | 后端端点 | 前端路由 | 数据库表 |
|------|----------|----------|----------|
| 登录 | POST /api/auth/login | /login | sys_user |
| 用户管理 | /api/portal/user/* | /portal/user | sys_user |
| 角色管理 | /api/portal/role/* | /portal/role | sys_role |
| 菜单管理 | /api/portal/menu/* | /portal/menu | sys_menu |
| 部门管理 | /api/portal/dept/* | /portal/dept | sys_dept |
| 租户管理 | /api/portal/tenant/* | /portal/tenant | sys_tenant |
| 操作日志 | /api/portal/log/* | /portal/log | sys_oper_log |
| 活动日志 | /api/portal/log/activity/* | — | user_activity_log |
| 系统配置 | /api/platform/config | /portal/config | sys_config |
| 安全设置 | — | /portal/security | — |
| 个人中心 | — | /portal/profile | sys_user |

### 5.2 数据汇聚平台 (Aggregation)

| 功能 | 后端端点 | 前端路由 | 数据库表 |
|------|----------|----------|----------|
| 数据源管理 | /api/aggregation/datasource/* | /aggregation/datasource | agg_datasource |
| 连通性测试 | POST /datasource/{id}/test | — | — |
| 数据流管理 | /api/aggregation/dataflow/* | /aggregation/dataflow | agg_dataflow |
| 数据流执行 | POST /dataflow/{id}/execute | — | — |
| 补数据 | POST /dataflow/{id}/backfill | — | — |
| 执行日志 | /api/aggregation/dataflow/log/* | — | agg_dataflow_log |
| 全量同步 | /api/aggregation/fullsync/* | /aggregation/fullsync | agg_full_sync |
| CDC增量 | /api/aggregation/cdc/* | /aggregation/cdc | agg_cdc_task |
| 增量字段 | /api/aggregation/increment/* | /aggregation/increment | agg_increment_field |
| 汇聚监控 | — | /aggregation/monitor | — |

**Controller:** `AggregationController.java` + `FullSyncCdcController.java`

### 5.3 数据开发平台 (Development)

| 功能 | 后端端点 | 前端路由 | 数据库表 |
|------|----------|----------|----------|
| 项目管理 | /api/development/project/* | /development/workflow | dev_project |
| 脚本开发 | /api/development/script/* | /development/script | dev_script |
| 脚本版本 | /api/development/script/version/* | — | dev_script_version |
| 脚本执行日志 | /api/development/script/execution/* | — | dev_script_execution_log |
| 工作流开发 | /api/development/workflow/* | /development/workflow | dev_workflow |
| 调度管理 | /api/development/schedule/* | /development/schedule | dev_schedule_task |
| 调度启停 | POST /schedule/{id}/start, /stop | — | — |
| 质量规则 | /api/development/quality/rule/* | /development/quality | dev_quality_rule |
| 质量任务 | /api/development/quality/task/* | /development/quality | dev_quality_task |
| 评分历史 | /api/development/quality/score/* | — | dev_quality_score_history |
| HDFS管理 | /api/development/hdfs/* | — | dev_hdfs_file |
| UDF管理 | /api/development/udf/* | — | dev_udf |
| 文件监听 | /api/development/file-watcher/* | — | dev_file_watcher |
| 开发监控 | /api/development/monitor/stats | /development/monitor | — |

**Controller:** `DevelopmentController.java`

### 5.4 数据服务平台 (Service)

| 功能 | 后端端点 | 前端路由 | 数据库表 |
|------|----------|----------|----------|
| API管理 | /api/service/api/* | /service/api | svc_api |
| 应用管理 | /api/service/application/* | /service/application | svc_application |
| 服务目录 | /api/service/catalog/* | /service/catalog | svc_catalog |
| 服务单元 | /api/service/unit/* | /service/unit | svc_service_unit |
| 流控策略 | /api/service/ratelimit/* | /service/ratelimit | svc_rate_limit |
| 服务工单 | /api/service/workorder/* | /service/workorder | svc_workorder |
| 调用日志 | /api/service/call-log/* | — | svc_api_call_log |
| 服务监控 | — | /service/monitor | — |

**Controller:** `ServiceController.java`

### 5.5 数据资产平台 (Asset)

| 功能 | 后端端点 | 前端路由 | 数据库表 |
|------|----------|----------|----------|
| 数据地图 | /api/asset/table/* | /asset/map | asset_table |
| 资产字段 | — | — | asset_field |
| 数据血缘 | /api/asset/lineage/* | — | asset_lineage |
| 资产总览 | /api/asset/overview | /asset/overview | — |
| 资产监控 | — | /asset/monitor | — |
| 收藏管理 | /api/asset/favorite/* | — | asset_favorite |
| 关注管理 | /api/asset/follow/* | — | asset_follow |
| 标签管理 | /api/asset/tag/* | — | asset_tag, asset_tag_relation |

**Controller:** `AssetController.java` + `LineageController.java` + `AssetTagController.java`

### 5.6 数据治理平台 (Governance)

| 功能 | 后端端点 | 前端路由 | 数据库表 |
|------|----------|----------|----------|
| 治理目录 | /api/governance/catalog/* | /governance/standard | gov_standard_catalog |
| 代码集 | /api/governance/codeset/* | /governance/standard | gov_code_set |
| 数据元 | /api/governance/word/* | /governance/standard | gov_word |
| 数仓分层 | /api/governance/layer/* | /governance/warehouse | gov_warehouse_layer |
| 数仓目录 | /api/governance/warehouse-catalog/* | /governance/warehouse | gov_warehouse_catalog |
| 数据建模 | /api/governance/model/* | /governance/modeling | gov_model_table |
| DDL生成 | POST /model/{id}/generate-ddl | — | — |
| 指标管理 | /api/governance/atomic/*, /derived/*, /period/*, /limit/* | /governance/indicator | gov_atomic_indicator, gov_derived_indicator, gov_composite_indicator, gov_stat_period, gov_business_limit |
| 业务域 | /api/governance/domain/* | /governance/domain | gov_business_domain |
| 代理节点 | /api/governance/proxy-node/* | /governance/proxynode | gov_proxy_node |
| 治理监控 | — | /governance/monitor | — |
| 元数据采集 | /api/metadata/collector/* | /governance/collector | meta_collector_task, meta_collector_log |
| 模型设计器 | — | /governance/designer | — |

**Controller:** `GovernanceController.java` + `MetaCollectorController.java` + `GovCrossDomainController.java`

### 5.7 基础支撑平台 (Infrastructure)

| 功能 | 后端端点 | 前端路由 | 数据库表 |
|------|----------|----------|----------|
| 集群管理 | /api/infrastructure/cluster/* | /infrastructure/cluster | infra_cluster |
| 主机管理 | /api/infrastructure/host/* | /infrastructure/cluster | infra_host |
| 组件管理 | /api/infrastructure/component/* | /infrastructure/component | infra_component |
| 资源组 | /api/infrastructure/resource-group/* | /infrastructure/cluster | infra_resource_group |
| 存储配额 | /api/infrastructure/storage-quota/* | /infrastructure/cluster | storage_quota |
| 主机指标 | /api/infrastructure/monitor/host-metrics | — | — |
| 资源监控 | — | /infrastructure/monitor | — |

**Controller:** `InfrastructureController.java`

---

## 6. 数据库字典

### 核心业务表 (65张)

#### 统一门户 (8张)
| 表名 | 说明 | 核心字段 |
|------|------|----------|
| sys_user | 用户表 | username, password, nickname, phone, tenant_id, status |
| sys_role | 角色表 | role_code, role_name, tenant_id |
| sys_user_role | 用户角色关联 | user_id, role_id |
| sys_menu | 菜单表 | menu_code, menu_name, parent_id, menu_type(1目录/2菜单/3按钮), path |
| sys_dept | 部门表 | dept_name, parent_id, tenant_id |
| sys_tenant | 租户表 | tenant_code, tenant_name |
| sys_oper_log | 操作日志 | username, operation, method, params, ip |
| user_activity_log | 活动日志 | user_id, activity_type, content |

#### 数据汇聚 (6张)
| 表名 | 说明 | 核心字段 |
|------|------|----------|
| agg_datasource | 数据源 | name, db_type, host, port, username |
| agg_dataflow | 数据流 | name, source_id, target_id, sync_mode |
| agg_dataflow_log | 流日志 | dataflow_id, status, read_rows, write_rows |
| agg_full_sync | 全量同步 | name, source_id, target_id, table_name |
| agg_cdc_task | CDC任务 | name, source_id, target_id, binlog_file |
| agg_increment_field | 增量字段 | table_name, field_name, field_type |

#### 数据开发 (16张)
| 表名 | 说明 | 核心字段 |
|------|------|----------|
| dev_project | 项目 | name, domain, description |
| dev_script | 脚本 | name, type(SQL/SPARK/SHELL), content |
| dev_script_version | 脚本版本 | script_id, version, content |
| dev_script_execution_log | 执行日志 | script_id, status, duration |
| dev_workflow | 工作流 | name, type, parallel_strategy, retry_count |
| dev_schedule_task | 调度任务 | name, cron_expr, workflow_id, status |
| dev_schedule_execution_log | 调度日志 | schedule_id, status |
| dev_quality_rule | 质量规则 | name, rule_type, sql_expression |
| dev_quality_task | 质量任务 | name, rule_json, table_name |
| dev_quality_score_history | 评分历史 | task_id, score |
| dev_hdfs_file | HDFS文件 | file_name, file_path, file_size |
| dev_udf | UDF函数 | udf_name, udf_type, class_name, jar_path |
| dev_file_watcher | 文件监听 | watch_path, file_pattern, action |

#### 数据服务 (8张)
| 表名 | 说明 | 核心字段 |
|------|------|----------|
| svc_api | API | api_name, api_path, method, sql_text |
| svc_api_call_log | 调用日志 | api_id, caller_app, request_body, response |
| svc_application | 应用 | app_name, app_key, app_secret |
| svc_catalog | 服务目录 | name, parent_id |
| svc_catalog_project | 目录项目关联 | catalog_id, project_id |
| svc_service_unit | 服务单元 | unit_name, description, status |
| svc_rate_limit | 流控策略 | name, api_id, limit_count, time_window |
| svc_workorder | 工单 | title, type, applicant, status |

#### 数据资产 (6张)
| 表名 | 说明 | 核心字段 |
|------|------|----------|
| asset_table | 资产表 | name, database_name, layer, row_count |
| asset_field | 资产字段 | table_id, field_name, field_type, is_key |
| asset_lineage | 血缘关系 | source_table_id, target_table_id, relation_type |
| asset_favorite | 收藏 | user_id, table_id |
| asset_follow | 关注 | user_id, table_id |
| asset_tag | 标签 | tag_name, color |
| asset_tag_relation | 标签关联 | tag_id, table_id |

#### 数据治理 (16张)
| 表名 | 说明 | 核心字段 |
|------|------|----------|
| gov_standard_catalog | 标准目录 | name, parent_id, standard_type |
| gov_code_set | 代码集 | code, name, table_name |
| gov_word | 数据元 | word_name, word_type, length |
| gov_warehouse_catalog | 数仓目录 | name, parent_id |
| gov_warehouse_layer | 数仓分层 | layer_code, layer_name, layer_order |
| gov_model_table | 模型表 | name, layer_id, sql_ddl |
| gov_atomic_indicator | 原子指标 | indicator_name, stat_type, aggregation |
| gov_derived_indicator | 派生指标 | name, atomic_id, period_id, limit_id |
| gov_composite_indicator | 复合指标 | name, expression, derived_ids |
| gov_stat_period | 统计周期 | period_name, period_unit |
| gov_business_limit | 业务限定 | limit_name, condition_expression |
| gov_business_domain | 业务域 | domain_name, description |
| gov_proxy_node | 代理节点 | name, host, port, status |
| gov_cross_domain_metadata | 跨域元数据 | source_system, target_system |
| meta_collector_task | 采集任务 | task_name, source_type, target_table |
| meta_collector_log | 采集日志 | task_id, status, records |

#### 基础设施 (6张)
| 表名 | 说明 | 核心字段 |
|------|------|----------|
| infra_cluster | 集群 | cluster_name, cluster_type |
| infra_host | 主机 | host_name, ip, cpu_cores, memory_gb |
| infra_component | 组件 | component_name, version, status |
| infra_resource_group | 资源组 | group_name, cluster_id |
| storage_quota | 存储配额 | name, quota_gb, used_gb |
| sys_config | 系统配置 | config_key, config_value |

---

## 7. 故障排查

### MySQL 连接失败
```bash
# 检查网络连通性
telnet 103.236.96.82 13306

# 检查数据库是否存在
mysql -h 103.236.96.82 -P 13306 -u remote -p -e "SHOW DATABASES;"
```

### Docker 服务启动失败
```bash
# 查看容器日志
docker compose -f docker/docker-compose.yml logs <service-name>

# 重启全部
docker compose -f docker/docker-compose.yml down
docker compose -f docker/docker-compose.yml up -d

# 检查端口
lsof -i :9200  # ES
lsof -i :9092  # Kafka
lsof -i :6380  # Redis
```

### 端口冲突 (8088)
```bash
# 查找占用进程
lsof -i :8088

# 强制释放
lsof -ti:8088 | xargs kill -9

# 修改端口: application.yml 中 server.port
```

### 中文乱码修复

**原因:** MySQL 客户端连接字符集为 latin1 导致中文乱码存储。

**修复步骤:**
```bash
# 1. 所有连接必须指定 utf8mb4
mysql -h 103.236.96.82 -P 13306 -u remote -p \
  --default-character-set=utf8mb4

# 2. 验证
SELECT id, menu_name, HEX(menu_name) FROM sys_menu WHERE id=73;
# HEX值应为 E4B8AA... (中文UTF-8编码)

# 3. JDBC 连接字符串必须包含
# characterEncoding=UTF-8&connectionCollation=utf8mb4_unicode_ci
```

### 前端页面空白

**原因:** 数据库中菜单 `path` 字段缺少 `/` 前缀，导致 el-menu hash 跳转与 vue-router history 模式冲突。

**修复:**
```sql
UPDATE sys_menu SET path = CONCAT('/', path) WHERE menu_type = 2 AND path NOT LIKE '/%';
```

---

## 8. 开发指南

### 项目代码结构
```
dataflow/
├── backend/                          # Spring Boot 后端
│   └── src/main/java/com/zhangye/dataflow/
│       ├── controller/               # 20个 Controller
│       ├── entity/                   # 65个 Entity (数据库表映射)
│       ├── mapper/                   # 65个 MyBatis Mapper
│       ├── service/                  # 业务服务层
│       ├── config/                   # Spring 配置 (Security, MyBatis, DataInit)
│       ├── security/                 # JWT + Security
│       ├── common/                   # 公共类 (Result, PageResult, Exception)
│       ├── engine/                   # 执行引擎
│       └── dto/                      # 数据传输对象
├── frontend/                         # Vue 3 前端
│   └── src/
│       ├── views/                    # 页面组件 (7个子系统目录)
│       ├── api/                      # API 调用模块 (index.js)
│       ├── router/                   # 路由配置
│       ├── stores/                   # Pinia 状态管理
│       └── layout/                   # 布局组件
├── docker/                           # Docker Compose 配置
├── sql/                              # 数据库脚本
│   ├── schema-mysql.sql              # 完整 DDL (65张表)
│   └── init-data-mysql.sql           # 种子数据
└── docs/                             # 文档
    ├── DEPLOY.md                     # 本文档
    ├── backend-logic-test.md         # 后端业务逻辑测试报告
    └── frontend-e2e-test.md          # 前端 E2E 测试报告
```

### 新增模块步骤

**后端新增:**
1. 创建 `Entity` 类 (`src/main/java/.../entity/`)
2. 创建 `Mapper` 接口 (`src/main/java/.../mapper/`)
3. 在对应 `Controller` 中添加 CRUD 端点
4. 在远端 MySQL 执行 `CREATE TABLE` DDL

**前端新增:**
1. 创建 Vue 页面 (`src/views/<module>/index.vue`)
2. 在 `src/api/index.js` 添加 API 调用函数
3. 在 `src/router/index.js` 注册路由
4. 在 MySQL `sys_menu` 表添加菜单记录

### 常用命令

```bash
# 后端编译和启动
cd backend/
mvn clean compile
mvn spring-boot:run -Dspring-boot.run.profiles=mysql

# 前端开发
cd frontend/
npm run dev          # 开发模式 (端口4000)
npm run build        # 生产构建

# Docker 管理
cd docker/
docker compose up -d    # 启动
docker compose down     # 停止
docker compose logs -f  # 查看日志

# API 测试
# 登录获取 token
TOKEN=$(curl -s -X POST http://localhost:8088/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}' \
  | jq -r '.data.token')

# 使用 token 调用 API
curl -H "Authorization: Bearer $TOKEN" \
  http://localhost:8088/api/dashboard/stats

# 数据库操作
mysql -h 103.236.96.82 -P 13306 -u remote -p \
  --default-character-set=utf8mb4 dataflow
```

---

## 9. 测试结果

| 测试项 | 通过率 | 详情 |
|--------|--------|------|
| 后端 API | 61/61 (100%) | 7大子系统全部端点正常 |
| 前端 E2E | 38/39 (97.4%) | 1项告警(应用管理,已修复) |
| 编译构建 | ✅ 无错误 | 前后端均编译通过 |
| Docker 依赖 | ✅ 6/6 运行 | ES/Kafka/ZK/MinIO/Redis/Mailpit |
| 字符编码 | ✅ 零乱码 | 全部表中文正常 |

---

> 📝 **文档维护:** 铁壳 (Iron Shell)  |  最后更新: 2026-06-30
