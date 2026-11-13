package com.dgp.security;

import com.dgp.common.*;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** dgp-security:8088：敏感识别、脱敏、访问审批、审计与告警。 */
@RestController
@RequestMapping("/api/v1/security")
public class SecurityController {

    private final Db db;
    private final SensitiveScanService scanner;
    private final MaskService maskService;
    private final AuditService auditService;

    public SecurityController(Db db, SensitiveScanService scanner, MaskService maskService, AuditService auditService) {
        this.db = db;
        this.scanner = scanner;
        this.maskService = maskService;
        this.auditService = auditService;
    }

    private static void requireAdmin() {
        AuthUser u = AuthContext.get();
        if (u == null || !u.isAdmin()) {
            throw new BizException(ErrorCode.FORBIDDEN, "该操作仅限数据治理管理员");
        }
    }

    // ---------------- 敏感识别 ----------------
    @GetMapping("/sensitive/rules")
    @RequirePerm("security:sensitive:view")
    public Result<Object> rules() {
        return Result.ok(M.of("records", db.list("SELECT * FROM sec_sensitive_rule ORDER BY id")));
    }

    @PostMapping("/sensitive/rules")
    @RequirePerm("security:sensitive:edit")
    public Result<Object> createRule(@RequestBody Map<String, Object> b) {
        String pattern = Req.str(b.get("pattern"));
        try {
            java.util.regex.Pattern.compile(pattern);
        } catch (Exception e) {
            throw new BizException(ErrorCode.PARAM_INVALID, "正则表达式不合法：" + e.getMessage());
        }
        long id = db.insert("INSERT INTO sec_sensitive_rule(tenant_id,code,name,match_type,pattern,category,security_level,mask_algo,"
                        + "status,create_time) VALUES(?,?,?,?,?,?,?,?,'ENABLED',?)",
                AuthContext.tenantId(), b.getOrDefault("code", Ids.code("SR")), b.get("name"),
                b.getOrDefault("matchType", "NAME"), pattern, b.get("category"),
                b.getOrDefault("securityLevel", "L3"), b.getOrDefault("maskAlgo", "MASK"), LocalDateTime.now());
        return Result.ok(M.of("id", id));
    }

    @PostMapping("/sensitive/scan")
    @RequirePerm("security:sensitive:scan")
    public Result<Object> scan(@RequestBody(required = false) Map<String, Object> b) {
        String scope = b == null ? null : Req.str(b.get("tableName"));
        return Result.ok(scanner.scan(scope, AuthContext.username()));
    }

    @GetMapping("/sensitive/results")
    @RequirePerm("security:sensitive:view")
    public Result<Object> results(@RequestParam(required = false) String tableName,
                                  @RequestParam(required = false) String level,
                                  @RequestParam(defaultValue = "1") int pageNum,
                                  @RequestParam(defaultValue = "20") int pageSize) {
        StringBuilder where = new StringBuilder(" WHERE 1=1");
        List<Object> args = new ArrayList<>();
        if (!Req.blank(tableName)) {
            where.append(" AND table_name=?");
            args.add(tableName);
        }
        if (!Req.blank(level)) {
            where.append(" AND security_level=?");
            args.add(level);
        }
        return Result.ok(db.page("SELECT * FROM sec_sensitive_result" + where, "security_level DESC, table_name",
                pageNum, pageSize, args.toArray()).toMap());
    }

    @PostMapping("/sensitive/results/{id}/confirm")
    @RequirePerm("security:sensitive:edit")
    public Result<Object> confirm(@PathVariable long id, @RequestBody Map<String, Object> b) {
        String status = Req.str(b.getOrDefault("status", "CONFIRMED"));
        if (!List.of("CONFIRMED", "FALSE_POSITIVE").contains(status)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "状态只能是 CONFIRMED / FALSE_POSITIVE");
        }
        db.update("UPDATE sec_sensitive_result SET status=? WHERE id=?", status, id);
        return Result.ok(M.of("id", id));
    }

    // ---------------- 脱敏 ----------------
    @GetMapping("/mask/policies")
    @RequirePerm("security:mask:view")
    public Result<Object> policies(@RequestParam(required = false) String tableName,
                                   @RequestParam(required = false) String status) {
        StringBuilder where = new StringBuilder(" WHERE 1=1");
        List<Object> args = new ArrayList<>();
        if (!Req.blank(tableName)) {
            where.append(" AND table_name=?");
            args.add(tableName);
        }
        if (!Req.blank(status)) {
            where.append(" AND status=?");
            args.add(status);
        }
        return Result.ok(M.of("records", db.list("SELECT * FROM sec_mask_policy" + where
                + " ORDER BY table_name, column_name", args.toArray())));
    }

    @PostMapping("/mask/policies")
    @RequirePerm("security:mask:create")
    public Result<Object> createPolicy(@RequestBody Map<String, Object> b) {
        String table = Req.str(b.get("tableName"));
        String column = Req.str(b.get("columnName"));
        Map<String, Object> col = db.one("SELECT c.id, t.id AS tid FROM md_column c JOIN md_table t ON t.id=c.table_id "
                + "WHERE t.name=? AND c.name=?", table, column);
        if (col == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "字段不存在于元数据：" + table + "." + column);
        }
        String algorithm = Req.str(b.getOrDefault("algorithm", "MASK")).toUpperCase();
        if (!List.of("MASK", "REPLACE", "TRUNCATE", "HASH", "ENCRYPT", "GENERALIZE").contains(algorithm)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "不支持的脱敏算法：" + algorithm);
        }
        if (db.count("SELECT COUNT(*) FROM sec_mask_policy WHERE table_name=? AND column_name=? AND status<>'DISABLED'",
                table, column) > 0) {
            throw new BizException(ErrorCode.MASK_POLICY_CONFLICT, "该字段已存在生效中的脱敏策略");
        }
        long id = db.insert("INSERT INTO sec_mask_policy(tenant_id,table_id,table_name,column_id,column_name,algorithm,security_level,"
                        + "role_codes,status,create_by,create_time) VALUES(?,?,?,?,?,?,?,?,'PENDING',?,?)",
                AuthContext.tenantId(), col.get("tid"), table, col.get("id"), column, algorithm,
                b.getOrDefault("securityLevel", "L3"), b.getOrDefault("roleCodes", "ROLE_ANALYST"),
                AuthContext.username(), LocalDateTime.now());
        return Result.ok(M.of("id", id, "status", "PENDING"));
    }

    @PutMapping("/mask/policies/{id}")
    @RequirePerm("security:mask:edit")
    public Result<Object> updatePolicy(@PathVariable long id, @RequestBody Map<String, Object> b) {
        Map<String, Object> before = db.one("SELECT * FROM sec_mask_policy WHERE id=?", id);
        if (before == null) throw new BizException(ErrorCode.NOT_FOUND, "策略不存在");
        db.update("UPDATE sec_mask_policy SET algorithm=?,role_codes=?,security_level=? WHERE id=?",
                b.containsKey("algorithm") ? b.get("algorithm") : before.get("algorithm"),
                b.containsKey("roleCodes") ? b.get("roleCodes") : before.get("roleCodes"),
                b.containsKey("securityLevel") ? b.get("securityLevel") : before.get("securityLevel"), id);
        return Result.ok(M.of("id", id));
    }

    /** 删除脱敏策略（管理员/安全管理员；用于下线策略与验收清理）。 */
    @DeleteMapping("/mask/policies/{id}")
    @RequirePerm("security:mask:edit")
    public Result<Object> deletePolicy(@PathVariable long id) {
        Map<String, Object> before = db.one("SELECT * FROM sec_mask_policy WHERE id=?", id);
        if (before == null) throw new BizException(ErrorCode.NOT_FOUND, "策略不存在");
        db.update("DELETE FROM sec_mask_policy WHERE id=?", id);
        auditService.record(AuthContext.username(), "MASK_DELETE", "security", String.valueOf(id),
                String.valueOf(before.get("tableName")) + "." + before.get("columnName"), "-", "SUCCESS");
        return Result.ok(M.of("id", id, "deleted", true));
    }

    @PostMapping("/mask/policies/{id}/approve")
    @RequirePerm("security:mask:approve")
    public Result<Object> approve(@PathVariable long id, @RequestBody(required = false) Map<String, Object> b) {
        Map<String, Object> p = db.one("SELECT * FROM sec_mask_policy WHERE id=?", id);
        if (p == null) throw new BizException(ErrorCode.NOT_FOUND, "策略不存在");
        if (AuthContext.username().equals(String.valueOf(p.get("createBy")))) {
            throw new BizException(ErrorCode.FORBIDDEN, "策略创建人不能审批自己提交的策略");
        }
        boolean pass = b == null || !"REJECTED".equals(String.valueOf(b.getOrDefault("result", "APPROVED")));
        db.update("UPDATE sec_mask_policy SET status=?,approver=?,approve_time=? WHERE id=?",
                pass ? "ENABLED" : "REJECTED", AuthContext.username(), LocalDateTime.now(), id);
        auditService.record(AuthContext.username(), "MASK_APPROVE", "security", String.valueOf(id),
                "脱敏策略审批：" + (pass ? "通过" : "驳回"), "-", "SUCCESS");
        return Result.ok(M.of("id", id, "status", pass ? "ENABLED" : "REJECTED"));
    }

    @PostMapping("/mask/policies/{id}/enable")
    @RequirePerm("security:mask:approve")
    public Result<Object> enable(@PathVariable long id, @RequestBody Map<String, Object> b) {
        Map<String, Object> p = db.one("SELECT * FROM sec_mask_policy WHERE id=?", id);
        if (p == null) throw new BizException(ErrorCode.NOT_FOUND, "策略不存在");
        boolean on = !Boolean.FALSE.equals(b.get("enabled"));
        db.update("UPDATE sec_mask_policy SET status=? WHERE id=?", on ? "ENABLED" : "DISABLED", id);
        auditService.record(AuthContext.username(), on ? "MASK_ENABLE" : "MASK_DISABLE", "security",
                String.valueOf(id), String.valueOf(p.get("tableName")) + "." + p.get("columnName"), "-", "SUCCESS");
        return Result.ok(M.of("id", id, "status", on ? "ENABLED" : "DISABLED"));
    }

    /**
     * 脱敏预览：只允许预览"已登记在元数据中的字段"；原文仅在具备脱敏审批权限时回传，
     * 其它角色只拿到脱敏结果（原实现可传任意 tableName/columnName 读取任意表并回传原文）。
     */
    @PostMapping("/mask/preview")
    @RequirePerm("security:mask:view")
    public Result<Object> preview(@RequestBody Map<String, Object> b) {
        String table = Req.str(b.get("tableName"));
        String column = Req.str(b.get("columnName"));
        String algo = Req.str(b.getOrDefault("algorithm", "MASK")).toUpperCase();
        if (table == null || column == null) throw new BizException(ErrorCode.PARAM_INVALID, "tableName/columnName 必填");
        if (!Db.isIdentifier(table) || !Db.isIdentifier(column)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "非法的表名或字段名");
        }
        if (db.one("SELECT c.id FROM md_column c JOIN md_table t ON t.id=c.table_id WHERE t.name=? AND c.name=?",
                table, column) == null) {
            throw new BizException(ErrorCode.METADATA_NOT_FOUND, "字段未登记在元数据中，无法预览");
        }
        boolean canSeeRaw = AuthContext.get() != null && AuthContext.get().has("security:mask:approve");
        List<Map<String, Object>> rows = db.list("SELECT " + column + " AS v FROM " + table
                + " WHERE " + column + " IS NOT NULL LIMIT 8");
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> r : rows) {
            String raw = String.valueOf(r.get("v"));
            Map<String, Object> item = M.of("masked", maskService.mask(algo, raw));
            if (canSeeRaw) item.put("raw", raw);
            out.add(item);
        }
        return Result.ok(M.of("tableName", table, "columnName", column, "algorithm", algo, "samples", out,
                "rawVisible", canSeeRaw,
                "note", canSeeRaw ? "已具备脱敏审批权限，可核对原文" : "仅返回脱敏结果；如需核对原文需脱敏审批权限"));
    }

    @PostMapping("/mask/unmask-requests")
    @RequirePerm("security:mask:apply")
    public Result<Object> unmaskRequest(@RequestBody Map<String, Object> b) {
        long id = db.insert("INSERT INTO sec_access_apply(tenant_id,applicant,asset_type,asset_id,asset_name,reason,duration_days,"
                        + "status,create_time) VALUES(?,?,'COLUMN',?,?,?,?,'PENDING',?)",
                AuthContext.tenantId(), AuthContext.username(), b.get("columnId"), b.get("assetName"), b.get("reason"),
                b.getOrDefault("durationDays", 7), LocalDateTime.now());
        return Result.ok(M.of("applyId", id, "status", "PENDING"));
    }

    // ---------------- 访问审批 ----------------
    @GetMapping("/access-applies")
    @RequirePerm("security:access:view")
    public Result<Object> applies(@RequestParam(required = false) String status) {
        String sql = "SELECT * FROM sec_access_apply" + (Req.blank(status) ? "" : " WHERE status=?") + " ORDER BY id DESC";
        return Result.ok(M.of("records", Req.blank(status) ? db.list(sql) : db.list(sql, status)));
    }

    @PostMapping("/access-applies")
    @RequirePerm("security:access:apply")
    public Result<Object> apply(@RequestBody Map<String, Object> b) {
        long id = db.insert("INSERT INTO sec_access_apply(tenant_id,applicant,asset_type,asset_id,asset_name,reason,duration_days,"
                        + "status,create_time) VALUES(?,?,?,?,?,?,?,'PENDING',?)",
                AuthContext.tenantId(), AuthContext.username(), b.getOrDefault("assetType", "TABLE"), b.get("assetId"),
                b.get("assetName"), b.get("reason"), b.getOrDefault("durationDays", 30), LocalDateTime.now());
        return Result.ok(M.of("id", id, "status", "PENDING"));
    }

    @PostMapping("/access-applies/{id}/approve")
    @RequirePerm("security:access:approve")
    @Transactional
    public Result<Object> approveApply(@PathVariable long id, @RequestBody(required = false) Map<String, Object> b) {
        Map<String, Object> a = db.one("SELECT * FROM sec_access_apply WHERE id=?", id);
        if (a == null) throw new BizException(ErrorCode.NOT_FOUND);
        if (AuthContext.username().equals(String.valueOf(a.get("applicant")))) {
            throw new BizException(ErrorCode.FORBIDDEN, "申请人不能审批自己的访问申请");
        }
        if (!"PENDING".equals(String.valueOf(a.get("status")))) {
            throw new BizException(ErrorCode.PARAM_INVALID, "该申请已处理：" + a.get("status"));
        }
        boolean pass = b == null || !"REJECTED".equals(String.valueOf(b.getOrDefault("result", "APPROVED")));
        db.update("UPDATE sec_access_apply SET status=?,approver=?,approve_time=? WHERE id=?",
                pass ? "APPROVED" : "REJECTED", AuthContext.username(), LocalDateTime.now(), id);
        if (pass) {
            db.insert("INSERT INTO sec_access_grant(tenant_id,apply_id,subject,asset_name,scope,expire_time,status) "
                            + "VALUES(?,?,?,?,'READ',?,'ACTIVE')",
                    AuthContext.tenantId(), id, a.get("applicant"), a.get("assetName"),
                    LocalDateTime.now().plusDays((int) Db.longVal(a.get("durationDays"), 30)));
        }
        auditService.record(AuthContext.username(), "ACCESS_APPROVE", "security", String.valueOf(id),
                pass ? "通过" : "驳回", "-", "SUCCESS");
        return Result.ok(M.of("id", id, "status", pass ? "APPROVED" : "REJECTED"));
    }

    @GetMapping("/access-grants")
    @RequirePerm("security:access:view")
    public Result<Object> grants() {
        return Result.ok(M.of("records", db.list("SELECT * FROM sec_access_grant ORDER BY id DESC")));
    }

    @DeleteMapping("/access-grants/{id}")
    @RequirePerm("security:access:revoke")
    public Result<Object> revoke(@PathVariable long id) {
        db.update("UPDATE sec_access_grant SET status='REVOKED' WHERE id=?", id);
        return Result.ok(M.of("id", id, "status", "REVOKED"));
    }

    // ---------------- 审计 ----------------
    @GetMapping("/audit/logs")
    @RequirePerm("security:audit:view")
    public Result<Object> auditLogs(@RequestParam(required = false) String username,
                                    @RequestParam(required = false) String module,
                                    @RequestParam(required = false) String result,
                                    @RequestParam(required = false) String from,
                                    @RequestParam(required = false) String to,
                                    @RequestParam(defaultValue = "1") int pageNum,
                                    @RequestParam(defaultValue = "20") int pageSize) {
        StringBuilder where = new StringBuilder(" WHERE 1=1");
        List<Object> args = new ArrayList<>();
        if (!Req.blank(username)) {
            where.append(" AND username LIKE ?");
            args.add("%" + username + "%");
        }
        if (!Req.blank(module)) {
            where.append(" AND module=?");
            args.add(module);
        }
        if (!Req.blank(result)) {
            where.append(" AND result=?");
            args.add(result);
        }
        try {
            if (!Req.blank(from)) {
                where.append(" AND log_time >= ?");
                args.add(java.time.LocalDate.parse(from).atStartOfDay());
            }
            if (!Req.blank(to)) {
                where.append(" AND log_time < ?");
                args.add(java.time.LocalDate.parse(to).plusDays(1).atStartOfDay());
            }
        } catch (Exception e) {
            throw new BizException(ErrorCode.PARAM_INVALID, "日期格式应为 yyyy-MM-dd");
        }
        Map<String, Object> page = db.page("SELECT * FROM sec_audit_log" + where, "id DESC", pageNum, pageSize, args.toArray()).toMap();
        page.put("retainDays", 190);
        // 修正：原实现硬编码 chainVerified=true，与 verify-chain 的真实结果相反
        Map<String, Object> chain = auditService.cachedVerify();
        page.put("chainVerified", chain.get("intact"));
        page.put("chainBrokenAt", chain.get("brokenAt"));
        page.put("chainChecked", chain.get("checked"));
        return Result.ok(page);
    }

    @GetMapping("/audit/logs/export")
    @RequirePerm("security:audit:export")
    public Result<Object> exportAudit() {
        List<Map<String, Object>> rows = db.list("SELECT * FROM sec_audit_log ORDER BY id DESC LIMIT 5000");
        StringBuilder sb = new StringBuilder("\uFEFF时间,用户,动作,模块,资源,结果,IP,哈希\n");
        for (Map<String, Object> r : rows) {
            sb.append(csv(r.get("logTime"))).append(",").append(csv(r.get("username"))).append(",")
                    .append(csv(r.get("action"))).append(",").append(csv(r.get("module"))).append(",")
                    .append(csv(r.get("resource"))).append(",").append(csv(r.get("result"))).append(",")
                    .append(csv(r.get("ip"))).append(",").append(csv(r.get("hash"))).append("\n");
        }
        try {
            java.nio.file.Path dir = java.nio.file.Paths.get("data", "reports");
            java.nio.file.Files.createDirectories(dir);
            java.nio.file.Path f = dir.resolve("audit-" + System.currentTimeMillis() + ".csv");
            java.nio.file.Files.writeString(f, sb.toString(), java.nio.charset.StandardCharsets.UTF_8);
            auditService.record(AuthContext.username(), "AUDIT_EXPORT", "security", f.getFileName().toString(),
                    "rows=" + rows.size(), "-", "SUCCESS");
            return Result.ok(M.of("rows", rows.size(), "fileName", f.getFileName().toString(),
                    "filePath", f.toString().replace('\\', '/'), "note", "保留期 190 天，满足等保三级 ≥6 个月要求"));
        } catch (java.io.IOException e) {
            throw new IllegalStateException("审计导出失败", e);
        }
    }

    /** CSV 单元格转义：防公式注入与分隔符破坏。 */
    private static String csv(Object v) {
        if (v == null) return "";
        String s = String.valueOf(v);
        if (!s.isEmpty() && "=+-@".indexOf(s.charAt(0)) >= 0) s = "'" + s;
        return "\"" + s.replace("\"", "\"\"") + "\"";
    }

    @PostMapping("/audit/verify-chain")
    @RequirePerm("security:audit:view")
    public Result<Object> verifyChain(@RequestBody(required = false) Map<String, Object> b) {
        int limit = b == null ? 5000 : Req.toInt(b.getOrDefault("limit", 5000), 5000);
        return Result.ok(auditService.verifyChain(limit));
    }

    // ---------------- 告警 ----------------
    @GetMapping("/alerts")
    @RequirePerm("security:alert:view")
    public Result<Object> alerts(@RequestParam(required = false) String status) {
        String sql = "SELECT * FROM sec_alert" + (Req.blank(status) ? "" : " WHERE status=?") + " ORDER BY id DESC";
        return Result.ok(M.of("records", Req.blank(status) ? db.list(sql) : db.list(sql, status)));
    }

    @PostMapping("/alerts/{id}/handle")
    @RequirePerm("security:alert:handle")
    public Result<Object> handle(@PathVariable long id, @RequestBody(required = false) Map<String, Object> b) {
        Map<String, Object> a = db.one("SELECT * FROM sec_alert WHERE id=?", id);
        if (a == null) throw new BizException(ErrorCode.NOT_FOUND, "告警不存在");
        if ("HANDLED".equals(String.valueOf(a.get("status")))) {
            throw new BizException(ErrorCode.PARAM_INVALID, "该告警已处置");
        }
        db.update("UPDATE sec_alert SET status='HANDLED',handler=?,handle_time=?,detail=CONCAT(COALESCE(detail,''),?) WHERE id=?",
                AuthContext.username(), LocalDateTime.now(),
                " | 处置说明：" + (b == null ? "已确认并处置" : b.getOrDefault("comment", "已确认并处置")), id);
        return Result.ok(M.of("id", id, "status", "HANDLED"));
    }

    @GetMapping("/alert-rules")
    @RequirePerm("security:alert:view")
    public Result<Object> alertRules() {
        return Result.ok(M.of("records", db.list("SELECT * FROM sec_alert_rule ORDER BY id")));
    }

    @PutMapping("/alert-rules/{id}")
    @RequirePerm("security:alert:edit")
    public Result<Object> updateAlertRule(@PathVariable long id, @RequestBody Map<String, Object> b) {
        Map<String, Object> before = db.one("SELECT * FROM sec_alert_rule WHERE id=?", id);
        if (before == null) throw new BizException(ErrorCode.NOT_FOUND, "告警规则不存在");
        db.update("UPDATE sec_alert_rule SET threshold=?,window_min=?,severity=?,notify_channel=?,status=? WHERE id=?",
                b.containsKey("threshold") ? b.get("threshold") : before.get("threshold"),
                b.containsKey("windowMin") ? b.get("windowMin") : before.get("windowMin"),
                b.containsKey("severity") ? b.get("severity") : before.get("severity"),
                b.containsKey("notifyChannel") ? b.get("notifyChannel") : before.get("notifyChannel"),
                b.containsKey("status") ? b.get("status") : before.get("status"), id);
        return Result.ok(M.of("id", id));
    }

    @GetMapping("/stats/overview")
    @RequirePerm("security:overview:view")
    public Result<Object> stats() {
        return com.dgp.common.TtlCache.get("security:stats:" + com.dgp.security.AuthContext.tenantId(), 15_000,
                this::computeStats);
    }

    private Result<Object> computeStats() {
        return Result.ok(M.of(
                "sensitiveColumns", db.count("SELECT COUNT(*) FROM sec_sensitive_result"),
                "l4Columns", db.count("SELECT COUNT(*) FROM sec_sensitive_result WHERE security_level='L4'"),
                "maskPolicies", db.count("SELECT COUNT(*) FROM sec_mask_policy"),
                "pendingPolicies", db.count("SELECT COUNT(*) FROM sec_mask_policy WHERE status='PENDING'"),
                "auditLogs", db.count("SELECT COUNT(*) FROM sec_audit_log"),
                "openAlerts", db.count("SELECT COUNT(*) FROM sec_alert WHERE status='OPEN'"),
                "byLevel", db.list("SELECT security_level, COUNT(*) AS cnt FROM sec_sensitive_result GROUP BY security_level ORDER BY security_level"),
                "byCategory", db.list("SELECT r.category, COUNT(*) AS cnt FROM sec_sensitive_result s JOIN sec_sensitive_rule r ON r.id=s.rule_id GROUP BY r.category"),
                "recentAlerts", db.list("SELECT * FROM sec_alert ORDER BY id DESC LIMIT 5")));
    }
}
