package com.dgp.scheduler;

import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * XXL-Job 处理器：任务参数为业务 ID（作业/采集/稽核/工作流）。
 * 在 XXL-Job Admin 中注册同名 Handler，Cron 与平台作业对齐。
 */
@Component
@ConditionalOnProperty(name = "dgp.scheduler.xxl.enabled", havingValue = "true")
public class XxlJobHandlers {

    private final CoreClient core;

    public XxlJobHandlers(CoreClient core) {
        this.core = core;
    }

    @XxlJob("dgpRunJob")
    public void dgpRunJob() {
        long id = Long.parseLong(requireParam());
        core.runJob(id);
        XxlJobHelper.handleSuccess("job " + id + " done");
    }

    @XxlJob("dgpRunWorkflow")
    public void dgpRunWorkflow() {
        long id = Long.parseLong(requireParam());
        core.runWorkflow(id);
        XxlJobHelper.handleSuccess("workflow " + id + " done");
    }

    @XxlJob("dgpRunCollect")
    public void dgpRunCollect() {
        long id = Long.parseLong(requireParam());
        core.runCollect(id);
        XxlJobHelper.handleSuccess("collect " + id + " done");
    }

    @XxlJob("dgpRunCheck")
    public void dgpRunCheck() {
        long id = Long.parseLong(requireParam());
        core.runCheck(id);
        XxlJobHelper.handleSuccess("check " + id + " done");
    }

    @XxlJob("dgpRefreshAggregations")
    public void dgpRefreshAggregations() {
        core.refreshAggregations();
        XxlJobHelper.handleSuccess("agg refreshed");
    }

    @XxlJob("dgpMarkOverdue")
    public void dgpMarkOverdue() {
        core.markOverdue();
        XxlJobHelper.handleSuccess("overdue marked");
    }

    @XxlJob("dgpResetDailyQuota")
    public void dgpResetDailyQuota() {
        core.resetDailyQuota();
        XxlJobHelper.handleSuccess("quota reset");
    }

    /** 一分钟一次：扫描并执行所有到期 ETL/采集/稽核（可替代 LocalDueScanner）。 */
    @XxlJob("dgpScanDueAll")
    public void dgpScanDueAll() {
        int jobs = 0, collects = 0, checks = 0;
        for (var j : core.dueJobs(10)) {
            core.runJob(((Number) j.get("id")).longValue());
            jobs++;
        }
        for (var t : core.dueCollects(5)) {
            core.runCollect(((Number) t.get("id")).longValue());
            collects++;
        }
        for (var c : core.dueChecks(5)) {
            core.runCheck(((Number) c.get("id")).longValue());
            checks++;
        }
        core.refreshAggregations();
        XxlJobHelper.handleSuccess("jobs=" + jobs + ",collects=" + collects + ",checks=" + checks);
    }

    private static String requireParam() {
        String p = XxlJobHelper.getJobParam();
        if (p == null || p.isBlank()) {
            throw new IllegalArgumentException("XXL 任务参数不能为空（业务 ID）");
        }
        return p.trim();
    }
}
