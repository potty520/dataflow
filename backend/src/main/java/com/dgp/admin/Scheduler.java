package com.dgp.admin;

import com.dgp.common.Db;
import com.dgp.integration.IntegrationService;
import com.dgp.metadata.service.MetaCollectService;
import com.dgp.migration.AggregationService;
import com.dgp.quality.service.QualityEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

/**
 * 内置调度器：到期作业执行、超期问题单升级、定时稽核、元数据定时采集、日配额重置。
 * 生产形态由 DolphinScheduler + XXL-Job 承担，此处为单进程等价实现。
 */
@Component
public class Scheduler {

    private static final Logger log = LoggerFactory.getLogger(Scheduler.class);

    private final Db db;
    private final IntegrationService integrationService;
    private final QualityEngine qualityEngine;
    private final MetaCollectService metaCollectService;
    private final AggregationService aggregationService;

    public Scheduler(Db db, IntegrationService integrationService, QualityEngine qualityEngine,
                     MetaCollectService metaCollectService, AggregationService aggregationService) {
        this.db = db;
        this.integrationService = integrationService;
        this.qualityEngine = qualityEngine;
        this.metaCollectService = metaCollectService;
        this.aggregationService = aggregationService;
    }

    private boolean notReady() {
        try {
            return db.count("SELECT COUNT(*) FROM sys_user") == 0;
        } catch (Exception e) {
            return true;
        }
    }

    /** 聚合汇总定时刷新：每分钟扫描到期的汇总定义（同实例互斥由单实例部署保证，双实例将加分布式锁）。 */
    @Scheduled(fixedDelay = 60000, initialDelay = 75000)
    public void refreshAggregations() {
        if (notReady()) return;
        try {
            int n = aggregationService.refreshDue();
            if (n > 0) log.info("聚合汇总定时刷新完成 {} 个", n);
        } catch (Exception e) {
            log.warn("聚合汇总定时刷新异常: {}", e.getMessage());
        }
    }

    /** 到期 ETL 作业：每次最多取 5 个，避免单轮占用调度线程过久。 */
    @Scheduled(fixedDelay = 60000, initialDelay = 45000)
    public void runDueJobs() {
        if (notReady()) return;
        List<Map<String, Object>> due = db.list(
                "SELECT id,code,cn_name,cron,schedule_type,interval_min FROM int_job WHERE status='PUBLISHED' "
                        + "AND job_type<>'WORKFLOW' AND next_run_time IS NOT NULL AND next_run_time <= ? LIMIT 5", LocalDateTime.now());
        for (Map<String, Object> j : due) {
            long jobId = ((Number) j.get("id")).longValue();
            try {
                Map<String, Object> r = integrationService.runJob(jobId, "CRON", "scheduler");
                log.info("调度作业 {} 执行完成：{}（{} 行）", j.get("code"), r.get("status"), r.get("rowsWritten"));
            } catch (Exception e) {
                // 失败也要推进 next_run_time，否则每个周期都会重试同一个失败作业
                db.update("UPDATE int_job SET next_run_time=? WHERE id=?", nextRun(j.get("cron"), j.get("interval_min")), jobId);
                log.warn("调度作业 {} 失败：{}", j.get("code"), e.getMessage());
            }
        }
    }

    /** 到期元数据采集任务（原实现 md_collect_task 只有手动触发，定时采集从不执行）。 */
    @Scheduled(fixedDelay = 60000, initialDelay = 90000)
    public void runDueCollectTasks() {
        if (notReady()) return;
        List<Map<String, Object>> due = db.list(
                "SELECT id,datasource_id,name,cron,owner FROM md_collect_task WHERE status='ENABLED' "
                        + "AND next_run_time IS NOT NULL AND next_run_time <= ? LIMIT 3", LocalDateTime.now());
        for (Map<String, Object> t : due) {
            long id = ((Number) t.get("id")).longValue();
            try {
                metaCollectService.collect(((Number) t.get("datasource_id")).longValue(), "CRON", "scheduler");
                db.update("UPDATE md_collect_task SET next_run_time=? WHERE id=?", nextRun(t.get("cron"), null), id);
                log.info("定时采集 {} 完成", t.get("name"));
            } catch (Exception e) {
                db.update("UPDATE md_collect_task SET next_run_time=? WHERE id=?", nextRun(t.get("cron"), null), id);
                log.warn("定时采集 {} 失败：{}", t.get("name"), e.getMessage());
            }
        }
    }

    @Scheduled(fixedDelay = 300000, initialDelay = 60000)
    public void markOverdue() {
        if (notReady()) return;
        int n = db.update("UPDATE qa_issue SET status='OVERDUE' WHERE status IN ('DISPATCHED','PROCESSING') "
                + "AND due_time IS NOT NULL AND due_time < ?", LocalDateTime.now());
        if (n > 0) log.info("升级超期问题单 {} 张", n);
    }

    @Scheduled(fixedDelay = 3600000, initialDelay = 120000)
    public void runScheduledChecks() {
        if (notReady()) return;
        List<Map<String, Object>> checks = db.list(
                "SELECT id,name,cron FROM qa_check WHERE status='ENABLED' AND schedule_type='CRON' "
                        + "AND next_run_time IS NOT NULL AND next_run_time <= ? LIMIT 2", LocalDateTime.now());
        for (Map<String, Object> c : checks) {
            long checkId = ((Number) c.get("id")).longValue();
            try {
                Map<String, Object> r = qualityEngine.runCheck(checkId, "CRON", "scheduler");
                db.update("UPDATE qa_check SET next_run_time=? WHERE id=?", nextRun(c.get("cron"), null), checkId);
                log.info("定时稽核 {} 完成，得分 {}", c.get("name"), r.get("score"));
            } catch (Exception e) {
                db.update("UPDATE qa_check SET next_run_time=? WHERE id=?", nextRun(c.get("cron"), null), checkId);
                log.warn("定时稽核 {} 失败：{}", c.get("name"), e.getMessage());
            }
        }
    }

    /** 日配额重置：原实现 used_today 只增不减，长期运行后所有开放调用会被永久拒绝。 */
    @Scheduled(cron = "0 5 0 * * *")
    public void resetDailyQuota() {
        if (notReady()) return;
        int n = db.update("UPDATE svc_quota SET used_today=0");
        log.info("日配额已重置，影响 {} 个应用", n);
    }

    /**
     * 计算下一次执行时间：支持 5/6 段 cron 中"分 时"为固定值的常见表达式（如 0 0 2 * * ?），
     * 其它表达式回退为 intervalMin（默认 60 分钟）。
     */
    private LocalDateTime nextRun(Object cronObj, Object intervalObj) {
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
