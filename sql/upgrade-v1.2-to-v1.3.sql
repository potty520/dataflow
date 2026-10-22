-- =============================================================
-- DGP v1.2 → v1.3 升级脚本（已有数据库使用；新建库直接用 schema.sql）
-- 执行一次即可（MySQL 8）：
--   mysql -u root -p dgp < sql/upgrade-v1.2-to-v1.3.sql
-- =============================================================
ALTER TABLE qa_stream_source ADD COLUMN source_type VARCHAR(16) DEFAULT 'TABLE';
ALTER TABLE qa_stream_source ADD COLUMN pk_col VARCHAR(64);
ALTER TABLE mig_project ADD COLUMN batch_size INT DEFAULT 5000;
ALTER TABLE mig_project ADD COLUMN rollback_on_fail INT DEFAULT 0;
ALTER TABLE mig_run ADD COLUMN start_watermark VARCHAR(64);
ALTER TABLE mig_run ADD COLUMN end_watermark VARCHAR(64);
ALTER TABLE mig_run ADD COLUMN chunk_count INT DEFAULT 0;
ALTER TABLE mig_run ADD COLUMN rollback_status VARCHAR(16);

CREATE TABLE IF NOT EXISTS qa_stream_push (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    source_id BIGINT NOT NULL UNIQUE,
    field_mapping TEXT,
    status VARCHAR(16) DEFAULT 'ENABLED',
    push_count BIGINT DEFAULT 0,
    last_push_time DATETIME,
    create_time DATETIME
);
CREATE INDEX idx_qa_stream_push_src ON qa_stream_push(source_id);