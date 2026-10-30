-- =============================================================
-- DGP v1.12 → v1.13 升级脚本（MySQL 8）
-- 新增：BPM 审批引擎（流程定义 / 实例 / 任务）
-- =============================================================

CREATE TABLE IF NOT EXISTS bpm_process_def (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT DEFAULT 1,
    code VARCHAR(64) NOT NULL,
    name VARCHAR(128) NOT NULL,
    version INT DEFAULT 1,
    status VARCHAR(16) DEFAULT 'DRAFT',
    nodes_json TEXT,
    remark VARCHAR(255),
    create_time DATETIME,
    update_time DATETIME,
    UNIQUE KEY uk_bpm_def_code (code)
);

CREATE TABLE IF NOT EXISTS bpm_instance (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT DEFAULT 1,
    def_id BIGINT,
    def_code VARCHAR(64),
    def_name VARCHAR(128),
    title VARCHAR(255),
    business_type VARCHAR(64),
    business_id VARCHAR(64),
    form_json TEXT,
    status VARCHAR(16) DEFAULT 'RUNNING',
    current_node VARCHAR(64),
    starter VARCHAR(64),
    start_time DATETIME,
    end_time DATETIME
);

CREATE TABLE IF NOT EXISTS bpm_task (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    instance_id BIGINT NOT NULL,
    node_id VARCHAR(64),
    node_name VARCHAR(128),
    assignee_role VARCHAR(64),
    assignee VARCHAR(64),
    status VARCHAR(16) DEFAULT 'PENDING',
    comment VARCHAR(500),
    create_time DATETIME,
    finish_time DATETIME,
    operator VARCHAR(64),
    KEY idx_bpm_task_instance (instance_id),
    KEY idx_bpm_task_status (status)
);
