-- =============================================================
-- DGP v1.16 → v1.17 升级脚本（MySQL 8）
-- BPM 会签/加签：任务表增加审批模式
-- =============================================================

ALTER TABLE bpm_task
    ADD COLUMN approval_mode VARCHAR(16) DEFAULT 'SINGLE' AFTER status;
