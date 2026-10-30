-- =============================================================
-- DGP v1.13 → v1.14 升级脚本（MySQL 8）
-- 新增：BPM 业务挂接（标准发布等业务走审批）
-- =============================================================

CREATE TABLE IF NOT EXISTS bpm_business_hook (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    business_type VARCHAR(64) NOT NULL,
    def_code VARCHAR(64) NOT NULL,
    enabled INT DEFAULT 0,
    remark VARCHAR(255),
    create_time DATETIME,
    update_time DATETIME,
    UNIQUE KEY uk_bpm_hook_type (business_type)
);
