-- 租户
CREATE TABLE IF NOT EXISTS sys_tenant (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_code VARCHAR(64) NOT NULL UNIQUE,
    tenant_name VARCHAR(128) NOT NULL UNIQUE,
    status TINYINT DEFAULT 1 COMMENT '0冻结 1正常',
    start_time TIMESTAMP,
    end_time TIMESTAMP,
    create_by VARCHAR(64),
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted TINYINT DEFAULT 0
);

-- 部门
CREATE TABLE IF NOT EXISTS sys_dept (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    parent_id BIGINT DEFAULT 0,
    dept_name VARCHAR(128) NOT NULL,
    sort_order INT DEFAULT 0,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted TINYINT DEFAULT 0
);

-- 角色
CREATE TABLE IF NOT EXISTS sys_role (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    role_name VARCHAR(64) NOT NULL,
    role_key VARCHAR(64) NOT NULL,
    description VARCHAR(256),
    data_scope TINYINT DEFAULT 1 COMMENT '1全部 2自定义 3本级及子级 4本级',
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted TINYINT DEFAULT 0
);

-- 用户
CREATE TABLE IF NOT EXISTS sys_user (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    dept_id BIGINT,
    username VARCHAR(64) NOT NULL UNIQUE,
    password VARCHAR(128) NOT NULL,
    nickname VARCHAR(64),
    phone VARCHAR(20),
    avatar VARCHAR(256),
    status TINYINT DEFAULT 1 COMMENT '0锁定 1正常',
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted TINYINT DEFAULT 0
);

CREATE TABLE IF NOT EXISTS sys_user_role (
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    PRIMARY KEY (user_id, role_id)
);

-- 菜单
CREATE TABLE IF NOT EXISTS sys_menu (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    parent_id BIGINT DEFAULT 0,
    menu_code VARCHAR(64) NOT NULL UNIQUE,
    menu_name VARCHAR(128) NOT NULL,
    description VARCHAR(256),
    path VARCHAR(256),
    component VARCHAR(256),
    menu_type TINYINT DEFAULT 1 COMMENT '1目录 2菜单 3按钮',
    service_name VARCHAR(64),
    icon VARCHAR(64),
    sort_order INT DEFAULT 0,
    visible TINYINT DEFAULT 1,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted TINYINT DEFAULT 0
);

CREATE TABLE IF NOT EXISTS sys_role_menu (
    role_id BIGINT NOT NULL,
    menu_id BIGINT NOT NULL,
    PRIMARY KEY (role_id, menu_id)
);

-- 操作日志
CREATE TABLE IF NOT EXISTS sys_oper_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT,
    user_id BIGINT,
    username VARCHAR(64),
    event_name VARCHAR(128),
    result TINYINT DEFAULT 1 COMMENT '0失败 1成功',
    method VARCHAR(16),
    resource_type VARCHAR(64),
    resource_name VARCHAR(128),
    ip VARCHAR(64),
    detail CLOB,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 数据源
CREATE TABLE IF NOT EXISTS agg_datasource (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    name VARCHAR(128) NOT NULL,
    db_type VARCHAR(32) NOT NULL,
    host VARCHAR(128),
    port INT,
    database_name VARCHAR(128),
    username VARCHAR(128),
    password VARCHAR(256),
    file_path VARCHAR(512),
    config_json CLOB,
    locked TINYINT DEFAULT 0 COMMENT '是否被数据流锁定',
    create_by VARCHAR(64),
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted TINYINT DEFAULT 0
);

-- 增量字段
CREATE TABLE IF NOT EXISTS agg_increment_field (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    table_name VARCHAR(128) NOT NULL,
    field_name VARCHAR(128) NOT NULL,
    field_position INT,
    field_type VARCHAR(64),
    field_comment VARCHAR(256),
    increment_value VARCHAR(256),
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted TINYINT DEFAULT 0
);

-- 数据流
CREATE TABLE IF NOT EXISTS agg_dataflow (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    name VARCHAR(128) NOT NULL,
    source_id BIGINT NOT NULL,
    target_id BIGINT NOT NULL,
    write_mode VARCHAR(32) DEFAULT 'insert',
    fault_tolerance INT DEFAULT 0,
    pre_sql CLOB,
    field_mapping CLOB,
    schedule_cron VARCHAR(64),
    schedule_enabled TINYINT DEFAULT 0,
    status VARCHAR(32) DEFAULT 'NOT_STARTED',
    read_count BIGINT DEFAULT 0,
    write_count BIGINT DEFAULT 0,
    last_run_time TIMESTAMP,
    create_by VARCHAR(64),
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted TINYINT DEFAULT 0
);

-- 整库同步
CREATE TABLE IF NOT EXISTS agg_full_sync (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    name VARCHAR(128) NOT NULL,
    sync_type VARCHAR(32),
    source_id BIGINT NOT NULL,
    target_id BIGINT NOT NULL,
    tables_json CLOB,
    fault_limit INT DEFAULT 0,
    target_action VARCHAR(32),
    remark VARCHAR(512),
    status VARCHAR(32) DEFAULT 'NOT_STARTED',
    table_total INT DEFAULT 0,
    success_count INT DEFAULT 0,
    fail_count INT DEFAULT 0,
    running_count INT DEFAULT 0,
    last_run_time TIMESTAMP,
    create_by VARCHAR(64),
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted TINYINT DEFAULT 0
);

-- CDC实时同步
CREATE TABLE IF NOT EXISTS agg_cdc_task (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    name VARCHAR(128) NOT NULL,
    source_id BIGINT NOT NULL,
    target_id BIGINT NOT NULL,
    operation_mode VARCHAR(32),
    field_mapping CLOB,
    remark VARCHAR(512),
    status VARCHAR(32) DEFAULT 'STOPPED',
    start_time TIMESTAMP,
    create_by VARCHAR(64),
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted TINYINT DEFAULT 0
);

-- 数据标准-标准表目录
CREATE TABLE IF NOT EXISTS gov_standard_catalog (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    parent_id BIGINT DEFAULT 0,
    name VARCHAR(128) NOT NULL,
    catalog_type VARCHAR(32) DEFAULT 'TABLE',
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted TINYINT DEFAULT 0
);

-- API应用
CREATE TABLE IF NOT EXISTS svc_application (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    app_name VARCHAR(128) NOT NULL UNIQUE,
    app_key VARCHAR(64) NOT NULL UNIQUE,
    app_secret VARCHAR(128),
    description VARCHAR(512),
    status TINYINT DEFAULT 1,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted TINYINT DEFAULT 0
);

-- API服务
CREATE TABLE IF NOT EXISTS svc_api (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    api_name VARCHAR(128) NOT NULL,
    request_method VARCHAR(16) DEFAULT 'GET',
    update_frequency VARCHAR(32),
    description VARCHAR(512),
    catalog_id BIGINT,
    create_type VARCHAR(32) DEFAULT 'WIZARD',
    sql_content CLOB,
    status TINYINT DEFAULT 0 COMMENT '0草稿 1已发布 2已下线',
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted TINYINT DEFAULT 0
);

-- 服务目录
CREATE TABLE IF NOT EXISTS svc_catalog (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    project_id BIGINT DEFAULT 0,
    parent_id BIGINT DEFAULT 0,
    name VARCHAR(128) NOT NULL,
    enabled TINYINT DEFAULT 1,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted TINYINT DEFAULT 0
);

-- ========== 数据治理 ==========
CREATE TABLE IF NOT EXISTS gov_code_set (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    catalog_id BIGINT DEFAULT 0,
    code VARCHAR(64) NOT NULL,
    name VARCHAR(128) NOT NULL,
    code_value VARCHAR(256),
    version VARCHAR(32) DEFAULT '1.0',
    description VARCHAR(512),
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted TINYINT DEFAULT 0
);

CREATE TABLE IF NOT EXISTS gov_word (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    catalog_id BIGINT DEFAULT 0,
    word_en VARCHAR(128) NOT NULL,
    word_cn VARCHAR(128),
    definition VARCHAR(512),
    version VARCHAR(32) DEFAULT '1.0',
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted TINYINT DEFAULT 0
);

CREATE TABLE IF NOT EXISTS gov_warehouse_layer (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    layer_code VARCHAR(16) NOT NULL,
    layer_name VARCHAR(64) NOT NULL,
    description VARCHAR(256),
    sort_order INT DEFAULT 0,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted TINYINT DEFAULT 0
);

CREATE TABLE IF NOT EXISTS gov_warehouse_catalog (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    layer_id BIGINT NOT NULL,
    parent_id BIGINT DEFAULT 0,
    name VARCHAR(128) NOT NULL,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted TINYINT DEFAULT 0
);

CREATE TABLE IF NOT EXISTS gov_model_table (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    layer_code VARCHAR(16) NOT NULL,
    table_en VARCHAR(128) NOT NULL,
    table_cn VARCHAR(128),
    datasource_id BIGINT,
    clean_rules CLOB,
    remark VARCHAR(512),
    create_by VARCHAR(64),
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted TINYINT DEFAULT 0
);

CREATE TABLE IF NOT EXISTS gov_stat_period (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    name VARCHAR(128) NOT NULL,
    cron_expr VARCHAR(64),
    status TINYINT DEFAULT 0 COMMENT '0草稿 1发布 2下线',
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted TINYINT DEFAULT 0
);

CREATE TABLE IF NOT EXISTS gov_atomic_indicator (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    name VARCHAR(128) NOT NULL,
    calc_logic CLOB,
    source_field VARCHAR(128),
    status TINYINT DEFAULT 0,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted TINYINT DEFAULT 0
);

CREATE TABLE IF NOT EXISTS gov_business_limit (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    name VARCHAR(128) NOT NULL,
    source_field VARCHAR(128),
    calc_logic CLOB,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted TINYINT DEFAULT 0
);

CREATE TABLE IF NOT EXISTS gov_derived_indicator (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    name VARCHAR(128) NOT NULL,
    atomic_id BIGINT,
    period_id BIGINT,
    limit_id BIGINT,
    granularity VARCHAR(64),
    data_theme VARCHAR(64),
    status TINYINT DEFAULT 0,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted TINYINT DEFAULT 0
);

CREATE TABLE IF NOT EXISTS gov_proxy_node (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    name VARCHAR(128) NOT NULL,
    host VARCHAR(128),
    port INT,
    status VARCHAR(32) DEFAULT 'STOPPED',
    remark VARCHAR(512),
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted TINYINT DEFAULT 0
);

-- ========== 数据开发 ==========
CREATE TABLE IF NOT EXISTS dev_project (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    name VARCHAR(128) NOT NULL,
    domain VARCHAR(64),
    description VARCHAR(512),
    create_by VARCHAR(64),
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted TINYINT DEFAULT 0
);

CREATE TABLE IF NOT EXISTS dev_workflow (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    project_id BIGINT,
    name VARCHAR(128) NOT NULL,
    dag_json CLOB,
    status VARCHAR(32) DEFAULT 'DRAFT',
    schedule_cron VARCHAR(64),
    create_by VARCHAR(64),
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted TINYINT DEFAULT 0
);

CREATE TABLE IF NOT EXISTS dev_script (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    name VARCHAR(128) NOT NULL,
    script_type VARCHAR(32) DEFAULT 'SQL',
    content CLOB,
    status VARCHAR(32) DEFAULT 'DRAFT',
    last_run_time TIMESTAMP,
    create_by VARCHAR(64),
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted TINYINT DEFAULT 0
);

CREATE TABLE IF NOT EXISTS dev_quality_rule (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    name VARCHAR(128) NOT NULL,
    dimension VARCHAR(32),
    rule_level VARCHAR(32) DEFAULT 'FIELD',
    rule_expr CLOB,
    template_type VARCHAR(64),
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted TINYINT DEFAULT 0
);

CREATE TABLE IF NOT EXISTS dev_quality_task (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    rule_id BIGINT NOT NULL,
    target_table VARCHAR(128),
    status VARCHAR(32) DEFAULT 'PENDING',
    score DECIMAL(5,2),
    issue_count INT DEFAULT 0,
    last_run_time TIMESTAMP,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted TINYINT DEFAULT 0
);

CREATE TABLE IF NOT EXISTS dev_schedule_task (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    name VARCHAR(128) NOT NULL,
    workflow_id BIGINT,
    trigger_type VARCHAR(32) DEFAULT 'CRON',
    cron_expr VARCHAR(64),
    status VARCHAR(32) DEFAULT 'STOPPED',
    last_run_time TIMESTAMP,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted TINYINT DEFAULT 0
);

CREATE TABLE IF NOT EXISTS dev_hdfs_file (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    file_path VARCHAR(512),
    file_name VARCHAR(256),
    is_dir TINYINT DEFAULT 0,
    file_size BIGINT DEFAULT 0,
    content_type VARCHAR(128),
    parent_path VARCHAR(512),
    create_by VARCHAR(64),
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted TINYINT DEFAULT 0
);

CREATE TABLE IF NOT EXISTS dev_udf (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    udf_name VARCHAR(128) NOT NULL,
    udf_type VARCHAR(32),
    owner VARCHAR(64),
    class_name VARCHAR(256),
    jar_path VARCHAR(512),
    create_sql CLOB,
    description VARCHAR(512),
    status TINYINT DEFAULT 0,
    create_by VARCHAR(64),
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted TINYINT DEFAULT 0
);

CREATE TABLE IF NOT EXISTS dev_script_execution_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    script_id BIGINT,
    tenant_id BIGINT NOT NULL,
    status VARCHAR(32),
    output CLOB,
    error_log CLOB,
    exit_code INT,
    duration_ms BIGINT,
    trigger_by VARCHAR(64),
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS dev_schedule_execution_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    task_id BIGINT,
    task_type VARCHAR(32),
    tenant_id BIGINT NOT NULL,
    status VARCHAR(32),
    output CLOB,
    error_log CLOB,
    start_time TIMESTAMP,
    end_time TIMESTAMP,
    duration_ms BIGINT,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- ========== 数据服务扩展 ==========
CREATE TABLE IF NOT EXISTS svc_service_unit (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    unit_name VARCHAR(128) NOT NULL,
    description VARCHAR(512),
    status TINYINT DEFAULT 0 COMMENT '0草稿 1发布 2下线',
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted TINYINT DEFAULT 0
);

CREATE TABLE IF NOT EXISTS svc_catalog_project (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    project_name VARCHAR(128) NOT NULL,
    description VARCHAR(512),
    enabled TINYINT DEFAULT 1,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted TINYINT DEFAULT 0
);

CREATE TABLE IF NOT EXISTS svc_workorder (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    api_id BIGINT,
    app_id BIGINT,
    api_name VARCHAR(128),
    app_name VARCHAR(128),
    applicant VARCHAR(64),
    apply_type VARCHAR(32),
    reason VARCHAR(512),
    expire_time TIMESTAMP,
    status TINYINT DEFAULT 0 COMMENT '0待审 1通过 2拒绝',
    approve_reason VARCHAR(512),
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS svc_rate_limit (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    api_id BIGINT,
    api_name VARCHAR(128),
    time_window_sec INT DEFAULT 60,
    max_requests INT DEFAULT 100,
    enabled TINYINT DEFAULT 1,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted TINYINT DEFAULT 0
);

CREATE TABLE IF NOT EXISTS svc_api_call_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    api_id BIGINT,
    api_name VARCHAR(128),
    app_name VARCHAR(128),
    success TINYINT DEFAULT 1,
    response_ms INT,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- ========== 数据资产 ==========
CREATE TABLE IF NOT EXISTS asset_table (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    table_en VARCHAR(128) NOT NULL,
    table_cn VARCHAR(128),
    database_name VARCHAR(128),
    datasource_id BIGINT,
    layer_code VARCHAR(16),
    row_count BIGINT DEFAULT 0,
    is_key_asset TINYINT DEFAULT 0,
    create_by VARCHAR(64),
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted TINYINT DEFAULT 0
);

CREATE TABLE IF NOT EXISTS asset_field (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    table_id BIGINT NOT NULL,
    field_en VARCHAR(128) NOT NULL,
    field_cn VARCHAR(128),
    field_type VARCHAR(64),
    description VARCHAR(256),
    is_sensitive TINYINT DEFAULT 0,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS asset_lineage (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    source_table_id BIGINT NOT NULL,
    target_table_id BIGINT NOT NULL,
    relation_type VARCHAR(32) DEFAULT 'DERIVE',
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- ========== 基础支撑 ==========
CREATE TABLE IF NOT EXISTS infra_host (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    hostname VARCHAR(128) NOT NULL,
    ip VARCHAR(64),
    cpu_cores INT,
    memory_gb INT,
    disk_gb INT,
    status VARCHAR(32) DEFAULT 'ONLINE',
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted TINYINT DEFAULT 0
);

CREATE TABLE IF NOT EXISTS infra_component (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    host_id BIGINT,
    component_type VARCHAR(32) NOT NULL,
    version VARCHAR(32),
    status VARCHAR(32) DEFAULT 'RUNNING',
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted TINYINT DEFAULT 0
);

CREATE TABLE IF NOT EXISTS infra_cluster (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    name VARCHAR(128) NOT NULL,
    description VARCHAR(512),
    host_count INT DEFAULT 0,
    status VARCHAR(32) DEFAULT 'NORMAL',
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted TINYINT DEFAULT 0
);

-- ========== 补充缺失表 ==========

CREATE TABLE IF NOT EXISTS agg_dataflow_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    dataflow_id BIGINT DEFAULT 0,
    run_id VARCHAR(64),
    log_level VARCHAR(16),
    message CLOB,
    record_count BIGINT DEFAULT 0,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS asset_favorite (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    user_id BIGINT,
    asset_table_id BIGINT,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS asset_follow (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    user_id BIGINT,
    asset_table_id BIGINT,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS asset_tag (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    tag_name VARCHAR(128) NOT NULL,
    tag_type VARCHAR(32),
    tag_color VARCHAR(32),
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted TINYINT DEFAULT 0
);

CREATE TABLE IF NOT EXISTS asset_tag_relation (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    asset_table_id BIGINT,
    tag_id BIGINT,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS dev_file_watcher (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    name VARCHAR(128) NOT NULL,
    watch_directory VARCHAR(512),
    file_name_pattern VARCHAR(256),
    target_workflow_id BIGINT,
    status VARCHAR(32) DEFAULT 'STOPPED',
    create_by VARCHAR(64),
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted TINYINT DEFAULT 0
);

CREATE TABLE IF NOT EXISTS dev_quality_score_history (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    task_id BIGINT,
    score DECIMAL(5,2),
    issue_count INT DEFAULT 0,
    check_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS dev_script_version (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    script_id BIGINT NOT NULL,
    version_num INT DEFAULT 1,
    content CLOB,
    change_note VARCHAR(512),
    create_by VARCHAR(64),
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS gov_business_domain (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    layer_id BIGINT,
    parent_id BIGINT DEFAULT 0,
    domain_name VARCHAR(128) NOT NULL,
    domain_code VARCHAR(64),
    description VARCHAR(512),
    sort_order INT DEFAULT 0,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted TINYINT DEFAULT 0
);

CREATE TABLE IF NOT EXISTS gov_composite_indicator (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    indicator_code VARCHAR(64),
    indicator_name VARCHAR(128) NOT NULL,
    expression VARCHAR(512),
    indicator_ids VARCHAR(512),
    unit VARCHAR(64),
    description VARCHAR(512),
    status VARCHAR(32) DEFAULT 'DRAFT',
    create_by VARCHAR(64),
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted TINYINT DEFAULT 0
);

CREATE TABLE IF NOT EXISTS gov_cross_domain_metadata (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    source_name VARCHAR(128),
    source_type VARCHAR(32),
    export_format VARCHAR(32),
    metadata_json CLOB,
    sync_status VARCHAR(32) DEFAULT 'PENDING',
    version VARCHAR(32),
    create_by VARCHAR(64),
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted TINYINT DEFAULT 0
);

CREATE TABLE IF NOT EXISTS infra_resource_group (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    group_name VARCHAR(128) NOT NULL,
    resource_type VARCHAR(64),
    resource_key VARCHAR(256),
    description VARCHAR(512),
    max_cores INT DEFAULT 0,
    max_memory_gb DECIMAL(10,2) DEFAULT 0,
    status VARCHAR(32) DEFAULT 'NORMAL',
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted TINYINT DEFAULT 0
);

CREATE TABLE IF NOT EXISTS meta_collector_task (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    task_name VARCHAR(128) NOT NULL,
    datasource_id BIGINT,
    collect_type VARCHAR(32),
    target_pattern VARCHAR(512),
    cron_expr VARCHAR(64),
    trigger_on_change TINYINT DEFAULT 0,
    status VARCHAR(32) DEFAULT 'STOPPED',
    last_run_time TIMESTAMP,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted TINYINT DEFAULT 0
);

CREATE TABLE IF NOT EXISTS meta_collector_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    task_id BIGINT,
    status VARCHAR(32),
    table_count INT DEFAULT 0,
    field_count INT DEFAULT 0,
    error_log CLOB,
    duration_ms BIGINT DEFAULT 0,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS storage_quota (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    datasource_id BIGINT,
    database_name VARCHAR(128),
    max_size_gb DECIMAL(12,2) DEFAULT 0,
    current_usage_gb DECIMAL(12,2) DEFAULT 0,
    alert_threshold_pct INT DEFAULT 80,
    status VARCHAR(32) DEFAULT 'NORMAL',
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted TINYINT DEFAULT 0
);

CREATE TABLE IF NOT EXISTS sys_config (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    config_key VARCHAR(128) NOT NULL,
    config_value CLOB,
    description VARCHAR(512),
    env VARCHAR(32) DEFAULT 'DEV',
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS user_activity_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    user_id BIGINT,
    username VARCHAR(64),
    activity_type VARCHAR(64),
    target_desc VARCHAR(256),
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
