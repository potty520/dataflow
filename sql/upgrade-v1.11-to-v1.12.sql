-- =============================================================
-- DGP v1.11 → v1.12 升级脚本（MySQL 8）
-- 新增：CAS / 统一门户（sys_cas_config / sys_portal）
-- =============================================================

CREATE TABLE IF NOT EXISTS sys_cas_config (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    enabled INT DEFAULT 0,
    server_url VARCHAR(255),
    service_url VARCHAR(255),
    portal_code VARCHAR(64),
    auto_create INT DEFAULT 1,
    default_role VARCHAR(64) DEFAULT 'ROLE_ANALYST',
    attribute_user VARCHAR(64) DEFAULT 'user',
    attribute_name VARCHAR(64) DEFAULT 'displayName',
    attribute_email VARCHAR(64) DEFAULT 'mail',
    update_time DATETIME,
    update_by VARCHAR(64)
);

CREATE TABLE IF NOT EXISTS sys_portal (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    code VARCHAR(64) NOT NULL,
    name VARCHAR(128) NOT NULL,
    domain VARCHAR(255),
    status VARCHAR(16) DEFAULT 'ENABLED',
    remark VARCHAR(255),
    create_time DATETIME,
    UNIQUE KEY uk_sys_portal_code (code)
);
