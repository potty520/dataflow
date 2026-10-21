package com.dgp.service;

import com.dgp.common.BizException;
import com.dgp.common.Db;
import com.dgp.common.ErrorCode;
import com.dgp.common.Ids;
import com.dgp.common.JsonUtil;
import com.dgp.common.M;
import com.dgp.common.TraceIdFilter;
import com.dgp.security.MaskService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 数据服务执行引擎：参数绑定、限流配额、脱敏输出、调用计量。
 */
@Service
public class ServiceExecutionService {

    /** 服务 SQL 只读沙箱：禁止写操作、多语句、注释、文件/外部访问函数。 */
    private static final Pattern DANGEROUS = Pattern.compile(
            "(?i)\\b(csvread|csvwrite|file_read|file_write|link_schema|runscript|pg_read_file|pg_read_binary_file|"
                    + "pg_ls_dir|lo_import|lo_export|dblink|xp_cmdshell)\\b");
    private static final Pattern PLACEHOLDER = Pattern.compile("\\$\\{([A-Za-z_][A-Za-z0-9_]*)}");
    private static final Pattern TABLE_REF = Pattern.compile("(?i)\\b(?:from|join)\\s+([A-Za-z_][A-Za-z0-9_]*)");

    private final Db db;
    private final MaskService maskService;

    public ServiceExecutionService(Db db, MaskService maskService) {
        this.db = db;
        this.maskService = maskService;
    }

    /** 只读校验：数据服务/自助取数/规则试跑统一入口。 */
    public static void assertReadOnly(String sql) {
        if (sql == null || sql.isBlank()) throw new BizException(ErrorCode.SQL_INVALID, "SQL 不能为空");
        String s = sql.trim();
        String lower = s.toLowerCase();
        if (!lower.startsWith("select")) {
            throw new BizException(ErrorCode.SQL_INVALID, "仅允许 SELECT 查询");
        }
        if (s.contains(";")) throw new BizException(ErrorCode.SQL_INVALID, "禁止多语句执行");
        if (s.contains("--") || s.contains("/*")) throw new BizException(ErrorCode.SQL_INVALID, "SQL 中不允许注释");
        if (DANGEROUS.matcher(s).find()) {
            throw new BizException(ErrorCode.SQL_INVALID, "SQL 中不允许调用文件/外部访问类函数");
        }
        if (lower.contains(" into outfile") || lower.contains(" into dumpfile")) {
            throw new BizException(ErrorCode.SQL_INVALID, "SQL 中不允许写文件");
        }
    }

    public Map<String, Object> execute(long apiId, Map<String, Object> params, String appCode, String ip, boolean dryRun) {
        Map<String, Object> api = db.one("SELECT * FROM svc_api WHERE id=?", apiId);
        if (api == null) throw new BizException(ErrorCode.NOT_FOUND, "API 不存在");
        return execute(api, params, appCode, ip, dryRun);
    }

    public Map<String, Object> execute(Map<String, Object> api, Map<String, Object> params, String appCode, String ip, boolean dryRun) {
        String status = String.valueOf(api.get("status"));
        if (!dryRun && !"PUBLISHED".equals(status)) {
            throw new BizException("OFFLINE".equals(status) ? ErrorCode.API_OFFLINE : ErrorCode.API_NOT_PUBLISHED,
                    "API 当前状态为 " + status + "，不可调用");
        }
        // 限流：最近 1 分钟调用次数
        int rateLimit = api.get("rateLimit") == null ? 100 : ((Number) api.get("rateLimit")).intValue();
        long lastMinute = db.count("SELECT COUNT(*) FROM svc_call_log WHERE api_id=? AND call_time > ?",
                api.get("id"), LocalDateTime.now().minusMinutes(1));
        if (!dryRun && lastMinute >= rateLimit) {
            log(api, appCode, ip, 429, 1, "触发限流：" + rateLimit + " 次/分钟", 0);
            throw new BizException(ErrorCode.RATE_LIMITED);
        }
        // 配额
        if (!dryRun && appCode != null) {
            Map<String, Object> quota = db.one("SELECT * FROM svc_quota WHERE app_code=?", appCode);
            if (quota != null && quota.get("usedToday") != null
                    && Db.longVal(quota.get("usedToday"), 0L) >= Db.longVal(quota.get("quotaDay"), Long.MAX_VALUE)) {
                log(api, appCode, ip, 429, 1, "日配额超限", 0);
                throw new BizException(ErrorCode.QUOTA_EXCEEDED);
            }
        }
        String sql = String.valueOf(api.get("sqlText"));
        String bound = bind(sql, params);
        assertReadOnly(bound);
        if (!bound.toLowerCase().contains(" limit ")) {
            bound = bound + " LIMIT 500";
        }
        List<Map<String, Object>> masks = maskingPolicies(sql);
        long t0 = System.currentTimeMillis();
        List<Map<String, Object>> rows;
        try {
            rows = db.list(bound);
        } catch (Exception e) {
            long cost = System.currentTimeMillis() - t0;
            // 不再把数据库原始错误串回传调用方（会泄露表结构/SQL）
            log(api, appCode, ip, 500, cost, "SQL 执行失败：" + e.getClass().getSimpleName(), 0);
            throw new BizException(ErrorCode.SQL_INVALID, "服务取数失败，请联系管理员（traceId：" + TraceIdFilter.current() + "）");
        }
        long cost = System.currentTimeMillis() - t0;
        boolean maskEnabled = !Integer.valueOf(0).equals(api.get("maskEnabled"));
        if (maskEnabled && !masks.isEmpty()) {
            for (Map<String, Object> row : rows) {
                for (Map<String, Object> mk : masks) {
                    String col = String.valueOf(mk.get("columnName"));
                    String camel = Db.camel(col);
                    Object v = row.containsKey(col) ? row.get(col) : row.get(camel);
                    if (v != null) {
                        row.put(row.containsKey(col) ? col : camel,
                                maskService.mask(String.valueOf(mk.get("algorithm")), v));
                    }
                }
            }
        }
        if (!dryRun) {
            log(api, appCode, ip, 200, cost, null, rows.size());
            db.update("UPDATE svc_quota SET used_today=used_today+1 WHERE app_code=?", appCode);
            db.update("UPDATE svc_api SET call_count=call_count+1 WHERE id=?", api.get("id"));
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("apiCode", api.get("code"));
        out.put("apiName", api.get("name"));
        out.put("version", api.get("version"));
        out.put("rows", rows);
        out.put("rowCount", rows.size());
        out.put("costMs", cost);
        out.put("masked", maskEnabled && !masks.isEmpty());
        out.put("maskedColumns", masks.stream().map(m -> m.get("columnName")).toList());
        out.put("traceId", TraceIdFilter.current());
        out.put("params", params);
        return out;
    }

    /** 收集 SQL 中所有源表的脱敏策略（原实现只看第一个 FROM 表，JOIN 出来的敏感列不脱敏）。 */
    private List<Map<String, Object>> maskingPolicies(String sql) {
        Set<String> tables = new LinkedHashSet<>();
        Matcher m = TABLE_REF.matcher(sql);
        while (m.find()) {
            String t = m.group(1);
            if (Db.isIdentifier(t)) tables.add(t);
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (String t : tables) {
            out.addAll(db.list("SELECT column_name,algorithm FROM sec_mask_policy WHERE table_name=? AND status='ENABLED'", t));
        }
        return out;
    }

    private void log(Map<String, Object> api, String appCode, String ip, int code, long cost, String error, int rows) {
        db.insert("INSERT INTO svc_call_log(api_id,api_code,api_name,call_time,status_code,duration_ms,app_code,ip,row_cnt,error_msg,trace_id) "
                        + "VALUES(?,?,?,?,?,?,?,?,?,?,?)",
                api.get("id"), api.get("code"), api.get("name"), LocalDateTime.now(), code, cost,
                appCode, ip, rows, error == null ? null : (error.length() > 480 ? error.substring(0, 480) : error),
                TraceIdFilter.current());
    }

    /**
     * ${param} 字面量绑定：根据占位符是否位于引号内决定是否加引号，
     * 字符串一律做单引号转义，数字/日期按原样内联，杜绝注入（原实现在字符串列上把数字内联导致 500）。
     */
    public String bind(String sql, Map<String, Object> params) {
        Matcher m = PLACEHOLDER.matcher(sql);
        StringBuilder out = new StringBuilder();
        int last = 0;
        while (m.find()) {
            out.append(sql, last, m.start());
            String name = m.group(1);
            Object v = params == null ? null : params.get(name);
            if (v == null || String.valueOf(v).isBlank()) {
                if (!"limit".equals(name)) {
                    throw new BizException(ErrorCode.PARAM_INVALID, "缺少必填参数：" + name);
                }
                v = "50";
            }
            boolean quoted = m.start() > 0 && sql.charAt(m.start() - 1) == '\''
                    && m.end() < sql.length() && sql.charAt(m.end()) == '\'';
            out.append(literal(String.valueOf(v), quoted));
            last = m.end();
        }
        out.append(sql.substring(last));
        return out.toString();
    }

    private String literal(String value, boolean quoted) {
        String s = value.trim().replace("\u0000", "");
        if (quoted) {
            return s.replace("'", "''");
        }
        if (s.matches("-?\\d+(\\.\\d+)?")) return s;
        if (s.matches("\\d{4}-\\d{2}-\\d{2}([ T]\\d{2}:\\d{2}(:\\d{2})?)?")) return "'" + s + "'";
        return "'" + s.replace("'", "''") + "'";
    }

    public List<Map<String, Object>> metrics(int days) {
        return new ArrayList<>(db.list("SELECT stat_date, SUM(call_cnt) AS call_cnt, SUM(success_cnt) AS success_cnt, "
                + "SUM(fail_cnt) AS fail_cnt, ROUND(AVG(avg_ms),2) AS avg_ms FROM svc_call_stat "
                + "WHERE stat_date >= ? GROUP BY stat_date ORDER BY stat_date", java.time.LocalDate.now().minusDays(days)));
    }

    public Map<String, Object> orchestrate(long orchId) {
        Map<String, Object> orch = db.one("SELECT * FROM svc_orchestration WHERE id=?", orchId);
        if (orch == null) throw new BizException(ErrorCode.NOT_FOUND);
        Map<String, Object> obj = JsonUtil.parse(String.valueOf(orch.get("steps")));
        Object stepsObj = obj.get("steps");
        List<Map<String, Object>> steps = stepsObj instanceof List<?> l
                ? JsonUtil.mapper().convertValue(l, new com.fasterxml.jackson.core.type.TypeReference<List<Map<String, Object>>>() {
        }) : List.of();
        long start = System.currentTimeMillis();
        List<Map<String, Object>> results = new ArrayList<>();
        int failed = 0;
        for (Map<String, Object> s : steps) {
            Map<String, Object> api = db.one("SELECT * FROM svc_api WHERE code=?", s.get("apiCode"));
            if (api == null) {
                failed++;
                results.add(M.of("step", s.get("step"), "apiCode", s.get("apiCode"), "status", "SKIPPED",
                        "error", "API 不存在"));
                continue;
            }
            Map<String, Object> params = new LinkedHashMap<>();
            Object stepParams = s.get("params");
            if (stepParams instanceof Map<?, ?> pm) {
                pm.forEach((k, v) -> params.put(String.valueOf(k), v));
            }
            try {
                Map<String, Object> r = execute(api, params, "ORCH", "-", false);
                results.add(M.of("step", s.get("step"), "apiCode", s.get("apiCode"), "status", "SUCCESS",
                        "rowCount", r.get("rowCount"), "costMs", r.get("costMs")));
            } catch (Exception e) {
                failed++;
                results.add(M.of("step", s.get("step"), "apiCode", s.get("apiCode"), "status", "FAILED",
                        "error", e.getMessage()));
            }
        }
        String status = failed == 0 ? "SUCCESS" : (failed == results.size() ? "FAILED" : "PARTIAL_FAILED");
        db.update("UPDATE svc_orchestration SET call_cnt=call_cnt+1 WHERE id=?", orchId);
        db.insert("INSERT INTO svc_orchestration_run(orch_id,status,start_time,end_time,result,operator) VALUES(?,?,?,?,?,?)",
                orchId, status, LocalDateTime.now(), LocalDateTime.now(), JsonUtil.toJson(results),
                com.dgp.security.AuthContext.username());
        return M.of("orchestration", orch.get("name"), "steps", results, "stepCount", results.size(),
                "status", status, "failedSteps", failed,
                "durationMs", System.currentTimeMillis() - start, "runId", Ids.next());
    }

    public Map<String, Object> topApis(int limit) {
        return M.of("records", db.list("SELECT a.id,a.code,a.name,a.call_count,a.avg_ms,a.status,category FROM svc_api a "
                + "ORDER BY a.call_count DESC LIMIT ?", Math.max(1, Math.min(limit, 100))));
    }

    public Map<String, Object> callMetrics() {
        long total = db.count("SELECT COUNT(*) FROM svc_call_log");
        long success = db.count("SELECT COUNT(*) FROM svc_call_log WHERE status_code=200");
        // 真 p99：取降序第 ceil(1% * total) 个耗时（原实现把 MAX 标成 p99）
        Object p99 = null;
        long rank = Math.max(1, (long) Math.ceil(total * 0.01));
        if (total > 0) {
            Map<String, Object> row = db.one("SELECT duration_ms FROM svc_call_log ORDER BY duration_ms DESC LIMIT 1 OFFSET ?", rank - 1);
            p99 = row == null ? null : row.get("durationMs");
        }
        return M.of("totalCalls", total, "successCalls", success,
                "successRate", total == 0 ? 0 : BigDecimal.valueOf(success * 100d / total).setScale(2, java.math.RoundingMode.HALF_UP),
                "avgMs", db.scalar("SELECT ROUND(AVG(duration_ms),2) FROM svc_call_log"),
                "p99Ms", p99,
                "maxMs", db.scalar("SELECT MAX(duration_ms) FROM svc_call_log"),
                "activeApps", db.count("SELECT COUNT(DISTINCT app_code) FROM svc_call_log"));
    }
}
