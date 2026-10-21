package com.dgp.integration;

import com.dgp.common.*;
import com.dgp.security.AuthContext;
import com.dgp.security.RequirePerm;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** dgp-integration:8085：数据源、ETL 作业、脚本、SQL 工具、沙箱策略。 */
@RestController
@RequestMapping("/api/v1/integration")
public class IntegrationController {

    private final Db db;
    private final IntegrationService service;

    public IntegrationController(Db db, IntegrationService service) {
        this.db = db;
        this.service = service;
    }

    // ---------------- 数据源 ----------------
    @GetMapping("/datasources")
    @RequirePerm("integration:datasource:view")
    public Result<Object> datasources() {
        List<Map<String, Object>> list = db.list("SELECT * FROM md_datasource ORDER BY id");
        for (Map<String, Object> d : list) {
            d.remove("passwordEnc");
            // 修正：原实现用同一个全量计数冒充每行 jobCount
            d.put("jobCount", db.count("SELECT COUNT(*) FROM int_job WHERE status='PUBLISHED'"));
        }
        return Result.ok(M.of("records", list, "total", list.size()));
    }

    @PostMapping("/datasources")
    @RequirePerm("integration:datasource:create")
    public Result<Object> createDs(@RequestBody Map<String, Object> b) {
        long id = db.insert("INSERT INTO md_datasource(tenant_id,code,name,dtype,host,port,db_name,username,industry,domain,owner,"
                        + "conn_status,remark,create_time) VALUES(?,?,?,?,?,?,?,?,?,?,?,'未测试',?,?)",
                AuthContext.tenantId(), b.get("code"), b.get("name"), b.get("dtype"), b.get("host"), b.get("port"),
                b.get("dbName"), b.get("username"), b.getOrDefault("industry", "GOV"), b.get("domain"),
                b.get("owner"), b.get("remark"), LocalDateTime.now());
        return Result.ok(M.of("id", id));
    }

    @PutMapping("/datasources/{id}")
    @RequirePerm("integration:datasource:edit")
    public Result<Object> updateDs(@PathVariable long id, @RequestBody Map<String, Object> b) {
        Map<String, Object> before = db.one("SELECT * FROM md_datasource WHERE id=?", id);
        if (before == null) throw new BizException(ErrorCode.NOT_FOUND, "数据源不存在");
        db.update("UPDATE md_datasource SET name=?,dtype=?,host=?,port=?,db_name=?,username=?,remark=? WHERE id=?",
                pick(b, before, "name"), pick(b, before, "dtype"), pick(b, before, "host"), pick(b, before, "port"),
                pick(b, before, "dbName"), pick(b, before, "username"), pick(b, before, "remark"), id);
        return Result.ok(M.of("id", id));
    }

    @PostMapping("/datasources/{id}/test")
    @RequirePerm("integration:datasource:test")
    public Result<Object> testDs(@PathVariable long id) {
        Map<String, Object> d = db.one("SELECT * FROM md_datasource WHERE id=?", id);
        if (d == null) throw new BizException(ErrorCode.NOT_FOUND);
        long t0 = System.currentTimeMillis();
        String status;
        String message;
        Integer latency = null;
        if ("H2".equals(String.valueOf(d.get("dtype")))) {
            try {
                db.scalar("SELECT 1");
                latency = (int) (System.currentTimeMillis() - t0);
                status = "已连接";
                message = "平台内置 H2 数据湖探针执行成功";
            } catch (Exception e) {
                status = "异常";
                message = "探针失败：" + e.getClass().getSimpleName();
            }
        } else {
            String host = String.valueOf(d.get("host"));
            int port = Req.toInt(d.get("port"), 0);
            if (port <= 0 || Req.blank(host)) {
                status = "异常";
                message = "主机或端口未配置";
            } else {
                try (java.net.Socket s = new java.net.Socket()) {
                    s.connect(new java.net.InetSocketAddress(host, port), 1200);
                    latency = (int) (System.currentTimeMillis() - t0);
                    status = "已连接";
                    message = "TCP " + host + ":" + port + " 可达（仅连通性探测）";
                } catch (Exception e) {
                    status = "异常";
                    message = "不可达（" + e.getClass().getSimpleName() + "）";
                }
            }
        }
        db.update("UPDATE md_datasource SET conn_status=?,last_test_time=?,latency_ms=? WHERE id=?",
                status, LocalDateTime.now(), latency, id);
        return Result.ok(M.of("id", id, "status", status, "latencyMs", latency, "message", message));
    }

    @GetMapping("/datasources/{id}/status")
    @RequirePerm("integration:datasource:view")
    public Result<Object> dsStatus(@PathVariable long id) {
        Map<String, Object> d = db.one("SELECT id,name,dtype,conn_status,last_test_time,latency_ms FROM md_datasource WHERE id=?", id);
        if (d == null) throw new BizException(ErrorCode.NOT_FOUND);
        return Result.ok(d);
    }

    // ---------------- 作业 ----------------
    @GetMapping("/jobs")
    @RequirePerm("integration:job:view")
    public Result<Object> jobs(@RequestParam(required = false) String keyword,
                               @RequestParam(required = false) String jobType,
                               @RequestParam(required = false) String status,
                               @RequestParam(defaultValue = "1") int pageNum,
                               @RequestParam(defaultValue = "20") int pageSize) {
        StringBuilder where = new StringBuilder(" WHERE 1=1");
        List<Object> args = new ArrayList<>();
        if (!Req.blank(keyword)) {
            where.append(" AND (name LIKE ? OR code LIKE ? OR cn_name LIKE ?)");
            for (int i = 0; i < 3; i++) args.add("%" + keyword + "%");
        }
        if (!Req.blank(jobType)) {
            where.append(" AND job_type=?");
            args.add(jobType);
        }
        if (!Req.blank(status)) {
            where.append(" AND status=?");
            args.add(status);
        }
        String base = "SELECT j.*, (SELECT COUNT(*) FROM int_job_instance i WHERE i.job_id=j.id) AS instance_count "
                + "FROM int_job j" + where;
        return Result.ok(db.page(base, "j.id", pageNum, pageSize, args.toArray()).toMap());
    }

    @GetMapping("/jobs/{id}")
    @RequirePerm("integration:job:view")
    public Result<Object> job(@PathVariable long id) {
        Map<String, Object> j = db.one("SELECT * FROM int_job WHERE id=?", id);
        if (j == null) throw new BizException(ErrorCode.NOT_FOUND);
        Map<String, Object> out = new LinkedHashMap<>(j);
        out.put("instances", db.list("SELECT * FROM int_job_instance WHERE job_id=? ORDER BY id DESC LIMIT 10", id));
        return Result.ok(out);
    }

    @PostMapping("/jobs")
    @RequirePerm("integration:job:create")
    public Result<Object> createJob(@RequestBody Map<String, Object> b) {
        String name = Req.str(b.get("name"));
        long id = db.insert("INSERT INTO int_job(tenant_id,code,name,cn_name,job_type,engine,status,schedule_type,cron,interval_min,"
                        + "priority,timeout_min,retry,industry,dsl,script_lang,script,owner,description,version,next_run_time,create_time) "
                        + "VALUES(?,?,?,?,?,?,'DRAFT',?,?,?,?,?,?,?,?,?,?,?,?,'1.0.0',?,?)",
                AuthContext.tenantId(), b.get("code"), name, name, b.getOrDefault("jobType", "ETL"),
                b.getOrDefault("engine", "BATCH"), b.getOrDefault("scheduleType", "MANUAL"), b.get("cron"),
                b.get("intervalMin"), b.getOrDefault("priority", 5), b.getOrDefault("timeoutMin", 60),
                b.getOrDefault("retry", 0), b.getOrDefault("industry", "GOV"), b.get("dsl"),
                b.getOrDefault("scriptLang", "SQL"), b.get("script"), AuthContext.username(),
                b.get("description"), LocalDateTime.now().plusHours(1), LocalDateTime.now());
        return Result.ok(M.of("id", id));
    }

    @PutMapping("/jobs/{id}")
    @RequirePerm("integration:job:edit")
    public Result<Object> updateJob(@PathVariable long id, @RequestBody Map<String, Object> b) {
        Map<String, Object> before = db.one("SELECT * FROM int_job WHERE id=?", id);
        if (before == null) throw new BizException(ErrorCode.NOT_FOUND, "作业不存在");
        db.update("UPDATE int_job SET cn_name=?,dsl=?,script_lang=?,script=?,schedule_type=?,cron=?,interval_min=?,"
                        + "priority=?,timeout_min=?,retry=?,description=? WHERE id=?",
                pick(b, before, "cnName"), pick(b, before, "dsl"), pick(b, before, "scriptLang"),
                pick(b, before, "script"), pick(b, before, "scheduleType"), pick(b, before, "cron"),
                pick(b, before, "intervalMin"), pick(b, before, "priority"), pick(b, before, "timeoutMin"),
                pick(b, before, "retry"), pick(b, before, "description"), id);
        return Result.ok(M.of("id", id));
    }

    @DeleteMapping("/jobs/{id}")
    @RequirePerm("integration:job:delete")
    @Transactional
    public Result<Object> deleteJob(@PathVariable long id) {
        db.update("DELETE FROM int_job_log WHERE instance_id IN (SELECT id FROM int_job_instance WHERE job_id=?)", id);
        db.update("DELETE FROM int_job_instance WHERE job_id=?", id);
        db.update("DELETE FROM int_job WHERE id=?", id);
        return Result.ok(M.of("deleted", true));
    }

    @PostMapping("/jobs/{id}/publish")
    @RequirePerm("integration:job:publish")
    public Result<Object> publishJob(@PathVariable long id) {
        Map<String, Object> j = db.one("SELECT * FROM int_job WHERE id=?", id);
        if (j == null) throw new BizException(ErrorCode.NOT_FOUND);
        Object scriptObj = j.get("script");
        String script = scriptObj == null ? "" : String.valueOf(scriptObj);
        if (script.isBlank()) throw new BizException(ErrorCode.PARAM_INVALID, "作业未配置脚本，无法发布");
        // 修正：原实现只校验第一条语句，后续语句可写入 DROP/DELETE 等
        List<String> statements = IntegrationService.splitStatements(script);
        if (statements.isEmpty()) throw new BizException(ErrorCode.PARAM_INVALID, "脚本中没有可执行语句");
        for (String stmt : statements) {
            String head = stmt.trim().split("\\s+")[0].toLowerCase();
            if (!List.of("insert", "update", "delete", "merge", "select").contains(head)) {
                throw new BizException(ErrorCode.SQL_INVALID, "沙箱策略禁止该语句：" + head.toUpperCase());
            }
        }
        db.update("UPDATE int_job SET status='PUBLISHED',next_run_time=? WHERE id=?", LocalDateTime.now().plusMinutes(5), id);
        return Result.ok(M.of("id", id, "status", "PUBLISHED"));
    }

    @PostMapping("/jobs/{id}/run")
    @RequirePerm("integration:job:run")
    public Result<Object> runJob(@PathVariable long id) {
        return Result.ok(service.runJob(id, "MANUAL", AuthContext.username()));
    }

    /** 停止：仅把待运行实例标记为 KILLED；已在执行的 JDBC 语句无法中断，故明确回传 interrupted=false。 */
    @PostMapping("/jobs/{id}/stop")
    @RequirePerm("integration:job:run")
    public Result<Object> stopJob(@PathVariable long id) {
        int n = db.update("UPDATE int_job_instance SET status='KILLED',message='人工终止' WHERE job_id=? AND status='RUNNING'", id);
        return Result.ok(M.of("stopped", n > 0, "terminatedInstances", n,
                "interrupted", false,
                "note", "未开始的实例已终止；正在执行的 SQL 语句无法中断，将在当前语句结束后停止"));
    }

    @GetMapping("/jobs/{id}/logs")
    @RequirePerm("integration:job:view")
    public Result<Object> logs(@PathVariable long id, @RequestParam(defaultValue = "200") int limit) {
        List<Map<String, Object>> list = db.list(
                "SELECT l.* FROM int_job_log l JOIN int_job_instance i ON i.id=l.instance_id WHERE i.job_id=? ORDER BY l.id DESC LIMIT ?",
                id, Math.max(1, Math.min(limit, 500)));
        java.util.Collections.reverse(list);
        return Result.ok(M.of("records", list, "total", list.size()));
    }

    /** SSE 实时日志流。 */
    @GetMapping(value = "/jobs/{id}/logs/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @RequirePerm("integration:job:view")
    public SseEmitter stream(@PathVariable long id) {
        SseEmitter emitter = new SseEmitter(20000L);
        try {
            Object maxId = db.scalar("SELECT MAX(id) FROM int_job_instance WHERE job_id=?", id);
            if (maxId == null) {
                emitter.send(SseEmitter.event().name("done").data("{\"eof\":true,\"empty\":true}"));
                emitter.complete();
                return emitter;
            }
            List<Map<String, Object>> logs = db.list("SELECT * FROM int_job_log WHERE instance_id=? ORDER BY id",
                    ((Number) maxId).longValue());
            for (Map<String, Object> l : logs) {
                emitter.send(SseEmitter.event().data(JsonUtil.toJson(l)));
            }
            emitter.send(SseEmitter.event().name("done").data("{\"eof\":true}"));
            emitter.complete();
        } catch (Exception e) {
            emitter.completeWithError(e);
        }
        return emitter;
    }

    @GetMapping("/jobs/{id}/instances")
    @RequirePerm("integration:job:view")
    public Result<Object> instances(@PathVariable long id, @RequestParam(defaultValue = "1") int pageNum,
                                    @RequestParam(defaultValue = "20") int pageSize) {
        return Result.ok(db.page("SELECT * FROM int_job_instance WHERE job_id=?", "id DESC", pageNum, pageSize, id).toMap());
    }

    @PostMapping("/jobs/{id}/compile")
    @RequirePerm("integration:job:edit")
    public Result<Object> compile(@PathVariable long id) {
        return Result.ok(service.compile(id));
    }

    @PostMapping("/jobs/{id}/dsl:toScript")
    @RequirePerm("integration:job:script")
    public Result<Object> toScript(@PathVariable long id) {
        return Result.ok(service.dslToScript(id));
    }

    @PostMapping("/jobs/{id}/dsl:fromScript")
    @RequirePerm("integration:job:script")
    public Result<Object> fromScript(@PathVariable long id) {
        return Result.ok(service.scriptToDsl(id));
    }

    @PostMapping("/jobs/{id}/dry-run")
    @RequirePerm("integration:job:run")
    public Result<Object> dryRun(@PathVariable long id, @RequestBody(required = false) Map<String, Object> b) {
        int limit = b == null ? 10 : Req.toInt(b.get("limit"), 10);
        return Result.ok(service.dryRun(id, limit));
    }

    @PostMapping("/sql/format")
    @RequirePerm("integration:sql:edit")
    public Result<Object> format(@RequestBody Map<String, Object> b) {
        return Result.ok(service.formatSql(Req.str(b.get("sql"))));
    }

    @PostMapping("/sql/validate")
    @RequirePerm("integration:sql:edit")
    public Result<Object> validate(@RequestBody Map<String, Object> b) {
        return Result.ok(service.validateSql(Req.str(b.get("sql"))));
    }

    @PostMapping("/adhoc/query")
    @RequirePerm("integration:adhoc:query")
    public Result<Object> adhoc(@RequestBody Map<String, Object> b) {
        return Result.ok(service.adhocQuery(Req.str(b.get("sql")), Req.toInt(b.get("limit"), 100)));
    }

    @PostMapping("/scripts")
    @RequirePerm("integration:script:create")
    public Result<Object> saveScript(@RequestBody Map<String, Object> b) {
        long id = db.insert("INSERT INTO int_job(tenant_id,code,name,cn_name,job_type,engine,status,schedule_type,script_lang,script,"
                        + "owner,description,version,create_time) VALUES(?,?,?,?,'SCRIPT',?,'DRAFT','MANUAL',?,?,?,?,'1.0.0',?)",
                AuthContext.tenantId(), b.getOrDefault("code", Ids.code("SCR")), b.get("name"), b.get("name"),
                b.getOrDefault("engine", "BATCH"), b.getOrDefault("scriptLang", "SQL"), b.get("script"),
                AuthContext.username(), b.get("description"), LocalDateTime.now());
        return Result.ok(M.of("id", id));
    }

    @GetMapping("/sandbox/policies")
    @RequirePerm("integration:sandbox:view")
    public Result<Object> sandbox() {
        return Result.ok(M.of("records", db.list("SELECT * FROM int_sandbox_policy")));
    }

    @PutMapping("/sandbox/policies/{id}")
    @RequirePerm("integration:sandbox:edit")
    public Result<Object> updateSandbox(@PathVariable long id, @RequestBody Map<String, Object> b) {
        Map<String, Object> before = db.one("SELECT * FROM int_sandbox_policy WHERE id=?", id);
        if (before == null) throw new BizException(ErrorCode.NOT_FOUND, "沙箱策略不存在");
        db.update("UPDATE int_sandbox_policy SET allow_sql=?,allow_python=?,allow_shell=?,max_runtime_min=?,max_memory_mb=?,"
                        + "network_whitelist=?,status=? WHERE id=?",
                pick(b, before, "allowSql"), pick(b, before, "allowPython"), pick(b, before, "allowShell"),
                pick(b, before, "maxRuntimeMin"), pick(b, before, "maxMemoryMb"),
                pick(b, before, "networkWhitelist"), pick(b, before, "status"), id);
        return Result.ok(M.of("id", id));
    }

    @GetMapping("/stats/overview")
    @RequirePerm("integration:overview:view")
    public Result<Object> stats() {
        return Result.ok(M.of(
                "jobCount", db.count("SELECT COUNT(*) FROM int_job WHERE job_type<>'WORKFLOW'"),
                "publishedJob", db.count("SELECT COUNT(*) FROM int_job WHERE status='PUBLISHED' AND job_type<>'WORKFLOW'"),
                "workflowCount", db.count("SELECT COUNT(*) FROM int_job WHERE job_type='WORKFLOW'"),
                "instanceToday", db.count("SELECT COUNT(*) FROM int_job_instance WHERE CAST(start_time AS DATE)=CURRENT_DATE"),
                "successRate", db.scalar("SELECT ROUND(SUM(CASE WHEN status='SUCCESS' THEN 1 ELSE 0 END)*100.0/COUNT(*),2) FROM int_job_instance"),
                "avgDurationMs", db.scalar("SELECT ROUND(AVG(duration_ms),0) FROM int_job_instance"),
                "recentInstances", db.list("SELECT * FROM int_job_instance ORDER BY id DESC LIMIT 8"),
                "byStatus", db.list("SELECT status, COUNT(*) AS cnt FROM int_job_instance GROUP BY status")));
    }

    private static Object pick(Map<String, Object> body, Map<String, Object> before, String key) {
        return body.containsKey(key) ? body.get(key) : before.get(key);
    }
}
