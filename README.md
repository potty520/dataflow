# 大数据基座平台

基于《大数据平台基座项目软件需求规格说明书》实现的 Java + Vue 全栈项目。

## 技术栈

| 层级 | 技术 |
|------|------|
| 后端 | Spring Boot 2.7、Spring Security、JWT、MyBatis-Plus |
| 前端 | Vue 3、Vite、Element Plus、Pinia、Vue Router |
| 数据库 | H2（开发默认）/ MySQL（生产可选） |

## 子系统实现情况

| 子系统 | 状态 | 说明 |
|--------|------|------|
| 统一门户 | ✅ 已实现 | 租户/用户/角色/部门/菜单/日志/个人中心/RBAC |
| 数据汇聚平台 | ✅ 已实现 | 数据源、增量字段、数据流、监控、整库同步、CDC |
| 数据治理平台 | ✅ 已实现 | 数据标准、仓库分层、数据建模、指标管理、代理节点 |
| 数据开发平台 | ✅ 已实现 | 工作流、脚本、数据质量、数据调度 |
| 数据服务平台 | ✅ 已实现 | 应用、API、服务目录、工单、服务单元、流控策略 |
| 数据资产平台 | ✅ 已实现 | 数据总览、数据地图、资产监控、血缘 |
| 基础支撑平台 | ✅ 已实现 | 组件管理、集群管理、主机管理 |

## 快速启动

### 1. 启动后端

```bash
cd backend
mvn spring-boot:run
```

后端地址：http://localhost:8080  
H2 控制台：http://localhost:8080/h2-console（JDBC URL: `jdbc:h2:file:./data/dataflow`）

### 2. 启动前端

```bash
cd frontend
npm install
npm run dev
```

前端地址：http://localhost:5173

### 3. 默认账号

- 用户名：`admin`
- 密码：`admin123`

## 使用 MySQL

1. 创建数据库 `dataflow`
2. 执行 `sql/schema-mysql.sql`
3. 启动时指定 profile：

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=mysql
```

并修改 `application.yml` 中 mysql 数据源账号密码。

**新增模块后若使用 H2，需删除 `backend/data` 目录后重启**，以便重新初始化数据库表和示例数据。

## 项目结构

```
dataflow/
├── backend/                 # Spring Boot 后端
│   └── src/main/java/com/zhangye/dataflow/
│       ├── controller/      # REST API
│       ├── entity/          # 实体类
│       ├── mapper/          # MyBatis Mapper
│       └── security/        # JWT 认证
├── frontend/                # Vue 3 前端
│   └── src/
│       ├── views/portal/    # 统一门户页面
│       ├── views/aggregation/  # 数据汇聚页面
│       └── views/service/   # 数据服务页面
├── sql/                     # 数据库脚本
└── requirements_extracted.txt  # 需求文档提取文本
```

## API 概览

- `POST /api/auth/login` - 登录
- `GET /api/portal/tenant/page` - 租户分页
- `GET /api/portal/user/page` - 用户分页
- `GET /api/aggregation/datasource/page` - 数据源分页
- `GET /api/aggregation/dataflow/page` - 数据流分页
- `GET /api/dashboard/stats` - 首页统计

完整 API 见各 Controller 类。

## 说明

需求规格说明书涵盖 7 大子系统、数百项功能点（含 Hadoop 集群管理、LLVM 引擎等底层能力）。本项目实现了可运行的核心基座与管理功能，其余模块已预留菜单路由与数据库表结构，可按优先级分期建设。
"# dataflow" 
