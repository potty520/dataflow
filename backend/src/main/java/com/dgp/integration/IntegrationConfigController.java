package com.dgp.integration;

import com.dgp.common.BizException;
import com.dgp.common.Db;
import com.dgp.common.ErrorCode;
import com.dgp.common.M;
import com.dgp.common.Result;
import com.dgp.security.AuthContext;
import com.dgp.security.RequirePerm;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** BF-12：集成配置（函数库 / 增量字段 / 批次）。 */
@RestController
@RequestMapping("/api/v1/integration/config")
public class IntegrationConfigController {

    private final Db db;

    public IntegrationConfigController(Db db) {
        this.db = db;
    }

    // ---------------- 函数库 ----------------
    @GetMapping("/functions")
    @RequirePerm("integration:config:view")
    public Result<Object> functions(@RequestParam(required = false) String keyword) {
        String sql = "SELECT * FROM int_function";
        if (keyword == null || keyword.isBlank()) {
            return Result.ok(M.of("records", db.list(sql + " ORDER BY id")));
        }
        return Result.ok(M.of("records", db.list(sql + " WHERE code LIKE ? OR name LIKE ? ORDER BY id",
                "%" + keyword.trim() + "%", "%" + keyword.trim() + "%")));
    }

    @PostMapping("/functions")
    @RequirePerm("integration:config:edit")
    public Result<Object> createFunction(@RequestBody Map<String, Object> b) {
        String code = Db.str(b.get("code"));
        String expr = Db.str(b.get("expression"));
        if (code == null || code.isBlank() || expr == null || expr.isBlank()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "编码与表达式不能为空");
        }
        if (db.one("SELECT id FROM int_function WHERE code=?", code) != null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "函数编码已存在");
        }
        long id = db.insert("INSERT INTO int_function(tenant_id,code,name,expression,category,status,remark,create_time,update_time) "
                        + "VALUES(?,?,?,?,?,?,?,?,?)",
                AuthContext.tenantId(), code, b.get("name"), expr, b.get("category"),
                b.getOrDefault("status", "ENABLED"), b.get("remark"), LocalDateTime.now(), LocalDateTime.now());
        return Result.ok(M.of("id", id));
    }

    @PutMapping("/functions/{id}")
    @RequirePerm("integration:config:edit")
    public Result<Object> updateFunction(@PathVariable long id, @RequestBody Map<String, Object> b) {
        Map<String, Object> r = db.one("SELECT * FROM int_function WHERE id=?", id);
        if (r == null) throw new BizException(ErrorCode.NOT_FOUND, "函数不存在");
        db.update("UPDATE int_function SET name=?,expression=?,category=?,status=?,remark=?,update_time=? WHERE id=?",
                b.containsKey("name") ? b.get("name") : r.get("name"),
                b.containsKey("expression") ? b.get("expression") : r.get("expression"),
                b.containsKey("category") ? b.get("category") : r.get("category"),
                b.containsKey("status") ? b.get("status") : r.get("status"),
                b.containsKey("remark") ? b.get("remark") : r.get("remark"), LocalDateTime.now(), id);
        return Result.ok(M.of("id", id));
    }

    @DeleteMapping("/functions/{id}")
    @RequirePerm("integration:config:edit")
    public Result<Object> deleteFunction(@PathVariable long id) {
        db.update("DELETE FROM int_function WHERE id=?", id);
        return Result.ok(M.of("deleted", true));
    }

    /** 试算：只允许单表达式 SELECT，禁止多语句/写操作。 */
    @PostMapping("/functions/test")
    @RequirePerm("integration:config:view")
    public Result<Object> testFunction(@RequestBody Map<String, Object> b) {
        String expr = Db.str(b.get("expression"));
        if (expr == null || expr.isBlank()) throw new BizException(ErrorCode.PARAM_INVALID, "表达式不能为空");
        String lower = expr.toLowerCase(Locale.ROOT);
        if (lower.contains(";") || lower.contains("--") || lower.contains("/*")
                || lower.contains("insert") || lower.contains("update") || lower.contains("delete")
                || lower.contains("drop") || lower.contains("alter") || lower.contains("truncate")) {
            throw new BizException(ErrorCode.PARAM_INVALID, "表达式包含非法关键字");
        }
        try {
            Object v = db.scalar("SELECT (" + expr + ")");
            return Result.ok(M.of("value", v, "expression", expr));
        } catch (Exception e) {
            throw new BizException(ErrorCode.SQL_INVALID, "表达式执行失败：" + e.getMessage());
        }
    }

    // ---------------- 增量字段 ----------------
    @GetMapping("/incr")
    @RequirePerm("integration:config:view")
    public Result<Object> incr(@RequestParam(required = false) Long jobId) {
        return Result.ok(M.of("records", jobId == null
                ? db.list("SELECT * FROM int_incr_config ORDER BY id")
                : db.list("SELECT * FROM int_incr_config WHERE job_id=?", jobId)));
    }

    @PostMapping("/incr")
    @RequirePerm("integration:config:edit")
    public Result<Object> saveIncr(@RequestBody Map<String, Object> b) {
        Long jobId = Db.longVal(b.get("jobId"), 0L);
        String field = Db.str(b.get("fieldName"));
        if (jobId <= 0 || field == null || field.isBlank()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "作业与增量字段不能为空");
        }
        Map<String, Object> exist = db.one("SELECT * FROM int_incr_config WHERE job_id=?", jobId);
        if (exist == null) {
            long id = db.insert("INSERT INTO int_incr_config(tenant_id,job_id,field_name,field_type,start_value,last_value,"
                            + "status,remark,create_time,update_time) VALUES(?,?,?,?,?,?,?,?,?,?)",
                    AuthContext.tenantId(), jobId, field, b.getOrDefault("fieldType", "TIMESTAMP"),
                    b.get("startValue"), b.get("startValue"), b.getOrDefault("status", "ENABLED"),
                    b.get("remark"), LocalDateTime.now(), LocalDateTime.now());
            return Result.ok(M.of("id", id));
        }
        db.update("UPDATE int_incr_config SET field_name=?,field_type=?,start_value=?,last_value=?,status=?,remark=?,"
                        + "update_time=? WHERE job_id=?",
                field, b.containsKey("fieldType") ? b.get("fieldType") : exist.get("fieldType"),
                b.containsKey("startValue") ? b.get("startValue") : exist.get("startValue"),
                b.containsKey("lastValue") ? b.get("lastValue") : exist.get("lastValue"),
                b.containsKey("status") ? b.get("status") : exist.get("status"),
                b.containsKey("remark") ? b.get("remark") : exist.get("remark"),
                LocalDateTime.now(), jobId);
        return Result.ok(M.of("id", exist.get("id")));
    }

    @DeleteMapping("/incr/{id}")
    @RequirePerm("integration:config:edit")
    public Result<Object> deleteIncr(@PathVariable long id) {
        db.update("DELETE FROM int_incr_config WHERE id=?", id);
        return Result.ok(M.of("deleted", true));
    }

    // ---------------- 批次配置 ----------------
    @GetMapping("/batch")
    @RequirePerm("integration:config:view")
    public Result<Object> batch(@RequestParam(required = false) Long jobId) {
        return Result.ok(M.of("records", jobId == null
                ? db.list("SELECT * FROM int_batch_config ORDER BY id")
                : db.list("SELECT * FROM int_batch_config WHERE job_id=?", jobId)));
    }

    @PostMapping("/batch")
    @RequirePerm("integration:config:edit")
    public Result<Object> saveBatch(@RequestBody Map<String, Object> b) {
        Long jobId = Db.longVal(b.get("jobId"), 0L);
        if (jobId <= 0) throw new BizException(ErrorCode.PARAM_INVALID, "作业不能为空");
        Map<String, Object> exist = db.one("SELECT * FROM int_batch_config WHERE job_id=?", jobId);
        if (exist == null) {
            long id = db.insert("INSERT INTO int_batch_config(tenant_id,job_id,batch_size,concurrency,retry_count,status,"
                            + "remark,create_time,update_time) VALUES(?,?,?,?,?,?,?,?,?)",
                    AuthContext.tenantId(), jobId, b.getOrDefault("batchSize", 10000),
                    b.getOrDefault("concurrency", 2), b.getOrDefault("retryCount", 3),
                    b.getOrDefault("status", "ENABLED"), b.get("remark"), LocalDateTime.now(), LocalDateTime.now());
            return Result.ok(M.of("id", id));
        }
        db.update("UPDATE int_batch_config SET batch_size=?,concurrency=?,retry_count=?,status=?,remark=?,update_time=? "
                        + "WHERE job_id=?",
                b.containsKey("batchSize") ? b.get("batchSize") : exist.get("batchSize"),
                b.containsKey("concurrency") ? b.get("concurrency") : exist.get("concurrency"),
                b.containsKey("retryCount") ? b.get("retryCount") : exist.get("retryCount"),
                b.containsKey("status") ? b.get("status") : exist.get("status"),
                b.containsKey("remark") ? b.get("remark") : exist.get("remark"),
                LocalDateTime.now(), jobId);
        return Result.ok(M.of("id", exist.get("id")));
    }

    @DeleteMapping("/batch/{id}")
    @RequirePerm("integration:config:edit")
    public Result<Object> deleteBatch(@PathVariable long id) {
        db.update("DELETE FROM int_batch_config WHERE id=?", id);
        return Result.ok(M.of("deleted", true));
    }
}
