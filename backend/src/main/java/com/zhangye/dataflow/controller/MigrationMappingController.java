package com.zhangye.dataflow.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhangye.dataflow.common.PageResult;
import com.zhangye.dataflow.common.Result;
import com.zhangye.dataflow.entity.AggDataflow;
import com.zhangye.dataflow.entity.AggDatasource;
import com.zhangye.dataflow.entity.AggMigrationMapping;
import com.zhangye.dataflow.entity.AssetField;
import com.zhangye.dataflow.entity.AssetTable;
import com.zhangye.dataflow.mapper.AggDataflowMapper;
import com.zhangye.dataflow.mapper.AggDatasourceMapper;
import com.zhangye.dataflow.mapper.AggMigrationMappingMapper;
import com.zhangye.dataflow.mapper.AssetFieldMapper;
import com.zhangye.dataflow.mapper.AssetTableMapper;
import com.zhangye.dataflow.security.SecurityUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/aggregation/migration")
public class MigrationMappingController {

    private final AggMigrationMappingMapper mappingMapper;
    private final AssetTableMapper tableMapper;
    private final AssetFieldMapper fieldMapper;
    private final AggDataflowMapper dataflowMapper;
    private final AggDatasourceMapper datasourceMapper;

    public MigrationMappingController(AggMigrationMappingMapper mappingMapper,
                                      AssetTableMapper tableMapper,
                                      AssetFieldMapper fieldMapper,
                                      AggDataflowMapper dataflowMapper,
                                      AggDatasourceMapper datasourceMapper) {
        this.mappingMapper = mappingMapper;
        this.tableMapper = tableMapper;
        this.fieldMapper = fieldMapper;
        this.dataflowMapper = dataflowMapper;
        this.datasourceMapper = datasourceMapper;
    }

    private Long tid() { return SecurityUtils.getCurrentTenantId(); }

    @GetMapping("/page")
    public Result<PageResult<AggMigrationMapping>> page(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size,
            @RequestParam(required = false) String name) {
        LambdaQueryWrapper<AggMigrationMapping> qw = new LambdaQueryWrapper<AggMigrationMapping>()
                .eq(AggMigrationMapping::getTenantId, tid())
                .orderByDesc(AggMigrationMapping::getCreateTime);
        if (StringUtils.hasText(name)) {
            qw.like(AggMigrationMapping::getName, name);
        }
        Page<AggMigrationMapping> p = mappingMapper.selectPage(new Page<>(page, size), qw);
        enrichNames(p.getRecords());
        return Result.ok(PageResult.of(p.getTotal(), page, size, p.getRecords()));
    }

    @GetMapping("/list")
    public Result<List<AggMigrationMapping>> list() {
        List<AggMigrationMapping> items = mappingMapper.selectList(new LambdaQueryWrapper<AggMigrationMapping>()
                .eq(AggMigrationMapping::getTenantId, tid())
                .orderByDesc(AggMigrationMapping::getCreateTime));
        enrichNames(items);
        return Result.ok(items);
    }

    @PostMapping
    public Result<Map<String, Object>> create(@RequestBody AggMigrationMapping mapping) {
        mapping.setTenantId(tid());
        mapping.setCreateBy(SecurityUtils.getCurrentUsername());
        mapping.setCreateTime(LocalDateTime.now());
        mapping.setUpdateTime(LocalDateTime.now());
        mapping.setStatus("DRAFT");
        mappingMapper.insert(mapping);
        Map<String, Object> data = new HashMap<>();
        data.put("id", mapping.getId());
        data.put("name", mapping.getName());
        return Result.ok(data);
    }

    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody AggMigrationMapping mapping) {
        mapping.setId(id);
        mapping.setUpdateTime(LocalDateTime.now());
        mappingMapper.updateById(mapping);
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        mappingMapper.deleteById(id);
        return Result.ok();
    }

    @GetMapping("/{id}/validate")
    public Result<Map<String, Object>> validate(@PathVariable Long id) {
        AggMigrationMapping mapping = loadMapping(id);
        Map<String, Object> data = buildValidation(mapping);
        return Result.ok(data);
    }

    @PostMapping("/{id}/generate-dataflow")
    public Result<Map<String, Object>> generateDataflow(@PathVariable Long id) {
        AggMigrationMapping mapping = loadMapping(id);
        Map<String, Object> validation = buildValidation(mapping);
        if (!Boolean.TRUE.equals(validation.get("canGenerateDataflow"))) {
            return Result.fail("当前映射无法生成数据流，请先补齐源表、目标表和字段映射");
        }
        AssetTable sourceTable = tableMapper.selectById(mapping.getSourceTableId());
        AssetTable targetTable = tableMapper.selectById(mapping.getTargetTableId());
        Long sourceDatasourceId = sourceTable == null ? null : sourceTable.getDatasourceId();
        Long targetDatasourceId = targetTable == null ? null : targetTable.getDatasourceId();
        if (sourceDatasourceId == null || targetDatasourceId == null) {
            return Result.fail("源表或目标表未绑定数据源，无法生成数据流");
        }
        AggDataflow flow = new AggDataflow();
        flow.setTenantId(tid());
        flow.setName(mapping.getName() + "_flow");
        flow.setSourceId(sourceDatasourceId);
        flow.setTargetId(targetDatasourceId);
        flow.setWriteMode(mapping.getWriteMode());
        flow.setFaultTolerance(mapping.getFaultTolerance());
        flow.setPreSql(mapping.getPreSql());
        flow.setFieldMapping(mapping.getFieldMapping());
        flow.setStatus("NOT_STARTED");
        flow.setReadCount(0L);
        flow.setWriteCount(0L);
        flow.setCreateBy(SecurityUtils.getCurrentUsername());
        flow.setCreateTime(LocalDateTime.now());
        flow.setUpdateTime(LocalDateTime.now());
        dataflowMapper.insert(flow);
        lockDatasource(sourceDatasourceId);
        lockDatasource(targetDatasourceId);
        mapping.setStatus("GENERATED");
        mapping.setUpdateTime(LocalDateTime.now());
        mappingMapper.updateById(mapping);
        Map<String, Object> data = new HashMap<>();
        data.put("dataflowId", flow.getId());
        data.put("dataflowName", flow.getName());
        data.put("sourceDatasourceId", sourceDatasourceId);
        data.put("targetDatasourceId", targetDatasourceId);
        return Result.ok(data);
    }

    @GetMapping("/{id}")
    public Result<Map<String, Object>> detail(@PathVariable Long id) {
        AggMigrationMapping mapping = mappingMapper.selectById(id);
        if (mapping == null) {
            return Result.fail("记录不存在");
        }
        enrichNames(Collections.singletonList(mapping));
        AssetTable source = tableMapper.selectById(mapping.getSourceTableId());
        AssetTable target = tableMapper.selectById(mapping.getTargetTableId());
        List<AssetField> sourceFields = source == null ? new ArrayList<AssetField>() : fieldMapper.selectList(
                new LambdaQueryWrapper<AssetField>().eq(AssetField::getTableId, source.getId()));
        List<AssetField> targetFields = target == null ? new ArrayList<AssetField>() : fieldMapper.selectList(
                new LambdaQueryWrapper<AssetField>().eq(AssetField::getTableId, target.getId()));
        Map<String, Object> res = new HashMap<>();
        res.put("mapping", mapping);
        res.put("sourceFields", sourceFields);
        res.put("targetFields", targetFields);
        return Result.ok(res);
    }

    private AggMigrationMapping loadMapping(Long id) {
        AggMigrationMapping mapping = mappingMapper.selectById(id);
        if (mapping == null) {
            throw new IllegalArgumentException("记录不存在");
        }
        enrichNames(Collections.singletonList(mapping));
        return mapping;
    }

    private Map<String, Object> buildValidation(AggMigrationMapping mapping) {
        Map<String, Object> data = new HashMap<>();
        AssetTable source = tableMapper.selectById(mapping.getSourceTableId());
        AssetTable target = tableMapper.selectById(mapping.getTargetTableId());
        List<AssetField> sourceFields = source == null ? new ArrayList<AssetField>() : fieldMapper.selectList(
                new LambdaQueryWrapper<AssetField>().eq(AssetField::getTableId, source.getId()));
        List<AssetField> targetFields = target == null ? new ArrayList<AssetField>() : fieldMapper.selectList(
                new LambdaQueryWrapper<AssetField>().eq(AssetField::getTableId, target.getId()));
        Set<String> sourceNames = sourceFields.stream().map(AssetField::getFieldEn).collect(Collectors.toSet());
        Set<String> targetNames = targetFields.stream().map(AssetField::getFieldEn).collect(Collectors.toSet());
        List<Map<String, Object>> rows = parseFieldMapping(mapping.getFieldMapping());
        List<String> warnings = new ArrayList<>();
        List<String> missingSource = new ArrayList<>();
        List<String> missingTarget = new ArrayList<>();
        int matched = 0;
        for (Map<String, Object> row : rows) {
            String sourceField = text(row.get("sourceField"));
            String targetField = text(row.get("targetField"));
            if (StringUtils.hasText(sourceField) && !sourceNames.contains(sourceField)) {
                missingSource.add(sourceField);
            }
            if (StringUtils.hasText(targetField) && !targetNames.contains(targetField)) {
                missingTarget.add(targetField);
            }
            if (sourceNames.contains(sourceField) && targetNames.contains(targetField)) {
                matched++;
            }
        }
        if (source == null) warnings.add("源表不存在");
        if (target == null) warnings.add("目标表不存在");
        if (rows.isEmpty()) warnings.add("字段映射为空");
        if (!missingSource.isEmpty()) warnings.add("缺失源字段: " + String.join(", ", dedupe(missingSource)));
        if (!missingTarget.isEmpty()) warnings.add("缺失目标字段: " + String.join(", ", dedupe(missingTarget)));
        Long sourceDatasourceId = source == null ? null : source.getDatasourceId();
        Long targetDatasourceId = target == null ? null : target.getDatasourceId();
        AggDatasource sourceDatasource = sourceDatasourceId == null ? null : datasourceMapper.selectById(sourceDatasourceId);
        AggDatasource targetDatasource = targetDatasourceId == null ? null : datasourceMapper.selectById(targetDatasourceId);
        data.put("mappingId", mapping.getId());
        data.put("sourceTable", source);
        data.put("targetTable", target);
        data.put("sourceFieldCount", sourceFields.size());
        data.put("targetFieldCount", targetFields.size());
        data.put("mappingFieldCount", rows.size());
        data.put("matchedCount", matched);
        data.put("missingSourceFields", dedupe(missingSource));
        data.put("missingTargetFields", dedupe(missingTarget));
        data.put("warnings", warnings);
        data.put("sourceDatasourceId", sourceDatasourceId);
        data.put("targetDatasourceId", targetDatasourceId);
        data.put("sourceDatasourceName", sourceDatasource == null ? null : sourceDatasource.getName());
        data.put("targetDatasourceName", targetDatasource == null ? null : targetDatasource.getName());
        data.put("canGenerateDataflow", sourceDatasourceId != null && targetDatasourceId != null
                && !rows.isEmpty() && missingSource.isEmpty() && missingTarget.isEmpty());
        data.put("mappingRows", rows);
        return data;
    }

    private List<Map<String, Object>> parseFieldMapping(String fieldMapping) {
        if (!StringUtils.hasText(fieldMapping)) {
            return new ArrayList<Map<String, Object>>();
        }
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            return mapper.readValue(fieldMapping, new com.fasterxml.jackson.core.type.TypeReference<List<Map<String, Object>>>() {});
        } catch (Exception ex) {
            return new ArrayList<Map<String, Object>>();
        }
    }

    private List<String> dedupe(List<String> values) {
        return values.stream().filter(StringUtils::hasText).distinct().collect(Collectors.toList());
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
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

    private void enrichNames(List<AggMigrationMapping> mappings) {
        if (mappings == null || mappings.isEmpty()) return;
        Set<Long> ids = new HashSet<>();
        for (AggMigrationMapping m : mappings) {
            if (m.getSourceTableId() != null) ids.add(m.getSourceTableId());
            if (m.getTargetTableId() != null) ids.add(m.getTargetTableId());
        }
        if (ids.isEmpty()) return;
        Map<Long, String> tableNameMap = tableMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(AssetTable::getId, t -> t.getTableEn() + (t.getTableCn() != null ? " / " + t.getTableCn() : "")));
        for (AggMigrationMapping m : mappings) {
            m.setSourceTableName(tableNameMap.get(m.getSourceTableId()));
            m.setTargetTableName(tableNameMap.get(m.getTargetTableId()));
        }
    }
}
