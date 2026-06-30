package com.zhangye.dataflow.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhangye.dataflow.common.Result;
import com.zhangye.dataflow.entity.AssetField;
import com.zhangye.dataflow.entity.AssetLineage;
import com.zhangye.dataflow.entity.AssetTable;
import com.zhangye.dataflow.mapper.AssetFieldMapper;
import com.zhangye.dataflow.mapper.AssetLineageMapper;
import com.zhangye.dataflow.mapper.AssetTableMapper;
import com.zhangye.dataflow.security.SecurityUtils;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/asset/lineage")
public class LineageController {

    private final AssetLineageMapper lineageMapper;
    private final AssetTableMapper tableMapper;
    private final AssetFieldMapper fieldMapper;

    public LineageController(AssetLineageMapper lineageMapper, AssetTableMapper tableMapper,
                              AssetFieldMapper fieldMapper) {
        this.lineageMapper = lineageMapper;
        this.tableMapper = tableMapper;
        this.fieldMapper = fieldMapper;
    }

    private Long tid() { return SecurityUtils.getCurrentTenantId(); }

    @GetMapping("/table/{tableId}")
    public Result<Map<String, Object>> tableLineage(@PathVariable Long tableId) {
        // Upstream: find all lineages where target = this table
        List<AssetLineage> upstream = lineageMapper.selectList(new LambdaQueryWrapper<AssetLineage>()
                .eq(AssetLineage::getTenantId, tid())
                .eq(AssetLineage::getTargetTableId, tableId));

        // Downstream: find all lineages where source = this table
        List<AssetLineage> downstream = lineageMapper.selectList(new LambdaQueryWrapper<AssetLineage>()
                .eq(AssetLineage::getTenantId, tid())
                .eq(AssetLineage::getSourceTableId, tableId));

        Map<String, Object> result = new HashMap<>();
        result.put("upstream", enrichLineages(upstream));
        result.put("downstream", enrichLineages(downstream));
        return Result.ok(result);
    }

    @GetMapping("/field/{tableId}/{fieldName}")
    public Result<List<Map<String, Object>>> fieldLineage(@PathVariable Long tableId,
                                                            @PathVariable String fieldName) {
        List<AssetField> fields = fieldMapper.selectList(new LambdaQueryWrapper<AssetField>()
                .eq(AssetField::getTableId, tableId)
                .eq(AssetField::getFieldEn, fieldName));

        if (fields.isEmpty()) return Result.ok(Collections.emptyList());

        AssetField field = fields.get(0);
        // Find field-level lineages
        List<AssetLineage> lineages = lineageMapper.selectList(new LambdaQueryWrapper<AssetLineage>()
                .eq(AssetLineage::getTenantId, tid())
                .and(w -> w.eq(AssetLineage::getSourceFieldId, field.getId())
                        .or().eq(AssetLineage::getTargetFieldId, field.getId())));

        List<Map<String, Object>> result = new ArrayList<>();
        for (AssetLineage l : lineages) {
            Map<String, Object> m = new HashMap<>();
            AssetTable src = tableMapper.selectById(l.getSourceTableId());
            AssetTable tgt = tableMapper.selectById(l.getTargetTableId());
            AssetField srcField = l.getSourceFieldId() != null ? fieldMapper.selectById(l.getSourceFieldId()) : null;
            AssetField tgtField = l.getTargetFieldId() != null ? fieldMapper.selectById(l.getTargetFieldId()) : null;
            m.put("sourceTable", src != null ? src.getTableEn() : null);
            m.put("targetTable", tgt != null ? tgt.getTableEn() : null);
            m.put("sourceField", srcField != null ? srcField.getFieldEn() : null);
            m.put("targetField", tgtField != null ? tgtField.getFieldEn() : null);
            m.put("relationType", l.getRelationType());
            result.add(m);
        }
        return Result.ok(result);
    }

    @PostMapping
    public Result<Void> createLineage(@RequestBody AssetLineage e) {
        e.setTenantId(tid());
        e.setCreateTime(LocalDateTime.now());
        lineageMapper.insert(e);
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    public Result<Void> deleteLineage(@PathVariable Long id) {
        lineageMapper.deleteById(id);
        return Result.ok();
    }

    private List<Map<String, Object>> enrichLineages(List<AssetLineage> lineages) {
        return lineages.stream().map(l -> {
            Map<String, Object> m = new HashMap<>();
            m.put("id", l.getId());
            AssetTable src = tableMapper.selectById(l.getSourceTableId());
            AssetTable tgt = tableMapper.selectById(l.getTargetTableId());
            m.put("sourceTableId", l.getSourceTableId());
            m.put("targetTableId", l.getTargetTableId());
            m.put("sourceTable", src != null ? src.getTableEn() : null);
            m.put("targetTable", tgt != null ? tgt.getTableEn() : null);
            m.put("sourceTableCn", src != null ? src.getTableCn() : null);
            m.put("targetTableCn", tgt != null ? tgt.getTableCn() : null);
            m.put("sourceFieldId", l.getSourceFieldId());
            m.put("targetFieldId", l.getTargetFieldId());
            m.put("relationType", l.getRelationType());
            return m;
        }).collect(Collectors.toList());
    }
}
