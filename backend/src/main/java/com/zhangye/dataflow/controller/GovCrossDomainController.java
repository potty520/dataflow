package com.zhangye.dataflow.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhangye.dataflow.common.PageResult;
import com.zhangye.dataflow.common.Result;
import com.zhangye.dataflow.entity.GovCrossDomainMetadata;
import com.zhangye.dataflow.mapper.GovCrossDomainMetadataMapper;
import com.zhangye.dataflow.security.SecurityUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/governance/cross-domain")
public class GovCrossDomainController {

    private final GovCrossDomainMetadataMapper metadataMapper;

    public GovCrossDomainController(GovCrossDomainMetadataMapper metadataMapper) {
        this.metadataMapper = metadataMapper;
    }

    private Long tid() { return SecurityUtils.getCurrentTenantId(); }

    @GetMapping("/metadata/page")
    public Result<PageResult<GovCrossDomainMetadata>> metadataPage(
            @RequestParam(defaultValue = "1") long page, @RequestParam(defaultValue = "10") long size,
            @RequestParam(required = false) String sourceName, @RequestParam(required = false) String sourceType) {
        LambdaQueryWrapper<GovCrossDomainMetadata> qw = new LambdaQueryWrapper<GovCrossDomainMetadata>()
                .eq(GovCrossDomainMetadata::getTenantId, tid());
        if (StringUtils.hasText(sourceName)) qw.like(GovCrossDomainMetadata::getSourceName, sourceName);
        if (StringUtils.hasText(sourceType)) qw.eq(GovCrossDomainMetadata::getSourceType, sourceType);
        Page<GovCrossDomainMetadata> p = metadataMapper.selectPage(new Page<>(page, size), qw);
        return Result.ok(PageResult.of(p.getTotal(), page, size, p.getRecords()));
    }

    @PostMapping("/metadata/export")
    public Result<Void> exportMetadata(@RequestBody GovCrossDomainMetadata e) {
        e.setTenantId(tid());
        e.setSyncStatus("EXPORTED");
        e.setCreateBy(SecurityUtils.getCurrentUsername());
        e.setCreateTime(LocalDateTime.now());
        e.setUpdateTime(LocalDateTime.now());
        metadataMapper.insert(e);
        return Result.ok();
    }

    @PostMapping("/metadata/collect")
    public Result<Void> collectMetadata(@RequestBody GovCrossDomainMetadata e) {
        e.setTenantId(tid());
        e.setSyncStatus("COLLECTED");
        e.setCreateBy(SecurityUtils.getCurrentUsername());
        e.setCreateTime(LocalDateTime.now());
        e.setUpdateTime(LocalDateTime.now());
        metadataMapper.insert(e);
        return Result.ok();
    }

    @GetMapping("/sync/stats")
    public Result<Map<String, Object>> syncStats() {
        Long tid = tid();
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalMetadata", metadataMapper.selectCount(
                new LambdaQueryWrapper<GovCrossDomainMetadata>().eq(GovCrossDomainMetadata::getTenantId, tid)));
        stats.put("exportedCount", metadataMapper.selectCount(
                new LambdaQueryWrapper<GovCrossDomainMetadata>().eq(GovCrossDomainMetadata::getTenantId, tid)
                        .eq(GovCrossDomainMetadata::getSyncStatus, "EXPORTED")));
        stats.put("collectedCount", metadataMapper.selectCount(
                new LambdaQueryWrapper<GovCrossDomainMetadata>().eq(GovCrossDomainMetadata::getTenantId, tid)
                        .eq(GovCrossDomainMetadata::getSyncStatus, "COLLECTED")));
        return Result.ok(stats);
    }
}
