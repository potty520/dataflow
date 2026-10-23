-- =============================================================
-- DGP v1.5 → v1.6 升级脚本（MySQL 8，执行一次）
--   mysql -u root -p dgp < sql/upgrade-v1.5-to-v1.6.sql
-- 说明：v1.6 新增会话与 refreshToken 轮换表（登出吊销会话、重放检测）；
--       同时新增 tenant1 回归账号 dev_t1/quality_t1/analyst_t1（可选，演示用）
-- =============================================================

CREATE TABLE IF NOT EXISTS sys_session (
    sid VARCHAR(64) NOT NULL PRIMARY KEY,
    username VARCHAR(64),
    user_id BIGINT,
    tenant_id BIGINT,
    status VARCHAR(16) DEFAULT 'ACTIVE',
    ip VARCHAR(64),
    user_agent VARCHAR(255),
    create_time DATETIME,
    last_seen DATETIME,
    revoked_at DATETIME,
    KEY idx_session_user (username, status)
);

CREATE TABLE IF NOT EXISTS sys_refresh_token (
    jti VARCHAR(64) NOT NULL PRIMARY KEY,
    sid VARCHAR(64),
    username VARCHAR(64),
    user_id BIGINT,
    tenant_id BIGINT,
    expire_at BIGINT,
    used_at DATETIME,
    revoked_at DATETIME,
    replaced_by VARCHAR(64),
    create_time DATETIME,
    KEY idx_refresh_sid (sid)
);
