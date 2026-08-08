-- 默认租户
INSERT INTO sys_tenant (id, tenant_code, tenant_name, status, start_time, end_time, create_by)
VALUES (1, 'T001', '张掖市大数据平台', 1, CURRENT_TIMESTAMP, DATEADD('YEAR', 5, CURRENT_TIMESTAMP), 'system');

-- 默认部门
INSERT INTO sys_dept (id, tenant_id, parent_id, dept_name, sort_order) VALUES
(1, 1, 0, '张掖市政府', 1),
(2, 1, 1, '大数据中心', 1),
(3, 1, 1, '信息中心', 2);

-- 默认角色
INSERT INTO sys_role (id, tenant_id, role_name, role_key, description, data_scope) VALUES
(1, 1, '超级管理员', 'admin', '系统超级管理员', 1),
(2, 1, '租户管理员', 'tenant_admin', '租户管理员', 1),
(3, 1, '普通用户', 'user', '普通开发用户', 4);

-- 默认用户 admin/admin123
INSERT INTO sys_user (id, tenant_id, dept_id, username, password, nickname, phone, status) VALUES
(1, 1, 2, 'admin', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iwK8pJK', '系统管理员', '13800000000', 1);

INSERT INTO sys_user_role (user_id, role_id) VALUES (1, 1);

-- 菜单
INSERT INTO sys_menu (id, parent_id, menu_code, menu_name, path, component, menu_type, service_name, icon, sort_order) VALUES
(1, 0, 'dashboard', '首页', '/dashboard', 'dashboard/index', 2, 'portal', 'HomeFilled', 0),
(10, 0, 'portal', '统一门户', '/portal', NULL, 1, 'portal', 'User', 1),
(11, 10, 'tenant', '租户管理', '/portal/tenant', 'portal/tenant/index', 2, 'portal', NULL, 1),
(12, 10, 'user', '用户管理', '/portal/user', 'portal/user/index', 2, 'portal', NULL, 2),
(13, 10, 'role', '角色管理', '/portal/role', 'portal/role/index', 2, 'portal', NULL, 3),
(14, 10, 'dept', '部门管理', '/portal/dept', 'portal/dept/index', 2, 'portal', NULL, 4),
(15, 10, 'menu', '菜单管理', '/portal/menu', 'portal/menu/index', 2, 'portal', NULL, 5),
(16, 10, 'log', '日志管理', '/portal/log', 'portal/log/index', 2, 'portal', NULL, 6),
(20, 0, 'aggregation', '数据汇聚', '/aggregation', NULL, 1, 'aggregation', 'Connection', 2),
(21, 20, 'datasource', '数据源管理', '/aggregation/datasource', 'aggregation/datasource/index', 2, 'aggregation', NULL, 1),
(22, 20, 'increment', '增量字段', '/aggregation/increment', 'aggregation/increment/index', 2, 'aggregation', NULL, 2),
(23, 20, 'dataflow', '数据流管理', '/aggregation/dataflow', 'aggregation/dataflow/index', 2, 'aggregation', NULL, 3),
(24, 20, 'agg_monitor', '数据流监控', '/aggregation/monitor', 'aggregation/monitor/index', 2, 'aggregation', NULL, 4),
(25, 20, 'fullsync', '整库同步', '/aggregation/fullsync', 'aggregation/fullsync/index', 2, 'aggregation', NULL, 5),
(80, 20, 'migration_map', '数据迁移映射', '/aggregation/migration-map', 'aggregation/migration-map/index', 2, 'aggregation', 'Connection', 7),
(26, 20, 'cdc', 'CDC实时同步', '/aggregation/cdc', 'aggregation/cdc/index', 2, 'aggregation', NULL, 6),
(30, 0, 'governance', '数据治理', '/governance', NULL, 1, 'governance', 'DataAnalysis', 3),
(31, 30, 'standard', '数据标准', '/governance/standard', 'governance/standard/index', 2, 'governance', NULL, 1),
(32, 30, 'warehouse', '仓库分层', '/governance/warehouse', 'governance/warehouse/index', 2, 'governance', NULL, 2),
(33, 30, 'modeling', '数据建模', '/governance/modeling', 'governance/modeling/index', 2, 'governance', NULL, 3),
(35, 30, 'cleaning', '数据清洗', '/governance/cleaning', 'governance/cleaning/index', 2, 'governance', 'MagicStick', 4),
(34, 30, 'indicator', '指标管理', '/governance/indicator', 'governance/indicator/index', 2, 'governance', NULL, 5),
(40, 0, 'development', '数据开发', '/development', NULL, 1, 'development', 'Edit', 4),
(41, 40, 'workflow', '工作流开发', '/development/workflow', 'development/workflow/index', 2, 'development', NULL, 1),
(42, 40, 'script', '脚本开发', '/development/script', 'development/script/index', 2, 'development', NULL, 2),
(43, 40, 'quality', '数据质量', '/development/quality', 'development/quality/index', 2, 'development', NULL, 3),
(44, 40, 'schedule', '数据调度', '/development/schedule', 'development/schedule/index', 2, 'development', NULL, 4),
(50, 0, 'service', '数据服务', '/service', NULL, 1, 'service', 'Share', 5),
(51, 50, 'application', '应用管理', '/service/application', 'service/application/index', 2, 'service', NULL, 1),
(52, 50, 'api', 'API管理', '/service/api', 'service/api/index', 2, 'service', NULL, 2),
(53, 50, 'catalog', '服务目录', '/service/catalog', 'service/catalog/index', 2, 'service', NULL, 3),
(54, 50, 'workorder', '工单管理', '/service/workorder', 'service/workorder/index', 2, 'service', NULL, 4),
(60, 0, 'asset', '数据资产', '/asset', NULL, 1, 'asset', 'PieChart', 6),
(61, 60, 'overview', '数据总览', '/asset/overview', 'asset/overview/index', 2, 'asset', NULL, 1),
(62, 60, 'map', '数据地图', '/asset/map', 'asset/map/index', 2, 'asset', NULL, 2),
(63, 60, 'asset_monitor', '资产监控', '/asset/monitor', 'asset/monitor/index', 2, 'asset', NULL, 3),
(70, 0, 'infrastructure', '基础支撑', '/infrastructure', NULL, 1, 'infrastructure', 'Monitor', 7),
(81, 70, 'infra_overview', '基础支撑总览', '/infrastructure/overview', 'infrastructure/overview/index', 2, 'infrastructure', 'DataBoard', 1),
(82, 70, 'infra_host', '主机管理', '/infrastructure/host', 'infrastructure/host/index', 2, 'infrastructure', 'Platform', 2),
(71, 70, 'component', '组件管理', '/infrastructure/component', 'infrastructure/component/index', 2, 'infrastructure', 'Setting', 3),
(72, 70, 'cluster', '集群管理', '/infrastructure/cluster', 'infrastructure/cluster/index', 2, 'infrastructure', 'Collection', 4),
(83, 70, 'infra_repository', '组件仓库', '/infrastructure/repository', 'infrastructure/repository/index', 2, 'infrastructure', 'FolderOpened', 5),
(84, 70, 'infra_config', '服务配置', '/infrastructure/config', 'infrastructure/config/index', 2, 'infrastructure', 'Document', 6),
(85, 70, 'infra_alert', '告警中心', '/infrastructure/alert', 'infrastructure/alert/index', 2, 'infrastructure', 'WarningFilled', 7),
(86, 70, 'infra_monitor', '资源监控', '/infrastructure/monitor', 'infrastructure/monitor/index', 2, 'infrastructure', 'Monitor', 8),
(87, 70, 'infra_resource_group', '资源组', '/infrastructure/resource-group', 'infrastructure/resource-group/index', 2, 'infrastructure', 'Grid', 9),
(88, 70, 'infra_storage_quota', '存储配额', '/infrastructure/storage-quota', 'infrastructure/storage-quota/index', 2, 'infrastructure', 'Coin', 10),
(74, 70, 'gov_proxy', '代理节点', '/infrastructure/proxynode', 'governance/proxynode/index', 2, 'infrastructure', 'Connection', 11),
(89, 70, 'infra_log', '运维日志', '/infrastructure/log', 'portal/log/index', 2, 'infrastructure', 'Tickets', 12);

INSERT INTO sys_menu (id, parent_id, menu_code, menu_name, path, component, menu_type, service_name, icon, sort_order, visible) VALUES
(73, 10, 'portal_profile', '个人中心', '/portal/profile', 'portal/profile/index', 2, 'portal', 'UserFilled', 99, 0),
(79, 10, 'portal_security', '安全管理', '/portal/security', 'portal/security/index', 2, 'portal', 'Lock', 7, 1),
(75, 30, 'gov_monitor', '治理监控', '/governance/monitor', 'governance/monitor/index', 2, 'governance', 'Monitor', 7, 1),
(76, 50, 'svc_unit', '服务单元', '/service/unit', 'service/unit/index', 2, 'service', 'Grid', 5, 1),
(77, 50, 'svc_ratelimit', '限流配置', '/service/ratelimit', 'service/ratelimit/index', 2, 'service', 'Timer', 6, 1),
(78, 50, 'svc_monitor', '服务监控', '/service/monitor', 'service/monitor/index', 2, 'service', 'DataLine', 7, 1);

-- 管理员拥有全部菜单
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 1, id FROM sys_menu;

-- 示例数据源
INSERT INTO agg_datasource (tenant_id, name, db_type, host, port, database_name, username, password, create_by) VALUES
(1, '业务MySQL库', 'MySQL', '127.0.0.1', 3306, 'business_db', 'root', '******', 'admin'),
(1, '数据仓库PG', 'PostgreSQL', '127.0.0.1', 5432, 'dw_db', 'postgres', '******', 'admin');

-- 数仓分层
INSERT INTO gov_warehouse_layer (tenant_id, layer_code, layer_name, description, sort_order) VALUES
(1, 'ODS', '贴源层', '原始数据层', 1),
(1, 'STD', '标准层', '数据标准层', 2),
(1, 'DWD', '明细层', '明细数据层', 3),
(1, 'DWS', '汇总层', '轻度汇总层', 4),
(1, 'ADS', '应用层', '应用数据层', 5);

-- 代码集示例
INSERT INTO gov_code_set (tenant_id, code, name, code_value, version, description) VALUES
(1, 'GENDER', '性别', '1=男,2=女', '1.0', '性别代码集'),
(1, 'STATUS', '状态', '0=禁用,1=启用', '1.0', '通用状态');

-- 数据建模示例
INSERT INTO gov_model_table (tenant_id, layer_code, table_en, table_cn, clean_rules, create_by) VALUES
(1, 'ODS', 'ods_user_info', '用户信息贴源表', NULL, 'admin'),
(1, 'STD', 'std_user_info', '用户信息标准表', '空值过滤,除去空白', 'admin');

-- 指标示例
INSERT INTO gov_stat_period (tenant_id, name, cron_expr, status) VALUES
(1, '日统计', '0 0 1 * * ?', 1),
(1, '月统计', '0 0 1 1 * ?', 1);

INSERT INTO gov_atomic_indicator (tenant_id, name, calc_logic, source_field, status) VALUES
(1, '用户数', 'COUNT(user_id)', 'user_id', 1);

-- 工作流/脚本示例
INSERT INTO dev_project (tenant_id, name, domain, description, create_by) VALUES
(1, '政务数据工程', '政务', '政务数据ETL工程', 'admin');

INSERT INTO dev_workflow (tenant_id, project_id, name, dag_json, status, create_by) VALUES
(1, 1, '用户数据同步流', '{"nodes":[{"id":"1","type":"extract","label":"数据抽取","position":{"x":80,"y":120}},{"id":"2","type":"transform","label":"数据转换","position":{"x":320,"y":120}},{"id":"3","type":"quality","label":"质量检查","position":{"x":560,"y":120}},{"id":"4","type":"load","label":"数据加载","position":{"x":800,"y":120}}],"edges":[{"id":"e1-2","source":"1","target":"2"},{"id":"e2-3","source":"2","target":"3"},{"id":"e3-4","source":"3","target":"4"}]}', 'DRAFT', 'admin');

INSERT INTO dev_script (tenant_id, name, script_type, content, status, create_by) VALUES
(1, '用户统计SQL', 'SQL', 'SELECT COUNT(*) FROM ods_user_info', 'DRAFT', 'admin');

INSERT INTO dev_quality_rule (tenant_id, name, dimension, rule_level, rule_expr, template_type) VALUES
(1, '用户ID唯一性', '唯一性', 'FIELD', 'user_id IS UNIQUE', '唯一性_TEMPLATE');

-- 服务目录
INSERT INTO svc_catalog_project (tenant_id, project_name, description, enabled) VALUES
(1, '政务服务目录', '政务数据API服务目录', 1);

INSERT INTO svc_catalog (tenant_id, project_id, parent_id, name, enabled) VALUES
(1, 1, 0, '人口服务', 1),
(1, 1, 1, '用户信息', 1);

INSERT INTO svc_service_unit (tenant_id, unit_name, description, status) VALUES
(1, '用户信息服务单元', '提供用户基础信息查询', 1);

-- 数据资产
INSERT INTO asset_table (tenant_id, table_en, table_cn, database_name, datasource_id, layer_code, row_count, is_key_asset, create_by) VALUES
(1, 'ods_user_info', '用户信息表', 'dw_db', 1, 'ODS', 10000, 1, 'admin'),
(1, 'std_user_info', '用户标准表', 'dw_db', 2, 'STD', 9800, 0, 'admin');

INSERT INTO asset_field (table_id, field_en, field_cn, field_type, is_sensitive) VALUES
(1, 'user_id', '用户ID', 'BIGINT', 0),
(1, 'phone', '手机号', 'VARCHAR', 1),
(1, 'id_card', '身份证号', 'VARCHAR', 1),
(2, 'user_id', '用户ID', 'BIGINT', 0),
(2, 'phone', '手机号', 'VARCHAR', 1),
(2, 'id_card', '身份证号', 'VARCHAR', 1),
(2, 'clean_time', '清洗时间', 'TIMESTAMP', 0);

INSERT INTO asset_lineage (tenant_id, source_table_id, target_table_id, relation_type) VALUES
(1, 1, 2, 'ETL');

INSERT INTO asset_table (tenant_id, table_en, table_cn, database_name, layer_code, row_count, is_key_asset, create_by) VALUES
(1, 'dwd_user_detail', '用户明细表', 'dw_db', 'DWD', 9500, 0, 'admin'),
(1, 'dws_user_daily', '用户日汇总', 'dw_db', 'DWS', 3650, 0, 'admin'),
(1, 'ads_user_report', '用户分析报表', 'dw_db', 'ADS', 120, 1, 'admin');

INSERT INTO asset_lineage (tenant_id, source_table_id, target_table_id, relation_type) VALUES
(1, 2, 3, 'CLEAN'),
(1, 3, 4, 'AGG'),
(1, 4, 5, 'REPORT');

-- 基础支撑
INSERT INTO infra_host (tenant_id, hostname, ip, cpu_cores, memory_gb, disk_gb, status) VALUES
(1, 'node-master', '192.168.1.101', 16, 64, 2000, 'ONLINE'),
(1, 'node-worker-1', '192.168.1.102', 32, 128, 4000, 'ONLINE');

INSERT INTO infra_component (tenant_id, host_id, component_type, version, status) VALUES
(1, 1, 'HDFS', '3.3.4', 'RUNNING'),
(1, 1, 'YARN', '3.3.4', 'RUNNING'),
(1, 2, 'Kafka', '3.4.0', 'RUNNING');

INSERT INTO infra_cluster (tenant_id, name, description, host_count, status) VALUES
(1, '张掖大数据集群', '生产环境主集群', 2, 'NORMAL');

INSERT INTO infra_repository (tenant_id, component_type, package_name, version, file_path, package_size_mb, checksum, compatible_os, status, remark) VALUES
(1, 'HDFS', 'hadoop-3.3.4.tar.gz', '3.3.4', '/opt/packages/hadoop-3.3.4.tar.gz', 528.5, 'c8d5d7f4b2', 'CentOS 7.6', 'READY', 'NameNode 和 DataNode 部署包'),
(1, 'Flink', 'flink-1.17.1.tar.gz', '1.17.1', '/opt/packages/flink-1.17.1.tar.gz', 312.8, 'f3ac92b7a1', 'CentOS 7.6', 'DISTRIBUTED', 'Flink SQL 运行包');

INSERT INTO infra_alert (tenant_id, alert_name, alert_level, alert_type, target_type, target_name, rule_expr, message, status, handle_suggestion, last_trigger_time) VALUES
(1, 'HDFS 容量预警', 'WARN', 'CAPACITY', 'CLUSTER', '张掖大数据集群', 'disk_usage > 80', 'HDFS 存储空间使用率超过阈值', 'ACTIVE', '扩容数据盘或清理历史文件', CURRENT_TIMESTAMP),
(1, 'YARN 资源池告警', 'INFO', 'RESOURCE_POOL', 'QUEUE', 'default', 'pending_app > 20', 'YARN 队列积压任务较多', 'ACKED', '检查调度优先级和资源池配额', CURRENT_TIMESTAMP);

INSERT INTO infra_resource_group (tenant_id, group_name, resource_type, resource_key, description, max_cores, max_memory_gb, status) VALUES
(1, 'default', 'YARN_QUEUE', 'root.default', '默认计算资源池', 24, 96, 'ENABLED');

INSERT INTO storage_quota (tenant_id, datasource_id, database_name, max_size_gb, current_usage_gb, alert_threshold_pct, status) VALUES
(1, NULL, 'dw_db', 1000, 580, 80, 'NORMAL');
