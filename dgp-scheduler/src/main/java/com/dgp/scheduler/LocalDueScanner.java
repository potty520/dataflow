package com.dgp.scheduler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 本地引擎：从 core 拉取到期任务并触发（无需 XXL-Job Admin）。
 * 仅当 engine=local 且未启用 XXL 时加载，避免与 XXL 双跑。
 */
@Component
@ConditionalOnExpression("'${dgp.scheduler.engine:local}'.equalsIgnoreCase('local') && !'${dgp.scheduler.xxl.enabled:false}'.equalsIgnoreCase('true')")
public class LocalDueScanner {

    private static final Logger log = LoggerFactory.getLogger(LocalDueScanner.class);
    private final CoreClient core;

    public LocalDueScanner(CoreClient core) {
        this.core = core;
    }

    @Scheduled(fixedDelay = 60_000, initialDelay = 20_000)
    public void scanJobs() {
        try {
            List<Map<String, Object>> due = core.dueJobs(5);
            for (Map<String, Object> j : due) {
                long id = ((Number) j.get("id")).longValue();
                try {
                    core.runJob(id);
                    log.info("调度作业 {} 完成", j.get("code"));
                } catch (Exception e) {
                    log.warn("调度作业 {} 失败：{}", j.get("code"), e.getMessage());
                }
            }
        } catch (Exception e) {
            log.debug("拉取到期作业失败：{}", e.getMessage());
        }
    }

    @Scheduled(fixedDelay = 60_000, initialDelay = 35_000)
    public void scanCollects() {
        try {
            for (Map<String, Object> t : core.dueCollects(3)) {
                long id = ((Number) t.get("id")).longValue();
                try {
                    core.runCollect(id);
                    log.info("定时采集 {} 完成", t.get("name"));
                } catch (Exception e) {
                    log.warn("定时采集 {} 失败：{}", t.get("name"), e.getMessage());
                }
            }
        } catch (Exception e) {
            log.debug("拉取采集任务失败：{}", e.getMessage());
        }
    }

    @Scheduled(fixedDelay = 60_000, initialDelay = 50_000)
    public void scanChecks() {
        try {
            for (Map<String, Object> c : core.dueChecks(2)) {
                long id = ((Number) c.get("id")).longValue();
                try {
                    core.runCheck(id);
                    log.info("定时稽核 {} 完成", c.get("name"));
                } catch (Exception e) {
                    log.warn("定时稽核 {} 失败：{}", c.get("name"), e.getMessage());
                }
            }
        } catch (Exception e) {
            log.debug("拉取稽核任务失败：{}", e.getMessage());
        }
    }

    @Scheduled(fixedDelay = 60_000, initialDelay = 40_000)
    public void refreshAgg() {
        try {
            core.refreshAggregations();
        } catch (Exception e) {
            log.debug("聚合刷新失败：{}", e.getMessage());
        }
    }

    @Scheduled(fixedDelay = 300_000, initialDelay = 60_000)
    public void markOverdue() {
        try {
            core.markOverdue();
        } catch (Exception e) {
            log.debug("超期升级失败：{}", e.getMessage());
        }
    }

    @Scheduled(cron = "0 5 0 * * *")
    public void resetQuota() {
        try {
            core.resetDailyQuota();
            log.info("日配额已重置");
        } catch (Exception e) {
            log.warn("日配额重置失败：{}", e.getMessage());
        }
    }
}
