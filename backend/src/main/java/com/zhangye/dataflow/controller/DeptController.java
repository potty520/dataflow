package com.zhangye.dataflow.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhangye.dataflow.common.BusinessException;
import com.zhangye.dataflow.common.Result;
import com.zhangye.dataflow.entity.SysDept;
import com.zhangye.dataflow.mapper.SysDeptMapper;
import com.zhangye.dataflow.security.SecurityUtils;
import com.zhangye.dataflow.service.OperLogService;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/portal/dept")
public class DeptController {

    private final SysDeptMapper deptMapper;
    private final OperLogService operLogService;

    public DeptController(SysDeptMapper deptMapper, OperLogService operLogService) {
        this.deptMapper = deptMapper;
        this.operLogService = operLogService;
    }

    @GetMapping("/tree")
    public Result<List<SysDept>> tree() {
        List<SysDept> all = deptMapper.selectList(new LambdaQueryWrapper<SysDept>()
                .eq(SysDept::getTenantId, SecurityUtils.getCurrentTenantId())
                .orderByAsc(SysDept::getSortOrder));
        return Result.ok(buildTree(all, 0L));
    }

    @PostMapping
    public Result<Void> create(@RequestBody SysDept dept, HttpServletRequest request) {
        dept.setTenantId(SecurityUtils.getCurrentTenantId());
        dept.setCreateTime(LocalDateTime.now());
        dept.setUpdateTime(LocalDateTime.now());
        if (dept.getParentId() == null) {
            dept.setParentId(0L);
        }
        deptMapper.insert(dept);
        operLogService.log(request, "创建部门", "部门管理", dept.getDeptName(), true, null);
        return Result.ok();
    }

    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody SysDept dept, HttpServletRequest request) {
        dept.setId(id);
        dept.setUpdateTime(LocalDateTime.now());
        deptMapper.updateById(dept);
        operLogService.log(request, "编辑部门", "部门管理", dept.getDeptName(), true, null);
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id, HttpServletRequest request) {
        Long childCount = deptMapper.selectCount(new LambdaQueryWrapper<SysDept>().eq(SysDept::getParentId, id));
        if (childCount > 0) {
            throw new BusinessException("包含子部门的部门不允许删除");
        }
        SysDept dept = deptMapper.selectById(id);
        deptMapper.deleteById(id);
        operLogService.log(request, "删除部门", "部门管理", dept != null ? dept.getDeptName() : String.valueOf(id), true, null);
        return Result.ok();
    }

    private List<SysDept> buildTree(List<SysDept> all, Long parentId) {
        return all.stream()
                .filter(d -> parentId.equals(d.getParentId() == null ? 0L : d.getParentId()))
                .peek(d -> d.setChildren(buildTree(all, d.getId())))
                .collect(Collectors.toList());
    }
}
