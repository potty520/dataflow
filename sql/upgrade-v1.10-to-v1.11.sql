-- =============================================================
-- DGP v1.10 → v1.11 升级脚本（MySQL 8）
-- 新增：菜单管理与门户角色（BF-10）
-- =============================================================

CREATE TABLE IF NOT EXISTS sys_menu (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT DEFAULT 1,
    code VARCHAR(64) NOT NULL,
    title VARCHAR(128) NOT NULL,
    path VARCHAR(255),
    icon VARCHAR(64),
    perm VARCHAR(128),
    parent_code VARCHAR(64),
    sort_no INT DEFAULT 0,
    status VARCHAR(16) DEFAULT 'ENABLED',
    visible INT DEFAULT 1,
    create_time DATETIME,
    update_time DATETIME,
    UNIQUE KEY uk_sys_menu_code (code)
);

CREATE TABLE IF NOT EXISTS sys_role_menu (
    role_code VARCHAR(64) NOT NULL,
    menu_code VARCHAR(64) NOT NULL,
    PRIMARY KEY (role_code, menu_code)
);

CREATE TABLE IF NOT EXISTS sys_portal_role (
    portal_code VARCHAR(64) NOT NULL,
    role_code VARCHAR(64) NOT NULL,
    status VARCHAR(16) DEFAULT 'ENABLED',
    remark VARCHAR(255),
    create_time DATETIME,
    PRIMARY KEY (portal_code, role_code)
);
