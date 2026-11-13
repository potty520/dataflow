package com.dgp.quality;

import com.dgp.common.BizException;
import com.dgp.common.Db;
import com.dgp.common.ErrorCode;
import com.dgp.common.M;
import com.dgp.common.Result;
import com.dgp.common.JsonUtil;
import com.dgp.security.AuthContext;
import com.dgp.security.RequirePerm;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** BF-08：质量配置化（工单流转规则、稽核任务模板）。 */
@RestController
@RequestMapping("/api/v1/quality/config")
public class QualityConfigController {

    private final Db db;

    public QualityConfigController(Db db) {
        this.db = db;
    }

    // ---------------- 工单规则 ----------------
    @GetMapping("/issue-rules")
    @RequirePerm("quality:config:view")
    public Result<Object> issueRules() {
        return Result.ok(M.of("records", db.list("SELECT * FROM qa_issue_rule ORDER BY id")));
    }

    @PostMapping("/issue-rules")
    @RequirePerm("quality:config:edit")
    public Result<Object> createIssueRule(@RequestBody Map<String, Object> b) {
        String code = Db.str(b.get("code"));
        if (code == null || code.isBlank()) throw new BizException(ErrorCode.PARAM_INVALID, "编码不能为空");
        if (db.one("SELECT id FROM qa_issue_rule WHERE code=?", code) != null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "编码已存在：" + code);
        }
        long id = db.insert("INSERT INTO qa_issue_rule(tenant_id,code,name,auto_create,overdue_hours,escalate_hours,"
                        + "default_assignee,status,remark,create_time,update_time) VALUES(?,?,?,?,?,?,?,?,?,?,?)",
                AuthContext.tenantId(), code, b.get("name"), b.getOrDefault("autoCreate", 1),
                b.getOrDefault("overdueHours", 72), b.getOrDefault("escalateHours", 96),
                b.get("defaultAssignee"), b.getOrDefault("status", "ENABLED"), b.get("remark"),
                LocalDateTime.now(), LocalDateTime.now());
        return Result.ok(M.of("id", id));
    }

    @PutMapping("/issue-rules/{id}")
    @RequirePerm("quality:config:edit")
    public Result<Object> updateIssueRule(@PathVariable long id, @RequestBody Map<String, Object> b) {
        Map<String, Object> r = db.one("SELECT * FROM qa_issue_rule WHERE id=?", id);
        if (r == null) throw new BizException(ErrorCode.NOT_FOUND, "规则不存在");
        db.update("UPDATE qa_issue_rule SET name=?,auto_create=?,overdue_hours=?,escalate_hours=?,default_assignee=?,"
                        + "status=?,remark=?,update_time=? WHERE id=?",
                b.containsKey("name") ? b.get("name") : r.get("name"),
                b.containsKey("autoCreate") ? b.get("autoCreate") : r.get("autoCreate"),
                b.containsKey("overdueHours") ? b.get("overdueHours") : r.get("overdueHours"),
                b.containsKey("escalateHours") ? b.get("escalateHours") : r.get("escalateHours"),
                b.containsKey("defaultAssignee") ? b.get("defaultAssignee") : r.get("defaultAssignee"),
                b.containsKey("status") ? b.get("status") : r.get("status"),
                b.containsKey("remark") ? b.get("remark") : r.get("remark"), LocalDateTime.now(), id);
        return Result.ok(M.of("id", id));
    }

    @DeleteMapping("/issue-rules/{id}")
    @RequirePerm("quality:config:edit")
    public Result<Object> deleteIssueRule(@PathVariable long id) {
        db.update("DELETE FROM qa_issue_rule WHERE id=?", id);
        return Result.ok(M.of("deleted", true));
    }

    @SuppressWarnings("unchecked")
    private static List<String> parseCodes(String json) {
        try {
            Object o = JsonUtil.mapper().readValue(json, Object.class);
            List<String> out = new ArrayList<>();
            if (o instanceof List<?> l) for (Object x : l) out.add(String.valueOf(x));
            return out;
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    // ---------------- 稽核任务模板 ----------------
    @GetMapping("/check-templates")
    @RequirePerm("quality:config:view")
    public Result<Object> checkTemplates() {
        List<Map<String, Object>> rows = db.list("SELECT * FROM qa_check_template ORDER BY id");
        for (Map<String, Object> r : rows) {
            r.put("ruleCodeList", parseCodes(String.valueOf(r.get("ruleCodes"))));
        }
        return Result.ok(M.of("records", rows));
    }

    @PostMapping("/check-templates")
    @RequirePerm("quality:config:edit")
    public Result<Object> createTemplate(@RequestBody Map<String, Object> b) {
        String code = Db.str(b.get("code"));
        if (code == null || code.isBlank()) throw new BizException(ErrorCode.PARAM_INVALID, "编码不能为空");
        if (db.one("SELECT id FROM qa_check_template WHERE code=?", code) != null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "编码已存在：" + code);
        }
        long id = db.insert("INSERT INTO qa_check_template(tenant_id,code,name,check_mode,schedule_type,cron,batch_size,"
                        + "rule_codes,status,remark,create_time,update_time) VALUES(?,?,?,?,?,?,?,?,?,?,?,?)",
                AuthContext.tenantId(), code, b.get("name"), b.getOrDefault("checkMode", "FULL"),
                b.getOrDefault("scheduleType", "MANUAL"), b.get("cron"), b.getOrDefault("batchSize", 10000),
                JsonUtil.toJson(b.getOrDefault("ruleCodes", List.of())),
                b.getOrDefault("status", "ENABLED"), b.get("remark"), LocalDateTime.now(), LocalDateTime.now());
        return Result.ok(M.of("id", id));
    }

    @PutMapping("/check-templates/{id}")
    @RequirePerm("quality:config:edit")
    public Result<Object> updateTemplate(@PathVariable long id, @RequestBody Map<String, Object> b) {
        Map<String, Object> r = db.one("SELECT * FROM qa_check_template WHERE id=?", id);
        if (r == null) throw new BizException(ErrorCode.NOT_FOUND, "模板不存在");
        Object ruleCodes = b.containsKey("ruleCodes") ? JsonUtil.toJson(b.get("ruleCodes")) : r.get("ruleCodes");
        db.update("UPDATE qa_check_template SET name=?,check_mode=?,schedule_type=?,cron=?,batch_size=?,rule_codes=?,"
                        + "status=?,remark=?,update_time=? WHERE id=?",
                b.containsKey("name") ? b.get("name") : r.get("name"),
                b.containsKey("checkMode") ? b.get("checkMode") : r.get("checkMode"),
                b.containsKey("scheduleType") ? b.get("scheduleType") : r.get("scheduleType"),
                b.containsKey("cron") ? b.get("cron") : r.get("cron"),
                b.containsKey("batchSize") ? b.get("batchSize") : r.get("batchSize"),
                ruleCodes, b.containsKey("status") ? b.get("status") : r.get("status"),
                b.containsKey("remark") ? b.get("remark") : r.get("remark"), LocalDateTime.now(), id);
        return Result.ok(M.of("id", id));
    }

    @DeleteMapping("/check-templates/{id}")
    @RequirePerm("quality:config:edit")
    public Result<Object> deleteTemplate(@PathVariable long id) {
        db.update("DELETE FROM qa_check_template WHERE id=?", id);
        return Result.ok(M.of("deleted", true));
    }

    /** 从模板创建稽核任务（复制模板参数并绑定规则编码）。 */
    @PostMapping("/check-templates/{id}/apply")
    @RequirePerm("quality:config:edit")
    public Result<Object> applyTemplate(@PathVariable long id) {
        Map<String, Object> t = db.one("SELECT * FROM qa_check_template WHERE id=?", id);
        if (t == null) throw new BizException(ErrorCode.NOT_FOUND, "模板不存在");
        String code = Db.str(t.get("code"));
        String checkCode = code + "-" + System.currentTimeMillis() % 100000;
        long checkId = db.insert("INSERT INTO qa_check(tenant_id,code,name,description,check_mode,schedule_type,cron,"
                        + "status,owner,create_time) VALUES(?,?,?,?,?,?,?,'ENABLED',?,?)",
                AuthContext.tenantId(), checkCode, t.get("name"), "由模板 " + code + " 创建",
                t.get("checkMode"), t.get("scheduleType"), t.get("cron"),
                AuthContext.username(), LocalDateTime.now());
        int bound = 0;
        for (Object rc : parseCodes(String.valueOf(t.get("ruleCodes")))) {
            Map<String, Object> rule = db.one("SELECT id FROM qa_rule WHERE code=?", rc);
            if (rule != null) {
                db.insert("INSERT INTO qa_check_rule(check_id,rule_id) VALUES(?,?)", checkId, rule.get("id"));
                bound++;
            }
        }
        return Result.ok(M.of("checkId", checkId, "code", checkCode, "rules", bound));
    }
}
