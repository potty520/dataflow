package com.zhangye.dataflow.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhangye.dataflow.common.PageResult;
import com.zhangye.dataflow.common.Result;
import com.zhangye.dataflow.entity.*;
import com.zhangye.dataflow.mapper.*;
import com.zhangye.dataflow.security.SecurityUtils;
import com.zhangye.dataflow.service.OperLogService;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/governance")
public class GovernanceController {

    private final GovStandardCatalogMapper catalogMapper;
    private final GovCodeSetMapper codeSetMapper;
    private final GovWordMapper wordMapper;
    private final GovWarehouseLayerMapper layerMapper;
    private final GovWarehouseCatalogMapper whCatalogMapper;
    private final GovModelTableMapper modelTableMapper;
    private final GovStatPeriodMapper statPeriodMapper;
    private final GovAtomicIndicatorMapper atomicMapper;
    private final GovBusinessLimitMapper limitMapper;
    private final GovDerivedIndicatorMapper derivedMapper;
    private final GovProxyNodeMapper proxyNodeMapper;
    private final OperLogService operLogService;

    public GovernanceController(GovStandardCatalogMapper catalogMapper, GovCodeSetMapper codeSetMapper,
                                GovWordMapper wordMapper, GovWarehouseLayerMapper layerMapper,
                                GovWarehouseCatalogMapper whCatalogMapper, GovModelTableMapper modelTableMapper,
                                GovStatPeriodMapper statPeriodMapper, GovAtomicIndicatorMapper atomicMapper,
                                GovBusinessLimitMapper limitMapper, GovDerivedIndicatorMapper derivedMapper,
                                GovProxyNodeMapper proxyNodeMapper, OperLogService operLogService) {
        this.catalogMapper = catalogMapper;
        this.codeSetMapper = codeSetMapper;
        this.wordMapper = wordMapper;
        this.layerMapper = layerMapper;
        this.whCatalogMapper = whCatalogMapper;
        this.modelTableMapper = modelTableMapper;
        this.statPeriodMapper = statPeriodMapper;
        this.atomicMapper = atomicMapper;
        this.limitMapper = limitMapper;
        this.derivedMapper = derivedMapper;
        this.proxyNodeMapper = proxyNodeMapper;
        this.operLogService = operLogService;
    }

    // ----- 标准目录 -----
    @GetMapping("/catalog/tree")
    public Result<List<GovStandardCatalog>> catalogTree(@RequestParam(required = false) String catalogType) {
        LambdaQueryWrapper<GovStandardCatalog> qw = new LambdaQueryWrapper<>();
        qw.eq(GovStandardCatalog::getTenantId, SecurityUtils.getCurrentTenantId());
        if (StringUtils.hasText(catalogType)) {
            qw.eq(GovStandardCatalog::getCatalogType, catalogType);
        }
        List<GovStandardCatalog> all = catalogMapper.selectList(qw);
        return Result.ok(buildCatalogTree(all, 0L));
    }

    @PostMapping("/catalog")
    public Result<Void> createCatalog(@RequestBody GovStandardCatalog c, HttpServletRequest req) {
        c.setTenantId(SecurityUtils.getCurrentTenantId());
        c.setCreateTime(LocalDateTime.now());
        catalogMapper.insert(c);
        operLogService.log(req, "创建标准目录", "数据标准", c.getName(), true, null);
        return Result.ok();
    }

    @PutMapping("/catalog/{id}")
    public Result<Void> updateCatalog(@PathVariable Long id, @RequestBody GovStandardCatalog c, HttpServletRequest req) {
        c.setId(id);
        catalogMapper.updateById(c);
        operLogService.log(req, "编辑标准目录", "数据标准", c.getName(), true, null);
        return Result.ok();
    }

    @DeleteMapping("/catalog/{id}")
    public Result<Void> deleteCatalog(@PathVariable Long id, HttpServletRequest req) {
        GovStandardCatalog c = catalogMapper.selectById(id);
        catalogMapper.deleteById(id);
        operLogService.log(req, "删除标准目录", "数据标准", c != null ? c.getName() : String.valueOf(id), true, null);
        return Result.ok();
    }

    // ----- 代码集 -----
    @GetMapping("/codeset/page")
    public Result<PageResult<GovCodeSet>> codeSetPage(
            @RequestParam(defaultValue = "1") long page, @RequestParam(defaultValue = "10") long size,
            @RequestParam(required = false) String name, @RequestParam(required = false) String code) {
        LambdaQueryWrapper<GovCodeSet> qw = tenantQw();
        if (StringUtils.hasText(name)) qw.like(GovCodeSet::getName, name);
        if (StringUtils.hasText(code)) qw.like(GovCodeSet::getCode, code);
        Page<GovCodeSet> p = codeSetMapper.selectPage(new Page<>(page, size), qw);
        return Result.ok(PageResult.of(p.getTotal(), page, size, p.getRecords()));
    }

    @PostMapping("/codeset")
    public Result<Void> createCodeSet(@RequestBody GovCodeSet e) {
        e.setTenantId(SecurityUtils.getCurrentTenantId());
        e.setCreateTime(LocalDateTime.now());
        codeSetMapper.insert(e);
        return Result.ok();
    }

    @PutMapping("/codeset/{id}")
    public Result<Void> updateCodeSet(@PathVariable Long id, @RequestBody GovCodeSet e) {
        e.setId(id);
        codeSetMapper.updateById(e);
        return Result.ok();
    }

    @DeleteMapping("/codeset/{id}")
    public Result<Void> deleteCodeSet(@PathVariable Long id) {
        codeSetMapper.deleteById(id);
        return Result.ok();
    }

    // ----- 单词 -----
    @GetMapping("/word/page")
    public Result<PageResult<GovWord>> wordPage(
            @RequestParam(defaultValue = "1") long page, @RequestParam(defaultValue = "10") long size,
            @RequestParam(required = false) String keyword) {
        LambdaQueryWrapper<GovWord> qw = new LambdaQueryWrapper<>();
        qw.eq(GovWord::getTenantId, SecurityUtils.getCurrentTenantId());
        if (StringUtils.hasText(keyword)) {
            qw.and(w -> w.like(GovWord::getWordEn, keyword).or().like(GovWord::getWordCn, keyword));
        }
        Page<GovWord> p = wordMapper.selectPage(new Page<>(page, size), qw);
        return Result.ok(PageResult.of(p.getTotal(), page, size, p.getRecords()));
    }

    @PostMapping("/word")
    public Result<Void> createWord(@RequestBody GovWord e) {
        e.setTenantId(SecurityUtils.getCurrentTenantId());
        e.setCreateTime(LocalDateTime.now());
        wordMapper.insert(e);
        return Result.ok();
    }

    @PutMapping("/word/{id}")
    public Result<Void> updateWord(@PathVariable Long id, @RequestBody GovWord e) {
        e.setId(id);
        wordMapper.updateById(e);
        return Result.ok();
    }

    @DeleteMapping("/word/{id}")
    public Result<Void> deleteWord(@PathVariable Long id) {
        wordMapper.deleteById(id);
        return Result.ok();
    }

    // ----- 数仓分层 -----
    @GetMapping("/layer/list")
    public Result<List<GovWarehouseLayer>> layerList() {
        return Result.ok(layerMapper.selectList(new LambdaQueryWrapper<GovWarehouseLayer>()
                .eq(GovWarehouseLayer::getTenantId, SecurityUtils.getCurrentTenantId())
                .orderByAsc(GovWarehouseLayer::getSortOrder)));
    }

    @PostMapping("/layer")
    public Result<Void> createLayer(@RequestBody GovWarehouseLayer e) {
        e.setTenantId(SecurityUtils.getCurrentTenantId());
        e.setCreateTime(LocalDateTime.now());
        layerMapper.insert(e);
        return Result.ok();
    }

    @PutMapping("/layer/{id}")
    public Result<Void> updateLayer(@PathVariable Long id, @RequestBody GovWarehouseLayer e) {
        e.setId(id);
        layerMapper.updateById(e);
        return Result.ok();
    }

    @DeleteMapping("/layer/{id}")
    public Result<Void> deleteLayer(@PathVariable Long id) {
        layerMapper.deleteById(id);
        return Result.ok();
    }

    @GetMapping("/warehouse-catalog/tree")
    public Result<List<GovWarehouseCatalog>> whCatalogTree(@RequestParam(required = false) Long layerId) {
        LambdaQueryWrapper<GovWarehouseCatalog> qw = new LambdaQueryWrapper<>();
        qw.eq(GovWarehouseCatalog::getTenantId, SecurityUtils.getCurrentTenantId());
        if (layerId != null) qw.eq(GovWarehouseCatalog::getLayerId, layerId);
        List<GovWarehouseCatalog> all = whCatalogMapper.selectList(qw);
        return Result.ok(buildWhCatalogTree(all, 0L));
    }

    @PostMapping("/warehouse-catalog")
    public Result<Void> createWhCatalog(@RequestBody GovWarehouseCatalog e) {
        e.setTenantId(SecurityUtils.getCurrentTenantId());
        e.setCreateTime(LocalDateTime.now());
        whCatalogMapper.insert(e);
        return Result.ok();
    }

    @PutMapping("/warehouse-catalog/{id}")
    public Result<Void> updateWhCatalog(@PathVariable Long id, @RequestBody GovWarehouseCatalog e) {
        e.setId(id);
        whCatalogMapper.updateById(e);
        return Result.ok();
    }

    @DeleteMapping("/warehouse-catalog/{id}")
    public Result<Void> deleteWhCatalog(@PathVariable Long id) {
        whCatalogMapper.deleteById(id);
        return Result.ok();
    }

    // ----- 数据建模 -----
    @GetMapping("/model/page")
    public Result<PageResult<GovModelTable>> modelPage(
            @RequestParam(defaultValue = "1") long page, @RequestParam(defaultValue = "10") long size,
            @RequestParam(required = false) String layerCode, @RequestParam(required = false) String keyword) {
        LambdaQueryWrapper<GovModelTable> qw = new LambdaQueryWrapper<>();
        qw.eq(GovModelTable::getTenantId, SecurityUtils.getCurrentTenantId());
        if (StringUtils.hasText(layerCode)) qw.eq(GovModelTable::getLayerCode, layerCode);
        if (StringUtils.hasText(keyword)) {
            qw.and(w -> w.like(GovModelTable::getTableEn, keyword).or().like(GovModelTable::getTableCn, keyword));
        }
        Page<GovModelTable> p = modelTableMapper.selectPage(new Page<>(page, size), qw);
        return Result.ok(PageResult.of(p.getTotal(), page, size, p.getRecords()));
    }

    @PostMapping("/model")
    public Result<Void> createModel(@RequestBody GovModelTable e) {
        e.setTenantId(SecurityUtils.getCurrentTenantId());
        e.setCreateBy(SecurityUtils.getCurrentUsername());
        e.setCreateTime(LocalDateTime.now());
        e.setUpdateTime(LocalDateTime.now());
        modelTableMapper.insert(e);
        return Result.ok();
    }

    @PutMapping("/model/{id}")
    public Result<Void> updateModel(@PathVariable Long id, @RequestBody GovModelTable e) {
        e.setId(id);
        e.setUpdateTime(LocalDateTime.now());
        modelTableMapper.updateById(e);
        return Result.ok();
    }

    @DeleteMapping("/model/{id}")
    public Result<Void> deleteModel(@PathVariable Long id) {
        modelTableMapper.deleteById(id);
        return Result.ok();
    }

    // ----- 指标管理 -----
    @GetMapping("/period/page")
    public Result<PageResult<GovStatPeriod>> periodPage(
            @RequestParam(defaultValue = "1") long page, @RequestParam(defaultValue = "10") long size,
            @RequestParam(required = false) String name, @RequestParam(required = false) Integer status) {
        LambdaQueryWrapper<GovStatPeriod> qw = new LambdaQueryWrapper<>();
        qw.eq(GovStatPeriod::getTenantId, SecurityUtils.getCurrentTenantId());
        if (StringUtils.hasText(name)) qw.like(GovStatPeriod::getName, name);
        if (status != null) qw.eq(GovStatPeriod::getStatus, status);
        Page<GovStatPeriod> p = statPeriodMapper.selectPage(new Page<>(page, size), qw);
        return Result.ok(PageResult.of(p.getTotal(), page, size, p.getRecords()));
    }

    @PostMapping("/period")
    public Result<Void> createPeriod(@RequestBody GovStatPeriod e) {
        e.setTenantId(SecurityUtils.getCurrentTenantId());
        e.setCreateTime(LocalDateTime.now());
        e.setUpdateTime(LocalDateTime.now());
        statPeriodMapper.insert(e);
        return Result.ok();
    }

    @PutMapping("/period/{id}")
    public Result<Void> updatePeriod(@PathVariable Long id, @RequestBody GovStatPeriod e) {
        e.setId(id);
        e.setUpdateTime(LocalDateTime.now());
        statPeriodMapper.updateById(e);
        return Result.ok();
    }

    @PostMapping("/period/{id}/publish")
    public Result<Void> publishPeriod(@PathVariable Long id) {
        GovStatPeriod e = statPeriodMapper.selectById(id);
        e.setStatus(1);
        e.setUpdateTime(LocalDateTime.now());
        statPeriodMapper.updateById(e);
        return Result.ok();
    }

    @DeleteMapping("/period/{id}")
    public Result<Void> deletePeriod(@PathVariable Long id) {
        statPeriodMapper.deleteById(id);
        return Result.ok();
    }

    @GetMapping("/atomic/page")
    public Result<PageResult<GovAtomicIndicator>> atomicPage(
            @RequestParam(defaultValue = "1") long page, @RequestParam(defaultValue = "10") long size,
            @RequestParam(required = false) String name) {
        LambdaQueryWrapper<GovAtomicIndicator> qw = new LambdaQueryWrapper<>();
        qw.eq(GovAtomicIndicator::getTenantId, SecurityUtils.getCurrentTenantId());
        if (StringUtils.hasText(name)) qw.like(GovAtomicIndicator::getName, name);
        Page<GovAtomicIndicator> p = atomicMapper.selectPage(new Page<>(page, size), qw);
        return Result.ok(PageResult.of(p.getTotal(), page, size, p.getRecords()));
    }

    @PostMapping("/atomic")
    public Result<Void> createAtomic(@RequestBody GovAtomicIndicator e) {
        e.setTenantId(SecurityUtils.getCurrentTenantId());
        e.setCreateTime(LocalDateTime.now());
        atomicMapper.insert(e);
        return Result.ok();
    }

    @PutMapping("/atomic/{id}")
    public Result<Void> updateAtomic(@PathVariable Long id, @RequestBody GovAtomicIndicator e) {
        e.setId(id);
        atomicMapper.updateById(e);
        return Result.ok();
    }

    @DeleteMapping("/atomic/{id}")
    public Result<Void> deleteAtomic(@PathVariable Long id) {
        atomicMapper.deleteById(id);
        return Result.ok();
    }

    @GetMapping("/limit/page")
    public Result<PageResult<GovBusinessLimit>> limitPage(
            @RequestParam(defaultValue = "1") long page, @RequestParam(defaultValue = "10") long size,
            @RequestParam(required = false) String name) {
        LambdaQueryWrapper<GovBusinessLimit> qw = new LambdaQueryWrapper<>();
        qw.eq(GovBusinessLimit::getTenantId, SecurityUtils.getCurrentTenantId());
        if (StringUtils.hasText(name)) qw.like(GovBusinessLimit::getName, name);
        Page<GovBusinessLimit> p = limitMapper.selectPage(new Page<>(page, size), qw);
        return Result.ok(PageResult.of(p.getTotal(), page, size, p.getRecords()));
    }

    @PostMapping("/limit")
    public Result<Void> createLimit(@RequestBody GovBusinessLimit e) {
        e.setTenantId(SecurityUtils.getCurrentTenantId());
        e.setCreateTime(LocalDateTime.now());
        limitMapper.insert(e);
        return Result.ok();
    }

    @PutMapping("/limit/{id}")
    public Result<Void> updateLimit(@PathVariable Long id, @RequestBody GovBusinessLimit e) {
        e.setId(id);
        limitMapper.updateById(e);
        return Result.ok();
    }

    @DeleteMapping("/limit/{id}")
    public Result<Void> deleteLimit(@PathVariable Long id) {
        limitMapper.deleteById(id);
        return Result.ok();
    }

    @GetMapping("/derived/page")
    public Result<PageResult<GovDerivedIndicator>> derivedPage(
            @RequestParam(defaultValue = "1") long page, @RequestParam(defaultValue = "10") long size,
            @RequestParam(required = false) String name, @RequestParam(required = false) Integer status) {
        LambdaQueryWrapper<GovDerivedIndicator> qw = new LambdaQueryWrapper<>();
        qw.eq(GovDerivedIndicator::getTenantId, SecurityUtils.getCurrentTenantId());
        if (StringUtils.hasText(name)) qw.like(GovDerivedIndicator::getName, name);
        if (status != null) qw.eq(GovDerivedIndicator::getStatus, status);
        qw.orderByDesc(GovDerivedIndicator::getUpdateTime);
        Page<GovDerivedIndicator> p = derivedMapper.selectPage(new Page<>(page, size), qw);
        return Result.ok(PageResult.of(p.getTotal(), page, size, p.getRecords()));
    }

    @PostMapping("/derived")
    public Result<Void> createDerived(@RequestBody GovDerivedIndicator e) {
        e.setTenantId(SecurityUtils.getCurrentTenantId());
        e.setCreateTime(LocalDateTime.now());
        e.setUpdateTime(LocalDateTime.now());
        derivedMapper.insert(e);
        return Result.ok();
    }

    @PutMapping("/derived/{id}")
    public Result<Void> updateDerived(@PathVariable Long id, @RequestBody GovDerivedIndicator e) {
        e.setId(id);
        e.setUpdateTime(LocalDateTime.now());
        derivedMapper.updateById(e);
        return Result.ok();
    }

    @DeleteMapping("/derived/{id}")
    public Result<Void> deleteDerived(@PathVariable Long id) {
        derivedMapper.deleteById(id);
        return Result.ok();
    }

    // ----- 代理节点 -----
    @GetMapping("/proxy/page")
    public Result<PageResult<GovProxyNode>> proxyPage(
            @RequestParam(defaultValue = "1") long page, @RequestParam(defaultValue = "10") long size,
            @RequestParam(required = false) String name) {
        LambdaQueryWrapper<GovProxyNode> qw = new LambdaQueryWrapper<>();
        qw.eq(GovProxyNode::getTenantId, SecurityUtils.getCurrentTenantId());
        if (StringUtils.hasText(name)) qw.like(GovProxyNode::getName, name);
        Page<GovProxyNode> p = proxyNodeMapper.selectPage(new Page<>(page, size), qw);
        return Result.ok(PageResult.of(p.getTotal(), page, size, p.getRecords()));
    }

    @PostMapping("/proxy")
    public Result<Void> createProxy(@RequestBody GovProxyNode e) {
        e.setTenantId(SecurityUtils.getCurrentTenantId());
        e.setCreateTime(LocalDateTime.now());
        e.setUpdateTime(LocalDateTime.now());
        proxyNodeMapper.insert(e);
        return Result.ok();
    }

    @PutMapping("/proxy/{id}")
    public Result<Void> updateProxy(@PathVariable Long id, @RequestBody GovProxyNode e) {
        e.setId(id);
        e.setUpdateTime(LocalDateTime.now());
        proxyNodeMapper.updateById(e);
        return Result.ok();
    }

    @PostMapping("/proxy/{id}/start")
    public Result<Void> startProxy(@PathVariable Long id) {
        GovProxyNode e = proxyNodeMapper.selectById(id);
        e.setStatus("RUNNING");
        e.setUpdateTime(LocalDateTime.now());
        proxyNodeMapper.updateById(e);
        return Result.ok();
    }

    @PostMapping("/proxy/{id}/stop")
    public Result<Void> stopProxy(@PathVariable Long id) {
        GovProxyNode e = proxyNodeMapper.selectById(id);
        e.setStatus("STOPPED");
        e.setUpdateTime(LocalDateTime.now());
        proxyNodeMapper.updateById(e);
        return Result.ok();
    }

    @DeleteMapping("/proxy/{id}")
    public Result<Void> deleteProxy(@PathVariable Long id) {
        proxyNodeMapper.deleteById(id);
        return Result.ok();
    }

    @GetMapping("/monitor/stats")
    public Result<Map<String, Object>> monitorStats() {
        Long tid = SecurityUtils.getCurrentTenantId();
        Map<String, Object> stats = new HashMap<>();
        stats.put("codeSetCount", codeSetMapper.selectCount(tenantQw()));
        stats.put("wordCount", wordMapper.selectCount(new LambdaQueryWrapper<GovWord>().eq(GovWord::getTenantId, tid)));
        stats.put("modelCount", modelTableMapper.selectCount(new LambdaQueryWrapper<GovModelTable>().eq(GovModelTable::getTenantId, tid)));
        stats.put("derivedCount", derivedMapper.selectCount(new LambdaQueryWrapper<GovDerivedIndicator>().eq(GovDerivedIndicator::getTenantId, tid)));
        stats.put("proxyRunning", proxyNodeMapper.selectCount(new LambdaQueryWrapper<GovProxyNode>()
                .eq(GovProxyNode::getTenantId, tid).eq(GovProxyNode::getStatus, "RUNNING")));
        return Result.ok(stats);
    }

    private LambdaQueryWrapper<GovCodeSet> tenantQw() {
        return new LambdaQueryWrapper<GovCodeSet>().eq(GovCodeSet::getTenantId, SecurityUtils.getCurrentTenantId());
    }

    private List<GovStandardCatalog> buildCatalogTree(List<GovStandardCatalog> all, Long parentId) {
        return all.stream().filter(c -> parentId.equals(c.getParentId() == null ? 0L : c.getParentId())).collect(Collectors.toList());
    }

    private List<GovWarehouseCatalog> buildWhCatalogTree(List<GovWarehouseCatalog> all, Long parentId) {
        return all.stream()
                .filter(c -> parentId.equals(c.getParentId() == null ? 0L : c.getParentId()))
                .peek(c -> c.setChildren(buildWhCatalogTree(all, c.getId())))
                .collect(Collectors.toList());
    }
}
