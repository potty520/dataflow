package com.dgp.migration;

import com.dgp.common.M;
import com.dgp.common.Result;
import com.dgp.security.AuthContext;
import com.dgp.security.RequirePerm;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** FTP 文件迁移任务：表 → CSV → FTP ↔ CSV → 表。 */
@RestController
@RequestMapping("/api/v1/migration/files")
public class FileMigrationController {

    private final FileMigrationService service;

    public FileMigrationController(FileMigrationService service) {
        this.service = service;
    }

    @GetMapping("/tasks")
    @RequirePerm("migration:file:view")
    public Result<Object> list(@RequestParam(required = false) String keyword) {
        return Result.ok(M.of("records", service.listTasks(keyword)));
    }

    @GetMapping("/tasks/{id}")
    @RequirePerm("migration:file:view")
    public Result<Object> detail(@PathVariable long id) {
        return Result.ok(service.taskDetail(id));
    }

    @PostMapping("/tasks")
    @RequirePerm("migration:file:edit")
    public Result<Object> create(@RequestBody Map<String, Object> b) {
        return Result.ok(service.createTask(b));
    }

    @PutMapping("/tasks/{id}")
    @RequirePerm("migration:file:edit")
    public Result<Object> update(@PathVariable long id, @RequestBody Map<String, Object> b) {
        return Result.ok(service.updateTask(id, b));
    }

    @DeleteMapping("/tasks/{id}")
    @RequirePerm("migration:file:edit")
    public Result<Object> delete(@PathVariable long id) {
        service.deleteTask(id);
        return Result.ok(M.of("deleted", true));
    }

    @PostMapping("/tasks/{id}/run")
    @RequirePerm("migration:file:exec")
    public Result<Object> run(@PathVariable long id) {
        return Result.ok(service.run(id, AuthContext.username()));
    }

    @GetMapping("/tasks/{id}/runs")
    @RequirePerm("migration:file:view")
    public Result<Object> runs(@PathVariable long id) {
        return Result.ok(M.of("records", service.runs(id)));
    }

    /** 未保存配置的 FTP 连通性测试。 */
    @PostMapping("/test")
    @RequirePerm("migration:file:view")
    public Result<Object> test(@RequestBody Map<String, Object> b) {
        return Result.ok(service.testFtp(b));
    }
}
