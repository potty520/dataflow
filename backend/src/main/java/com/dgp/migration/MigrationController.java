package com.dgp.migration;

import com.dgp.common.BizException;
import com.dgp.common.Db;
import com.dgp.common.ErrorCode;
import com.dgp.common.M;
import com.dgp.common.Req;
import com.dgp.common.Result;
import com.dgp.common.TtlCache;
import com.dgp.security.AuthContext;
import com.dgp.security.RequirePerm;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 数据迁移映射：迁移项目、字段映射（推荐/保存）、预检、执行、对账。
 *
 * <p>典型流程：创建项目 → 拉取映射推荐 → 调整转换规则并保存 → 预检（DDL/类型/样例）→ 执行 →
 * 对账（行数/主键/空值/合计）→ 查看差异明细。
 */
@RestController
@RequestMapping("/api/v1/migration")
public class MigrationController {

    private final Db db;
    private final MigrationService service;

    public MigrationController(Db db, MigrationService service) {
        this.db = db;
        this.service = service;
    }

    // ------------------------------------------------------------ 概览/选项
    @GetMapping("/overview")
    @RequirePerm("migration:project:view")
    public Result<Object> overview() {
        Object cached = TtlCache.get("mig:overview:" + AuthContext.tenantId(), 5_000, service::overview);
        return Result.ok(cached);
    }

    /** 可选表（元数据表清单）。 */
    @GetMapping("/tables")
    @RequirePerm("migration:project:view")
    public Result<Object> tables(@RequestParam(required = false) String keyword) {
        if (keyword != null && !keyword.isBlank()) {
            return Result.ok(M.of("records", db.list(
                    "SELECT name, cn_name, layer FROM md_table WHERE name LIKE ? OR cn_name LIKE ? ORDER BY name",
                    "%" + keyword + "%", "%" + keyword + "%")));
        }
        return Result.ok(M.of("records", db.list("SELECT name, cn_name, layer FROM md_table ORDER BY name")));
    }

    @GetMapping("/tables/{table}/columns")
    @RequirePerm("migration:project:view")
    public Result<Object> columns(@PathVariable String table) {
        return Result.ok(M.of("records", service.columnsOf(Db.identifier(table))));
    }

    // ------------------------------------------------------------ 项目
    @GetMapping("/projects")
    @RequirePerm("migration:project:view")
    public Result<Object> projects(@RequestParam(required = false) String keyword,
                                   @RequestParam(required = false) String status,
                                   @RequestParam(defaultValue = "1") int pageNum,
                                   @RequestParam(defaultValue = "20") int pageSize) {
        StringBuilder where = new StringBuilder(" WHERE 1=1");
        List<Object> args = new ArrayList<>();
        if (keyword != null && !keyword.isBlank()) {
            where.append(" AND (name LIKE ? OR code LIKE ? OR source_table LIKE ? OR target_table LIKE ?)");
            for (int i = 0; i < 4; i++) args.add("%" + keyword + "%");
        }
        if (status != null && !status.isBlank()) {
            where.append(" AND status=?");
            args.add(status);
        }
        String base = "SELECT p.*, (SELECT COUNT(*) FROM mig_mapping m WHERE m.project_id=p.id) AS mapping_count, "
                + "(SELECT COUNT(*) FROM mig_check c WHERE c.project_id=p.id) AS check_count FROM mig_project p" + where;
        return Result.ok(db.page(base, "p.id DESC", Req.pageNum(pageNum), Req.pageSize(pageSize), args.toArray()).toMap());
    }

    @GetMapping("/projects/{id}")
    @RequirePerm("migration:project:view")
    public Result<Object> project(@PathVariable long id) {
        return Result.ok(service.projectDetail(id));
    }

    @PostMapping("/projects")
    @RequirePerm("migration:project:create")
    public Result<Object> createProject(@RequestBody Map<String, Object> b) {
        TtlCache.evict("mig");
        String code = Req.str(b.get("code"));
        if (Req.blank(code)) throw new BizException(ErrorCode.PARAM_INVALID, "项目编码不能为空");
        if (db.one("SELECT id FROM mig_project WHERE code=?", code) != null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "项目编码已存在：" + code);
        }
        String source = Db.identifier(Req.str(b.get("sourceTable")));
        String target = Db.identifier(Req.str(b.get("targetTable")));
        if (source.equals(target)) throw new BizException(ErrorCode.PARAM_INVALID, "源表与目标表不能相同");
        service.assertReadable(source);
        String mode = "INCREMENTAL".equalsIgnoreCase(Req.str(b.get("mode"))) ? "INCREMENTAL" : "FULL";
        String wm = Req.str(b.get("watermarkCol"));
        if (wm != null && !wm.isBlank()) Db.identifier(wm);
        long id = db.insert("INSERT INTO mig_project(tenant_id,code,name,source_ds_id,target_ds_id,source_table,target_table,"
                        + "mode,watermark_col,replace_target,batch_size,rollback_on_fail,status,description,owner,create_time,update_time) "
                        + "VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                1L, code, Req.str(b.getOrDefault("name", code)),
                Req.toInt(b.get("sourceDsId"), 0), Req.toInt(b.get("targetDsId"), 0), source, target, mode,
                (wm == null || wm.isBlank()) ? null : wm, Req.toInt(b.get("replaceTarget"), 1),
                Req.toInt(b.get("batchSize"), 5000), Req.toInt(b.get("rollbackOnFail"), 0),
                Req.str(b.getOrDefault("status", "DRAFT")), Req.str(b.get("description")),
                AuthContext.username(), LocalDateTime.now(), LocalDateTime.now());
        return Result.ok(M.of("id", id));
    }

    @PutMapping("/projects/{id}")
    @RequirePerm("migration:project:edit")
    public Result<Object> updateProject(@PathVariable long id, @RequestBody Map<String, Object> b) {
        TtlCache.evict("mig");
        Map<String, Object> before = requireProject(id);
        String source = Db.identifier(String.valueOf(pick(b, before, "sourceTable")));
        String target = Db.identifier(String.valueOf(pick(b, before, "targetTable")));
        String mode = "INCREMENTAL".equalsIgnoreCase(String.valueOf(pick(b, before, "mode"))) ? "INCREMENTAL" : "FULL";
        String wm = Req.str(pick(b, before, "watermarkCol"));
        db.update("UPDATE mig_project SET name=?,source_ds_id=?,target_ds_id=?,source_table=?,target_table=?,mode=?,watermark_col=?,"
                        + "replace_target=?,batch_size=?,rollback_on_fail=?,status=?,description=?,update_time=? WHERE id=?",
                pick(b, before, "name"),
                Req.toInt(pick(b, before, "sourceDsId"), 0), Req.toInt(pick(b, before, "targetDsId"), 0),
                source, target, mode,
                (wm == null || wm.isBlank()) ? null : Db.identifier(wm),
                Req.toInt(pick(b, before, "replaceTarget"), 1),
                Req.toInt(pick(b, before, "batchSize"), 5000),
                Req.toInt(pick(b, before, "rollbackOnFail"), 0),
                pick(b, before, "status"), pick(b, before, "description"), LocalDateTime.now(), id);
        return Result.ok(M.of("id", id));
    }

    @DeleteMapping("/projects/{id}")
    @RequirePerm("migration:project:edit")
    public Result<Object> deleteProject(@PathVariable long id) {
        TtlCache.evict("mig");
        db.update("DELETE FROM mig_diff WHERE run_id IN (SELECT id FROM mig_run WHERE project_id=?)", id);
        db.update("DELETE FROM mig_run WHERE project_id=?", id);
        db.update("DELETE FROM mig_check WHERE project_id=?", id);
        db.update("DELETE FROM mig_mapping WHERE project_id=?", id);
        db.update("DELETE FROM mig_project WHERE id=?", id);
        return Result.ok(M.of("deleted", true));
    }

    // ------------------------------------------------------------ 映射
    @GetMapping("/projects/{id}/mappings")
    @RequirePerm("migration:project:view")
    public Result<Object> mappings(@PathVariable long id) {
        return Result.ok(M.of("records",
                db.list("SELECT * FROM mig_mapping WHERE project_id=? ORDER BY sort_no,id", id)));
    }

    @GetMapping("/projects/{id}/recommend")
    @RequirePerm("migration:project:view")
    public Result<Object> recommend(@PathVariable long id) {
        return Result.ok(service.recommend(id));
    }

    @PostMapping("/projects/{id}/mappings")
    @RequirePerm("migration:project:edit")
    public Result<Object> saveMappings(@PathVariable long id, @RequestBody Map<String, Object> b) {
        TtlCache.evict("mig");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) b.get("mappings");
        if (items == null) throw new BizException(ErrorCode.PARAM_INVALID, "mappings 不能为空");
        service.saveMappings(id, items);
        return Result.ok(M.of("projectId", id, "mappingCount", items.size()));
    }

    // ------------------------------------------------------------ 检查项
    @PostMapping("/projects/{id}/checks")
    @RequirePerm("migration:project:edit")
    public Result<Object> saveChecks(@PathVariable long id, @RequestBody Map<String, Object> b) {
        TtlCache.evict("mig");
        requireProject(id);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) b.get("checks");
        if (items == null) throw new BizException(ErrorCode.PARAM_INVALID, "checks 不能为空");
        db.update("DELETE FROM mig_check WHERE project_id=?", id);
        for (Map<String, Object> c : items) {
            String type = String.valueOf(c.getOrDefault("checkType", "")).toUpperCase();
            if (type.isBlank()) continue;
            String col = Req.str(c.get("columnName"));
            if (col != null && !col.isBlank()) Db.identifier(col);
            db.insert("INSERT INTO mig_check(project_id,check_type,column_name,tolerance) VALUES(?,?,?,?)",
                    id, type, (col == null || col.isBlank()) ? null : col,
                    java.math.BigDecimal.valueOf(Db.num(c.getOrDefault("tolerance", 0)) == null
                            ? 0 : Db.num(c.get("tolerance"))));
        }
        return Result.ok(M.of("projectId", id, "checkCount", items.size()));
    }

    // ------------------------------------------------------------ 预检/执行/对账
    @PostMapping("/projects/{id}/precheck")
    @RequirePerm("migration:project:view")
    public Result<Object> precheck(@PathVariable long id) {
        return Result.ok(service.precheck(id));
    }

    @GetMapping("/projects/{id}/statement")
    @RequirePerm("migration:project:view")
    public Result<Object> statement(@PathVariable long id) {
        return Result.ok(service.statement(id));
    }


    /** 按字段映射一键创建目标表（目标表不存在时使用）。 */
    @PostMapping("/projects/{id}/create-target")
    @RequirePerm("migration:project:edit")
    public Result<Object> createTarget(@PathVariable long id) {
        TtlCache.evict("mig");
        return Result.ok(service.createTargetTable(id));
    }

    @PostMapping("/projects/{id}/run")
    @RequirePerm("migration:project:exec")
    public Result<Object> run(@PathVariable long id, @RequestBody(required = false) Map<String, Object> b) {
        TtlCache.evict("mig");
        boolean replace = b == null || Req.toInt(b.getOrDefault("replace", 1), 1) == 1;
        int batchSize = b == null ? 0 : Req.toInt(b.get("batchSize"), 0);
        return Result.ok(service.run(id, replace, batchSize, AuthContext.username()));
    }

    /** 回滚增量批次（删除该批次起始水位之后的写入）。 */
    @PostMapping("/runs/{runId}/rollback")
    @RequirePerm("migration:project:exec")
    public Result<Object> rollback(@PathVariable long runId) {
        TtlCache.evict("mig");
        return Result.ok(service.rollback(runId));
    }

    @GetMapping("/projects/{id}/runs")
    @RequirePerm("migration:project:view")
    public Result<Object> runs(@PathVariable long id,
                               @RequestParam(defaultValue = "1") int pageNum,
                               @RequestParam(defaultValue = "20") int pageSize) {
        return Result.ok(db.page("SELECT * FROM mig_run WHERE project_id=?", "id DESC",
                Req.pageNum(pageNum), Req.pageSize(pageSize), id).toMap());
    }

    @GetMapping("/runs/{runId}")
    @RequirePerm("migration:project:view")
    public Result<Object> runDetail(@PathVariable long runId) {
        Map<String, Object> run = db.one("SELECT * FROM mig_run WHERE id=?", runId);
        if (run == null) throw new BizException(ErrorCode.NOT_FOUND, "迁移批次不存在：" + runId);
        run.put("diffs", db.list("SELECT * FROM mig_diff WHERE run_id=? ORDER BY id", runId));
        return Result.ok(run);
    }

    @PostMapping("/runs/{runId}/reconcile")
    @RequirePerm("migration:project:reconcile")
    public Result<Object> reconcile(@PathVariable long runId) {
        return Result.ok(service.reconcile(runId));
    }

    @GetMapping("/runs/{runId}/diffs")
    @RequirePerm("migration:project:view")
    public Result<Object> diffs(@PathVariable long runId) {
        return Result.ok(M.of("records",
                db.list("SELECT * FROM mig_diff WHERE run_id=? ORDER BY id", runId)));
    }

    private Map<String, Object> requireProject(long id) {
        Map<String, Object> p = db.one("SELECT * FROM mig_project WHERE id=?", id);
        if (p == null) throw new BizException(ErrorCode.NOT_FOUND, "迁移项目不存在：" + id);
        return p;
    }

    private static Object pick(Map<String, Object> body, Map<String, Object> before, String key) {
        return body.containsKey(key) ? body.get(key) : before.get(key);
    }
}
