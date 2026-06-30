package com.zhangye.dataflow.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhangye.dataflow.common.BusinessException;
import com.zhangye.dataflow.common.PageResult;
import com.zhangye.dataflow.common.Result;
import com.zhangye.dataflow.entity.AggDataflow;
import com.zhangye.dataflow.entity.AggDatasource;
import com.zhangye.dataflow.entity.AggIncrementField;
import com.zhangye.dataflow.mapper.AggDataflowMapper;
import com.zhangye.dataflow.mapper.AggDatasourceMapper;
import com.zhangye.dataflow.mapper.AggIncrementFieldMapper;
import com.zhangye.dataflow.security.SecurityUtils;
import com.zhangye.dataflow.service.OperLogService;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/aggregation")
public class AggregationController {

    private final AggDatasourceMapper datasourceMapper;
    private final AggIncrementFieldMapper incrementFieldMapper;
    private final AggDataflowMapper dataflowMapper;
    private final OperLogService operLogService;

    public AggregationController(AggDatasourceMapper datasourceMapper,
                                 AggIncrementFieldMapper incrementFieldMapper,
                                 AggDataflowMapper dataflowMapper,
                                 OperLogService operLogService) {
        this.datasourceMapper = datasourceMapper;
        this.incrementFieldMapper = incrementFieldMapper;
        this.dataflowMapper = dataflowMapper;
        this.operLogService = operLogService;
    }

    // ---------- 数据源 ----------
    @GetMapping("/datasource/page")
    public Result<PageResult<AggDatasource>> datasourcePage(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String dbType) {
        LambdaQueryWrapper<AggDatasource> qw = baseTenantWrapper();
        if (StringUtils.hasText(name)) {
            qw.like(AggDatasource::getName, name);
        }
        if (StringUtils.hasText(dbType)) {
            qw.eq(AggDatasource::getDbType, dbType);
        }
        Page<AggDatasource> p = datasourceMapper.selectPage(new Page<>(page, size), qw);
        return Result.ok(PageResult.of(p.getTotal(), page, size, p.getRecords()));
    }

    @GetMapping("/datasource/list")
    public Result<List<AggDatasource>> datasourceList() {
        return Result.ok(datasourceMapper.selectList(baseTenantWrapper()));
    }

    @PostMapping("/datasource")
    public Result<Void> createDatasource(@RequestBody AggDatasource ds, HttpServletRequest request) {
        ds.setTenantId(SecurityUtils.getCurrentTenantId());
        ds.setCreateBy(SecurityUtils.getCurrentUsername());
        ds.setCreateTime(LocalDateTime.now());
        ds.setUpdateTime(LocalDateTime.now());
        ds.setLocked(0);
        datasourceMapper.insert(ds);
        operLogService.log(request, "新增数据源", "数据源管理", ds.getName(), true, null);
        return Result.ok();
    }

    @PutMapping("/datasource/{id}")
    public Result<Void> updateDatasource(@PathVariable Long id, @RequestBody AggDatasource ds, HttpServletRequest request) {
        AggDatasource existing = datasourceMapper.selectById(id);
        if (existing != null && existing.getLocked() != null && existing.getLocked() == 1) {
            throw new BusinessException("已建立数据流任务的数据源不允许编辑");
        }
        ds.setId(id);
        ds.setUpdateTime(LocalDateTime.now());
        datasourceMapper.updateById(ds);
        operLogService.log(request, "编辑数据源", "数据源管理", ds.getName(), true, null);
        return Result.ok();
    }

    @DeleteMapping("/datasource/{id}")
    public Result<Void> deleteDatasource(@PathVariable Long id, HttpServletRequest request) {
        AggDatasource ds = datasourceMapper.selectById(id);
        if (ds != null && ds.getLocked() != null && ds.getLocked() == 1) {
            throw new BusinessException("已建立数据流任务的数据源不允许删除");
        }
        datasourceMapper.deleteById(id);
        operLogService.log(request, "删除数据源", "数据源管理", ds != null ? ds.getName() : String.valueOf(id), true, null);
        return Result.ok();
    }

    @DeleteMapping("/datasource/batch")
    public Result<Void> batchDeleteDatasource(@RequestBody List<Long> ids, HttpServletRequest request) {
        for (Long id : ids) {
            deleteDatasource(id, request);
        }
        return Result.ok();
    }

    // ---------- 增量字段 ----------
    @GetMapping("/increment/page")
    public Result<PageResult<AggIncrementField>> incrementPage(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size,
            @RequestParam(required = false) String tableName) {
        LambdaQueryWrapper<AggIncrementField> qw = new LambdaQueryWrapper<>();
        qw.eq(AggIncrementField::getTenantId, SecurityUtils.getCurrentTenantId());
        if (StringUtils.hasText(tableName)) {
            qw.like(AggIncrementField::getTableName, tableName);
        }
        Page<AggIncrementField> p = incrementFieldMapper.selectPage(new Page<>(page, size), qw);
        return Result.ok(PageResult.of(p.getTotal(), page, size, p.getRecords()));
    }

    @PostMapping("/increment")
    public Result<Void> createIncrement(@RequestBody AggIncrementField field, HttpServletRequest request) {
        field.setTenantId(SecurityUtils.getCurrentTenantId());
        field.setCreateTime(LocalDateTime.now());
        field.setUpdateTime(LocalDateTime.now());
        incrementFieldMapper.insert(field);
        operLogService.log(request, "添加增量字段", "增量字段管理", field.getTableName(), true, null);
        return Result.ok();
    }

    @PutMapping("/increment/{id}")
    public Result<Void> updateIncrement(@PathVariable Long id, @RequestBody AggIncrementField field, HttpServletRequest request) {
        field.setId(id);
        field.setUpdateTime(LocalDateTime.now());
        incrementFieldMapper.updateById(field);
        operLogService.log(request, "编辑增量字段", "增量字段管理", field.getTableName(), true, null);
        return Result.ok();
    }

    @DeleteMapping("/increment/{id}")
    public Result<Void> deleteIncrement(@PathVariable Long id, HttpServletRequest request) {
        AggIncrementField field = incrementFieldMapper.selectById(id);
        incrementFieldMapper.deleteById(id);
        operLogService.log(request, "删除增量字段", "增量字段管理", field != null ? field.getTableName() : String.valueOf(id), true, null);
        return Result.ok();
    }

    // ---------- 数据流 ----------
    @GetMapping("/dataflow/page")
    public Result<PageResult<AggDataflow>> dataflowPage(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String status) {
        LambdaQueryWrapper<AggDataflow> qw = new LambdaQueryWrapper<>();
        qw.eq(AggDataflow::getTenantId, SecurityUtils.getCurrentTenantId());
        if (StringUtils.hasText(name)) {
            qw.like(AggDataflow::getName, name);
        }
        if (StringUtils.hasText(status)) {
            qw.eq(AggDataflow::getStatus, status);
        }
        Page<AggDataflow> p = dataflowMapper.selectPage(new Page<>(page, size), qw);
        fillDataflowNames(p.getRecords());
        return Result.ok(PageResult.of(p.getTotal(), page, size, p.getRecords()));
    }

    @PostMapping("/dataflow")
    public Result<Void> createDataflow(@RequestBody AggDataflow flow, HttpServletRequest request) {
        flow.setTenantId(SecurityUtils.getCurrentTenantId());
        flow.setCreateBy(SecurityUtils.getCurrentUsername());
        flow.setCreateTime(LocalDateTime.now());
        flow.setUpdateTime(LocalDateTime.now());
        flow.setStatus("NOT_STARTED");
        dataflowMapper.insert(flow);
        lockDatasource(flow.getSourceId());
        lockDatasource(flow.getTargetId());
        operLogService.log(request, "新建数据流", "数据流管理", flow.getName(), true, null);
        return Result.ok();
    }

    @PutMapping("/dataflow/{id}")
    public Result<Void> updateDataflow(@PathVariable Long id, @RequestBody AggDataflow flow, HttpServletRequest request) {
        flow.setId(id);
        flow.setUpdateTime(LocalDateTime.now());
        dataflowMapper.updateById(flow);
        operLogService.log(request, "编辑数据流", "数据流管理", flow.getName(), true, null);
        return Result.ok();
    }

    @DeleteMapping("/dataflow/{id}")
    public Result<Void> deleteDataflow(@PathVariable Long id, HttpServletRequest request) {
        AggDataflow flow = dataflowMapper.selectById(id);
        dataflowMapper.deleteById(id);
        operLogService.log(request, "删除数据流", "数据流管理", flow != null ? flow.getName() : String.valueOf(id), true, null);
        return Result.ok();
    }

    @PostMapping("/dataflow/{id}/execute")
    public Result<Void> executeDataflow(@PathVariable Long id, HttpServletRequest request) {
        AggDataflow flow = dataflowMapper.selectById(id);
        if (flow == null) {
            throw new BusinessException("数据流不存在");
        }
        flow.setStatus("SUCCESS");
        flow.setReadCount(flow.getReadCount() == null ? 1000L : flow.getReadCount() + 1000);
        flow.setWriteCount(flow.getWriteCount() == null ? 1000L : flow.getWriteCount() + 1000);
        flow.setLastRunTime(LocalDateTime.now());
        flow.setUpdateTime(LocalDateTime.now());
        dataflowMapper.updateById(flow);
        operLogService.log(request, "执行数据流", "数据流管理", flow.getName(), true, "模拟同步成功");
        return Result.ok();
    }

    // ---------- 监控统计 ----------
    @GetMapping("/monitor/stats")
    public Result<Map<String, Object>> monitorStats() {
        Long tenantId = SecurityUtils.getCurrentTenantId();
        List<AggDataflow> flows = dataflowMapper.selectList(new LambdaQueryWrapper<AggDataflow>()
                .eq(AggDataflow::getTenantId, tenantId));
        Map<String, Long> statusCount = flows.stream()
                .collect(Collectors.groupingBy(f -> f.getStatus() == null ? "NOT_STARTED" : f.getStatus(), Collectors.counting()));
        long totalRead = flows.stream().mapToLong(f -> f.getReadCount() == null ? 0 : f.getReadCount()).sum();
        long totalWrite = flows.stream().mapToLong(f -> f.getWriteCount() == null ? 0 : f.getWriteCount()).sum();
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalExecutions", flows.size());
        stats.put("statusCount", statusCount);
        stats.put("totalRead", totalRead);
        stats.put("totalWrite", totalWrite);
        stats.put("datasourceCount", datasourceMapper.selectCount(baseTenantWrapper()));
        return Result.ok(stats);
    }

    private LambdaQueryWrapper<AggDatasource> baseTenantWrapper() {
        LambdaQueryWrapper<AggDatasource> qw = new LambdaQueryWrapper<>();
        qw.eq(AggDatasource::getTenantId, SecurityUtils.getCurrentTenantId());
        qw.orderByDesc(AggDatasource::getCreateTime);
        return qw;
    }

    private void lockDatasource(Long id) {
        if (id == null) {
            return;
        }
        AggDatasource ds = datasourceMapper.selectById(id);
        if (ds != null) {
            ds.setLocked(1);
            datasourceMapper.updateById(ds);
        }
    }

    private void fillDataflowNames(List<AggDataflow> flows) {
        if (flows.isEmpty()) {
            return;
        }
        Set<Long> ids = new HashSet<>();
        flows.forEach(f -> {
            ids.add(f.getSourceId());
            ids.add(f.getTargetId());
        });
        Map<Long, String> nameMap = datasourceMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(AggDatasource::getId, AggDatasource::getName));
        flows.forEach(f -> {
            f.setSourceName(nameMap.get(f.getSourceId()));
            f.setTargetName(nameMap.get(f.getTargetId()));
        });
    }
}
