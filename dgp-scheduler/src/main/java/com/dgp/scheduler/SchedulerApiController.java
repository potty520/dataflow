package com.dgp.scheduler;

import com.dgp.common.M;
import com.dgp.common.Result;
import com.dgp.security.RequirePerm;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 对外调度 API（经网关 /api/v1/scheduler/**）。 */
@RestController
@RequestMapping("/api/v1/scheduler")
public class SchedulerApiController {

    private final CoreClient core;
    private final String engine;
    private final boolean xxlEnabled;
    private final String xxlAdmin;

    public SchedulerApiController(
            CoreClient core,
            @Value("${dgp.scheduler.engine:local}") String engine,
            @Value("${dgp.scheduler.xxl.enabled:false}") boolean xxlEnabled,
            @Value("${xxl.job.admin.addresses:}") String xxlAdmin) {
        this.core = core;
        this.engine = engine;
        this.xxlEnabled = xxlEnabled;
        this.xxlAdmin = xxlAdmin;
    }

    @GetMapping("/status")
    @RequirePerm("workflow:definition:view")
    public Result<Object> status() {
        return Result.ok(M.of(
                "engine", xxlEnabled ? "xxl-job" : engine,
                "xxlEnabled", xxlEnabled,
                "xxlAdmin", xxlAdmin,
                "handlers", List.of(
                        "dgpRunJob", "dgpRunWorkflow", "dgpRunCollect", "dgpRunCheck",
                        "dgpRefreshAggregations", "dgpMarkOverdue", "dgpResetDailyQuota",
                        "dgpScanDueAll"),
                "supported", true,
                "detail", xxlEnabled
                        ? "已接入 XXL-Job；可按业务 ID 注册 Handler，或注册 dgpScanDueAll 每分钟扫描到期任务；父子任务可做 DAG"
                        : "本地到期扫描引擎（调用 dgp-core 内部 API）；生产建议 DGP_XXL_ENABLED=true 且 DGP_SCHEDULER_ENGINE=xxl"));
    }

    @GetMapping("/jobs")
    @RequirePerm("workflow:definition:view")
    public Result<Object> jobs() {
        List<Map<String, Object>> published = core.publishedJobs();
        List<Map<String, Object>> advice = new ArrayList<>();
        for (Map<String, Object> j : published) {
            Map<String, Object> row = new LinkedHashMap<>(j);
            String type = String.valueOf(j.get("jobType"));
            row.put("xxlHandler", "WORKFLOW".equalsIgnoreCase(type) ? "dgpRunWorkflow" : "dgpRunJob");
            row.put("xxlParam", String.valueOf(j.get("id")));
            row.put("suggestedCron", j.get("cron"));
            advice.add(row);
        }
        return Result.ok(M.of("total", advice.size(), "records", advice));
    }

    @PostMapping("/jobs/{id}/trigger")
    @RequirePerm("workflow:definition:publish")
    public Result<Object> trigger(@PathVariable long id, @RequestParam(defaultValue = "false") boolean workflow) {
        return Result.ok(workflow ? core.runWorkflow(id) : core.runJob(id));
    }
}
