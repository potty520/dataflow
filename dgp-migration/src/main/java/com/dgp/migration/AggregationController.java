package com.dgp.migration;

import com.dgp.common.BizException;
import com.dgp.common.ErrorCode;
import com.dgp.common.M;
import com.dgp.common.Result;
import com.dgp.security.AuthContext;
import com.dgp.security.RequirePerm;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** 聚合汇总：视图迁移向导 + 汇总刷新作业。 */
@RestController
@RequestMapping("/api/v1/migration/agg")
public class AggregationController {

    private final AggregationService service;

    public AggregationController(AggregationService service) {
        this.service = service;
    }

    @GetMapping
    @RequirePerm("migration:agg:view")
    public Result<Object> list(@RequestParam(required = false) String keyword) {
        return Result.ok(M.of("records", service.list(keyword)));
    }

    @GetMapping("/{id}")
    @RequirePerm("migration:agg:view")
    public Result<Object> detail(@PathVariable long id) {
        return Result.ok(service.detail(id));
    }

    @PostMapping
    @RequirePerm("migration:agg:create")
    public Result<Object> create(@RequestBody Map<String, Object> b) {
        return Result.ok(service.create(b));
    }

    @PutMapping("/{id}")
    @RequirePerm("migration:agg:edit")
    public Result<Object> update(@PathVariable long id, @RequestBody Map<String, Object> b) {
        return Result.ok(service.update(id, b));
    }

    @DeleteMapping("/{id}")
    @RequirePerm("migration:agg:edit")
    public Result<Object> delete(@PathVariable long id) {
        service.delete(id);
        return Result.ok(M.of("deleted", true));
    }

    /** 预览生成的视图 SQL 与目标表 DDL。 */
    @PostMapping("/{id}/preview")
    @RequirePerm("migration:agg:view")
    public Result<Object> preview(@PathVariable long id) {
        return Result.ok(service.preview(id));
    }

    /** 一键生成：源库视图 + 目标汇总表 + 迁移项目。 */
    @PostMapping("/{id}/build")
    @RequirePerm("migration:agg:edit")
    public Result<Object> build(@PathVariable long id) {
        return Result.ok(service.build(id));
    }

    /** 立即刷新（FULL 或 UPSERT）。 */
    @PostMapping("/{id}/refresh")
    @RequirePerm("migration:agg:exec")
    public Result<Object> refresh(@PathVariable long id,
                                  @RequestBody(required = false) Map<String, Object> b) {
        String trigger = b != null && b.get("trigger") != null ? String.valueOf(b.get("trigger")) : "MANUAL";
        return Result.ok(service.refresh(id, trigger, AuthContext.username()));
    }

    @GetMapping("/{id}/runs")
    @RequirePerm("migration:agg:view")
    public Result<Object> runs(@PathVariable long id) {
        return Result.ok(M.of("records", service.runs(id)));
    }

    @PostMapping("/{id}/enable")
    @RequirePerm("migration:agg:edit")
    public Result<Object> enable(@PathVariable long id, @RequestBody Map<String, Object> b) {
        boolean on = !Boolean.FALSE.equals(b.get("enabled"));
        service.update(id, Map.of("status", on ? "ENABLED" : "DISABLED"));
        return Result.ok(M.of("id", id, "status", on ? "ENABLED" : "DISABLED"));
    }
}
