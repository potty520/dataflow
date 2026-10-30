-- =============================================================
-- DGP v1.6 → v1.7 升级脚本（MySQL 8）
-- 新增：数据标准基础库（数据字典/代码集/常用词汇/模型标准）
-- 执行：mysql -u root -p dgp < sql/upgrade-v1.6-to-v1.7.sql
-- =============================================================

CREATE TABLE IF NOT EXISTS std_base_group (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT DEFAULT 1,
    type VARCHAR(16) NOT NULL,
    code VARCHAR(64) NOT NULL,
    name VARCHAR(128) NOT NULL,
    description VARCHAR(500),
    status VARCHAR(16) DEFAULT 'ENABLED',
    create_time DATETIME,
    update_time DATETIME,
    UNIQUE KEY uk_std_base_group (type, code)
);

CREATE TABLE IF NOT EXISTS std_base_item (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    group_id BIGINT NOT NULL,
    code VARCHAR(64) NOT NULL,
    name VARCHAR(128) NOT NULL,
    description VARCHAR(500),
    sort_no INT DEFAULT 0,
    status VARCHAR(16) DEFAULT 'ENABLED',
    create_time DATETIME,
    KEY idx_std_base_item_group (group_id)
);
