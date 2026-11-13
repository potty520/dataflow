package com.dgp.integration;

import com.dgp.common.BizException;
import com.dgp.common.Db;
import com.dgp.common.ErrorCode;
import com.dgp.common.JsonUtil;
import com.dgp.common.M;
import com.dgp.common.Req;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/** 集成执行引擎（对应 engine-batch 的本地实现）：作业真实执行、日志采集、工作流编排。 */
@Service
public class IntegrationService {

    private static final Logger log = LoggerFactory.getLogger(IntegrationService.class);

    private final Db db;
    private static final Set<String> ALLOWED = Set.of("insert", "update", "delete", "merge", "select");
    /**
     * ETL 全局执行锁：批量作业会写同一批 DWD/DWS 表，并行执行会互相等锁（MySQL 上表现为
     * lock wait timeout）。这里串行执行，且只等待 2s 就快速失败返回"作业运行中"，不阻塞 HTTP 线程。
     * 工作流内部调用 runJob 时由同一线程重入，仍可正常执行。
     */
    private final java.util.concurrent.locks.ReentrantLock execLock =
            new java.util.concurrent.locks.ReentrantLock(true);

    public IntegrationService(Db db) {
        this.db = db;
    }

    private void acquireExec() {
        try {
            if (!execLock.tryLock(2, java.util.concurrent.TimeUnit.SECONDS)) {
                throw new BizException(ErrorCode.JOB_RUNNING, "已有作业正在执行，请稍后重试");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BizException(ErrorCode.JOB_RUNNING, "等待执行锁被中断");
        }
    }

    public Map<String, Object> runJob(long jobId, String trigger, String operator) {
        acquireExec();
        try {
            return doRunJob(jobId, trigger, operator);
        } finally {
            execLock.unlock();
        }
    }

    /** 沙箱策略：int_sandbox_policy 中 allow_sql=0 时禁止执行（原策略表只是展示，从未生效）。 */
    private void assertSandboxAllowsSql() {
        List<Map<String, Object>> policies = db.list("SELECT * FROM int_sandbox_policy WHERE status='ENABLED'");
        if (policies.isEmpty()) return;
        for (Map<String, Object> p : policies) {
            if (Db.longVal(p.get("allowSql"), 1L) == 0L) {
                throw new BizException(ErrorCode.SQL_INVALID, "沙箱策略【" + p.get("name") + "】禁止 SQL 执行");
            }
        }
    }

    private Map<String, Object> doRunJob(long jobId, String trigger, String operator) {
        Map<String, Object> job = db.one("SELECT * FROM int_job WHERE id=?", jobId);
        if (job == null) throw new BizException(ErrorCode.NOT_FOUND, "作业不存在");
        String script = job.get("script") == null ? "" : String.valueOf(job.get("script"));
        if (script.isBlank()) throw new BizException(ErrorCode.PARAM_INVALID, "作业未配置可执行脚本");
        assertSandboxAllowsSql();
        // 同一作业已有 RUNNING 实例时直接拒绝，避免并发写同一张目标表
        if (db.count("SELECT COUNT(*) FROM int_job_instance WHERE job_id=? AND status='RUNNING'", jobId) > 0) {
            throw new BizException(ErrorCode.JOB_RUNNING, "该作业已有运行中的实例");
        }
        LocalDateTime start = LocalDateTime.now();
        long instanceId = db.insert("INSERT INTO int_job_instance(job_id,job_name,status,trigger_type,start_time,rows_written,operator) "
                + "VALUES(?,?,'RUNNING',?,?,0,?)", jobId, job.get("cnName"), trigger, start, operator);
        log(instanceId, "INFO", "开始执行作业 " + job.get("code") + "，触发方式 " + trigger);
        long rows = 0;
        String status = "SUCCESS";
        String message = "执行成功";
        try {
            List<String> statements = splitStatements(script);
            if (statements.isEmpty()) throw new BizException(ErrorCode.PARAM_INVALID, "脚本中没有可执行语句");
            int idx = 1;
            for (String stmt : statements) {
                String head = firstKeyword(stmt);
                if (!ALLOWED.contains(head)) {
                    throw new BizException(ErrorCode.SQL_INVALID, "沙箱策略禁止该语句：" + head.toUpperCase());
                }
                long t0 = System.currentTimeMillis();
                int affected;
                if ("select".equals(head)) {
                    affected = db.list(stmt).size();
                } else {
                    affected = db.update(stmt);
                }
                rows += Math.max(affected, 0);
                log(instanceId, "INFO", "步骤 " + idx + " 完成，影响 " + affected + " 行，耗时 "
                        + (System.currentTimeMillis() - t0) + " ms：" + (stmt.length() > 160 ? stmt.substring(0, 160) + "..." : stmt));
                idx++;
            }
            message = "执行成功，共影响 " + rows + " 行";
        } catch (BizException e) {
            status = "FAILED";
            message = "执行失败：" + e.getMessage();
            log(instanceId, "ERROR", message);
        } catch (Exception e) {
            status = "FAILED";
            // 原始 SQL 异常只进日志，不回显给调用方
            message = "执行失败：" + e.getClass().getSimpleName() + "，详见实例日志";
            log(instanceId, "ERROR", "执行失败：" + e.getMessage());
        }
        LocalDateTime end = LocalDateTime.now();
        long duration = java.time.Duration.between(start, end).toMillis();
        db.update("UPDATE int_job_instance SET status=?,end_time=?,duration_ms=?,rows_written=?,message=? WHERE id=?",
                status, end, duration, rows, message, instanceId);
        db.update("UPDATE int_job SET last_run_time=?,next_run_time=?,success_count=success_count+CASE WHEN ?='SUCCESS' THEN 1 ELSE 0 END,"
                        + "fail_count=fail_count+CASE WHEN ?='FAILED' THEN 1 ELSE 0 END WHERE id=?",
                end, nextRun(job), status, status, jobId);
        log(instanceId, "INFO", "作业结束，状态 " + status + "，耗时 " + duration + " ms");
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("instanceId", instanceId);
        out.put("jobId", jobId);
        out.put("jobCode", job.get("code"));
        out.put("status", status);
        out.put("rowsWritten", rows);
        out.put("durationMs", duration);
        out.put("message", message);
        out.put("triggerType", trigger);
        return out;
    }

    private LocalDateTime nextRun(Map<String, Object> job) {
        int interval = (int) Db.longVal(job.get("intervalMin"), 60L);
        return LocalDateTime.now().plusMinutes(Math.max(1, interval));
    }

    /**
     * 脚本切分：识别单引号/双引号字符串、行注释、块注释，只有"语句级分号"才切分。
     * （原实现 script.split(";") 遇到字符串内的分号就切坏语句）
     */
    public static List<String> splitStatements(String script) {
        List<String> out = new ArrayList<>();
        if (script == null) return out;
        StringBuilder cur = new StringBuilder();
        boolean inSingle = false, inDouble = false, inLine = false, inBlock = false;
        for (int i = 0; i < script.length(); i++) {
            char c = script.charAt(i);
            char n = i + 1 < script.length() ? script.charAt(i + 1) : '\0';
            if (inLine) {
                if (c == '\n') {
                    inLine = false;
                    cur.append('\n');
                }
                continue;
            }
            if (inBlock) {
                if (c == '*' && n == '/') {
                    inBlock = false;
                    i++;
                }
                continue;
            }
            if (inSingle) {
                cur.append(c);
                if (c == '\'') {
                    if (n == '\'') {
                        cur.append(n);
                        i++;
                    } else {
                        inSingle = false;
                    }
                }
                continue;
            }
            if (inDouble) {
                cur.append(c);
                if (c == '"') {
                    if (n == '"') {
                        cur.append(n);
                        i++;
                    } else {
                        inDouble = false;
                    }
                }
                continue;
            }
            if (c == '-' && n == '-') {
                inLine = true;
                i++;
                continue;
            }
            if (c == '/' && n == '*') {
                inBlock = true;
                i++;
                continue;
            }
            if (c == '\'') {
                inSingle = true;
                cur.append(c);
                continue;
            }
            if (c == '"') {
                inDouble = true;
                cur.append(c);
                continue;
            }
            if (c == ';') {
                String s = cur.toString().trim();
                if (!s.isEmpty()) out.add(s);
                cur.setLength(0);
                continue;
            }
            cur.append(c);
        }
        String tail = cur.toString().trim();
        if (!tail.isEmpty()) out.add(tail);
        return out;
    }

    private static String firstKeyword(String stmt) {
        String s = stmt.trim();
        int i = 0;
        while (i < s.length() && !Character.isLetter(s.charAt(i))) i++;
        int j = i;
        while (j < s.length() && Character.isLetter(s.charAt(j))) j++;
        return s.substring(i, j).toLowerCase();
    }

    private void log(long instanceId, String level, String msg) {
        db.insert("INSERT INTO int_job_log(instance_id,log_time,log_level,message) VALUES(?,?,?,?)",
                instanceId, LocalDateTime.now(), level, msg.length() > 900 ? msg.substring(0, 900) : msg);
    }

    /** 工作流触发：按节点上游依赖拓扑排序后依次执行。 */
    public Map<String, Object> triggerWorkflow(long workflowId, String operator) {
        acquireExec();
        try {
            return doTriggerWorkflow(workflowId, operator);
        } finally {
            execLock.unlock();
        }
    }

    private Map<String, Object> doTriggerWorkflow(long workflowId, String operator) {
        Map<String, Object> wf = db.one("SELECT * FROM int_job WHERE id=? AND job_type='WORKFLOW'", workflowId);
        if (wf == null) throw new BizException(ErrorCode.NOT_FOUND, "工作流不存在");
        List<Map<String, Object>> nodes = JsonUtil.mapper().convertValue(
                JsonUtil.parse(String.valueOf(wf.get("dsl"))).getOrDefault("nodes", List.of()),
                new com.fasterxml.jackson.core.type.TypeReference<List<Map<String, Object>>>() {
                });
        if (nodes == null || nodes.isEmpty()) {
            // 原实现：节点为空仍然返回 SUCCESS、nodeCount=0（"成功但什么都没执行"）
            throw new BizException(ErrorCode.PARAM_INVALID, "工作流未配置任何节点，无法执行");
        }
        List<Map<String, Object>> ordered = topo(nodes);
        LocalDateTime start = LocalDateTime.now();
        long instanceId = db.insert("INSERT INTO int_job_instance(job_id,job_name,status,trigger_type,start_time,rows_written,operator) "
                + "VALUES(?,?,'RUNNING','MANUAL',?,0,?)", workflowId, wf.get("cnName"), start, operator);
        List<Map<String, Object>> executed = new ArrayList<>();
        String status = "SUCCESS";
        long rows = 0;
        int missing = 0;
        for (Map<String, Object> n : ordered) {
            String jobCode = String.valueOf(n.get("jobCode"));
            Map<String, Object> j = db.one("SELECT id,code,cn_name FROM int_job WHERE code=? AND job_type<>'WORKFLOW'", jobCode);
            if (j == null) {
                missing++;
                status = "PARTIAL_FAILED";
                executed.add(M.of("jobCode", jobCode, "status", "SKIPPED", "error", "作业不存在"));
                log(instanceId, "ERROR", "节点 " + jobCode + " 对应的作业不存在，已跳过");
                continue;
            }
            try {
                Map<String, Object> r = runJob(((Number) j.get("id")).longValue(), "WORKFLOW", operator);
                rows += Db.longVal(r.get("rowsWritten"), 0L);
                executed.add(M.of("jobCode", jobCode, "jobName", j.get("cnName"), "status", r.get("status"),
                        "rows", r.get("rowsWritten"), "durationMs", r.get("durationMs")));
                log(instanceId, "INFO", "节点 " + jobCode + " 执行完成");
                if ("FAILED".equals(r.get("status"))) status = "PARTIAL_FAILED";
            } catch (Exception e) {
                status = "FAILED";
                executed.add(M.of("jobCode", jobCode, "jobName", j.get("cnName"), "status", "FAILED", "error", e.getMessage()));
                log(instanceId, "ERROR", "节点 " + jobCode + " 失败：" + e.getMessage());
                break;
            }
        }
        LocalDateTime end = LocalDateTime.now();
        db.update("UPDATE int_job_instance SET status=?,end_time=?,duration_ms=?,rows_written=?,message=? WHERE id=?",
                status, end, java.time.Duration.between(start, end).toMillis(), rows,
                "工作流执行完成：成功 " + (executed.size() - missing) + " 个节点，缺失 " + missing + " 个", instanceId);
        return M.of("instanceId", instanceId, "workflow", wf.get("code"), "status", status,
                "nodeCount", executed.size(), "missingNodes", missing, "nodes", executed, "rowsWritten", rows,
                "durationMs", java.time.Duration.between(start, end).toMillis());
    }

    /** 拓扑排序：检测环与缺失依赖，出现环直接报错（原实现静默乱序执行）。 */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> topo(List<Map<String, Object>> nodes) {
        Map<String, Map<String, Object>> byKey = new LinkedHashMap<>();
        for (Map<String, Object> n : nodes) {
            byKey.put(String.valueOf(n.get("key")), n);
        }
        List<Map<String, Object>> out = new ArrayList<>();
        Set<String> done = new LinkedHashSet<>();
        int guard = 0;
        while (out.size() < byKey.size() && guard++ <= byKey.size() + 1) {
            boolean progressed = false;
            for (Map<String, Object> n : nodes) {
                String key = String.valueOf(n.get("key"));
                if (done.contains(key)) continue;
                List<String> ups = (List<String>) n.getOrDefault("upstream", List.of());
                boolean ready = true;
                for (String up : ups) {
                    if (!byKey.containsKey(up)) {
                        ready = false;
                        log.warn("工作流节点 {} 依赖的上游节点 {} 不存在，按就绪处理", key, up);
                        break;
                    }
                    if (!done.contains(up)) {
                        ready = false;
                        break;
                    }
                }
                if (ready) {
                    out.add(n);
                    done.add(key);
                    progressed = true;
                }
            }
            if (!progressed) break;
        }
        if (out.size() < byKey.size()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "工作流 DAG 存在环或缺失依赖，无法确定执行顺序");
        }
        return out;
    }

    /** DSL → 脚本（图形化转脚本）。 */
    public Map<String, Object> dslToScript(long jobId) {
        Map<String, Object> job = db.one("SELECT * FROM int_job WHERE id=?", jobId);
        if (job == null) throw new BizException(ErrorCode.NOT_FOUND);
        Map<String, Object> dsl = JsonUtil.parse(String.valueOf(job.get("dsl")));
        Object nodesObj = dsl.get("nodes");
        if (!(nodesObj instanceof List<?> nodes) || nodes.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "作业未配置图形化 DSL");
        }
        String source = null;
        String sink = null;
        List<String> filters = new ArrayList<>();
        for (Object o : nodes) {
            if (!(o instanceof Map<?, ?> n)) continue;
            String type = String.valueOf(n.get("type"));
            if ("SOURCE".equals(type)) source = String.valueOf(n.get("table"));
            if ("SINK".equals(type)) sink = String.valueOf(n.get("table"));
            if ("FILTER".equals(type) && n.get("config") != null) filters.add(String.valueOf(n.get("config")));
        }
        if (source == null || sink == null) throw new BizException(ErrorCode.PARAM_INVALID, "DSL 缺少 SOURCE 或 SINK 节点");
        // 修正：sink/source 来自用户 DSL，直接拼接可注入；必须经标识符校验与元数据登记校验
        if (db.one("SELECT id FROM md_table WHERE name=?", Db.identifier(source)) == null) {
            throw new BizException(ErrorCode.METADATA_NOT_FOUND, "源表未登记在元数据中：" + source);
        }
        if (db.one("SELECT id FROM md_table WHERE name=?", Db.identifier(sink)) == null) {
            throw new BizException(ErrorCode.METADATA_NOT_FOUND, "目标表未登记在元数据中：" + sink);
        }
        for (String f : filters) {
            if (f.contains(";") || f.contains("--") || f.contains("/*")) {
                throw new BizException(ErrorCode.PARAM_INVALID, "过滤条件包含非法字符");
            }
        }
        String sql = "INSERT INTO " + sink + " SELECT * FROM " + source
                + (filters.isEmpty() ? "" : " WHERE " + String.join(" AND ", filters));
        db.update("UPDATE int_job SET script_lang='SQL',script=? WHERE id=?", sql, jobId);
        return M.of("jobId", jobId, "scriptLang", "SQL", "script", sql, "converted", true);
    }

    /** 脚本 → DSL（脚本转图形化）。 */
    public Map<String, Object> scriptToDsl(long jobId) {
        Map<String, Object> job = db.one("SELECT * FROM int_job WHERE id=?", jobId);
        if (job == null) throw new BizException(ErrorCode.NOT_FOUND);
        String script = String.valueOf(job.getOrDefault("script", ""));
        java.util.regex.Matcher ins = java.util.regex.Pattern
                .compile("(?i)insert\\s+into\\s+([\\w]+)").matcher(script);
        java.util.regex.Matcher from = java.util.regex.Pattern
                .compile("(?i)\\bfrom\\s+([\\w]+)").matcher(script);
        String sink = ins.find() ? ins.group(1) : "target_table";
        String source = from.find() ? from.group(1) : "source_table";
        Map<String, Object> dsl = new LinkedHashMap<>();
        dsl.put("dslVersion", "1.0");
        dsl.put("nodes", List.of(
                M.of("id", "src", "type", "SOURCE", "name", source, "table", source),
                M.of("id", "m1", "type", "FIELD_MAP", "name", "字段映射"),
                M.of("id", "sink", "type", "SINK", "name", sink, "table", sink)));
        dsl.put("edges", List.of(M.of("from", "src", "to", "m1"), M.of("from", "m1", "to", "sink")));
        db.update("UPDATE int_job SET dsl=? WHERE id=?", JsonUtil.toJson(dsl), jobId);
        return M.of("jobId", jobId, "dsl", dsl, "converted", true);
    }

    public Map<String, Object> compile(long jobId) {
        Map<String, Object> job = db.one("SELECT * FROM int_job WHERE id=?", jobId);
        if (job == null) throw new BizException(ErrorCode.NOT_FOUND);
        String script = String.valueOf(job.getOrDefault("script", ""));
        List<Map<String, Object>> plan = new ArrayList<>();
        int i = 1;
        for (String stmt : splitStatements(script)) {
            plan.add(M.of("step", i++, "operator", firstKeyword(stmt).toUpperCase(),
                    "target", stmt.length() > 60 ? stmt.substring(0, 60) + "..." : stmt,
                    "parallelism", 4, "estimatedCost", "LOW"));
        }
        return M.of("jobId", jobId, "engine", "engine-batch", "plan", plan, "stepCount", plan.size(),
                "compiled", true, "note", "本地形态下推 H2/PG 执行，生产环境由 Spark 提交");
    }

    /** 试运行：不落实例，读取目标表采样返回。 */
    public Map<String, Object> dryRun(long jobId, int limit) {
        Map<String, Object> job = db.one("SELECT * FROM int_job WHERE id=?", jobId);
        if (job == null) throw new BizException(ErrorCode.NOT_FOUND);
        Map<String, Object> dsl = JsonUtil.parse(String.valueOf(job.get("dsl")));
        String sink = null;
        Object nodes = dsl.get("nodes");
        if (nodes instanceof List<?> list) {
            for (Object o : list) {
                if (o instanceof Map<?, ?> n && "SINK".equals(String.valueOf(n.get("type")))) {
                    sink = String.valueOf(n.get("table"));
                }
            }
        }
        if (sink == null) throw new BizException(ErrorCode.PARAM_INVALID, "DSL 未配置目标表，无法采样");
        Db.identifier(sink);
        if (db.one("SELECT id FROM md_table WHERE name=?", sink) == null) {
            throw new BizException(ErrorCode.METADATA_NOT_FOUND, "目标表未登记在元数据中：" + sink);
        }
        int safeLimit = Math.max(1, Math.min(limit, 50));
        List<Map<String, Object>> rows = db.list("SELECT * FROM " + sink + " LIMIT " + safeLimit);
        return M.of("jobId", jobId, "sinkTable", sink, "sampled", rows.size(), "rows", rows, "committed", false);
    }

    public Map<String, Object> validateSql(String sql) {
        if (sql == null || sql.isBlank()) throw new BizException(ErrorCode.PARAM_INVALID, "SQL 不能为空");
        List<String> statements = splitStatements(sql);
        if (statements.size() != 1) {
            throw new BizException(ErrorCode.SQL_INVALID, "一次只能校验/执行一条语句");
        }
        String stmt = statements.get(0);
        String head = firstKeyword(stmt);
        if (!ALLOWED.contains(head)) {
            throw new BizException(ErrorCode.SQL_INVALID, "仅支持 SELECT/INSERT/UPDATE/DELETE/MERGE");
        }
        try {
            db.jdbc().execute("EXPLAIN " + stmt);
            return M.of("valid", true, "statementType", head.toUpperCase(), "message", "语法校验通过");
        } catch (Exception e) {
            throw new BizException(ErrorCode.SQL_INVALID, "语法校验失败：" + e.getMessage());
        }
    }

    public Map<String, Object> formatSql(String sql) {
        if (sql == null) return M.of("formatted", "", "changed", false);
        String[] keywords = {"select", "from", "where", "group by", "order by", "having", "left join",
                "inner join", "join", "union all", "limit", "insert into", "values", "update", "set"};
        String out = sql.replaceAll("\\s+", " ").trim();
        for (String k : keywords) {
            out = out.replaceAll("(?i)\\s+" + k.replace(" ", "\\s+") + "\\s+", "\n" + k.toUpperCase() + " ");
        }
        return M.of("formatted", out, "changed", !out.equals(sql), "dialect", "H2/PostgreSQL");
    }

    /**
     * 自助取数：只读 + 表白名单 + 危险函数拦截 + 强制 LIMIT。
     * （原实现仅 startsWith("select")，可通过 CSVREAD('任意文件') 读取服务器文件）
     */
    public Map<String, Object> adhocQuery(String sql, int limit) {
        com.dgp.service.ServiceExecutionService.assertReadOnly(sql);
        // 表白名单：只允许查询已登记在元数据中的表，杜绝 CSVREAD/系统表/任意表读取
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("(?i)\\b(?:from|join)\\s+([A-Za-z_][A-Za-z0-9_]*)").matcher(sql);
        List<String> tables = new ArrayList<>();
        while (m.find()) tables.add(m.group(1));
        for (String t : tables) {
            if ("dual".equalsIgnoreCase(t)) continue;
            Db.identifier(t);
            if (db.one("SELECT id FROM md_table WHERE name=?", t) == null) {
                throw new BizException(ErrorCode.SQL_INVALID, "仅允许查询已登记的元数据表：" + t);
            }
        }
        String limited = sql.toLowerCase().contains(" limit ") ? sql : sql + " LIMIT " + Math.max(1, Math.min(limit, 500));
        long t0 = System.currentTimeMillis();
        List<Map<String, Object>> rows = db.list(limited);
        return M.of("rows", rows, "rowCount", rows.size(), "costMs", System.currentTimeMillis() - t0,
                "columns", rows.isEmpty() ? List.of() : new ArrayList<>(rows.get(0).keySet()));
    }
}
