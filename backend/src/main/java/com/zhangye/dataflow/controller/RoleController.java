package com.zhangye.dataflow.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhangye.dataflow.common.PageResult;
import com.zhangye.dataflow.common.Result;
import com.zhangye.dataflow.entity.SysRole;
import com.zhangye.dataflow.mapper.SysRoleMapper;
import com.zhangye.dataflow.security.SecurityUtils;
import com.zhangye.dataflow.service.OperLogService;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/portal/role")
public class RoleController {

    private final SysRoleMapper roleMapper;
    private final OperLogService operLogService;

    public RoleController(SysRoleMapper roleMapper, OperLogService operLogService) {
        this.roleMapper = roleMapper;
        this.operLogService = operLogService;
    }

    @GetMapping("/list")
    public Result<java.util.List<SysRole>> list() {
        return Result.ok(roleMapper.selectList(new LambdaQueryWrapper<SysRole>()
                .eq(SysRole::getTenantId, SecurityUtils.getCurrentTenantId())));
    }

    @GetMapping("/page")
    public Result<PageResult<SysRole>> page(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size,
            @RequestParam(required = false) String roleName) {
        LambdaQueryWrapper<SysRole> qw = new LambdaQueryWrapper<>();
        qw.eq(SysRole::getTenantId, SecurityUtils.getCurrentTenantId());
        if (StringUtils.hasText(roleName)) {
            qw.like(SysRole::getRoleName, roleName);
        }
        Page<SysRole> p = roleMapper.selectPage(new Page<>(page, size), qw);
        return Result.ok(PageResult.of(p.getTotal(), page, size, p.getRecords()));
    }

    @PostMapping
    public Result<Void> create(@RequestBody SysRole role, HttpServletRequest request) {
        role.setTenantId(SecurityUtils.getCurrentTenantId());
        role.setCreateTime(LocalDateTime.now());
        role.setUpdateTime(LocalDateTime.now());
        roleMapper.insert(role);
        operLogService.log(request, "创建角色", "角色管理", role.getRoleName(), true, null);
        return Result.ok();
    }

    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody SysRole role, HttpServletRequest request) {
        role.setId(id);
        role.setUpdateTime(LocalDateTime.now());
        roleMapper.updateById(role);
        operLogService.log(request, "编辑角色", "角色管理", role.getRoleName(), true, null);
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id, HttpServletRequest request) {
        SysRole role = roleMapper.selectById(id);
        roleMapper.deleteById(id);
        operLogService.log(request, "删除角色", "角色管理", role != null ? role.getRoleName() : String.valueOf(id), true, null);
        return Result.ok();
    }
}
