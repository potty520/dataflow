package com.dgp.security;

import com.dgp.common.BizException;
import com.dgp.common.Db;
import com.dgp.common.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 敏感数据识别：字段名 / 字段内容 / 字段注释三种方式（Req-3.6.1），
 * 内容方式对样本值做正则命中率统计，命中率 ≥60% 判定为敏感。
 *
 * <p>修正点：按表扫描不再删除全域结果；规则正则预编译；标识符先经白名单校验；
 * 整体放入事务，避免扫描中断留下半套结果。</p>
 */
@Service
public class SensitiveScanService {

    private static final int SAMPLE_SIZE = 200;

    private final Db db;

    public SensitiveScanService(Db db) {
        this.db = db;
    }

    @Transactional
    public Map<String, Object> scan(String scope, String operator) {
        long start = System.currentTimeMillis();
        if (scope != null && !scope.isBlank() && !Db.isIdentifier(scope)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "非法的表名");
        }
        List<Map<String, Object>> rules = db.list("SELECT * FROM sec_sensitive_rule WHERE status='ENABLED'");
        Map<Long, Pattern> compiled = new LinkedHashMap<>();
        for (Map<String, Object> r : rules) {
            try {
                compiled.put(((Number) r.get("id")).longValue(),
                        Pattern.compile(String.valueOf(r.get("pattern")),
                                "NAME".equals(String.valueOf(r.get("matchType"))) ? Pattern.CASE_INSENSITIVE : 0));
            } catch (Exception ignore) {
                // 非法正则在创建时已拒绝，这里仅兜底跳过
            }
        }
        boolean scoped = scope != null && !scope.isBlank();
        String sql = "SELECT c.*, t.name AS tbl FROM md_column c JOIN md_table t ON t.id=c.table_id "
                + (scoped ? "WHERE t.name=? " : "") + "ORDER BY t.name, c.column_order";
        List<Map<String, Object>> columns = scoped ? db.list(sql, scope) : db.list(sql);

        // 只清理本次扫描范围内的历史结果（原实现 DELETE 全表，按表扫描会摧毁其它表结果）
        if (scoped) {
            db.update("DELETE FROM sec_sensitive_result WHERE table_name=?", scope);
        } else {
            db.update("DELETE FROM sec_sensitive_result");
        }
        int hits = 0;
        Map<String, Integer> ruleHits = new LinkedHashMap<>();
        for (Map<String, Object> c : columns) {
            String columnName = String.valueOf(c.get("name"));
            String tableName = String.valueOf(c.get("tbl"));
            if (!Db.isIdentifier(columnName) || !Db.isIdentifier(tableName)) continue;
            for (Map<String, Object> r : rules) {
                String matchType = String.valueOf(r.get("matchType"));
                Pattern pattern = compiled.get(((Number) r.get("id")).longValue());
                if (pattern == null) continue;
                String comment = c.get("comment") == null ? "" : String.valueOf(c.get("comment"));
                boolean matched = false;
                String sample = null;
                double hitRate = 0;
                if ("NAME".equals(matchType)) {
                    matched = pattern.matcher(columnName).matches();
                } else if ("COMMENT".equals(matchType)) {
                    matched = pattern.matcher(comment).find();
                } else if ("CONTENT".equals(matchType)) {
                    try {
                        List<Map<String, Object>> vals = db.list(
                                "SELECT " + columnName + " AS v FROM " + tableName
                                        + " WHERE " + columnName + " IS NOT NULL LIMIT " + SAMPLE_SIZE);
                        if (!vals.isEmpty()) {
                            int hit = 0;
                            for (Map<String, Object> v : vals) {
                                if (pattern.matcher(String.valueOf(v.get("v"))).matches()) {
                                    hit++;
                                    if (sample == null) sample = String.valueOf(v.get("v"));
                                }
                            }
                            hitRate = hit * 100d / vals.size();
                            matched = hitRate >= 60;
                        }
                    } catch (Exception ignore) {
                        matched = false;
                    }
                }
                if (matched) {
                    db.insert("INSERT INTO sec_sensitive_result(tenant_id,rule_id,table_id,table_name,column_id,column_name,"
                                    + "match_type,security_level,sample_value,hit_rate,status,scan_time) "
                                    + "VALUES(?,?,?,?,?,?,?,?,?,?,'CONFIRMED',?)",
                            AuthContext.tenantId(), r.get("id"), c.get("tableId"), tableName, c.get("id"), columnName,
                            matchType, r.get("securityLevel"),
                            sample == null ? "（按字段名/注释命中）" : maskSample(sample),
                            BigDecimal.valueOf(hitRate).setScale(2, java.math.RoundingMode.HALF_UP), LocalDateTime.now());
                    db.update("UPDATE md_column SET sensitive_level=? WHERE id=? AND (sensitive_level IS NULL OR sensitive_level < ?)",
                            r.get("securityLevel"), c.get("id"), r.get("securityLevel"));
                    ruleHits.merge(String.valueOf(r.get("name")), 1, Integer::sum);
                    hits++;
                    break;
                }
            }
        }
        for (Map.Entry<String, Integer> e : ruleHits.entrySet()) {
            db.update("UPDATE sec_sensitive_rule SET hit_columns=? WHERE name=?", e.getValue(), e.getKey());
        }
        long duration = System.currentTimeMillis() - start;
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("scannedColumns", columns.size());
        out.put("sensitiveColumns", hits);
        out.put("scope", scope == null ? "全域" : scope);
        out.put("durationMs", duration);
        out.put("ruleHits", ruleHits);
        out.put("operator", operator);
        out.put("scanTime", LocalDateTime.now());
        return out;
    }

    private String maskSample(String s) {
        if (s.length() <= 4) return "****";
        return s.substring(0, 2) + "****" + s.substring(s.length() - 2);
    }

    public List<Map<String, Object>> policiesFor(String table) {
        if (!Db.isIdentifier(table)) return new ArrayList<>();
        List<Map<String, Object>> list = db.list(
                "SELECT * FROM sec_mask_policy WHERE status='ENABLED' AND table_name=?", table);
        return list == null ? new ArrayList<>() : list;
    }
}
