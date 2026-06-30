# 大数据基座平台

> 数字政府大数据全生命周期管理平台 — 基于 Spring Boot + Vue 3 全栈实现

## 技术栈

| 层级 | 技术 |
|------|------|
| 后端 | Spring Boot 2.7、Spring Security、JWT、MyBatis-Plus |
| 前端 | Vue 3、Vite、Element Plus、Pinia、Vue Router |
| 数据库 | MySQL 8.0（utf8mb4） |
| 中间件 | Elasticsearch、Kafka、Redis、MinIO、Mailpit |

## 子系统

| 子系统 | 功能模块 |
|--------|----------|
| 统一门户 | 租户/用户/角色/部门/菜单/日志/个人中心/RBAC |
| 数据汇聚 | 数据源、数据流、全量同步、CDC、汇聚监控 |
| 数据治理 | 数据标准、代码集、数仓分层、模型设计、指标管理、代理节点 |
| 数据开发 | 项目、脚本、工作流、调度、质量规则、HDFS/UDF |
| 数据服务 | API、应用、服务目录、服务单元、流控策略、工单 |
| 数据资产 | 数据地图、血缘追踪、资产总览、收藏/关注 |
| 基础支撑 | 集群、主机、组件、资源组、存储配额、监控 |

## 快速启动

详见 [docs/DEPLOY.md](docs/DEPLOY.md)

### 1. 数据库

```bash
mysql -h <host> -P 13306 -u remote -p --default-character-set=utf8mb4 dataflow < sql/schema-mysql.sql
mysql -h <host> -P 13306 -u remote -p --default-character-set=utf8mb4 dataflow < sql/init-data-mysql.sql
```

### 2. Docker 依赖

```bash
cd docker && docker compose up -d
```

### 3. 后端

```bash
cd backend
mvn spring-boot:run -Dspring-boot.run.profiles=mysql
# → http://localhost:8088
```

### 4. 前端

```bash
cd frontend
npm install && npm run dev
# → http://localhost:4000
```

### 5. 登录

- 用户名：`admin`
- 密码：`admin123`

## 项目结构

```
dataflow/
├── backend/                 # Spring Boot 后端
│   └── src/main/java/com/zhangye/dataflow/
│       ├── controller/      # 20个 REST Controller
│       ├── entity/          # 65个 Entity
│       ├── mapper/          # 65个 MyBatis Mapper
│       ├── service/         # 业务服务层
│       ├── security/        # JWT + Spring Security
│       ├── config/          # 配置文件
│       └── common/          # 公共类
├── frontend/                # Vue 3 前端
│   └── src/
│       ├── views/           # 7个子系统页面
│       ├── api/             # 后端 API 调用
│       ├── router/          # 49条路由
│       └── layout/          # 布局组件
├── sql/                     # 完整DDL + 种子数据
├── docker/                  # Docker Compose
└── docs/                    # 操作文档 + 测试报告
```

## 文档

- [操作文档 (DEPLOY.md)](docs/DEPLOY.md) — 完整部署运维指南
- [后端测试报告](docs/backend-logic-test.md) — 61端点 100%通过
- [前端测试报告](docs/frontend-e2e-test.md) — 39页面 97.4%通过
- [需求规格说明书](需求说明书—大数据基座平台.md)
