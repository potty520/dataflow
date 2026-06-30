package com.zhangye.dataflow.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhangye.dataflow.common.PageResult;
import com.zhangye.dataflow.common.Result;
import com.zhangye.dataflow.entity.InfraCluster;
import com.zhangye.dataflow.entity.InfraComponent;
import com.zhangye.dataflow.entity.InfraHost;
import com.zhangye.dataflow.mapper.InfraClusterMapper;
import com.zhangye.dataflow.mapper.InfraComponentMapper;
import com.zhangye.dataflow.mapper.InfraHostMapper;
import com.zhangye.dataflow.security.SecurityUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/infrastructure")
public class InfrastructureController {

    private final InfraHostMapper hostMapper;
    private final InfraComponentMapper componentMapper;
    private final InfraClusterMapper clusterMapper;

    public InfrastructureController(InfraHostMapper hostMapper, InfraComponentMapper componentMapper,
                                    InfraClusterMapper clusterMapper) {
        this.hostMapper = hostMapper;
        this.componentMapper = componentMapper;
        this.clusterMapper = clusterMapper;
    }

    private Long tid() { return SecurityUtils.getCurrentTenantId(); }

    @GetMapping("/host/page")
    public Result<PageResult<InfraHost>> hostPage(
            @RequestParam(defaultValue = "1") long page, @RequestParam(defaultValue = "10") long size,
            @RequestParam(required = false) String hostname) {
        LambdaQueryWrapper<InfraHost> qw = new LambdaQueryWrapper<InfraHost>().eq(InfraHost::getTenantId, tid());
        if (StringUtils.hasText(hostname)) qw.like(InfraHost::getHostname, hostname);
        Page<InfraHost> p = hostMapper.selectPage(new Page<>(page, size), qw);
        return Result.ok(PageResult.of(p.getTotal(), page, size, p.getRecords()));
    }

    @PostMapping("/host")
    public Result<Void> createHost(@RequestBody InfraHost e) {
        e.setTenantId(tid());
        e.setCreateTime(LocalDateTime.now());
        hostMapper.insert(e);
        return Result.ok();
    }

    @PutMapping("/host/{id}")
    public Result<Void> updateHost(@PathVariable Long id, @RequestBody InfraHost e) {
        e.setId(id);
        hostMapper.updateById(e);
        return Result.ok();
    }

    @DeleteMapping("/host/{id}")
    public Result<Void> deleteHost(@PathVariable Long id) {
        hostMapper.deleteById(id);
        return Result.ok();
    }

    @GetMapping("/component/page")
    public Result<PageResult<InfraComponent>> componentPage(
            @RequestParam(defaultValue = "1") long page, @RequestParam(defaultValue = "10") long size,
            @RequestParam(required = false) String componentType) {
        LambdaQueryWrapper<InfraComponent> qw = new LambdaQueryWrapper<InfraComponent>().eq(InfraComponent::getTenantId, tid());
        if (StringUtils.hasText(componentType)) qw.eq(InfraComponent::getComponentType, componentType);
        Page<InfraComponent> p = componentMapper.selectPage(new Page<>(page, size), qw);
        return Result.ok(PageResult.of(p.getTotal(), page, size, p.getRecords()));
    }

    @PostMapping("/component")
    public Result<Void> createComponent(@RequestBody InfraComponent e) {
        e.setTenantId(tid());
        e.setCreateTime(LocalDateTime.now());
        e.setUpdateTime(LocalDateTime.now());
        componentMapper.insert(e);
        return Result.ok();
    }

    @PutMapping("/component/{id}")
    public Result<Void> updateComponent(@PathVariable Long id, @RequestBody InfraComponent e) {
        e.setId(id);
        e.setUpdateTime(LocalDateTime.now());
        componentMapper.updateById(e);
        return Result.ok();
    }

    @PostMapping("/component/{id}/start")
    public Result<Void> startComponent(@PathVariable Long id) {
        InfraComponent e = componentMapper.selectById(id);
        e.setStatus("RUNNING");
        e.setUpdateTime(LocalDateTime.now());
        componentMapper.updateById(e);
        return Result.ok();
    }

    @PostMapping("/component/{id}/stop")
    public Result<Void> stopComponent(@PathVariable Long id) {
        InfraComponent e = componentMapper.selectById(id);
        e.setStatus("STOPPED");
        e.setUpdateTime(LocalDateTime.now());
        componentMapper.updateById(e);
        return Result.ok();
    }

    @DeleteMapping("/component/{id}")
    public Result<Void> deleteComponent(@PathVariable Long id) {
        componentMapper.deleteById(id);
        return Result.ok();
    }

    @GetMapping("/cluster/page")
    public Result<PageResult<InfraCluster>> clusterPage(
            @RequestParam(defaultValue = "1") long page, @RequestParam(defaultValue = "10") long size) {
        LambdaQueryWrapper<InfraCluster> qw = new LambdaQueryWrapper<InfraCluster>().eq(InfraCluster::getTenantId, tid());
        Page<InfraCluster> p = clusterMapper.selectPage(new Page<>(page, size), qw);
        return Result.ok(PageResult.of(p.getTotal(), page, size, p.getRecords()));
    }

    @PostMapping("/cluster")
    public Result<Void> createCluster(@RequestBody InfraCluster e) {
        e.setTenantId(tid());
        e.setCreateTime(LocalDateTime.now());
        clusterMapper.insert(e);
        return Result.ok();
    }

    @PutMapping("/cluster/{id}")
    public Result<Void> updateCluster(@PathVariable Long id, @RequestBody InfraCluster e) {
        e.setId(id);
        clusterMapper.updateById(e);
        return Result.ok();
    }

    @DeleteMapping("/cluster/{id}")
    public Result<Void> deleteCluster(@PathVariable Long id) {
        clusterMapper.deleteById(id);
        return Result.ok();
    }

    @GetMapping("/dashboard")
    public Result<Map<String, Object>> dashboard() {
        Long tid = tid();
        Map<String, Object> stats = new HashMap<>();
        stats.put("hostCount", hostMapper.selectCount(new LambdaQueryWrapper<InfraHost>().eq(InfraHost::getTenantId, tid)));
        stats.put("onlineHosts", hostMapper.selectCount(new LambdaQueryWrapper<InfraHost>()
                .eq(InfraHost::getTenantId, tid).eq(InfraHost::getStatus, "ONLINE")));
        stats.put("componentCount", componentMapper.selectCount(new LambdaQueryWrapper<InfraComponent>().eq(InfraComponent::getTenantId, tid)));
        stats.put("runningComponents", componentMapper.selectCount(new LambdaQueryWrapper<InfraComponent>()
                .eq(InfraComponent::getTenantId, tid).eq(InfraComponent::getStatus, "RUNNING")));
        stats.put("clusterCount", clusterMapper.selectCount(new LambdaQueryWrapper<InfraCluster>().eq(InfraCluster::getTenantId, tid)));
        return Result.ok(stats);
    }
}
