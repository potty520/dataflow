package com.dgp.workflow;

import com.dgp.common.*;
import com.dgp.integration.IntegrationService;
import com.dgp.security.AuthContext;
import com.dgp.security.RequirePerm;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** dgp-workflow:8086：工作流定义、实例监控、重试/补数、节点日志。 */
@RestController
@RequestMapping("/api/v1/workflow")
public class WorkflowController {

    private final Db db;
    private final IntegrationService integrationService;

    public WorkflowController(Db db, IntegrationService integrationService) {
        this.db = db;
        this.integrationService = integrationService;
    }

    @GetMapping("/definitions")
    @RequirePerm("workflow:definition:view")
    public Result<Object> definitions() {
        List<Map<String, Object>> list = db.list("SELECT * FROM int_job WHERE job_type='WORKFLOW' ORDER BY id");
        for (Map<String, Object> w : list) {
            w.put("nodeCount", countNodes(w.get("dsl")));
            w.put("lastInstanceStatus", db.scalar("SELECT status FROM int_job_instance WHERE job_id=? ORDER BY id DESC LIMIT 1", w.get("id")));
        }
        return Result.ok(M.of("records", list, "total", list.size()));
    }

    private int countNodes(Object dsl) {
        Object nodes = JsonUtil.parse(String.valueOf(dsl)).get("nodes");
        return nodes instanceof List<?> l ? l.size() : 0;
    }

    @GetMapping("/definitions/{id}")
    @RequirePerm("workflow:definition:view")
    public Result<Object> definition(@PathVariable long id) {
        Map<String, Object> w = db.one("SELECT * FROM int_job WHERE id=? AND job_type='WORKFLOW'", id);
        if (w == null) throw new BizException(ErrorCode.NOT_FOUND);
        Map<String, Object> out = new LinkedHashMap<>(w);
        out.put("dslJson", JsonUtil.parse(String.valueOf(w.get("dsl"))));
        out.put("instances", db.list("SELECT * FROM int_job_instance WHERE job_id=? ORDER BY id DESC LIMIT 10", id));
        return Result.ok(out);
    }

    @PostMapping("/definitions")
    @RequirePerm("workflow:definition:create")
    public Result<Object> create(@RequestBody Map<String, Object> b) {
        String name = Req.str(b.get("name"));
        String cnName = b.get("cnName") == null ? name : String.valueOf(b.get("cnName"));
        long id = db.insert("INSERT INTO int_job(tenant_id,code,name,cn_name,job_type,engine,status,schedule_type,cron,interval_min,"
                        + "priority,timeout_min,retry,industry,dsl,script_lang,owner,description,version,next_run_time,create_time) "
                        + "VALUES(?,?,?,?,'WORKFLOW','BATCH','DRAFT',?,?,?,?,?,?,?,?,'SHELL',?,?,'1.0.0',?,?)",
                AuthContext.tenantId(), b.getOrDefault("code", Ids.code("WF")), name, cnName,
                b.getOrDefault("scheduleType", "MANUAL"), b.get("cron"), b.get("intervalMin"),
                b.getOrDefault("priority", 5), b.getOrDefault("timeoutMin", 180), b.getOrDefault("retry", 1),
                b.getOrDefault("industry", "GOV"),
                b.get("nodes") == null ? "{\"nodes\":[]}" : JsonUtil.toJson(Map.of("nodes", b.get("nodes"))),
                AuthContext.username(), b.get("description"), LocalDateTime.now().plusHours(3), LocalDateTime.now());
        return Result.ok(M.of("id", id));
    }

    @PutMapping("/definitions/{id}")
    @RequirePerm("workflow:definition:edit")
    public Result<Object> update(@PathVariable long id, @RequestBody Map<String, Object> b) {
        Map<String, Object> before = db.one("SELECT * FROM int_job WHERE id=? AND job_type='WORKFLOW'", id);
        if (before == null) throw new BizException(ErrorCode.NOT_FOUND, "工作流不存在");
        db.update("UPDATE int_job SET cn_name=?,cron=?,interval_min=?,priority=?,timeout_min=?,retry=?,dsl=?,description=? WHERE id=?",
                pick(b, before, "cnName"), pick(b, before, "cron"), pick(b, before, "intervalMin"),
                pick(b, before, "priority"), pick(b, before, "timeoutMin"), pick(b, before, "retry"),
                b.containsKey("nodes") ? (b.get("nodes") == null ? null : JsonUtil.toJson(Map.of("nodes", b.get("nodes"))))
                        : before.get("dsl"),
                pick(b, before, "description"), id);
        return Result.ok(M.of("id", id));
    }

    @DeleteMapping("/definitions/{id}")
    @RequirePerm("workflow:definition:delete")
    @Transactional
    public Result<Object> delete(@PathVariable long id) {
        db.update("DELETE FROM int_job_log WHERE instance_id IN (SELECT id FROM int_job_instance WHERE job_id=?)", id);
        db.update("DELETE FROM int_job_instance WHERE job_id=?", id);
        db.update("DELETE FROM int_job WHERE id=? AND job_type='WORKFLOW'", id);
        return Result.ok(M.of("deleted", true));
    }

    @PostMapping("/definitions/{id}/online")
    @RequirePerm("workflow:definition:publish")
    public Result<Object> online(@PathVariable long id) {
        Map<String, Object> w = db.one("SELECT * FROM int_job WHERE id=? AND job_type='WORKFLOW'", id);
        if (w == null) throw new BizException(ErrorCode.NOT_FOUND, "工作流不存在");
        if (countNodes(w.get("dsl")) == 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "工作流未配置节点，不能上线");
        }
        if ("PUBLISHED".equals(String.valueOf(w.get("status")))) {
            throw new BizException(ErrorCode.PARAM_INVALID, "工作流已处于上线状态");
        }
        db.update("UPDATE int_job SET status='PUBLISHED',next_run_time=? WHERE id=?", LocalDateTime.now().plusMinutes(10), id);
        return Result.ok(M.of("id", id, "status", "PUBLISHED"));
    }

    @PostMapping("/definitions/{id}/offline")
    @RequirePerm("workflow:definition:publish")
    public Result<Object> offline(@PathVariable long id) {
        Map<String, Object> w = db.one("SELECT * FROM int_job WHERE id=? AND job_type='WORKFLOW'", id);
        if (w == null) throw new BizException(ErrorCode.NOT_FOUND, "工作流不存在");
        if ("OFFLINE".equals(String.valueOf(w.get("status")))) {
            throw new BizException(ErrorCode.PARAM_INVALID, "工作流已处于下线状态");
        }
        db.update("UPDATE int_job SET status='OFFLINE',next_run_time=NULL WHERE id=?", id);
        return Result.ok(M.of("id", id, "status", "OFFLINE"));
    }

    @PostMapping("/definitions/{id}/trigger")
    @RequirePerm("workflow:definition:trigger")
    public Result<Object> trigger(@PathVariable long id) {
        return Result.ok(integrationService.triggerWorkflow(id, AuthContext.username()));
    }

    @GetMapping("/instances")
    @RequirePerm("workflow:instance:view")
    public Result<Object> instances(@RequestParam(required = false) String status,
                                    @RequestParam(defaultValue = "1") int pageNum,
                                    @RequestParam(defaultValue = "20") int pageSize) {
        String sql = "SELECT i.* FROM int_job_instance i JOIN int_job j ON j.id=i.job_id WHERE j.job_type='WORKFLOW'"
                + (Req.blank(status) ? "" : " AND i.status=?");
        return Result.ok(Req.blank(status)
                ? db.page(sql, "i.id DESC", pageNum, pageSize).toMap()
                : db.page(sql, "i.id DESC", pageNum, pageSize, status).toMap());
    }

    @GetMapping("/instances/{id}")
    @RequirePerm("workflow:instance:view")
    public Result<Object> instance(@PathVariable long id) {
        Map<String, Object> i = db.one("SELECT * FROM int_job_instance WHERE id=?", id);
        if (i == null) throw new BizException(ErrorCode.NOT_FOUND);
        Map<String, Object> out = new LinkedHashMap<>(i);
        List<Map<String, Object>> logs = db.list("SELECT * FROM int_job_log WHERE instance_id=? ORDER BY id", id);
        out.put("logs", logs);
        // 修正：原实现把"同作业的历史实例"当作节点列表返回；这里按 DSL 节点 + 实例日志推导真实节点状态
        out.put("nodes", deriveNodeStatus(i.get("jobId"), logs));
        return Result.ok(out);
    }

    private List<Map<String, Object>> deriveNodeStatus(Object jobId, List<Map<String, Object>> logs) {
        List<Map<String, Object>> nodes = new ArrayList<>();
        Map<String, Object> job = db.one("SELECT dsl FROM int_job WHERE id=?", jobId);
        if (job == null) return nodes;
        Object nodesObj = JsonUtil.parse(String.valueOf(job.get("dsl"))).get("nodes");
        if (!(nodesObj instanceof List<?> list)) return nodes;
        for (Object o : list) {
            if (!(o instanceof Map<?, ?> n)) continue;
            String jobCode = String.valueOf(n.get("jobCode"));
            String status = "PENDING";
            for (Map<String, Object> l : logs) {
                String msg = String.valueOf(l.get("message"));
                if (msg.contains("节点 " + jobCode + " 执行完成")) status = "SUCCESS";
                if (msg.contains("节点 " + jobCode + " 失败") || msg.contains("节点 " + jobCode + " 对应的作业不存在")) {
                    status = "FAILED";
                }
            }
            nodes.add(M.of("key", n.get("key"), "jobCode", jobCode, "status", status));
        }
        return nodes;
    }

    @PostMapping("/instances/{id}/kill")
    @RequirePerm("workflow:instance:kill")
    public Result<Object> kill(@PathVariable long id) {
        Map<String, Object> i = db.one("SELECT * FROM int_job_instance WHERE id=?", id);
        if (i == null) throw new BizException(ErrorCode.NOT_FOUND);
        if (!"RUNNING".equals(String.valueOf(i.get("status")))) {
            throw new BizException(ErrorCode.PARAM_INVALID, "只有运行中的实例可以终止，当前：" + i.get("status"));
        }
        db.update("UPDATE int_job_instance SET status='KILLED',message='人工终止' WHERE id=?", id);
        return Result.ok(M.of("id", id, "status", "KILLED", "interrupted", false,
                "note", "实例状态已置为终止；正在执行的 SQL 语句无法中断，将在语句结束后停止"));
    }

    @PostMapping("/instances/{id}/retry")
    @RequirePerm("workflow:instance:retry")
    public Result<Object> retry(@PathVariable long id) {
        Map<String, Object> i = db.one("SELECT * FROM int_job_instance WHERE id=?", id);
        if (i == null) throw new BizException(ErrorCode.NOT_FOUND);
        if ("RUNNING".equals(String.valueOf(i.get("status")))) {
            throw new BizException(ErrorCode.JOB_RUNNING, "实例仍在运行，不能重试");
        }
        Map<String, Object> r = integrationService.triggerWorkflow(((Number) i.get("jobId")).longValue(),
                AuthContext.username());
        return Result.ok(M.of("originInstanceId", id, "retryInstanceId", r.get("instanceId"), "status", r.get("status")));
    }

    @PostMapping("/instances/backfill")
    @RequirePerm("workflow:instance:backfill")
    public Result<Object> backfill(@RequestBody Map<String, Object> b) {
        Object ids = b.get("instanceIds");
        if (!(ids instanceof List<?> l)) throw new BizException(ErrorCode.PARAM_INVALID, "instanceIds 必须为数组");
        if (l.size() > 50) throw new BizException(ErrorCode.PARAM_INVALID, "单次补数不能超过 50 个实例");
        List<Map<String, Object>> results = new ArrayList<>();
        for (Object o : l) {
            Map<String, Object> i = db.one("SELECT * FROM int_job_instance WHERE id=?", o);
            if (i == null) {
                results.add(M.of("origin", o, "status", "SKIPPED", "error", "实例不存在"));
                continue;
            }
            try {
                Map<String, Object> r = integrationService.triggerWorkflow(((Number) i.get("jobId")).longValue(),
                        AuthContext.username());
                results.add(M.of("origin", o, "newInstance", r.get("instanceId"), "status", r.get("status")));
            } catch (Exception e) {
                results.add(M.of("origin", o, "status", "FAILED", "error", e.getMessage()));
            }
        }
        long ok = results.stream().filter(r -> !"FAILED".equals(r.get("status")) && !"SKIPPED".equals(r.get("status"))).count();
        return Result.ok(M.of("backfilled", ok, "total", l.size(), "results", results));
    }

    @GetMapping("/instances/{id}/nodes/{nodeKey}/logs")
    @RequirePerm("workflow:instance:view")
    public Result<Object> nodeLogs(@PathVariable long id, @PathVariable String nodeKey) {
        if (nodeKey.contains("%") || nodeKey.contains("_")) {
            throw new BizException(ErrorCode.PARAM_INVALID, "节点标识不合法");
        }
        List<Map<String, Object>> logs = db.list(
                "SELECT l.* FROM int_job_log l WHERE l.instance_id=? AND l.message LIKE ? ORDER BY l.id", id, "%" + nodeKey + "%");
        return Result.ok(M.of("nodeKey", nodeKey, "records", logs));
    }

    /**
     * 调度引擎同步状态：本形态没有外部调度引擎（DolphinScheduler 适配层在 README 中登记为差异），
     * 因此如实返回 unsupported，不再伪造 IN_SYNC/driftCount=0。
     */
    @GetMapping("/sync/status")
    @RequirePerm("workflow:definition:view")
    public Result<Object> syncStatus() {
        Map<String, Object> last = db.one("SELECT MAX(end_time) AS last_end FROM int_job_instance i "
                + "JOIN int_job j ON j.id=i.job_id WHERE j.job_type='WORKFLOW'");
        return Result.ok(M.of("engine", "local-scheduler", "syncStatus", "NOT_APPLICABLE",
                "supported", false,
                "detail", "单进程内置调度器，未接入外部调度引擎，无需同步对齐",
                "definitionCount", db.count("SELECT COUNT(*) FROM int_job WHERE job_type='WORKFLOW'"),
                "onlineCount", db.count("SELECT COUNT(*) FROM int_job WHERE job_type='WORKFLOW' AND status='PUBLISHED'"),
                "lastRunTime", last == null ? null : last.get("lastEnd")));
    }

    @PostMapping("/sync/reconcile")
    @RequirePerm("workflow:definition:publish")
    public Result<Object> reconcile() {
        throw new BizException(ErrorCode.METHOD_NOT_ALLOWED,
                "当前形态未接入外部调度引擎，无需对齐操作");
    }

    @GetMapping("/stats/overview")
    @RequirePerm("workflow:overview:view")
    public Result<Object> stats() {
        return Result.ok(M.of(
                "definitionCount", db.count("SELECT COUNT(*) FROM int_job WHERE job_type='WORKFLOW'"),
                "onlineCount", db.count("SELECT COUNT(*) FROM int_job WHERE job_type='WORKFLOW' AND status='PUBLISHED'"),
                "instanceCount", db.count("SELECT COUNT(*) FROM int_job_instance i JOIN int_job j ON j.id=i.job_id WHERE j.job_type='WORKFLOW'"),
                "runningCount", db.count("SELECT COUNT(*) FROM int_job_instance WHERE status='RUNNING'"),
                "successRate", db.scalar("SELECT ROUND(SUM(CASE WHEN i.status='SUCCESS' THEN 1 ELSE 0 END)*100.0/COUNT(*),2) "
                        + "FROM int_job_instance i JOIN int_job j ON j.id=i.job_id WHERE j.job_type='WORKFLOW'"),
                "recent", db.list("SELECT i.* FROM int_job_instance i JOIN int_job j ON j.id=i.job_id WHERE j.job_type='WORKFLOW' ORDER BY i.id DESC LIMIT 8")));
    }

    private static Object pick(Map<String, Object> body, Map<String, Object> before, String key) {
        return body.containsKey(key) ? body.get(key) : before.get(key);
    }
}
