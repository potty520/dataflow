package com.zhangye.dataflow.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhangye.dataflow.common.PageResult;
import com.zhangye.dataflow.common.Result;
import com.zhangye.dataflow.entity.*;
import com.zhangye.dataflow.mapper.*;
import com.zhangye.dataflow.security.SecurityUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/asset")
public class AssetController {

    private final AssetTableMapper tableMapper;
    private final AssetFieldMapper fieldMapper;
    private final AssetLineageMapper lineageMapper;
    private final AggDatasourceMapper datasourceMapper;

    public AssetController(AssetTableMapper tableMapper, AssetFieldMapper fieldMapper,
                           AssetLineageMapper lineageMapper, AggDatasourceMapper datasourceMapper) {
        this.tableMapper = tableMapper;
        this.fieldMapper = fieldMapper;
        this.lineageMapper = lineageMapper;
        this.datasourceMapper = datasourceMapper;
    }

    private Long tid() { return SecurityUtils.getCurrentTenantId(); }

    @GetMapping("/overview")
    public Result<Map<String, Object>> overview() {
        Long tid = tid();
        Map<String, Object> stats = new HashMap<>();
        stats.put("tableCount", tableMapper.selectCount(new LambdaQueryWrapper<AssetTable>().eq(AssetTable::getTenantId, tid)));
        stats.put("fieldCount", fieldMapper.selectCount(null));
        stats.put("keyAssetCount", tableMapper.selectCount(new LambdaQueryWrapper<AssetTable>()
                .eq(AssetTable::getTenantId, tid).eq(AssetTable::getIsKeyAsset, 1)));
        stats.put("datasourceCount", datasourceMapper.selectCount(new LambdaQueryWrapper<AggDatasource>()
                .eq(AggDatasource::getTenantId, tid)));
        long totalRows = tableMapper.selectList(new LambdaQueryWrapper<AssetTable>().eq(AssetTable::getTenantId, tid))
                .stream().mapToLong(t -> t.getRowCount() == null ? 0 : t.getRowCount()).sum();
        stats.put("totalRows", totalRows);
        Map<String, Long> layerStats = tableMapper.selectList(new LambdaQueryWrapper<AssetTable>().eq(AssetTable::getTenantId, tid))
                .stream().collect(Collectors.groupingBy(t -> t.getLayerCode() == null ? "OTHER" : t.getLayerCode(), Collectors.counting()));
        stats.put("layerStats", layerStats);
        return Result.ok(stats);
    }

    @GetMapping("/table/page")
    public Result<PageResult<AssetTable>> tablePage(
            @RequestParam(defaultValue = "1") long page, @RequestParam(defaultValue = "10") long size,
            @RequestParam(required = false) String keyword, @RequestParam(required = false) String layerCode) {
        LambdaQueryWrapper<AssetTable> qw = new LambdaQueryWrapper<AssetTable>().eq(AssetTable::getTenantId, tid());
        if (StringUtils.hasText(keyword)) {
            qw.and(w -> w.like(AssetTable::getTableEn, keyword).or().like(AssetTable::getTableCn, keyword)
                    .or().like(AssetTable::getDatabaseName, keyword));
        }
        if (StringUtils.hasText(layerCode)) qw.eq(AssetTable::getLayerCode, layerCode);
        Page<AssetTable> p = tableMapper.selectPage(new Page<>(page, size), qw);
        return Result.ok(PageResult.of(p.getTotal(), page, size, p.getRecords()));
    }

    @GetMapping("/table/{id}")
    public Result<Map<String, Object>> tableDetail(@PathVariable Long id) {
        AssetTable table = tableMapper.selectById(id);
        List<AssetField> fields = fieldMapper.selectList(new LambdaQueryWrapper<AssetField>().eq(AssetField::getTableId, id));
        Map<String, Object> detail = new HashMap<>();
        detail.put("table", table);
        detail.put("fields", fields);
        return Result.ok(detail);
    }

    @PostMapping("/table")
    public Result<Void> createTable(@RequestBody AssetTable e) {
        e.setTenantId(tid());
        e.setCreateBy(SecurityUtils.getCurrentUsername());
        e.setCreateTime(LocalDateTime.now());
        e.setUpdateTime(LocalDateTime.now());
        tableMapper.insert(e);
        return Result.ok();
    }

    @PutMapping("/table/{id}")
    public Result<Void> updateTable(@PathVariable Long id, @RequestBody AssetTable e) {
        e.setId(id);
        e.setUpdateTime(LocalDateTime.now());
        tableMapper.updateById(e);
        return Result.ok();
    }

    @PostMapping("/table/{id}/mark-key")
    public Result<Void> markKeyAsset(@PathVariable Long id, @RequestParam Integer isKey) {
        AssetTable e = tableMapper.selectById(id);
        e.setIsKeyAsset(isKey);
        e.setUpdateTime(LocalDateTime.now());
        tableMapper.updateById(e);
        return Result.ok();
    }

    @DeleteMapping("/table/{id}")
    public Result<Void> deleteTable(@PathVariable Long id) {
        tableMapper.deleteById(id);
        fieldMapper.delete(new LambdaQueryWrapper<AssetField>().eq(AssetField::getTableId, id));
        return Result.ok();
    }

    @PostMapping("/field")
    public Result<Void> createField(@RequestBody AssetField e) {
        e.setCreateTime(LocalDateTime.now());
        fieldMapper.insert(e);
        return Result.ok();
    }

    @GetMapping("/lineage")
    public Result<Map<String, Object>> lineageGraph() {
        Long tid = tid();
        List<AssetTable> allTables = tableMapper.selectList(new LambdaQueryWrapper<AssetTable>()
                .eq(AssetTable::getTenantId, tid));
        Map<Long, AssetTable> tableMap = allTables.stream()
                .collect(Collectors.toMap(AssetTable::getId, t -> t));
        List<AssetLineage> lines = lineageMapper.selectList(new LambdaQueryWrapper<AssetLineage>()
                .eq(AssetLineage::getTenantId, tid));
        List<Map<String, Object>> edges = new ArrayList<>();
        for (AssetLineage l : lines) {
            Map<String, Object> edge = new HashMap<>();
            AssetTable src = tableMap.get(l.getSourceTableId());
            AssetTable tgt = tableMap.get(l.getTargetTableId());
            edge.put("sourceId", String.valueOf(l.getSourceTableId()));
            edge.put("targetId", String.valueOf(l.getTargetTableId()));
            edge.put("source", src != null ? src.getTableEn() : String.valueOf(l.getSourceTableId()));
            edge.put("target", tgt != null ? tgt.getTableEn() : String.valueOf(l.getTargetTableId()));
            edge.put("relationType", l.getRelationType());
            edges.add(edge);
        }
        List<Map<String, Object>> nodes = tableMap.values().stream().map(t -> {
            Map<String, Object> n = new HashMap<>();
            n.put("id", t.getId());
            n.put("name", t.getTableEn());
            n.put("label", t.getTableCn());
            n.put("layer", t.getLayerCode());
            n.put("databaseName", t.getDatabaseName());
            n.put("isKeyAsset", t.getIsKeyAsset());
            n.put("rowCount", t.getRowCount());
            return n;
        }).collect(Collectors.toList());
        Map<String, Object> graph = new HashMap<>();
        graph.put("nodes", nodes);
        graph.put("edges", edges);
        return Result.ok(graph);
    }

    @PostMapping("/lineage")
    public Result<Void> createLineage(@RequestBody AssetLineage e) {
        e.setTenantId(tid());
        e.setCreateTime(LocalDateTime.now());
        lineageMapper.insert(e);
        return Result.ok();
    }

    @GetMapping("/monitor/stats")
    public Result<Map<String, Object>> monitorStats() {
        Long tid = tid();
        Map<String, Object> stats = new HashMap<>();
        stats.put("keyAssets", tableMapper.selectCount(new LambdaQueryWrapper<AssetTable>()
                .eq(AssetTable::getTenantId, tid).eq(AssetTable::getIsKeyAsset, 1)));
        stats.put("lineageCount", lineageMapper.selectCount(new LambdaQueryWrapper<AssetLineage>().eq(AssetLineage::getTenantId, tid)));
        stats.put("sensitiveFields", fieldMapper.selectCount(new LambdaQueryWrapper<AssetField>().eq(AssetField::getIsSensitive, 1)));
        return Result.ok(stats);
    }
}
