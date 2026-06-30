package com.zhangye.dataflow.engine;

import com.zhangye.dataflow.engine.job.DataflowSyncJob;
import com.zhangye.dataflow.engine.job.WorkflowExecutionJob;
import org.quartz.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Service
public class ScheduleEngine {

    private static final Logger log = LoggerFactory.getLogger(ScheduleEngine.class);

    // Job key prefixes for different task types
    private static final String WORKFLOW_GROUP = "WORKFLOW";
    private static final String DATAFLOW_GROUP = "DATAFLOW";
    private static final String QUALITY_GROUP = "QUALITY";

    private final Scheduler scheduler;

    public ScheduleEngine(Scheduler scheduler) {
        this.scheduler = scheduler;
    }

    /**
     * Schedule a workflow execution task
     */
    public void scheduleWorkflow(Long workflowId, Long tenantId, String cronExpr) throws SchedulerException {
        String jobName = "workflow_" + workflowId;
        scheduleJob(jobName, WORKFLOW_GROUP, workflowId, tenantId, cronExpr,
                WorkflowExecutionJob.class, buildWorkflowDataMap(workflowId, tenantId));
    }

    /**
     * Schedule a dataflow/data sync task
     */
    public void scheduleDataflow(Long dataflowId, Long tenantId, String cronExpr) throws SchedulerException {
        String jobName = "dataflow_" + dataflowId;
        scheduleJob(jobName, DATAFLOW_GROUP, dataflowId, tenantId, cronExpr,
                DataflowSyncJob.class, buildDataflowDataMap(dataflowId, tenantId));
    }

    /**
     * Schedule a quality check task
     */
    public void scheduleQualityCheck(Long taskId, Long tenantId, String cronExpr) throws SchedulerException {
        String jobName = "quality_" + taskId;
        scheduleJob(jobName, QUALITY_GROUP, taskId, tenantId, cronExpr,
                WorkflowExecutionJob.class, buildQualityDataMap(taskId, tenantId));
    }

    /**
     * Generic method to schedule a Quartz job
     */
    private void scheduleJob(String jobName, String jobGroup, Long taskId, Long tenantId,
                             String cronExpr, Class<? extends Job> jobClass,
                             Map<String, Object> dataMap) throws SchedulerException {
        // Remove existing job if any
        JobKey jobKey = new JobKey(jobName, jobGroup);
        if (scheduler.checkExists(jobKey)) {
            scheduler.deleteJob(jobKey);
            log.info("Deleted existing job: {}", jobKey);
        }

        JobDetail jobDetail = JobBuilder.newJob(jobClass)
                .withIdentity(jobName, jobGroup)
                .usingJobData(new JobDataMap(dataMap))
                .storeDurably(true)
                .build();

        Trigger trigger = TriggerBuilder.newTrigger()
                .withIdentity(jobName + "_trigger", jobGroup)
                .withSchedule(CronScheduleBuilder.cronSchedule(cronExpr))
                .build();

        scheduler.scheduleJob(jobDetail, trigger);
        log.info("Scheduled job: {} with cron: {}", jobKey, cronExpr);
    }

    /**
     * Unschedule/remove a job
     */
    public boolean unschedule(Long taskId, String taskType) {
        try {
            String jobName = getJobName(taskId, taskType);
            String jobGroup = getJobGroup(taskType);
            JobKey jobKey = new JobKey(jobName, jobGroup);
            if (scheduler.checkExists(jobKey)) {
                scheduler.deleteJob(jobKey);
                log.info("Unscheduled job: {}", jobKey);
                return true;
            }
            return false;
        } catch (SchedulerException e) {
            log.error("Failed to unschedule task: taskId={}, type={}", taskId, taskType, e);
            return false;
        }
    }

    /**
     * Get job status
     */
    public Map<String, Object> getJobStatus(Long taskId, String taskType) {
        Map<String, Object> status = new HashMap<>();
        status.put("taskId", taskId);
        status.put("taskType", taskType);
        try {
            String jobName = getJobName(taskId, taskType);
            String jobGroup = getJobGroup(taskType);
            JobKey jobKey = new JobKey(jobName, jobGroup);

            if (scheduler.checkExists(jobKey)) {
                status.put("scheduled", true);
                JobDetail jobDetail = scheduler.getJobDetail(jobKey);
                Trigger trigger = scheduler.getTriggersOfJob(jobKey).stream().findFirst().orElse(null);
                if (trigger != null) {
                    status.put("nextFireTime", trigger.getNextFireTime());
                    status.put("previousFireTime", trigger.getPreviousFireTime());
                    status.put("triggerState", scheduler.getTriggerState(trigger.getKey()).name());
                }
            } else {
                status.put("scheduled", false);
            }
        } catch (SchedulerException e) {
            status.put("scheduled", false);
            status.put("error", e.getMessage());
        }
        return status;
    }

    /**
     * Trigger a job immediately (run once, doesn't affect schedule)
     */
    public void triggerNow(Long taskId, String taskType, JobDataMap jobData) throws SchedulerException {
        String jobName = getJobName(taskId, taskType);
        String jobGroup = getJobGroup(taskType);
        JobKey jobKey = new JobKey(jobName, jobGroup);
        if (scheduler.checkExists(jobKey)) {
            scheduler.triggerJob(jobKey, jobData);
        } else {
            // Create a one-shot job
            Class<? extends Job> jobClass = determineJobClass(taskType);
            JobDataMap dataMap = jobData != null ? jobData : new JobDataMap();
            dataMap.put("taskId", taskId);

            JobDetail jobDetail = JobBuilder.newJob(jobClass)
                    .withIdentity(jobName + "_oneshot_" + System.currentTimeMillis(), jobGroup)
                    .usingJobData(dataMap)
                    .build();

            Trigger trigger = TriggerBuilder.newTrigger()
                    .withIdentity(jobName + "_oneshot_trigger_" + System.currentTimeMillis(), jobGroup)
                    .startNow()
                    .build();

            scheduler.scheduleJob(jobDetail, trigger);
        }
    }

    private String getJobName(Long taskId, String taskType) {
        if (taskType == null) taskType = "WORKFLOW";
        return taskType.toLowerCase() + "_" + taskId;
    }

    private String getJobGroup(String taskType) {
        if (taskType == null) return WORKFLOW_GROUP;
        switch (taskType.toUpperCase()) {
            case "DATAFLOW": return DATAFLOW_GROUP;
            case "QUALITY": return QUALITY_GROUP;
            default: return WORKFLOW_GROUP;
        }
    }

    private Class<? extends Job> determineJobClass(String taskType) {
        if ("DATAFLOW".equalsIgnoreCase(taskType)) {
            return DataflowSyncJob.class;
        }
        return WorkflowExecutionJob.class;
    }

    private Map<String, Object> buildWorkflowDataMap(Long workflowId, Long tenantId) {
        Map<String, Object> map = new HashMap<>();
        map.put("workflowId", workflowId);
        map.put("tenantId", tenantId);
        map.put("taskType", "WORKFLOW");
        map.put("triggerBy", "SCHEDULER");
        map.put("executeTime", LocalDateTime.now());
        return map;
    }

    private Map<String, Object> buildDataflowDataMap(Long dataflowId, Long tenantId) {
        Map<String, Object> map = new HashMap<>();
        map.put("dataflowId", dataflowId);
        map.put("tenantId", tenantId);
        map.put("taskType", "DATAFLOW");
        map.put("triggerBy", "SCHEDULER");
        map.put("executeTime", LocalDateTime.now());
        return map;
    }

    private Map<String, Object> buildQualityDataMap(Long taskId, Long tenantId) {
        Map<String, Object> map = new HashMap<>();
        map.put("qualityTaskId", taskId);
        map.put("tenantId", tenantId);
        map.put("taskType", "QUALITY");
        map.put("triggerBy", "SCHEDULER");
        map.put("executeTime", LocalDateTime.now());
        return map;
    }
}
