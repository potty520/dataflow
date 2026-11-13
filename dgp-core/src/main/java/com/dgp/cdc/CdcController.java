package com.dgp.cdc;

import com.dgp.common.M;
import com.dgp.common.Result;
import com.dgp.security.RequirePerm;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** CDC / 整库同步接口。 */
@RestController
@RequestMapping("/api/v1/cdc")
public class CdcController {

    private final CdcService cdc;

    public CdcController(CdcService cdc) {
        this.cdc = cdc;
    }

    @GetMapping("/tasks")
    @RequirePerm("cdc:view")
    public Result<Object> tasks() {
        return Result.ok(M.of("records", cdc.tasks()));
    }

    @PostMapping("/tasks")
    @RequirePerm("cdc:edit")
    public Result<Object> save(@RequestBody Map<String, Object> b) {
        return Result.ok(cdc.save(b));
    }

    @DeleteMapping("/tasks/{id}")
    @RequirePerm("cdc:edit")
    public Result<Object> delete(@PathVariable long id) {
        cdc.delete(id);
        return Result.ok(M.of("deleted", true));
    }

    @PostMapping("/tasks/{id}/run")
    @RequirePerm("cdc:run")
    public Result<Object> run(@PathVariable long id) {
        return Result.ok(cdc.run(id));
    }

    @GetMapping("/runs")
    @RequirePerm("cdc:view")
    public Result<Object> runs(@RequestParam(required = false) Long taskId) {
        return Result.ok(M.of("records", cdc.runs(taskId)));
    }
}
