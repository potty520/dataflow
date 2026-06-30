package com.zhangye.dataflow.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhangye.dataflow.common.PageResult;
import com.zhangye.dataflow.common.Result;
import com.zhangye.dataflow.entity.SysMenu;
import com.zhangye.dataflow.mapper.SysMenuMapper;
import com.zhangye.dataflow.service.OperLogService;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/portal/menu")
public class MenuController {

    private final SysMenuMapper menuMapper;
    private final OperLogService operLogService;

    public MenuController(SysMenuMapper menuMapper, OperLogService operLogService) {
        this.menuMapper = menuMapper;
        this.operLogService = operLogService;
    }

    @GetMapping("/tree")
    public Result<List<SysMenu>> tree(@RequestParam(required = false) String menuName,
                                      @RequestParam(required = false) String menuCode) {
        LambdaQueryWrapper<SysMenu> qw = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(menuName)) {
            qw.like(SysMenu::getMenuName, menuName);
        }
        if (StringUtils.hasText(menuCode)) {
            qw.like(SysMenu::getMenuCode, menuCode);
        }
        qw.orderByAsc(SysMenu::getSortOrder);
        List<SysMenu> all = menuMapper.selectList(qw);
        return Result.ok(buildTree(all, 0L));
    }

    @GetMapping("/page")
    public Result<PageResult<SysMenu>> page(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size,
            @RequestParam(required = false) String menuName) {
        LambdaQueryWrapper<SysMenu> qw = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(menuName)) {
            qw.like(SysMenu::getMenuName, menuName);
        }
        Page<SysMenu> p = menuMapper.selectPage(new Page<>(page, size), qw);
        return Result.ok(PageResult.of(p.getTotal(), page, size, p.getRecords()));
    }

    @PostMapping
    public Result<Void> create(@RequestBody SysMenu menu, HttpServletRequest request) {
        menu.setCreateTime(LocalDateTime.now());
        menu.setUpdateTime(LocalDateTime.now());
        menuMapper.insert(menu);
        operLogService.log(request, "创建菜单", "菜单管理", menu.getMenuName(), true, null);
        return Result.ok();
    }

    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody SysMenu menu, HttpServletRequest request) {
        menu.setId(id);
        menu.setUpdateTime(LocalDateTime.now());
        menuMapper.updateById(menu);
        operLogService.log(request, "编辑菜单", "菜单管理", menu.getMenuName(), true, null);
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id, HttpServletRequest request) {
        SysMenu menu = menuMapper.selectById(id);
        menuMapper.deleteById(id);
        operLogService.log(request, "删除菜单", "菜单管理", menu != null ? menu.getMenuName() : String.valueOf(id), true, null);
        return Result.ok();
    }

    private List<SysMenu> buildTree(List<SysMenu> all, Long parentId) {
        return all.stream()
                .filter(m -> parentId.equals(m.getParentId() == null ? 0L : m.getParentId()))
                .peek(m -> m.setChildren(buildTree(all, m.getId())))
                .collect(Collectors.toList());
    }
}
