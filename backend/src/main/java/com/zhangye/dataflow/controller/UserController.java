package com.zhangye.dataflow.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhangye.dataflow.common.BusinessException;
import com.zhangye.dataflow.common.PageResult;
import com.zhangye.dataflow.common.Result;
import com.zhangye.dataflow.entity.SysDept;
import com.zhangye.dataflow.entity.SysRole;
import com.zhangye.dataflow.entity.SysUser;
import com.zhangye.dataflow.mapper.SysDeptMapper;
import com.zhangye.dataflow.mapper.SysRoleMapper;
import com.zhangye.dataflow.mapper.SysUserMapper;
import com.zhangye.dataflow.mapper.SysUserRoleMapper;
import com.zhangye.dataflow.security.SecurityUtils;
import com.zhangye.dataflow.service.OperLogService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/portal/user")
public class UserController {

    private final SysUserMapper userMapper;
    private final SysDeptMapper deptMapper;
    private final SysRoleMapper roleMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final PasswordEncoder passwordEncoder;
    private final OperLogService operLogService;

    public UserController(SysUserMapper userMapper, SysDeptMapper deptMapper, SysRoleMapper roleMapper,
                          SysUserRoleMapper userRoleMapper, PasswordEncoder passwordEncoder,
                          OperLogService operLogService) {
        this.userMapper = userMapper;
        this.deptMapper = deptMapper;
        this.roleMapper = roleMapper;
        this.userRoleMapper = userRoleMapper;
        this.passwordEncoder = passwordEncoder;
        this.operLogService = operLogService;
    }

    @GetMapping("/page")
    public Result<PageResult<SysUser>> page(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size,
            @RequestParam(required = false) String username,
            @RequestParam(required = false) Integer status) {
        Long tenantId = SecurityUtils.getCurrentTenantId();
        LambdaQueryWrapper<SysUser> qw = new LambdaQueryWrapper<>();
        qw.eq(SysUser::getTenantId, tenantId);
        if (StringUtils.hasText(username)) {
            qw.like(SysUser::getUsername, username);
        }
        if (status != null) {
            qw.eq(SysUser::getStatus, status);
        }
        qw.orderByDesc(SysUser::getCreateTime);
        Page<SysUser> p = userMapper.selectPage(new Page<>(page, size), qw);
        p.getRecords().forEach(this::fillUserExtra);
        return Result.ok(PageResult.of(p.getTotal(), page, size, p.getRecords()));
    }

    @GetMapping("/{id}")
    public Result<SysUser> get(@PathVariable Long id) {
        SysUser user = userMapper.selectById(id);
        if (user != null) {
            fillUserExtra(user);
            user.setPassword(null);
        }
        return Result.ok(user);
    }

    @PostMapping
    public Result<Void> create(@RequestBody SysUser user, HttpServletRequest request) {
        if (!StringUtils.hasText(user.getUsername()) || !StringUtils.hasText(user.getPassword())) {
            throw new BusinessException("用户名和密码不能为空");
        }
        Long count = userMapper.selectCount(new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, user.getUsername()));
        if (count > 0) {
            throw new BusinessException("用户名已存在");
        }
        user.setTenantId(SecurityUtils.getCurrentTenantId());
        user.setPassword(passwordEncoder.encode(user.getPassword()));
        user.setCreateTime(LocalDateTime.now());
        user.setUpdateTime(LocalDateTime.now());
        if (user.getStatus() == null) {
            user.setStatus(1);
        }
        userMapper.insert(user);
        saveUserRoles(user.getId(), user.getRoleIds());
        operLogService.log(request, "创建用户", "用户管理", user.getUsername(), true, null);
        return Result.ok();
    }

    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody SysUser user, HttpServletRequest request) {
        SysUser existing = userMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException("用户不存在");
        }
        existing.setDeptId(user.getDeptId());
        existing.setPhone(user.getPhone());
        existing.setNickname(user.getNickname());
        existing.setStatus(user.getStatus());
        existing.setUpdateTime(LocalDateTime.now());
        if (StringUtils.hasText(user.getPassword())) {
            existing.setPassword(passwordEncoder.encode(user.getPassword()));
        }
        userMapper.updateById(existing);
        if (user.getRoleIds() != null) {
            userRoleMapper.deleteByUserId(id);
            saveUserRoles(id, user.getRoleIds());
        }
        operLogService.log(request, "编辑用户", "用户管理", existing.getUsername(), true, null);
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id, HttpServletRequest request) {
        if (id.equals(SecurityUtils.getCurrentUserId())) {
            throw new BusinessException("不能删除当前登录用户");
        }
        SysUser user = userMapper.selectById(id);
        userMapper.deleteById(id);
        userRoleMapper.deleteByUserId(id);
        operLogService.log(request, "删除用户", "用户管理", user != null ? user.getUsername() : String.valueOf(id), true, null);
        return Result.ok();
    }

    @PutMapping("/{id}/lock")
    public Result<Void> lock(@PathVariable Long id, @RequestParam Integer status, HttpServletRequest request) {
        SysUser user = userMapper.selectById(id);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        user.setStatus(status);
        user.setUpdateTime(LocalDateTime.now());
        userMapper.updateById(user);
        operLogService.log(request, status == 0 ? "锁定用户" : "解锁用户", "用户管理", user.getUsername(), true, null);
        return Result.ok();
    }

    private void saveUserRoles(Long userId, List<Long> roleIds) {
        if (roleIds == null) {
            return;
        }
        for (Long roleId : roleIds) {
            userRoleMapper.insert(userId, roleId);
        }
    }

    private void fillUserExtra(SysUser user) {
        if (user.getDeptId() != null) {
            SysDept dept = deptMapper.selectById(user.getDeptId());
            if (dept != null) {
                user.setDeptName(dept.getDeptName());
            }
        }
        List<Long> roleIds = userRoleMapper.selectRoleIdsByUserId(user.getId());
        user.setRoleIds(roleIds);
        if (!roleIds.isEmpty()) {
            user.setRoleNames(roleMapper.selectBatchIds(roleIds).stream().map(SysRole::getRoleName).collect(Collectors.toList()));
        }
        user.setPassword(null);
    }
}
