package com.dgp.quality;

import com.dgp.common.*;
import com.dgp.quality.service.QualityEngine;
import com.dgp.report.ReportService;
import com.dgp.security.AuthContext;
import com.dgp.security.RequirePerm;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** dgp-quality:8087：规则、稽核、评分、报告、问题闭环。 */
@RestController
@RequestMapping("/api/v1/quality")
public class QualityController {

    private final Db db;
    private final QualityEngine engine;
    private final ReportService reportService;

    private static final Map<String, Set<String>> TRANSITIONS = Map.of(
            "DISCOVERED", Set.of("DISPATCHED", "IGNORED"),
            // 修正：原实现缺少 DISPATCHED→PROCESSING，导致"派单后无法处理"（24103）
            "DISPATCHED", Set.of("PROCESSING", "OVERDUE"),
            "PROCESSING", Set.of("REVIEWING", "OVERDUE"),
            "REVIEWING", Set.of("CLOSED", "PROCESSING"),
            "OVERDUE", Set.of("PROCESSING"),
            "CLOSED", Set.of("RECURRED"),
            "RECURRED", Set.of("DISPATCHED"),
            "IGNORED", Set.of());

    public QualityController(Db db, QualityEngine engine, ReportService reportService) {
        this.db = db;
        this.engine = engine;
        this.reportService = reportService;
    }

    // ================= 规则 =================
    @GetMapping("/rules")
    @RequirePerm("quality:rule:view")
    public Result<Object> rules(@RequestParam(required = false) String keyword,
                                @RequestParam(required = false) String tableName,
                                @RequestParam(required = false) String dimension,
                                @RequestParam(required = false) String severity,
                                @RequestParam(defaultValue = "1") int pageNum,
                                @RequestParam(defaultValue = "20") int pageSize) {
        StringBuilder where = new StringBuilder(" WHERE 1=1");
        List<Object> args = new ArrayList<>();
        if (!Req.blank(keyword)) {
            where.append(" AND (name LIKE ? OR code LIKE ? OR table_name LIKE ?)");
            for (int i = 0; i < 3; i++) args.add("%" + keyword + "%");
        }
        if (!Req.blank(tableName)) {
            where.append(" AND table_name=?");
            args.add(tableName);
        }
        if (!Req.blank(dimension)) {
            where.append(" AND dimension=?");
            args.add(dimension);
        }
        if (!Req.blank(severity)) {
            where.append(" AND severity=?");
            args.add(severity);
        }
        return Result.ok(db.page("SELECT * FROM qa_rule" + where, "id", pageNum, pageSize, args.toArray()).toMap());
    }

    @PostMapping("/rules")
    @RequirePerm("quality:rule:create")
    public Result<Object> createRule(@RequestBody Map<String, Object> b) {
        String expr = Req.str(b.get("expression"));
        QualityEngine.assertSafe(expr);
        String table = Req.str(b.get("tableName"));
        Object tableId = db.scalar("SELECT id FROM md_table WHERE name=?", table);
        long id = db.insert("INSERT INTO qa_rule(tenant_id,code,name,table_id,table_name,column_name,dimension,rule_type,expression,"
                        + "tolerance,weight,severity,status,industry,owner,cycle,check_mode,auto_issue,create_time) "
                        + "VALUES(1,?,?,?,?,?,?,?,?,?,?,?,'ENABLED',?,?,?,?,?,?)",
                Ids.code("QR"), b.get("name"), tableId, table, b.get("columnName"), b.get("dimension"),
                b.getOrDefault("ruleType", "CUSTOM_SQL"), expr,
                b.getOrDefault("tolerance", 0.01), b.getOrDefault("weight", 1), b.getOrDefault("severity", "MAJOR"),
                b.getOrDefault("industry", "GOV"), b.getOrDefault("owner", AuthContext.username()),
                b.getOrDefault("cycle", "DAILY"), b.getOrDefault("checkMode", "FULL"),
                b.getOrDefault("autoIssue", 1), LocalDateTime.now());
        return Result.ok(M.of("id", id));
    }

    @PutMapping("/rules/{id}")
    @RequirePerm("quality:rule:edit")
    public Result<Object> updateRule(@PathVariable long id, @RequestBody Map<String, Object> b) {
        if (b.get("expression") != null) QualityEngine.assertSafe(String.valueOf(b.get("expression")));
        db.update("UPDATE qa_rule SET name=?,expression=?,tolerance=?,weight=?,severity=?,owner=?,cycle=?,auto_issue=? WHERE id=?",
                b.get("name"), b.get("expression"), b.get("tolerance"), b.get("weight"), b.get("severity"),
                b.get("owner"), b.get("cycle"), b.getOrDefault("autoIssue", 1), id);
        return Result.ok(M.of("id", id));
    }

    @DeleteMapping("/rules/{id}")
    @RequirePerm("quality:rule:delete")
    @Transactional
    public Result<Object> deleteRule(@PathVariable long id) {
        db.update("DELETE FROM qa_check_rule WHERE rule_id=?", id);
        db.update("DELETE FROM qa_check_result WHERE rule_id=?", id);
        db.update("DELETE FROM qa_rule WHERE id=?", id);
        return Result.ok(M.of("deleted", true));
    }

    @PostMapping("/rules/{id}/audit")
    @RequirePerm("quality:rule:audit")
    public Result<Object> auditRule(@PathVariable long id, @RequestBody Map<String, Object> b) {
        String status = String.valueOf(b.getOrDefault("status", "ENABLED"));
        db.update("UPDATE qa_rule SET status=? WHERE id=?", status, id);
        return Result.ok(M.of("id", id, "status", status));
    }

    @GetMapping("/rules/templates")
    @RequirePerm("quality:template:view")
    public Result<Object> ruleTemplates(@RequestParam(required = false) String industry) {
        String sql = "SELECT * FROM qa_rule_template" + (Req.blank(industry) ? "" : " WHERE industry=? OR industry='*'") + " ORDER BY id";
        return Result.ok(M.of("records", Req.blank(industry) ? db.list(sql) : db.list(sql, industry)));
    }

    /** 批量应用：按模板 + 目标表生成规则（占位符替换为真实表/列）。 */
    @PostMapping("/rules/batch-apply")
    @RequirePerm("quality:rule:batch")
    public Result<Object> batchApply(@RequestBody Map<String, Object> b) {
        String templateCode = Req.str(b.get("templateCode"));
        String table = Req.str(b.get("tableName"));
        String column = Req.str(b.get("columnName"));
        if (!Db.isIdentifier(table)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "目标表名不合法");
        }
        if (column != null && !"null".equals(column) && !column.isBlank() && !Db.isIdentifier(column)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "目标字段名不合法");
        }
        if (db.one("SELECT id FROM md_table WHERE name=?", table) == null) {
            throw new BizException(ErrorCode.METADATA_NOT_FOUND, "目标表未登记在元数据中：" + table);
        }
        Map<String, Object> tpl = db.one("SELECT * FROM qa_rule_template WHERE code=?", templateCode);
        if (tpl == null) throw new BizException(ErrorCode.NOT_FOUND, "规则模板不存在");
        String expr = String.valueOf(tpl.get("expression"))
                .replace("${table}", table).replace("${column}", column == null ? "id" : column);
        // 修正：批量应用绕过了 createRule 的安全校验，可写入任意 SQL 表达式
        QualityEngine.assertSafe(expr);
        long id = db.insert("INSERT INTO qa_rule(tenant_id,code,name,table_id,table_name,column_name,dimension,rule_type,expression,"
                        + "tolerance,weight,severity,status,industry,owner,cycle,check_mode,auto_issue,create_time) "
                        + "VALUES(?,?,?,?,?,?,?,?,?,0.01,1,'MAJOR','ENABLED',?,?,'DAILY','FULL',1,?)",
                AuthContext.tenantId(), Ids.code("QR"), tpl.get("name") + "（" + table + "）",
                db.scalar("SELECT id FROM md_table WHERE name=?", table), table, column, tpl.get("dimension"),
                tpl.get("ruleType"), expr, tpl.get("industry"), AuthContext.username(), LocalDateTime.now());
        return Result.ok(M.of("id", id, "expression", expr, "fromTemplate", templateCode));
    }

    @PostMapping("/rules/{id}/try-run")
    @RequirePerm("quality:rule:exec")
    public Result<Object> tryRun(@PathVariable long id) {
        return Result.ok(engine.runRule(id, "MANUAL", AuthContext.username(), null, false));
    }

    // ================= 稽核任务 =================
    @GetMapping("/checks")
    @RequirePerm("quality:check:view")
    public Result<Object> checks() {
        List<Map<String, Object>> list = db.list("SELECT * FROM qa_check ORDER BY id");
        for (Map<String, Object> c : list) {
            c.put("ruleCount", db.count("SELECT COUNT(*) FROM qa_check_rule WHERE check_id=?", c.get("id")));
            c.put("lastScore", db.scalar("SELECT score FROM qa_check_instance WHERE check_id=? ORDER BY id DESC LIMIT 1", c.get("id")));
            c.put("lastInstanceStatus", db.scalar("SELECT status FROM qa_check_instance WHERE check_id=? ORDER BY id DESC LIMIT 1", c.get("id")));
        }
        return Result.ok(M.of("records", list, "total", list.size()));
    }

    @PostMapping("/checks")
    @RequirePerm("quality:check:create")
    @Transactional
    public Result<Object> createCheck(@RequestBody Map<String, Object> b) {
        long id = db.insert("INSERT INTO qa_check(tenant_id,code,name,description,check_mode,schedule_type,cron,status,owner,"
                        + "next_run_time,create_time) VALUES(?,?,?,?,?,?,?,'ENABLED',?,?,?)",
                AuthContext.tenantId(), b.getOrDefault("code", Ids.code("QC")), b.get("name"), b.get("description"),
                b.getOrDefault("checkMode", "FULL"), b.getOrDefault("scheduleType", "MANUAL"),
                b.get("cron"), AuthContext.username(), LocalDateTime.now().plusHours(6), LocalDateTime.now());
        Object ruleIds = b.get("ruleIds");
        int bound = 0;
        if (ruleIds instanceof List<?> list) {
            for (Object rid : list) {
                long ruleId = Db.longVal(rid, 0L);
                if (ruleId <= 0 || db.one("SELECT id FROM qa_rule WHERE id=?", ruleId) == null) continue;
                if (db.count("SELECT COUNT(*) FROM qa_check_rule WHERE check_id=? AND rule_id=?", id, ruleId) > 0) continue;
                db.insert("INSERT INTO qa_check_rule(check_id,rule_id) VALUES(?,?)", id, ruleId);
                bound++;
            }
        }
        return Result.ok(M.of("id", id, "ruleCount", bound));
    }

    @PutMapping("/checks/{id}")
    @RequirePerm("quality:check:edit")
    public Result<Object> updateCheck(@PathVariable long id, @RequestBody Map<String, Object> b) {
        db.update("UPDATE qa_check SET name=?,description=?,schedule_type=?,cron=?,status=? WHERE id=?",
                b.get("name"), b.get("description"), b.get("scheduleType"), b.get("cron"),
                b.getOrDefault("status", "ENABLED"), id);
        return Result.ok(M.of("id", id));
    }

    @PostMapping("/checks/{id}/run")
    @RequirePerm("quality:check:exec")
    public Result<Object> runCheck(@PathVariable long id) {
        return Result.ok(engine.runCheck(id, "MANUAL", AuthContext.username()));
    }

    @GetMapping("/checks/{id}/instances")
    @RequirePerm("quality:check:view")
    public Result<Object> checkInstances(@PathVariable long id) {
        List<Map<String, Object>> list = db.list("SELECT * FROM qa_check_instance WHERE check_id=? ORDER BY id DESC LIMIT 20", id);
        for (Map<String, Object> i : list) {
            i.put("results", db.list("SELECT * FROM qa_check_result WHERE instance_id=? ORDER BY id", i.get("id")));
        }
        return Result.ok(M.of("records", list, "total", list.size()));
    }

    // ================= 报告 =================
    @GetMapping("/reports")
    @RequirePerm("quality:report:view")
    public Result<Object> reports(@RequestParam(defaultValue = "1") int pageNum, @RequestParam(defaultValue = "20") int pageSize) {
        return Result.ok(db.page("SELECT * FROM qa_report", "id DESC", pageNum, pageSize).toMap());
    }

    @GetMapping("/reports/{id}")
    @RequirePerm("quality:report:view")
    public Result<Object> report(@PathVariable long id) {
        Map<String, Object> r = db.one("SELECT * FROM qa_report WHERE id=?", id);
        if (r == null) throw new BizException(ErrorCode.NOT_FOUND);
        Map<String, Object> out = new LinkedHashMap<>(r);
        out.put("context", JsonUtil.parse(String.valueOf(r.get("context"))));
        out.put("files", db.list("SELECT * FROM rpt_file WHERE report_id=?", id));
        return Result.ok(out);
    }

    @PostMapping("/reports")
    @RequirePerm("quality:report:view")
    public Result<Object> generateReport(@RequestBody(required = false) Map<String, Object> b) {
        String scope = b == null ? null : Req.str(b.get("scope"));
        String period = b == null ? null : Req.str(b.get("period"));
        return Result.ok(reportService.generateQualityReport(scope, period, AuthContext.username()));
    }

    @PostMapping("/reports/{id}/export")
    @RequirePerm("quality:report:export")
    public Result<Object> exportReport(@PathVariable long id, @RequestBody(required = false) Map<String, Object> b) {
        String format = b == null ? "html" : String.valueOf(b.getOrDefault("format", "html"));
        return Result.ok(reportService.export(id, format));
    }

    // ================= 评分 =================
    @GetMapping("/scores")
    @RequirePerm("quality:score:view")
    public Result<Object> scores(@RequestParam(required = false) String tableName,
                                 @RequestParam(defaultValue = "14") int days) {
        return com.dgp.common.TtlCache.get("quality:scores:" + tableName + ":" + days, 8_000,
                () -> computeScores(tableName, days));
    }

    private Result<Object> computeScores(String tableName, int days) {
        List<Map<String, Object>> trend = db.list(
                "SELECT stat_date, ROUND(AVG(total_score),2) AS score FROM qa_score WHERE stat_date>=? GROUP BY stat_date ORDER BY stat_date",
                LocalDate.now().minusDays(days));
        List<Map<String, Object>> tables = db.list(
                "SELECT name,cn_name,quality_score,row_count,owner,sensitive_level FROM md_table WHERE quality_score>0 ORDER BY quality_score ASC");
        List<Map<String, Object>> dims = db.list(
                "SELECT dimension, ROUND(AVG(last_pass_rate),2) AS pass_rate, COUNT(*) AS rules FROM qa_rule GROUP BY dimension");
        Object overall = db.scalar("SELECT ROUND(AVG(quality_score),2) FROM md_table WHERE quality_score>0");
        List<Map<String, Object>> detail = tableName == null ? List.of()
                : db.list("SELECT * FROM qa_score WHERE object_name=? ORDER BY stat_date DESC LIMIT 30", tableName);
        return Result.ok(M.of("overallScore", overall, "level", ReportService.levelOf(overall == null ? 0 : ((Number) overall).doubleValue()),
                "weights", engine.dimensionWeights(), "dimensions", dims, "trend", trend, "tables", tables, "detail", detail));
    }

    @PutMapping("/scores/weights")
    @RequirePerm("quality:score:config")
    public Result<Object> weights(@RequestBody Map<String, Object> b) {
        Map<String, Object> w = new LinkedHashMap<>();
        double sum = 0;
        for (String d : new String[]{"COMPLETENESS", "UNIQUENESS", "ACCURACY", "VALIDITY", "CONSISTENCY", "TIMELINESS"}) {
            Double v = Db.num(b.get(d));
            if (v == null) v = 0d;
            if (v < 0) throw new BizException(ErrorCode.PARAM_INVALID, "维度权重不能为负数：" + d);
            w.put(d, v);
            sum += v;
        }
        if (Math.abs(sum - 1.0) > 0.001) {
            throw new BizException(ErrorCode.PARAM_INVALID, "维度权重之和必须为 1.0，当前为 " + sum);
        }
        String json = JsonUtil.toJson(w);
        if (db.one("SELECT config_key FROM sys_config WHERE config_key='quality.dimension.weight'") == null) {
            db.insert("INSERT INTO sys_config(config_key,config_value,description,update_time) VALUES(?,?,?,?)",
                    "quality.dimension.weight", json, "质量维度权重", LocalDateTime.now());
        } else {
            db.update("UPDATE sys_config SET config_value=?,update_time=? WHERE config_key='quality.dimension.weight'",
                    json, LocalDateTime.now());
        }
        return Result.ok(M.of("weights", w));
    }

    @GetMapping("/metrics/rule-coverage")
    @RequirePerm("quality:score:view")
    public Result<Object> coverage() {
        // 修正：原实现分子统计"全量规则字段"、分母只统计"核心层字段"，口径不一致且可能 >100%
        long coreColumns = db.count("SELECT COUNT(*) FROM md_column c JOIN md_table t ON t.id=c.table_id "
                + "WHERE t.layer IN ('DWD','DWS','ADS')");
        long ruleFields = db.count("SELECT COUNT(DISTINCT CONCAT(r.table_name,'.',r.column_name)) FROM qa_rule r "
                + "JOIN md_table t ON t.name=r.table_name WHERE r.column_name IS NOT NULL AND t.layer IN ('DWD','DWS','ADS')");
        long tableWithRules = db.count("SELECT COUNT(DISTINCT table_name) FROM qa_rule");
        long totalTables = db.count("SELECT COUNT(*) FROM md_table");
        double fieldRate = coreColumns == 0 ? 0 : Math.min(100d, ruleFields * 100d / coreColumns);
        return Result.ok(M.of("ruleFields", ruleFields, "coreColumns", coreColumns,
                "fieldCoverage", BigDecimal.valueOf(fieldRate).setScale(2, RoundingMode.HALF_UP),
                "tableCoverage", BigDecimal.valueOf(tableWithRules * 100d / Math.max(1, totalTables)).setScale(2, RoundingMode.HALF_UP),
                "tablesWithRules", tableWithRules, "totalTables", totalTables, "target", 80,
                "byTable", db.list("SELECT table_name, COUNT(*) AS rule_cnt, COUNT(DISTINCT column_name) AS field_cnt FROM qa_rule GROUP BY table_name ORDER BY rule_cnt DESC")));
    }

    // ================= 问题闭环 =================
    @GetMapping("/issues")
    @RequirePerm("quality:issue:view")
    public Result<Object> issues(@RequestParam(required = false) String status,
                                 @RequestParam(required = false) String severity,
                                 @RequestParam(required = false) String assignee,
                                 @RequestParam(required = false) String tableName,
                                 @RequestParam(defaultValue = "1") int pageNum,
                                 @RequestParam(defaultValue = "20") int pageSize) {
        StringBuilder where = new StringBuilder(" WHERE 1=1");
        List<Object> args = new ArrayList<>();
        if (!Req.blank(status)) {
            where.append(" AND status=?");
            args.add(status);
        }
        if (!Req.blank(severity)) {
            where.append(" AND severity=?");
            args.add(severity);
        }
        if (!Req.blank(assignee)) {
            where.append(" AND assignee=?");
            args.add(assignee);
        }
        if (!Req.blank(tableName)) {
            where.append(" AND table_name=?");
            args.add(tableName);
        }
        return Result.ok(db.page("SELECT * FROM qa_issue" + where, "id DESC", pageNum, pageSize, args.toArray()).toMap());
    }

    @GetMapping("/issues/{id}")
    @RequirePerm("quality:issue:view")
    public Result<Object> issue(@PathVariable long id) {
        Map<String, Object> i = db.one("SELECT * FROM qa_issue WHERE id=?", id);
        if (i == null) throw new BizException(ErrorCode.NOT_FOUND);
        Map<String, Object> out = new LinkedHashMap<>(i);
        out.put("flows", db.list("SELECT * FROM qa_issue_flow WHERE issue_id=? ORDER BY id", id));
        out.put("rule", db.one("SELECT * FROM qa_rule WHERE id=?", i.get("ruleId")));
        return Result.ok(out);
    }

    @PostMapping("/issues/{id}/dispatch")
    @RequirePerm("quality:issue:dispatch")
    public Result<Object> dispatch(@PathVariable long id, @RequestBody Map<String, Object> b) {
        Map<String, Object> i = db.one("SELECT * FROM qa_issue WHERE id=?", id);
        if (i == null) throw new BizException(ErrorCode.NOT_FOUND);
        assertTransition(String.valueOf(i.get("status")), "DISPATCHED");
        Object assignee = b.get("assignee");
        if (assignee == null) throw new BizException(ErrorCode.PARAM_INVALID, "必须指定责任人");
        int hours = Req.toInt(b.getOrDefault("dueHours", 72), 72);
        db.update("UPDATE qa_issue SET status='DISPATCHED',assignee=?,due_time=? WHERE id=?",
                assignee, LocalDateTime.now().plusHours(hours), id);
        flow(id, String.valueOf(i.get("status")), "DISPATCHED", "派单给 " + assignee);
        return Result.ok(M.of("id", id, "status", "DISPATCHED", "assignee", assignee));
    }

    @PostMapping("/issues/{id}/handle")
    @RequirePerm("quality:issue:handle")
    public Result<Object> handle(@PathVariable long id, @RequestBody Map<String, Object> b) {
        Map<String, Object> i = db.one("SELECT * FROM qa_issue WHERE id=?", id);
        if (i == null) throw new BizException(ErrorCode.NOT_FOUND);
        String current = String.valueOf(i.get("status"));
        String result = Req.str(b.get("handleResult"));
        if (result == null || result.isBlank()) throw new BizException(ErrorCode.PARAM_INVALID, "必须填写处理结果");
        // 派单后直接处理：先补记 DISPATCHED→PROCESSING 迁移，再进入待复核
        if ("OVERDUE".equals(current) || "DISPATCHED".equals(current)) {
            assertTransition(current, "PROCESSING");
            flow(id, current, "PROCESSING", "开始处理");
            current = "PROCESSING";
        }
        assertTransition(current, "REVIEWING");
        db.update("UPDATE qa_issue SET status='REVIEWING',handle_result=? WHERE id=?", result, id);
        flow(id, current, "REVIEWING", result);
        return Result.ok(M.of("id", id, "status", "REVIEWING"));
    }

    @PostMapping("/issues/{id}/review")
    @RequirePerm("quality:issue:review")
    public Result<Object> review(@PathVariable long id, @RequestBody Map<String, Object> b) {
        Map<String, Object> i = db.one("SELECT * FROM qa_issue WHERE id=?", id);
        if (i == null) throw new BizException(ErrorCode.NOT_FOUND);
        boolean pass = !"REJECTED".equals(String.valueOf(b.getOrDefault("result", "PASS")));
        if (pass) {
            if (AuthContext.username().equals(String.valueOf(i.get("assignee")))) {
                throw new BizException(ErrorCode.FORBIDDEN, "复核人不能与责任人相同");
            }
            assertTransition(String.valueOf(i.get("status")), "CLOSED");
            db.update("UPDATE qa_issue SET status='CLOSED',close_time=?,review_result=? WHERE id=?",
                    LocalDateTime.now(), b.getOrDefault("comment", "复核通过"), id);
            flow(id, String.valueOf(i.get("status")), "CLOSED", "复核通过");
        } else {
            assertTransition(String.valueOf(i.get("status")), "PROCESSING");
            db.update("UPDATE qa_issue SET status='PROCESSING',review_result=? WHERE id=?",
                    b.getOrDefault("comment", "复核驳回"), id);
            flow(id, String.valueOf(i.get("status")), "PROCESSING", "复核驳回：" + b.getOrDefault("comment", ""));
        }
        return Result.ok(M.of("id", id, "status", pass ? "CLOSED" : "PROCESSING"));
    }

    @PostMapping("/issues/{id}/delay")
    @RequirePerm("quality:issue:handle")
    public Result<Object> delay(@PathVariable long id, @RequestBody Map<String, Object> b) {
        Map<String, Object> i = db.one("SELECT * FROM qa_issue WHERE id=?", id);
        if (i == null) throw new BizException(ErrorCode.NOT_FOUND);
        String status = String.valueOf(i.get("status"));
        if (!Set.of("DISPATCHED", "PROCESSING", "OVERDUE").contains(status)) {
            throw new BizException(ErrorCode.ISSUE_STATE_ILLEGAL, "当前状态不允许延期：" + status);
        }
        int hours = Math.min(720, Math.max(1, Req.toInt(b.getOrDefault("hours", 24), 24)));
        db.update("UPDATE qa_issue SET due_time=?,status=CASE WHEN status='OVERDUE' THEN 'PROCESSING' ELSE status END WHERE id=?",
                LocalDateTime.now().plusHours(hours), id);
        flow(id, status, "OVERDUE".equals(status) ? "PROCESSING" : status, "延期 " + hours + " 小时");
        return Result.ok(M.of("id", id, "dueTime", LocalDateTime.now().plusHours(hours)));
    }

    private void flow(long issueId, String from, String to, String comment) {
        db.insert("INSERT INTO qa_issue_flow(issue_id,from_status,to_status,operator,comment,create_time) VALUES(?,?,?,?,?,?)",
                issueId, from, to, AuthContext.username(), comment, LocalDateTime.now());
    }

    private void assertTransition(String from, String to) {
        if (!TRANSITIONS.getOrDefault(from, Set.of()).contains(to)) {
            throw new BizException(ErrorCode.ISSUE_STATE_ILLEGAL, "问题单不允许从 " + from + " 迁移到 " + to);
        }
    }

    @GetMapping("/issues/metrics")
    @RequirePerm("quality:issue:view")
    public Result<Object> issueMetrics() {
        long closed = db.count("SELECT COUNT(*) FROM qa_issue WHERE status='CLOSED'");
        long open = db.count("SELECT COUNT(*) FROM qa_issue WHERE status NOT IN ('CLOSED','IGNORED')");
        long ignored = db.count("SELECT COUNT(*) FROM qa_issue WHERE status='IGNORED'");
        long total = db.count("SELECT COUNT(*) FROM qa_issue");
        long overdue = db.count("SELECT COUNT(*) FROM qa_issue WHERE status='OVERDUE'");
        long recurred = db.count("SELECT COUNT(*) FROM qa_issue WHERE recur_cnt>0");
        double rate = total == 0 ? 0 : closed * 100d / Math.max(1, closed + open + ignored);
        // 修正：DATEDIFF('HOUR',...) 是 H2 专有函数，切换 PostgreSQL 会直接报错；改为 Java 侧计算
        Double avgHours = averageHandleHours(null);
        return Result.ok(M.of("total", total, "closed", closed, "open", open, "ignored", ignored, "overdue", overdue,
                "recurred", recurred, "closedRate", BigDecimal.valueOf(rate).setScale(2, RoundingMode.HALF_UP),
                "target", 70, "avgHandleHours", avgHours == null ? 0 : Math.round(avgHours),
                "overdueRate", BigDecimal.valueOf(overdue * 100d / Math.max(1, total)).setScale(2, RoundingMode.HALF_UP),
                "recurRate", BigDecimal.valueOf(recurred * 100d / Math.max(1, closed)).setScale(2, RoundingMode.HALF_UP),
                "byStatus", db.list("SELECT status, COUNT(*) AS cnt FROM qa_issue GROUP BY status"),
                "bySeverity", db.list("SELECT severity, COUNT(*) AS cnt FROM qa_issue GROUP BY severity"),
                "trend", db.list("SELECT stat_date,new_cnt,closed_cnt,closed_rate FROM qa_issue_metric_daily ORDER BY stat_date DESC LIMIT 14")));
    }

    /** 平均处理时长（小时）；assignee 非空时只统计该责任人。数据库无关实现。 */
    private Double averageHandleHours(String assignee) {
        String sql = "SELECT discover_time, close_time FROM qa_issue WHERE status='CLOSED' AND close_time IS NOT NULL"
                + (assignee == null ? "" : " AND assignee=?");
        List<Map<String, Object>> rows = assignee == null ? db.list(sql) : db.list(sql, assignee);
        if (rows.isEmpty()) return null;
        double sum = 0;
        int n = 0;
        for (Map<String, Object> r : rows) {
            Object a = r.get("discoverTime");
            Object b = r.get("closeTime");
            if (a instanceof java.time.LocalDateTime at && b instanceof java.time.LocalDateTime bt) {
                sum += java.time.Duration.between(at, bt).toMinutes() / 60d;
                n++;
            }
        }
        return n == 0 ? null : sum / n;
    }

    @GetMapping("/issues/metrics/by-assignee")
    @RequirePerm("quality:issue:view")
    public Result<Object> byAssignee() {
        List<Map<String, Object>> list = db.list(
                "SELECT assignee, COUNT(*) AS total, SUM(CASE WHEN status='CLOSED' THEN 1 ELSE 0 END) AS closed, "
                        + "SUM(CASE WHEN status='OVERDUE' THEN 1 ELSE 0 END) AS overdue "
                        + "FROM qa_issue GROUP BY assignee ORDER BY total DESC");
        for (Map<String, Object> r : list) {
            long t = ((Number) r.get("total")).longValue();
            long c = ((Number) r.get("closed")).longValue();
            r.put("closedRate", t == 0 ? 0 : BigDecimal.valueOf(c * 100d / t).setScale(2, RoundingMode.HALF_UP));
            Double avg = averageHandleHours(r.get("assignee") == null ? null : String.valueOf(r.get("assignee")));
            r.put("avgHours", avg == null ? 0 : Math.round(avg * 10) / 10d);
        }
        return Result.ok(M.of("records", list));
    }

    @GetMapping("/stats/overview")
    @RequirePerm("quality:overview:view")
    public Result<Object> stats() {
        return com.dgp.common.TtlCache.get("quality:stats:" + com.dgp.security.AuthContext.tenantId(), 10_000,
                this::computeStats);
    }

    private Result<Object> computeStats() {
        return Result.ok(M.of(
                "ruleCount", db.count("SELECT COUNT(*) FROM qa_rule"),
                "enabledRule", db.count("SELECT COUNT(*) FROM qa_rule WHERE status='ENABLED'"),
                "checkCount", db.count("SELECT COUNT(*) FROM qa_check"),
                "issueOpen", db.count("SELECT COUNT(*) FROM qa_issue WHERE status NOT IN ('CLOSED','IGNORED')"),
                "issueClosed", db.count("SELECT COUNT(*) FROM qa_issue WHERE status='CLOSED'"),
                "avgScore", db.scalar("SELECT ROUND(AVG(quality_score),2) FROM md_table WHERE quality_score>0"),
                "byDimension", db.list("SELECT dimension, COUNT(*) AS cnt, ROUND(AVG(last_pass_rate),2) AS pass_rate FROM qa_rule GROUP BY dimension"),
                "recentChecks", db.list("SELECT * FROM qa_check_instance ORDER BY id DESC LIMIT 5")));
    }
}
