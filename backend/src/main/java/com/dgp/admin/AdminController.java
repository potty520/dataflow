package com.dgp.admin;

import com.dgp.common.*;
import com.dgp.security.AuthContext;
import com.dgp.security.RequirePerm;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.lang.management.ManagementFactory;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** dgp-admin:8093：字典、参数、日志、监控健康、API 清单。 */
@RestController
@RequestMapping("/api/v1/admin")
public class AdminController {

    private final Db db;
    private final RequestMappingHandlerMapping mappings;

    public AdminController(Db db, RequestMappingHandlerMapping mappings) {
        this.db = db;
        this.mappings = mappings;
    }

    /** 免权限探针：前端顶栏只用来判断"后端是否可达"，不暴露任何业务数据。 */
    @GetMapping("/ping")
    public Result<Object> ping() {
        long t0 = System.nanoTime();
        boolean dbOk;
        try {
            dbOk = db.scalar("SELECT 1") != null;
        } catch (Exception e) {
            dbOk = false;
        }
        long dbMs = (System.nanoTime() - t0) / 1_000_000;
        return Result.ok(M.of("status", dbOk ? "UP" : "DEGRADED", "version",
                db.scalar("SELECT config_value FROM sys_config WHERE config_key='platform.version'"),
                "uptimeSeconds", ManagementFactory.getRuntimeMXBean().getUptime() / 1000,
                "dbLatencyMs", dbMs, "time", LocalDateTime.now()));
    }

    @GetMapping("/dicts")
    @RequirePerm("sys:dict:view")
    public Result<Object> dicts() {
        List<Map<String, Object>> out = new ArrayList<>(db.list("SELECT dict_code, dict_name, items FROM sys_dict ORDER BY id"));
        for (Map<String, Object> d : out) {
            String items = d.get("items") == null ? null : String.valueOf(d.get("items"));
            if (items != null && items.trim().startsWith("[")) {
                try {
                    d.put("items", JsonUtil.mapper().readValue(items, List.class));
                } catch (Exception ignore) {
                    // 保持原始字符串
                }
            }
        }
        return Result.ok(M.of("records", out));
    }

    @PostMapping("/dicts")
    @RequirePerm("sys:dict:edit")
    public Result<Object> createDict(@RequestBody Map<String, Object> b) {
        String code = Req.str(b.get("dictCode"));
        if (Req.blank(code)) throw new BizException(ErrorCode.PARAM_INVALID, "dictCode 不能为空");
        String items = JsonUtil.toJson(b.getOrDefault("items", List.of()));
        db.update("DELETE FROM sys_dict WHERE dict_code=?", code);
        long id = db.insert("INSERT INTO sys_dict(dict_code,dict_name,items,update_time,create_time) VALUES(?,?,?,?,?)",
                code, b.get("dictName"), items, LocalDateTime.now(), LocalDateTime.now());
        return Result.ok(M.of("created", true, "id", id, "dictCode", code));
    }

    @GetMapping("/configs")
    @RequirePerm("sys:config:view")
    public Result<Object> configs() {
        return Result.ok(M.of("records", db.list("SELECT * FROM sys_config ORDER BY config_key")));
    }

    @PutMapping("/configs/{key}")
    @RequirePerm("sys:config:edit")
    public Result<Object> updateConfig(@PathVariable String key, @RequestBody Map<String, Object> b) {
        Object raw = b.get("configValue");
        if (raw == null) throw new BizException(ErrorCode.PARAM_INVALID, "configValue 不能为空");
        String value = String.valueOf(raw);
        if (key.endsWith("weight")) {
            Map<String, Object> m = JsonUtil.parse(value);
            if (m.isEmpty()) throw new BizException(ErrorCode.PARAM_INVALID, "权重配置必须是 JSON 对象");
            double sum = 0;
            for (Map.Entry<String, Object> e : m.entrySet()) {
                Double d = Db.num(e.getValue());
                if (d == null) throw new BizException(ErrorCode.PARAM_INVALID, "权重必须是数字：" + e.getKey());
                sum += d;
            }
            if (Math.abs(sum - 1.0) > 0.001) {
                throw new BizException(ErrorCode.PARAM_INVALID, "权重之和必须为 1.0，当前为 " + sum);
            }
        }
        if (db.one("SELECT config_key FROM sys_config WHERE config_key=?", key) == null) {
            db.insert("INSERT INTO sys_config(config_key,config_value,description,update_time) VALUES(?,?,?,?)",
                    key, value, "运行时新增", LocalDateTime.now());
        } else {
            db.update("UPDATE sys_config SET config_value=?,update_time=? WHERE config_key=?", value, LocalDateTime.now(), key);
        }
        return Result.ok(M.of("configKey", key, "configValue", value));
    }

    @GetMapping("/logs/operation")
    @RequirePerm("sys:log:view")
    public Result<Object> operationLogs(@RequestParam(defaultValue = "1") int pageNum,
                                        @RequestParam(defaultValue = "20") int pageSize) {
        return Result.ok(db.page("SELECT * FROM sec_audit_log", "id DESC", pageNum, pageSize).toMap());
    }

    @GetMapping("/logs/login")
    @RequirePerm("sys:log:view")
    public Result<Object> loginLogs(@RequestParam(defaultValue = "1") int pageNum,
                                    @RequestParam(defaultValue = "20") int pageSize) {
        return Result.ok(db.page("SELECT * FROM sys_login_log", "id DESC", pageNum, pageSize).toMap());
    }

    /**
     * 服务视图：单进程形态下的逻辑模块清单。状态与延迟取自真实数据库探针，
     * 不再使用 Math.random() 伪造监控数据（原实现 status 恒 UP、latency 随机）。
     */
    @GetMapping("/monitor/services")
    @RequirePerm("sys:monitor:view")
    public Result<Object> services() {
        Object[][] svc = {
                {"dgp-gateway", 8080, "路由/鉴权/限流", "metadata"},
                {"dgp-auth", 8081, "登录认证/租户/RBAC", "auth"},
                {"dgp-metadata", 8082, "元数据/血缘", "metadata"},
                {"dgp-model", 8083, "建模/发布/DDL", "model"},
                {"dgp-standard", 8084, "数据元/映射/落地", "standard"},
                {"dgp-integration", 8085, "数据源/ETL/脚本", "integration"},
                {"dgp-workflow", 8086, "工作流/调度实例", "workflow"},
                {"dgp-quality", 8087, "规则/稽核/闭环", "quality"},
                {"dgp-security", 8088, "敏感/脱敏/审计", "security"},
                {"dgp-asset", 8089, "资产目录/地图", "asset"},
                {"dgp-service", 8090, "API 生命周期/开放通道", "service"},
                {"dgp-industry", 8091, "行业包", "industry"},
                {"dgp-report", 8092, "报告生成/导出", "report"},
                {"dgp-admin", 8093, "字典/参数/监控", "admin"}
        };
        long t0 = System.nanoTime();
        boolean up;
        try {
            up = db.scalar("SELECT 1") != null;
        } catch (Exception e) {
            up = false;
        }
        int latency = (int) ((System.nanoTime() - t0) / 1_000_000);
        List<Map<String, Object>> out = new ArrayList<>();
        for (Object[] s : svc) {
            out.add(M.of("service", s[0], "port", s[1], "duty", s[2], "module", s[3],
                    "status", up ? "UP" : "DOWN", "latencyMs", latency, "instances", 1, "version", "1.0.0",
                    "note", "单进程形态内的逻辑模块（非独立进程）"));
        }
        return Result.ok(M.of("records", out, "total", out.size()));
    }

    @GetMapping("/monitor/health")
    @RequirePerm("sys:monitor:view")
    public Result<Object> health() {
        return Result.ok(healthBody());
    }

    @GetMapping("/health")
    @RequirePerm("admin:system:monitor")
    public Result<Object> health2() {
        return Result.ok(healthBody());
    }

    private Map<String, Object> healthBody() {
        boolean dbOk;
        long t0 = System.nanoTime();
        try {
            dbOk = db.scalar("SELECT 1") != null;
        } catch (Exception e) {
            dbOk = false;
        }
        long dbLatency = (System.nanoTime() - t0) / 1_000_000;
        long uptime = ManagementFactory.getRuntimeMXBean().getUptime() / 1000;
        Runtime rt = Runtime.getRuntime();
        return M.of("status", dbOk ? "UP" : "DOWN",
                "version", db.scalar("SELECT config_value FROM sys_config WHERE config_key='platform.version'"),
                "uptimeSeconds", uptime,
                "components", List.of(
                        M.of("name", "dgp-boot", "status", "UP", "detail", "Spring Boot 3.2 / JDK " + System.getProperty("java.version")),
                        M.of("name", "database", "status", dbOk ? "UP" : "DOWN", "detail", "探针 " + dbLatency + " ms"),
                        M.of("name", "scheduler", "status", "UP", "detail", "内置调度器（4 线程池）扫描到期作业/稽核/采集"),
                        M.of("name", "engine-batch", "status", "UP", "detail", "本地 JDBC 执行器"),
                        M.of("name", "engine-quality", "status", "UP", "detail", "规则下推 SQL 执行"),
                        M.of("name", "registry", "status", "N/A", "detail", "单进程形态未启用 Nacos"),
                        M.of("name", "cache", "status", "N/A", "detail", "未启用 Redis，使用本地缓存")),
                "jvm", M.of("heapUsedMb", (rt.totalMemory() - rt.freeMemory()) / 1048576,
                        "heapMaxMb", rt.maxMemory() / 1048576,
                        "processors", rt.availableProcessors()),
                "data", M.of("tables", db.count("SELECT COUNT(*) FROM md_table"),
                        "qualityRules", db.count("SELECT COUNT(*) FROM qa_rule"),
                        "apiCount", db.count("SELECT COUNT(*) FROM svc_api"),
                        "auditLogs", db.count("SELECT COUNT(*) FROM sec_audit_log")),
                "timestamp", LocalDateTime.now());
    }

    /** 异步任务查询：不存在的任务返回 404，不再伪造"已完成"。 */
    @GetMapping("/tasks/{taskId}")
    public Result<Object> asyncTask(@PathVariable String taskId) {
        Map<String, Object> t = db.one("SELECT * FROM sys_async_task WHERE task_id=?", taskId);
        if (t == null) throw new BizException(ErrorCode.NOT_FOUND, "任务不存在：" + taskId);
        return Result.ok(t);
    }

    /** API 文档：从 Spring MVC 注册表实时导出，替代静态 OpenAPI 文件。 */
    @GetMapping("/apis")
    @RequirePerm("admin:system:monitor")
    public Result<Object> apiDocs() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map.Entry<RequestMappingInfo, HandlerMethod> e : mappings.getHandlerMethods().entrySet()) {
            for (var pattern : e.getKey().getPatternValues()) {
                String path = pattern;
                var methods = e.getKey().getMethodsCondition().getMethods();
                String method = methods.isEmpty() ? "ANY" : methods.iterator().next().name();
                if (!path.startsWith("/api/") && !path.startsWith("/open/")) continue;
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("method", method);
                m.put("path", path);
                m.put("handler", e.getValue().getBeanType().getSimpleName() + "#" + e.getValue().getMethod().getName());
                var perm = e.getValue().getMethodAnnotation(com.dgp.security.RequirePerm.class);
                if (perm == null) {
                    perm = e.getValue().getBeanType().getAnnotation(com.dgp.security.RequirePerm.class);
                }
                m.put("perm", perm == null ? "登录态" : perm.value());
                String[] seg = path.split("/");
                m.put("domain", path.startsWith("/open/") ? "open" : (seg.length > 3 ? seg[3] : "-"));
                out.add(m);
            }
        }
        out.sort((a, b) -> String.valueOf(a.get("path")).compareTo(String.valueOf(b.get("path"))));
        return Result.ok(M.of("total", out.size(), "records", out));
    }

    @GetMapping("/login-info")
    public Result<Object> loginInfo() {
        return Result.ok(M.of("username", AuthContext.username(), "tenantId", AuthContext.tenantId(),
                "role", AuthContext.get() == null ? null : AuthContext.get().getRoleCode()));
    }
}
