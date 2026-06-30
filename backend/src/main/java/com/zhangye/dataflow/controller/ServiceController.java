package com.zhangye.dataflow.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhangye.dataflow.common.BusinessException;
import com.zhangye.dataflow.common.PageResult;
import com.zhangye.dataflow.common.Result;
import com.zhangye.dataflow.entity.*;
import com.zhangye.dataflow.mapper.*;
import com.zhangye.dataflow.security.SecurityUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/service")
public class ServiceController {

    private final SvcCatalogProjectMapper projectMapper;
    private final SvcCatalogMapper catalogMapper;
    private final SvcServiceUnitMapper serviceUnitMapper;
    private final SvcWorkorderMapper workorderMapper;
    private final SvcRateLimitMapper rateLimitMapper;
    private final SvcApiCallLogMapper callLogMapper;
    private final SvcApiMapper apiMapper;

    public ServiceController(SvcCatalogProjectMapper projectMapper, SvcCatalogMapper catalogMapper,
                             SvcServiceUnitMapper serviceUnitMapper, SvcWorkorderMapper workorderMapper,
                             SvcRateLimitMapper rateLimitMapper, SvcApiCallLogMapper callLogMapper,
                             SvcApiMapper apiMapper) {
        this.projectMapper = projectMapper;
        this.catalogMapper = catalogMapper;
        this.serviceUnitMapper = serviceUnitMapper;
        this.workorderMapper = workorderMapper;
        this.rateLimitMapper = rateLimitMapper;
        this.callLogMapper = callLogMapper;
        this.apiMapper = apiMapper;
    }

    private Long tid() { return SecurityUtils.getCurrentTenantId(); }

    @GetMapping("/catalog-project/list")
    public Result<List<SvcCatalogProject>> catalogProjects() {
        return Result.ok(projectMapper.selectList(new LambdaQueryWrapper<SvcCatalogProject>()
                .eq(SvcCatalogProject::getTenantId, tid())));
    }

    @PostMapping("/catalog-project")
    public Result<Void> createCatalogProject(@RequestBody SvcCatalogProject e) {
        e.setTenantId(tid());
        e.setCreateTime(LocalDateTime.now());
        projectMapper.insert(e);
        return Result.ok();
    }

    @PutMapping("/catalog-project/{id}")
    public Result<Void> updateCatalogProject(@PathVariable Long id, @RequestBody SvcCatalogProject e) {
        e.setId(id);
        projectMapper.updateById(e);
        return Result.ok();
    }

    @DeleteMapping("/catalog-project/{id}")
    public Result<Void> deleteCatalogProject(@PathVariable Long id) {
        projectMapper.deleteById(id);
        return Result.ok();
    }

    @GetMapping("/catalog/tree")
    public Result<List<SvcCatalog>> catalogTree(@RequestParam(required = false) Long projectId) {
        LambdaQueryWrapper<SvcCatalog> qw = new LambdaQueryWrapper<SvcCatalog>().eq(SvcCatalog::getTenantId, tid());
        if (projectId != null) qw.eq(SvcCatalog::getProjectId, projectId);
        List<SvcCatalog> all = catalogMapper.selectList(qw);
        return Result.ok(buildCatalogTree(all, 0L));
    }

    @PostMapping("/catalog")
    public Result<Void> createCatalog(@RequestBody SvcCatalog e) {
        e.setTenantId(tid());
        e.setCreateTime(LocalDateTime.now());
        catalogMapper.insert(e);
        return Result.ok();
    }

    @PutMapping("/catalog/{id}")
    public Result<Void> updateCatalog(@PathVariable Long id, @RequestBody SvcCatalog e) {
        e.setId(id);
        catalogMapper.updateById(e);
        return Result.ok();
    }

    @DeleteMapping("/catalog/{id}")
    public Result<Void> deleteCatalog(@PathVariable Long id) {
        catalogMapper.deleteById(id);
        return Result.ok();
    }

    @GetMapping("/unit/page")
    public Result<PageResult<SvcServiceUnit>> unitPage(
            @RequestParam(defaultValue = "1") long page, @RequestParam(defaultValue = "10") long size,
            @RequestParam(required = false) String unitName) {
        LambdaQueryWrapper<SvcServiceUnit> qw = new LambdaQueryWrapper<SvcServiceUnit>().eq(SvcServiceUnit::getTenantId, tid());
        if (StringUtils.hasText(unitName)) qw.like(SvcServiceUnit::getUnitName, unitName);
        Page<SvcServiceUnit> p = serviceUnitMapper.selectPage(new Page<>(page, size), qw);
        return Result.ok(PageResult.of(p.getTotal(), page, size, p.getRecords()));
    }

    @PostMapping("/unit")
    public Result<Void> createUnit(@RequestBody SvcServiceUnit e) {
        e.setTenantId(tid());
        e.setCreateTime(LocalDateTime.now());
        e.setUpdateTime(LocalDateTime.now());
        serviceUnitMapper.insert(e);
        return Result.ok();
    }

    @PutMapping("/unit/{id}")
    public Result<Void> updateUnit(@PathVariable Long id, @RequestBody SvcServiceUnit e) {
        e.setId(id);
        e.setUpdateTime(LocalDateTime.now());
        serviceUnitMapper.updateById(e);
        return Result.ok();
    }

    @PostMapping("/unit/{id}/publish")
    public Result<Void> publishUnit(@PathVariable Long id) {
        SvcServiceUnit e = serviceUnitMapper.selectById(id);
        e.setStatus(1);
        e.setUpdateTime(LocalDateTime.now());
        serviceUnitMapper.updateById(e);
        return Result.ok();
    }

    @PostMapping("/unit/{id}/offline")
    public Result<Void> offlineUnit(@PathVariable Long id) {
        SvcServiceUnit e = serviceUnitMapper.selectById(id);
        e.setStatus(2);
        e.setUpdateTime(LocalDateTime.now());
        serviceUnitMapper.updateById(e);
        return Result.ok();
    }

    @DeleteMapping("/unit/{id}")
    public Result<Void> deleteUnit(@PathVariable Long id) {
        SvcServiceUnit e = serviceUnitMapper.selectById(id);
        if (e != null && e.getStatus() != null && e.getStatus() == 1) {
            throw new BusinessException("已发布服务单元不能删除，请先下线");
        }
        serviceUnitMapper.deleteById(id);
        return Result.ok();
    }

    @GetMapping("/workorder/page")
    public Result<PageResult<SvcWorkorder>> workorderPage(
            @RequestParam(defaultValue = "1") long page, @RequestParam(defaultValue = "10") long size,
            @RequestParam(required = false) Integer status) {
        LambdaQueryWrapper<SvcWorkorder> qw = new LambdaQueryWrapper<SvcWorkorder>().eq(SvcWorkorder::getTenantId, tid());
        if (status != null) qw.eq(SvcWorkorder::getStatus, status);
        qw.orderByDesc(SvcWorkorder::getCreateTime);
        Page<SvcWorkorder> p = workorderMapper.selectPage(new Page<>(page, size), qw);
        return Result.ok(PageResult.of(p.getTotal(), page, size, p.getRecords()));
    }

    @PostMapping("/workorder")
    public Result<Void> createWorkorder(@RequestBody SvcWorkorder e) {
        e.setTenantId(tid());
        e.setApplicant(SecurityUtils.getCurrentUsername());
        e.setCreateTime(LocalDateTime.now());
        e.setUpdateTime(LocalDateTime.now());
        e.setStatus(0);
        workorderMapper.insert(e);
        return Result.ok();
    }

    @PostMapping("/workorder/{id}/approve")
    public Result<Void> approveWorkorder(@PathVariable Long id, @RequestParam boolean pass,
                                           @RequestParam(required = false) String reason) {
        SvcWorkorder e = workorderMapper.selectById(id);
        e.setStatus(pass ? 1 : 2);
        e.setApproveReason(reason);
        e.setUpdateTime(LocalDateTime.now());
        workorderMapper.updateById(e);
        return Result.ok();
    }

    @GetMapping("/ratelimit/page")
    public Result<PageResult<SvcRateLimit>> rateLimitPage(
            @RequestParam(defaultValue = "1") long page, @RequestParam(defaultValue = "10") long size) {
        LambdaQueryWrapper<SvcRateLimit> qw = new LambdaQueryWrapper<SvcRateLimit>().eq(SvcRateLimit::getTenantId, tid());
        Page<SvcRateLimit> p = rateLimitMapper.selectPage(new Page<>(page, size), qw);
        return Result.ok(PageResult.of(p.getTotal(), page, size, p.getRecords()));
    }

    @PostMapping("/ratelimit")
    public Result<Void> createRateLimit(@RequestBody SvcRateLimit e) {
        e.setTenantId(tid());
        e.setCreateTime(LocalDateTime.now());
        rateLimitMapper.insert(e);
        return Result.ok();
    }

    @PutMapping("/ratelimit/{id}")
    public Result<Void> updateRateLimit(@PathVariable Long id, @RequestBody SvcRateLimit e) {
        e.setId(id);
        rateLimitMapper.updateById(e);
        return Result.ok();
    }

    @DeleteMapping("/ratelimit/{id}")
    public Result<Void> deleteRateLimit(@PathVariable Long id) {
        rateLimitMapper.deleteById(id);
        return Result.ok();
    }

    @GetMapping("/call-log/page")
    public Result<PageResult<SvcApiCallLog>> callLogPage(
            @RequestParam(defaultValue = "1") long page, @RequestParam(defaultValue = "10") long size,
            @RequestParam(required = false) String apiName,
            @RequestParam(required = false) Integer success) {
        LambdaQueryWrapper<SvcApiCallLog> qw = new LambdaQueryWrapper<SvcApiCallLog>().eq(SvcApiCallLog::getTenantId, tid());
        if (StringUtils.hasText(apiName)) qw.like(SvcApiCallLog::getApiName, apiName);
        if (success != null) qw.eq(SvcApiCallLog::getSuccess, success);
        qw.orderByDesc(SvcApiCallLog::getCreateTime);
        Page<SvcApiCallLog> p = callLogMapper.selectPage(new Page<>(page, size), qw);
        return Result.ok(PageResult.of(p.getTotal(), page, size, p.getRecords()));
    }

    @GetMapping("/monitor/stats")
    public Result<Map<String, Object>> monitorStats() {
        Long tid = tid();
        Map<String, Object> stats = new HashMap<>();
        stats.put("apiTotal", apiMapper.selectCount(new LambdaQueryWrapper<SvcApi>().eq(SvcApi::getTenantId, tid)));
        stats.put("publishedApi", apiMapper.selectCount(new LambdaQueryWrapper<SvcApi>().eq(SvcApi::getTenantId, tid).eq(SvcApi::getStatus, 1)));
        stats.put("pendingWorkorder", workorderMapper.selectCount(new LambdaQueryWrapper<SvcWorkorder>().eq(SvcWorkorder::getTenantId, tid).eq(SvcWorkorder::getStatus, 0)));
        stats.put("todayCalls", callLogMapper.selectCount(new LambdaQueryWrapper<SvcApiCallLog>()
                .eq(SvcApiCallLog::getTenantId, tid)));
        stats.put("serviceUnits", serviceUnitMapper.selectCount(new LambdaQueryWrapper<SvcServiceUnit>().eq(SvcServiceUnit::getTenantId, tid)));
        return Result.ok(stats);
    }

    @GetMapping("/monitor/call-stats")
    public Result<Map<String, Object>> callStats(@RequestParam(defaultValue = "day") String range) {
        Long tid = tid();
        Map<String, Object> stats = new HashMap<>();
        List<SvcApiCallLog> allLogs = callLogMapper.selectList(
                new LambdaQueryWrapper<SvcApiCallLog>().eq(SvcApiCallLog::getTenantId, tid));
        long totalCalls = allLogs.size();
        long successCount = allLogs.stream().filter(l -> l.getSuccess() != null && l.getSuccess() == 1).count();
        double successRate = totalCalls > 0 ? Math.round(successCount * 10000.0 / totalCalls) / 100.0 : 100.0;
        double avgResponseMs = allLogs.stream().mapToLong(l -> l.getResponseMs() == null ? 0 : l.getResponseMs())
                .average().orElse(0);
        double errorRate = totalCalls > 0 ? Math.round((totalCalls - successCount) * 10000.0 / totalCalls) / 100.0 : 0;
        stats.put("totalCalls", totalCalls);
        stats.put("successRate", successRate);
        stats.put("errorRate", errorRate);
        stats.put("avgResponseTime", Math.round(avgResponseMs));
        // Calls by hour (sample data)
        List<Map<String, Object>> callsByHour = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();
        for (int i = 23; i >= 0; i--) {
            Map<String, Object> m = new HashMap<>();
            m.put("hour", now.minus(i, ChronoUnit.HOURS).getHour() + ":00");
            m.put("count", new Random().nextInt(200) + 50);
            callsByHour.add(m);
        }
        stats.put("callsByHour", callsByHour);
        // Top APIs
        Map<String, Long> apiCountMap = allLogs.stream()
                .collect(Collectors.groupingBy(l -> l.getApiName() == null ? "Unknown" : l.getApiName(), Collectors.counting()));
        List<Map<String, Object>> topApis = apiCountMap.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(5)
                .map(e -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("apiName", e.getKey());
                    m.put("count", e.getValue());
                    return m;
                })
                .collect(Collectors.toList());
        stats.put("topApis", topApis);
        return Result.ok(stats);
    }

    private List<SvcCatalog> buildCatalogTree(List<SvcCatalog> all, Long parentId) {
        return all.stream()
                .filter(c -> parentId.equals(c.getParentId() == null ? 0L : c.getParentId()))
                .collect(Collectors.toList());
    }
}
