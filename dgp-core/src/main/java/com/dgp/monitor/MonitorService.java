package com.dgp.monitor;

import com.dgp.common.BizException;
import com.dgp.common.Db;
import com.dgp.common.ErrorCode;
import com.dgp.common.M;
import com.dgp.security.AuthContext;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** P1 运维监控：指标采集、服务探针、告警规则评估。 */
@Service
public class MonitorService {

    private final Db db;

    public MonitorService(Db db) {
        this.db = db;
    }

    @Scheduled(fixedDelay = 60_000, initialDelay = 30_000)
    public void scheduledCollect() {
        try {
            collectAndEvaluate();
        } catch (Exception ignore) {
            // 表未就绪/单次采集失败不阻断
        }
    }

    public Map<String, Object> collectAndEvaluate() {
        Map<String, Object> metrics = collectMetrics();
        for (Map.Entry<String, Object> e : metrics.entrySet()) {
            if (!(e.getValue() instanceof Number n)) continue;
            db.insert("INSERT INTO mon_metric_snapshot(metric,metric_value,unit,collect_time) VALUES(?,?,?,?)",
                    e.getKey(), n.doubleValue(), unit(e.getKey()), LocalDateTime.now());
        }
        evaluate(metrics);
        return metrics;
    }

    public Map<String, Object> collectMetrics() {
        Map<String, Object> out = new LinkedHashMap<>();
        MemoryMXBean mem = ManagementFactory.getMemoryMXBean();
        MemoryUsage heap = mem.getHeapMemoryUsage();
        double heapPct = heap.getMax() > 0 ? (heap.getUsed() * 100d / heap.getMax()) : 0;
        out.put("jvm.heap.percent", round(heapPct));
        out.put("jvm.threads", ManagementFactory.getThreadMXBean().getThreadCount());
        long t0 = System.nanoTime();
        try {
            db.scalar("SELECT 1");
            out.put("db.latency.ms", round((System.nanoTime() - t0) / 1_000_000d));
            out.put("db.up", 1);
        } catch (Exception e) {
            out.put("db.latency.ms", 9999);
            out.put("db.up", 0);
        }
        try {
            out.put("quality.open.issues", db.count(
                    "SELECT COUNT(*) FROM qa_issue WHERE status NOT IN ('CLOSED','IGNORED')"));
        } catch (Exception ignore) {
            out.put("quality.open.issues", 0);
        }
        try {
            out.put("integration.failed.jobs", db.count(
                    "SELECT COUNT(*) FROM int_job_instance WHERE status='FAIL' AND start_time > ?",
                    LocalDateTime.now().minusHours(24)));
        } catch (Exception ignore) {
            out.put("integration.failed.jobs", 0);
        }
        try {
            out.put("bpm.pending.tasks", db.count("SELECT COUNT(*) FROM bpm_task WHERE status='PENDING'"));
        } catch (Exception ignore) {
            out.put("bpm.pending.tasks", 0);
        }
        try {
            out.put("alerts.open", db.count("SELECT COUNT(*) FROM mon_alert WHERE status='OPEN'"));
        } catch (Exception ignore) {
            out.put("alerts.open", 0);
        }
        return out;
    }

    public Map<String, Object> overview() {
        Map<String, Object> metrics = collectMetrics();
        List<Map<String, Object>> services = new ArrayList<>();
        services.add(service("数据库", "dgp-db", num(metrics, "db.up") >= 1 ? "UP" : "DOWN",
                num(metrics, "db.latency.ms"), "连接/查询探针"));
        services.add(service("数据质量", "dgp-quality", "UP", null,
                "未闭环问题 " + (long) num(metrics, "quality.open.issues")));
        services.add(service("数据集成", "dgp-integration", num(metrics, "integration.failed.jobs") > 0 ? "WARN" : "UP", null,
                "24h 失败实例 " + (long) num(metrics, "integration.failed.jobs")));
        services.add(service("BPM 审批", "dgp-bpm", "UP", null,
                "待办 " + (long) num(metrics, "bpm.pending.tasks")));
        services.add(service("调度引擎", "dgp-workflow", "UP", null, "内置调度器运行中"));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("metrics", metrics);
        out.put("services", services);
        out.put("openAlerts", db.count("SELECT COUNT(*) FROM mon_alert WHERE status='OPEN'"));
        out.put("snapshots", snapshots(null, 20));
        return out;
    }

    public List<Map<String, Object>> snapshots(String metric, int limit) {
        if (metric == null || metric.isBlank()) {
            return db.list("SELECT * FROM mon_metric_snapshot ORDER BY id DESC LIMIT ?", Math.min(limit, 500));
        }
        return db.list("SELECT * FROM mon_metric_snapshot WHERE metric=? ORDER BY id DESC LIMIT ?",
                metric, Math.min(limit, 500));
    }

    public List<Map<String, Object>> alerts(String status) {
        if (status == null || status.isBlank()) {
            return db.list("SELECT * FROM mon_alert ORDER BY id DESC LIMIT 200");
        }
        return db.list("SELECT * FROM mon_alert WHERE status=? ORDER BY id DESC LIMIT 200", status);
    }

    public void ack(long id) {
        db.update("UPDATE mon_alert SET status='ACK',ack_by=?,ack_time=? WHERE id=? AND status='OPEN'",
                AuthContext.username(), LocalDateTime.now(), id);
    }

    public List<Map<String, Object>> rules() {
        return db.list("SELECT * FROM mon_alert_rule ORDER BY id");
    }

    public Map<String, Object> saveRule(Map<String, Object> b) {
        String code = Db.str(b.get("code"));
        String metric = Db.str(b.get("metric"));
        if (code == null || code.isBlank() || metric == null || metric.isBlank()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "规则编码与指标不能为空");
        }
        Map<String, Object> exist = db.one("SELECT * FROM mon_alert_rule WHERE code=?", code);
        if (exist == null) {
            long id = db.insert("INSERT INTO mon_alert_rule(tenant_id,code,name,metric,operator,threshold,level,enabled,"
                            + "remark,create_time,update_time) VALUES(?,?,?,?,?,?,?,?,?,?,?)",
                    AuthContext.tenantId(), code, b.getOrDefault("name", code), metric,
                    b.getOrDefault("operator", ">"), b.getOrDefault("threshold", 0),
                    b.getOrDefault("level", "WARN"), b.getOrDefault("enabled", 1), b.get("remark"),
                    LocalDateTime.now(), LocalDateTime.now());
            return M.of("id", id);
        }
        db.update("UPDATE mon_alert_rule SET name=?,metric=?,operator=?,threshold=?,level=?,enabled=?,remark=?,"
                        + "update_time=? WHERE code=?",
                b.containsKey("name") ? b.get("name") : exist.get("name"),
                metric, b.containsKey("operator") ? b.get("operator") : exist.get("operator"),
                b.containsKey("threshold") ? b.get("threshold") : exist.get("threshold"),
                b.containsKey("level") ? b.get("level") : exist.get("level"),
                b.containsKey("enabled") ? b.get("enabled") : exist.get("enabled"),
                b.containsKey("remark") ? b.get("remark") : exist.get("remark"),
                LocalDateTime.now(), code);
        return M.of("id", exist.get("id"));
    }

    public void deleteRule(long id) {
        db.update("DELETE FROM mon_alert_rule WHERE id=?", id);
    }

    private void evaluate(Map<String, Object> metrics) {
        for (Map<String, Object> r : rules()) {
            if (Db.longVal(r.get("enabled"), 0) != 1) continue;
            String metric = String.valueOf(r.get("metric"));
            if (!metrics.containsKey(metric)) continue;
            double value = num(metrics, metric);
            double threshold = num(r, "threshold");
            boolean hit = switch (String.valueOf(r.get("operator"))) {
                case ">=" -> value >= threshold;
                case "<" -> value < threshold;
                case "<=" -> value <= threshold;
                case "=" -> value == threshold;
                default -> value > threshold;
            };
            if (!hit) continue;
            long open = db.count("SELECT COUNT(*) FROM mon_alert WHERE rule_id=? AND status='OPEN'", r.get("id"));
            if (open > 0) continue;
            db.insert("INSERT INTO mon_alert(tenant_id,rule_id,rule_name,metric,metric_value,level,status,message,create_time) "
                            + "VALUES(?,?,?,?,?,?,'OPEN',?,?)",
                    AuthContext.tenantId(), r.get("id"), r.get("name"), metric, value, r.get("level"),
                    r.get("name") + " 触发：" + metric + " = " + value + "（阈值 " + r.get("operator") + " " + threshold + "）",
                    LocalDateTime.now());
        }
    }

    private static Map<String, Object> service(String name, String code, String status, Double latency, String detail) {
        return M.of("name", name, "code", code, "status", status, "latency", latency, "detail", detail);
    }

    private static String unit(String metric) {
        if (metric.endsWith(".percent")) return "%";
        if (metric.endsWith(".ms")) return "ms";
        return "count";
    }

    private static double num(Map<String, Object> m, String key) {
        Object v = m.get(key);
        return v instanceof Number n ? n.doubleValue() : 0d;
    }

    private static double round(double v) {
        return Math.round(v * 100d) / 100d;
    }
}
