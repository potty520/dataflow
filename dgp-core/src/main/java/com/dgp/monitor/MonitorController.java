package com.dgp.monitor;

import com.dgp.common.M;
import com.dgp.common.Result;
import com.dgp.security.RequirePerm;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** 运维监控接口。 */
@RestController
@RequestMapping("/api/v1/monitor")
public class MonitorController {

    private final MonitorService monitor;

    public MonitorController(MonitorService monitor) {
        this.monitor = monitor;
    }

    @GetMapping("/overview")
    @RequirePerm("monitor:view")
    public Result<Object> overview() {
        return Result.ok(monitor.overview());
    }

    @PostMapping("/collect")
    @RequirePerm("monitor:edit")
    public Result<Object> collect() {
        return Result.ok(monitor.collectAndEvaluate());
    }

    @GetMapping("/snapshots")
    @RequirePerm("monitor:view")
    public Result<Object> snapshots(@RequestParam(required = false) String metric,
                                    @RequestParam(defaultValue = "50") int limit) {
        return Result.ok(M.of("records", monitor.snapshots(metric, limit)));
    }

    @GetMapping("/alerts")
    @RequirePerm("monitor:view")
    public Result<Object> alerts(@RequestParam(required = false) String status) {
        return Result.ok(M.of("records", monitor.alerts(status)));
    }

    @PostMapping("/alerts/{id}/ack")
    @RequirePerm("monitor:edit")
    public Result<Object> ack(@PathVariable long id) {
        monitor.ack(id);
        return Result.ok(M.of("id", id, "status", "ACK"));
    }

    @GetMapping("/rules")
    @RequirePerm("monitor:view")
    public Result<Object> rules() {
        return Result.ok(M.of("records", monitor.rules()));
    }

    @PostMapping("/rules")
    @RequirePerm("monitor:edit")
    public Result<Object> saveRule(@RequestBody Map<String, Object> b) {
        return Result.ok(monitor.saveRule(b));
    }

    @DeleteMapping("/rules/{id}")
    @RequirePerm("monitor:edit")
    public Result<Object> deleteRule(@PathVariable long id) {
        monitor.deleteRule(id);
        return Result.ok(M.of("deleted", true));
    }
}
