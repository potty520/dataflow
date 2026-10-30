package com.dgp.bpm;

import com.dgp.common.M;
import com.dgp.common.Result;
import com.dgp.security.AuthContext;
import com.dgp.security.RequirePerm;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** BPM 审批接口（P1 最小闭环）。 */
@RestController
@RequestMapping("/api/v1/bpm")
public class BpmController {

    private final BpmService bpm;

    public BpmController(BpmService bpm) {
        this.bpm = bpm;
    }

    @GetMapping("/defs")
    @RequirePerm("bpm:def:view")
    public Result<Object> defs() {
        return Result.ok(M.of("records", bpm.definitions()));
    }

    @PostMapping("/defs")
    @RequirePerm("bpm:def:edit")
    public Result<Object> createDef(@RequestBody Map<String, Object> b) {
        return Result.ok(bpm.createDefinition(b));
    }

    @PutMapping("/defs/{id}")
    @RequirePerm("bpm:def:edit")
    public Result<Object> updateDef(@PathVariable long id, @RequestBody Map<String, Object> b) {
        return Result.ok(bpm.updateDefinition(id, b));
    }

    @PostMapping("/defs/{id}/publish")
    @RequirePerm("bpm:def:edit")
    public Result<Object> publish(@PathVariable long id) {
        bpm.publish(id);
        return Result.ok(M.of("id", id, "status", "PUBLISHED"));
    }

    @DeleteMapping("/defs/{id}")
    @RequirePerm("bpm:def:edit")
    public Result<Object> deleteDef(@PathVariable long id) {
        bpm.deleteDefinition(id);
        return Result.ok(M.of("deleted", true));
    }

    @PostMapping("/instances")
    @RequirePerm("bpm:task:start")
    public Result<Object> start(@RequestBody Map<String, Object> b) {
        String defCode = String.valueOf(b.get("defCode"));
        return Result.ok(bpm.start(defCode, (String) b.get("title"), (String) b.get("businessType"),
                (String) b.get("businessId"), b.get("form"), AuthContext.username()));
    }

    @GetMapping("/instances/mine")
    @RequirePerm("bpm:task:view")
    public Result<Object> mine() {
        return Result.ok(M.of("records", bpm.mine()));
    }

    @GetMapping("/instances/{id}")
    @RequirePerm("bpm:task:view")
    public Result<Object> detail(@PathVariable long id) {
        return Result.ok(bpm.detail(id));
    }

    @GetMapping("/tasks/todo")
    @RequirePerm("bpm:task:view")
    public Result<Object> todo() {
        return Result.ok(M.of("records", bpm.todo()));
    }

    @PostMapping("/tasks/{id}/approve")
    @RequirePerm("bpm:task:handle")
    public Result<Object> approve(@PathVariable long id, @RequestBody(required = false) Map<String, Object> b) {
        bpm.approve(id, b == null ? null : (String) b.get("comment"));
        return Result.ok(M.of("id", id, "status", "APPROVED"));
    }

    @PostMapping("/tasks/{id}/reject")
    @RequirePerm("bpm:task:handle")
    public Result<Object> reject(@PathVariable long id, @RequestBody(required = false) Map<String, Object> b) {
        bpm.reject(id, b == null ? null : (String) b.get("comment"));
        return Result.ok(M.of("id", id, "status", "REJECTED"));
    }

    @PostMapping("/tasks/{id}/transfer")
    @RequirePerm("bpm:task:handle")
    public Result<Object> transfer(@PathVariable long id, @RequestBody Map<String, Object> b) {
        bpm.transfer(id, (String) b.get("assignee"), (String) b.get("assigneeRole"), (String) b.get("comment"));
        return Result.ok(M.of("id", id, "status", "TRANSFERRED"));
    }
}
