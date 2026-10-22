-- =============================================================
-- DGP v1.3 → v1.4 升级脚本（MySQL 8，执行一次）
--   mysql -u root -p dgp < sql/upgrade-v1.3-to-v1.4.sql
-- =============================================================
ALTER TABLE mig_project ADD COLUMN source_ds_id BIGINT DEFAULT 0;
ALTER TABLE mig_project ADD COLUMN target_ds_id BIGINT DEFAULT 0;

CREATE TABLE IF NOT EXISTS mig_datasource (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT DEFAULT 1,
    code VARCHAR(64) NOT NULL UNIQUE,
    name VARCHAR(128) NOT NULL,
    db_type VARCHAR(16) DEFAULT 'MYSQL',
    host VARCHAR(128) NOT NULL,
    port INT DEFAULT 3306,
    db_name VARCHAR(128) NOT NULL,
    username VARCHAR(64),
    password_enc VARCHAR(512),
    proxy_type VARCHAR(16) DEFAULT 'DIRECT',
    proxy_host VARCHAR(128),
    proxy_port INT,
    proxy_user VARCHAR(64),
    proxy_password_enc VARCHAR(512),
    status VARCHAR(16) DEFAULT 'ENABLED',
    last_test_ok INT,
    last_test_time DATETIME,
    last_test_msg VARCHAR(500),
    create_time DATETIME,
    update_time DATETIME
);

CREATE TABLE IF NOT EXISTS mig_file_task (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT DEFAULT 1,
    code VARCHAR(64) NOT NULL UNIQUE,
    name VARCHAR(128) NOT NULL,
    direction VARCHAR(16) NOT NULL,
    project_id BIGINT,
    source_table VARCHAR(128),
    target_table VARCHAR(128),
    file_format VARCHAR(16) DEFAULT 'CSV',
    remote_type VARCHAR(16) DEFAULT 'FTP',
    remote_host VARCHAR(128),
    remote_port INT DEFAULT 21,
    remote_user VARCHAR(64),
    remote_password_enc VARCHAR(512),
    remote_dir VARCHAR(255) DEFAULT '/',
    remote_file VARCHAR(255),
    proxy_type VARCHAR(16) DEFAULT 'DIRECT',
    proxy_host VARCHAR(128),
    proxy_port INT,
    proxy_user VARCHAR(64),
    proxy_password_enc VARCHAR(512),
    compress INT DEFAULT 0,
    status VARCHAR(16) DEFAULT 'ENABLED',
    create_time DATETIME,
    update_time DATETIME
);

CREATE TABLE IF NOT EXISTS mig_file_run (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    task_id BIGINT NOT NULL,
    direction VARCHAR(16),
    status VARCHAR(16) DEFAULT 'RUNNING',
    start_time DATETIME,
    end_time DATETIME,
    rows_count BIGINT DEFAULT 0,
    bytes_count BIGINT DEFAULT 0,
    checksum VARCHAR(128),
    message VARCHAR(500),
    operator VARCHAR(64)
);
CREATE INDEX idx_mig_file_run_task ON mig_file_run(task_id, start_time);
