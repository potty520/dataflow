-- =============================================================
-- DGP v1.15 → v1.16 升级脚本（MySQL 8）
-- 新增：CDC / 整库同步（cdc_task / cdc_run）
-- =============================================================

CREATE TABLE IF NOT EXISTS cdc_task (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT DEFAULT 1,
    code VARCHAR(64) NOT NULL,
    name VARCHAR(128) NOT NULL,
    source_ds_id BIGINT DEFAULT 0,
    source_table VARCHAR(128) NOT NULL,
    target_ds_id BIGINT DEFAULT 0,
    target_table VARCHAR(128) NOT NULL,
    sync_mode VARCHAR(16) DEFAULT 'FULL',
    incr_column VARCHAR(128),
    batch_size INT DEFAULT 1000,
    auto_create INT DEFAULT 1,
    conflict_strategy VARCHAR(16) DEFAULT 'OVERWRITE',
    status VARCHAR(16) DEFAULT 'ENABLED',
    watermark VARCHAR(255),
    last_run_time DATETIME,
    create_time DATETIME,
    update_time DATETIME,
    UNIQUE KEY uk_cdc_task_code (code)
);

CREATE TABLE IF NOT EXISTS cdc_run (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    task_id BIGINT,
    status VARCHAR(16),
    rows_read BIGINT DEFAULT 0,
    rows_written BIGINT DEFAULT 0,
    watermark_before VARCHAR(255),
    watermark_after VARCHAR(255),
    start_time DATETIME,
    end_time DATETIME,
    message VARCHAR(1000),
    KEY idx_cdc_run_task (task_id)
);
