package com.dgp.quality.service;

import com.dgp.common.BizException;
import com.dgp.common.Db;
import com.dgp.common.ErrorCode;
import com.dgp.common.Ids;
import com.dgp.common.JsonUtil;
import com.dgp.security.MaskService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 质量稽核引擎（对应 engine-quality 的本地实现）。
 *
 * <p>规则存储为"返回异常行数的 SQL 表达式"，引擎在源数据层真实执行，按
 * {@code score = 100 × max(0, 1 - failRate / tolerance)} 计算单规则得分，
 * 再按维度权重汇总（对齐 01 文档 §8.6.5）。</p>
 */
@Service
public class QualityEngine {

    private final Db db;
    private final MaskService maskService;

    public static final Map<String, String> DIM_NAME = new LinkedHashMap<>();
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    /** 规则表达式禁止出现的危险函数/关键字（只读沙箱）。 */
    private static final Pattern DANGEROUS = Pattern.compile(
            "(?i)\\b(csvread|csvwrite|file_read|file_write|link_schema|runscript|pg_read_file|pg_read_binary_file|"
                    + "pg_ls_dir|lo_import|lo_export|dblink|xp_cmdshell|into\\s+outfile|into\\s+dumpfile)\\b");

    static {
        DIM_NAME.put("COMPLETENESS", "完整性");
        DIM_NAME.put("UNIQUENESS", "唯一性");
        DIM_NAME.put("ACCURACY", "准确性");
        DIM_NAME.put("VALIDITY", "有效性");
        DIM_NAME.put("CONSISTENCY", "一致性");
        DIM_NAME.put("TIMELINESS", "时效性");
    }

    public QualityEngine(Db db, MaskService maskService) {
        this.db = db;
        this.maskService = maskService;
    }

    public Map<String, Object> runRule(long ruleId, String trigger, String operator, Long instanceId, boolean autoIssue) {
        Map<String, Object> rule = db.one("SELECT * FROM qa_rule WHERE id=?", ruleId);
        if (rule == null) throw new BizException(ErrorCode.RULE_SYNTAX_ERROR, "规则不存在");
        String table = String.valueOf(rule.get("tableName"));
        if (!Db.isIdentifier(table)) throw new BizException(ErrorCode.RULE_SYNTAX_ERROR, "规则关联的表名不合法");
        long total = db.count("SELECT COUNT(*) FROM " + table);
        long fail;
        long start = System.currentTimeMillis();
        try {
            fail = db.count(substitute(String.valueOf(rule.get("expression"))));
        } catch (Exception e) {
            db.update("UPDATE qa_rule SET status='ERROR', last_run_time=? WHERE id=?", LocalDateTime.now(), ruleId);
            throw new BizException(ErrorCode.RULE_SYNTAX_ERROR, "规则表达式执行失败，请检查语法：" + rule.get("name"));
        }
        long duration = System.currentTimeMillis() - start;
        if (fail < 0) fail = 0;
        if (fail > total) fail = total;
        double passRate = total == 0 ? 100d : (total - fail) * 100d / total;
        Double tolNum = Db.num(rule.get("tolerance"));
        double tol = tolNum == null || tolNum <= 0 ? 0.01 : tolNum;
        double score = 100d * Math.max(0d, 1d - (fail / (double) Math.max(total, 1)) / tol);
        score = Math.min(100d, Math.round(score * 100) / 100d);
        BigDecimal pr = BigDecimal.valueOf(passRate).setScale(2, RoundingMode.HALF_UP);
        BigDecimal sc = BigDecimal.valueOf(score).setScale(2, RoundingMode.HALF_UP);
        String status = fail == 0 ? "PASSED" : "FAILED";

        db.update("UPDATE qa_rule SET last_total=?, last_failed=?, last_pass_rate=?, last_run_time=?, status='ENABLED' WHERE id=?",
                total, fail, pr, LocalDateTime.now(), ruleId);
        if (instanceId != null) {
            db.insert("INSERT INTO qa_check_result(instance_id,rule_id,rule_name,table_name,column_name,dimension,total_cnt,fail_cnt,"
                            + "pass_rate,score,result_status,run_time,duration_ms) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?)",
                    instanceId, ruleId, rule.get("name"), table, rule.get("columnName"), rule.get("dimension"),
                    total, fail, pr, sc, status, LocalDateTime.now(), duration);
        }
        if (fail > 0 && autoIssue && Db.longVal(rule.get("autoIssue"), 0L) == 1L) {
            createIssue(rule, fail, total, String.valueOf(rule.get("owner")));
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("ruleId", ruleId);
        out.put("ruleName", rule.get("name"));
        out.put("tableName", table);
        out.put("columnName", rule.get("columnName"));
        out.put("dimension", rule.get("dimension"));
        out.put("total", total);
        out.put("failed", fail);
        out.put("passRate", pr);
        out.put("score", sc);
        out.put("status", status);
        out.put("durationMs", duration);
        out.put("triggerType", trigger);
        out.put("operator", operator);
        return out;
    }

    @Transactional
    public Map<String, Object> runCheck(long checkId, String trigger, String operator) {
        Map<String, Object> check = db.one("SELECT * FROM qa_check WHERE id=?", checkId);
        if (check == null) throw new BizException(ErrorCode.PARAM_INVALID, "稽核任务不存在");
        List<Map<String, Object>> rules = db.list(
                "SELECT r.* FROM qa_check_rule cr JOIN qa_rule r ON r.id=cr.rule_id WHERE cr.check_id=? AND r.status<>'DISABLED'", checkId);
        LocalDateTime start = LocalDateTime.now();
        long instanceId = db.insert("INSERT INTO qa_check_instance(check_id,check_name,status,start_time,rule_total,trigger_type,operator) "
                + "VALUES(?,?,'RUNNING',?,?,?,?)", checkId, check.get("name"), start, rules.size(), trigger, operator);
        List<Map<String, Object>> results = new ArrayList<>();
        int passed = 0;
        Map<String, double[]> dimAgg = new LinkedHashMap<>();
        java.util.Set<String> touchedTables = new java.util.LinkedHashSet<>();
        for (Map<String, Object> r : rules) {
            Map<String, Object> res = runRule(((Number) r.get("id")).longValue(), trigger, operator, instanceId, true);
            results.add(res);
            touchedTables.add(String.valueOf(res.get("tableName")));
            if ("PASSED".equals(res.get("status"))) passed++;
            String dim = String.valueOf(res.get("dimension"));
            double score = Db.num(res.get("score")) == null ? 0 : Db.num(res.get("score"));
            double weight = Db.num(r.get("weight")) == null ? 1 : Db.num(r.get("weight"));
            dimAgg.computeIfAbsent(dim, k -> new double[]{0, 0});
            dimAgg.get(dim)[0] += score * weight;
            dimAgg.get(dim)[1] += weight;
        }
        Map<String, Double> dimScores = new LinkedHashMap<>();
        for (Map.Entry<String, double[]> e : dimAgg.entrySet()) {
            dimScores.put(e.getKey(), Math.round(e.getValue()[0] / Math.max(e.getValue()[1], 1) * 100) / 100d);
        }
        // 与 tableScore 使用同一套"维度权重"口径，避免两个接口给出两个综合分（原实现用规则权重）
        Map<String, Double> weights = dimensionWeights();
        double weighted = 0, wsum = 0;
        for (Map.Entry<String, Double> e : dimScores.entrySet()) {
            double w = weights.getOrDefault(e.getKey(), 0.1);
            weighted += e.getValue() * w;
            wsum += w;
        }
        double totalScore = wsum == 0 ? 100 : Math.round(weighted / wsum * 100) / 100d;
        LocalDateTime end = LocalDateTime.now();
        boolean hasIssue = results.stream().anyMatch(x -> "FAILED".equals(x.get("status")));
        db.update("UPDATE qa_check_instance SET status=?,end_time=?,duration_ms=?,rule_passed=?,score=? WHERE id=?",
                hasIssue ? "SUCCESS_WITH_ISSUE" : "SUCCESS",
                end, java.time.Duration.between(start, end).toMillis(), passed,
                BigDecimal.valueOf(totalScore), instanceId);
        db.update("UPDATE qa_check SET last_run_time=?,next_run_time=? WHERE id=?",
                end, end.plusHours(6), checkId);
        // 真实稽核结束后把结果回写到表级评分与趋势表（原实现从不回写，概览分数永远是种子值）
        for (String t : touchedTables) {
            if (Db.isIdentifier(t)) tableScore(t);
        }
        db.update("INSERT INTO qa_score(tenant_id,object_type,object_id,object_name,stat_date,total_score,score_level,"
                        + "dimension_scores,passed_rules,total_rules) VALUES(?,?,?,?,?,?,?,?,?,?)",
                AuthContextHolder.tenantId(), "CHECK", checkId, check.get("name"), LocalDate.now(),
                BigDecimal.valueOf(totalScore), levelOf(totalScore), JsonUtil.toJson(dimScores), passed, rules.size());

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("instanceId", instanceId);
        out.put("checkId", checkId);
        out.put("checkName", check.get("name"));
        out.put("status", hasIssue ? "SUCCESS_WITH_ISSUE" : "SUCCESS");
        out.put("ruleTotal", rules.size());
        out.put("rulePassed", passed);
        out.put("score", totalScore);
        out.put("scoreLevel", levelOf(totalScore));
        out.put("dimensionScores", dimScores);
        out.put("durationMs", java.time.Duration.between(start, end).toMillis());
        out.put("results", results);
        return out;
    }

    /** 单表评分：按规则最新结果与维度权重计算，并回写 md_table.quality_score。 */
    public Map<String, Object> tableScore(String tableName) {
        if (!Db.isIdentifier(tableName)) throw new BizException(ErrorCode.PARAM_INVALID, "非法的表名");
        List<Map<String, Object>> rules = db.list("SELECT * FROM qa_rule WHERE table_name=? AND last_pass_rate IS NOT NULL", tableName);
        Map<String, double[]> dim = new LinkedHashMap<>();
        Map<String, Double> weights = dimensionWeights();
        for (Map<String, Object> r : rules) {
            Double tolNum = Db.num(r.get("tolerance"));
            double t = tolNum == null || tolNum <= 0 ? 0.01 : tolNum;
            Double prNum = Db.num(r.get("lastPassRate"));
            double pr = (prNum == null ? 100 : prNum) / 100d;
            double score = Math.min(100d, 100d * Math.max(0d, 1d - (1d - pr) / t));
            double w = Db.num(r.get("weight")) == null ? 1 : Db.num(r.get("weight"));
            String d = String.valueOf(r.get("dimension"));
            dim.computeIfAbsent(d, k -> new double[]{0, 0});
            dim.get(d)[0] += score * w;
            dim.get(d)[1] += w;
        }
        Map<String, Object> dimOut = new LinkedHashMap<>();
        double weighted = 0, wsum = 0;
        for (Map.Entry<String, double[]> e : dim.entrySet()) {
            double s = e.getValue()[0] / Math.max(e.getValue()[1], 1);
            dimOut.put(e.getKey(), Math.round(s * 100) / 100d);
            double w = weights.getOrDefault(e.getKey(), 0.1);
            weighted += s * w;
            wsum += w;
        }
        BigDecimal total = BigDecimal.valueOf(wsum == 0 ? 0 : Math.round(weighted / wsum * 100) / 100d);
        if (!rules.isEmpty()) {
            db.update("UPDATE md_table SET quality_score=? WHERE name=?", total, tableName);
            // 写入当日趋势（同表同日只保留一条），保证概览/报告的 qualityTrend 是真实稽核结果
            db.update("DELETE FROM qa_score WHERE object_type='TABLE' AND object_name=? AND stat_date=?",
                    tableName, LocalDate.now());
            db.insert("INSERT INTO qa_score(tenant_id,object_type,object_id,object_name,stat_date,total_score,score_level,"
                            + "dimension_scores,passed_rules,total_rules) VALUES(?,?,?,?,?,?,?,?,?,?)",
                    AuthContextHolder.tenantId(), "TABLE", null, tableName, LocalDate.now(), total,
                    levelOf(total.doubleValue()), JsonUtil.toJson(dimOut), 0, rules.size());
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("tableName", tableName);
        out.put("score", total);
        out.put("level", levelOf(total.doubleValue()));
        out.put("dimensionScores", dimOut);
        out.put("ruleCount", rules.size());
        return out;
    }

    public Map<String, Double> dimensionWeights() {
        Map<String, Double> out = new LinkedHashMap<>();
        Object v = db.scalar("SELECT config_value FROM sys_config WHERE config_key='quality.dimension.weight'");
        Map<String, Object> m = JsonUtil.parse(v == null ? null : String.valueOf(v));
        for (Map.Entry<String, Object> e : m.entrySet()) {
            Double d = Db.num(e.getValue());
            if (d != null) out.put(e.getKey(), d);
        }
        if (out.isEmpty()) {
            out.put("COMPLETENESS", 0.25);
            out.put("UNIQUENESS", 0.15);
            out.put("ACCURACY", 0.2);
            out.put("VALIDITY", 0.15);
            out.put("CONSISTENCY", 0.15);
            out.put("TIMELINESS", 0.1);
        }
        return out;
    }

    private void createIssue(Map<String, Object> rule, long fail, long total, String owner) {
        String table = String.valueOf(rule.get("tableName"));
        String column = String.valueOf(rule.get("columnName"));
        long open = db.count("SELECT COUNT(*) FROM qa_issue WHERE rule_id=? AND table_name=? AND status NOT IN ('CLOSED','IGNORED')",
                rule.get("id"), table);
        if (open > 0) {
            db.update("UPDATE qa_issue SET recur_cnt=recur_cnt+1 WHERE rule_id=? AND status NOT IN ('CLOSED','IGNORED')",
                    rule.get("id"));
            return;
        }
        String assignee = (owner == null || "null".equals(owner) || owner.isBlank()) ? "刘琴" : owner;
        String sample = sample(table, column);
        db.insert("INSERT INTO qa_issue(tenant_id,code,rule_id,rule_name,table_id,table_name,column_name,dimension,severity,assignee,"
                        + "status,discover_time,due_time,description,sample_data,recur_cnt,biz_date) "
                        + "VALUES(?,?,?,?,?,?,?,?,?,?,'DISCOVERED',?,?,?,?,0,?)",
                AuthContextHolder.tenantId(), Ids.code("QI"), rule.get("id"), rule.get("name"), rule.get("tableId"), table,
                column, rule.get("dimension"), rule.get("severity"), assignee,
                LocalDateTime.now(), LocalDateTime.now().plusHours(72),
                fail + " 条数据未通过规则【" + rule.get("name") + "】，异常率 "
                        + String.format("%.2f", fail * 100d / Math.max(1, total)) + "%",
                sample, LocalDate.now());
    }

    /** 问题单样例数据：脱敏后再落库（原实现把手机号/证件号原文写进问题单并在接口返回）。 */
    private String sample(String table, String column) {
        try {
            if (column == null || "null".equals(column) || column.isBlank() || !Db.isIdentifier(column)) return "";
            List<Map<String, Object>> r = db.list("SELECT " + column + " AS v FROM " + table
                    + " WHERE " + column + " IS NOT NULL LIMIT 3");
            StringBuilder sb = new StringBuilder();
            for (Map<String, Object> m : r) {
                Object v = m.get("v");
                if (v == null) continue;
                sb.append(maskService.mask("MASK", v)).append("; ");
            }
            return sb.length() > 480 ? sb.substring(0, 480) : sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    /** 将 ${now-minus-minutes:N} 占位替换为可移植的时间字面量。 */
    static String substitute(String expr) {
        String result = expr;
        int idx;
        while ((idx = result.indexOf("${now-minus-minutes:")) >= 0) {
            int end = result.indexOf("}", idx);
            if (end < 0) throw new BizException(ErrorCode.RULE_SYNTAX_ERROR, "时间占位符缺少右花括号");
            try {
                int m = Integer.parseInt(result.substring(idx + "${now-minus-minutes:".length(), end).trim());
                String lit = "TIMESTAMP '" + LocalDateTime.now().minusMinutes(m).format(FMT) + "'";
                result = result.substring(0, idx) + lit + result.substring(end + 1);
            } catch (NumberFormatException e) {
                throw new BizException(ErrorCode.RULE_SYNTAX_ERROR, "时间占位符参数必须为整数分钟");
            }
        }
        return result;
    }

    public static String levelOf(double score) {
        if (score >= 95) return "优秀";
        if (score >= 85) return "良好";
        if (score >= 70) return "一般";
        if (score >= 60) return "较差";
        return "差";
    }

    /** 校验规则 SQL 合法性：仅允许单条 SELECT，且禁止文件读取类函数。 */
    public static void assertSafe(String sql) {
        if (sql == null || sql.isBlank()) {
            throw new BizException(ErrorCode.RULE_SYNTAX_ERROR, "规则表达式不能为空");
        }
        String s = sql.trim().toLowerCase();
        if (!s.startsWith("select")) throw new BizException(ErrorCode.RULE_SYNTAX_ERROR, "规则表达式必须为 SELECT 查询");
        if (s.contains(";") || s.contains("--") || s.contains("/*")) {
            throw new BizException(ErrorCode.RULE_SYNTAX_ERROR, "规则表达式禁止包含多语句或注释");
        }
        for (String bad : new String[]{" insert ", " update ", " delete ", " drop ", " alter ", " truncate ", " create ", " merge "}) {
            if ((" " + s + " ").contains(bad)) {
                throw new BizException(ErrorCode.RULE_SYNTAX_ERROR, "规则表达式禁止包含写操作");
            }
        }
        if (DANGEROUS.matcher(s).find()) {
            throw new BizException(ErrorCode.RULE_SYNTAX_ERROR, "规则表达式禁止调用文件/外部访问类函数");
        }
    }

    /** 质量引擎在非请求线程（定时稽核）中也要用到租户号。 */
    private static final class AuthContextHolder {
        static long tenantId() {
            Long t = com.dgp.security.AuthContext.tenantId();
            return t == null ? 1L : t;
        }
    }
}
