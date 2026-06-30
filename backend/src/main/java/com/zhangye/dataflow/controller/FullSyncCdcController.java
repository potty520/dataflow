package com.zhangye.dataflow.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhangye.dataflow.common.PageResult;
import com.zhangye.dataflow.common.Result;
import com.zhangye.dataflow.entity.AggCdcTask;
import com.zhangye.dataflow.entity.AggFullSync;
import com.zhangye.dataflow.mapper.AggCdcTaskMapper;
import com.zhangye.dataflow.mapper.AggFullSyncMapper;
import com.zhangye.dataflow.security.SecurityUtils;
import com.zhangye.dataflow.service.OperLogService;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/aggregation")
public class FullSyncCdcController {

    private final AggFullSyncMapper fullSyncMapper;
    private final AggCdcTaskMapper cdcTaskMapper;
    private final OperLogService operLogService;

    public FullSyncCdcController(AggFullSyncMapper fullSyncMapper, AggCdcTaskMapper cdcTaskMapper,
                                 OperLogService operLogService) {
        this.fullSyncMapper = fullSyncMapper;
        this.cdcTaskMapper = cdcTaskMapper;
        this.operLogService = operLogService;
    }

    @GetMapping("/fullsync/page")
    public Result<PageResult<AggFullSync>> fullSyncPage(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size,
            @RequestParam(required = false) String name) {
        LambdaQueryWrapper<AggFullSync> qw = new LambdaQueryWrapper<>();
        qw.eq(AggFullSync::getTenantId, SecurityUtils.getCurrentTenantId());
        if (StringUtils.hasText(name)) {
            qw.like(AggFullSync::getName, name);
        }
        Page<AggFullSync> p = fullSyncMapper.selectPage(new Page<>(page, size), qw);
        return Result.ok(PageResult.of(p.getTotal(), page, size, p.getRecords()));
    }

    @PostMapping("/fullsync")
    public Result<Void> createFullSync(@RequestBody AggFullSync task, HttpServletRequest request) {
        task.setTenantId(SecurityUtils.getCurrentTenantId());
        task.setCreateBy(SecurityUtils.getCurrentUsername());
        task.setCreateTime(LocalDateTime.now());
        task.setUpdateTime(LocalDateTime.now());
        task.setStatus("NOT_STARTED");
        fullSyncMapper.insert(task);
        operLogService.log(request, "新建整库同步", "整库同步", task.getName(), true, null);
        return Result.ok();
    }

    @PutMapping("/fullsync/{id}")
    public Result<Void> updateFullSync(@PathVariable Long id, @RequestBody AggFullSync task, HttpServletRequest request) {
        task.setId(id);
        task.setUpdateTime(LocalDateTime.now());
        fullSyncMapper.updateById(task);
        operLogService.log(request, "编辑整库同步", "整库同步", task.getName(), true, null);
        return Result.ok();
    }

    @DeleteMapping("/fullsync/{id}")
    public Result<Void> deleteFullSync(@PathVariable Long id, HttpServletRequest request) {
        AggFullSync task = fullSyncMapper.selectById(id);
        fullSyncMapper.deleteById(id);
        operLogService.log(request, "删除整库同步", "整库同步", task != null ? task.getName() : String.valueOf(id), true, null);
        return Result.ok();
    }

    @DeleteMapping("/fullsync/batch")
    public Result<Void> batchDeleteFullSync(@RequestBody List<Long> ids, HttpServletRequest request) {
        for (Long id : ids) {
            deleteFullSync(id, request);
        }
        return Result.ok();
    }

    @GetMapping("/cdc/page")
    public Result<PageResult<AggCdcTask>> cdcPage(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size,
            @RequestParam(required = false) String name) {
        LambdaQueryWrapper<AggCdcTask> qw = new LambdaQueryWrapper<>();
        qw.eq(AggCdcTask::getTenantId, SecurityUtils.getCurrentTenantId());
        if (StringUtils.hasText(name)) {
            qw.like(AggCdcTask::getName, name);
        }
        Page<AggCdcTask> p = cdcTaskMapper.selectPage(new Page<>(page, size), qw);
        return Result.ok(PageResult.of(p.getTotal(), page, size, p.getRecords()));
    }

    @PostMapping("/cdc")
    public Result<Void> createCdc(@RequestBody AggCdcTask task, HttpServletRequest request) {
        task.setTenantId(SecurityUtils.getCurrentTenantId());
        task.setCreateBy(SecurityUtils.getCurrentUsername());
        task.setCreateTime(LocalDateTime.now());
        task.setUpdateTime(LocalDateTime.now());
        task.setStatus("STOPPED");
        cdcTaskMapper.insert(task);
        operLogService.log(request, "新建CDC任务", "CDC实时同步", task.getName(), true, null);
        return Result.ok();
    }

    @PutMapping("/cdc/{id}")
    public Result<Void> updateCdc(@PathVariable Long id, @RequestBody AggCdcTask task, HttpServletRequest request) {
        task.setId(id);
        task.setUpdateTime(LocalDateTime.now());
        cdcTaskMapper.updateById(task);
        operLogService.log(request, "编辑CDC任务", "CDC实时同步", task.getName(), true, null);
        return Result.ok();
    }

    @PostMapping("/cdc/{id}/start")
    public Result<Void> startCdc(@PathVariable Long id, HttpServletRequest request) {
        AggCdcTask task = cdcTaskMapper.selectById(id);
        task.setStatus("RUNNING");
        task.setStartTime(LocalDateTime.now());
        task.setUpdateTime(LocalDateTime.now());
        cdcTaskMapper.updateById(task);
        operLogService.log(request, "启动CDC任务", "CDC实时同步", task.getName(), true, null);
        return Result.ok();
    }

    @PostMapping("/cdc/{id}/stop")
    public Result<Void> stopCdc(@PathVariable Long id, HttpServletRequest request) {
        AggCdcTask task = cdcTaskMapper.selectById(id);
        task.setStatus("STOPPED");
        task.setUpdateTime(LocalDateTime.now());
        cdcTaskMapper.updateById(task);
        operLogService.log(request, "停止CDC任务", "CDC实时同步", task.getName(), true, null);
        return Result.ok();
    }

    @DeleteMapping("/cdc/{id}")
    public Result<Void> deleteCdc(@PathVariable Long id, HttpServletRequest request) {
        AggCdcTask task = cdcTaskMapper.selectById(id);
        cdcTaskMapper.deleteById(id);
        operLogService.log(request, "删除CDC任务", "CDC实时同步", task != null ? task.getName() : String.valueOf(id), true, null);
        return Result.ok();
    }
}
