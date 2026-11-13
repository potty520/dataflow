package com.dgp.admin;

import com.dgp.common.*;
import com.dgp.integration.IntegrationService;
import com.dgp.metadata.service.MetaCollectService;
import com.dgp.migration.AggregationService;
import com.dgp.quality.service.QualityEngine;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

/**
 * 供 dgp-scheduler / XXL-Job 执行器调用的内部调度 API（共享 X-Scheduler-Token）。
 */
@RestController
@RequestMapping("/api/v1/internal/scheduler")
public class InternalSchedulerController {

    private final Db db;
    private final IntegrationService integrationService;
    private final QualityEngine qualityEngine;
    private final MetaCollectService metaCollectService;
    private final AggregationService aggregationService;

    public InternalSchedulerController(Db db, IntegrationService integrationService, QualityEngine qualityEngine,
                                       MetaCollectService metaCollectService, AggregationService aggregationService) {
        this.db = db;
        this.integrationService = integrationService;
        this.qualityEngine = qualityEngine;
        this.metaCollectService = metaCollectService;
        this.aggregationService = aggregationService;
    }

    @GetMapping("/due/jobs")
    public Result<Object> dueJobs(@RequestParam(defaultValue = "5") int limit) {
        List<Map<String, Object>> due = db.list(
                "SELECT id,code,cn_name,cron,schedule_type,interval_min,tenant_id FROM int_job WHERE status='PUBLISHED' "
                        + "AND job_type<>'WORKFLOW' AND next_run_time IS NOT NULL AND next_run_time <= ? LIMIT ?",
                LocalDateTime.now(), Math.max(1, Math.min(limit, 50)));
        return Result.ok(M.of("records", due));
    }

    @GetMapping("/due/collects")
    public Result<Object> dueCollects(@RequestParam(defaultValue = "3") int limit) {
        List<Map<String, Object>> due = db.list(
                "SELECT id,datasource_id,name,cron,owner,tenant_id FROM md_collect_task WHERE status='ENABLED' "
                        + "AND next_run_time IS NOT NULL AND next_run_time <= ? LIMIT ?",
                LocalDateTime.now(), Math.max(1, Math.min(limit, 20)));
        return Result.ok(M.of("records", due));
    }

    @GetMapping("/due/checks")
    public Result<Object> dueChecks(@RequestParam(defaultValue = "2") int limit) {
        List<Map<String, Object>> due = db.list(
                "SELECT id,name,cron,tenant_id FROM qa_check WHERE status='ENABLED' AND schedule_type='CRON' "
                        + "AND next_run_time IS NOT NULL AND next_run_time <= ? LIMIT ?",
                LocalDateTime.now(), Math.max(1, Math.min(limit, 20)));
        return Result.ok(M.of("records", due));
    }

    @GetMapping("/jobs/published")
    public Result<Object> publishedJobs() {
        return Result.ok(M.of("records", db.list(
                "SELECT id,code,cn_name,job_type,cron,schedule_type,interval_min,status,tenant_id FROM int_job "
                        + "WHERE status='PUBLISHED' ORDER BY id")));
    }

    @PostMapping("/jobs/{id}/run")
    public Result<Object> runJob(@PathVariable long id,
                                 @RequestParam(defaultValue = "CRON") String trigger,
                                 @RequestParam(defaultValue = "scheduler") String operator) {
        Map<String, Object> job = db.one("SELECT * FROM int_job WHERE id=?", id);
        if (job == null) throw new BizException(ErrorCode.NOT_FOUND, "作业不存在");
        try {
            Map<String, Object> r = integrationService.runJob(id, trigger, operator);
            db.update("UPDATE int_job SET next_run_time=? WHERE id=?",
                    nextRun(job.get("cron"), job.get("intervalMin")), id);
            return Result.ok(r);
        } catch (RuntimeException e) {
            db.update("UPDATE int_job SET next_run_time=? WHERE id=?",
                    nextRun(job.get("cron"), job.get("intervalMin")), id);
            throw e;
        }
    }

    @PostMapping("/workflows/{id}/run")
    public Result<Object> runWorkflow(@PathVariable long id,
                                      @RequestParam(defaultValue = "scheduler") String operator) {
        return Result.ok(integrationService.triggerWorkflow(id, operator));
    }

    @PostMapping("/collects/{id}/run")
    public Result<Object> runCollect(@PathVariable long id) {
        Map<String, Object> t = db.one("SELECT * FROM md_collect_task WHERE id=?", id);
        if (t == null) throw new BizException(ErrorCode.NOT_FOUND, "采集任务不存在");
        try {
            Map<String, Object> r = metaCollectService.collect(
                    ((Number) t.get("datasourceId")).longValue(), "CRON", "scheduler");
            db.update("UPDATE md_collect_task SET next_run_time=? WHERE id=?", nextRun(t.get("cron"), null), id);
            return Result.ok(r);
        } catch (RuntimeException e) {
            db.update("UPDATE md_collect_task SET next_run_time=? WHERE id=?", nextRun(t.get("cron"), null), id);
            throw e;
        }
    }

    @PostMapping("/checks/{id}/run")
    public Result<Object> runCheck(@PathVariable long id) {
        Map<String, Object> c = db.one("SELECT * FROM qa_check WHERE id=?", id);
        if (c == null) throw new BizException(ErrorCode.NOT_FOUND, "稽核任务不存在");
        try {
            Map<String, Object> r = qualityEngine.runCheck(id, "CRON", "scheduler");
            db.update("UPDATE qa_check SET next_run_time=? WHERE id=?", nextRun(c.get("cron"), null), id);
            return Result.ok(r);
        } catch (RuntimeException e) {
            db.update("UPDATE qa_check SET next_run_time=? WHERE id=?", nextRun(c.get("cron"), null), id);
            throw e;
        }
    }

    @PostMapping("/aggregations/refresh-due")
    public Result<Object> refreshAggregations() {
        int n = aggregationService.refreshDue();
        return Result.ok(M.of("refreshed", n));
    }

    @PostMapping("/issues/mark-overdue")
    public Result<Object> markOverdue() {
        int n = db.update("UPDATE qa_issue SET status='OVERDUE' WHERE status IN ('DISPATCHED','PROCESSING') "
                + "AND due_time IS NOT NULL AND due_time < ?", LocalDateTime.now());
        return Result.ok(M.of("updated", n));
    }

    @PostMapping("/quota/reset-daily")
    public Result<Object> resetDailyQuota() {
        int n = db.update("UPDATE svc_quota SET used_today=0");
        return Result.ok(M.of("updated", n));
    }

    static LocalDateTime nextRun(Object cronObj, Object intervalObj) {
        String cron = cronObj == null ? null : String.valueOf(cronObj).trim();
        if (cron != null && !cron.isBlank() && !"null".equals(cron)) {
            String[] parts = cron.split("\\s+");
            if (parts.length >= 2) {
                String minutePart = parts[0];
                String hourPart = parts[1];
                if (minutePart.matches("\\d{1,2}") && hourPart.matches("\\d{1,2}")) {
                    int minute = Integer.parseInt(minutePart);
                    int hour = Integer.parseInt(hourPart);
                    if (minute < 60 && hour < 24) {
                        LocalDateTime now = LocalDateTime.now();
                        LocalDateTime candidate = now.toLocalDate().atTime(LocalTime.of(hour, minute));
                        if (!candidate.isAfter(now)) candidate = candidate.plusDays(1);
                        return candidate;
                    }
                }
            }
        }
        int interval = (int) Db.longVal(intervalObj, 60L);
        return LocalDateTime.now().plusMinutes(Math.max(1, interval));
    }
}
