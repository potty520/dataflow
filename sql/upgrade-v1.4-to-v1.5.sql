-- =============================================================
-- DGP v1.4 → v1.5 升级脚本（MySQL 8，执行一次）
--   mysql -u root -p dgp < sql/upgrade-v1.4-to-v1.5.sql
-- 说明：v1.5 新增「聚合汇总」两张表，其余为行为增强，无字段变更
-- =============================================================

CREATE TABLE IF NOT EXISTS mig_agg_def (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT DEFAULT 1,
    code VARCHAR(64) NOT NULL UNIQUE,
    name VARCHAR(128) NOT NULL,
    source_ds_id BIGINT DEFAULT 0,
    target_ds_id BIGINT DEFAULT 0,
    source_table VARCHAR(128) NOT NULL,
    target_table VARCHAR(128) NOT NULL,
    dims TEXT NOT NULL,
    metrics TEXT NOT NULL,
    where_clause VARCHAR(500),
    view_name VARCHAR(128),
    refresh_mode VARCHAR(16) DEFAULT 'FULL',
    refresh_minutes INT DEFAULT 0,
    status VARCHAR(16) DEFAULT 'ENABLED',
    project_id BIGINT,
    last_run_time DATETIME,
    next_run_time DATETIME,
    last_rows BIGINT,
    last_status VARCHAR(16),
    last_message VARCHAR(500),
    create_time DATETIME,
    update_time DATETIME,
    KEY idx_mig_agg_def_next (status, next_run_time)
);

CREATE TABLE IF NOT EXISTS mig_agg_run (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    agg_id BIGINT NOT NULL,
    trigger_type VARCHAR(16) DEFAULT 'MANUAL',
    status VARCHAR(16) DEFAULT 'RUNNING',
    start_time DATETIME,
    end_time DATETIME,
    rows_count BIGINT DEFAULT 0,
    message VARCHAR(500),
    operator VARCHAR(64),
    KEY idx_mig_agg_run_agg (agg_id, start_time)
);
