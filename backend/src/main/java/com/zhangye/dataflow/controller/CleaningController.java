package com.zhangye.dataflow.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhangye.dataflow.common.BusinessException;
import com.zhangye.dataflow.common.PageResult;
import com.zhangye.dataflow.common.Result;
import com.zhangye.dataflow.entity.GovModelTable;
import com.zhangye.dataflow.mapper.GovModelTableMapper;
import com.zhangye.dataflow.security.SecurityUtils;
import com.zhangye.dataflow.service.OperLogService;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/governance/cleaning")
public class CleaningController {

    private final GovModelTableMapper modelTableMapper;
    private final OperLogService operLogService;

    public CleaningController(GovModelTableMapper modelTableMapper, OperLogService operLogService) {
        this.modelTableMapper = modelTableMapper;
        this.operLogService = operLogService;
    }

    @GetMapping("/overview")
    public Result<Map<String, Object>> overview() {
        Long tid = SecurityUtils.getCurrentTenantId();
        List<GovModelTable> models = modelTableMapper.selectList(new LambdaQueryWrapper<GovModelTable>()
                .eq(GovModelTable::getTenantId, tid)
                .orderByDesc(GovModelTable::getUpdateTime));
        long configuredCount = models.stream().filter(this::hasRules).count();
        long ruleCount = models.stream().mapToLong(m -> splitRules(m.getCleanRules()).size()).sum();
        Map<String, Long> layerCount = models.stream().collect(Collectors.groupingBy(
                m -> StringUtils.hasText(m.getLayerCode()) ? m.getLayerCode() : "OTHER",
                Collectors.counting()));

        Map<String, Object> data = new HashMap<>();
        data.put("modelCount", models.size());
        data.put("configuredCount", configuredCount);
        data.put("pendingCount", Math.max(0, models.size() - configuredCount));
        data.put("ruleCount", ruleCount);
        data.put("layerCount", layerCount);
        data.put("recentModels", models.stream().limit(5).map(this::toSummary).collect(Collectors.toList()));
        return Result.ok(data);
    }

    @GetMapping("/templates")
    public Result<List<Map<String, Object>>> templates() {
        List<Map<String, Object>> items = new ArrayList<>();
        items.add(template("基础空值治理", "适合 ODS / STD 层", "空值过滤,字段修剪,默认值补齐"));
        items.add(template("重复数据治理", "适合明细和汇总表", "重复值去重,主键唯一性校验,异常记录隔离"));
        items.add(template("敏感字段治理", "适合个人信息和联系方式", "手机号脱敏,身份证脱敏,敏感字段访问留痕"));
        items.add(template("格式标准化", "适合日期、编码和状态字段", "日期格式标准化,编码值域校验,状态值统一"));
        return Result.ok(items);
    }

    @GetMapping("/page")
    public Result<PageResult<GovModelTable>> page(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String layerCode,
            @RequestParam(required = false) String ruleState) {
        LambdaQueryWrapper<GovModelTable> qw = new LambdaQueryWrapper<>();
        qw.eq(GovModelTable::getTenantId, SecurityUtils.getCurrentTenantId());
        if (StringUtils.hasText(layerCode)) {
            qw.eq(GovModelTable::getLayerCode, layerCode);
        }
        if (StringUtils.hasText(keyword)) {
            qw.and(w -> w.like(GovModelTable::getTableEn, keyword).or().like(GovModelTable::getTableCn, keyword));
        }
        if ("CONFIGURED".equalsIgnoreCase(ruleState)) {
            qw.and(w -> w.isNotNull(GovModelTable::getCleanRules).ne(GovModelTable::getCleanRules, ""));
        } else if ("EMPTY".equalsIgnoreCase(ruleState)) {
            qw.and(w -> w.isNull(GovModelTable::getCleanRules).or().eq(GovModelTable::getCleanRules, ""));
        }
        qw.orderByDesc(GovModelTable::getUpdateTime);
        Page<GovModelTable> p = modelTableMapper.selectPage(new Page<>(page, size), qw);
        return Result.ok(PageResult.of(p.getTotal(), page, size, p.getRecords()));
    }

    @PostMapping
    public Result<Void> create(@RequestBody GovModelTable model, HttpServletRequest req) {
        model.setTenantId(SecurityUtils.getCurrentTenantId());
        model.setCreateBy(SecurityUtils.getCurrentUsername());
        model.setCreateTime(LocalDateTime.now());
        model.setUpdateTime(LocalDateTime.now());
        modelTableMapper.insert(model);
        operLogService.log(req, "创建清洗模型", "数据清洗", model.getTableEn(), true, null);
        return Result.ok();
    }

    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody GovModelTable model, HttpServletRequest req) {
        model.setId(id);
        model.setUpdateTime(LocalDateTime.now());
        modelTableMapper.updateById(model);
        operLogService.log(req, "编辑清洗模型", "数据清洗", model.getTableEn(), true, null);
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id, HttpServletRequest req) {
        GovModelTable model = modelTableMapper.selectById(id);
        modelTableMapper.deleteById(id);
        operLogService.log(req, "删除清洗模型", "数据清洗", model != null ? model.getTableEn() : String.valueOf(id), true, null);
        return Result.ok();
    }

    @GetMapping("/{id}/preview")
    public Result<Map<String, Object>> preview(@PathVariable Long id) {
        GovModelTable model = modelTableMapper.selectById(id);
        if (model == null) {
            throw new BusinessException("清洗模型不存在");
        }
        return Result.ok(buildPreviewData(model));
    }

    @PostMapping("/{id}/execute")
    public Result<Map<String, Object>> execute(@PathVariable Long id, HttpServletRequest req) {
        GovModelTable model = modelTableMapper.selectById(id);
        if (model == null) {
            throw new BusinessException("清洗模型不存在");
        }
        Map<String, Object> data = buildPreviewData(model);
        data.put("executedAt", LocalDateTime.now());
        data.put("executionMode", "SIMULATION");
        operLogService.log(req, "执行清洗模型", "数据清洗", model.getTableEn(), true, "已完成清洗规则预演");
        return Result.ok(data);
    }

    private Map<String, Object> buildPreviewData(GovModelTable model) {
        List<String> rules = splitRules(model.getCleanRules());
        if (rules.isEmpty()) {
            rules = Collections.singletonList("空值过滤");
        }
        long beforeRows = estimateBeforeRows(model);
        long afterRows = estimateAfterRows(beforeRows, rules);

        Map<String, Object> data = new HashMap<>();
        data.put("model", toSummary(model));
        data.put("rules", rules);
        data.put("summary", buildSummary(beforeRows, afterRows, rules));
        data.put("steps", buildSteps(rules));
        data.put("suggestions", buildSuggestions(model, rules));
        return data;
    }

    private Map<String, Object> template(String name, String description, String rules) {
        Map<String, Object> item = new HashMap<>();
        item.put("name", name);
        item.put("description", description);
        item.put("rules", rules);
        return item;
    }

    private Map<String, Object> toSummary(GovModelTable model) {
        Map<String, Object> item = new HashMap<>();
        item.put("id", model.getId());
        item.put("layerCode", model.getLayerCode());
        item.put("tableEn", model.getTableEn());
        item.put("tableCn", model.getTableCn());
        item.put("cleanRules", model.getCleanRules());
        item.put("ruleCount", splitRules(model.getCleanRules()).size());
        item.put("updatedAt", model.getUpdateTime());
        item.put("configured", hasRules(model));
        return item;
    }

    private Map<String, Object> buildSummary(long beforeRows, long afterRows, List<String> rules) {
        Map<String, Object> summary = new HashMap<>();
        summary.put("beforeRows", beforeRows);
        summary.put("afterRows", afterRows);
        summary.put("removedRows", Math.max(0, beforeRows - afterRows));
        summary.put("ruleCount", rules.size());
        summary.put("riskLevel", detectRiskLevel(rules));
        return summary;
    }

    private List<Map<String, Object>> buildSteps(List<String> rules) {
        List<Map<String, Object>> steps = new ArrayList<>();
        steps.add(step("1", "读取源表", "扫描字段、样本值与空值分布", "输入"));
        int index = 2;
        for (String rule : rules) {
            steps.add(step(String.valueOf(index), classifyRule(rule), rule, classifyImpact(rule)));
            index++;
        }
        steps.add(step(String.valueOf(index), "输出结果", "生成标准化后的清洗结果与问题明细", "输出"));
        return steps;
    }

    private Map<String, Object> step(String order, String title, String detail, String impact) {
        Map<String, Object> item = new HashMap<>();
        item.put("order", order);
        item.put("title", title);
        item.put("detail", detail);
        item.put("impact", impact);
        return item;
    }

    private List<String> buildSuggestions(GovModelTable model, List<String> rules) {
        if (!hasRules(model)) {
            return Arrays.asList("建议先补充空值过滤、重复值去重、敏感字段脱敏三类基础规则", "可先在标准层完成清洗，再下沉到明细层");
        }
        List<String> suggestions = new ArrayList<>();
        if (rules.stream().noneMatch(this::containsSensitiveKeyword)) {
            suggestions.add("建议补充敏感字段脱敏，减少报表层扩散风险");
        }
        if (rules.stream().noneMatch(this::containsDuplicateKeyword)) {
            suggestions.add("建议增加重复值去重，避免口径重复统计");
        }
        if (suggestions.isEmpty()) {
            suggestions.add("当前清洗规则已覆盖核心场景，可直接作为标准清洗模板");
        }
        return suggestions;
    }

    private String detectRiskLevel(List<String> rules) {
        long sensitiveCount = rules.stream().filter(this::containsSensitiveKeyword).count();
        long total = rules.size();
        if (sensitiveCount > 0 || total >= 5) {
            return "HIGH";
        }
        if (total >= 3) {
            return "MEDIUM";
        }
        return "LOW";
    }

    private String classifyRule(String rule) {
        if (containsSensitiveKeyword(rule)) {
            return "敏感字段处理";
        }
        if (containsDuplicateKeyword(rule)) {
            return "重复值处理";
        }
        if (rule.contains("空")) {
            return "空值处理";
        }
        if (rule.contains("格式")) {
            return "格式标准化";
        }
        if (rule.contains("值域") || rule.contains("范围")) {
            return "值域校验";
        }
        return "自定义清洗";
    }

    private String classifyImpact(String rule) {
        if (containsSensitiveKeyword(rule)) {
            return "脱敏";
        }
        if (containsDuplicateKeyword(rule)) {
            return "去重";
        }
        if (rule.contains("空")) {
            return "过滤/补齐";
        }
        if (rule.contains("格式")) {
            return "标准化";
        }
        return "修正";
    }

    private boolean containsSensitiveKeyword(String text) {
        return StringUtils.hasText(text) && (text.contains("脱敏") || text.contains("敏感"));
    }

    private boolean containsDuplicateKeyword(String text) {
        return StringUtils.hasText(text) && (text.contains("重复") || text.contains("去重"));
    }

    private boolean hasRules(GovModelTable model) {
        return StringUtils.hasText(model.getCleanRules());
    }

    private List<String> splitRules(String cleanRules) {
        if (!StringUtils.hasText(cleanRules)) {
            return Collections.emptyList();
        }
        return Arrays.stream(cleanRules.split("[,，;；\\n\\r]+"))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .distinct()
                .collect(Collectors.toList());
    }

    private long estimateBeforeRows(GovModelTable model) {
        long base = 8000L + Math.abs(Objects.hash(model.getLayerCode(), model.getTableEn())) % 6000L;
        return Math.max(1000L, base);
    }

    private long estimateAfterRows(long beforeRows, List<String> rules) {
        long reduction = Math.min(beforeRows - 500L, Math.max(120L, rules.size() * 180L));
        return Math.max(500L, beforeRows - reduction);
    }
}
