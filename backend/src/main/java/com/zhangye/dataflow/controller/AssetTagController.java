package com.zhangye.dataflow.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhangye.dataflow.common.Result;
import com.zhangye.dataflow.entity.AssetTag;
import com.zhangye.dataflow.entity.AssetTagRelation;
import com.zhangye.dataflow.mapper.AssetTagMapper;
import com.zhangye.dataflow.mapper.AssetTagRelationMapper;
import com.zhangye.dataflow.security.SecurityUtils;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/asset")
public class AssetTagController {

    private final AssetTagMapper tagMapper;
    private final AssetTagRelationMapper relationMapper;

    public AssetTagController(AssetTagMapper tagMapper, AssetTagRelationMapper relationMapper) {
        this.tagMapper = tagMapper;
        this.relationMapper = relationMapper;
    }

    private Long tid() { return SecurityUtils.getCurrentTenantId(); }

    @GetMapping("/tags")
    public Result<List<AssetTag>> listTags() {
        return Result.ok(tagMapper.selectList(new LambdaQueryWrapper<AssetTag>()
                .eq(AssetTag::getTenantId, tid())));
    }

    @PostMapping("/tags")
    public Result<Void> createTag(@RequestBody AssetTag tag) {
        tag.setTenantId(tid());
        tag.setCreateTime(LocalDateTime.now());
        tagMapper.insert(tag);
        return Result.ok();
    }

    @PostMapping("/tags/assign")
    public Result<Void> assignTags(@RequestBody Map<String, Object> body) {
        Long tableId = body.get("tableId") != null ? Long.valueOf(body.get("tableId").toString()) : null;
        @SuppressWarnings("unchecked")
        List<Integer> tagIds = (List<Integer>) body.get("tagIds");
        if (tableId == null || tagIds == null) return Result.fail("参数错误");

        // Remove existing relations
        relationMapper.delete(new LambdaQueryWrapper<AssetTagRelation>()
                .eq(AssetTagRelation::getAssetTableId, tableId));

        // Add new relations
        for (Integer tagId : tagIds) {
            AssetTagRelation rel = new AssetTagRelation();
            rel.setAssetTableId(tableId);
            rel.setTagId(Long.valueOf(tagId));
            rel.setCreateTime(LocalDateTime.now());
            relationMapper.insert(rel);
        }
        return Result.ok();
    }

    @GetMapping("/table/{id}/tags")
    public Result<List<AssetTag>> tableTags(@PathVariable Long id) {
        List<Long> tagIds = relationMapper.selectList(new LambdaQueryWrapper<AssetTagRelation>()
                        .eq(AssetTagRelation::getAssetTableId, id))
                .stream().map(AssetTagRelation::getTagId).collect(Collectors.toList());
        if (tagIds.isEmpty()) return Result.ok(java.util.Collections.emptyList());
        return Result.ok(tagMapper.selectBatchIds(tagIds));
    }
}
