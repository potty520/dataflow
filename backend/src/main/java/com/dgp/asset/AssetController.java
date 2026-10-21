package com.dgp.asset;

import com.dgp.common.*;
import com.dgp.security.AuthContext;
import com.dgp.security.RequirePerm;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** dgp-asset:8089：资产目录、分类标签、资产地图、运营指标与价值评估。 */
@RestController
@RequestMapping("/api/v1/asset")
public class AssetController {

    private final Db db;

    public AssetController(Db db) {
        this.db = db;
    }

    @GetMapping("/catalog/search")
    @RequirePerm("asset:catalog:view")
    public Result<Object> search(@RequestParam(required = false) String keyword,
                                 @RequestParam(required = false) String category,
                                 @RequestParam(required = false) String domain,
                                 @RequestParam(required = false) String publishStatus,
                                 @RequestParam(defaultValue = "1") int pageNum,
                                 @RequestParam(defaultValue = "20") int pageSize) {
        StringBuilder where = new StringBuilder(" WHERE 1=1");
        List<Object> args = new ArrayList<>();
        if (!Req.blank(keyword)) {
            where.append(" AND (a.asset_name LIKE ? OR a.business_desc LIKE ? OR a.table_name LIKE ?)");
            for (int i = 0; i < 3; i++) args.add("%" + keyword + "%");
        }
        if (!Req.blank(category)) {
            where.append(" AND a.category=?");
            args.add(category);
        }
        if (!Req.blank(domain)) {
            where.append(" AND a.domain=?");
            args.add(domain);
        }
        if (!Req.blank(publishStatus)) {
            where.append(" AND a.publish_status=?");
            args.add(publishStatus);
        }
        String base = "SELECT a.*, t.row_count, t.quality_score, t.update_freq FROM ast_catalog a "
                + "LEFT JOIN md_table t ON t.id=a.table_id" + where;
        PageResult p = db.page(base, "a.value_score DESC", pageNum, pageSize, args.toArray());
        // 修正：原实现为每条结果写一条访问日志（一页 20 条 = 20 次写），既放大量又污染运营指标
        db.insert("INSERT INTO ast_visit_log(visit_time,username,asset_id,asset_name,action,ip) VALUES(?,?,?,?,'SEARCH','-')",
                LocalDateTime.now(), AuthContext.username(), null, truncateKeyword(keyword));
        return Result.ok(p.toMap());
    }

    private static String truncateKeyword(String keyword) {
        if (keyword == null) return null;
        return keyword.length() > 120 ? keyword.substring(0, 120) : keyword;
    }

    @GetMapping("/catalog/{id}")
    @RequirePerm("asset:catalog:view")
    public Result<Object> detail(@PathVariable long id) {
        Map<String, Object> a = db.one("SELECT * FROM ast_catalog WHERE id=?", id);
        if (a == null) throw new BizException(ErrorCode.NOT_FOUND);
        db.update("UPDATE ast_catalog SET view_cnt=view_cnt+1 WHERE id=?", id);
        java.util.Map<String, Object> out = new java.util.LinkedHashMap<>(a);
        out.put("table", db.one("SELECT * FROM md_table WHERE id=?", a.get("tableId")));
        out.put("columns", db.list("SELECT * FROM md_column WHERE table_id=? ORDER BY column_order", a.get("tableId")));
        out.put("quality", db.list("SELECT name,dimension,last_pass_rate,last_failed FROM qa_rule WHERE table_name=?", a.get("tableName")));
        out.put("upstream", db.list("SELECT DISTINCT src_table FROM md_lineage_edge WHERE dst_table=?", a.get("tableName")));
        out.put("downstream", db.list("SELECT DISTINCT dst_table FROM md_lineage_edge WHERE src_table=?", a.get("tableName")));
        // 修正：原实现用 ast_value_score 整行覆盖了数值型 valueScore，前端渲染成 [object Object]
        Map<String, Object> breakdown = db.one("SELECT * FROM ast_value_score WHERE table_name=?", a.get("tableName"));
        out.put("valueBreakdown", breakdown);
        out.put("valueScore", breakdown == null ? a.get("valueScore") : breakdown.get("score"));
        out.put("valueBasis", "价值分 = 质量(45%) + 访问热度(30%) + 服务调用(25%)");
        return Result.ok(out);
    }

    @PostMapping("/catalog")
    @RequirePerm("asset:catalog:create")
    public Result<Object> create(@RequestBody Map<String, Object> b) {
        long id = db.insert("INSERT INTO ast_catalog(tenant_id,table_id,table_name,asset_name,asset_type,category,domain,business_desc,"
                        + "cover_scope,update_cycle,owner,publish_status,create_time) VALUES(1,?,?,?,'TABLE',?,?,?,?,?,?,'DRAFT',?)",
                b.get("tableId"), b.get("tableName"), b.get("assetName"), b.get("category"), b.get("domain"),
                b.get("businessDesc"), b.get("coverScope"), b.get("updateCycle"), AuthContext.username(), LocalDateTime.now());
        return Result.ok(M.of("id", id));
    }

    @PutMapping("/catalog/{id}")
    @RequirePerm("asset:catalog:edit")
    public Result<Object> update(@PathVariable long id, @RequestBody Map<String, Object> b) {
        Map<String, Object> before = db.one("SELECT * FROM ast_catalog WHERE id=?", id);
        if (before == null) throw new BizException(ErrorCode.NOT_FOUND, "资产不存在");
        db.update("UPDATE ast_catalog SET asset_name=?,category=?,domain=?,business_desc=?,cover_scope=?,update_cycle=? WHERE id=?",
                pick(b, before, "assetName"), pick(b, before, "category"), pick(b, before, "domain"),
                pick(b, before, "businessDesc"), pick(b, before, "coverScope"), pick(b, before, "updateCycle"), id);
        return Result.ok(M.of("id", id));
    }

    /** 基于最近两条快照计算日均增量（无快照时返回 null，不编造数字）。 */
    private Double dataGrowthPerDay() {
        List<Map<String, Object>> snaps = db.list("SELECT stat_date, total_size_mb FROM ast_map_snapshot ORDER BY stat_date DESC LIMIT 2");
        if (snaps.size() < 2) return null;
        java.math.BigDecimal a = Db.decimal(snaps.get(0).get("totalSizeMb"));
        java.math.BigDecimal b2 = Db.decimal(snaps.get(1).get("totalSizeMb"));
        if (a == null || b2 == null) return null;
        Object d1 = snaps.get(0).get("statDate");
        Object d2 = snaps.get(1).get("statDate");
        long days = 1;
        if (d1 instanceof java.time.LocalDate x && d2 instanceof java.time.LocalDate y) {
            days = Math.max(1, java.time.temporal.ChronoUnit.DAYS.between(y, x));
        }
        return a.subtract(b2).doubleValue() / days;
    }

    private static Object pick(Map<String, Object> body, Map<String, Object> before, String key) {
        return body.containsKey(key) ? body.get(key) : before.get(key);
    }

    @PostMapping("/catalog/{id}/publish")
    @RequirePerm("asset:catalog:publish")
    public Result<Object> publish(@PathVariable long id) {
        db.update("UPDATE ast_catalog SET publish_status='PUBLISHED' WHERE id=?", id);
        return Result.ok(M.of("id", id, "publishStatus", "PUBLISHED"));
    }

    @GetMapping("/categories/tree")
    @RequirePerm("asset:category:view")
    public Result<Object> categories() {
        List<Map<String, Object>> all = db.list("SELECT * FROM ast_category ORDER BY sort_no, id");
        // 分类下的资产数一次聚合，避免每个节点一次 count
        java.util.Map<String, Long> counts = new java.util.HashMap<>();
        for (Map<String, Object> r : db.list("SELECT category, COUNT(*) AS cnt FROM ast_catalog GROUP BY category")) {
            counts.put(String.valueOf(r.get("category")), Db.longVal(r.get("cnt"), 0L));
        }
        return Result.ok(M.of("tree", buildTree(all, 0L, counts, new java.util.HashSet<>(), 0)));
    }

    private List<Map<String, Object>> buildTree(List<Map<String, Object>> all, long parent,
                                               java.util.Map<String, Long> counts,
                                               java.util.Set<Long> visited, int depth) {
        List<Map<String, Object>> out = new ArrayList<>();
        if (depth > 12) return out; // 防脏数据成环导致无限递归
        for (Map<String, Object> c : all) {
            long pid = Db.longVal(c.get("parentId"), 0L);
            if (pid != parent) continue;
            long id = Db.longVal(c.get("id"), 0L);
            if (!visited.add(id)) continue;
            Map<String, Object> node = new java.util.LinkedHashMap<>(c);
            node.put("children", buildTree(all, id, counts, visited, depth + 1));
            node.put("assetCount", counts.getOrDefault(String.valueOf(c.get("name")), 0L));
            out.add(node);
        }
        return out;
    }

    @PostMapping("/categories")
    @RequirePerm("asset:category:edit")
    public Result<Object> createCategory(@RequestBody Map<String, Object> b) {
        long id = db.insert("INSERT INTO ast_category(parent_id,code,name,sort_no) VALUES(?,?,?,?)",
                b.getOrDefault("parentId", 0), b.get("code"), b.get("name"), b.getOrDefault("sortNo", 10));
        return Result.ok(M.of("id", id));
    }

    @GetMapping("/labels")
    @RequirePerm("asset:label:view")
    public Result<Object> labels() {
        List<Map<String, Object>> list = db.list(
                "SELECT sensitive_level AS label, COUNT(*) AS cnt FROM md_table GROUP BY sensitive_level "
                        + "UNION ALL SELECT domain AS label, COUNT(*) AS cnt FROM md_table GROUP BY domain");
        return Result.ok(M.of("records", list));
    }

    @PostMapping("/labels")
    @RequirePerm("asset:label:edit")
    public Result<Object> addLabel(@RequestBody Map<String, Object> b) {
        db.update("UPDATE md_table SET tags=CONCAT(COALESCE(tags,''),?) WHERE name=?",
                "," + b.get("label"), b.get("tableName"));
        return Result.ok(M.of("labeled", true));
    }

    // ---------------- 资产地图 ----------------
    @GetMapping("/map/overview")
    @RequirePerm("asset:map:view")
    public Result<Object> mapOverview() {
        return com.dgp.common.TtlCache.get("asset:map:" + com.dgp.security.AuthContext.tenantId(), 20_000,
                this::computeMapOverview);
    }

    private Result<Object> computeMapOverview() {
        Map<String, Object> snap = db.one("SELECT * FROM ast_map_snapshot ORDER BY id DESC LIMIT 1");
        List<Map<String, Object>> domains = db.list(
                "SELECT domain, COUNT(*) AS table_cnt, SUM(row_count) AS rows_cnt, ROUND(SUM(data_size_mb),2) AS size_mb, "
                        + "ROUND(AVG(quality_score),2) AS avg_score FROM md_table GROUP BY domain ORDER BY table_cnt DESC");
        List<Map<String, Object>> layers = db.list(
                "SELECT layer, COUNT(*) AS table_cnt, SUM(row_count) AS rows_cnt, ROUND(SUM(data_size_mb),2) AS size_mb "
                        + "FROM md_table GROUP BY layer ORDER BY layer");
        List<Map<String, Object>> freq = db.list("SELECT update_freq, COUNT(*) AS cnt FROM md_table GROUP BY update_freq");
        List<Map<String, Object>> top = db.list(
                "SELECT name,cn_name,row_count,data_size_mb,quality_score,domain FROM md_table ORDER BY data_size_mb DESC LIMIT 10");
        return Result.ok(M.of("totalTables", db.count("SELECT COUNT(*) FROM md_table"),
                "totalRows", db.scalar("SELECT COALESCE(SUM(row_count),0) FROM md_table"),
                "totalSizeMb", db.scalar("SELECT ROUND(COALESCE(SUM(data_size_mb),0),2) FROM md_table"),
                "datasourceCount", db.count("SELECT COUNT(*) FROM md_datasource"),
                "columnCount", db.count("SELECT COUNT(*) FROM md_column"),
                "sensitiveTables", db.count("SELECT COUNT(*) FROM md_table WHERE sensitive_level IN ('L3','L4')"),
                "domains", domains, "layers", layers, "updateFreq", freq, "topTables", top,
                "snapshotTime", snap == null ? null : snap.get("statDate"),
                // 修正：原实现返回硬编码的 4.5MB/天增长
                "dataGrowthPerDayMb", dataGrowthPerDay()));
    }

    @GetMapping("/map/distribution")
    @RequirePerm("asset:map:view")
    public Result<Object> distribution() {
        return Result.ok(M.of(
                "byDomain", db.list("SELECT domain AS name, COUNT(*) AS cnt FROM md_table GROUP BY domain"),
                "byLayer", db.list("SELECT layer AS name, COUNT(*) AS cnt FROM md_table GROUP BY layer"),
                "byQuality", db.list("SELECT CASE WHEN quality_score>=90 THEN '优秀(≥90)' WHEN quality_score>=80 THEN '良好(80-90)' "
                        + "WHEN quality_score>=70 THEN '一般(70-80)' ELSE '待改进(<70)' END AS name, COUNT(*) AS cnt "
                        + "FROM md_table GROUP BY CASE WHEN quality_score>=90 THEN '优秀(≥90)' WHEN quality_score>=80 THEN '良好(80-90)' "
                        + "WHEN quality_score>=70 THEN '一般(70-80)' ELSE '待改进(<70)' END"),
                "bySensitive", db.list("SELECT sensitive_level AS name, COUNT(*) AS cnt FROM md_table GROUP BY sensitive_level"),
                "byFreq", db.list("SELECT update_freq AS name, COUNT(*) AS cnt FROM md_table GROUP BY update_freq")));
    }

    // ---------------- 运营指标 ----------------
    @GetMapping("/metrics")
    @RequirePerm("asset:metrics:view")
    public Result<Object> metrics() {
        return com.dgp.common.TtlCache.get("asset:metrics:" + com.dgp.security.AuthContext.tenantId(), 20_000,
                this::computeMetrics);
    }

    private Result<Object> computeMetrics() {
        Map<String, Object> today = db.one("SELECT * FROM ast_metric_daily ORDER BY stat_date DESC LIMIT 1");
        return Result.ok(M.of("tableCount", db.count("SELECT COUNT(*) FROM md_table"),
                "storageMb", db.scalar("SELECT ROUND(COALESCE(SUM(data_size_mb),0),2) FROM md_table"),
                "totalRows", db.scalar("SELECT COALESCE(SUM(row_count),0) FROM md_table"),
                "accessCount", db.count("SELECT COUNT(*) FROM ast_visit_log"),
                "serviceCalls", db.count("SELECT COUNT(*) FROM svc_call_log"),
                "queryCount", db.count("SELECT COUNT(*) FROM qa_check_result"),
                "activeUsers", db.count("SELECT COUNT(DISTINCT username) FROM ast_visit_log"),
                "favoriteCount", db.count("SELECT COALESCE(SUM(favorite_cnt),0) FROM ast_catalog"),
                "assetValue", today == null ? 0 : today.get("assetValue"),
                "today", today));
    }

    @GetMapping("/metrics/trend")
    @RequirePerm("asset:metrics:view")
    public Result<Object> trend(@RequestParam(defaultValue = "30") int days) {
        return Result.ok(M.of("records", db.list("SELECT * FROM ast_metric_daily WHERE stat_date>=? ORDER BY stat_date",
                LocalDate.now().minusDays(days))));
    }

    @GetMapping("/value-basis")
    @RequirePerm("asset:value:view")
    public Result<Object> valueBasis() {
        List<Map<String, Object>> list = db.list(
                "SELECT v.*, t.cn_name, t.quality_score, t.access_count, t.svc_calls FROM ast_value_score v "
                        + "LEFT JOIN md_table t ON t.name=v.table_name ORDER BY v.score DESC");
        for (Map<String, Object> r : list) {
            r.put("dimensions", JsonUtil.parse(String.valueOf(r.get("dimJson"))));
        }
        return Result.ok(M.of("records", list,
                "formula", "价值分 = 质量(45%) + 访问热度(30%) + 服务调用(25%)",
                "avgScore", db.scalar("SELECT ROUND(AVG(score),2) FROM ast_value_score")));
    }

    @PostMapping("/favorites/{assetId}")
    public Result<Object> favorite(@PathVariable long assetId) {
        db.update("UPDATE ast_catalog SET favorite_cnt=favorite_cnt+1 WHERE id=?", assetId);
        db.insert("INSERT INTO ast_visit_log(visit_time,username,asset_id,asset_name,action,ip) "
                + "SELECT ?,?,id,asset_name,'FAVORITE','-' FROM ast_catalog WHERE id=?", LocalDateTime.now(), AuthContext.username(), assetId);
        return Result.ok(M.of("assetId", assetId, "favorited", true));
    }

    @GetMapping("/stats/overview")
    @RequirePerm("asset:overview:view")
    public Result<Object> stats() {
        return com.dgp.common.TtlCache.get("asset:stats:" + com.dgp.security.AuthContext.tenantId(), 20_000,
                this::computeStats);
    }

    private Result<Object> computeStats() {
        return Result.ok(M.of("catalogCount", db.count("SELECT COUNT(*) FROM ast_catalog"),
                "publishedCount", db.count("SELECT COUNT(*) FROM ast_catalog WHERE publish_status='PUBLISHED'"),
                "avgValueScore", db.scalar("SELECT ROUND(AVG(value_score),2) FROM ast_catalog"),
                "topViewed", db.list("SELECT asset_name,view_cnt,favorite_cnt FROM ast_catalog ORDER BY view_cnt DESC LIMIT 8")));
    }
}
