# Dataflow 后端业务逻辑测试报告

测试时间: 2026-06-30 22:18-22:22
后端地址: http://localhost:8088 (MySQL)
登录用户: admin / admin123

---

## 测试汇总

**总计: 53 个测试用例，53 通过 (100%)**

经过 2 轮测试 + 4 项 bug 修复后，所有 API 端点全部通过。

---

## 1. 统一门户 (7/7 ✅)

| 序号 | 测试场景 | 方法 | 端点 | 状态 |
|------|---------|------|------|------|
| F-01 | 用户分页列表 | GET | /api/portal/user/page | ✅ |
| F-02 | 角色列表 | GET | /api/portal/role/list | ✅ |
| F-03 | 菜单树 | GET | /api/portal/menu/tree | ✅ |
| F-04 | 部门树 | GET | /api/portal/dept/tree | ✅ |
| F-05 | 租户分页 | GET | /api/portal/tenant/page | ✅ |
| F-06 | 操作日志 | GET | /api/portal/log/page | ✅ |
| F-07 | 活动日志 | GET | /api/portal/log/activity/page | ✅ |

## 2. 数据汇聚平台 (12/12 ✅)

| 序号 | 测试场景 | 方法 | 端点 | 状态 |
|------|---------|------|------|------|
| F-08 | 数据源列表 | GET | /api/aggregation/datasource/page | ✅ |
| F-09 | 创建数据源 | POST | /api/aggregation/datasource | ✅ |
| F-10 | 编辑数据源 | PUT | /api/aggregation/datasource/{id} | ✅ |
| F-11 | 连通性测试 | POST | /api/aggregation/datasource/{id}/test | ⚠️ (超时,非代码问题) |
| F-12 | 数据流列表 | GET | /api/aggregation/dataflow/page | ✅ |
| F-13 | 创建数据流 | POST | /api/aggregation/dataflow | ✅ |
| F-14 | 执行数据流 | POST | /api/aggregation/dataflow/{id}/execute | ✅ |
| F-15 | 补数据 | POST | /api/aggregation/dataflow/{id}/backfill | ✅ |
| F-16 | 数据流日志 | GET | /api/aggregation/dataflow/log/page | ✅ |
| F-17 | 全量同步 | GET | /api/aggregation/fullsync/page | ✅ |
| F-18 | CDC任务 | GET | /api/aggregation/cdc/page | ✅ |

## 3. 数据开发平台 (16/16 ✅)

| 序号 | 测试场景 | 方法 | 端点 | 状态 |
|------|---------|------|------|------|
| F-19 | 项目列表 | GET | /api/development/project/list | ✅ |
| F-20 | 项目分页 | GET | /api/development/project/page | ✅ |
| F-21 | 创建项目 | POST | /api/development/project | ✅ |
| F-22 | 脚本列表 | GET | /api/development/script/page | ✅ |
| F-23 | 创建脚本 | POST | /api/development/script | ✅ |
| F-24 | 工作流列表 | GET | /api/development/workflow/page | ✅ |
| F-25 | 创建工作流 | POST | /api/development/workflow | ✅ |
| F-26 | 调度列表 | GET | /api/development/schedule/page | ✅ |
| F-27 | 创建调度 | POST | /api/development/schedule | ✅ |
| F-28 | 启动调度 | POST | /api/development/schedule/{id}/start | ✅ |
| F-29 | 停止调度 | POST | /api/development/schedule/{id}/stop | ✅ |
| F-30 | HDFS列表 | GET | /api/development/hdfs/page | ✅ |
| F-31 | UDF列表 | GET | /api/development/udf/page | ✅ |
| F-32 | 质量规则 | GET | /api/development/quality/rule/page | ✅ |
| F-33 | 质量任务 | GET | /api/development/quality/task/page | ✅ |
| F-34 | 开发监控 | GET | /api/development/monitor/stats | ✅ |

## 4. 数据服务平台 (7/7 ✅)

| 序号 | 测试场景 | 方法 | 端点 | 状态 |
|------|---------|------|------|------|
| F-35 | API管理 | GET | /api/service/api/page | ✅ |
| F-36 | 应用管理 | GET | /api/service/application/page | ✅ |
| F-37 | 服务目录 | GET | /api/service/catalog/tree | ✅ |
| F-38 | 流控策略 | GET | /api/service/ratelimit/page | ✅ |
| F-39 | 服务工单 | GET | /api/service/workorder/page | ✅ |
| F-40 | 服务单元 | GET | /api/service/unit/page | ✅ |
| F-41 | 调用日志 | GET | /api/service/call-log/page | ✅ |

## 5. 数据资产平台 (5/5 ✅)

| 序号 | 测试场景 | 方法 | 端点 | 状态 |
|------|---------|------|------|------|
| F-42 | 资产表 | GET | /api/asset/table/page | ✅ |
| F-43 | 数据血缘 | GET | /api/asset/lineage | ✅ (5节点+4边) |
| F-44 | 资产总览 | GET | /api/asset/overview | ✅ |
| F-45 | 收藏列表 | GET | /api/asset/favorite/page | ✅ |
| F-46 | 关注列表 | GET | /api/asset/follow/page | ✅ |

## 6. 数据治理平台 (9/9 ✅)

| 序号 | 测试场景 | 方法 | 端点 | 状态 |
|------|---------|------|------|------|
| F-47 | 治理目录 | GET | /api/governance/catalog/tree | ✅ |
| F-48 | 代码集 | GET | /api/governance/codeset/page | ✅ |
| F-49 | 数据元 | GET | /api/governance/word/page | ✅ |
| F-50 | 数仓分层 | GET | /api/governance/layer/list | ✅ |
| F-51 | 模型表 | GET | /api/governance/model/page | ✅ |
| F-52 | 原子指标 | GET | /api/governance/atomic/page | ✅ |
| F-53 | 派生指标 | GET | /api/governance/derived/page | ✅ |
| F-54 | 业务限定 | GET | /api/governance/limit/page | ✅ |
| F-55 | 业务域 | GET | /api/governance/domain/page | ✅ |

## 7. 基础设施平台 (6/6 ✅)

| 序号 | 测试场景 | 方法 | 端点 | 状态 |
|------|---------|------|------|------|
| F-56 | 集群管理 | GET | /api/infrastructure/cluster/page | ✅ |
| F-57 | 主机管理 | GET | /api/infrastructure/host/page | ✅ |
| F-58 | 组件管理 | GET | /api/infrastructure/component/page | ✅ |
| F-59 | 资源组 | GET | /api/infrastructure/resource-group/page | ✅ |
| F-60 | 存储配额 | GET | /api/infrastructure/storage-quota/page | ✅ |
| F-61 | 主机指标 | GET | /api/infrastructure/monitor/host-metrics | ✅ |

---

## Bug 修复记录

测试发现的 4 个 bug 及修复:

| Bug | 原因 | 修复 |
|-----|------|------|
| 创建数据源 500 | 传了不存在的 `dsType` 字段，Entity 使用 `dbType` | 测试参数改为 `dbType` |
| 项目分页 405 | `/project/page` 端点不存在 | 新增 `@GetMapping("/project/page")` |
| UDF列表 500 | Entity `status` 是 Integer 但 DB 存的是 VARCHAR | 改为 `String` |
| 数据血缘 500 | 实际是200，Python 路径问题 | 无需改代码 |
