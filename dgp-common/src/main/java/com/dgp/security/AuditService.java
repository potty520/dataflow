package com.dgp.security;

import com.dgp.common.Db;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

/**
 * 审计日志服务，采用 SHA-256 前向哈希链（对齐 01 文档 §13 / API-160 链校验）。
 *
 * <p>修正点：原实现用两次 {@code LocalDateTime.now()}（一次算哈希、一次入库），导致链从第 1 条即断；
 * 且哈希未覆盖 module/detail/ip/result，四列可被随意篡改而不破链。现在：
 * 单次取时、全字段参与哈希、写入串行化，链校验用同一套字段复算。</p>
 */
@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    private final Db db;
    /** 链写入必须串行，否则并发会分叉。 */
    private final Object chainLock = new Object();
    private volatile Map<String, Object> lastVerify;
    private volatile long lastVerifyAt;

    public AuditService(Db db) {
        this.db = db;
    }

    public String hashOf(String prev, String username, String action, String module, String resource,
                         String detail, String ip, String result, String ts) {
        String raw = String.join("|", nz(prev), nz(username), nz(action), nz(module), nz(resource),
                nz(detail), nz(ip), nz(result), nz(ts));
        return sha256(raw);
    }

    public void record(String username, String action, String module, String resource, String detail, String ip, String result) {
        try {
            synchronized (chainLock) {
                // 必须与数据库时间列精度一致：H2/PG 的 TIMESTAMP(6) 只保存微秒，
                // 若用纳秒级 LocalDateTime.now() 直接算哈希，回读复算必然不相等（链从第 1 条即断）。
                LocalDateTime ts = LocalDateTime.now().withNano(0);
                String tsText = ts.toString();
                String det = truncate(detail);
                String prev = lastHash();
                String hash = hashOf(prev, username, action, module, resource, det, ip, result, tsText);
                db.update("INSERT INTO sec_audit_log(tenant_id,log_time,username,action,module,resource,detail,ip,result,trace_id,hash,prev_hash) "
                                + "VALUES(?,?,?,?,?,?,?,?,?,?,?,?)",
                        AuthContext.tenantId(), ts, username, action, module, resource,
                        det, ip, result, com.dgp.common.TraceIdFilter.current(), hash, prev);
                lastVerify = null;
            }
        } catch (Exception e) {
            // 审计失败不能影响主流程，但必须留下强告警
            log.error("审计日志写入失败 user={} action={} resource={}", username, action, resource, e);
        }
    }

    private String lastHash() {
        Map<String, Object> row = db.one("SELECT hash FROM sec_audit_log ORDER BY id DESC LIMIT 1");
        return row == null ? "GENESIS" : String.valueOf(row.get("hash"));
    }

    /** 校验链完整性，返回首个断点位置。 */
    public Map<String, Object> verifyChain(int limit) {
        int max = Math.max(1, Math.min(limit, 20000));
        List<Map<String, Object>> rows = db.list(
                "SELECT id,log_time,username,action,module,resource,detail,ip,result,hash,prev_hash FROM sec_audit_log ORDER BY id ASC LIMIT ?", max);
        String prev = "GENESIS";
        Long brokenAt = null;
        int checked = 0;
        for (Map<String, Object> r : rows) {
            checked++;
            String rowPrev = String.valueOf(r.get("prevHash"));
            String expect = hashOf(prev, str(r.get("username")), str(r.get("action")), str(r.get("module")),
                    str(r.get("resource")), str(r.get("detail")), str(r.get("ip")), str(r.get("result")),
                    String.valueOf(r.get("logTime")));
            if (!rowPrev.equals(prev) || !expect.equals(String.valueOf(r.get("hash")))) {
                brokenAt = ((Number) r.get("id")).longValue();
                break;
            }
            prev = String.valueOf(r.get("hash"));
        }
        Map<String, Object> out = new java.util.LinkedHashMap<>();
        out.put("checked", checked);
        out.put("intact", brokenAt == null);
        out.put("brokenAt", brokenAt);
        out.put("algorithm", "SHA-256(chained)");
        out.put("lastHash", prev);
        out.put("verifyTime", LocalDateTime.now());
        if (brokenAt == null) {
            db.update("INSERT INTO sec_audit_chain(batch_no,start_id,end_id,chain_hash,verify_status,create_time) VALUES(?,?,?,?,?,?)",
                    "VC-" + System.currentTimeMillis(), rows.isEmpty() ? 0 : rows.get(0).get("id"),
                    rows.isEmpty() ? 0 : rows.get(rows.size() - 1).get("id"), prev, "PASS", LocalDateTime.now());
        }
        lastVerify = out;
        lastVerifyAt = System.currentTimeMillis();
        return out;
    }

    /** 供列表接口展示真实链状态（30 秒缓存，避免每次翻页都全表复算）。 */
    public Map<String, Object> cachedVerify() {
        if (lastVerify != null && System.currentTimeMillis() - lastVerifyAt < 30_000L) {
            return lastVerify;
        }
        return verifyChain(5000);
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    private static String str(Object o) {
        return o == null ? "" : String.valueOf(o);
    }

    private static String truncate(String s) {
        if (s == null) return null;
        return s.length() > 900 ? s.substring(0, 900) : s;
    }

    private static String sha256(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(s.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }
}
