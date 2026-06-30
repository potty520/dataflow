package com.zhangye.dataflow.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhangye.dataflow.common.PageResult;
import com.zhangye.dataflow.common.Result;
import com.zhangye.dataflow.entity.SysOperLog;
import com.zhangye.dataflow.mapper.SysOperLogMapper;
import com.zhangye.dataflow.security.SecurityUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/portal/log")
public class LogController {

    private final SysOperLogMapper operLogMapper;

    public LogController(SysOperLogMapper operLogMapper) {
        this.operLogMapper = operLogMapper;
    }

    @GetMapping("/page")
    public Result<PageResult<SysOperLog>> page(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size,
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String eventName,
            @RequestParam(required = false) Integer result,
            @RequestParam(required = false) String resourceType,
            @RequestParam(required = false) String method,
            @RequestParam(required = false) String resourceName) {
        LambdaQueryWrapper<SysOperLog> qw = new LambdaQueryWrapper<>();
        Long tenantId = SecurityUtils.getCurrentTenantId();
        if (tenantId != null) {
            qw.eq(SysOperLog::getTenantId, tenantId);
        }
        if (StringUtils.hasText(username)) {
            qw.like(SysOperLog::getUsername, username);
        }
        if (StringUtils.hasText(eventName)) {
            qw.like(SysOperLog::getEventName, eventName);
        }
        if (result != null) {
            qw.eq(SysOperLog::getResult, result);
        }
        if (StringUtils.hasText(resourceType)) {
            qw.eq(SysOperLog::getResourceType, resourceType);
        }
        if (StringUtils.hasText(method)) {
            qw.eq(SysOperLog::getMethod, method);
        }
        if (StringUtils.hasText(resourceName)) {
            qw.like(SysOperLog::getResourceName, resourceName);
        }
        qw.orderByDesc(SysOperLog::getCreateTime);
        Page<SysOperLog> p = operLogMapper.selectPage(new Page<>(page, size), qw);
        return Result.ok(PageResult.of(p.getTotal(), page, size, p.getRecords()));
    }

    @GetMapping("/{id}")
    public Result<SysOperLog> detail(@PathVariable Long id) {
        return Result.ok(operLogMapper.selectById(id));
    }
}
