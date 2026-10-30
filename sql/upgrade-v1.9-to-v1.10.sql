-- =============================================================
-- DGP v1.9 → v1.10 升级脚本（MySQL 8）
-- 新增：集成配置（函数库 / 增量字段 / 批次）
-- =============================================================

CREATE TABLE IF NOT EXISTS int_function (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT DEFAULT 1,
    code VARCHAR(64) NOT NULL,
    name VARCHAR(128) NOT NULL,
    expression VARCHAR(1000),
    category VARCHAR(64),
    status VARCHAR(16) DEFAULT 'ENABLED',
    remark VARCHAR(255),
    create_time DATETIME,
    update_time DATETIME,
    UNIQUE KEY uk_int_function_code (code)
);

CREATE TABLE IF NOT EXISTS int_incr_config (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT DEFAULT 1,
    job_id BIGINT NOT NULL,
    field_name VARCHAR(128) NOT NULL,
    field_type VARCHAR(16) DEFAULT 'TIMESTAMP',
    start_value VARCHAR(255),
    last_value VARCHAR(255),
    status VARCHAR(16) DEFAULT 'ENABLED',
    remark VARCHAR(255),
    create_time DATETIME,
    update_time DATETIME,
    UNIQUE KEY uk_int_incr_job (job_id)
);

CREATE TABLE IF NOT EXISTS int_batch_config (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT DEFAULT 1,
    job_id BIGINT NOT NULL,
    batch_size INT DEFAULT 10000,
    concurrency INT DEFAULT 2,
    retry_count INT DEFAULT 3,
    status VARCHAR(16) DEFAULT 'ENABLED',
    remark VARCHAR(255),
    create_time DATETIME,
    update_time DATETIME,
    UNIQUE KEY uk_int_batch_job (job_id)
);
