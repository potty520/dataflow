package com.dgp.service;

import com.dgp.common.*;
import com.dgp.security.AuthContext;
import com.dgp.security.RequirePerm;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** dgp-service:8090：API 生命周期、应用与配额、黑白名单、编排、调用统计。 */
@RestController
@RequestMapping("/api/v1/service")
public class ServiceController {

    private final Db db;
    private final ServiceExecutionService executor;

    public ServiceController(Db db, ServiceExecutionService executor) {
        this.db = db;
        this.executor = executor;
    }

    private static void requireAdmin() {
        var u = AuthContext.get();
        if (u == null || !u.isAdmin()) throw new BizException(ErrorCode.FORBIDDEN, "该操作仅限数据治理管理员");
    }

    @GetMapping("/apis")
    @RequirePerm("service:api:view")
    public Result<Object> apis(@RequestParam(required = false) String keyword,
                               @RequestParam(required = false) String status,
                               @RequestParam(required = false) String category,
                               @RequestParam(defaultValue = "1") int pageNum,
                               @RequestParam(defaultValue = "20") int pageSize) {
        StringBuilder where = new StringBuilder(" WHERE 1=1");
        List<Object> args = new ArrayList<>();
        if (!Req.blank(keyword)) {
            where.append(" AND (name LIKE ? OR code LIKE ? OR api_path LIKE ?)");
            for (int i = 0; i < 3; i++) args.add("%" + keyword + "%");
        }
        if (!Req.blank(status)) {
            where.append(" AND status=?");
            args.add(status);
        }
        if (!Req.blank(category)) {
            where.append(" AND category=?");
            args.add(category);
        }
        return Result.ok(db.page("SELECT * FROM svc_api" + where, "id", pageNum, pageSize, args.toArray()).toMap());
    }

    @GetMapping("/apis/{id}")
    @RequirePerm("service:api:view")
    public Result<Object> api(@PathVariable long id) {
        Map<String, Object> a = db.one("SELECT * FROM svc_api WHERE id=?", id);
        if (a == null) throw new BizException(ErrorCode.NOT_FOUND);
        Map<String, Object> out = new LinkedHashMap<>(a);
        out.put("paramDefs", JsonUtil.parse(String.valueOf(a.get("params"))));
        out.put("versions", db.list("SELECT * FROM svc_api_version WHERE api_id=? ORDER BY id DESC", id));
        out.put("recentCalls", db.list("SELECT * FROM svc_call_log WHERE api_id=? ORDER BY id DESC LIMIT 10", id));
        out.put("openPath", "/open/v1" + a.get("apiPath"));
        return Result.ok(out);
    }

    @PostMapping("/apis")
    @RequirePerm("service:api:create")
    public Result<Object> createApi(@RequestBody Map<String, Object> b) {
        String code = Req.str(b.get("code"));
        String apiPath = Req.str(b.get("apiPath"));
        String sqlText = Req.str(b.get("sqlText"));
        if (Req.blank(code) || Req.blank(apiPath)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "code 与 apiPath 不能为空");
        }
        if (!apiPath.startsWith("/") || apiPath.contains("..")) {
            throw new BizException(ErrorCode.PARAM_INVALID, "apiPath 必须以 / 开头且不能包含 ..");
        }
        if (db.one("SELECT id FROM svc_api WHERE api_path=? AND status<>'OFFLINE'", apiPath) != null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "apiPath 已被占用：" + apiPath);
        }
        // 创建即校验只读，避免"草稿态可以写任意 SQL"（原实现只在 publish 校验，且 /test 直接执行）
        ServiceExecutionService.assertReadOnly(sqlText);
        long id = db.insert("INSERT INTO svc_api(tenant_id,code,name,api_path,method,version,status,category,sql_text,params,owner,"
                        + "app_code,auth_type,rate_limit,timeout_ms,mask_enabled,description,create_time,industry) "
                        + "VALUES(?,?,?,?,?,'v1','DRAFT',?,?,?,?,?,'AKSK',?,?,?,?,?,'GOV')",
                AuthContext.tenantId(), code, b.get("name"), apiPath, b.getOrDefault("method", "GET"),
                b.get("category"), sqlText, b.getOrDefault("params", "[]").toString(),
                AuthContext.username(), b.get("appCode"), b.getOrDefault("rateLimit", 100),
                b.getOrDefault("timeoutMs", 3000), b.getOrDefault("maskEnabled", 1), b.get("description"),
                LocalDateTime.now());
        return Result.ok(M.of("id", id));
    }

    @PutMapping("/apis/{id}")
    @RequirePerm("service:api:edit")
    public Result<Object> updateApi(@PathVariable long id, @RequestBody Map<String, Object> b) {
        Map<String, Object> before = db.one("SELECT * FROM svc_api WHERE id=?", id);
        if (before == null) throw new BizException(ErrorCode.NOT_FOUND, "API 不存在");
        String sqlText = b.containsKey("sqlText") ? Req.str(b.get("sqlText")) : String.valueOf(before.get("sqlText"));
        ServiceExecutionService.assertReadOnly(sqlText);
        // 部分更新：仅覆盖传入字段
        db.update("UPDATE svc_api SET name=?,api_path=?,method=?,category=?,sql_text=?,params=?,rate_limit=?,timeout_ms=?,"
                        + "mask_enabled=?,description=? WHERE id=?",
                b.containsKey("name") ? b.get("name") : before.get("name"),
                b.containsKey("apiPath") ? b.get("apiPath") : before.get("apiPath"),
                b.containsKey("method") ? b.get("method") : before.get("method"),
                b.containsKey("category") ? b.get("category") : before.get("category"),
                sqlText,
                b.containsKey("params") ? (b.get("params") == null ? null : b.get("params").toString()) : before.get("params"),
                b.containsKey("rateLimit") ? b.get("rateLimit") : before.get("rateLimit"),
                b.containsKey("timeoutMs") ? b.get("timeoutMs") : before.get("timeoutMs"),
                b.containsKey("maskEnabled") ? b.get("maskEnabled") : before.get("maskEnabled"),
                b.containsKey("description") ? b.get("description") : before.get("description"), id);
        return Result.ok(M.of("id", id));
    }

    @PostMapping("/apis/{id}/test")
    @RequirePerm("service:api:test")
    public Result<Object> test(@PathVariable long id, @RequestBody(required = false) Map<String, Object> b) {
        Map<String, Object> params = b == null ? Map.of() : (b.get("params") instanceof Map<?, ?> m
                ? JsonUtil.mapper().convertValue(m, new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {
        }) : Map.of());
        // dryRun 只影响记账与状态校验，SQL 仍走只读沙箱
        return Result.ok(executor.execute(id, params, null, "-", true));
    }

    @PostMapping("/apis/{id}/submit")
    @RequirePerm("service:api:submit")
    public Result<Object> submit(@PathVariable long id) {
        Map<String, Object> a = db.one("SELECT * FROM svc_api WHERE id=?", id);
        if (a == null) throw new BizException(ErrorCode.NOT_FOUND);
        if (Req.blank(String.valueOf(a.get("sqlText")))) throw new BizException(ErrorCode.PARAM_INVALID, "SQL 未配置");
        ServiceExecutionService.assertReadOnly(String.valueOf(a.get("sqlText")));
        db.update("UPDATE svc_api SET status='PENDING' WHERE id=?", id);
        return Result.ok(M.of("id", id, "status", "PENDING"));
    }

    @PostMapping("/apis/{id}/audit")
    @RequirePerm("service:api:audit")
    public Result<Object> audit(@PathVariable long id, @RequestBody(required = false) Map<String, Object> b) {
        Map<String, Object> a = db.one("SELECT * FROM svc_api WHERE id=?", id);
        if (a == null) throw new BizException(ErrorCode.NOT_FOUND);
        if (!"PENDING".equals(String.valueOf(a.get("status")))) {
            throw new BizException(ErrorCode.PARAM_INVALID, "只有待审核状态可以审核，当前：" + a.get("status"));
        }
        if (AuthContext.username().equals(String.valueOf(a.get("owner")))) {
            throw new BizException(ErrorCode.FORBIDDEN, "API 创建人不能审核自己提交的 API");
        }
        boolean pass = b == null || !"REJECTED".equals(String.valueOf(b.getOrDefault("result", "APPROVED")));
        db.update("UPDATE svc_api SET status=? WHERE id=?", pass ? "APPROVED" : "DRAFT", id);
        return Result.ok(M.of("id", id, "status", pass ? "APPROVED" : "DRAFT"));
    }

    @PostMapping("/apis/{id}/publish")
    @RequirePerm("service:api:publish")
    public Result<Object> publish(@PathVariable long id) {
        Map<String, Object> a = db.one("SELECT * FROM svc_api WHERE id=?", id);
        if (a == null) throw new BizException(ErrorCode.NOT_FOUND);
        String sqlText = String.valueOf(a.get("sqlText"));
        ServiceExecutionService.assertReadOnly(sqlText);
        if (!"APPROVED".equals(String.valueOf(a.get("status")))) {
            throw new BizException(ErrorCode.PARAM_INVALID, "只有已审核通过的 API 才能发布，当前：" + a.get("status"));
        }
        db.update("UPDATE svc_api SET status='PUBLISHED',publish_time=? WHERE id=?", LocalDateTime.now(), id);
        db.insert("INSERT INTO svc_api_version(api_id,version,sql_text,params,change_note,operator,create_time) "
                + "VALUES(?,?,?,?,'发布上线',?,?)", id, a.get("version"), sqlText,
                a.get("params"), AuthContext.username(), LocalDateTime.now());
        return Result.ok(M.of("id", id, "status", "PUBLISHED", "openPath", "/open/v1" + a.get("apiPath")));
    }

    @PostMapping("/apis/{id}/offline")
    @RequirePerm("service:api:offline")
    public Result<Object> offline(@PathVariable long id, @RequestBody(required = false) Map<String, Object> b) {
        Map<String, Object> a = db.one("SELECT status FROM svc_api WHERE id=?", id);
        if (a == null) throw new BizException(ErrorCode.NOT_FOUND);
        if ("OFFLINE".equals(String.valueOf(a.get("status")))) {
            throw new BizException(ErrorCode.PARAM_INVALID, "API 已处于下线状态");
        }
        if (b == null || Req.blank(Req.str(b.get("reason")))) {
            throw new BizException(ErrorCode.PARAM_INVALID, "下线必须填写原因");
        }
        db.update("UPDATE svc_api SET status='OFFLINE' WHERE id=?", id);
        return Result.ok(M.of("id", id, "status", "OFFLINE", "reason", b.get("reason")));
    }

    @GetMapping("/apis/{id}/versions")
    @RequirePerm("service:api:view")
    public Result<Object> versions(@PathVariable long id) {
        return Result.ok(M.of("records", db.list("SELECT * FROM svc_api_version WHERE api_id=? ORDER BY id DESC", id)));
    }

    // ---------------- 应用 / 配额 / 黑名单 ----------------
    /** 应用列表：密钥只回显掩码，明文仅在创建/重置时一次性返回。 */
    @GetMapping("/apps")
    @RequirePerm("service:app:view")
    public Result<Object> apps() {
        List<Map<String, Object>> list = db.list(
                "SELECT a.*, q.used_today, q.qps_limit FROM svc_app a "
                        + "LEFT JOIN svc_quota q ON q.app_code=a.app_code ORDER BY a.id");
        for (Map<String, Object> a : list) {
            Object secret = a.get("appSecret");
            a.remove("appSecret");
            a.put("appSecretMasked", secret == null ? null : maskSecret(String.valueOf(secret)));
        }
        return Result.ok(M.of("records", list));
    }

    /** 编辑应用：名称/负责人/状态/日配额（停用后开放通道立即拒绝该应用）。 */
    @PutMapping("/apps/{id}")
    @RequirePerm("service:app:edit")
    public Result<Object> updateApp(@PathVariable long id, @RequestBody Map<String, Object> b) {
        Map<String, Object> app = db.one("SELECT * FROM svc_app WHERE id=?", id);
        if (app == null) throw new BizException(ErrorCode.NOT_FOUND);
        String status = b.get("status") == null ? String.valueOf(app.get("status")) : String.valueOf(b.get("status"));
        if (!"ENABLED".equals(status) && !"DISABLED".equals(status)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "状态只能是 ENABLED / DISABLED");
        }
        db.update("UPDATE svc_app SET app_name=?,owner=?,status=?,quota_day=? WHERE id=?",
                b.containsKey("appName") ? b.get("appName") : app.get("appName"),
                b.containsKey("owner") ? b.get("owner") : app.get("owner"),
                status,
                b.containsKey("quotaDay") ? b.get("quotaDay") : app.get("quotaDay"), id);
        if (b.containsKey("quotaDay")) {
            db.update("UPDATE svc_quota SET quota_day=? WHERE app_code=?", b.get("quotaDay"), app.get("appCode"));
        }
        return Result.ok(M.of("id", id, "status", status));
    }

    /** 删除应用：仍被未下线 API 绑定时禁止删除，避免开放通道悬空。 */
    @DeleteMapping("/apps/{id}")
    @RequirePerm("service:app:edit")
    public Result<Object> deleteApp(@PathVariable long id) {
        Map<String, Object> app = db.one("SELECT * FROM svc_app WHERE id=?", id);
        if (app == null) throw new BizException(ErrorCode.NOT_FOUND);
        long bound = db.count("SELECT COUNT(*) FROM svc_api WHERE app_code=? AND status<>'OFFLINE'", app.get("appCode"));
        if (bound > 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "该应用仍被 " + bound + " 个未下线 API 绑定，请先下线或改绑");
        }
        db.update("DELETE FROM svc_app WHERE id=?", id);
        db.update("DELETE FROM svc_quota WHERE app_code=?", app.get("appCode"));
        return Result.ok(M.of("deleted", true));
    }

    private static String maskSecret(String s) {
        if (s.length() <= 8) return "****";
        return s.substring(0, 4) + "****" + s.substring(s.length() - 4);
    }

    @PostMapping("/apps")
    @RequirePerm("service:app:create")
    public Result<Object> createApp(@RequestBody Map<String, Object> b) {
        String appCode = Req.str(b.get("appCode"));
        if (Req.blank(appCode) || !Db.isIdentifier(appCode.replace('-', '_'))) {
            throw new BizException(ErrorCode.PARAM_INVALID, "appCode 不合法");
        }
        if (db.one("SELECT id FROM svc_app WHERE app_code=?", appCode) != null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "appCode 已存在");
        }
        String secret = "SK-" + Ids.uuid().substring(0, 24);
        long id = db.insert("INSERT INTO svc_app(tenant_id,app_code,app_name,app_secret,owner,status,quota_day,create_time) "
                        + "VALUES(?,?,?,?,?,'ENABLED',?,?)",
                AuthContext.tenantId(), appCode, b.get("appName"), secret, b.get("owner"),
                b.getOrDefault("quotaDay", 10000), LocalDateTime.now());
        // 同步配额记录：否则调用计数（used_today）与配额校验不生效
        db.insert("INSERT INTO svc_quota(tenant_id,app_code,quota_day,used_today,qps_limit) VALUES(?,?,?,0,?)",
                AuthContext.tenantId(), appCode, b.getOrDefault("quotaDay", 10000),
                b.getOrDefault("qpsLimit", 100));
        // 一次性返回明文密钥（之后任何接口都不再回显）
        return Result.ok(M.of("id", id, "appCode", appCode, "appSecret", secret,
                "note", "请立即保存，服务端不再提供明文查询"));
    }

    @PostMapping("/apps/{id}/reset-secret")
    @RequirePerm("service:app:edit")
    public Result<Object> resetSecret(@PathVariable long id) {
        if (db.one("SELECT id FROM svc_app WHERE id=?", id) == null) throw new BizException(ErrorCode.NOT_FOUND);
        String secret = "SK-" + Ids.uuid().substring(0, 24);
        db.update("UPDATE svc_app SET app_secret=? WHERE id=?", secret, id);
        return Result.ok(M.of("id", id, "appSecret", secret));
    }

    @GetMapping("/quota")
    @RequirePerm("service:quota:view")
    public Result<Object> quota() {
        return Result.ok(M.of("records", db.list("SELECT * FROM svc_quota ORDER BY id")));
    }

    @PutMapping("/quota")
    @RequirePerm("service:quota:edit")
    public Result<Object> updateQuota(@RequestBody Map<String, Object> b) {
        String appCode = Req.str(b.get("appCode"));
        if (Req.blank(appCode)) throw new BizException(ErrorCode.PARAM_INVALID, "appCode 不能为空");
        Map<String, Object> before = db.one("SELECT * FROM svc_quota WHERE app_code=?", appCode);
        if (before == null) throw new BizException(ErrorCode.NOT_FOUND, "该应用没有配额配置");
        db.update("UPDATE svc_quota SET quota_day=?,qps_limit=? WHERE app_code=?",
                b.containsKey("quotaDay") ? b.get("quotaDay") : before.get("quotaDay"),
                b.containsKey("qpsLimit") ? b.get("qpsLimit") : before.get("qpsLimit"), appCode);
        return Result.ok(M.of("appCode", appCode));
    }

    @GetMapping("/blacklist")
    @RequirePerm("service:blacklist:view")
    public Result<Object> blacklist() {
        return Result.ok(M.of("records", db.list("SELECT * FROM svc_blacklist ORDER BY id DESC")));
    }

    @PostMapping("/blacklist")
    @RequirePerm("service:blacklist:edit")
    public Result<Object> addBlacklist(@RequestBody Map<String, Object> b) {
        long id = db.insert("INSERT INTO svc_blacklist(ip,reason,status,create_time) VALUES(?,?,'ENABLED',?)",
                b.get("ip"), b.get("reason"), LocalDateTime.now());
        return Result.ok(M.of("id", id));
    }

    // ---------------- 编排 ----------------
    @GetMapping("/orchestrations")
    @RequirePerm("service:orchestration:view")
    public Result<Object> orchestrations() {
        List<Map<String, Object>> list = db.list("SELECT * FROM svc_orchestration ORDER BY id DESC");
        for (Map<String, Object> o : list) {
            o.put("runCount", db.count("SELECT COUNT(*) FROM svc_orchestration_run WHERE orch_id=?", o.get("id")));
            int steps = 0;
            Object stepsObj = JsonUtil.parse(String.valueOf(o.get("steps"))).get("steps");
            if (stepsObj instanceof List<?> l) steps = l.size();
            o.put("stepCount", steps);
        }
        return Result.ok(M.of("records", list, "total", list.size()));
    }

    @PostMapping("/orchestrations")
    @RequirePerm("service:orchestration:edit")
    public Result<Object> createOrchestration(@RequestBody Map<String, Object> b) {
        long id = db.insert("INSERT INTO svc_orchestration(tenant_id,code,name,status,description,steps,owner,create_time) "
                        + "VALUES(?,?,?,?,?,?,?,?)",
                AuthContext.tenantId(), b.getOrDefault("code", Ids.code("ORCH")), b.get("name"),
                b.getOrDefault("status", "DRAFT"), b.get("description"),
                b.get("steps") == null ? "[]" : JsonUtil.toJson(b.get("steps")),
                AuthContext.username(), LocalDateTime.now());
        return Result.ok(M.of("id", id));
    }

    @PostMapping("/orchestrations/{id}/run")
    @RequirePerm("service:orchestration:exec")
    public Result<Object> runOrchestration(@PathVariable long id) {
        return Result.ok(executor.orchestrate(id));
    }

    // ---------------- 统计 ----------------
    @GetMapping("/metrics")
    @RequirePerm("service:metrics:view")
    public Result<Object> metrics(@RequestParam(defaultValue = "14") int days) {
        return com.dgp.common.TtlCache.get("service:metrics:" + days + ":" + com.dgp.security.AuthContext.tenantId(),
                10_000, () -> computeMetrics(days));
    }

    private Result<Object> computeMetrics(int days) {
        return Result.ok(M.of("summary", executor.callMetrics(), "trend", executor.metrics(days),
                "byApi", db.list("SELECT api_code,api_name,COUNT(*) AS calls,ROUND(AVG(duration_ms),1) AS avg_ms,"
                        + "SUM(CASE WHEN status_code=200 THEN 1 ELSE 0 END) AS success FROM svc_call_log GROUP BY api_code,api_name ORDER BY calls DESC"),
                "byStatus", db.list("SELECT status_code, COUNT(*) AS cnt FROM svc_call_log GROUP BY status_code")));
    }

    @GetMapping("/metrics/topn")
    @RequirePerm("service:metrics:view")
    public Result<Object> topn(@RequestParam(defaultValue = "10") int limit) {
        return Result.ok(executor.topApis(limit));
    }

    @GetMapping("/stats/overview")
    @RequirePerm("service:overview:view")
    public Result<Object> stats() {
        return com.dgp.common.TtlCache.get("service:stats:" + com.dgp.security.AuthContext.tenantId(), 10_000,
                this::computeStats);
    }

    private Result<Object> computeStats() {
        return Result.ok(M.of("apiCount", db.count("SELECT COUNT(*) FROM svc_api"),
                "publishedCount", db.count("SELECT COUNT(*) FROM svc_api WHERE status='PUBLISHED'"),
                "pendingCount", db.count("SELECT COUNT(*) FROM svc_api WHERE status='PENDING'"),
                "appCount", db.count("SELECT COUNT(*) FROM svc_app"),
                "callCount", db.count("SELECT COUNT(*) FROM svc_call_log"),
                "orchestrationCount", db.count("SELECT COUNT(*) FROM svc_orchestration"),
                "byCategory", db.list("SELECT category, COUNT(*) AS cnt FROM svc_api GROUP BY category"),
                "byStatus", db.list("SELECT status, COUNT(*) AS cnt FROM svc_api GROUP BY status")));
    }
}
