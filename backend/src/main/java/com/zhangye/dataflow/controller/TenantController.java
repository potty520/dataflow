package com.zhangye.dataflow.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhangye.dataflow.common.BusinessException;
import com.zhangye.dataflow.common.PageResult;
import com.zhangye.dataflow.common.Result;
import com.zhangye.dataflow.entity.SysTenant;
import com.zhangye.dataflow.mapper.SysTenantMapper;
import com.zhangye.dataflow.security.SecurityUtils;
import com.zhangye.dataflow.service.OperLogService;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/portal/tenant")
public class TenantController {

    private final SysTenantMapper tenantMapper;
    private final OperLogService operLogService;

    public TenantController(SysTenantMapper tenantMapper, OperLogService operLogService) {
        this.tenantMapper = tenantMapper;
        this.operLogService = operLogService;
    }

    @GetMapping("/page")
    public Result<PageResult<SysTenant>> page(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size,
            @RequestParam(required = false) String tenantName,
            @RequestParam(required = false) String createBy) {
        LambdaQueryWrapper<SysTenant> qw = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(tenantName)) {
            qw.like(SysTenant::getTenantName, tenantName);
        }
        if (StringUtils.hasText(createBy)) {
            qw.like(SysTenant::getCreateBy, createBy);
        }
        qw.orderByDesc(SysTenant::getCreateTime);
        Page<SysTenant> p = tenantMapper.selectPage(new Page<>(page, size), qw);
        return Result.ok(PageResult.of(p.getTotal(), page, size, p.getRecords()));
    }

    @GetMapping("/{id}")
    public Result<SysTenant> get(@PathVariable Long id) {
        return Result.ok(tenantMapper.selectById(id));
    }

    @PostMapping
    public Result<Void> create(@RequestBody SysTenant tenant, HttpServletRequest request) {
        Long count = tenantMapper.selectCount(new LambdaQueryWrapper<SysTenant>()
                .eq(SysTenant::getTenantName, tenant.getTenantName()));
        if (count > 0) {
            throw new BusinessException("租户名称已存在");
        }
        tenant.setCreateBy(SecurityUtils.getCurrentUsername());
        tenant.setCreateTime(LocalDateTime.now());
        tenant.setUpdateTime(LocalDateTime.now());
        if (tenant.getStatus() == null) {
            tenant.setStatus(1);
        }
        tenantMapper.insert(tenant);
        operLogService.log(request, "创建租户", "租户管理", tenant.getTenantName(), true, null);
        return Result.ok();
    }

    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody SysTenant tenant, HttpServletRequest request) {
        tenant.setId(id);
        tenant.setUpdateTime(LocalDateTime.now());
        tenantMapper.updateById(tenant);
        operLogService.log(request, "编辑租户", "租户管理", tenant.getTenantName(), true, null);
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id, HttpServletRequest request) {
        SysTenant tenant = tenantMapper.selectById(id);
        tenantMapper.deleteById(id);
        operLogService.log(request, "删除租户", "租户管理", tenant != null ? tenant.getTenantName() : String.valueOf(id), true, null);
        return Result.ok();
    }
}
