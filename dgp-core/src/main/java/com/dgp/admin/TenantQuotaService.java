package com.dgp.admin;

import com.dgp.common.Db;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 将 sys_tenant_quota 同步到 Redis，供网关按租户限流/日配额使用。
 * Redis key：
 *   dgp:quota:tenant:{id}:rpm
 *   dgp:quota:tenant:{id}:daily
 */
@Service
public class TenantQuotaService {

    private static final Logger log = LoggerFactory.getLogger(TenantQuotaService.class);
    public static final String RPM_KEY = "dgp:quota:tenant:%s:rpm";
    public static final String DAILY_KEY = "dgp:quota:tenant:%s:daily";

    private final Db db;
    private final StringRedisTemplate redis;

    public TenantQuotaService(Db db, @Autowired(required = false) StringRedisTemplate redis) {
        this.db = db;
        this.redis = redis;
    }

    public void ensureDefaults() {
        try {
            List<Map<String, Object>> tenants = db.list("SELECT id FROM sys_tenant");
            for (Map<String, Object> t : tenants) {
                long tid = Db.longVal(t.get("id"), 0L);
                if (tid <= 0) continue;
                Map<String, Object> q = db.one("SELECT id FROM sys_tenant_quota WHERE tenant_id=?", tid);
                if (q == null) {
                    db.insert("INSERT INTO sys_tenant_quota(tenant_id,rpm_limit,daily_api_limit,max_concurrent_jobs,max_storage_mb,update_time) "
                                    + "VALUES(?,?,?,?,?,?)",
                            tid, 600, 100000, 20, 10240, LocalDateTime.now());
                }
            }
            syncToRedis();
        } catch (Exception e) {
            log.warn("租户配额初始化跳过：{}", e.getMessage());
        }
    }

    @Scheduled(fixedDelay = 60_000, initialDelay = 15_000)
    public void syncToRedis() {
        if (redis == null) return;
        try {
            List<Map<String, Object>> rows = db.list("SELECT * FROM sys_tenant_quota");
            for (Map<String, Object> r : rows) {
                String tid = String.valueOf(r.get("tenantId"));
                redis.opsForValue().set(String.format(RPM_KEY, tid), String.valueOf(r.getOrDefault("rpmLimit", 600)));
                redis.opsForValue().set(String.format(DAILY_KEY, tid), String.valueOf(r.getOrDefault("dailyApiLimit", 100000)));
            }
        } catch (Exception e) {
            log.debug("配额同步 Redis 失败：{}", e.getMessage());
        }
    }

    public Map<String, Object> get(long tenantId) {
        Map<String, Object> q = db.one("SELECT * FROM sys_tenant_quota WHERE tenant_id=?", tenantId);
        if (q != null) return q;
        ensureDefaults();
        return db.one("SELECT * FROM sys_tenant_quota WHERE tenant_id=?", tenantId);
    }

    public void upsert(long tenantId, int rpm, int daily, int jobs, int storageMb) {
        Map<String, Object> q = db.one("SELECT id FROM sys_tenant_quota WHERE tenant_id=?", tenantId);
        if (q == null) {
            db.insert("INSERT INTO sys_tenant_quota(tenant_id,rpm_limit,daily_api_limit,max_concurrent_jobs,max_storage_mb,update_time) "
                            + "VALUES(?,?,?,?,?,?)",
                    tenantId, rpm, daily, jobs, storageMb, LocalDateTime.now());
        } else {
            db.update("UPDATE sys_tenant_quota SET rpm_limit=?,daily_api_limit=?,max_concurrent_jobs=?,max_storage_mb=?,update_time=? WHERE tenant_id=?",
                    rpm, daily, jobs, storageMb, LocalDateTime.now(), tenantId);
        }
        if (redis != null) {
            redis.opsForValue().set(String.format(RPM_KEY, tenantId), String.valueOf(rpm));
            redis.opsForValue().set(String.format(DAILY_KEY, tenantId), String.valueOf(daily));
        }
    }
}
