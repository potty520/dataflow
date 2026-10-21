package com.dgp.model;

import com.dgp.common.*;
import com.dgp.metadata.service.LineageService;
import com.dgp.security.AuthContext;
import com.dgp.security.RequirePerm;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** dgp-model:8083：概念/逻辑/物理模型、发布审批、版本对比、影响分析、自助建表。 */
@RestController
@RequestMapping("/api/v1/model")
public class ModelController {

    /** SQL 保留字：实体/属性编码若命中，建表会失败或产生歧义。 */
    private static final Set<String> RESERVED = Set.of(
            "select", "from", "where", "group", "order", "by", "insert", "update", "delete", "table", "index",
            "primary", "key", "foreign", "references", "user", "default", "check", "unique", "create",
            "drop", "alter", "join", "on", "as", "and", "or", "not", "null", "values", "into", "set");

    private final Db db;
    private final LineageService lineageService;

    public ModelController(Db db, LineageService lineageService) {
        this.db = db;
        this.lineageService = lineageService;
    }

    @GetMapping("/models")
    @RequirePerm("model:model:view")
    public Result<Object> models(@RequestParam(required = false) String keyword,
                                 @RequestParam(required = false) String modelType,
                                 @RequestParam(required = false) String industry,
                                 @RequestParam(defaultValue = "1") int pageNum,
                                 @RequestParam(defaultValue = "20") int pageSize) {
        StringBuilder where = new StringBuilder(" WHERE 1=1");
        List<Object> args = new ArrayList<>();
        if (!Req.blank(keyword)) {
            where.append(" AND (m.name LIKE ? OR m.cn_name LIKE ? OR m.code LIKE ?)");
            for (int i = 0; i < 3; i++) args.add("%" + keyword + "%");
        }
        if (!Req.blank(modelType)) {
            where.append(" AND m.model_type=?");
            args.add(modelType);
        }
        if (!Req.blank(industry)) {
            where.append(" AND m.industry=?");
            args.add(industry);
        }
        String base = "SELECT m.*, (SELECT COUNT(*) FROM mdl_entity e WHERE e.model_id=m.id) AS entity_count, "
                + "(SELECT COUNT(*) FROM mdl_attribute a JOIN mdl_entity e ON e.id=a.entity_id WHERE e.model_id=m.id) AS attribute_count "
                + "FROM mdl_model m" + where;
        return Result.ok(db.page(base, "m.id DESC", pageNum, pageSize, args.toArray()).toMap());
    }

    @GetMapping("/models/{id}")
    @RequirePerm("model:model:view")
    public Result<Object> model(@PathVariable long id) {
        Map<String, Object> m = db.one("SELECT * FROM mdl_model WHERE id=?", id);
        if (m == null) throw new BizException(ErrorCode.NOT_FOUND);
        List<Map<String, Object>> entities = db.list("SELECT * FROM mdl_entity WHERE model_id=? ORDER BY id", id);
        List<Map<String, Object>> rels = db.list("SELECT * FROM mdl_relation WHERE model_id=?", id);
        for (Map<String, Object> e : entities) {
            e.put("attributes", db.list("SELECT * FROM mdl_attribute WHERE entity_id=? ORDER BY attr_order", e.get("id")));
        }
        Map<String, Object> out = new LinkedHashMap<>(m);
        out.put("entities", entities);
        out.put("relations", rels);
        out.put("approvals", db.list("SELECT * FROM mdl_publish_flow WHERE model_id=? ORDER BY id DESC", id));
        out.put("versions", db.list("SELECT id,version,change_note,operator,create_time FROM mdl_version WHERE model_id=? ORDER BY id DESC", id));
        return Result.ok(out);
    }

    @PostMapping("/models")
    @RequirePerm("model:model:create")
    public Result<Object> createModel(@RequestBody Map<String, Object> b) {
        String code = Req.str(b.get("code"));
        String name = Req.str(b.get("name"));
        if (Req.blank(name)) throw new BizException(ErrorCode.PARAM_INVALID, "模型名称不能为空");
        if (Req.blank(code)) code = "MDL_" + Ids.uuid().substring(0, 8).toUpperCase();
        long id = db.insert("INSERT INTO mdl_model(tenant_id,code,name,cn_name,model_type,layer,industry,domain,status,version,owner,"
                        + "description,create_time,update_time) VALUES(?,?,?,?,?,?,?,?,'DRAFT','1.0.0',?,?,?,?)",
                AuthContext.tenantId(), code, name, b.get("cnName"), b.getOrDefault("modelType", "LOGICAL"),
                b.getOrDefault("layer", "DWD"), b.getOrDefault("industry", "GOV"), b.get("domain"),
                AuthContext.username(), b.get("description"), LocalDateTime.now(), LocalDateTime.now());
        return Result.ok(M.of("id", id, "code", code));
    }

    @PutMapping("/models/{id}")
    @RequirePerm("model:model:edit")
    public Result<Object> updateModel(@PathVariable long id, @RequestBody Map<String, Object> b) {
        Map<String, Object> before = db.one("SELECT * FROM mdl_model WHERE id=?", id);
        if (before == null) throw new BizException(ErrorCode.NOT_FOUND, "模型不存在");
        db.update("UPDATE mdl_model SET cn_name=?,layer=?,domain=?,description=?,update_time=? WHERE id=?",
                b.containsKey("cnName") ? b.get("cnName") : before.get("cnName"),
                b.containsKey("layer") ? b.get("layer") : before.get("layer"),
                b.containsKey("domain") ? b.get("domain") : before.get("domain"),
                b.containsKey("description") ? b.get("description") : before.get("description"),
                LocalDateTime.now(), id);
        return Result.ok(M.of("id", id));
    }

    @DeleteMapping("/models/{id}")
    @RequirePerm("model:model:delete")
    @Transactional
    public Result<Object> deleteModel(@PathVariable long id) {
        Map<String, Object> m = db.one("SELECT * FROM mdl_model WHERE id=?", id);
        if (m == null) throw new BizException(ErrorCode.NOT_FOUND, "模型不存在");
        long refs = db.count("SELECT COUNT(*) FROM mdl_entity WHERE model_id=?", id);
        if (refs > 0 && "PUBLISHED".equals(String.valueOf(m.get("status")))) {
            throw new BizException(ErrorCode.MODEL_REFERENCED, "已发布模型包含实体，禁止直接删除，请先下线");
        }
        db.update("DELETE FROM mdl_attribute WHERE entity_id IN (SELECT id FROM mdl_entity WHERE model_id=?)", id);
        db.update("DELETE FROM mdl_entity WHERE model_id=?", id);
        db.update("DELETE FROM mdl_relation WHERE model_id=?", id);
        db.update("DELETE FROM mdl_publish_flow WHERE model_id=?", id);
        db.update("DELETE FROM mdl_version WHERE model_id=?", id);
        db.update("DELETE FROM mdl_ddl_record WHERE model_id=?", id);
        db.update("DELETE FROM mdl_model WHERE id=?", id);
        return Result.ok(M.of("deleted", true));
    }

    @PutMapping("/models/{id}/graph")
    @RequirePerm("model:model:edit")
    public Result<Object> saveGraph(@PathVariable long id, @RequestBody Map<String, Object> b) {
        Object nodes = b.get("nodes");
        int updated = 0;
        if (nodes instanceof List<?> list) {
            for (Object o : list) {
                if (o instanceof Map<?, ?> n) {
                    updated += db.update("UPDATE mdl_entity SET pos_x=?,pos_y=? WHERE model_id=? AND code=?",
                            Db.num(n.get("x")) == null ? 0 : Db.num(n.get("x")),
                            Db.num(n.get("y")) == null ? 0 : Db.num(n.get("y")), id, n.get("code"));
                }
            }
        }
        return Result.ok(M.of("saved", true, "updated", updated));
    }

    @PostMapping("/models/{id}/publish")
    @RequirePerm("model:model:publish")
    public Result<Object> publish(@PathVariable long id, @RequestBody(required = false) Map<String, Object> b) {
        Map<String, Object> m = db.one("SELECT * FROM mdl_model WHERE id=?", id);
        if (m == null) throw new BizException(ErrorCode.NOT_FOUND);
        String status = String.valueOf(m.get("status"));
        if (!"DRAFT".equals(status) && !"REJECTED".equals(status)) {
            throw new BizException(ErrorCode.MODEL_STATE_ILLEGAL, "只有草稿/已驳回状态的模型可以提交发布，当前：" + status);
        }
        if (db.count("SELECT COUNT(*) FROM mdl_publish_flow WHERE model_id=? AND status='PENDING'", id) > 0) {
            throw new BizException(ErrorCode.MODEL_STATE_ILLEGAL, "该模型已有进行中的发布审批");
        }
        long entities = db.count("SELECT COUNT(*) FROM mdl_entity WHERE model_id=?", id);
        if (entities == 0) throw new BizException(ErrorCode.PARAM_INVALID, "模型不包含任何实体，无法发布");
        String version = String.valueOf(m.get("version"));
        long flow = db.insert("INSERT INTO mdl_publish_flow(model_id,model_name,version,applicant,auditor,status,comment,create_time) "
                        + "VALUES(?,?,?,?,NULL,'PENDING',?,?)",
                id, m.get("cnName"), version, AuthContext.username(),
                b == null ? null : b.get("comment"), LocalDateTime.now());
        db.update("UPDATE mdl_model SET status='REVIEWING',update_time=? WHERE id=?", LocalDateTime.now(), id);
        return Result.ok(M.of("flowId", flow, "status", "REVIEWING", "version", version));
    }

    @PostMapping("/models/{id}/approve")
    @RequirePerm("model:model:approve")
    @Transactional
    public Result<Object> approve(@PathVariable long id, @RequestBody Map<String, Object> b) {
        Map<String, Object> m = db.one("SELECT * FROM mdl_model WHERE id=?", id);
        if (m == null) throw new BizException(ErrorCode.NOT_FOUND);
        if (!"REVIEWING".equals(String.valueOf(m.get("status")))) {
            throw new BizException(ErrorCode.MODEL_STATE_ILLEGAL, "模型不在待审批状态：" + m.get("status"));
        }
        Map<String, Object> flowRow = db.one("SELECT * FROM mdl_publish_flow WHERE model_id=? AND status='PENDING' ORDER BY id DESC LIMIT 1", id);
        if (flowRow == null) throw new BizException(ErrorCode.MODEL_STATE_ILLEGAL, "没有待审批的发布申请");
        if (AuthContext.username().equals(String.valueOf(flowRow.get("applicant")))) {
            throw new BizException(ErrorCode.FORBIDDEN, "发布申请人不能审批自己的申请");
        }
        boolean pass = !"REJECTED".equals(String.valueOf(b.getOrDefault("result", "APPROVED")));
        db.update("UPDATE mdl_publish_flow SET status=?,comment=?,auditor=?,finish_time=? WHERE id=?",
                pass ? "APPROVED" : "REJECTED", b.get("comment"), AuthContext.username(), LocalDateTime.now(), flowRow.get("id"));
        if (pass) {
            String newVersion = bumpVersion(String.valueOf(m.get("version")));
            db.update("UPDATE mdl_model SET status='PUBLISHED',version=?,publish_time=?,update_time=? WHERE id=?",
                    newVersion, LocalDateTime.now(), LocalDateTime.now(), id);
            db.insert("INSERT INTO mdl_version(model_id,version,snapshot,change_note,operator,create_time) VALUES(?,?,?,?,?,?)",
                    id, newVersion, snapshot(id), String.valueOf(b.getOrDefault("comment", "发布审批通过")),
                    AuthContext.username(), LocalDateTime.now());
            return Result.ok(M.of("id", id, "status", "PUBLISHED", "version", newVersion));
        }
        db.update("UPDATE mdl_model SET status='DRAFT',update_time=? WHERE id=?", LocalDateTime.now(), id);
        return Result.ok(M.of("id", id, "status", "DRAFT"));
    }

    private String bumpVersion(String v) {
        String[] p = v.split("\\.");
        int patch = p.length > 2 ? Integer.parseInt(p[2]) + 1 : 1;
        return (p.length > 1 ? p[0] + "." + p[1] : v) + "." + patch;
    }

    @GetMapping("/models/{id}/versions")
    @RequirePerm("model:model:view")
    public Result<Object> versions(@PathVariable long id) {
        return Result.ok(M.of("records", db.list("SELECT id,version,change_note,operator,create_time FROM mdl_version WHERE model_id=? ORDER BY id DESC", id)));
    }

    /** 版本对比：真正按快照计算的差异（原实现 changeCount 恒为 1 + 固定文案）。 */
    @GetMapping("/models/{id}/versions/diff")
    @RequirePerm("model:model:view")
    public Result<Object> versionDiff(@PathVariable long id, @RequestParam long leftId, @RequestParam long rightId) {
        Map<String, Object> l = db.one("SELECT * FROM mdl_version WHERE id=? AND model_id=?", leftId, id);
        Map<String, Object> r = db.one("SELECT * FROM mdl_version WHERE id=? AND model_id=?", rightId, id);
        if (l == null || r == null) throw new BizException(ErrorCode.NOT_FOUND, "版本不存在或不属于该模型");
        Map<String, Object> a = JsonUtil.parse(String.valueOf(l.get("snapshot")));
        Map<String, Object> b = JsonUtil.parse(String.valueOf(r.get("snapshot")));
        List<Map<String, Object>> changes = new ArrayList<>();
        for (String k : b.keySet()) {
            if (!String.valueOf(b.get(k)).equals(String.valueOf(a.get(k)))) {
                changes.add(M.of("field", k, "before", a.get(k), "after", b.get(k)));
            }
        }
        for (String k : a.keySet()) {
            if (!b.containsKey(k)) changes.add(M.of("field", k, "before", a.get(k), "after", null));
        }
        return Result.ok(M.of("left", l.get("version"), "right", r.get("version"),
                "leftSnapshot", a, "rightSnapshot", b,
                "leftNote", l.get("changeNote"), "rightNote", r.get("changeNote"),
                "changeCount", changes.size(), "changes", changes));
    }

    @GetMapping("/models/{id}/impact")
    @RequirePerm("model:model:view")
    public Result<Object> impact(@PathVariable long id) {
        List<Map<String, Object>> entities = db.list("SELECT code,name FROM mdl_entity WHERE model_id=?", id);
        List<Map<String, Object>> impacts = new ArrayList<>();
        for (Map<String, Object> e : entities) {
            Map<String, Object> im = lineageService.impact(String.valueOf(e.get("code")), 2);
            impacts.add(M.of("entity", e.get("code"), "name", e.get("name"),
                    "downstreamTables", im.get("tableCount"), "summary", im.get("summary"), "detail", im.get("downstreamTables")));
        }
        return Result.ok(M.of("modelId", id, "entityCount", entities.size(), "impacts", impacts));
    }

    @PostMapping("/models/{id}/ddl")
    @RequirePerm("model:ddl:preview")
    public Result<Object> ddl(@PathVariable long id) {
        List<Map<String, Object>> entities = db.list("SELECT * FROM mdl_entity WHERE model_id=?", id);
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> e : entities) {
            out.add(M.of("entity", e.get("code"), "cnName", e.get("cnName"), "ddl", buildDdl(e)));
        }
        return Result.ok(M.of("modelId", id, "statements", out, "count", out.size()));
    }

    /** 真实建表：仅允许已发布模型，且实体/属性编码必须通过标识符与保留字校验。 */
    @PostMapping("/entities/{entityId}/ddl/execute")
    @RequirePerm("model:ddl:execute")
    @Transactional
    public Result<Object> executeDdl(@PathVariable long entityId) {
        Map<String, Object> e = db.one("SELECT * FROM mdl_entity WHERE id=?", entityId);
        if (e == null) throw new BizException(ErrorCode.NOT_FOUND);
        Map<String, Object> model = db.one("SELECT status FROM mdl_model WHERE id=?", e.get("modelId"));
        if (model == null || !"PUBLISHED".equals(String.valueOf(model.get("status")))) {
            throw new BizException(ErrorCode.MODEL_STATE_ILLEGAL, "只有已发布模型的实体才能执行建表");
        }
        String ddl = buildDdl(e);
        db.update(ddl);
        db.insert("INSERT INTO mdl_ddl_record(model_id,entity_id,table_name,ddl_text,status,operator,create_time) "
                + "VALUES(?,?,?,?,'SUCCESS',?,?)", e.get("modelId"), entityId, e.get("code"), ddl,
                AuthContext.username(), LocalDateTime.now());
        return Result.ok(M.of("executed", true, "table", e.get("code"), "ddl", ddl));
    }

    private String buildDdl(Map<String, Object> e) {
        String tableCode = safeIdentifier(String.valueOf(e.get("code")), "实体编码");
        List<Map<String, Object>> attrs = db.list("SELECT * FROM mdl_attribute WHERE entity_id=? ORDER BY attr_order", e.get("id"));
        if (attrs.isEmpty()) throw new BizException(ErrorCode.PARAM_INVALID, "实体没有任何属性，无法生成 DDL");
        StringBuilder sb = new StringBuilder("CREATE TABLE IF NOT EXISTS ").append(tableCode).append(" (\n");
        List<String> pk = new ArrayList<>();
        for (int i = 0; i < attrs.size(); i++) {
            Map<String, Object> a = attrs.get(i);
            String colCode = safeIdentifier(String.valueOf(a.get("code")), "属性编码");
            sb.append("  ").append(colCode).append(" ").append(sqlType(
                    String.valueOf(a.get("dataType")), String.valueOf(a.get("dataLen"))));
            if (Db.longVal(a.get("isPk"), 0L) == 1L) pk.add(colCode);
            if (Db.longVal(a.get("nullable"), 1L) == 0L && Db.longVal(a.get("isPk"), 0L) != 1L) sb.append(" NOT NULL");
            if (i < attrs.size() - 1) sb.append(",");
            sb.append("\n");
        }
        if (!pk.isEmpty()) {
            sb.append("  ,PRIMARY KEY (").append(String.join(",", pk)).append(")\n");
        }
        sb.append(")");
        return sb.toString().replace(",\n)", "\n)");
    }

    /** 标识符白名单 + 保留字校验，阻断通过实体/属性编码注入 DDL。 */
    private static String safeIdentifier(String code, String label) {
        if (code == null || !Db.isIdentifier(code)) {
            throw new BizException(ErrorCode.PARAM_INVALID, label + "不合法（仅允许字母/数字/下划线）：" + code);
        }
        if (RESERVED.contains(code.toLowerCase())) {
            throw new BizException(ErrorCode.PARAM_INVALID, label + "不能使用 SQL 保留字：" + code);
        }
        return code;
    }

    private String sqlType(String type, String len) {
        if (type == null) return "VARCHAR(64)";
        return switch (type.toUpperCase()) {
            case "STRING", "VARCHAR" -> "VARCHAR(" + (len != null && len.matches("\\d{1,4}") ? len : "64") + ")";
            case "DECIMAL" -> "DECIMAL(" + (len != null && len.matches("[\\d,]{1,9}") ? len : "12,2") + ")";
            case "INT", "INTEGER" -> "INT";
            case "BIGINT" -> "BIGINT";
            case "DATE" -> "DATE";
            case "TIMESTAMP" -> "TIMESTAMP";
            default -> "VARCHAR(64)";
        };
    }

    private String snapshot(long modelId) {
        return JsonUtil.toJson(M.of("modelId", modelId,
                "entities", db.list("SELECT code,name,entity_type FROM mdl_entity WHERE model_id=?", modelId),
                "attributeCount", db.count("SELECT COUNT(*) FROM mdl_attribute a JOIN mdl_entity e ON e.id=a.entity_id WHERE e.model_id=?", modelId)));
    }

    @GetMapping("/templates")
    @RequirePerm("model:template:view")
    public Result<Object> templates() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> p : db.list("SELECT id,package_code,package_name,industry_code,version FROM ind_package")) {
            out.add(M.of("code", p.get("packageCode"), "name", p.get("packageName"),
                    "industry", p.get("industryCode"), "version", p.get("version"),
                    // 修正：原实现统计的是全库 MODEL_TEMPLATE 总数（每个包显示同一个数）
                    "entityCount", db.count("SELECT COUNT(*) FROM ind_package_item WHERE package_id=? AND item_type='MODEL_TEMPLATE'",
                            p.get("id"))));
        }
        return Result.ok(M.of("records", out));
    }

    @GetMapping("/stats/overview")
    @RequirePerm("model:overview:view")
    public Result<Object> stats() {
        return Result.ok(M.of(
                "modelCount", db.count("SELECT COUNT(*) FROM mdl_model"),
                "entityCount", db.count("SELECT COUNT(*) FROM mdl_entity"),
                "attributeCount", db.count("SELECT COUNT(*) FROM mdl_attribute"),
                "publishedCount", db.count("SELECT COUNT(*) FROM mdl_model WHERE status='PUBLISHED'"),
                "pendingApproval", db.count("SELECT COUNT(*) FROM mdl_publish_flow WHERE status='PENDING'"),
                "byType", db.list("SELECT model_type, COUNT(*) AS cnt FROM mdl_model GROUP BY model_type"),
                "byLayer", db.list("SELECT layer, COUNT(*) AS cnt FROM mdl_model GROUP BY layer")));
    }
}
