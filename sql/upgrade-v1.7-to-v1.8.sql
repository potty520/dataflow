-- =============================================================
-- DGP v1.7 → v1.8 升级脚本（MySQL 8）
-- 新增：建模类型映射、库间字段映射配置（BF-11）
-- =============================================================

CREATE TABLE IF NOT EXISTS mdl_type_mapping (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    source_type VARCHAR(64) NOT NULL,
    target_dialect VARCHAR(16) NOT NULL,
    target_type VARCHAR(64) NOT NULL,
    status VARCHAR(16) DEFAULT 'ENABLED',
    remark VARCHAR(255),
    create_time DATETIME,
    update_time DATETIME,
    UNIQUE KEY uk_mdl_type_mapping (source_type, target_dialect)
);

CREATE TABLE IF NOT EXISTS mdl_column_mapping (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    model_id BIGINT,
    source_table VARCHAR(128),
    source_column VARCHAR(128),
    target_table VARCHAR(128),
    target_column VARCHAR(128),
    source_type VARCHAR(64),
    target_type VARCHAR(64),
    expression VARCHAR(500),
    status VARCHAR(16) DEFAULT 'ENABLED',
    remark VARCHAR(255),
    create_time DATETIME,
    KEY idx_mdl_col_map_model (model_id)
);
