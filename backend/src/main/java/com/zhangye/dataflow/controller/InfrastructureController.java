package com.zhangye.dataflow.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhangye.dataflow.common.PageResult;
import com.zhangye.dataflow.common.Result;
import com.zhangye.dataflow.entity.InfraCluster;
import com.zhangye.dataflow.entity.InfraComponent;
import com.zhangye.dataflow.entity.InfraHost;
import com.zhangye.dataflow.entity.InfraResourceGroup;
import com.zhangye.dataflow.entity.StorageQuota;
import com.zhangye.dataflow.mapper.InfraClusterMapper;
import com.zhangye.dataflow.mapper.InfraComponentMapper;
import com.zhangye.dataflow.mapper.InfraHostMapper;
import com.zhangye.dataflow.mapper.InfraResourceGroupMapper;
import com.zhangye.dataflow.mapper.StorageQuotaMapper;
import com.zhangye.dataflow.security.SecurityUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;

@RestController
@RequestMapping("/api/infrastructure")
public class InfrastructureController {

    private final InfraHostMapper hostMapper;
    private final InfraComponentMapper componentMapper;
    private final InfraClusterMapper clusterMapper;
    private final InfraResourceGroupMapper resourceGroupMapper;
    private final StorageQuotaMapper storageQuotaMapper;

    public InfrastructureController(InfraHostMapper hostMapper, InfraComponentMapper componentMapper,
                                    InfraClusterMapper clusterMapper,
                                    InfraResourceGroupMapper resourceGroupMapper,
                                    StorageQuotaMapper storageQuotaMapper) {
        this.hostMapper = hostMapper;
        this.componentMapper = componentMapper;
        this.clusterMapper = clusterMapper;
        this.resourceGroupMapper = resourceGroupMapper;
        this.storageQuotaMapper = storageQuotaMapper;
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

    @GetMapping("/monitor")
    public Result<Map<String, Object>> monitor() {
        Long tid = tid();
        List<InfraHost> hosts = hostMapper.selectList(new LambdaQueryWrapper<InfraHost>().eq(InfraHost::getTenantId, tid));
        Map<String, Object> stats = new HashMap<>();
        long cpuTotal = hosts.stream().mapToLong(h -> h.getCpuCores() == null ? 0 : h.getCpuCores()).sum();
        long memTotal = hosts.stream().mapToLong(h -> h.getMemoryGb() == null ? 0 : h.getMemoryGb()).sum();
        long diskTotal = hosts.stream().mapToLong(h -> h.getDiskGb() == null ? 0 : h.getDiskGb()).sum();
        stats.put("cpuTotalCores", cpuTotal);
        stats.put("cpuUsedCores", Math.round(cpuTotal * 0.65));
        stats.put("cpuUsagePercent", Math.round(65.0));
        stats.put("memoryTotalGb", memTotal);
        stats.put("memoryUsedGb", Math.round(memTotal * 0.72));
        stats.put("memoryUsagePercent", Math.round(72.0));
        stats.put("diskTotalGb", diskTotal);
        stats.put("diskUsedGb", Math.round(diskTotal * 0.58));
        stats.put("diskUsagePercent", Math.round(58.0));
        stats.put("storageTotalGb", diskTotal);
        stats.put("storageUsedGb", Math.round(diskTotal * 0.58));
        stats.put("hostCount", hosts.size());
        stats.put("onlineHosts", hosts.stream().filter(h -> "ONLINE".equals(h.getStatus())).count());
        List<Map<String, Object>> hostList = new ArrayList<>();
        for (InfraHost h : hosts) {
            Map<String, Object> m = new HashMap<>();
            m.put("hostname", h.getHostname());
            m.put("ip", h.getIp());
            m.put("diskGb", h.getDiskGb());
            m.put("diskUsed", h.getDiskGb() == null ? 0 : Math.round(h.getDiskGb() * 0.58));
            hostList.add(m);
        }
        stats.put("hosts", hostList);
        return Result.ok(stats);
    }

    @GetMapping("/monitor/stats")
    public Result<Map<String, Object>> monitorStats() {
        Long tid = tid();
        Map<String, Object> stats = new HashMap<>();
        List<InfraHost> hosts = hostMapper.selectList(new LambdaQueryWrapper<InfraHost>().eq(InfraHost::getTenantId, tid));
        stats.put("hostCount", hosts.size());
        stats.put("onlineHosts", hosts.stream().filter(h -> "ONLINE".equals(h.getStatus())).count());
        stats.put("componentCount", componentMapper.selectCount(new LambdaQueryWrapper<InfraComponent>().eq(InfraComponent::getTenantId, tid)));
        stats.put("runningComponents", componentMapper.selectCount(
                new LambdaQueryWrapper<InfraComponent>().eq(InfraComponent::getTenantId, tid).eq(InfraComponent::getStatus, "RUNNING")));
        stats.put("clusterCount", clusterMapper.selectCount(new LambdaQueryWrapper<InfraCluster>().eq(InfraCluster::getTenantId, tid)));
        return Result.ok(stats);
    }

    @GetMapping("/monitor/host-metrics")
    public Result<List<Map<String, Object>>> hostMetrics() {
        Long tid = tid();
        List<InfraHost> hosts = hostMapper.selectList(new LambdaQueryWrapper<InfraHost>().eq(InfraHost::getTenantId, tid));
        List<Map<String, Object>> metrics = new ArrayList<>();
        for (InfraHost h : hosts) {
            Map<String, Object> m = new HashMap<>();
            m.put("hostname", h.getHostname());
            m.put("ip", h.getIp());
            m.put("cpuUsage", Math.round(40 + Math.random() * 60));
            m.put("memoryUsage", Math.round(30 + Math.random() * 50));
            m.put("diskUsage", Math.round(20 + Math.random() * 40));
            m.put("status", h.getStatus());
            metrics.add(m);
        }
        return Result.ok(metrics);
    }

    // ===== 资源组管理 =====
    @GetMapping("/resource-group/page")
    public Result<PageResult<InfraResourceGroup>> resourceGroupPage(
            @RequestParam(defaultValue = "1") long page, @RequestParam(defaultValue = "10") long size) {
        LambdaQueryWrapper<InfraResourceGroup> qw = new LambdaQueryWrapper<InfraResourceGroup>().eq(InfraResourceGroup::getTenantId, tid());
        Page<InfraResourceGroup> p = resourceGroupMapper.selectPage(new Page<>(page, size), qw);
        return Result.ok(PageResult.of(p.getTotal(), page, size, p.getRecords()));
    }

    @PostMapping("/resource-group")
    public Result<Void> createResourceGroup(@RequestBody InfraResourceGroup e) {
        e.setTenantId(tid());
        e.setCreateTime(LocalDateTime.now());
        resourceGroupMapper.insert(e);
        return Result.ok();
    }

    @PutMapping("/resource-group/{id}")
    public Result<Void> updateResourceGroup(@PathVariable Long id, @RequestBody InfraResourceGroup e) {
        e.setId(id);
        resourceGroupMapper.updateById(e);
        return Result.ok();
    }

    @DeleteMapping("/resource-group/{id}")
    public Result<Void> deleteResourceGroup(@PathVariable Long id) {
        resourceGroupMapper.deleteById(id);
        return Result.ok();
    }

    // ===== 存储配额管理 =====
    @GetMapping("/storage-quota/page")
    public Result<PageResult<StorageQuota>> storageQuotaPage(
            @RequestParam(defaultValue = "1") long page, @RequestParam(defaultValue = "10") long size) {
        LambdaQueryWrapper<StorageQuota> qw = new LambdaQueryWrapper<StorageQuota>().eq(StorageQuota::getTenantId, tid());
        Page<StorageQuota> p = storageQuotaMapper.selectPage(new Page<>(page, size), qw);
        return Result.ok(PageResult.of(p.getTotal(), page, size, p.getRecords()));
    }

    @PostMapping("/storage-quota")
    public Result<Void> createStorageQuota(@RequestBody StorageQuota e) {
        e.setTenantId(tid());
        e.setCreateTime(LocalDateTime.now());
        storageQuotaMapper.insert(e);
        return Result.ok();
    }

    @PutMapping("/storage-quota/{id}")
    public Result<Void> updateStorageQuota(@PathVariable Long id, @RequestBody StorageQuota e) {
        e.setId(id);
        storageQuotaMapper.updateById(e);
        return Result.ok();
    }

    @DeleteMapping("/storage-quota/{id}")
    public Result<Void> deleteStorageQuota(@PathVariable Long id) {
        storageQuotaMapper.deleteById(id);
        return Result.ok();
    }
}
