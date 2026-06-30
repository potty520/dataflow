package com.zhangye.dataflow.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhangye.dataflow.common.PageResult;
import com.zhangye.dataflow.common.Result;
import com.zhangye.dataflow.entity.MetaCollectorLog;
import com.zhangye.dataflow.entity.MetaCollectorTask;
import com.zhangye.dataflow.mapper.MetaCollectorLogMapper;
import com.zhangye.dataflow.mapper.MetaCollectorTaskMapper;
import com.zhangye.dataflow.security.SecurityUtils;
import com.zhangye.dataflow.service.MetadataCollector;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api")
public class MetaCollectorController {

    private final MetaCollectorTaskMapper taskMapper;
    private final MetaCollectorLogMapper logMapper;
    private final MetadataCollector metadataCollector;

    public MetaCollectorController(MetaCollectorTaskMapper taskMapper,
                                   MetaCollectorLogMapper logMapper,
                                   MetadataCollector metadataCollector) {
        this.taskMapper = taskMapper;
        this.logMapper = logMapper;
        this.metadataCollector = metadataCollector;
    }

    private Long tid() { return SecurityUtils.getCurrentTenantId(); }

    @GetMapping("/metadata/collector/page")
    public Result<PageResult<MetaCollectorTask>> page(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size,
            @RequestParam(required = false) String taskName) {
        LambdaQueryWrapper<MetaCollectorTask> qw = new LambdaQueryWrapper<MetaCollectorTask>()
                .eq(MetaCollectorTask::getTenantId, tid());
        if (StringUtils.hasText(taskName)) qw.like(MetaCollectorTask::getTaskName, taskName);
        qw.orderByDesc(MetaCollectorTask::getCreateTime);
        Page<MetaCollectorTask> p = taskMapper.selectPage(new Page<>(page, size), qw);
        return Result.ok(PageResult.of(p.getTotal(), page, size, p.getRecords()));
    }

    @PostMapping("/metadata/collector")
    public Result<Void> create(@RequestBody MetaCollectorTask task) {
        task.setTenantId(tid());
        task.setStatus("STOPPED");
        task.setCreateTime(LocalDateTime.now());
        task.setUpdateTime(LocalDateTime.now());
        taskMapper.insert(task);
        return Result.ok();
    }

    @PutMapping("/metadata/collector/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody MetaCollectorTask task) {
        task.setId(id);
        task.setUpdateTime(LocalDateTime.now());
        taskMapper.updateById(task);
        return Result.ok();
    }

    @DeleteMapping("/metadata/collector/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        taskMapper.deleteById(id);
        logMapper.delete(new LambdaQueryWrapper<MetaCollectorLog>().eq(MetaCollectorLog::getTaskId, id));
        return Result.ok();
    }

    @PostMapping("/metadata/collector/{id}/run")
    public Result<MetaCollectorLog> run(@PathVariable Long id) {
        MetaCollectorTask task = taskMapper.selectById(id);
        if (task == null) return Result.fail("任务不存在");
        task.setStatus("RUNNING");
        task.setUpdateTime(LocalDateTime.now());
        taskMapper.updateById(task);
        MetaCollectorLog log = metadataCollector.runCollect(task);
        task.setStatus("STOPPED");
        task.setUpdateTime(LocalDateTime.now());
        taskMapper.updateById(task);
        return Result.ok(log);
    }

    @GetMapping("/metadata/collector/{id}/logs")
    public Result<List<MetaCollectorLog>> logs(@PathVariable Long id) {
        List<MetaCollectorLog> logs = logMapper.selectList(new LambdaQueryWrapper<MetaCollectorLog>()
                .eq(MetaCollectorLog::getTaskId, id)
                .orderByDesc(MetaCollectorLog::getCreateTime));
        return Result.ok(logs);
    }

    @GetMapping("/metadata/collector/log/page")
    public Result<PageResult<MetaCollectorLog>> logPage(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size,
            @RequestParam(required = false) Long taskId,
            @RequestParam(required = false) String status) {
        LambdaQueryWrapper<MetaCollectorLog> qw = new LambdaQueryWrapper<>();
        if (taskId != null) qw.eq(MetaCollectorLog::getTaskId, taskId);
        if (StringUtils.hasText(status)) qw.eq(MetaCollectorLog::getStatus, status);
        qw.orderByDesc(MetaCollectorLog::getCreateTime);
        Page<MetaCollectorLog> p = logMapper.selectPage(new Page<>(page, size), qw);
        return Result.ok(PageResult.of(p.getTotal(), page, size, p.getRecords()));
    }
}
