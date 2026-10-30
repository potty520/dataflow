-- =============================================================
-- DGP v1.14 → v1.15 升级脚本（MySQL 8）
-- 新增：运维监控（指标快照 / 告警规则 / 告警）
-- =============================================================

CREATE TABLE IF NOT EXISTS mon_metric_snapshot (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    metric VARCHAR(64) NOT NULL,
    metric_value DOUBLE,
    unit VARCHAR(16),
    collect_time DATETIME,
    KEY idx_mon_snapshot_metric (metric, collect_time)
);

CREATE TABLE IF NOT EXISTS mon_alert_rule (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT DEFAULT 1,
    code VARCHAR(64) NOT NULL,
    name VARCHAR(128) NOT NULL,
    metric VARCHAR(64) NOT NULL,
    operator VARCHAR(8) DEFAULT '>',
    threshold DOUBLE,
    level VARCHAR(16) DEFAULT 'WARN',
    enabled INT DEFAULT 1,
    remark VARCHAR(255),
    create_time DATETIME,
    update_time DATETIME,
    UNIQUE KEY uk_mon_rule_code (code)
);

CREATE TABLE IF NOT EXISTS mon_alert (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT DEFAULT 1,
    rule_id BIGINT,
    rule_name VARCHAR(128),
    metric VARCHAR(64),
    metric_value DOUBLE,
    level VARCHAR(16),
    status VARCHAR(16) DEFAULT 'OPEN',
    message VARCHAR(500),
    create_time DATETIME,
    ack_by VARCHAR(64),
    ack_time DATETIME,
    KEY idx_mon_alert_status (status)
);
