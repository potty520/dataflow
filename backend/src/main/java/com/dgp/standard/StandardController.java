package com.dgp.standard;

import com.dgp.common.*;
import com.dgp.security.AuthContext;
import com.dgp.security.RequirePerm;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** dgp-standard:8084：数据元、标准、映射（含自动推荐）、落地检查。 */
@RestController
@RequestMapping("/api/v1/standard")
public class StandardController {

    private final Db db;

    public StandardController(Db db) {
        this.db = db;
    }

    // ================= 分类 =================
    @GetMapping("/categories/tree")
    @RequirePerm("standard:category:view")
    public Result<Object> categories() {
        List<String> cats = new ArrayList<>();
        for (Map<String, Object> r : db.list("SELECT DISTINCT category FROM std_element WHERE category IS NOT NULL ORDER BY category")) {
            cats.add(String.valueOf(r.get("category")));
        }
        List<Map<String, Object>> tree = new ArrayList<>();
        for (String c : cats) {
            tree.add(M.of("code", c, "name", c, "children",
                    db.list("SELECT code,name,cn_name,data_type FROM std_element WHERE category=? ORDER BY code", c)));
        }
        return Result.ok(M.of("tree", tree));
    }

    @PostMapping("/categories")
    @RequirePerm("standard:category:edit")
    public Result<Object> createCategory(@RequestBody Map<String, Object> b) {
        return Result.ok(M.of("created", true, "name", b.get("name")));
    }

    // ================= 数据元 =================
    @GetMapping("/elements")
    @RequirePerm("standard:element:view")
    public Result<Object> elements(@RequestParam(required = false) String keyword,
                                   @RequestParam(required = false) String category,
                                   @RequestParam(required = false) String status,
                                   @RequestParam(defaultValue = "1") int pageNum,
                                   @RequestParam(defaultValue = "20") int pageSize) {
        StringBuilder where = new StringBuilder(" WHERE 1=1");
        List<Object> args = new ArrayList<>();
        if (!Req.blank(keyword)) {
            where.append(" AND (code LIKE ? OR name LIKE ? OR cn_name LIKE ?)");
            for (int i = 0; i < 3; i++) args.add("%" + keyword + "%");
        }
        if (!Req.blank(category)) {
            where.append(" AND category=?");
            args.add(category);
        }
        if (!Req.blank(status)) {
            where.append(" AND status=?");
            args.add(status);
        }
        String base = "SELECT e.*, (SELECT COUNT(*) FROM std_field_mapping m WHERE m.std_element_code=e.code) AS mapped_columns "
                + "FROM std_element e" + where;
        return Result.ok(db.page(base, "e.code", pageNum, pageSize, args.toArray()).toMap());
    }

    @PostMapping("/elements")
    @RequirePerm("standard:element:create")
    public Result<Object> createElement(@RequestBody Map<String, Object> b) {
        String code = Req.str(b.get("code"));
        if (Req.blank(code)) throw new BizException(ErrorCode.PARAM_INVALID, "数据元编码不能为空");
        if (db.one("SELECT id FROM std_element WHERE code=?", code) != null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "数据元编码已存在：" + code);
        }
        long id = db.insert("INSERT INTO std_element(tenant_id,code,name,cn_name,category,data_type,data_len,fmt,business_desc,rule_desc,"
                        + "source,industry,status,version,owner,create_time) VALUES(1,?,?,?,?,?,?,?,?,?,?,?,'DRAFT','1.0.0',?,?)",
                b.get("code"), b.get("name"), b.get("cnName"), b.get("category"), b.get("dataType"), b.get("dataLen"),
                b.get("fmt"), b.get("businessDesc"), b.get("ruleDesc"), b.get("source"),
                b.getOrDefault("industry", "GOV"), AuthContext.username(), LocalDateTime.now());
        return Result.ok(M.of("id", id));
    }

    @PutMapping("/elements/{id}")
    @RequirePerm("standard:element:edit")
    public Result<Object> updateElement(@PathVariable long id, @RequestBody Map<String, Object> b) {
        db.update("UPDATE std_element SET cn_name=?,data_type=?,data_len=?,fmt=?,business_desc=?,rule_desc=? WHERE id=?",
                b.get("cnName"), b.get("dataType"), b.get("dataLen"), b.get("fmt"), b.get("businessDesc"), b.get("ruleDesc"), id);
        return Result.ok(M.of("id", id));
    }

    @PostMapping("/elements/{id}/publish")
    @RequirePerm("standard:element:publish")
    public Result<Object> publishElement(@PathVariable long id) {
        db.update("UPDATE std_element SET status='PUBLISHED' WHERE id=?", id);
        return Result.ok(M.of("id", id, "status", "PUBLISHED"));
    }

    @PostMapping("/elements/{id}/deprecate")
    @RequirePerm("standard:element:publish")
    public Result<Object> deprecateElement(@PathVariable long id) {
        long mapped = db.count("SELECT COUNT(*) FROM std_field_mapping WHERE std_element_code=(SELECT code FROM std_element WHERE id=?)", id);
        if (mapped > 0) throw new BizException(ErrorCode.MAPPING_CONFLICT, "该数据元已被 " + mapped + " 个字段映射，禁止废弃");
        db.update("UPDATE std_element SET status='DEPRECATED' WHERE id=?", id);
        return Result.ok(M.of("id", id, "status", "DEPRECATED"));
    }

    @GetMapping("/standards")
    @RequirePerm("standard:standard:view")
    public Result<Object> standards() {
        List<Map<String, Object>> list = db.list("SELECT * FROM std_standard ORDER BY code");
        for (Map<String, Object> s : list) {
            s.put("elementCount", s.get("elementCodes") == null ? 0 : String.valueOf(s.get("elementCodes")).split(",").length);
        }
        return Result.ok(M.of("records", list, "total", list.size()));
    }

    @PostMapping("/standards")
    @RequirePerm("standard:standard:create")
    public Result<Object> createStandard(@RequestBody Map<String, Object> b) {
        long id = db.insert("INSERT INTO std_standard(tenant_id,code,name,cn_name,category,industry,source,status,version,"
                        + "element_codes,description,create_time) VALUES(1,?,?,?,?,?,?,'DRAFT','1.0.0',?,?,?)",
                b.get("code"), b.get("name"), b.get("cnName"), b.get("category"), b.getOrDefault("industry", "GOV"),
                b.get("source"), b.get("elementCodes"), b.get("description"), LocalDateTime.now());
        return Result.ok(M.of("id", id));
    }

    // ================= 映射 =================
    @GetMapping("/mappings")
    @RequirePerm("standard:mapping:view")
    public Result<Object> mappings(@RequestParam(required = false) String tableName,
                                   @RequestParam(required = false) String status,
                                   @RequestParam(defaultValue = "1") int pageNum,
                                   @RequestParam(defaultValue = "20") int pageSize) {
        StringBuilder where = new StringBuilder(" WHERE 1=1");
        List<Object> args = new ArrayList<>();
        if (!Req.blank(tableName)) {
            where.append(" AND m.table_name=?");
            args.add(tableName);
        }
        if (!Req.blank(status)) {
            where.append(" AND m.status=?");
            args.add(status);
        }
        String base = "SELECT m.*, e.cn_name AS element_cn_name, c.cn_name AS column_cn_name FROM std_field_mapping m "
                + "LEFT JOIN std_element e ON e.code=m.std_element_code LEFT JOIN md_column c ON c.id=m.column_id" + where;
        return Result.ok(db.page(base, "m.table_name, m.column_name", pageNum, pageSize, args.toArray()).toMap());
    }

    /** 自动映射推荐：字段名精确匹配 + 中文名相似度 + 类型一致性。 */
    @GetMapping("/mappings/recommend")
    @RequirePerm("standard:mapping:recommend")
    public Result<Object> recommend(@RequestParam(required = false) Long tableId,
                                    @RequestParam(defaultValue = "0.6") double minScore) {
        List<Map<String, Object>> elements = db.list("SELECT * FROM std_element WHERE status='PUBLISHED'");
        List<Map<String, Object>> columns = tableId == null
                ? db.list("SELECT c.*, t.name AS tbl FROM md_column c JOIN md_table t ON t.id=c.table_id WHERE c.std_code IS NULL")
                : db.list("SELECT c.*, t.name AS tbl FROM md_column c JOIN md_table t ON t.id=c.table_id WHERE c.table_id=? AND c.std_code IS NULL", tableId);
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> col : columns) {
            List<Map<String, Object>> candidates = new ArrayList<>();
            for (Map<String, Object> e : elements) {
                double score = similarity(col, e);
                if (score >= minScore * 100) {
                    candidates.add(M.of("stdCode", e.get("code"), "elementName", e.get("name"),
                            "elementCnName", e.get("cnName"), "dataType", e.get("dataType"),
                            "score", BigDecimal.valueOf(score).setScale(2, RoundingMode.HALF_UP)));
                }
            }
            candidates.sort((a, b) -> {
                Double sa = Db.num(a.get("score"));
                Double sb = Db.num(b.get("score"));
                return Double.compare(sb == null ? 0 : sb, sa == null ? 0 : sa);
            });
            if (!candidates.isEmpty()) {
                out.add(M.of("columnId", col.get("id"), "tableName", col.get("tbl"), "columnName", col.get("name"),
                        "columnCnName", col.get("cnName"), "dataType", col.get("dataType"),
                        "recommend", candidates.subList(0, Math.min(3, candidates.size()))));
            }
        }
        return Result.ok(M.of("total", out.size(), "records", out));
    }

    private double similarity(Map<String, Object> col, Map<String, Object> e) {
        double score = 0;
        String cn = String.valueOf(col.get("name")).toLowerCase();
        String en = String.valueOf(e.get("name")).toLowerCase();
        if (cn.equals(en)) score += 60;
        else if (cn.contains(en) || en.contains(cn)) score += 38;
        String colCn = col.get("cnName") == null ? "" : String.valueOf(col.get("cnName"));
        String eCn = e.get("cnName") == null ? "" : String.valueOf(e.get("cnName"));
        if (!colCn.isEmpty() && colCn.equals(eCn)) score += 32;
        else if (!colCn.isEmpty() && (colCn.contains(eCn) || eCn.contains(colCn))) score += 20;
        if (String.valueOf(col.get("dataType")).equalsIgnoreCase(String.valueOf(e.get("dataType")))) score += 8;
        return Math.min(100, score);
    }

    @PostMapping("/mappings/batch")
    @RequirePerm("standard:mapping:batch")
    @Transactional
    public Result<Object> batchMapping(@RequestBody Map<String, Object> body) {
        Object itemsObj = body.get("items");
        if (!(itemsObj instanceof List<?> items)) throw new BizException(ErrorCode.PARAM_INVALID, "items 必须为数组");
        int ok = 0;
        for (Object o : items) {
            if (!(o instanceof Map)) continue;
            Map<String, Object> it = JsonUtil.mapper().convertValue(o,
                    new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {
                    });
            Object columnId = it.get("columnId");
            Object stdCode = it.get("stdCode");
            if (columnId == null) continue;
            Map<String, Object> col = db.one("SELECT c.*, t.name AS tbl FROM md_column c JOIN md_table t ON t.id=c.table_id WHERE c.id=?", columnId);
            if (col == null) continue;
            if (stdCode != null && db.one("SELECT id FROM std_element WHERE code=?", stdCode) == null) {
                throw new BizException(ErrorCode.MAPPING_CONFLICT, "数据元不存在：" + stdCode);
            }
            db.update("DELETE FROM std_field_mapping WHERE column_id=?", columnId);
            db.insert("INSERT INTO std_field_mapping(tenant_id,table_id,table_name,column_id,column_name,std_element_code,"
                            + "match_type,score,status,create_time) VALUES(1,?,?,?,?,?,?,?,'CONFIRMED',?)",
                    col.get("tableId"), col.get("tbl"), columnId, col.get("name"), stdCode,
                    it.getOrDefault("matchType", "MANUAL"), it.getOrDefault("score", 100), LocalDateTime.now());
            db.update("UPDATE md_column SET std_code=? WHERE id=?", stdCode, columnId);
            ok++;
        }
        return Result.ok(M.of("mapped", ok, "total", items.size()));
    }

    // ================= 落地检查 =================
    @PostMapping("/compliance/check")
    @RequirePerm("standard:compliance:check")
    public Result<Object> complianceCheck(@RequestBody(required = false) Map<String, Object> b) {
        String scope = b == null ? null : Req.str(b.get("tableName"));
        boolean scoped = !Req.blank(scope);
        String sql = "SELECT c.id, c.name, c.cn_name, c.std_code, t.name AS tbl FROM md_column c "
                + "JOIN md_table t ON t.id=c.table_id" + (scoped ? " WHERE t.name=?" : " ORDER BY t.name, c.column_order");
        List<Map<String, Object>> cols = scoped ? db.list(sql, scope) : db.list(sql);
        List<Map<String, Object>> violations = new ArrayList<>();
        int compliant = 0;
        for (Map<String, Object> c : cols) {
            String std = c.get("stdCode") == null ? null : String.valueOf(c.get("stdCode"));
            if (std == null || db.one("SELECT id FROM std_element WHERE code=? AND status='PUBLISHED'", std) == null) {
                violations.add(M.of("tableName", c.get("tbl"), "columnName", c.get("name"), "columnCnName", c.get("cnName"),
                        "reason", std == null ? "未映射数据标准" : "映射的数据元不存在或已废弃"));
            } else {
                compliant++;
            }
        }
        BigDecimal rate = cols.isEmpty() ? BigDecimal.ZERO
                : BigDecimal.valueOf(compliant * 100d / cols.size()).setScale(2, RoundingMode.HALF_UP);
        db.insert("INSERT INTO std_compliance_check(check_time,scope,total_cols,compliant_cols,compliant_rate,detail,operator) "
                + "VALUES(?,?,?,?,?,?,?)", LocalDateTime.now(), scoped ? scope : "全域", cols.size(), compliant, rate,
                JsonUtil.toJson(violations), AuthContext.username());
        return Result.ok(M.of("scope", scoped ? scope : "全域", "totalColumns", cols.size(), "compliantColumns", compliant,
                "compliantRate", rate, "violations", violations, "pass", rate.doubleValue() >= 80));
    }

    @GetMapping("/compliance/summary")
    @RequirePerm("standard:compliance:view")
    public Result<Object> complianceSummary() {
        List<Map<String, Object>> byTable = db.list(
                "SELECT t.name AS table_name, t.cn_name, COUNT(c.id) AS total_cols, "
                        + "SUM(CASE WHEN c.std_code IS NOT NULL THEN 1 ELSE 0 END) AS mapped_cols, "
                        + "CASE WHEN COUNT(c.id)=0 THEN 0 ELSE ROUND(SUM(CASE WHEN c.std_code IS NOT NULL THEN 1 ELSE 0 END)*100.0/COUNT(c.id),2) END AS rate "
                        + "FROM md_table t LEFT JOIN md_column c ON c.table_id=t.id GROUP BY t.name, t.cn_name ORDER BY rate ASC");
        List<Map<String, Object>> history = db.list("SELECT * FROM std_compliance_check ORDER BY id DESC LIMIT 10");
        long total = db.count("SELECT COUNT(*) FROM md_column");
        long mapped = db.count("SELECT COUNT(*) FROM md_column WHERE std_code IS NOT NULL");
        return Result.ok(M.of("totalColumns", total, "mappedColumns", mapped,
                "overallRate", total == 0 ? 0 : BigDecimal.valueOf(mapped * 100d / total).setScale(2, RoundingMode.HALF_UP),
                "target", 80, "byTable", byTable, "history", history,
                "recommendations", List.of(
                        "对客户域 phone/id_card 强制执行 DE_MOBILE、DE_ID_CARD 映射",
                        "交易域 amount 字段统一引用 DE_AMOUNT 并校验精度",
                        "新建表须先选择标准数据元，方可提交建模审批")));
    }

    @PostMapping("/imports")
    @RequirePerm("standard:import:execute")
    public Result<Object> importElements(@RequestBody Map<String, Object> body) {
        Object rowsObj = body.get("rows");
        if (!(rowsObj instanceof List<?> rows)) throw new BizException(ErrorCode.PARAM_INVALID, "rows 必须为数组");
        int ok = 0;
        for (Object o : rows) {
            if (!(o instanceof Map)) continue;
            Map<String, Object> r = JsonUtil.mapper().convertValue(o,
                    new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {
                    });
            if (r.get("code") == null || db.one("SELECT id FROM std_element WHERE code=?", r.get("code")) != null) continue;
            db.insert("INSERT INTO std_element(tenant_id,code,name,cn_name,category,data_type,data_len,fmt,business_desc,rule_desc,"
                            + "source,industry,status,version,owner,create_time) VALUES(1,?,?,?,?,?,?,?,?,?,?,?,'PUBLISHED','1.0.0',?,?)",
                    r.get("code"), r.get("name"), r.get("cnName"), r.getOrDefault("category", "公共"), r.get("dataType"),
                    r.get("dataLen"), r.get("fmt"), r.get("businessDesc"), r.get("ruleDesc"), r.get("source"),
                    r.getOrDefault("industry", "GOV"), AuthContext.username(), LocalDateTime.now());
            ok++;
        }
        return Result.ok(M.of("imported", ok));
    }

    @GetMapping("/stats/overview")
    @RequirePerm("standard:overview:view")
    public Result<Object> stats() {
        return Result.ok(M.of("elementCount", db.count("SELECT COUNT(*) FROM std_element"),
                "publishedCount", db.count("SELECT COUNT(*) FROM std_element WHERE status='PUBLISHED'"),
                "standardCount", db.count("SELECT COUNT(*) FROM std_standard"),
                "mappingCount", db.count("SELECT COUNT(*) FROM std_field_mapping"),
                "byCategory", db.list("SELECT category, COUNT(*) AS cnt FROM std_element GROUP BY category"),
                "bySource", db.list("SELECT source, COUNT(*) AS cnt FROM std_element GROUP BY source")));
    }
}
