package com.zhangye.dataflow.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhangye.dataflow.common.PageResult;
import com.zhangye.dataflow.common.Result;
import com.zhangye.dataflow.entity.InfraCluster;
import com.zhangye.dataflow.entity.InfraAlert;
import com.zhangye.dataflow.entity.InfraComponent;
import com.zhangye.dataflow.entity.InfraHost;
import com.zhangye.dataflow.entity.InfraRepository;
import com.zhangye.dataflow.entity.InfraResourceGroup;
import com.zhangye.dataflow.entity.StorageQuota;
import com.zhangye.dataflow.entity.SysConfig;
import com.zhangye.dataflow.entity.SysOperLog;
import com.zhangye.dataflow.entity.GovProxyNode;
import com.zhangye.dataflow.mapper.InfraClusterMapper;
import com.zhangye.dataflow.mapper.InfraAlertMapper;
import com.zhangye.dataflow.mapper.InfraComponentMapper;
import com.zhangye.dataflow.mapper.InfraHostMapper;
import com.zhangye.dataflow.mapper.InfraRepositoryMapper;
import com.zhangye.dataflow.mapper.InfraResourceGroupMapper;
import com.zhangye.dataflow.mapper.StorageQuotaMapper;
import com.zhangye.dataflow.mapper.SysConfigMapper;
import com.zhangye.dataflow.mapper.SysOperLogMapper;
import com.zhangye.dataflow.mapper.GovProxyNodeMapper;
import com.zhangye.dataflow.security.SecurityUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/infrastructure")
public class InfrastructureController {

    private final InfraHostMapper hostMapper;
    private final InfraComponentMapper componentMapper;
    private final InfraClusterMapper clusterMapper;
    private final InfraResourceGroupMapper resourceGroupMapper;
    private final StorageQuotaMapper storageQuotaMapper;
    private final InfraRepositoryMapper repositoryMapper;
    private final InfraAlertMapper alertMapper;
    private final SysConfigMapper configMapper;
    private final SysOperLogMapper operLogMapper;
    private final GovProxyNodeMapper proxyNodeMapper;

    public InfrastructureController(InfraHostMapper hostMapper, InfraComponentMapper componentMapper,
                                    InfraClusterMapper clusterMapper,
                                    InfraResourceGroupMapper resourceGroupMapper,
                                    StorageQuotaMapper storageQuotaMapper,
                                    InfraRepositoryMapper repositoryMapper,
                                    InfraAlertMapper alertMapper,
                                    SysConfigMapper configMapper,
                                    SysOperLogMapper operLogMapper,
                                    GovProxyNodeMapper proxyNodeMapper) {
        this.hostMapper = hostMapper;
        this.componentMapper = componentMapper;
        this.clusterMapper = clusterMapper;
        this.resourceGroupMapper = resourceGroupMapper;
        this.storageQuotaMapper = storageQuotaMapper;
        this.repositoryMapper = repositoryMapper;
        this.alertMapper = alertMapper;
        this.configMapper = configMapper;
        this.operLogMapper = operLogMapper;
        this.proxyNodeMapper = proxyNodeMapper;
    }

    private Long tid() { return SecurityUtils.getCurrentTenantId(); }

    @GetMapping("/overview")
    public Result<Map<String, Object>> overview() {
        Long tenantId = tid();
        List<InfraHost> hosts = hostMapper.selectList(new LambdaQueryWrapper<InfraHost>().eq(InfraHost::getTenantId, tenantId));
        List<InfraComponent> components = componentMapper.selectList(new LambdaQueryWrapper<InfraComponent>().eq(InfraComponent::getTenantId, tenantId));
        List<InfraAlert> alerts = alertMapper.selectList(new LambdaQueryWrapper<InfraAlert>()
                .eq(InfraAlert::getTenantId, tenantId)
                .orderByDesc(InfraAlert::getLastTriggerTime)
                .last("LIMIT 6"));

        Map<String, Object> overview = new LinkedHashMap<>();
        overview.put("hostCount", hosts.size());
        overview.put("onlineHosts", hosts.stream().filter(h -> "ONLINE".equals(h.getStatus())).count());
        overview.put("componentCount", components.size());
        overview.put("runningComponents", components.stream().filter(c -> "RUNNING".equals(c.getStatus())).count());
        overview.put("clusterCount", clusterMapper.selectCount(new LambdaQueryWrapper<InfraCluster>().eq(InfraCluster::getTenantId, tenantId)));
        overview.put("resourceGroupCount", resourceGroupMapper.selectCount(new LambdaQueryWrapper<InfraResourceGroup>().eq(InfraResourceGroup::getTenantId, tenantId)));
        overview.put("repositoryCount", repositoryMapper.selectCount(new LambdaQueryWrapper<InfraRepository>().eq(InfraRepository::getTenantId, tenantId)));
        overview.put("activeAlertCount", alertMapper.selectCount(new LambdaQueryWrapper<InfraAlert>()
                .eq(InfraAlert::getTenantId, tenantId).in(InfraAlert::getStatus, Arrays.asList("ACTIVE", "ACKED"))));
        overview.put("proxyCount", proxyNodeMapper.selectCount(new LambdaQueryWrapper<GovProxyNode>().eq(GovProxyNode::getTenantId, tenantId)));
        overview.put("recentOperationCount", operLogMapper.selectCount(new LambdaQueryWrapper<SysOperLog>().eq(SysOperLog::getTenantId, tenantId)));
        overview.put("componentStatus", countBy(components, InfraComponent::getStatus));
        overview.put("hostStatus", countBy(hosts, InfraHost::getStatus));
        overview.put("componentCoverage", buildComponentCoverage(components));
        overview.put("configFiles", buildConfigFileRows());
        overview.put("alerts", alerts);
        overview.put("resourceSummary", monitor().getData());
        return Result.ok(overview);
    }

    private <T> Map<String, Long> countBy(List<T> list, java.util.function.Function<T, String> getter) {
        return list.stream()
                .collect(Collectors.groupingBy(e -> {
                    String value = getter.apply(e);
                    return StringUtils.hasText(value) ? value : "UNKNOWN";
                }, LinkedHashMap::new, Collectors.counting()));
    }

    private List<Map<String, Object>> buildComponentCoverage(List<InfraComponent> components) {
        List<String> required = Arrays.asList("HDFS", "YARN", "ZooKeeper", "Kafka", "Flink", "Hive", "Kerberos", "Ranger", "Hudi");
        Map<String, List<InfraComponent>> grouped = components.stream()
                .collect(Collectors.groupingBy(c -> normalizeComponentType(c.getComponentType())));
        List<Map<String, Object>> rows = new ArrayList<>();
        for (String name : required) {
            List<InfraComponent> items = grouped.getOrDefault(normalizeComponentType(name), Collections.emptyList());
            long running = items.stream().filter(c -> "RUNNING".equals(c.getStatus())).count();
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("name", name);
            row.put("instances", items.size());
            row.put("running", running);
            row.put("status", items.isEmpty() ? "MISSING" : (running == items.size() ? "RUNNING" : "ATTENTION"));
            rows.add(row);
        }
        return rows;
    }

    private String normalizeComponentType(String value) {
        return value == null ? "" : value.replace("-", "").replace("_", "").toUpperCase(Locale.ROOT);
    }

    private List<Map<String, Object>> buildConfigFileRows() {
        List<Map<String, String>> defs = configDefinitions();
        List<SysConfig> configs = configMapper.selectList(new LambdaQueryWrapper<SysConfig>()
                .likeRight(SysConfig::getConfigKey, "infra.config."));
        Map<String, SysConfig> configMap = configs.stream().collect(Collectors.toMap(SysConfig::getConfigKey, c -> c, (a, b) -> a));
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Map<String, String> def : defs) {
            SysConfig config = configMap.get(def.get("key"));
            Map<String, Object> row = new LinkedHashMap<>();
            row.putAll(def);
            row.put("configured", config != null && StringUtils.hasText(config.getConfigValue()));
            row.put("configValue", config == null ? defaultConfigValue(def.get("fileName")) : config.getConfigValue());
            row.put("updateTime", config == null ? null : config.getUpdateTime());
            rows.add(row);
        }
        return rows;
    }

    private List<Map<String, String>> configDefinitions() {
        List<Map<String, String>> defs = new ArrayList<>();
        defs.add(configDef("infra.config.core-site.xml", "core-site.xml", "Hadoop", "HDFS/YARN 基础连接配置", "1"));
        defs.add(configDef("infra.config.hdfs-site.xml", "hdfs-site.xml", "HDFS", "NameNode、DataNode 与副本策略", "1"));
        defs.add(configDef("infra.config.yarn-site.xml", "yarn-site.xml", "YARN", "ResourceManager 与队列资源配置", "1"));
        defs.add(configDef("infra.config.hive-site.xml", "hive-site.xml", "Hive", "Hive Metastore 与同步配置", "0"));
        defs.add(configDef("infra.config.flink-runtime", "flink-runtime.yaml", "Flink", "Flink SQL 作业运行环境", "0"));
        defs.add(configDef("infra.config.hudi-sync", "hudi-sync.properties", "Hudi", "Hudi 初始化、增量同步与 Hive 同步", "0"));
        return defs;
    }

    private Map<String, String> configDef(String key, String fileName, String component, String description, String required) {
        Map<String, String> def = new LinkedHashMap<>();
        def.put("key", key);
        def.put("fileName", fileName);
        def.put("component", component);
        def.put("description", description);
        def.put("required", required);
        return def;
    }

    private String defaultConfigValue(String fileName) {
        if (fileName.endsWith(".xml")) {
            return "<configuration>\n  <property>\n    <name></name>\n    <value></value>\n  </property>\n</configuration>";
        }
        if (fileName.endsWith(".yaml")) {
            return "jobmanager.rpc.address: hadoop132\nparallelism.default: 2";
        }
        return "hoodie.datasource.hive_sync.enable=true\nhoodie.datasource.hive_sync.jdbcurl=jdbc:hive2://192.168.184.132:10000";
    }

    @GetMapping("/host/page")
    public Result<PageResult<InfraHost>> hostPage(
            @RequestParam(defaultValue = "1") long page, @RequestParam(defaultValue = "10") long size,
            @RequestParam(required = false) String hostname,
            @RequestParam(required = false) String status) {
        LambdaQueryWrapper<InfraHost> qw = new LambdaQueryWrapper<InfraHost>().eq(InfraHost::getTenantId, tid());
        if (StringUtils.hasText(hostname)) qw.like(InfraHost::getHostname, hostname);
        if (StringUtils.hasText(status)) qw.eq(InfraHost::getStatus, status);
        qw.orderByDesc(InfraHost::getCreateTime);
        Page<InfraHost> p = hostMapper.selectPage(new Page<>(page, size), qw);
        return Result.ok(PageResult.of(p.getTotal(), page, size, p.getRecords()));
    }

    @PostMapping("/host")
    public Result<Void> createHost(@RequestBody InfraHost e) {
        e.setTenantId(tid());
        if (!StringUtils.hasText(e.getStatus())) {
            e.setStatus("ONLINE");
        }
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

    @PostMapping("/host/{id}/online")
    public Result<Void> onlineHost(@PathVariable Long id) {
        updateHostStatus(Collections.singletonList(id), "ONLINE");
        return Result.ok();
    }

    @PostMapping("/host/{id}/offline")
    public Result<Void> offlineHost(@PathVariable Long id) {
        updateHostStatus(Collections.singletonList(id), "OFFLINE");
        return Result.ok();
    }

    @PostMapping("/host/batch-status")
    public Result<Void> batchHostStatus(@RequestBody Map<String, Object> body) {
        updateHostStatus(readIds(body.get("ids")), String.valueOf(body.getOrDefault("status", "ONLINE")));
        return Result.ok();
    }

    @PostMapping("/host/batch-delete")
    public Result<Void> batchDeleteHost(@RequestBody Map<String, Object> body) {
        for (Long id : readIds(body.get("ids"))) {
            hostMapper.deleteById(id);
        }
        return Result.ok();
    }

    private void updateHostStatus(List<Long> ids, String status) {
        for (Long id : ids) {
            InfraHost host = hostMapper.selectById(id);
            if (host != null && Objects.equals(host.getTenantId(), tid())) {
                host.setStatus(status);
                hostMapper.updateById(host);
            }
        }
    }

    private List<Long> readIds(Object raw) {
        if (!(raw instanceof Collection<?>)) {
            return Collections.emptyList();
        }
        List<Long> ids = new ArrayList<>();
        for (Object value : (Collection<?>) raw) {
            if (value instanceof Number) {
                ids.add(((Number) value).longValue());
            } else if (value != null) {
                ids.add(Long.parseLong(String.valueOf(value)));
            }
        }
        return ids;
    }

    @GetMapping("/component/page")
    public Result<PageResult<InfraComponent>> componentPage(
            @RequestParam(defaultValue = "1") long page, @RequestParam(defaultValue = "10") long size,
            @RequestParam(required = false) String componentType,
            @RequestParam(required = false) String status) {
        LambdaQueryWrapper<InfraComponent> qw = new LambdaQueryWrapper<InfraComponent>().eq(InfraComponent::getTenantId, tid());
        if (StringUtils.hasText(componentType)) qw.eq(InfraComponent::getComponentType, componentType);
        if (StringUtils.hasText(status)) qw.eq(InfraComponent::getStatus, status);
        qw.orderByDesc(InfraComponent::getUpdateTime);
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
        if (e == null) return Result.fail("组件不存在");
        e.setStatus("RUNNING");
        e.setUpdateTime(LocalDateTime.now());
        componentMapper.updateById(e);
        return Result.ok();
    }

    @PostMapping("/component/{id}/stop")
    public Result<Void> stopComponent(@PathVariable Long id) {
        InfraComponent e = componentMapper.selectById(id);
        if (e == null) return Result.fail("组件不存在");
        e.setStatus("STOPPED");
        e.setUpdateTime(LocalDateTime.now());
        componentMapper.updateById(e);
        return Result.ok();
    }

    @PostMapping("/component/{id}/restart")
    public Result<Void> restartComponent(@PathVariable Long id) {
        InfraComponent e = componentMapper.selectById(id);
        if (e == null) return Result.fail("组件不存在");
        e.setStatus("RUNNING");
        e.setUpdateTime(LocalDateTime.now());
        componentMapper.updateById(e);
        return Result.ok();
    }

    @PostMapping("/component/batch-status")
    public Result<Void> batchComponentStatus(@RequestBody Map<String, Object> body) {
        String status = String.valueOf(body.getOrDefault("status", "RUNNING"));
        for (Long id : readIds(body.get("ids"))) {
            InfraComponent component = componentMapper.selectById(id);
            if (component != null && Objects.equals(component.getTenantId(), tid())) {
                component.setStatus(status);
                component.setUpdateTime(LocalDateTime.now());
                componentMapper.updateById(component);
            }
        }
        return Result.ok();
    }

    @GetMapping("/component/config-templates")
    public Result<List<Map<String, Object>>> componentConfigTemplates() {
        List<Map<String, Object>> rows = new ArrayList<>();
        rows.add(template("HDFS", "NameNode HA", "dfs.nameservices, dfs.ha.namenodes, dfs.replication", "hdfs-site.xml"));
        rows.add(template("YARN", "多资源队列", "yarn.scheduler.capacity.root.queues, maximum-am-resource-percent", "yarn-site.xml"));
        rows.add(template("ZooKeeper", "仲裁节点", "tickTime, initLimit, syncLimit, dataDir", "zoo.cfg"));
        rows.add(template("Kafka", "Broker 集群", "broker.id, listeners, log.dirs, zookeeper.connect", "server.properties"));
        rows.add(template("Flink", "SQL 运行环境", "jobmanager.rpc.address, taskmanager.numberOfTaskSlots", "flink-conf.yaml"));
        rows.add(template("Hive", "Metastore", "hive.metastore.uris, javax.jdo.option.ConnectionURL", "hive-site.xml"));
        rows.add(template("Kerberos", "安全认证", "krb5.conf, keytab, principal", "krb5.conf"));
        rows.add(template("Ranger", "权限治理", "ranger.plugin.*.policy.rest.url", "ranger-admin-site.xml"));
        rows.add(template("Hudi", "湖仓同步", "hoodie.datasource.hive_sync.*, hoodie.clean.*", "hudi-sync.properties"));
        return Result.ok(rows);
    }

    private Map<String, Object> template(String component, String scene, String keys, String fileName) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("component", component);
        row.put("scene", scene);
        row.put("keys", keys);
        row.put("fileName", fileName);
        return row;
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

    @GetMapping("/repository/page")
    public Result<PageResult<InfraRepository>> repositoryPage(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size,
            @RequestParam(required = false) String componentType,
            @RequestParam(required = false) String packageName,
            @RequestParam(required = false) String status) {
        LambdaQueryWrapper<InfraRepository> qw = new LambdaQueryWrapper<InfraRepository>().eq(InfraRepository::getTenantId, tid());
        if (StringUtils.hasText(componentType)) qw.eq(InfraRepository::getComponentType, componentType);
        if (StringUtils.hasText(packageName)) qw.like(InfraRepository::getPackageName, packageName);
        if (StringUtils.hasText(status)) qw.eq(InfraRepository::getStatus, status);
        qw.orderByDesc(InfraRepository::getUpdateTime);
        Page<InfraRepository> p = repositoryMapper.selectPage(new Page<>(page, size), qw);
        return Result.ok(PageResult.of(p.getTotal(), page, size, p.getRecords()));
    }

    @PostMapping("/repository")
    public Result<Void> createRepository(@RequestBody InfraRepository e) {
        e.setTenantId(tid());
        if (e.getPackageSizeMb() == null) {
            e.setPackageSizeMb(BigDecimal.ZERO);
        }
        if (!StringUtils.hasText(e.getStatus())) {
            e.setStatus("READY");
        }
        e.setCreateTime(LocalDateTime.now());
        e.setUpdateTime(LocalDateTime.now());
        repositoryMapper.insert(e);
        return Result.ok();
    }

    @PutMapping("/repository/{id}")
    public Result<Void> updateRepository(@PathVariable Long id, @RequestBody InfraRepository e) {
        e.setId(id);
        e.setUpdateTime(LocalDateTime.now());
        repositoryMapper.updateById(e);
        return Result.ok();
    }

    @PostMapping("/repository/{id}/distribute")
    public Result<Void> distributeRepository(@PathVariable Long id) {
        InfraRepository e = repositoryMapper.selectById(id);
        if (e == null) {
            return Result.fail("部署包不存在");
        }
        e.setStatus("DISTRIBUTED");
        e.setUpdateTime(LocalDateTime.now());
        repositoryMapper.updateById(e);
        return Result.ok();
    }

    @DeleteMapping("/repository/{id}")
    public Result<Void> deleteRepository(@PathVariable Long id) {
        repositoryMapper.deleteById(id);
        return Result.ok();
    }

    @GetMapping("/alert/page")
    public Result<PageResult<InfraAlert>> alertPage(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size,
            @RequestParam(required = false) String alertLevel,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String targetName) {
        LambdaQueryWrapper<InfraAlert> qw = new LambdaQueryWrapper<InfraAlert>().eq(InfraAlert::getTenantId, tid());
        if (StringUtils.hasText(alertLevel)) qw.eq(InfraAlert::getAlertLevel, alertLevel);
        if (StringUtils.hasText(status)) qw.eq(InfraAlert::getStatus, status);
        if (StringUtils.hasText(targetName)) qw.like(InfraAlert::getTargetName, targetName);
        qw.orderByDesc(InfraAlert::getLastTriggerTime);
        Page<InfraAlert> p = alertMapper.selectPage(new Page<>(page, size), qw);
        return Result.ok(PageResult.of(p.getTotal(), page, size, p.getRecords()));
    }

    @PostMapping("/alert")
    public Result<Void> createAlert(@RequestBody InfraAlert e) {
        e.setTenantId(tid());
        if (!StringUtils.hasText(e.getStatus())) {
            e.setStatus("ACTIVE");
        }
        if (e.getLastTriggerTime() == null) {
            e.setLastTriggerTime(LocalDateTime.now());
        }
        e.setCreateTime(LocalDateTime.now());
        e.setUpdateTime(LocalDateTime.now());
        alertMapper.insert(e);
        return Result.ok();
    }

    @PutMapping("/alert/{id}")
    public Result<Void> updateAlert(@PathVariable Long id, @RequestBody InfraAlert e) {
        e.setId(id);
        e.setUpdateTime(LocalDateTime.now());
        alertMapper.updateById(e);
        return Result.ok();
    }

    @PostMapping("/alert/{id}/ack")
    public Result<Void> ackAlert(@PathVariable Long id) {
        updateAlertStatus(id, "ACKED");
        return Result.ok();
    }

    @PostMapping("/alert/{id}/recover")
    public Result<Void> recoverAlert(@PathVariable Long id) {
        updateAlertStatus(id, "RECOVERED");
        return Result.ok();
    }

    @DeleteMapping("/alert/{id}")
    public Result<Void> deleteAlert(@PathVariable Long id) {
        alertMapper.deleteById(id);
        return Result.ok();
    }

    private void updateAlertStatus(Long id, String status) {
        InfraAlert e = alertMapper.selectById(id);
        if (e != null && Objects.equals(e.getTenantId(), tid())) {
            e.setStatus(status);
            e.setUpdateTime(LocalDateTime.now());
            alertMapper.updateById(e);
        }
    }

    @GetMapping("/config-files")
    public Result<List<Map<String, Object>>> configFiles() {
        return Result.ok(buildConfigFileRows());
    }

    @PutMapping("/config-files")
    public Result<Void> saveConfigFile(@RequestParam String key, @RequestBody Map<String, String> body) {
        String value = body.get("configValue");
        if (value == null) {
            return Result.fail("configValue is required");
        }
        SysConfig config = configMapper.selectOne(new LambdaQueryWrapper<SysConfig>().eq(SysConfig::getConfigKey, key));
        if (config == null) {
            config = new SysConfig();
            config.setConfigKey(key);
            config.setDescription(body.getOrDefault("description", "基础支撑配置文件"));
            config.setEnv(body.getOrDefault("env", "DEV"));
            config.setConfigValue(value);
            config.setUpdateTime(LocalDateTime.now());
            configMapper.insert(config);
        } else {
            config.setConfigValue(value);
            if (StringUtils.hasText(body.get("description"))) {
                config.setDescription(body.get("description"));
            }
            if (StringUtils.hasText(body.get("env"))) {
                config.setEnv(body.get("env"));
            }
            config.setUpdateTime(LocalDateTime.now());
            configMapper.updateById(config);
        }
        return Result.ok();
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
