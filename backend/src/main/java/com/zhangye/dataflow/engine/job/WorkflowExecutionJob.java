package com.zhangye.dataflow.engine.job;

import com.zhangye.dataflow.engine.ScriptExecutionEngine;
import com.zhangye.dataflow.entity.DevScheduleExecutionLog;
import com.zhangye.dataflow.entity.DevScript;
import com.zhangye.dataflow.entity.DevScriptExecutionLog;
import com.zhangye.dataflow.entity.DevWorkflow;
import com.zhangye.dataflow.mapper.DevScheduleExecutionLogMapper;
import com.zhangye.dataflow.mapper.DevScriptMapper;
import com.zhangye.dataflow.mapper.DevWorkflowMapper;
import com.zhangye.dataflow.service.AlertService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.quartz.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.quartz.QuartzJobBean;

import java.time.LocalDateTime;
import java.util.*;

@DisallowConcurrentExecution
public class WorkflowExecutionJob extends QuartzJobBean {

    private static final Logger log = LoggerFactory.getLogger(WorkflowExecutionJob.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private DevWorkflowMapper workflowMapper;
    @Autowired
    private DevScriptMapper scriptMapper;
    @Autowired
    private ScriptExecutionEngine scriptExecutionEngine;
    @Autowired
    private DevScheduleExecutionLogMapper executionLogMapper;
    @Autowired
    private AlertService alertService;

    @Override
    protected void executeInternal(JobExecutionContext context) throws JobExecutionException {
        JobDataMap dataMap = context.getMergedJobDataMap();
        Long workflowId = dataMap.getLong("workflowId");
        Long tenantId = dataMap.getLong("tenantId");
        String triggerBy = dataMap.getString("triggerBy");
        if (triggerBy == null) triggerBy = "SCHEDULER";

        if (workflowId == null || workflowId == 0) {
            log.warn("WorkflowExecutionJob: workflowId is null or zero, skipping");
            return;
        }

        Long taskId = workflowId;
        String taskType = "WORKFLOW";

        // Create execution log
        DevScheduleExecutionLog execLog = new DevScheduleExecutionLog();
        execLog.setTaskId(taskId);
        execLog.setTaskType(taskType);
        execLog.setTenantId(tenantId);
        execLog.setStatus("RUNNING");
        execLog.setStartTime(LocalDateTime.now());
        execLog.setCreateTime(LocalDateTime.now());
        executionLogMapper.insert(execLog);

        try {
            DevWorkflow workflow = workflowMapper.selectById(workflowId);
            if (workflow == null) {
                log.error("Workflow not found: {}", workflowId);
                markLogFailed(execLog, "Workflow not found: " + workflowId);
                return;
            }

            // Update workflow status
            workflow.setStatus("RUNNING");
            workflowMapper.updateById(workflow);

            // Parse DAG and execute nodes
            List<DagNode> sortedNodes = topologicalSort(workflow.getDagJson());
            StringBuilder outputBuilder = new StringBuilder();
            boolean allSuccess = true;

            // Execute nodes in topological order
            Map<String, Integer> nodeExitCodes = new HashMap<>();
            int maxRetries = workflow.getRetryCount() != null ? workflow.getRetryCount() : 0;
            int retryIntervalSec = workflow.getRetryIntervalSec() != null ? workflow.getRetryIntervalSec() : 300;

            for (DagNode node : sortedNodes) {
                // C3: Check if this is a CONDITION node
                if ("CONDITION".equalsIgnoreCase(node.getType())) {
                    outputBuilder.append("Evaluating condition node: ").append(node.getLabel()).append("\n");
                    String lastNodeStatus = nodeExitCodes.isEmpty() ? "NONE" :
                            nodeExitCodes.values().stream().anyMatch(code -> code != 0) ? "FAILED" : "SUCCESS";
                    outputBuilder.append("Last node status: ").append(lastNodeStatus).append("\n");
                    continue;
                }

                boolean nodeSuccess = false;
                int attempt = 0;
                String nodeError = null;

                // C7: Retry logic
                while (attempt <= maxRetries && !nodeSuccess) {
                    if (attempt > 0) {
                        outputBuilder.append("Retry attempt ").append(attempt)
                                .append(" for node: ").append(node.getLabel()).append("\n");
                        try {
                            Thread.sleep(retryIntervalSec * 1000L);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                        }
                    }
                    attempt++;

                    try {
                        DevScript script = findScriptForNode(node, workflow.getTenantId());
                        if (script != null) {
                            outputBuilder.append("Executing node: ").append(node.getLabel()).append("\n");
                            java.util.concurrent.CompletableFuture<DevScriptExecutionLog> future =
                                    scriptExecutionEngine.executeScript(script, triggerBy);
                            DevScriptExecutionLog result = future.get();
                            nodeExitCodes.put(node.getId(), result.getExitCode());
                            outputBuilder.append("Exit code: ").append(result.getExitCode()).append("\n");
                            if (result.getOutput() != null) {
                                outputBuilder.append(result.getOutput());
                            }
                            if (result.getExitCode() == 0) {
                                nodeSuccess = true;
                            } else {
                                nodeError = result.getErrorLog();
                                outputBuilder.append("Node failed: ").append(nodeError).append("\n");
                            }
                        } else {
                            outputBuilder.append("No script found for node: ")
                                    .append(node.getLabel()).append(" - skipping\n");
                            nodeSuccess = true;
                        }
                    } catch (Exception e) {
                        nodeError = e.getMessage();
                        outputBuilder.append("Node execution error: ").append(nodeError).append("\n");
                        log.error("Node execution failed: node={}", node.getLabel(), e);
                    }
                }

                if (!nodeSuccess) {
                    allSuccess = false;
                    // C7: Check on_failure strategy
                    String onFailure = workflow.getOnFailure() != null ? workflow.getOnFailure() : "TERMINATE";
                    if ("SKIP".equalsIgnoreCase(onFailure)) {
                        outputBuilder.append("Strategy SKIP: continuing after node failure\n");
                    } else if ("TERMINATE".equalsIgnoreCase(onFailure)) {
                        outputBuilder.append("Strategy TERMINATE: stopping workflow\n");
                        break;
                    }
                }
            }

            // Update workflow final status
            String finalStatus = allSuccess ? "SUCCESS" : "FAILED";
            workflow.setStatus(finalStatus);
            workflow.setUpdateTime(LocalDateTime.now());
            workflowMapper.updateById(workflow);

            execLog.setStatus(finalStatus);
            execLog.setOutput(outputBuilder.toString());
            execLog.setEndTime(LocalDateTime.now());
            execLog.setDurationMs(java.time.Duration.between(
                    execLog.getStartTime(), execLog.getEndTime()).toMillis());
            executionLogMapper.updateById(execLog);

            // C8: Send alert notification
            if (workflow.getAlertEnabled() != null && workflow.getAlertEnabled() == 1) {
                alertService.sendConditionalAlert(
                        workflow.getAlertChannels(),
                        workflow.getAlertConditions(),
                        workflow.getName(),
                        workflow.getId(),
                        finalStatus,
                        "工作流执行完成，状态: " + finalStatus
                );
            }

        } catch (Exception e) {
            log.error("Workflow execution failed: workflowId={}", workflowId, e);
            markLogFailed(execLog, e.getMessage());
            try {
                DevWorkflow workflow = workflowMapper.selectById(workflowId);
                if (workflow != null) {
                    workflow.setStatus("FAILED");
                    workflow.setUpdateTime(LocalDateTime.now());
                    workflowMapper.updateById(workflow);
                }
            } catch (Exception ignored) {}
        }
    }

    private List<DagNode> topologicalSort(String dagJson) {
        List<DagNode> result = new ArrayList<>();
        if (dagJson == null || dagJson.trim().isEmpty()) {
            return result;
        }

        try {
            JsonNode root = objectMapper.readTree(dagJson);
            JsonNode nodes = root.get("nodes");
            JsonNode edges = root.get("edges");

            if (nodes == null || !nodes.isArray()) return result;

            // Parse nodes
            Map<String, DagNode> nodeMap = new LinkedHashMap<>();
            for (JsonNode n : nodes) {
                DagNode dagNode = new DagNode();
                dagNode.setId(n.get("id").asText());
                dagNode.setLabel(n.has("label") ? n.get("label").asText() : dagNode.getId());
                dagNode.setType(n.has("type") ? n.get("type").asText() : "transform");
                nodeMap.put(dagNode.getId(), dagNode);
            }

            // Parse edges to build dependency graph
            Map<String, Set<String>> inDegree = new LinkedHashMap<>();
            Map<String, List<String>> adjacency = new LinkedHashMap<>();
            for (String id : nodeMap.keySet()) {
                inDegree.put(id, new HashSet<>());
                adjacency.put(id, new ArrayList<>());
            }

            if (edges != null && edges.isArray()) {
                for (JsonNode e : edges) {
                    String source = e.get("source").asText();
                    String target = e.get("target").asText();
                    if (nodeMap.containsKey(source) && nodeMap.containsKey(target)) {
                        inDegree.get(target).add(source);
                        adjacency.get(source).add(target);
                    }
                }
            }

            // BFS topological sort
            Queue<String> queue = new LinkedList<>();
            for (Map.Entry<String, Set<String>> entry : inDegree.entrySet()) {
                if (entry.getValue().isEmpty()) {
                    queue.offer(entry.getKey());
                }
            }

            while (!queue.isEmpty()) {
                String current = queue.poll();
                result.add(nodeMap.get(current));
                for (String neighbor : adjacency.get(current)) {
                    inDegree.get(neighbor).remove(current);
                    if (inDegree.get(neighbor).isEmpty()) {
                        queue.offer(neighbor);
                    }
                }
            }

            // Add any remaining nodes (in case of cycles)
            for (String id : nodeMap.keySet()) {
                if (result.stream().noneMatch(n -> n.getId().equals(id))) {
                    result.add(nodeMap.get(id));
                }
            }

        } catch (Exception e) {
            log.error("Failed to parse DAG JSON", e);
        }
        return result;
    }

    private DevScript findScriptForNode(DagNode node, Long tenantId) {
        // Try to find script by name matching node label
        List<DevScript> scripts = scriptMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<DevScript>()
                        .eq(DevScript::getTenantId, tenantId)
                        .like(DevScript::getName, node.getLabel()));
        if (scripts != null && !scripts.isEmpty()) {
            return scripts.get(0);
        }
        // Fallback: get any script for the tenant
        scripts = scriptMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<DevScript>()
                        .eq(DevScript::getTenantId, tenantId));
        return (scripts != null && !scripts.isEmpty()) ? scripts.get(0) : null;
    }

    private void markLogFailed(DevScheduleExecutionLog execLog, String error) {
        execLog.setStatus("FAILED");
        execLog.setErrorLog(error);
        execLog.setEndTime(LocalDateTime.now());
        if (execLog.getStartTime() != null) {
            execLog.setDurationMs(java.time.Duration.between(
                    execLog.getStartTime(), execLog.getEndTime()).toMillis());
        }
        try {
            executionLogMapper.updateById(execLog);
        } catch (Exception e) {
            log.error("Failed to update execution log", e);
        }
    }

    static class DagNode {
        private String id;
        private String label;
        private String type;

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getLabel() { return label; }
        public void setLabel(String label) { this.label = label; }
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
    }
}
