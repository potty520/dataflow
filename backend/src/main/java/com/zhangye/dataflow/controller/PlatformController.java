package com.zhangye.dataflow.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhangye.dataflow.common.PageResult;
import com.zhangye.dataflow.common.Result;
import com.zhangye.dataflow.entity.SvcApi;
import com.zhangye.dataflow.entity.SvcApplication;
import com.zhangye.dataflow.mapper.*;
import com.zhangye.dataflow.security.SecurityUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api")
public class PlatformController {

    private final AggDatasourceMapper datasourceMapper;
    private final AggDataflowMapper dataflowMapper;
    private final SvcApplicationMapper applicationMapper;
    private final SvcApiMapper apiMapper;

    public PlatformController(AggDatasourceMapper datasourceMapper, AggDataflowMapper dataflowMapper,
                              SvcApplicationMapper applicationMapper, SvcApiMapper apiMapper) {
        this.datasourceMapper = datasourceMapper;
        this.dataflowMapper = dataflowMapper;
        this.applicationMapper = applicationMapper;
        this.apiMapper = apiMapper;
    }

    @GetMapping("/dashboard/stats")
    public Result<Map<String, Object>> dashboardStats() {
        Long tenantId = SecurityUtils.getCurrentTenantId();
        Map<String, Object> stats = new HashMap<>();
        stats.put("datasourceCount", datasourceMapper.selectCount(new LambdaQueryWrapper<com.zhangye.dataflow.entity.AggDatasource>()
                .eq(com.zhangye.dataflow.entity.AggDatasource::getTenantId, tenantId)));
        stats.put("dataflowCount", dataflowMapper.selectCount(new LambdaQueryWrapper<com.zhangye.dataflow.entity.AggDataflow>()
                .eq(com.zhangye.dataflow.entity.AggDataflow::getTenantId, tenantId)));
        stats.put("apiCount", apiMapper.selectCount(new LambdaQueryWrapper<SvcApi>()
                .eq(SvcApi::getTenantId, tenantId)));
        stats.put("appCount", applicationMapper.selectCount(new LambdaQueryWrapper<SvcApplication>()
                .eq(SvcApplication::getTenantId, tenantId)));
        return Result.ok(stats);
    }

    @GetMapping("/service/application/page")
    public Result<PageResult<SvcApplication>> appPage(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size,
            @RequestParam(required = false) String appName) {
        LambdaQueryWrapper<SvcApplication> qw = new LambdaQueryWrapper<>();
        qw.eq(SvcApplication::getTenantId, SecurityUtils.getCurrentTenantId());
        if (StringUtils.hasText(appName)) {
            qw.like(SvcApplication::getAppName, appName);
        }
        Page<SvcApplication> p = applicationMapper.selectPage(new Page<>(page, size), qw);
        return Result.ok(PageResult.of(p.getTotal(), page, size, p.getRecords()));
    }

    @PostMapping("/service/application")
    public Result<Void> createApp(@RequestBody SvcApplication app) {
        app.setTenantId(SecurityUtils.getCurrentTenantId());
        app.setAppKey("APP_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase());
        app.setAppSecret(UUID.randomUUID().toString().replace("-", ""));
        app.setCreateTime(LocalDateTime.now());
        app.setStatus(1);
        applicationMapper.insert(app);
        return Result.ok();
    }

    @PutMapping("/service/application/{id}")
    public Result<Void> updateApp(@PathVariable Long id, @RequestBody SvcApplication app) {
        app.setId(id);
        applicationMapper.updateById(app);
        return Result.ok();
    }

    @DeleteMapping("/service/application/{id}")
    public Result<Void> deleteApp(@PathVariable Long id) {
        applicationMapper.deleteById(id);
        return Result.ok();
    }

    @GetMapping("/service/api/page")
    public Result<PageResult<SvcApi>> apiPage(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size,
            @RequestParam(required = false) String apiName) {
        LambdaQueryWrapper<SvcApi> qw = new LambdaQueryWrapper<>();
        qw.eq(SvcApi::getTenantId, SecurityUtils.getCurrentTenantId());
        if (StringUtils.hasText(apiName)) {
            qw.like(SvcApi::getApiName, apiName);
        }
        Page<SvcApi> p = apiMapper.selectPage(new Page<>(page, size), qw);
        return Result.ok(PageResult.of(p.getTotal(), page, size, p.getRecords()));
    }

    @PostMapping("/service/api")
    public Result<Void> createApi(@RequestBody SvcApi api) {
        api.setTenantId(SecurityUtils.getCurrentTenantId());
        api.setCreateTime(LocalDateTime.now());
        api.setUpdateTime(LocalDateTime.now());
        api.setStatus(0);
        apiMapper.insert(api);
        return Result.ok();
    }

    @PutMapping("/service/api/{id}")
    public Result<Void> updateApi(@PathVariable Long id, @RequestBody SvcApi api) {
        api.setId(id);
        api.setUpdateTime(LocalDateTime.now());
        apiMapper.updateById(api);
        return Result.ok();
    }

    @PostMapping("/service/api/{id}/publish")
    public Result<Void> publishApi(@PathVariable Long id) {
        SvcApi api = apiMapper.selectById(id);
        api.setStatus(1);
        api.setUpdateTime(LocalDateTime.now());
        apiMapper.updateById(api);
        return Result.ok();
    }

    @PostMapping("/service/api/{id}/offline")
    public Result<Void> offlineApi(@PathVariable Long id) {
        SvcApi api = apiMapper.selectById(id);
        api.setStatus(2);
        api.setUpdateTime(LocalDateTime.now());
        apiMapper.updateById(api);
        return Result.ok();
    }

    @DeleteMapping("/service/api/{id}")
    public Result<Void> deleteApi(@PathVariable Long id) {
        SvcApi api = apiMapper.selectById(id);
        if (api != null && api.getStatus() != null && api.getStatus() == 1) {
            return Result.fail("已发布API不能删除，请先下线");
        }
        apiMapper.deleteById(id);
        return Result.ok();
    }
}
