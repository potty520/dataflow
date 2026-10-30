-- =============================================================
-- DGP v1.8 → v1.9 升级脚本（MySQL 8）
-- 新增：质量配置化（工单规则 / 稽核模板）
-- =============================================================

CREATE TABLE IF NOT EXISTS qa_issue_rule (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT DEFAULT 1,
    code VARCHAR(64) NOT NULL,
    name VARCHAR(128) NOT NULL,
    auto_create INT DEFAULT 1,
    overdue_hours INT DEFAULT 72,
    escalate_hours INT DEFAULT 96,
    default_assignee VARCHAR(64),
    status VARCHAR(16) DEFAULT 'ENABLED',
    remark VARCHAR(255),
    create_time DATETIME,
    update_time DATETIME,
    UNIQUE KEY uk_qa_issue_rule_code (code)
);

CREATE TABLE IF NOT EXISTS qa_check_template (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT DEFAULT 1,
    code VARCHAR(64) NOT NULL,
    name VARCHAR(128) NOT NULL,
    check_mode VARCHAR(16) DEFAULT 'FULL',
    schedule_type VARCHAR(16) DEFAULT 'MANUAL',
    cron VARCHAR(64),
    batch_size INT DEFAULT 10000,
    rule_codes TEXT,
    status VARCHAR(16) DEFAULT 'ENABLED',
    remark VARCHAR(255),
    create_time DATETIME,
    update_time DATETIME,
    UNIQUE KEY uk_qa_check_template_code (code)
);
