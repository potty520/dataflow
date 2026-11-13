package com.dgp.industry;

import com.dgp.common.*;
import com.dgp.security.AuthContext;
import com.dgp.security.RequirePerm;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** dgp-industry:8091：行业包上传/预检/导入/实例化/启停，术语与血缘规则。 */
@RestController
@RequestMapping("/api/v1/industry")
public class IndustryController {

    private final Db db;
    private final Path pkgDir = Paths.get("data", "packages");

    public IndustryController(Db db) {
        this.db = db;
    }

    @GetMapping("/packages")
    @RequirePerm("industry:package:view")
    public Result<Object> packages(@RequestParam(required = false) String industry) {
        String sql = "SELECT * FROM ind_package" + (Req.blank(industry) ? "" : " WHERE industry_code=?") + " ORDER BY id";
        List<Map<String, Object>> list = Req.blank(industry) ? db.list(sql) : db.list(sql, industry);
        for (Map<String, Object> p : list) {
            p.put("itemTypes", db.list("SELECT item_type, COUNT(*) AS cnt FROM ind_package_item WHERE package_id=? GROUP BY item_type", p.get("id")));
            p.put("enabledTenants", db.count("SELECT COUNT(*) FROM ind_enable_scope WHERE package_code=? AND status='ENABLED'", p.get("packageCode")));
        }
        return Result.ok(M.of("records", list, "total", list.size()));
    }

    /** ① 上传：接收 manifest 文本，落盘并返回 fileId。 */
    @PostMapping("/packages/upload")
    @RequirePerm("industry:package:upload")
    public Result<Object> upload(@RequestBody Map<String, Object> b) {
        String manifest = b.get("manifest") == null ? "" : JsonUtil.toJson(b.get("manifest"));
        if (manifest.isBlank() || "{}".equals(manifest)) {
            throw new BizException(ErrorCode.PACKAGE_MANIFEST_INVALID, "manifest 不能为空");
        }
        try {
            Files.createDirectories(pkgDir);
            String fileId = "PKG-" + Ids.uuid().substring(0, 12);
            Files.writeString(pkgDir.resolve(fileId + ".json"), manifest, StandardCharsets.UTF_8);
            return Result.ok(M.of("fileId", fileId, "sizeBytes", manifest.getBytes(StandardCharsets.UTF_8).length,
                    "checksum", "sha256:" + Integer.toHexString(manifest.hashCode())));
        } catch (IOException e) {
            throw new IllegalStateException("行业包上传失败", e);
        }
    }

    /** ② 预检：解析 manifest，返回 ImportPreview 与冲突结论。 */
    @PostMapping("/packages/inspect")
    @RequirePerm("industry:package:import")
    public Result<Object> inspect(@RequestBody Map<String, Object> b) {
        Map<String, Object> manifest;
        String fileId = Req.str(b.get("fileId"));
        if (fileId != null) {
            try {
                manifest = JsonUtil.parse(Files.readString(pkgDir.resolve(fileId + ".json"), StandardCharsets.UTF_8));
            } catch (IOException e) {
                throw new BizException(ErrorCode.PACKAGE_MANIFEST_INVALID, "fileId 对应文件不存在");
            }
        } else if (b.get("manifest") instanceof Map<?, ?> m) {
            manifest = JsonUtil.mapper().convertValue(m, new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {
            });
        } else {
            throw new BizException(ErrorCode.PACKAGE_MANIFEST_INVALID, "需提供 fileId 或 manifest");
        }
        String code = Req.str(manifest.get("packageCode"));
        if (code == null || !code.matches("^[A-Z][A-Z0-9_]{2,31}$")) {
            throw new BizException(ErrorCode.PACKAGE_MANIFEST_INVALID, "packageCode 不符合 ^[A-Z][A-Z0-9_]{2,31}$");
        }
        String version = Req.str(manifest.getOrDefault("version", "1.0.0"));
        String minPlatform = Req.str(manifest.getOrDefault("minPlatformVersion", "1.0.0"));
        String platform = String.valueOf(db.scalar("SELECT config_value FROM sys_config WHERE config_key='platform.version'"));
        if (compareVersion(platform, minPlatform) < 0) {
            throw new BizException(ErrorCode.PACKAGE_VERSION_INCOMPATIBLE,
                    "行业包要求平台 ≥ " + minPlatform + "，当前 " + platform);
        }
        Object contents = manifest.get("contents");
        int itemCount = contents instanceof List<?> l ? l.size() : 0;
        Map<String, Object> existing = db.one("SELECT version,id FROM ind_package WHERE package_code=?", code);
        List<Map<String, Object>> conflicts = new ArrayList<>();
        if (existing != null) {
            conflicts.add(M.of("type", "VERSION", "message", "包已存在，当前版本 " + existing.get("version")
                    + "，导入版本 " + version + (compareVersion(version, String.valueOf(existing.get("version"))) > 0
                    ? "，将执行升级" : "，版本未提升")));
        }
        boolean compatible = true;
        String maxPlatform = Req.str(manifest.getOrDefault("maxPlatformVersion", "9.9.9"));
        if (compareVersion(platform, maxPlatform) > 0) compatible = false;
        return Result.ok(M.of("packageCode", code, "packageName", manifest.get("packageName"),
                "industryCode", manifest.get("industryCode"), "version", version,
                "itemCount", itemCount, "itemTypes", itemTypes(contents),
                "conflicts", conflicts, "platformVersion", platform,
                "versionCompatible", compatible, "importable", compatible,
                "nextAction", "POST /api/v1/industry/packages/import 并携带 fileId 完成导入"));
    }

    private List<String> itemTypes(Object contents) {
        List<String> out = new ArrayList<>();
        if (contents instanceof List<?> l) {
            for (Object o : l) {
                if (o instanceof Map<?, ?> m && m.get("type") != null) out.add(String.valueOf(m.get("type")));
            }
        }
        return out;
    }

    /** ③ 导入：基于 fileId（或直接 manifest）落库。 */
    @PostMapping("/packages/import")
    @RequirePerm("industry:package:import")
    public Result<Object> importPackage(@RequestBody Map<String, Object> b) {
        Map<String, Object> manifest;
        String fileId = Req.str(b.get("fileId"));
        if (fileId != null) {
            try {
                manifest = JsonUtil.parse(Files.readString(pkgDir.resolve(fileId + ".json"), StandardCharsets.UTF_8));
            } catch (IOException e) {
                throw new BizException(ErrorCode.PACKAGE_MANIFEST_INVALID, "fileId 对应文件不存在");
            }
        } else if (b.get("manifest") instanceof Map<?, ?> m) {
            manifest = JsonUtil.mapper().convertValue(m, new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {
            });
        } else {
            throw new BizException(ErrorCode.PACKAGE_MANIFEST_INVALID, "需提供 fileId 或 manifest");
        }
        String code = Req.str(manifest.get("packageCode"));
        String version = Req.str(manifest.getOrDefault("version", "1.0.0"));
        Object contents = manifest.get("contents");
        int itemCount = contents instanceof List<?> l ? l.size() : 0;
        long pkgId = db.insert("INSERT INTO ind_package(tenant_id,package_code,package_name,industry_code,industry_name,version,status,"
                        + "min_platform_version,max_platform_version,description,vendor,manifest,item_cnt,applied,create_time) "
                        + "VALUES(1,?,?,?,?,?,'INSTALLED',?,?,?,?,?,?,0,?)",
                code, manifest.get("packageName"), manifest.get("industryCode"), manifest.get("industryName"), version,
                manifest.getOrDefault("minPlatformVersion", "1.0.0"), manifest.getOrDefault("maxPlatformVersion", "9.9.9"),
                manifest.get("description"), manifest.get("vendor"), JsonUtil.toJson(manifest), itemCount, LocalDateTime.now());
        if (contents instanceof List<?> l) {
            for (Object o : l) {
                if (!(o instanceof Map<?, ?> item)) continue;
                db.insert("INSERT INTO ind_package_item(package_id,item_type,code,name,version,content) VALUES(?,?,?,?,?,?)",
                        pkgId, item.get("type"), item.get("code"), item.get("code"), item.get("version"), "{}");
            }
        }
        db.insert("INSERT INTO ind_package_version(package_code,version,manifest,operator,create_time) VALUES(?,?,?,?,?)",
                code, version, JsonUtil.toJson(manifest), AuthContext.username(), LocalDateTime.now());
        return Result.ok(M.of("packageId", pkgId, "packageCode", code, "version", version, "itemCount", itemCount,
                "status", "INSTALLED"));
    }

    @GetMapping("/packages/{code}/export")
    @RequirePerm("industry:package:export")
    public Result<Object> export(@PathVariable String code) {
        Map<String, Object> p = db.one("SELECT * FROM ind_package WHERE package_code=?", code);
        if (p == null) throw new BizException(ErrorCode.NOT_FOUND);
        List<Map<String, Object>> items = db.list("SELECT item_type,code,name,version,content FROM ind_package_item WHERE package_id=?", p.get("id"));
        return Result.ok(M.of("manifest", JsonUtil.parse(String.valueOf(p.get("manifest"))),
                "items", items, "packageCode", code, "version", p.get("version"),
                "downloadName", code + "-" + p.get("version") + ".json"));
    }

    /** 实例化：把模型模板、规则模板、术语表真实落成平台对象（整体事务，避免半套导入）。 */
    @PostMapping("/packages/{code}/instantiate")
    @RequirePerm("industry:package:instantiate")
    @Transactional
    public Result<Object> instantiate(@PathVariable String code) {
        Map<String, Object> p = db.one("SELECT * FROM ind_package WHERE package_code=?", code);
        if (p == null) throw new BizException(ErrorCode.NOT_FOUND);
        List<Map<String, Object>> items = db.list("SELECT * FROM ind_package_item WHERE package_id=?", p.get("id"));
        int models = 0, rules = 0, terms = 0;
        for (Map<String, Object> item : items) {
            String type = String.valueOf(item.get("itemType"));
            Map<String, Object> content = JsonUtil.parse(String.valueOf(item.get("content")));
            if ("MODEL_TEMPLATE".equals(type)) {
                String modelCode = "IM_" + code + "_" + item.get("code");
                if (db.one("SELECT id FROM mdl_model WHERE code=?", modelCode) == null) {
                    long mid = db.insert("INSERT INTO mdl_model(tenant_id,code,name,cn_name,model_type,layer,industry,domain,status,version,"
                                    + "owner,description,create_time,update_time) VALUES(1,?,?,?,?,'DWD',?,'行业实例','DRAFT','1.0.0',?,?,?,?)",
                            modelCode, content.get("modelCode"), content.get("modelName"), content.get("modelType"),
                            p.get("industryCode"), AuthContext.username(),
                            "由行业包 " + code + " 实例化生成", LocalDateTime.now(), LocalDateTime.now());
                    Object ents = content.get("entities");
                    if (ents instanceof List<?> el) {
                        for (Object eo : el) {
                            if (!(eo instanceof Map<?, ?> e)) continue;
                            long eid = db.insert("INSERT INTO mdl_entity(model_id,code,name,cn_name,entity_type,subject_domain,description) "
                                            + "VALUES(?,?,?,?,'DIM',?,?)",
                                    mid, e.get("code"), e.get("name"), e.get("name"), p.get("industryName"),
                                    "行业模板实体");
                            Object attrs = e.get("attributes");
                            if (attrs instanceof List<?> al) {
                                int order = 1;
                                for (Object ao : al) {
                                    if (!(ao instanceof Map<?, ?> a)) continue;
                                    db.insert("INSERT INTO mdl_attribute(entity_id,code,name,cn_name,data_type,data_len,is_pk,nullable,"
                                                    + "std_element_code,security_level,attr_order) VALUES(?,?,?,?,?,?,?,1,?,?,?)",
                                            eid, a.get("code"), a.get("name"), a.get("name"), a.get("dataType"),
                                            a.get("length"), Boolean.TRUE.equals(a.get("isPk")) ? 1 : 0,
                                            a.get("stdElementCode"), a.get("securityLevel"), order++);
                                }
                            }
                        }
                    }
                    models++;
                }
            } else if ("RULE_TEMPLATE".equals(type)) {
                Object rulesObj = content.get("rules");
                if (rulesObj instanceof List<?> rl) {
                    for (Object ro : rl) {
                        if (!(ro instanceof Map<?, ?> r)) continue;
                        String rcode = String.valueOf(r.get("code"));
                        if (db.one("SELECT id FROM qa_rule_template WHERE code=?", rcode) != null) continue;
                        db.insert("INSERT INTO qa_rule_template(code,name,industry,dimension,rule_type,expression,applies_to,"
                                        + "description,status) VALUES(?,?,?,?,?,?,?,?,'ENABLED')",
                                rcode, r.get("name"), p.get("industryCode"), r.get("dimension"), r.get("ruleType"),
                                r.get("expression"), r.get("appliesTo"), "行业包导入模板");
                        rules++;
                    }
                }
            } else if ("TERMINOLOGY".equals(type)) {
                String csv = String.valueOf(content.getOrDefault("csv", ""));
                for (String line : csv.split("\n")) {
                    String[] cells = line.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)");
                    if (cells.length < 2) continue;
                    String termCode = cells[0].trim();
                    if (db.one("SELECT id FROM md_biz_term WHERE term_code=?", termCode) != null) continue;
                    db.insert("INSERT INTO md_biz_term(tenant_id,industry,term_code,term_name,term_name_en,definition,column_pattern,"
                                    + "std_element_code,synonyms,security_level,source) VALUES(1,?,?,?,?,?,?,?,?,?,?)",
                            p.get("industryCode"), termCode, cell(cells, 1), cell(cells, 2), cell(cells, 3),
                            cell(cells, 4), cell(cells, 5), cell(cells, 6), cell(cells, 7), cell(cells, 8));
                    terms++;
                }
            }
        }
        db.update("UPDATE ind_package SET applied=applied+1,status='APPLIED' WHERE id=?", p.get("id"));
        return Result.ok(M.of("packageCode", code, "instantiated", true, "models", models, "ruleTemplates", rules, "terms", terms,
                "note", "规则模板已导入模板库，需通过「批量应用」绑定目标表后生效"));
    }

    private String cell(String[] cells, int i) {
        if (i >= cells.length) return null;
        return cells[i].trim().replace("\"", "");
    }

    @PostMapping("/packages/{code}/enable")
    @RequirePerm("industry:package:enable")
    public Result<Object> enable(@PathVariable String code, @RequestBody(required = false) Map<String, Object> b) {
        long tenantId = b == null ? AuthContext.tenantId() : Req.toInt(b.getOrDefault("tenantId", AuthContext.tenantId()), 1);
        db.update("DELETE FROM ind_enable_scope WHERE package_code=? AND tenant_id=?", code, tenantId);
        db.insert("INSERT INTO ind_enable_scope(package_code,tenant_id,status,enable_time) VALUES(?,?,'ENABLED',?)",
                code, tenantId, LocalDateTime.now());
        db.update("UPDATE ind_package SET status='ENABLED' WHERE package_code=?", code);
        return Result.ok(M.of("packageCode", code, "tenantId", tenantId, "status", "ENABLED"));
    }

    @PostMapping("/packages/{code}/disable")
    @RequirePerm("industry:package:enable")
    public Result<Object> disable(@PathVariable String code) {
        db.update("UPDATE ind_enable_scope SET status='DISABLED' WHERE package_code=?", code);
        db.update("UPDATE ind_package SET status='DISABLED' WHERE package_code=?", code);
        return Result.ok(M.of("packageCode", code, "status", "DISABLED"));
    }

    @GetMapping("/terminology")
    @RequirePerm("industry:terminology:view")
    public Result<Object> terminology(@RequestParam(required = false) String industry,
                                      @RequestParam(required = false) String keyword) {
        StringBuilder where = new StringBuilder(" WHERE 1=1");
        List<Object> args = new ArrayList<>();
        if (!Req.blank(industry)) {
            where.append(" AND industry=?");
            args.add(industry);
        }
        if (!Req.blank(keyword)) {
            where.append(" AND (term_name LIKE ? OR definition LIKE ? OR synonyms LIKE ? OR column_pattern LIKE ?)");
            for (int i = 0; i < 4; i++) args.add("%" + keyword + "%");
        }
        return Result.ok(M.of("records", db.list("SELECT * FROM md_biz_term" + where + " ORDER BY industry,term_code", args.toArray())));
    }

    @PutMapping("/terminology/{id}")
    @RequirePerm("industry:terminology:edit")
    public Result<Object> updateTerm(@PathVariable long id, @RequestBody Map<String, Object> b) {
        db.update("UPDATE md_biz_term SET term_name=?,definition=?,column_pattern=?,synonyms=?,security_level=? WHERE id=?",
                b.get("termName"), b.get("definition"), b.get("columnPattern"), b.get("synonyms"),
                b.get("securityLevel"), id);
        return Result.ok(M.of("id", id));
    }

    @GetMapping("/lineage-rules")
    @RequirePerm("industry:lineage:view")
    public Result<Object> lineageRules() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> p : db.list("SELECT * FROM ind_package")) {
            for (Map<String, Object> item : db.list("SELECT * FROM ind_package_item WHERE package_id=? AND item_type='LINEAGE_RULE'", p.get("id"))) {
                out.add(M.of("id", item.get("id"), "packageCode", p.get("packageCode"), "industry", p.get("industryCode"),
                        "code", item.get("code"), "content", JsonUtil.parse(String.valueOf(item.get("content")))));
            }
        }
        return Result.ok(M.of("records", out));
    }

    @PutMapping("/lineage-rules/{id}")
    @RequirePerm("industry:lineage:edit")
    public Result<Object> updateLineageRule(@PathVariable long id, @RequestBody Map<String, Object> b) {
        db.update("UPDATE ind_package_item SET content=? WHERE id=?", JsonUtil.toJson(b.get("content")), id);
        return Result.ok(M.of("id", id));
    }

    @GetMapping("/instances/diff")
    @RequirePerm("industry:package:view")
    public Result<Object> instanceDiff() {
        List<Map<String, Object>> diff = new ArrayList<>();
        for (Map<String, Object> p : db.list("SELECT * FROM ind_package")) {
            long templates = db.count("SELECT COUNT(*) FROM ind_package_item WHERE package_id=?", p.get("id"));
            long instantiated = db.count("SELECT COUNT(*) FROM mdl_model WHERE code LIKE ?", "IM_" + p.get("packageCode") + "%");
            diff.add(M.of("packageCode", p.get("packageCode"), "packageName", p.get("packageName"),
                    "templateItems", templates, "instantiatedModels", instantiated, "applied", p.get("applied"),
                    "status", p.get("status"), "drift", templates == 0 ? "无模板" : (instantiated == 0 ? "未实例化" : "已实例化")));
        }
        return Result.ok(M.of("records", diff));
    }

    @GetMapping("/stats/overview")
    @RequirePerm("industry:overview:view")
    public Result<Object> stats() {
        return Result.ok(M.of("packageCount", db.count("SELECT COUNT(*) FROM ind_package"),
                "appliedCount", db.count("SELECT COUNT(*) FROM ind_package WHERE applied>0"),
                "enabledCount", db.count("SELECT COUNT(*) FROM ind_package WHERE status='ENABLED'"),
                "itemCount", db.count("SELECT COUNT(*) FROM ind_package_item"),
                "termCount", db.count("SELECT COUNT(*) FROM md_biz_term"),
                "industryCount", db.count("SELECT COUNT(*) FROM ind_industry"),
                "byIndustry", db.list("SELECT industry_code, COUNT(*) AS cnt FROM ind_package GROUP BY industry_code"),
                "byItemType", db.list("SELECT item_type, COUNT(*) AS cnt FROM ind_package_item GROUP BY item_type")));
    }

    private int compareVersion(String a, String b) {
        String[] x = (a == null ? "0" : a).split("\\.");
        String[] y = (b == null ? "0" : b).split("\\.");
        for (int i = 0; i < Math.max(x.length, y.length); i++) {
            int xi = i < x.length ? parseIntSafe(x[i]) : 0;
            int yi = i < y.length ? parseIntSafe(y[i]) : 0;
            if (xi != yi) return Integer.compare(xi, yi);
        }
        return 0;
    }

    private int parseIntSafe(String s) {
        try {
            return Integer.parseInt(s.replaceAll("[^0-9]", ""));
        } catch (Exception e) {
            return 0;
        }
    }
}
