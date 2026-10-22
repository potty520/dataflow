-- 由 deploy/gen_mysql_schema.py 从 schema.sql 自动生成，请勿手工编辑。
-- 方言：MySQL 8.0 / InnoDB / utf8mb4；与 schema.sql（H2/PostgreSQL）保持结构一致。
-- 生成规则：IDENTITY -> AUTO_INCREMENT；独立索引内联为 KEY；TIMESTAMP -> DATETIME。

-- ============================================================================
-- 数据治理平台 DDL  V1.0
-- 命名遵循 01 文档 §9.1：{域前缀}_{对象}，公共字段 tenant_id/create_by/create_time/update_time/del_flag
-- 语法保持 H2(PostgreSQL 模式) 与 PostgreSQL/达梦/Kingbase 双向兼容：
--   不使用库独有语法，不使用自增关键字，不使用 COMMENT ON，不使用 DROP/ALTER
-- ============================================================================

-- ---------------------------- 认证与租户 dgp-auth ----------------------------
CREATE TABLE IF NOT EXISTS sys_tenant (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    code VARCHAR(64) NOT NULL UNIQUE,
    name VARCHAR(128) NOT NULL,
    parent_id BIGINT DEFAULT 0,
    tenant_path VARCHAR(255) DEFAULT '/',
    tenant_level INT DEFAULT 1,
    industry_code VARCHAR(32) DEFAULT 'GOV',
    isolation_mode VARCHAR(16) DEFAULT 'SHARED',
    status VARCHAR(16) DEFAULT 'ENABLED',
    create_time DATETIME
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE IF NOT EXISTS sys_role (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    code VARCHAR(64) NOT NULL UNIQUE,
    name VARCHAR(64) NOT NULL,
    perms TEXT,
    description VARCHAR(255)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE IF NOT EXISTS sys_user (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT DEFAULT 1,
    username VARCHAR(64) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    real_name VARCHAR(64),
    role_code VARCHAR(64),
    dept VARCHAR(128),
    email VARCHAR(128),
    phone VARCHAR(32),
    status VARCHAR(16) DEFAULT '启用',
    last_login_time DATETIME,
    create_time DATETIME,
    KEY idx_sys_user_role (role_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE IF NOT EXISTS sys_config (
    config_key VARCHAR(128) PRIMARY KEY,
    config_value TEXT,
    description VARCHAR(255),
    update_time DATETIME
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE IF NOT EXISTS sys_async_task (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    task_id VARCHAR(64) NOT NULL,
    task_type VARCHAR(64),
    status VARCHAR(16),
    progress INT DEFAULT 0,
    result TEXT,
    error TEXT,
    start_time DATETIME,
    end_time DATETIME,
    UNIQUE KEY ux_svc_oauth_token (task_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE IF NOT EXISTS sys_login_log (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(64),
    ip VARCHAR(64),
    status VARCHAR(16),
    message VARCHAR(255),
    user_agent VARCHAR(255),
    login_time DATETIME
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- ---------------------------- 元数据 dgp-metadata ----------------------------
CREATE TABLE IF NOT EXISTS md_datasource (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT DEFAULT 1,
    code VARCHAR(64) NOT NULL UNIQUE,
    name VARCHAR(128) NOT NULL,
    dtype VARCHAR(32) NOT NULL,
    host VARCHAR(128),
    port VARCHAR(16),
    db_name VARCHAR(128),
    username VARCHAR(64),
    password_enc VARCHAR(255),
    industry VARCHAR(32) DEFAULT 'GOV',
    domain VARCHAR(64),
    owner VARCHAR(64),
    conn_status VARCHAR(16) DEFAULT '未测试',
    last_test_time DATETIME,
    latency_ms INT,
    collect_mode VARCHAR(16) DEFAULT 'AUTO',
    remark VARCHAR(255),
    create_time DATETIME
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE IF NOT EXISTS md_table (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT DEFAULT 1,
    datasource_id BIGINT NOT NULL,
    name VARCHAR(128) NOT NULL,
    cn_name VARCHAR(128),
    obj_type VARCHAR(16) DEFAULT 'TABLE',
    layer VARCHAR(16),
    domain VARCHAR(64),
    row_count BIGINT DEFAULT 0,
    data_size_mb DECIMAL(14,2) DEFAULT 0,
    owner VARCHAR(64),
    description VARCHAR(500),
    biz_desc VARCHAR(500),
    update_freq VARCHAR(32),
    last_ddl_time DATETIME,
    status VARCHAR(16) DEFAULT '已发布',
    tags VARCHAR(255),
    quality_score DECIMAL(6,2) DEFAULT 0,
    access_count BIGINT DEFAULT 0,
    svc_calls BIGINT DEFAULT 0,
    sensitive_level VARCHAR(8) DEFAULT 'L1',
    industry VARCHAR(32),
    create_time DATETIME,
    KEY idx_md_table_ds (datasource_id),
    KEY idx_md_table_layer (layer),
    UNIQUE KEY ux_md_table_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE IF NOT EXISTS md_column (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    table_id BIGINT NOT NULL,
    name VARCHAR(128) NOT NULL,
    cn_name VARCHAR(128),
    data_type VARCHAR(64),
    data_len VARCHAR(32),
    nullable INT DEFAULT 1,
    is_pk INT DEFAULT 0,
    column_order INT DEFAULT 0,
    comment VARCHAR(500),
    std_code VARCHAR(64),
    sensitive_level VARCHAR(8) DEFAULT 'L1',
    mask_algo VARCHAR(32),
    KEY idx_md_col_table (table_id),
    UNIQUE KEY ux_md_col_name (table_id, name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE IF NOT EXISTS md_lineage_edge (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT DEFAULT 1,
    src_table VARCHAR(128),
    src_column VARCHAR(128),
    dst_table VARCHAR(128),
    dst_column VARCHAR(128),
    job_name VARCHAR(128),
    transform_type VARCHAR(32),
    transform_expr VARCHAR(500),
    granularity VARCHAR(16) DEFAULT 'TABLE',
    create_time DATETIME,
    KEY idx_md_lin_src (src_table),
    KEY idx_md_lin_dst (dst_table),
    KEY idx_md_lin_edge (src_table, dst_table)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE IF NOT EXISTS md_collect_task (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT DEFAULT 1,
    datasource_id BIGINT,
    name VARCHAR(128),
    mode VARCHAR(16) DEFAULT 'AUTO',
    cron VARCHAR(64),
    status VARCHAR(16) DEFAULT 'ENABLED',
    last_run_time DATETIME,
    next_run_time DATETIME,
    new_tables INT DEFAULT 0,
    changed_cols INT DEFAULT 0,
    owner VARCHAR(64),
    create_time DATETIME
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE IF NOT EXISTS md_collect_instance (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    task_id BIGINT,
    datasource_id BIGINT,
    ds_name VARCHAR(128),
    status VARCHAR(16),
    start_time DATETIME,
    end_time DATETIME,
    duration_ms BIGINT,
    new_tables INT DEFAULT 0,
    changed_cols INT DEFAULT 0,
    message VARCHAR(500),
    operator VARCHAR(64)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE IF NOT EXISTS md_object_version (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    object_type VARCHAR(32),
    object_id BIGINT,
    object_name VARCHAR(128),
    version VARCHAR(32),
    snapshot TEXT,
    change_note VARCHAR(255),
    operator VARCHAR(64),
    create_time DATETIME
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE IF NOT EXISTS md_biz_term (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT DEFAULT 1,
    industry VARCHAR(32),
    term_code VARCHAR(64),
    term_name VARCHAR(128),
    term_name_en VARCHAR(128),
    definition VARCHAR(500),
    column_pattern VARCHAR(255),
    std_element_code VARCHAR(64),
    synonyms VARCHAR(255),
    security_level VARCHAR(8) DEFAULT 'L1',
    source VARCHAR(64)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- ---------------------------- 数据建模 dgp-model ----------------------------
CREATE TABLE IF NOT EXISTS mdl_model (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT DEFAULT 1,
    code VARCHAR(64) NOT NULL UNIQUE,
    name VARCHAR(128) NOT NULL,
    cn_name VARCHAR(128),
    model_type VARCHAR(16) NOT NULL,
    layer VARCHAR(16),
    industry VARCHAR(32),
    domain VARCHAR(64),
    status VARCHAR(16) DEFAULT 'DRAFT',
    version VARCHAR(32) DEFAULT '1.0.0',
    owner VARCHAR(64),
    description VARCHAR(500),
    create_time DATETIME,
    update_time DATETIME,
    publish_time DATETIME
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE IF NOT EXISTS mdl_entity (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    model_id BIGINT NOT NULL,
    code VARCHAR(128) NOT NULL,
    name VARCHAR(128),
    cn_name VARCHAR(128),
    entity_type VARCHAR(16) DEFAULT 'DIM',
    subject_domain VARCHAR(64),
    description VARCHAR(500),
    pos_x DECIMAL(10,2) DEFAULT 0,
    pos_y DECIMAL(10,2) DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE IF NOT EXISTS mdl_attribute (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    entity_id BIGINT NOT NULL,
    code VARCHAR(128) NOT NULL,
    name VARCHAR(128),
    cn_name VARCHAR(128),
    data_type VARCHAR(32),
    data_len VARCHAR(32),
    is_pk INT DEFAULT 0,
    nullable INT DEFAULT 1,
    std_element_code VARCHAR(64),
    security_level VARCHAR(8) DEFAULT 'L1',
    description VARCHAR(500),
    attr_order INT DEFAULT 0,
    KEY idx_mdl_attr_entity (entity_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE IF NOT EXISTS mdl_relation (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    model_id BIGINT,
    source_entity VARCHAR(128),
    target_entity VARCHAR(128),
    relation_type VARCHAR(16),
    join_condition VARCHAR(500)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE IF NOT EXISTS mdl_version (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    model_id BIGINT,
    version VARCHAR(32),
    snapshot TEXT,
    change_note VARCHAR(255),
    operator VARCHAR(64),
    create_time DATETIME
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE IF NOT EXISTS mdl_publish_flow (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    model_id BIGINT,
    model_name VARCHAR(128),
    version VARCHAR(32),
    applicant VARCHAR(64),
    auditor VARCHAR(64),
    status VARCHAR(16) DEFAULT 'PENDING',
    comment VARCHAR(500),
    create_time DATETIME,
    finish_time DATETIME
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE IF NOT EXISTS mdl_ddl_record (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    model_id BIGINT,
    entity_id BIGINT,
    table_name VARCHAR(128),
    ddl_text TEXT,
    status VARCHAR(16),
    operator VARCHAR(64),
    create_time DATETIME
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- ---------------------------- 数据标准 dgp-standard ----------------------------
CREATE TABLE IF NOT EXISTS std_element (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT DEFAULT 1,
    code VARCHAR(64) NOT NULL UNIQUE,
    name VARCHAR(128),
    cn_name VARCHAR(128),
    category VARCHAR(64),
    data_type VARCHAR(32),
    data_len VARCHAR(32),
    fmt VARCHAR(64),
    business_desc VARCHAR(500),
    rule_desc VARCHAR(500),
    source VARCHAR(64),
    industry VARCHAR(32),
    status VARCHAR(16) DEFAULT 'PUBLISHED',
    version VARCHAR(32) DEFAULT '1.0.0',
    owner VARCHAR(64),
    create_time DATETIME
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE IF NOT EXISTS std_standard (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT DEFAULT 1,
    code VARCHAR(64) NOT NULL UNIQUE,
    name VARCHAR(128),
    cn_name VARCHAR(128),
    category VARCHAR(64),
    industry VARCHAR(32),
    source VARCHAR(64),
    status VARCHAR(16) DEFAULT 'PUBLISHED',
    version VARCHAR(32),
    element_codes TEXT,
    description VARCHAR(500),
    create_time DATETIME
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE IF NOT EXISTS std_field_mapping (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT DEFAULT 1,
    table_id BIGINT,
    table_name VARCHAR(128),
    column_id BIGINT,
    column_name VARCHAR(128),
    std_element_code VARCHAR(64),
    match_type VARCHAR(16) DEFAULT 'AUTO',
    score DECIMAL(6,2),
    status VARCHAR(16) DEFAULT 'CONFIRMED',
    create_time DATETIME,
    KEY idx_std_map_col (column_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE IF NOT EXISTS std_compliance_check (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    check_time DATETIME,
    scope VARCHAR(128),
    total_cols INT,
    compliant_cols INT,
    compliant_rate DECIMAL(6,2),
    detail TEXT,
    operator VARCHAR(64)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- ---------------------------- 集成与调度 dgp-integration / dgp-workflow ----------------------------
CREATE TABLE IF NOT EXISTS int_job (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT DEFAULT 1,
    code VARCHAR(64) NOT NULL UNIQUE,
    name VARCHAR(128),
    cn_name VARCHAR(128),
    job_type VARCHAR(16) DEFAULT 'ETL',
    engine VARCHAR(16) DEFAULT 'BATCH',
    status VARCHAR(16) DEFAULT 'DRAFT',
    schedule_type VARCHAR(16) DEFAULT 'MANUAL',
    cron VARCHAR(64),
    interval_min INT,
    priority INT DEFAULT 5,
    timeout_min INT DEFAULT 60,
    retry INT DEFAULT 0,
    industry VARCHAR(32),
    dsl TEXT,
    script_lang VARCHAR(16),
    script TEXT,
    owner VARCHAR(64),
    description VARCHAR(500),
    version VARCHAR(32) DEFAULT '1.0.0',
    last_run_time DATETIME,
    next_run_time DATETIME,
    success_count INT DEFAULT 0,
    fail_count INT DEFAULT 0,
    create_time DATETIME
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE IF NOT EXISTS int_job_instance (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    job_id BIGINT,
    job_name VARCHAR(128),
    status VARCHAR(16),
    trigger_type VARCHAR(16),
    start_time DATETIME,
    end_time DATETIME,
    duration_ms BIGINT,
    rows_written BIGINT DEFAULT 0,
    message VARCHAR(500),
    operator VARCHAR(64),
    trace_id VARCHAR(64),
    KEY idx_int_inst_job (job_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE IF NOT EXISTS int_job_log (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    instance_id BIGINT,
    log_time DATETIME,
    log_level VARCHAR(8),
    message VARCHAR(1000)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE IF NOT EXISTS int_sandbox_policy (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(128),
    allow_sql INT DEFAULT 1,
    allow_python INT DEFAULT 0,
    allow_shell INT DEFAULT 0,
    max_runtime_min INT DEFAULT 30,
    max_memory_mb INT DEFAULT 1024,
    network_whitelist VARCHAR(500),
    status VARCHAR(16) DEFAULT 'ENABLED'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- ---------------------------- 数据质量 dgp-quality ----------------------------
CREATE TABLE IF NOT EXISTS qa_rule (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT DEFAULT 1,
    code VARCHAR(64) NOT NULL UNIQUE,
    name VARCHAR(128),
    table_id BIGINT,
    table_name VARCHAR(128),
    column_name VARCHAR(128),
    dimension VARCHAR(16),
    rule_type VARCHAR(32),
    expression VARCHAR(1000),
    tolerance DECIMAL(6,4) DEFAULT 0.01,
    weight DECIMAL(6,2) DEFAULT 1,
    severity VARCHAR(8) DEFAULT 'MAJOR',
    status VARCHAR(16) DEFAULT 'ENABLED',
    industry VARCHAR(32),
    owner VARCHAR(64),
    cycle VARCHAR(16) DEFAULT 'DAILY',
    check_mode VARCHAR(16) DEFAULT 'FULL',
    auto_issue INT DEFAULT 1,
    last_total BIGINT,
    last_failed BIGINT,
    last_pass_rate DECIMAL(6,2),
    last_run_time DATETIME,
    create_time DATETIME,
    KEY idx_qa_rule_obj (table_name, column_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE IF NOT EXISTS qa_rule_template (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    code VARCHAR(64) NOT NULL UNIQUE,
    name VARCHAR(128),
    industry VARCHAR(32),
    dimension VARCHAR(16),
    rule_type VARCHAR(32),
    expression VARCHAR(1000),
    applies_to VARCHAR(255),
    description VARCHAR(500),
    status VARCHAR(16) DEFAULT 'ENABLED'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE IF NOT EXISTS qa_check (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT DEFAULT 1,
    code VARCHAR(64) NOT NULL UNIQUE,
    name VARCHAR(128),
    description VARCHAR(500),
    check_mode VARCHAR(16) DEFAULT 'FULL',
    schedule_type VARCHAR(16) DEFAULT 'MANUAL',
    cron VARCHAR(64),
    status VARCHAR(16) DEFAULT 'ENABLED',
    owner VARCHAR(64),
    last_run_time DATETIME,
    next_run_time DATETIME,
    create_time DATETIME
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE IF NOT EXISTS qa_check_rule (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    check_id BIGINT,
    rule_id BIGINT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE IF NOT EXISTS qa_check_instance (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    check_id BIGINT,
    check_name VARCHAR(128),
    status VARCHAR(32),
    start_time DATETIME,
    end_time DATETIME,
    duration_ms BIGINT,
    rule_total INT,
    rule_passed INT,
    score DECIMAL(6,2),
    trigger_type VARCHAR(16),
    operator VARCHAR(64),
    KEY idx_qa_inst_check (check_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE IF NOT EXISTS qa_check_result (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    instance_id BIGINT,
    rule_id BIGINT,
    rule_name VARCHAR(128),
    table_name VARCHAR(128),
    column_name VARCHAR(128),
    dimension VARCHAR(16),
    total_cnt BIGINT,
    fail_cnt BIGINT,
    pass_rate DECIMAL(6,2),
    score DECIMAL(6,2),
    result_status VARCHAR(16),
    run_time DATETIME,
    duration_ms BIGINT,
    KEY idx_qa_res_inst (instance_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE IF NOT EXISTS qa_score (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT DEFAULT 1,
    object_type VARCHAR(16),
    object_id BIGINT,
    object_name VARCHAR(128),
    stat_date DATE,
    total_score DECIMAL(6,2),
    score_level VARCHAR(16),
    dimension_scores TEXT,
    passed_rules INT,
    total_rules INT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE IF NOT EXISTS qa_report (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT DEFAULT 1,
    code VARCHAR(64),
    title VARCHAR(255),
    report_type VARCHAR(32),
    scope VARCHAR(255),
    period VARCHAR(64),
    total_score DECIMAL(6,2),
    score_level VARCHAR(16),
    context TEXT,
    file_key VARCHAR(255),
    status VARCHAR(16) DEFAULT 'GENERATED',
    creator VARCHAR(64),
    create_time DATETIME
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE IF NOT EXISTS qa_issue (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT DEFAULT 1,
    code VARCHAR(64),
    rule_id BIGINT,
    rule_name VARCHAR(128),
    table_id BIGINT,
    table_name VARCHAR(128),
    column_name VARCHAR(128),
    dimension VARCHAR(16),
    severity VARCHAR(8),
    assignee VARCHAR(64),
    status VARCHAR(16) DEFAULT 'DISCOVERED',
    discover_time DATETIME,
    due_time DATETIME,
    close_time DATETIME,
    description VARCHAR(1000),
    sample_data VARCHAR(1000),
    handle_result VARCHAR(1000),
    review_result VARCHAR(500),
    recur_cnt INT DEFAULT 0,
    biz_date DATE,
    KEY idx_qa_issue_status (status),
    KEY idx_qa_issue_assignee (assignee),
    KEY idx_qa_issue_rule (rule_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE IF NOT EXISTS qa_issue_flow (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    issue_id BIGINT,
    from_status VARCHAR(16),
    to_status VARCHAR(16),
    operator VARCHAR(64),
    comment VARCHAR(500),
    create_time DATETIME
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE IF NOT EXISTS qa_issue_metric_daily (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    stat_date DATE,
    new_cnt INT DEFAULT 0,
    closed_cnt INT DEFAULT 0,
    open_cnt INT DEFAULT 0,
    overdue_cnt INT DEFAULT 0,
    closed_rate DECIMAL(6,2)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- ---------------------------- 数据安全 dgp-security ----------------------------
CREATE TABLE IF NOT EXISTS sec_sensitive_rule (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT DEFAULT 1,
    code VARCHAR(64) NOT NULL UNIQUE,
    name VARCHAR(128),
    match_type VARCHAR(16),
    pattern VARCHAR(255),
    category VARCHAR(64),
    security_level VARCHAR(8),
    mask_algo VARCHAR(32),
    status VARCHAR(16) DEFAULT 'ENABLED',
    hit_columns INT DEFAULT 0,
    create_time DATETIME
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE IF NOT EXISTS sec_sensitive_result (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT DEFAULT 1,
    rule_id BIGINT,
    table_id BIGINT,
    table_name VARCHAR(128),
    column_id BIGINT,
    column_name VARCHAR(128),
    match_type VARCHAR(16),
    security_level VARCHAR(8),
    sample_value VARCHAR(255),
    hit_rate DECIMAL(6,2),
    status VARCHAR(16) DEFAULT 'CONFIRMED',
    scan_time DATETIME
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE IF NOT EXISTS sec_mask_policy (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT DEFAULT 1,
    table_id BIGINT,
    table_name VARCHAR(128),
    column_id BIGINT,
    column_name VARCHAR(128),
    algorithm VARCHAR(32),
    security_level VARCHAR(8),
    role_codes VARCHAR(255),
    status VARCHAR(16) DEFAULT 'PENDING',
    approver VARCHAR(64),
    approve_time DATETIME,
    create_by VARCHAR(64),
    create_time DATETIME,
    KEY idx_sec_mask_table (table_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE IF NOT EXISTS sec_access_apply (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT DEFAULT 1,
    applicant VARCHAR(64),
    asset_type VARCHAR(16),
    asset_id BIGINT,
    asset_name VARCHAR(128),
    reason VARCHAR(500),
    duration_days INT,
    status VARCHAR(16) DEFAULT 'PENDING',
    approver VARCHAR(64),
    approve_time DATETIME,
    create_time DATETIME
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE IF NOT EXISTS sec_access_grant (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT DEFAULT 1,
    apply_id BIGINT,
    subject VARCHAR(64),
    asset_name VARCHAR(128),
    scope VARCHAR(64),
    expire_time DATETIME,
    status VARCHAR(16) DEFAULT 'ACTIVE'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE IF NOT EXISTS sec_audit_log (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT DEFAULT 1,
    log_time DATETIME,
    username VARCHAR(64),
    action VARCHAR(64),
    module VARCHAR(32),
    resource VARCHAR(255),
    detail VARCHAR(1000),
    ip VARCHAR(64),
    result VARCHAR(16),
    trace_id VARCHAR(64),
    hash VARCHAR(64),
    prev_hash VARCHAR(64),
    KEY idx_sec_audit_time (log_time),
    KEY idx_audit_user_time (username, log_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE IF NOT EXISTS sec_audit_chain (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    batch_no VARCHAR(64),
    start_id BIGINT,
    end_id BIGINT,
    chain_hash VARCHAR(64),
    verify_status VARCHAR(16),
    create_time DATETIME
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE IF NOT EXISTS sec_alert (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT DEFAULT 1,
    alert_time DATETIME,
    alert_type VARCHAR(32),
    title VARCHAR(255),
    detail VARCHAR(1000),
    severity VARCHAR(8),
    status VARCHAR(16) DEFAULT 'OPEN',
    handler VARCHAR(64),
    handle_time DATETIME
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE IF NOT EXISTS sec_alert_rule (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(128),
    metric VARCHAR(64),
    threshold VARCHAR(64),
    window_min INT,
    severity VARCHAR(8),
    notify_channel VARCHAR(64),
    status VARCHAR(16) DEFAULT 'ENABLED'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- ---------------------------- 数据资产 dgp-asset ----------------------------
CREATE TABLE IF NOT EXISTS ast_category (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    parent_id BIGINT DEFAULT 0,
    code VARCHAR(64),
    name VARCHAR(64),
    sort_no INT DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE IF NOT EXISTS ast_catalog (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT DEFAULT 1,
    table_id BIGINT,
    table_name VARCHAR(128),
    asset_name VARCHAR(128),
    asset_type VARCHAR(16) DEFAULT 'TABLE',
    category VARCHAR(64),
    domain VARCHAR(64),
    business_desc VARCHAR(500),
    cover_scope VARCHAR(255),
    update_cycle VARCHAR(32),
    owner VARCHAR(64),
    publish_status VARCHAR(16) DEFAULT 'DRAFT',
    favorite_cnt INT DEFAULT 0,
    view_cnt INT DEFAULT 0,
    value_score DECIMAL(6,2) DEFAULT 0,
    sensitivity_level VARCHAR(8) DEFAULT 'L1',
    create_time DATETIME
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE IF NOT EXISTS ast_map_snapshot (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    stat_date DATE,
    total_tables INT,
    total_rows BIGINT,
    total_size_mb DECIMAL(14,2),
    ds_cnt INT,
    domain_json TEXT,
    layer_json TEXT,
    freq_json TEXT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE IF NOT EXISTS ast_metric_daily (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    stat_date DATE,
    table_cnt INT,
    storage_mb DECIMAL(14,2),
    access_cnt BIGINT,
    svc_call_cnt BIGINT,
    query_cnt BIGINT,
    active_users INT,
    asset_value DECIMAL(14,2)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE IF NOT EXISTS ast_visit_log (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    visit_time DATETIME,
    username VARCHAR(64),
    asset_id BIGINT,
    asset_name VARCHAR(128),
    action VARCHAR(32),
    ip VARCHAR(64)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE IF NOT EXISTS ast_value_score (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    table_name VARCHAR(128),
    score DECIMAL(6,2),
    dim_json TEXT,
    calc_time DATETIME
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- ---------------------------- 数据服务 dgp-service ----------------------------
CREATE TABLE IF NOT EXISTS svc_api (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT DEFAULT 1,
    code VARCHAR(64) NOT NULL UNIQUE,
    name VARCHAR(128),
    api_path VARCHAR(128),
    method VARCHAR(8) DEFAULT 'GET',
    version VARCHAR(32) DEFAULT 'v1',
    status VARCHAR(16) DEFAULT 'DRAFT',
    category VARCHAR(64),
    sql_text TEXT,
    params TEXT,
    owner VARCHAR(64),
    app_code VARCHAR(64),
    auth_type VARCHAR(16) DEFAULT 'AKSK',
    rate_limit INT DEFAULT 100,
    timeout_ms INT DEFAULT 3000,
    mask_enabled INT DEFAULT 1,
    call_count BIGINT DEFAULT 0,
    avg_ms DECIMAL(10,2) DEFAULT 0,
    description VARCHAR(500),
    create_time DATETIME,
    publish_time DATETIME,
    industry VARCHAR(32)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE IF NOT EXISTS svc_api_version (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    api_id BIGINT,
    version VARCHAR(32),
    sql_text TEXT,
    params TEXT,
    change_note VARCHAR(255),
    operator VARCHAR(64),
    create_time DATETIME
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE IF NOT EXISTS svc_app (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT DEFAULT 1,
    app_code VARCHAR(64) NOT NULL UNIQUE,
    app_name VARCHAR(128),
    app_secret VARCHAR(128),
    owner VARCHAR(64),
    status VARCHAR(16) DEFAULT 'ENABLED',
    quota_day INT DEFAULT 10000,
    create_time DATETIME
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE IF NOT EXISTS svc_call_log (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    api_id BIGINT,
    api_code VARCHAR(64),
    api_name VARCHAR(128),
    call_time DATETIME,
    status_code INT,
    duration_ms BIGINT,
    app_code VARCHAR(64),
    ip VARCHAR(64),
    row_cnt INT,
    error_msg VARCHAR(500),
    trace_id VARCHAR(64),
    KEY idx_svc_call_api_time (api_id, call_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE IF NOT EXISTS svc_call_stat (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    stat_date DATE,
    api_id BIGINT,
    call_cnt BIGINT,
    success_cnt BIGINT,
    fail_cnt BIGINT,
    avg_ms DECIMAL(10,2),
    p99_ms DECIMAL(10,2)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE IF NOT EXISTS svc_quota (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT DEFAULT 1,
    app_code VARCHAR(64),
    quota_day INT,
    used_today INT DEFAULT 0,
    qps_limit INT,
    UNIQUE KEY ux_svc_quota_app (app_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE IF NOT EXISTS svc_blacklist (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    ip VARCHAR(64),
    reason VARCHAR(255),
    status VARCHAR(16) DEFAULT 'ENABLED',
    create_time DATETIME
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE IF NOT EXISTS svc_orchestration (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT DEFAULT 1,
    code VARCHAR(64) NOT NULL UNIQUE,
    name VARCHAR(128),
    status VARCHAR(16) DEFAULT 'DRAFT',
    description VARCHAR(500),
    steps TEXT,
    owner VARCHAR(64),
    call_cnt INT DEFAULT 0,
    create_time DATETIME
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE IF NOT EXISTS svc_orchestration_run (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    orch_id BIGINT,
    status VARCHAR(16),
    start_time DATETIME,
    end_time DATETIME,
    result TEXT,
    operator VARCHAR(64)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- ---------------------------- 行业包 dgp-industry ----------------------------
CREATE TABLE IF NOT EXISTS ind_industry (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    code VARCHAR(32) NOT NULL UNIQUE,
    name VARCHAR(64),
    description VARCHAR(500)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE IF NOT EXISTS ind_package (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT DEFAULT 1,
    package_code VARCHAR(64) NOT NULL UNIQUE,
    package_name VARCHAR(128),
    industry_code VARCHAR(32),
    industry_name VARCHAR(64),
    version VARCHAR(32),
    status VARCHAR(16) DEFAULT 'INSTALLED',
    min_platform_version VARCHAR(32),
    max_platform_version VARCHAR(32),
    description VARCHAR(1000),
    vendor VARCHAR(128),
    manifest TEXT,
    item_cnt INT DEFAULT 0,
    applied INT DEFAULT 0,
    create_time DATETIME
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE IF NOT EXISTS ind_package_item (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    package_id BIGINT,
    item_type VARCHAR(32),
    code VARCHAR(64),
    name VARCHAR(128),
    version VARCHAR(32),
    content TEXT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE IF NOT EXISTS ind_package_version (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    package_code VARCHAR(64),
    version VARCHAR(32),
    manifest TEXT,
    operator VARCHAR(64),
    create_time DATETIME
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE IF NOT EXISTS ind_enable_scope (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    package_code VARCHAR(64),
    tenant_id BIGINT,
    status VARCHAR(16) DEFAULT 'ENABLED',
    enable_time DATETIME
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- ---------------------------- 报告 dgp-report ----------------------------
CREATE TABLE IF NOT EXISTS rpt_template (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    code VARCHAR(64) NOT NULL UNIQUE,
    name VARCHAR(128),
    report_type VARCHAR(32),
    industry VARCHAR(32),
    engine VARCHAR(32),
    content TEXT,
    status VARCHAR(16) DEFAULT 'ENABLED',
    create_time DATETIME
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE IF NOT EXISTS rpt_task (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    task_id VARCHAR(64),
    template_code VARCHAR(64),
    title VARCHAR(255),
    formats VARCHAR(64),
    status VARCHAR(16),
    start_time DATETIME,
    end_time DATETIME,
    result VARCHAR(500),
    operator VARCHAR(64)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE IF NOT EXISTS rpt_file (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    task_id VARCHAR(64),
    report_id BIGINT,
    file_name VARCHAR(255),
    file_path VARCHAR(500),
    fmt VARCHAR(16),
    size_kb INT,
    create_time DATETIME
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- ---------------------------- 数据权限 dgp-auth ----------------------------
CREATE TABLE IF NOT EXISTS sys_data_scope (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT DEFAULT 1,
    role_code VARCHAR(64),
    scope_type VARCHAR(16),
    scope_value VARCHAR(255),
    create_time DATETIME
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE IF NOT EXISTS rpt_export_log (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    report_id BIGINT,
    fmt VARCHAR(16),
    operator VARCHAR(64),
    ts DATETIME,
    file_path VARCHAR(500)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- ============================================================
-- v1.1 修复补充：令牌吊销、组织、字典、唯一约束与缺失索引
-- （H2 / PostgreSQL 双向兼容）
-- ============================================================
CREATE TABLE IF NOT EXISTS sys_token_blacklist (
    jti VARCHAR(64) PRIMARY KEY,
    username VARCHAR(64),
    expire_at BIGINT,
    create_time DATETIME
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE IF NOT EXISTS sys_org (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT DEFAULT 1,
    parent_id BIGINT DEFAULT 0,
    code VARCHAR(64) NOT NULL UNIQUE,
    name VARCHAR(128) NOT NULL,
    sort_no INT DEFAULT 0,
    create_time DATETIME
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE IF NOT EXISTS sys_dict (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    dict_code VARCHAR(64) NOT NULL UNIQUE,
    dict_name VARCHAR(128),
    items TEXT,
    update_time DATETIME,
    create_time DATETIME
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- 业务唯一性：元数据表名/列名为血缘、规则、脱敏策略的 join key，必须唯一
-- 热查询索引

-- ============================================================
-- v1.2 / v1.3 新增：流式（微批+推送）数据质检 + 数据迁移映射
-- 语法保持 H2(PostgreSQL 模式) / PostgreSQL / MySQL 三向兼容
-- ============================================================

-- ---------------------------- 流式质检 dgp-quality ----------------------------
CREATE TABLE IF NOT EXISTS qa_stream_source (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT DEFAULT 1,
    code VARCHAR(64) NOT NULL UNIQUE,
    name VARCHAR(128) NOT NULL,
    table_name VARCHAR(128) NOT NULL,
    watermark_col VARCHAR(64) NOT NULL,
    watermark_type VARCHAR(16) DEFAULT 'DATETIME',
    source_type VARCHAR(16) DEFAULT 'TABLE',
    pk_col VARCHAR(64),
    window_seconds INT DEFAULT 60,
    batch_limit INT DEFAULT 5000,
    status VARCHAR(16) DEFAULT 'ENABLED',
    last_watermark VARCHAR(64),
    last_run_time DATETIME,
    owner VARCHAR(64),
    create_time DATETIME
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE IF NOT EXISTS qa_stream_rule (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    source_id BIGINT NOT NULL,
    code VARCHAR(64) NOT NULL UNIQUE,
    name VARCHAR(128) NOT NULL,
    dimension VARCHAR(32) DEFAULT 'VALIDITY',
    expression VARCHAR(1000) NOT NULL,
    tolerance DECIMAL(6,4) DEFAULT 0.01,
    severity VARCHAR(16) DEFAULT 'MAJOR',
    status VARCHAR(16) DEFAULT 'ENABLED',
    last_pass_rate DECIMAL(6,2),
    last_fail_count BIGINT,
    last_row_count BIGINT,
    last_run_time DATETIME,
    create_time DATETIME
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE IF NOT EXISTS qa_stream_result (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    source_id BIGINT NOT NULL,
    rule_id BIGINT NOT NULL,
    window_from VARCHAR(64),
    window_to VARCHAR(64),
    row_count BIGINT DEFAULT 0,
    fail_count BIGINT DEFAULT 0,
    pass_rate DECIMAL(6,2),
    status VARCHAR(16) DEFAULT 'PASS',
    duration_ms BIGINT DEFAULT 0,
    run_time DATETIME,
    KEY idx_qa_stream_result_rule (rule_id, run_time),
    KEY idx_qa_stream_result_src (source_id, run_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE IF NOT EXISTS qa_stream_alert (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    source_id BIGINT NOT NULL,
    rule_id BIGINT NOT NULL,
    title VARCHAR(255),
    severity VARCHAR(16) DEFAULT 'MAJOR',
    pass_rate DECIMAL(6,2),
    window_from VARCHAR(64),
    window_to VARCHAR(64),
    status VARCHAR(16) DEFAULT 'OPEN',
    create_time DATETIME,
    handle_time DATETIME,
    handler VARCHAR(64),
    KEY idx_qa_stream_alert_status (status, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE IF NOT EXISTS qa_stream_push (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    source_id BIGINT NOT NULL UNIQUE,
    field_mapping TEXT,
    status VARCHAR(16) DEFAULT 'ENABLED',
    push_count BIGINT DEFAULT 0,
    last_push_time DATETIME,
    create_time DATETIME,
    KEY idx_qa_stream_push_src (source_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- ---------------------------- 数据迁移映射 dgp-migration ----------------------------
CREATE TABLE IF NOT EXISTS mig_project (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT DEFAULT 1,
    code VARCHAR(64) NOT NULL UNIQUE,
    name VARCHAR(128) NOT NULL,
    source_table VARCHAR(128) NOT NULL,
    target_table VARCHAR(128) NOT NULL,
    mode VARCHAR(16) DEFAULT 'FULL',
    watermark_col VARCHAR(64),
    last_watermark VARCHAR(64),
    replace_target INT DEFAULT 1,
    batch_size INT DEFAULT 5000,
    rollback_on_fail INT DEFAULT 0,
    status VARCHAR(16) DEFAULT 'DRAFT',
    description VARCHAR(500),
    last_run_time DATETIME,
    owner VARCHAR(64),
    create_time DATETIME,
    update_time DATETIME
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE IF NOT EXISTS mig_mapping (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    project_id BIGINT NOT NULL,
    source_column VARCHAR(64),
    source_type VARCHAR(32),
    target_column VARCHAR(64) NOT NULL,
    target_type VARCHAR(32),
    transform_type VARCHAR(16) DEFAULT 'DIRECT',
    transform_expr VARCHAR(500),
    default_value VARCHAR(255),
    sort_no INT DEFAULT 0,
    KEY idx_mig_mapping_project (project_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE IF NOT EXISTS mig_check (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    project_id BIGINT NOT NULL,
    check_type VARCHAR(16) NOT NULL,
    column_name VARCHAR(64),
    tolerance DECIMAL(8,4) DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE IF NOT EXISTS mig_run (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    project_id BIGINT NOT NULL,
    run_mode VARCHAR(16) DEFAULT 'MANUAL',
    status VARCHAR(16) DEFAULT 'RUNNING',
    start_time DATETIME,
    end_time DATETIME,
    start_watermark VARCHAR(64),
    end_watermark VARCHAR(64),
    chunk_count INT DEFAULT 0,
    rollback_status VARCHAR(16),
    source_rows BIGINT DEFAULT 0,
    target_rows BIGINT DEFAULT 0,
    inserted_rows BIGINT DEFAULT 0,
    failed_rows BIGINT DEFAULT 0,
    message VARCHAR(500),
    operator VARCHAR(64),
    KEY idx_mig_run_project (project_id, start_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE IF NOT EXISTS mig_diff (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    run_id BIGINT NOT NULL,
    check_type VARCHAR(16),
    column_name VARCHAR(64),
    expected_val VARCHAR(255),
    actual_val VARCHAR(255),
    diff_val VARCHAR(255),
    status VARCHAR(16) DEFAULT 'PASS',
    detail VARCHAR(500),
    KEY idx_mig_diff_run (run_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- 热查询索引
