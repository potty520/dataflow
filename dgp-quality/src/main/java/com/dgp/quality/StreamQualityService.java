package com.dgp.quality;

import com.dgp.common.BizException;
import com.dgp.common.Db;
import com.dgp.common.ErrorCode;
import com.dgp.common.JsonUtil;
import com.dgp.common.M;
import com.dgp.security.AuthContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * 流式（微批窗口）数据质检引擎。
 *
 * <p>不引入 Kafka/Flink：以"水位字段 + 定时微批"实现准实时质检——每 {@code window_seconds} 秒
 * 取水位区间内的新增数据，对每条规则执行一次"总行数 + 不合格行数"聚合 SQL，结果落 qa_stream_result，
 * 超容差生成 qa_stream_alert 告警。可平滑替换为真正的流消费（只需替换窗口取数部分）。
 */
@Service
public class StreamQualityService {

    private static final Logger log = LoggerFactory.getLogger(StreamQualityService.class);
    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final Pattern DANGEROUS = Pattern.compile(
            "csvread|read_csv|link_schema|file_read|load_file|xp_cmdshell|into\\s+outfile|into\\s+dumpfile");
    private static final long ALERT_DEDUP_MS = 60_000L;

    private final Db db;
    private final ConcurrentHashMap<Long, Long> lastAlertAt = new ConcurrentHashMap<>();

    public StreamQualityService(Db db) {
        this.db = db;
    }

    // ---------------------------------------------------------------- 校验
    /** 流式规则表达式是"不合格条件"（布尔谓词），例如 phone IS NULL OR phone=''。 */
    public static void assertSafePredicate(String expr) {
        if (expr == null || expr.isBlank()) {
            throw new BizException(ErrorCode.RULE_SYNTAX_ERROR, "规则条件不能为空");
        }
        if (expr.length() > 1000) {
            throw new BizException(ErrorCode.RULE_SYNTAX_ERROR, "规则条件过长（≤1000 字符）");
        }
        String s = expr.toLowerCase();
        if (s.contains(";") || s.contains("--") || s.contains("/*")) {
            throw new BizException(ErrorCode.RULE_SYNTAX_ERROR, "规则条件禁止包含多语句或注释");
        }
        for (String bad : new String[]{" insert ", " update ", " delete ", " drop ", " alter ", " truncate ",
                " create ", " merge ", " grant ", " revoke ", " call "}) {
            if ((" " + s.trim() + " ").contains(bad)) {
                throw new BizException(ErrorCode.RULE_SYNTAX_ERROR, "规则条件禁止包含写操作或 DDL");
            }
        }
        if (DANGEROUS.matcher(s).find()) {
            throw new BizException(ErrorCode.RULE_SYNTAX_ERROR, "规则条件禁止调用文件/外部访问类函数");
        }
    }

    /** 保存前校验：表与水位字段必须真实存在（执行一次极轻量查询）。 */
    public void assertSourceReadable(String table, String watermarkCol) {
        String t = Db.identifier(table);
        String c = Db.identifier(watermarkCol);
        try {
            db.list("SELECT " + c + " FROM " + t + " LIMIT 1");
        } catch (Exception e) {
            throw new BizException(ErrorCode.PARAM_INVALID, "表或水位字段不存在/不可读：" + t + "." + c);
        }
    }

    // ---------------------------------------------------------------- 调度
    /** 每 5 秒扫描一次启用的流源；每个源按各自的窗口长度取数。 */
    @Scheduled(initialDelay = 20_000L, fixedDelay = 5_000L)
    public void scheduled() {
        long now = System.currentTimeMillis();
        List<Map<String, Object>> sources = db.list("SELECT * FROM qa_stream_source WHERE status='ENABLED'");
        for (Map<String, Object> s : sources) {
            try {
                Long window = Db.longVal(s.get("windowSeconds"), 60);
                Object lastRun = s.get("lastRunTime");
                if (lastRun instanceof LocalDateTime ldt
                        && ldt.plusSeconds(Math.max(5, window / 3)).isAfter(LocalDateTime.now())) {
                    continue; // 还没到一个窗口，跳过
                }
                runSource(Db.longVal(s.get("id"), 0L), "AUTO");
            } catch (Exception e) {
                log.warn("流式质检源 {} 执行失败: {}", s.get("code"), e.getMessage());
            }
        }
        if (now % 60_000 < 5_000) {
            prune();
        }
    }

    private void prune() {
        try {
            db.update("DELETE FROM qa_stream_result WHERE run_time < ?",
                    LocalDateTime.now().minusDays(7));
        } catch (Exception ignore) {
            // 清理失败不影响主流程
        }
    }

    /** 手工/自动执行一个流源的一个窗口，返回窗口汇总。 */
    public Map<String, Object> runSource(long sourceId, String trigger) {
        Map<String, Object> src = db.one("SELECT * FROM qa_stream_source WHERE id=?", sourceId);
        if (src == null) throw new BizException(ErrorCode.NOT_FOUND, "流式质检源不存在");
        String table = Db.identifier(String.valueOf(src.get("tableName")));
        String wm = Db.identifier(String.valueOf(src.get("watermarkCol")));
        String wmType = String.valueOf(src.get("watermarkType") == null ? "TIMESTAMP" : src.get("watermarkType"));
        String windowFromRaw = src.get("lastWatermark") == null ? null : String.valueOf(src.get("lastWatermark"));
        long windowSeconds = Db.longVal(src.get("windowSeconds"), 60);

        Object fromArg;
        Object toArg;
        String fromText;
        String toText;
        if ("NUMBER".equalsIgnoreCase(wmType)) {
            long max = db.count("SELECT COALESCE(MAX(" + wm + "),0) FROM " + table);
            long from = windowFromRaw == null ? Math.max(0, max - Db.longVal(src.get("batchLimit"), 5000)) : Long.parseLong(windowFromRaw);
            if (max <= from) {
                return M.of("sourceId", sourceId, "status", "IDLE", "reason", "无新增数据",
                        "windowFrom", from, "windowTo", max, "results", List.of());
            }
            fromArg = from;
            toArg = max;
            fromText = String.valueOf(from);
            toText = String.valueOf(max);
        } else {
            LocalDateTime to = LocalDateTime.now().withNano(0);
            LocalDateTime from = windowFromRaw == null ? to.minusSeconds(windowSeconds) : parseTs(windowFromRaw);
            if (!from.isBefore(to)) {
                return M.of("sourceId", sourceId, "status", "IDLE", "reason", "窗口未推进",
                        "windowFrom", TS.format(from), "windowTo", TS.format(to), "results", List.of());
            }
            fromArg = from;
            toArg = to;
            fromText = TS.format(from);
            toText = TS.format(to);
        }

        List<Map<String, Object>> rules = db.list(
                "SELECT * FROM qa_stream_rule WHERE source_id=? AND status='ENABLED'", sourceId);
        List<Map<String, Object>> out = new ArrayList<>();
        int failedRules = 0;
        boolean anyRows = false;
        for (Map<String, Object> r : rules) {
            long ruleId = Db.longVal(r.get("id"), 0L);
            String expr = String.valueOf(r.get("expression"));
            long t0 = System.currentTimeMillis();
            long total;
            long failed;
            try {
                total = db.count("SELECT COUNT(*) FROM " + table + " WHERE " + wm + " > ? AND " + wm + " <= ?",
                        fromArg, toArg);
                failed = total == 0 ? 0 : db.count("SELECT COALESCE(SUM(CASE WHEN (" + expr
                        + ") THEN 1 ELSE 0 END),0) FROM " + table + " WHERE " + wm + " > ? AND " + wm + " <= ?",
                        fromArg, toArg);
            } catch (Exception e) {
                log.warn("流式规则 {} 执行失败: {}", r.get("code"), e.getMessage());
                continue;
            }
            if (total == 0) continue;
            anyRows = true;
            double passRate = Math.round((total - failed) * 10000.0 / total) / 100.0;
            double tolerance = Db.num(r.get("tolerance")) == null ? 0.01 : Db.num(r.get("tolerance"));
            boolean pass = (total - failed) * 1.0 / total >= (1 - tolerance);
            String status = pass ? "PASS" : "FAIL";
            long duration = System.currentTimeMillis() - t0;
            db.insert("INSERT INTO qa_stream_result(source_id,rule_id,window_from,window_to,row_count,fail_count,"
                            + "pass_rate,status,duration_ms,run_time) VALUES(?,?,?,?,?,?,?,?,?,?)",
                    sourceId, ruleId, fromText, toText, total, failed, BigDecimal.valueOf(passRate), status,
                    duration, LocalDateTime.now());
            db.update("UPDATE qa_stream_rule SET last_pass_rate=?,last_fail_count=?,last_row_count=?,last_run_time=? WHERE id=?",
                    BigDecimal.valueOf(passRate), failed, total, LocalDateTime.now(), ruleId);
            if (!pass) {
                failedRules++;
                maybeAlert(src, r, fromText, toText, passRate);
            }
            out.add(M.of("ruleId", ruleId, "ruleName", r.get("name"), "dimension", r.get("dimension"),
                    "rowCount", total, "failCount", failed, "passRate", passRate, "status", status,
                    "durationMs", duration));
        }
        // 空窗口不推进水位：留待下一轮重新评估，避免迟到数据被永久跳过
        if (anyRows) {
            db.update("UPDATE qa_stream_source SET last_watermark=?,last_run_time=? WHERE id=?",
                    toText, LocalDateTime.now(), sourceId);
        } else {
            db.update("UPDATE qa_stream_source SET last_run_time=? WHERE id=?", LocalDateTime.now(), sourceId);
        }
        return M.of("sourceId", sourceId, "sourceCode", src.get("code"), "trigger", trigger,
                "status", out.isEmpty() ? "IDLE" : "DONE", "windowFrom", fromText, "windowTo", toText,
                "ruleCount", out.size(), "failedRules", failedRules, "results", out);
    }

    private void maybeAlert(Map<String, Object> src, Map<String, Object> rule,
                            String from, String to, double passRate) {
        long ruleId = Db.longVal(rule.get("id"), 0L);
        long now = System.currentTimeMillis();
        Long last = lastAlertAt.get(ruleId);
        if (last != null && now - last < ALERT_DEDUP_MS) return;
        if (db.count("SELECT COUNT(*) FROM qa_stream_alert WHERE rule_id=? AND status='OPEN' AND create_time>?",
                ruleId, LocalDateTime.now().minusMinutes(10)) > 0) {
            lastAlertAt.put(ruleId, now);
            return;
        }
        db.insert("INSERT INTO qa_stream_alert(source_id,rule_id,title,severity,pass_rate,window_from,window_to,"
                        + "status,create_time) VALUES(?,?,?,?,?,?,?,'OPEN',?)",
                Db.longVal(src.get("id"), 0L), ruleId,
                "流式质检告警：" + src.get("name") + " / " + rule.get("name"),
                rule.get("severity"), BigDecimal.valueOf(passRate), from, to, LocalDateTime.now());
        lastAlertAt.put(ruleId, now);
    }

    private LocalDateTime parseTs(String s) {
        for (DateTimeFormatter f : new DateTimeFormatter[]{TS, DateTimeFormatter.ISO_LOCAL_DATE_TIME}) {
            try {
                return LocalDateTime.parse(s, f);
            } catch (DateTimeParseException ignore) {
                // try next
            }
        }
        return LocalDateTime.now().withNano(0);
    }

    // ---------------------------------------------------------------- 查询
    /** 实时 KPI：整体通过率、近 24h 结果、告警、按维度、按源 TopN。 */
    public Map<String, Object> metrics() {
        LocalDateTime dayAgo = LocalDateTime.now().minusDays(1);
        List<Map<String, Object>> recent = db.list(
                "SELECT r.*, s.name AS source_name, u.name AS rule_name FROM qa_stream_result r "
                        + "LEFT JOIN qa_stream_source s ON s.id=r.source_id "
                        + "LEFT JOIN qa_stream_rule u ON u.id=r.rule_id ORDER BY r.id DESC LIMIT 10");
        Double avgPass = Db.num(db.scalar(
                "SELECT ROUND(AVG(pass_rate),2) FROM qa_stream_result WHERE run_time>=?", dayAgo));
        long windows = db.count("SELECT COUNT(*) FROM qa_stream_result WHERE run_time>=?", dayAgo);
        long failWindows = db.count("SELECT COUNT(*) FROM qa_stream_result WHERE run_time>=? AND status='FAIL'", dayAgo);
        List<Map<String, Object>> byDimension = db.list(
                "SELECT u.dimension AS dimension, ROUND(AVG(r.pass_rate),2) AS pass_rate, COUNT(*) AS runs "
                        + "FROM qa_stream_result r JOIN qa_stream_rule u ON u.id=r.rule_id "
                        + "WHERE r.run_time>=? GROUP BY u.dimension", dayAgo);
        List<Map<String, Object>> bySource = db.list(
                "SELECT s.code AS source_code, s.name AS source_name, ROUND(AVG(r.pass_rate),2) AS pass_rate, "
                        + "COUNT(*) AS runs, SUM(r.fail_count) AS fail_rows FROM qa_stream_result r "
                        + "JOIN qa_stream_source s ON s.id=r.source_id WHERE r.run_time>=? "
                        + "GROUP BY s.code, s.name ORDER BY runs DESC", dayAgo);
        // 24 小时趋势：单条 GROUP BY（原实现逐小时查 24 次，是概览接口的主要开销）
        Map<Integer, Object> hourMap = new java.util.HashMap<>();
        for (Map<String, Object> row : db.list(
                "SELECT HOUR(run_time) AS h, ROUND(AVG(pass_rate),2) AS v FROM qa_stream_result "
                        + "WHERE run_time>=? GROUP BY HOUR(run_time)", dayAgo)) {
            Object h = row.get("h");
            Integer key = h == null ? null : Integer.valueOf(String.valueOf(h));
            if (key != null) hourMap.put(key, row.get("v"));
        }
        List<Map<String, Object>> trend = new ArrayList<>();
        for (int i = 23; i >= 0; i--) {
            LocalDateTime bucket = LocalDateTime.now().minusHours(i).withMinute(0).withSecond(0).withNano(0);
            trend.add(M.of("hour", bucket.format(DateTimeFormatter.ofPattern("HH:00")),
                    "passRate", hourMap.get(bucket.getHour())));
        }
        return M.of(
                "sourceCount", db.count("SELECT COUNT(*) FROM qa_stream_source"),
                "enabledSource", db.count("SELECT COUNT(*) FROM qa_stream_source WHERE status='ENABLED'"),
                "ruleCount", db.count("SELECT COUNT(*) FROM qa_stream_rule"),
                "avgPassRate", avgPass == null ? 100 : avgPass,
                "windowCount24h", windows,
                "failWindow24h", failWindows,
                "openAlerts", db.count("SELECT COUNT(*) FROM qa_stream_alert WHERE status='OPEN'"),
                "pendingAlerts", db.count("SELECT COUNT(*) FROM qa_stream_alert WHERE status='OPEN'"),
                "byDimension", byDimension,
                "bySource", bySource,
                "trend", trend,
                "recentResults", recent,
                "lastRunTime", db.scalar("SELECT MAX(run_time) FROM qa_stream_result"));
    }

    /** 手工触发全部启用的流源（演示/排障用）。 */
    public Map<String, Object> runAll() {
        List<Map<String, Object>> sources = db.list("SELECT id, code FROM qa_stream_source WHERE status='ENABLED'");
        List<Map<String, Object>> results = new ArrayList<>();
        int fail = 0;
        for (Map<String, Object> s : sources) {
            try {
                Map<String, Object> r = runSource(Db.longVal(s.get("id"), 0L), "MANUAL");
                results.add(r);
                fail += Db.longVal(r.get("failedRules"), 0L) > 0 ? 1 : 0;
            } catch (Exception e) {
                results.add(M.of("sourceId", s.get("id"), "status", "FAILED", "message", e.getMessage()));
            }
        }
        return M.of("sources", results, "total", sources.size(), "failedSources", fail);
    }

    /** 历史结果分页（供 UI 表格）。 */
    public Map<String, Object> results(Long sourceId, Long ruleId, String status, int pageNum, int pageSize) {
        StringBuilder where = new StringBuilder(" WHERE 1=1");
        List<Object> args = new ArrayList<>();
        if (sourceId != null) {
            where.append(" AND r.source_id=?");
            args.add(sourceId);
        }
        if (ruleId != null) {
            where.append(" AND r.rule_id=?");
            args.add(ruleId);
        }
        if (status != null && !status.isBlank()) {
            where.append(" AND r.status=?");
            args.add(status);
        }
        String base = "SELECT r.*, s.name AS source_name, s.code AS source_code, u.name AS rule_name, "
                + "u.dimension AS dimension, u.severity AS severity FROM qa_stream_result r "
                + "LEFT JOIN qa_stream_source s ON s.id=r.source_id "
                + "LEFT JOIN qa_stream_rule u ON u.id=r.rule_id" + where;
        return db.page(base, "r.id DESC", pageNum, pageSize, args.toArray()).toMap();
    }

    public Map<String, Object> handleAlert(long alertId) {
        Map<String, Object> a = db.one("SELECT * FROM qa_stream_alert WHERE id=?", alertId);
        if (a == null) throw new BizException(ErrorCode.NOT_FOUND, "告警不存在");
        if ("HANDLED".equals(String.valueOf(a.get("status")))) {
            throw new BizException(ErrorCode.PARAM_INVALID, "告警已处置，请勿重复操作");
        }
        db.update("UPDATE qa_stream_alert SET status='HANDLED',handle_time=?,handler=? WHERE id=?",
                LocalDateTime.now(), AuthContext.username(), alertId);
        return M.of("id", alertId, "status", "HANDLED");
    }

    public Map<String, Object> alerts(String status, int pageNum, int pageSize) {
        StringBuilder where = new StringBuilder(" WHERE 1=1");
        List<Object> args = new ArrayList<>();
        if (status != null && !status.isBlank()) {
            where.append(" AND a.status=?");
            args.add(status);
        }
        String base = "SELECT a.*, s.name AS source_name, u.name AS rule_name, u.dimension AS dimension "
                + "FROM qa_stream_alert a LEFT JOIN qa_stream_source s ON s.id=a.source_id "
                + "LEFT JOIN qa_stream_rule u ON u.id=a.rule_id" + where;
        return db.page(base, "a.id DESC", pageNum, pageSize, args.toArray()).toMap();
    }

    /** 源健康度：每个源最近一次执行、最近窗口通过率。 */
    public List<Map<String, Object>> sourceHealth() {
        List<Map<String, Object>> sources = db.list("SELECT * FROM qa_stream_source ORDER BY id");
        for (Map<String, Object> s : sources) {
            long id = Db.longVal(s.get("id"), 0L);
            s.put("ruleCount", db.count("SELECT COUNT(*) FROM qa_stream_rule WHERE source_id=?", id));
            Object avg = db.scalar("SELECT ROUND(AVG(pass_rate),2) FROM qa_stream_result WHERE source_id=? "
                    + "AND run_time>=?", id, LocalDateTime.now().minusHours(1));
            s.put("recentPassRate", avg == null ? 100 : avg);
            s.put("recentFailRows", db.count("SELECT COALESCE(SUM(fail_count),0) FROM qa_stream_result "
                    + "WHERE source_id=? AND run_time>=?", id, LocalDateTime.now().minusHours(1)));
            s.put("openAlerts", db.count("SELECT COUNT(*) FROM qa_stream_alert WHERE source_id=? AND status='OPEN'", id));
        }
        return sources;
    }

    public LocalDate today() {
        return LocalDate.now();
    }

    // ---------------------------------------------------------------- 实时推送接入
    /** 保存推送接入配置：fieldMapping 为 JSON（表字段 -> 报文键名），例如 {"device_id":"deviceId","metric_value":"value"}。 */
    public Map<String, Object> savePushConfig(long sourceId, String fieldMapping, String status) {
        if (db.one("SELECT id FROM qa_stream_source WHERE id=?", sourceId) == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "流源不存在");
        }
        if (fieldMapping == null || fieldMapping.isBlank()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "字段映射不能为空");
        }
        if (!(JsonUtil.parse(fieldMapping) instanceof Map)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "字段映射必须是 JSON 对象（表字段 -> 报文键名）");
        }
        db.update("DELETE FROM qa_stream_push WHERE source_id=?", sourceId);
        db.insert("INSERT INTO qa_stream_push(source_id,field_mapping,status,push_count,create_time) VALUES(?,?,?,0,?)",
                sourceId, fieldMapping, (status == null || status.isBlank()) ? "ENABLED" : status, LocalDateTime.now());
        return M.of("sourceId", sourceId, "status", (status == null || status.isBlank()) ? "ENABLED" : status);
    }

    public Map<String, Object> pushConfig(long sourceId) {
        return db.one("SELECT * FROM qa_stream_push WHERE source_id=?", sourceId);
    }

    /** 接收一条实时事件：按字段映射落库 → 逐规则判定 → 写结果/告警（事件级实时校验）。 */
    @SuppressWarnings("unchecked")
    public Map<String, Object> pushEvent(String sourceCode, Map<String, Object> payload) {
        if (payload == null || payload.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "推送报文不能为空");
        }
        Map<String, Object> src = db.one("SELECT * FROM qa_stream_source WHERE code=?", sourceCode);
        if (src == null) throw new BizException(ErrorCode.NOT_FOUND, "流源不存在：" + sourceCode);
        long sourceId = Db.longVal(src.get("id"), 0L);
        Map<String, Object> cfg = db.one("SELECT * FROM qa_stream_push WHERE source_id=? AND status='ENABLED'", sourceId);
        if (cfg == null) throw new BizException(ErrorCode.PARAM_INVALID, "该流源未开启推送接入，请先配置字段映射");
        Map<String, Object> mapping = (Map<String, Object>) JsonUtil.parse(String.valueOf(cfg.get("fieldMapping")));
        if (mapping == null || mapping.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "推送字段映射为空");
        }
        String table = Db.identifier(String.valueOf(src.get("tableName")));
        String pkCol = Db.str(src.get("pkCol"));
        if (pkCol == null || pkCol.isBlank()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "推送接入要求流源配置主键字段（pk_col），用于事件级规则判定");
        }
        Object pkKey = mapping.get(pkCol);
        if (pkKey == null || String.valueOf(pkKey).isBlank()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "推送字段映射缺少主键字段：" + pkCol);
        }
        Object pkVal = payload.get(String.valueOf(pkKey));
        if (pkVal == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "推送报文缺少主键字段：" + pkCol);
        }
        List<String> cols = new ArrayList<>();
        List<Object> vals = new ArrayList<>();
        for (Map.Entry<String, Object> e : mapping.entrySet()) {
            cols.add(Db.identifier(e.getKey()));
            vals.add(payload.get(String.valueOf(e.getValue())));
        }
        String insert = "INSERT INTO " + table + " (" + String.join(",", cols) + ") VALUES ("
                + String.join(",", Collections.nCopies(cols.size(), "?")) + ")";
        db.update(insert, vals.toArray());
        String eventTime = payload.get("eventTime") == null
                ? TS.format(LocalDateTime.now()) : String.valueOf(payload.get("eventTime"));
        List<Map<String, Object>> rules = db.list(
                "SELECT * FROM qa_stream_rule WHERE source_id=? AND status='ENABLED'", sourceId);
        List<Map<String, Object>> out = new ArrayList<>();
        int failedRules = 0;
        for (Map<String, Object> r : rules) {
            long ruleId = Db.longVal(r.get("id"), 0L);
            String expr = String.valueOf(r.get("expression"));
            long failed;
            try {
                failed = db.count("SELECT COUNT(*) FROM " + table + " WHERE "
                        + Db.identifier(pkCol) + "=? AND (" + expr + ")", pkVal);
            } catch (Exception e) {
                log.warn("推送事件规则 {} 判定失败: {}", r.get("code"), e.getMessage());
                continue;
            }
            String status = failed > 0 ? "FAIL" : "PASS";
            double passRate = failed > 0 ? 0 : 100;
            db.insert("INSERT INTO qa_stream_result(source_id,rule_id,window_from,window_to,row_count,fail_count,"
                            + "pass_rate,status,duration_ms,run_time) VALUES(?,?,?,?,?,?,?,?,?,?)",
                    sourceId, ruleId, eventTime, eventTime, 1L, failed, BigDecimal.valueOf(passRate),
                    status, 0L, LocalDateTime.now());
            db.update("UPDATE qa_stream_rule SET last_pass_rate=?,last_fail_count=?,last_row_count=1,last_run_time=? WHERE id=?",
                    BigDecimal.valueOf(passRate), failed, LocalDateTime.now(), ruleId);
            if (failed > 0) {
                failedRules++;
                maybeAlert(src, r, eventTime, eventTime, passRate);
            }
            out.add(M.of("ruleId", ruleId, "ruleName", r.get("name"), "dimension", r.get("dimension"),
                    "failed", failed > 0, "status", status));
        }
        db.update("UPDATE qa_stream_push SET push_count=push_count+1,last_push_time=? WHERE source_id=?",
                LocalDateTime.now(), sourceId);
        db.update("UPDATE qa_stream_source SET last_run_time=? WHERE id=?", LocalDateTime.now(), sourceId);
        return M.of("sourceCode", sourceCode, "accepted", true, "eventTime", eventTime,
                "ruleCount", out.size(), "failedRules", failedRules, "results", out);
    }
}
