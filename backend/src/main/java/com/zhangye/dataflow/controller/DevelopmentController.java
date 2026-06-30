package com.zhangye.dataflow.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhangye.dataflow.common.PageResult;
import com.zhangye.dataflow.common.Result;
import com.zhangye.dataflow.entity.*;
import com.zhangye.dataflow.mapper.*;
import com.zhangye.dataflow.security.SecurityUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

@RestController
@RequestMapping("/api/development")
public class DevelopmentController {

    private final DevProjectMapper projectMapper;
    private final DevWorkflowMapper workflowMapper;
    private final DevScriptMapper scriptMapper;
    private final DevQualityRuleMapper qualityRuleMapper;
    private final DevQualityTaskMapper qualityTaskMapper;
    private final DevScheduleTaskMapper scheduleTaskMapper;

    public DevelopmentController(DevProjectMapper projectMapper, DevWorkflowMapper workflowMapper,
                                 DevScriptMapper scriptMapper, DevQualityRuleMapper qualityRuleMapper,
                                 DevQualityTaskMapper qualityTaskMapper, DevScheduleTaskMapper scheduleTaskMapper) {
        this.projectMapper = projectMapper;
        this.workflowMapper = workflowMapper;
        this.scriptMapper = scriptMapper;
        this.qualityRuleMapper = qualityRuleMapper;
        this.qualityTaskMapper = qualityTaskMapper;
        this.scheduleTaskMapper = scheduleTaskMapper;
    }

    private Long tid() { return SecurityUtils.getCurrentTenantId(); }

    @GetMapping("/project/list")
    public Result<List<DevProject>> projectList() {
        return Result.ok(projectMapper.selectList(new LambdaQueryWrapper<DevProject>().eq(DevProject::getTenantId, tid())));
    }

    @PostMapping("/project")
    public Result<Void> createProject(@RequestBody DevProject e) {
        e.setTenantId(tid());
        e.setCreateBy(SecurityUtils.getCurrentUsername());
        e.setCreateTime(LocalDateTime.now());
        projectMapper.insert(e);
        return Result.ok();
    }

    @PutMapping("/project/{id}")
    public Result<Void> updateProject(@PathVariable Long id, @RequestBody DevProject e) {
        e.setId(id);
        projectMapper.updateById(e);
        return Result.ok();
    }

    @DeleteMapping("/project/{id}")
    public Result<Void> deleteProject(@PathVariable Long id) {
        projectMapper.deleteById(id);
        return Result.ok();
    }

    @GetMapping("/workflow/page")
    public Result<PageResult<DevWorkflow>> workflowPage(
            @RequestParam(defaultValue = "1") long page, @RequestParam(defaultValue = "10") long size,
            @RequestParam(required = false) String name) {
        LambdaQueryWrapper<DevWorkflow> qw = new LambdaQueryWrapper<DevWorkflow>().eq(DevWorkflow::getTenantId, tid());
        if (StringUtils.hasText(name)) qw.like(DevWorkflow::getName, name);
        Page<DevWorkflow> p = workflowMapper.selectPage(new Page<>(page, size), qw);
        return Result.ok(PageResult.of(p.getTotal(), page, size, p.getRecords()));
    }

    @PostMapping("/workflow")
    public Result<Void> createWorkflow(@RequestBody DevWorkflow e) {
        e.setTenantId(tid());
        e.setCreateBy(SecurityUtils.getCurrentUsername());
        e.setCreateTime(LocalDateTime.now());
        e.setUpdateTime(LocalDateTime.now());
        if (e.getDagJson() == null) e.setDagJson("{\"nodes\":[],\"edges\":[]}");
        workflowMapper.insert(e);
        return Result.ok();
    }

    @PutMapping("/workflow/{id}")
    public Result<Void> updateWorkflow(@PathVariable Long id, @RequestBody DevWorkflow e) {
        e.setId(id);
        e.setUpdateTime(LocalDateTime.now());
        workflowMapper.updateById(e);
        return Result.ok();
    }

    @PostMapping("/workflow/{id}/run")
    public Result<Void> runWorkflow(@PathVariable Long id) {
        DevWorkflow e = workflowMapper.selectById(id);
        e.setStatus("RUNNING");
        e.setUpdateTime(LocalDateTime.now());
        workflowMapper.updateById(e);
        return Result.ok();
    }

    @PostMapping("/workflow/{id}/publish")
    public Result<Void> publishWorkflow(@PathVariable Long id) {
        DevWorkflow e = workflowMapper.selectById(id);
        e.setStatus("PUBLISHED");
        e.setUpdateTime(LocalDateTime.now());
        workflowMapper.updateById(e);
        return Result.ok();
    }

    @DeleteMapping("/workflow/{id}")
    public Result<Void> deleteWorkflow(@PathVariable Long id) {
        workflowMapper.deleteById(id);
        return Result.ok();
    }

    @GetMapping("/script/page")
    public Result<PageResult<DevScript>> scriptPage(
            @RequestParam(defaultValue = "1") long page, @RequestParam(defaultValue = "10") long size,
            @RequestParam(required = false) String name, @RequestParam(required = false) String scriptType) {
        LambdaQueryWrapper<DevScript> qw = new LambdaQueryWrapper<DevScript>().eq(DevScript::getTenantId, tid());
        if (StringUtils.hasText(name)) qw.like(DevScript::getName, name);
        if (StringUtils.hasText(scriptType)) qw.eq(DevScript::getScriptType, scriptType);
        Page<DevScript> p = scriptMapper.selectPage(new Page<>(page, size), qw);
        return Result.ok(PageResult.of(p.getTotal(), page, size, p.getRecords()));
    }

    @PostMapping("/script")
    public Result<Void> createScript(@RequestBody DevScript e) {
        e.setTenantId(tid());
        e.setCreateBy(SecurityUtils.getCurrentUsername());
        e.setCreateTime(LocalDateTime.now());
        e.setUpdateTime(LocalDateTime.now());
        scriptMapper.insert(e);
        return Result.ok();
    }

    @PutMapping("/script/{id}")
    public Result<Void> updateScript(@PathVariable Long id, @RequestBody DevScript e) {
        e.setId(id);
        e.setUpdateTime(LocalDateTime.now());
        scriptMapper.updateById(e);
        return Result.ok();
    }

    @PostMapping("/script/{id}/run")
    public Result<Void> runScript(@PathVariable Long id) {
        DevScript e = scriptMapper.selectById(id);
        e.setStatus("SUCCESS");
        e.setLastRunTime(LocalDateTime.now());
        e.setUpdateTime(LocalDateTime.now());
        scriptMapper.updateById(e);
        return Result.ok();
    }

    @DeleteMapping("/script/{id}")
    public Result<Void> deleteScript(@PathVariable Long id) {
        scriptMapper.deleteById(id);
        return Result.ok();
    }

    @GetMapping("/quality/rule/page")
    public Result<PageResult<DevQualityRule>> rulePage(
            @RequestParam(defaultValue = "1") long page, @RequestParam(defaultValue = "10") long size,
            @RequestParam(required = false) String dimension) {
        LambdaQueryWrapper<DevQualityRule> qw = new LambdaQueryWrapper<DevQualityRule>().eq(DevQualityRule::getTenantId, tid());
        if (StringUtils.hasText(dimension)) qw.eq(DevQualityRule::getDimension, dimension);
        Page<DevQualityRule> p = qualityRuleMapper.selectPage(new Page<>(page, size), qw);
        return Result.ok(PageResult.of(p.getTotal(), page, size, p.getRecords()));
    }

    @GetMapping("/quality/templates")
    public Result<List<Map<String, String>>> templates() {
        String[] dims = {"唯一性", "完整性", "准确性", "规范性", "关联性", "一致性", "及时性"};
        List<Map<String, String>> list = new java.util.ArrayList<>();
        for (String d : dims) {
            Map<String, String> m = new HashMap<>();
            m.put("dimension", d);
            m.put("templateType", d + "_TEMPLATE");
            list.add(m);
        }
        return Result.ok(list);
    }

    @PostMapping("/quality/rule")
    public Result<Void> createRule(@RequestBody DevQualityRule e) {
        e.setTenantId(tid());
        e.setCreateTime(LocalDateTime.now());
        qualityRuleMapper.insert(e);
        return Result.ok();
    }

    @PutMapping("/quality/rule/{id}")
    public Result<Void> updateRule(@PathVariable Long id, @RequestBody DevQualityRule e) {
        e.setId(id);
        qualityRuleMapper.updateById(e);
        return Result.ok();
    }

    @DeleteMapping("/quality/rule/{id}")
    public Result<Void> deleteRule(@PathVariable Long id) {
        qualityRuleMapper.deleteById(id);
        return Result.ok();
    }

    @GetMapping("/quality/task/page")
    public Result<PageResult<DevQualityTask>> taskPage(
            @RequestParam(defaultValue = "1") long page, @RequestParam(defaultValue = "10") long size) {
        LambdaQueryWrapper<DevQualityTask> qw = new LambdaQueryWrapper<DevQualityTask>().eq(DevQualityTask::getTenantId, tid());
        Page<DevQualityTask> p = qualityTaskMapper.selectPage(new Page<>(page, size), qw);
        return Result.ok(PageResult.of(p.getTotal(), page, size, p.getRecords()));
    }

    @PostMapping("/quality/task")
    public Result<Void> createTask(@RequestBody DevQualityTask e) {
        e.setTenantId(tid());
        e.setCreateTime(LocalDateTime.now());
        qualityTaskMapper.insert(e);
        return Result.ok();
    }

    @PostMapping("/quality/task/{id}/execute")
    public Result<Void> executeTask(@PathVariable Long id) {
        DevQualityTask e = qualityTaskMapper.selectById(id);
        e.setStatus("SUCCESS");
        e.setScore(BigDecimal.valueOf(85 + new Random().nextInt(15)));
        e.setIssueCount(new Random().nextInt(10));
        e.setLastRunTime(LocalDateTime.now());
        qualityTaskMapper.updateById(e);
        return Result.ok();
    }

    @DeleteMapping("/quality/task/{id}")
    public Result<Void> deleteTask(@PathVariable Long id) {
        qualityTaskMapper.deleteById(id);
        return Result.ok();
    }

    @GetMapping("/quality/overview")
    public Result<Map<String, Object>> qualityOverview() {
        Long tid = tid();
        Map<String, Object> m = new HashMap<>();
        m.put("ruleCount", qualityRuleMapper.selectCount(new LambdaQueryWrapper<DevQualityRule>().eq(DevQualityRule::getTenantId, tid)));
        m.put("taskCount", qualityTaskMapper.selectCount(new LambdaQueryWrapper<DevQualityTask>().eq(DevQualityTask::getTenantId, tid)));
        m.put("successCount", qualityTaskMapper.selectCount(new LambdaQueryWrapper<DevQualityTask>()
                .eq(DevQualityTask::getTenantId, tid).eq(DevQualityTask::getStatus, "SUCCESS")));
        return Result.ok(m);
    }

    @GetMapping("/schedule/page")
    public Result<PageResult<DevScheduleTask>> schedulePage(
            @RequestParam(defaultValue = "1") long page, @RequestParam(defaultValue = "10") long size,
            @RequestParam(required = false) String name) {
        LambdaQueryWrapper<DevScheduleTask> qw = new LambdaQueryWrapper<DevScheduleTask>().eq(DevScheduleTask::getTenantId, tid());
        if (StringUtils.hasText(name)) qw.like(DevScheduleTask::getName, name);
        Page<DevScheduleTask> p = scheduleTaskMapper.selectPage(new Page<>(page, size), qw);
        return Result.ok(PageResult.of(p.getTotal(), page, size, p.getRecords()));
    }

    @PostMapping("/schedule")
    public Result<Void> createSchedule(@RequestBody DevScheduleTask e) {
        e.setTenantId(tid());
        e.setCreateTime(LocalDateTime.now());
        e.setUpdateTime(LocalDateTime.now());
        scheduleTaskMapper.insert(e);
        return Result.ok();
    }

    @PutMapping("/schedule/{id}")
    public Result<Void> updateSchedule(@PathVariable Long id, @RequestBody DevScheduleTask e) {
        e.setId(id);
        e.setUpdateTime(LocalDateTime.now());
        scheduleTaskMapper.updateById(e);
        return Result.ok();
    }

    @PostMapping("/schedule/{id}/start")
    public Result<Void> startSchedule(@PathVariable Long id) {
        DevScheduleTask e = scheduleTaskMapper.selectById(id);
        e.setStatus("RUNNING");
        e.setLastRunTime(LocalDateTime.now());
        e.setUpdateTime(LocalDateTime.now());
        scheduleTaskMapper.updateById(e);
        return Result.ok();
    }

    @PostMapping("/schedule/{id}/stop")
    public Result<Void> stopSchedule(@PathVariable Long id) {
        DevScheduleTask e = scheduleTaskMapper.selectById(id);
        e.setStatus("STOPPED");
        e.setUpdateTime(LocalDateTime.now());
        scheduleTaskMapper.updateById(e);
        return Result.ok();
    }

    @DeleteMapping("/schedule/{id}")
    public Result<Void> deleteSchedule(@PathVariable Long id) {
        scheduleTaskMapper.deleteById(id);
        return Result.ok();
    }
}
