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
    private final GovBusinessDomainMapper domainMapper;
    private final GovCompositeIndicatorMapper compositeIndicatorMapper;
    private final GovProxyNodeMapper proxyNodeMapper;
    private final OperLogService operLogService;

    public GovernanceController(GovStandardCatalogMapper catalogMapper, GovCodeSetMapper codeSetMapper,
                                GovWordMapper wordMapper, GovWarehouseLayerMapper layerMapper,
                                GovWarehouseCatalogMapper whCatalogMapper, GovModelTableMapper modelTableMapper,
                                GovStatPeriodMapper statPeriodMapper, GovAtomicIndicatorMapper atomicMapper,
                                GovBusinessLimitMapper limitMapper, GovDerivedIndicatorMapper derivedMapper,
                                GovBusinessDomainMapper domainMapper,
                                GovCompositeIndicatorMapper compositeIndicatorMapper,
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
        this.domainMapper = domainMapper;
        this.compositeIndicatorMapper = compositeIndicatorMapper;
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

    @PostMapping("/model/preview-ddl")
    @SuppressWarnings("unchecked")
    public Result<Map<String, Object>> previewDdl(@RequestBody Map<String, Object> body) {
        String tableEn = text(body.get("tableEn"));
        if (!StringUtils.hasText(tableEn)) {
            return Result.fail("英文表名不能为空");
        }
        String engine = StringUtils.hasText(text(body.get("engine"))) ? text(body.get("engine")).toLowerCase() : "hive";
        String tableCn = text(body.get("tableCn"));
        String layerCode = text(body.get("layerCode"));
        List<Map<String, Object>> fields = body.get("fields") instanceof List ? (List<Map<String, Object>>) body.get("fields") : new java.util.ArrayList<Map<String, Object>>();
        String ddl = buildPreviewDdl(engine, tableEn, tableCn, layerCode, fields);
        Map<String, Object> data = new HashMap<>();
        data.put("engine", engine);
        data.put("tableEn", tableEn);
        data.put("tableCn", tableCn);
        data.put("layerCode", layerCode);
        data.put("fieldCount", fields.size());
        data.put("ddl", ddl);
        return Result.ok(data);
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

    // ----- 业务域管理 -----
    @GetMapping("/domain/page")
    public Result<PageResult<GovBusinessDomain>> domainPage(
            @RequestParam(defaultValue = "1") long page, @RequestParam(defaultValue = "10") long size,
            @RequestParam(required = false) String domainName,
            @RequestParam(required = false) Long layerId) {
        LambdaQueryWrapper<GovBusinessDomain> qw = new LambdaQueryWrapper<>();
        qw.eq(GovBusinessDomain::getTenantId, SecurityUtils.getCurrentTenantId());
        if (layerId != null) qw.eq(GovBusinessDomain::getLayerId, layerId);
        if (StringUtils.hasText(domainName)) qw.like(GovBusinessDomain::getDomainName, domainName);
        qw.orderByAsc(GovBusinessDomain::getSortOrder);
        Page<GovBusinessDomain> p = domainMapper.selectPage(new Page<>(page, size), qw);
        return Result.ok(PageResult.of(p.getTotal(), page, size, p.getRecords()));
    }

    @GetMapping("/domain/tree")
    public Result<List<GovBusinessDomain>> domainTree(@RequestParam(required = false) Long layerId) {
        LambdaQueryWrapper<GovBusinessDomain> qw = new LambdaQueryWrapper<>();
        qw.eq(GovBusinessDomain::getTenantId, SecurityUtils.getCurrentTenantId());
        if (layerId != null) qw.eq(GovBusinessDomain::getLayerId, layerId);
        qw.orderByAsc(GovBusinessDomain::getSortOrder);
        return Result.ok(domainMapper.selectList(qw));
    }

    @PostMapping("/domain")
    public Result<Void> createDomain(@RequestBody GovBusinessDomain e) {
        e.setTenantId(SecurityUtils.getCurrentTenantId());
        e.setCreateTime(LocalDateTime.now());
        domainMapper.insert(e);
        return Result.ok();
    }

    @PutMapping("/domain/{id}")
    public Result<Void> updateDomain(@PathVariable Long id, @RequestBody GovBusinessDomain e) {
        e.setId(id);
        domainMapper.updateById(e);
        return Result.ok();
    }

    @DeleteMapping("/domain/{id}")
    public Result<Void> deleteDomain(@PathVariable Long id) {
        domainMapper.deleteById(id);
        return Result.ok();
    }

    @PutMapping("/domain/sort")
    public Result<Void> sortDomain(@RequestBody List<GovBusinessDomain> updates) {
        for (GovBusinessDomain item : updates) {
            if (item.getId() == null) continue;
            GovBusinessDomain update = new GovBusinessDomain();
            update.setId(item.getId());
            update.setSortOrder(item.getSortOrder());
            domainMapper.updateById(update);
        }
        return Result.ok();
    }

    // ----- 复合指标管理 -----
    @GetMapping("/composite/page")
    public Result<PageResult<GovCompositeIndicator>> compositePage(
            @RequestParam(defaultValue = "1") long page, @RequestParam(defaultValue = "10") long size,
            @RequestParam(required = false) String indicatorName) {
        LambdaQueryWrapper<GovCompositeIndicator> qw = new LambdaQueryWrapper<>();
        qw.eq(GovCompositeIndicator::getTenantId, SecurityUtils.getCurrentTenantId());
        if (StringUtils.hasText(indicatorName)) qw.like(GovCompositeIndicator::getIndicatorName, indicatorName);
        qw.orderByDesc(GovCompositeIndicator::getCreateTime);
        Page<GovCompositeIndicator> p = compositeIndicatorMapper.selectPage(new Page<>(page, size), qw);
        return Result.ok(PageResult.of(p.getTotal(), page, size, p.getRecords()));
    }

    @PostMapping("/composite")
    public Result<Void> createComposite(@RequestBody GovCompositeIndicator e) {
        e.setTenantId(SecurityUtils.getCurrentTenantId());
        e.setCreateBy(SecurityUtils.getCurrentUsername());
        e.setCreateTime(LocalDateTime.now());
        e.setUpdateTime(LocalDateTime.now());
        e.setStatus(e.getStatus() != null ? e.getStatus() : "DRAFT");
        compositeIndicatorMapper.insert(e);
        return Result.ok();
    }

    @PutMapping("/composite/{id}")
    public Result<Void> updateComposite(@PathVariable Long id, @RequestBody GovCompositeIndicator e) {
        e.setId(id);
        e.setUpdateTime(LocalDateTime.now());
        compositeIndicatorMapper.updateById(e);
        return Result.ok();
    }

    @DeleteMapping("/composite/{id}")
    public Result<Void> deleteComposite(@PathVariable Long id) {
        compositeIndicatorMapper.deleteById(id);
        return Result.ok();
    }

    @GetMapping("/monitor/stats")
    public Result<Map<String, Object>> monitorStats() {
        Long tid = SecurityUtils.getCurrentTenantId();
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalStandards", catalogMapper.selectCount(new LambdaQueryWrapper<GovStandardCatalog>()
                .eq(GovStandardCatalog::getTenantId, tid)));
        stats.put("totalModels", modelTableMapper.selectCount(new LambdaQueryWrapper<GovModelTable>().eq(GovModelTable::getTenantId, tid)));
        stats.put("totalIndicators", atomicMapper.selectCount(new LambdaQueryWrapper<GovAtomicIndicator>().eq(GovAtomicIndicator::getTenantId, tid))
                + derivedMapper.selectCount(new LambdaQueryWrapper<GovDerivedIndicator>().eq(GovDerivedIndicator::getTenantId, tid)));
        stats.put("totalProxyNodes", proxyNodeMapper.selectCount(new LambdaQueryWrapper<GovProxyNode>().eq(GovProxyNode::getTenantId, tid)));
        stats.put("activeProxyNodes", proxyNodeMapper.selectCount(new LambdaQueryWrapper<GovProxyNode>()
                .eq(GovProxyNode::getTenantId, tid).eq(GovProxyNode::getStatus, "RUNNING")));
        stats.put("codeSetCount", codeSetMapper.selectCount(tenantQw()));
        stats.put("wordCount", wordMapper.selectCount(new LambdaQueryWrapper<GovWord>().eq(GovWord::getTenantId, tid)));
        stats.put("modelCount", modelTableMapper.selectCount(new LambdaQueryWrapper<GovModelTable>().eq(GovModelTable::getTenantId, tid)));
        stats.put("derivedCount", derivedMapper.selectCount(new LambdaQueryWrapper<GovDerivedIndicator>().eq(GovDerivedIndicator::getTenantId, tid)));
        stats.put("atomicCount", atomicMapper.selectCount(new LambdaQueryWrapper<GovAtomicIndicator>().eq(GovAtomicIndicator::getTenantId, tid)));
        stats.put("limitCount", limitMapper.selectCount(new LambdaQueryWrapper<GovBusinessLimit>().eq(GovBusinessLimit::getTenantId, tid)));
        stats.put("periodCount", statPeriodMapper.selectCount(new LambdaQueryWrapper<GovStatPeriod>().eq(GovStatPeriod::getTenantId, tid)));
        // Recent changes - use recent models as changelog
        List<GovModelTable> recentModels = modelTableMapper.selectList(
                new LambdaQueryWrapper<GovModelTable>().eq(GovModelTable::getTenantId, tid)
                        .orderByDesc(GovModelTable::getUpdateTime).last("limit 10"));
        List<Map<String, Object>> recentChanges = recentModels.stream().map(m -> {
            Map<String, Object> item = new HashMap<>();
            item.put("time", m.getUpdateTime() != null ? m.getUpdateTime().toString() : "-");
            item.put("detail", "模型「" + m.getTableEn() + "」已更新");
            return item;
        }).collect(Collectors.toList());
        stats.put("recentChanges", recentChanges);
        return Result.ok(stats);
    }

    private LambdaQueryWrapper<GovCodeSet> tenantQw() {
        return new LambdaQueryWrapper<GovCodeSet>().eq(GovCodeSet::getTenantId, SecurityUtils.getCurrentTenantId());
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private String buildPreviewDdl(String engine, String tableEn, String tableCn, String layerCode, List<Map<String, Object>> fields) {
        List<Map<String, Object>> actualFields = fields == null ? new java.util.ArrayList<Map<String, Object>>() : fields;
        StringBuilder ddl = new StringBuilder();
        ddl.append("-- engine: ").append(engine).append('\n');
        if (StringUtils.hasText(layerCode)) {
            ddl.append("-- layer: ").append(layerCode).append('\n');
        }
        if ("mysql".equalsIgnoreCase(engine)) {
            ddl.append("CREATE TABLE IF NOT EXISTS `").append(tableEn).append("` (\n");
        } else {
            ddl.append("CREATE TABLE IF NOT EXISTS ").append(tableEn).append(" (\n");
        }

        List<String> columns = new java.util.ArrayList<>();
        List<String> primaryKeys = new java.util.ArrayList<>();
        String partitionColumn = null;
        for (Map<String, Object> field : actualFields) {
            String fieldEn = text(field.get("fieldEn"));
            if (!StringUtils.hasText(fieldEn)) {
                continue;
            }
            String dataType = text(field.get("dataType"));
            if (!StringUtils.hasText(dataType)) {
                dataType = "STRING";
            }
            String length = text(field.get("length"));
            String precision = text(field.get("precision"));
            boolean nullable = field.get("nullable") == null || Boolean.parseBoolean(String.valueOf(field.get("nullable")));
            boolean isPk = field.get("isPk") != null && Boolean.parseBoolean(String.valueOf(field.get("isPk")));
            boolean isPartition = field.get("isPartition") != null && Boolean.parseBoolean(String.valueOf(field.get("isPartition")));
            String comment = escapeComment(text(field.get("comment")));
            String defaultVal = escapeComment(text(field.get("defaultVal")));
            String columnType = normalizeColumnType(engine, dataType, length, precision);
            StringBuilder column = new StringBuilder();
            if ("mysql".equalsIgnoreCase(engine)) {
                column.append("  `").append(fieldEn).append("` ").append(columnType);
            } else {
                column.append("  ").append(fieldEn).append(" ").append(columnType);
            }
            if (!nullable) {
                column.append(" NOT NULL");
            }
            if (StringUtils.hasText(defaultVal)) {
                column.append(" DEFAULT '").append(defaultVal).append("'");
            }
            if (StringUtils.hasText(comment)) {
                column.append(" COMMENT '").append(comment).append("'");
            }
            columns.add(column.toString());
            if (isPk) {
                primaryKeys.add(fieldEn);
            }
            if (isPartition && partitionColumn == null) {
                partitionColumn = fieldEn;
            }
        }
        if (columns.isEmpty()) {
            columns.add("  id BIGINT");
        }
        ddl.append(String.join(",\n", columns));
        if (!primaryKeys.isEmpty() && "mysql".equalsIgnoreCase(engine)) {
            ddl.append(",\n  PRIMARY KEY (").append(String.join(", ", primaryKeys)).append(")");
        }
        ddl.append("\n)");
        if (StringUtils.hasText(tableCn)) {
            ddl.append(" COMMENT='").append(escapeComment(tableCn)).append("'");
        }
        if (StringUtils.hasText(partitionColumn) && !"mysql".equalsIgnoreCase(engine)) {
            ddl.append("\nPARTITIONED BY (").append(partitionColumn).append(" STRING)");
        }
        if ("mysql".equalsIgnoreCase(engine)) {
            ddl.append(" ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;");
        } else {
            ddl.append("\nSTORED AS PARQUET;");
        }
        return ddl.toString();
    }

    private String normalizeColumnType(String engine, String dataType, String length, String precision) {
        String type = dataType.toUpperCase();
        if ("MYSQL".equalsIgnoreCase(engine)) {
            if ("STRING".equals(type) || "VARCHAR".equals(type) || "CHAR".equals(type)) {
                return StringUtils.hasText(length) ? "VARCHAR(" + length + ")" : "VARCHAR(255)";
            }
            if ("INT".equals(type) || "INTEGER".equals(type)) {
                return "INT";
            }
            if ("BIGINT".equals(type)) {
                return "BIGINT";
            }
            if ("DOUBLE".equals(type) || "FLOAT".equals(type)) {
                return "DOUBLE";
            }
            if ("DECIMAL".equals(type)) {
                return StringUtils.hasText(precision) ? "DECIMAL(18," + precision + ")" : "DECIMAL(18,2)";
            }
            if ("DATE".equals(type)) {
                return "DATE";
            }
            if ("TIMESTAMP".equals(type) || "DATETIME".equals(type)) {
                return "DATETIME";
            }
            if ("BOOLEAN".equals(type) || "BOOL".equals(type)) {
                return "TINYINT(1)";
            }
            return type;
        }
        if ("VARCHAR".equals(type) || "CHAR".equals(type)) {
            return "STRING";
        }
        if ("INT".equals(type) || "INTEGER".equals(type)) {
            return "INT";
        }
        if ("BIGINT".equals(type)) {
            return "BIGINT";
        }
        if ("DOUBLE".equals(type) || "FLOAT".equals(type)) {
            return "DOUBLE";
        }
        if ("DECIMAL".equals(type)) {
            return StringUtils.hasText(precision) ? "DECIMAL(18," + precision + ")" : "DECIMAL(18,2)";
        }
        if ("DATE".equals(type)) {
            return "DATE";
        }
        if ("TIMESTAMP".equals(type) || "DATETIME".equals(type)) {
            return "TIMESTAMP";
        }
        if ("BOOLEAN".equals(type) || "BOOL".equals(type)) {
            return "BOOLEAN";
        }
        return type;
    }

    private String escapeComment(String value) {
        return value == null ? "" : value.replace("'", "''");
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
