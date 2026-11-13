package com.dgp.migration;

import com.dgp.common.BizException;
import com.dgp.common.ErrorCode;
import com.dgp.common.M;
import com.dgp.common.Result;
import com.dgp.security.RequirePerm;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** 远程（跨网络）数据源管理：直连 / SOCKS5 / HTTP 代理，支持连接测试与字段探测。 */
@RestController
@RequestMapping("/api/v1/migration/datasources")
public class RemoteDsController {

    private final RemoteDsService service;

    public RemoteDsController(RemoteDsService service) {
        this.service = service;
    }

    @GetMapping
    @RequirePerm("migration:ds:view")
    public Result<Object> list(@RequestParam(required = false) String keyword) {
        return Result.ok(M.of("records", service.list(keyword)));
    }

    @GetMapping("/{id}")
    @RequirePerm("migration:ds:view")
    public Result<Object> detail(@PathVariable long id) {
        return Result.ok(service.detail(id));
    }

    @PostMapping
    @RequirePerm("migration:ds:edit")
    public Result<Object> create(@RequestBody Map<String, Object> b) {
        return Result.ok(service.create(b));
    }

    @PutMapping("/{id}")
    @RequirePerm("migration:ds:edit")
    public Result<Object> update(@PathVariable long id, @RequestBody Map<String, Object> b) {
        return Result.ok(service.update(id, b));
    }

    @DeleteMapping("/{id}")
    @RequirePerm("migration:ds:edit")
    public Result<Object> delete(@PathVariable long id) {
        service.delete(id);
        return Result.ok(M.of("deleted", true));
    }

    @PostMapping("/{id}/test")
    @RequirePerm("migration:ds:test")
    public Result<Object> test(@PathVariable long id) {
        return Result.ok(service.test(id));
    }

    /** 未保存配置的临时测试（前端表单"测试连接"用）。 */
    @PostMapping("/test")
    @RequirePerm("migration:ds:test")
    public Result<Object> testAdhoc(@RequestBody Map<String, Object> b) {
        return Result.ok(service.testAdhoc(b));
    }

    @GetMapping("/{id}/columns")
    @RequirePerm("migration:ds:view")
    public Result<Object> columns(@PathVariable long id, @RequestParam String table) {
        if (table == null || table.isBlank()) throw new BizException(ErrorCode.PARAM_INVALID, "table 不能为空");
        return Result.ok(M.of("records", service.columnsOf(id, table)));
    }
}
