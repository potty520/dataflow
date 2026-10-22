package com.dgp.quality;

import com.dgp.common.BizException;
import com.dgp.common.Db;
import com.dgp.common.ErrorCode;
import com.dgp.common.M;
import com.dgp.common.Req;
import com.dgp.common.Result;
import com.dgp.security.AuthContext;
import com.dgp.security.RequirePerm;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 流式（微批）数据质检：流源、规则、窗口结果、告警。
 *
 * <p>与批式稽核的区别：规则是"不合格条件"布尔谓词，按水位窗口对新增数据实时计算通过率，
 * 超容差即产生告警，适合贴源层（ODS）的准实时校验。
 */
@RestController
@RequestMapping("/api/v1/quality/stream")
public class StreamQualityController {

    private final Db db;
    private final StreamQualityService service;

    public StreamQualityController(Db db, StreamQualityService service) {
        this.db = db;
        this.service = service;
    }

    // ------------------------------------------------------------ 概览/健康
    @GetMapping("/overview")
    @RequirePerm("quality:stream:view")
    public Result<Object> overview() {
        return Result.ok(service.metrics());
    }

    @GetMapping("/health")
    @RequirePerm("quality:stream:view")
    public Result<Object> health() {
        return Result.ok(M.of("sources", service.sourceHealth()));
    }

    /** 可供选择的源表（元数据表清单，供前端下拉）。 */
    @GetMapping("/tables")
    @RequirePerm("quality:stream:view")
    public Result<Object> tables() {
        return Result.ok(M.of("records",
                db.list("SELECT name, cn_name, layer FROM md_table ORDER BY name")));
    }

    @GetMapping("/columns")
    @RequirePerm("quality:stream:view")
    public Result<Object> columns(@RequestParam String table) {
        String t = Db.identifier(table);
        return Result.ok(M.of("records", db.list(
                "SELECT c.name, c.data_type FROM md_column c JOIN md_table t ON t.id=c.table_id "
                        + "WHERE t.name=? ORDER BY c.id", t)));
    }

    // ------------------------------------------------------------ 流源
    @GetMapping("/sources")
    @RequirePerm("quality:stream:view")
    public Result<Object> sources(@RequestParam(required = false) String keyword,
                                  @RequestParam(defaultValue = "1") int pageNum,
                                  @RequestParam(defaultValue = "20") int pageSize) {
        String base = "SELECT * FROM qa_stream_source WHERE 1=1";
        if (keyword != null && !keyword.isBlank()) {
            return Result.ok(db.page(base + " AND (name LIKE ? OR code LIKE ? OR table_name LIKE ?)",
                    "id DESC", Req.pageNum(pageNum), Req.pageSize(pageSize),
                    "%" + keyword + "%", "%" + keyword + "%", "%" + keyword + "%").toMap());
        }
        return Result.ok(db.page(base, "id DESC", Req.pageNum(pageNum), Req.pageSize(pageSize)).toMap());
    }

    @PostMapping("/sources")
    @RequirePerm("quality:stream:create")
    public Result<Object> createSource(@RequestBody Map<String, Object> b) {
        String code = Req.str(b.get("code"));
        if (Req.blank(code)) throw new BizException(ErrorCode.PARAM_INVALID, "编码不能为空");
        if (db.one("SELECT id FROM qa_stream_source WHERE code=?", code) != null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "流源编码已存在：" + code);
        }
        String table = Db.identifier(Req.str(b.get("tableName")));
        String wm = Db.identifier(Req.str(b.get("watermarkCol")));
        service.assertSourceReadable(table, wm);
        String wmType = "NUMBER".equalsIgnoreCase(Req.str(b.get("watermarkType"))) ? "NUMBER" : "TIMESTAMP";
        int window = Math.max(5, Req.toInt(b.get("windowSeconds"), 60));
        long id = db.insert("INSERT INTO qa_stream_source(tenant_id,code,name,table_name,watermark_col,"
                        + "watermark_type,window_seconds,batch_limit,status,owner,create_time) "
                        + "VALUES(?,?,?,?,?,?,?,?,?,?,?)",
                1L, code, Req.str(b.getOrDefault("name", code)), table, wm, wmType, window,
                Req.toInt(b.get("batchLimit"), 5000),
                Req.str(b.getOrDefault("status", "ENABLED")), AuthContext.username(), LocalDateTime.now());
        return Result.ok(M.of("id", id));
    }

    @PutMapping("/sources/{id}")
    @RequirePerm("quality:stream:edit")
    public Result<Object> updateSource(@PathVariable long id, @RequestBody Map<String, Object> b) {
        Map<String, Object> before = db.one("SELECT * FROM qa_stream_source WHERE id=?", id);
        if (before == null) throw new BizException(ErrorCode.NOT_FOUND, "流源不存在");
        Object table = pick(b, before, "tableName");
        Object wm = pick(b, before, "watermarkCol");
        service.assertSourceReadable(String.valueOf(table), String.valueOf(wm));
        db.update("UPDATE qa_stream_source SET name=?,table_name=?,watermark_col=?,watermark_type=?,"
                        + "window_seconds=?,batch_limit=?,status=? WHERE id=?",
                pick(b, before, "name"), String.valueOf(table), String.valueOf(wm),
                pick(b, before, "watermarkType"),
                Req.toInt(pick(b, before, "windowSeconds"), 60), Req.toInt(pick(b, before, "batchLimit"), 5000),
                pick(b, before, "status"), id);
        return Result.ok(M.of("id", id));
    }

    @DeleteMapping("/sources/{id}")
    @RequirePerm("quality:stream:edit")
    public Result<Object> deleteSource(@PathVariable long id) {
        db.update("DELETE FROM qa_stream_result WHERE source_id=?", id);
        db.update("DELETE FROM qa_stream_alert WHERE source_id=?", id);
        db.update("DELETE FROM qa_stream_rule WHERE source_id=?", id);
        db.update("DELETE FROM qa_stream_source WHERE id=?", id);
        return Result.ok(M.of("deleted", true));
    }

    /** 手工执行一个源（立即跑一个窗口）。 */
    @PostMapping("/sources/{id}/run")
    @RequirePerm("quality:stream:exec")
    public Result<Object> runSource(@PathVariable long id) {
        return Result.ok(service.runSource(id, "MANUAL"));
    }

    /** 重置水位（回到"从未消费"状态，下次按窗口长度回溯取数；用于补数/联调）。 */
    @PostMapping("/sources/{id}/reset-watermark")
    @RequirePerm("quality:stream:exec")
    public Result<Object> resetWatermark(@PathVariable long id) {
        if (db.one("SELECT id FROM qa_stream_source WHERE id=?", id) == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "流源不存在");
        }
        db.update("UPDATE qa_stream_source SET last_watermark=NULL WHERE id=?", id);
        return Result.ok(M.of("id", id, "lastWatermark", null));
    }
    @PostMapping("/sources/run-all")
    @RequirePerm("quality:stream:exec")
    public Result<Object> runAll() {
        return Result.ok(service.runAll());
    }

    // ------------------------------------------------------------ 规则
    @GetMapping("/rules")
    @RequirePerm("quality:stream:view")
    public Result<Object> rules(@RequestParam(required = false) Long sourceId,
                                @RequestParam(required = false) String status,
                                @RequestParam(defaultValue = "1") int pageNum,
                                @RequestParam(defaultValue = "50") int pageSize) {
        StringBuilder where = new StringBuilder(" WHERE 1=1");
        List<Object> args = new java.util.ArrayList<>();
        if (sourceId != null) {
            where.append(" AND r.source_id=?");
            args.add(sourceId);
        }
        if (status != null && !status.isBlank()) {
            where.append(" AND r.status=?");
            args.add(status);
        }
        String base = "SELECT r.*, s.name AS source_name, s.code AS source_code FROM qa_stream_rule r "
                + "LEFT JOIN qa_stream_source s ON s.id=r.source_id" + where;
        return Result.ok(db.page(base, "r.id DESC", Req.pageNum(pageNum), Req.pageSize(pageSize),
                args.toArray()).toMap());
    }

    @PostMapping("/rules")
    @RequirePerm("quality:stream:create")
    public Result<Object> createRule(@RequestBody Map<String, Object> b) {
        long sourceId = Db.longVal(b.get("sourceId"), 0L);
        if (db.one("SELECT id FROM qa_stream_source WHERE id=?", sourceId) == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "流源不存在：" + sourceId);
        }
        String code = Req.str(b.get("code"));
        if (Req.blank(code)) throw new BizException(ErrorCode.PARAM_INVALID, "规则编码不能为空");
        if (db.one("SELECT id FROM qa_stream_rule WHERE code=?", code) != null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "规则编码已存在：" + code);
        }
        String expr = Req.str(b.get("expression"));
        StreamQualityService.assertSafePredicate(expr);
        double tolerance = Db.num(b.getOrDefault("tolerance", 0.01)) == null ? 0.01 : Db.num(b.get("tolerance"));
        if (tolerance < 0 || tolerance > 1) throw new BizException(ErrorCode.PARAM_INVALID, "容差须在 0~1 之间");
        long id = db.insert("INSERT INTO qa_stream_rule(source_id,code,name,dimension,expression,tolerance,"
                        + "severity,status,create_time) VALUES(?,?,?,?,?,?,?,?,?)",
                sourceId, code, Req.str(b.getOrDefault("name", code)),
                Req.str(b.getOrDefault("dimension", "VALIDITY")), expr, java.math.BigDecimal.valueOf(tolerance),
                Req.str(b.getOrDefault("severity", "MAJOR")), Req.str(b.getOrDefault("status", "ENABLED")),
                LocalDateTime.now());
        return Result.ok(M.of("id", id));
    }

    @PutMapping("/rules/{id}")
    @RequirePerm("quality:stream:edit")
    public Result<Object> updateRule(@PathVariable long id, @RequestBody Map<String, Object> b) {
        Map<String, Object> before = db.one("SELECT * FROM qa_stream_rule WHERE id=?", id);
        if (before == null) throw new BizException(ErrorCode.NOT_FOUND, "规则不存在");
        if (b.get("expression") != null) StreamQualityService.assertSafePredicate(Req.str(b.get("expression")));
        db.update("UPDATE qa_stream_rule SET name=?,dimension=?,expression=?,tolerance=?,severity=?,status=? WHERE id=?",
                pick(b, before, "name"), pick(b, before, "dimension"), pick(b, before, "expression"),
                java.math.BigDecimal.valueOf(Db.num(pick(b, before, "tolerance")) == null
                        ? 0.01 : Db.num(pick(b, before, "tolerance"))),
                pick(b, before, "severity"), pick(b, before, "status"), id);
        return Result.ok(M.of("id", id));
    }

    @DeleteMapping("/rules/{id}")
    @RequirePerm("quality:stream:edit")
    public Result<Object> deleteRule(@PathVariable long id) {
        db.update("DELETE FROM qa_stream_result WHERE rule_id=?", id);
        db.update("DELETE FROM qa_stream_alert WHERE rule_id=?", id);
        db.update("DELETE FROM qa_stream_rule WHERE id=?", id);
        return Result.ok(M.of("deleted", true));
    }

    // ------------------------------------------------------------ 结果与告警
    @GetMapping("/results")
    @RequirePerm("quality:stream:view")
    public Result<Object> results(@RequestParam(required = false) Long sourceId,
                                  @RequestParam(required = false) Long ruleId,
                                  @RequestParam(required = false) String status,
                                  @RequestParam(defaultValue = "1") int pageNum,
                                  @RequestParam(defaultValue = "20") int pageSize) {
        return Result.ok(service.results(sourceId, ruleId, status, Req.pageNum(pageNum), Req.pageSize(pageSize)));
    }

    @GetMapping("/alerts")
    @RequirePerm("quality:stream:view")
    public Result<Object> alerts(@RequestParam(required = false) String status,
                                 @RequestParam(defaultValue = "1") int pageNum,
                                 @RequestParam(defaultValue = "20") int pageSize) {
        return Result.ok(service.alerts(status, Req.pageNum(pageNum), Req.pageSize(pageSize)));
    }

    @PostMapping("/alerts/{id}/handle")
    @RequirePerm("quality:stream:handle")
    public Result<Object> handleAlert(@PathVariable long id) {
        return Result.ok(service.handleAlert(id));
    }

    private static Object pick(Map<String, Object> body, Map<String, Object> before, String key) {
        return body.containsKey(key) ? body.get(key) : before.get(key);
    }
}
