package com.dgp.metadata.service;

import com.dgp.common.BizException;
import com.dgp.common.Db;
import com.dgp.common.ErrorCode;
import com.dgp.common.M;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 血缘服务：表级 / 字段级 / 加工级，支持向上向下追溯与 SQL 解析。
 *
 * <p>SQL 解析为轻量实现（正则 + INSERT/SELECT 子句识别），替代 Calcite/Druid AST；
 * 输出结构与 01 文档 §10.2 一致，便于后续替换为解析引擎。</p>
 */
@Service
public class LineageService {

    private final Db db;

    public LineageService(Db db) {
        this.db = db;
    }

    public Map<String, Object> graph(String objectId, String granularity, String direction, int depth) {
        if (objectId == null || objectId.isBlank()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "objectId 不能为空");
        }
        String start = objectId;
        Set<String> nodes = new LinkedHashSet<>();
        nodes.add(start);
        Set<String> frontier = new HashSet<>();
        frontier.add(start);
        Set<Long> edgeIds = new LinkedHashSet<>();
        List<Map<String, Object>> edges = new ArrayList<>();
        boolean up = "upstream".equalsIgnoreCase(direction) || "both".equalsIgnoreCase(direction);
        boolean down = "downstream".equalsIgnoreCase(direction) || "both".equalsIgnoreCase(direction) || direction == null;

        for (int i = 0; i < Math.max(1, Math.min(depth, 6)); i++) {
            Set<String> next = new HashSet<>();
            for (String node : frontier) {
                List<Map<String, Object>> found = new ArrayList<>();
                if (up) {
                    found.addAll(db.list("SELECT * FROM md_lineage_edge WHERE dst_table=? AND (?='TABLE' OR granularity='COLUMN')",
                            node, "COLUMN".equalsIgnoreCase(granularity) ? "COLUMN" : "TABLE"));
                }
                if (down) {
                    found.addAll(db.list("SELECT * FROM md_lineage_edge WHERE src_table=? AND (?='TABLE' OR granularity='COLUMN')",
                            node, "COLUMN".equalsIgnoreCase(granularity) ? "COLUMN" : "TABLE"));
                }
                for (Map<String, Object> e : found) {
                    edgeIds.add(((Number) e.get("id")).longValue());
                    String other = String.valueOf(e.get("srcTable")).equals(node)
                            ? String.valueOf(e.get("dstTable")) : String.valueOf(e.get("srcTable"));
                    if (!nodes.contains(other)) {
                        nodes.add(other);
                        next.add(other);
                    }
                }
            }
            frontier = next;
            if (frontier.isEmpty()) break;
        }

        List<Map<String, Object>> nodesOut = new ArrayList<>();
        for (String n : nodes) {
            Map<String, Object> t = db.one("SELECT name,cn_name,layer,domain,obj_type,quality_score,row_count,owner FROM md_table WHERE name=?", n);
            Map<String, Object> node = new LinkedHashMap<>();
            node.put("id", n);
            node.put("name", n);
            node.put("cnName", t == null ? n : t.get("cnName"));
            node.put("layer", t == null ? "EXT" : t.get("layer"));
            node.put("domain", t == null ? "外部" : t.get("domain"));
            node.put("type", t == null ? "EXTERNAL" : t.get("objType"));
            node.put("qualityScore", t == null ? null : t.get("qualityScore"));
            node.put("rowCount", t == null ? null : t.get("rowCount"));
            node.put("owner", t == null ? null : t.get("owner"));
            node.put("isRoot", n.equals(start));
            nodesOut.add(node);
        }
        for (Long id : edgeIds) {
            Map<String, Object> e = db.one("SELECT * FROM md_lineage_edge WHERE id=?", id);
            if (e == null) continue;
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("id", e.get("id"));
            out.put("source", e.get("srcTable"));
            out.put("target", e.get("dstTable"));
            out.put("srcColumn", e.get("srcColumn"));
            out.put("dstColumn", e.get("dstColumn"));
            out.put("job", e.get("jobName"));
            out.put("transformType", e.get("transformType"));
            out.put("transformExpr", e.get("transformExpr"));
            out.put("granularity", e.get("granularity"));
            edges.add(out);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("nodes", nodesOut);
        out.put("edges", edges);
        out.put("stats", M.of("nodeCount", nodesOut.size(), "edgeCount", edges.size(),
                "granularity", granularity == null ? "TABLE" : granularity, "direction", direction == null ? "downstream" : direction));
        return out;
    }

    /** 变更影响分析：下游表、作业、质量规则与 API。 */
    public Map<String, Object> impact(String objectId, int depth) {
        Map<String, Object> g = graph(objectId, "TABLE", "downstream", depth);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> nodes = (List<Map<String, Object>>) g.get("nodes");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> edges = (List<Map<String, Object>>) g.get("edges");
        List<String> tables = new ArrayList<>();
        for (Map<String, Object> n : nodes) {
            if (!Boolean.TRUE.equals(n.get("isRoot"))) tables.add(String.valueOf(n.get("name")));
        }
        Set<String> jobs = new LinkedHashSet<>();
        for (Map<String, Object> e : edges) {
            if (e.get("job") != null) jobs.add(String.valueOf(e.get("job")));
        }
        List<Map<String, Object>> rules = tables.isEmpty() ? List.of()
                : db.list("SELECT id,name,table_name FROM qa_rule WHERE table_name IN (" + placeholders(tables.size()) + ")",
                tables.toArray());
        List<Map<String, Object>> apis = db.list("SELECT id,code,name FROM svc_api WHERE sql_text LIKE ?", "%" + objectId + "%");
        List<Map<String, Object>> catalogs = tables.isEmpty() ? List.of()
                : db.list("SELECT id,asset_name,table_name FROM ast_catalog WHERE table_name IN ("
                + placeholders(tables.size()) + ")", tables.toArray());
        List<Map<String, Object>> out = new ArrayList<>();
        for (String t : tables) {
            out.add(M.of("table", t, "owner", db.scalar("SELECT owner FROM md_table WHERE name=?", t),
                    "qualityScore", db.scalar("SELECT quality_score FROM md_table WHERE name=?", t)));
        }
        return M.of("objectId", objectId, "downstreamTables", out, "tableCount", out.size(),
                "jobs", new ArrayList<>(jobs), "ruleCount", rules.size(), "rules", rules,
                "affectedApis", apis, "catalogCount", catalogs.size(),
                "summary", "影响 " + out.size() + " 张下游表、" + jobs.size() + " 个作业、" + rules.size() + " 条质量规则、"
                        + apis.size() + " 个数据服务");
    }

    private String placeholders(int n) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n; i++) {
            if (i > 0) sb.append(",");
            sb.append("?");
        }
        return sb.toString();
    }

    /** 解析 SQL 血缘并落库（表级 + 字段级）。 */
    public Map<String, Object> parse(String sql, String jobName, boolean persist) {
        String s = sql == null ? "" : sql.replaceAll("\\s+", " ");
        String head = s.trim().toLowerCase();
        // 血缘解析仅对"会读/写表"的语句有意义；DDL 与 DELETE 等无血缘语义，直接拒绝而不是返回空结果
        for (String bad : new String[]{"delete", "drop", "truncate", "create", "alter", "grant", "call", "update"}) {
            if (head.startsWith(bad + " ") || head.equals(bad)) {
                throw new BizException(ErrorCode.LINEAGE_PARSE_FAIL,
                        "血缘解析仅支持 SELECT / INSERT ... SELECT 语句，当前为：" + bad.toUpperCase());
            }
        }
        Matcher insert = Pattern.compile("(?i)\\binsert\\s+(?:into|overwrite)\\s+([\\w.]+)").matcher(s);
        String target = insert.find() ? normalize(insert.group(1)) : null;
        Set<String> sources = new LinkedHashSet<>();
        Matcher from = Pattern.compile("(?i)\\b(?:from|join)\\s+([\\w.]+)").matcher(s);
        while (from.find()) {
            String t = normalize(from.group(1));
            if (!t.equals(target) && !t.equals("select")) sources.add(t);
        }
        if (target == null && s.toUpperCase().startsWith("SELECT")) {
            target = null;
        }
        if (sources.isEmpty() && target == null) {
            throw new BizException(ErrorCode.LINEAGE_PARSE_FAIL, "未能从 SQL 中识别出源表或目标表");
        }
        List<Map<String, Object>> columnEdges = new ArrayList<>();
        Matcher sel = Pattern.compile("(?is)select\\s+(.*?)\\s+from\\s").matcher(s);
        if (sel.find()) {
            for (String item : sel.group(1).split(",")) {
                String expr = item.trim();
                Matcher alias = Pattern.compile("(?i)([\\w.]+)\\s+as\\s+([\\w]+)").matcher(expr);
                if (alias.find()) {
                    columnEdges.add(M.of("srcColumn", alias.group(1), "dstColumn", alias.group(2), "transformExpr", expr));
                } else if (expr.matches("[\\w.]+")) {
                    columnEdges.add(M.of("srcColumn", expr, "dstColumn", expr.contains(".") ? expr.substring(expr.indexOf('.') + 1) : expr,
                            "transformExpr", expr));
                }
            }
        }
        if (persist && target != null) {
            for (String src : sources) {
                db.insert("INSERT INTO md_lineage_edge(tenant_id,src_table,dst_table,job_name,transform_type,granularity,create_time) "
                                + "VALUES(1,?,?,?,'SQL_PARSE','TABLE',?)",
                        src, target, jobName == null ? "SQL_PARSE" : jobName, LocalDateTime.now());
                for (Map<String, Object> ce : columnEdges) {
                    db.insert("INSERT INTO md_lineage_edge(tenant_id,src_table,src_column,dst_table,dst_column,job_name,"
                                    + "transform_type,transform_expr,granularity,create_time) VALUES(1,?,?,?,?,?,'SQL_PARSE',?,'COLUMN',?)",
                            src, ce.get("srcColumn"), target, ce.get("dstColumn"),
                            jobName == null ? "SQL_PARSE" : jobName, ce.get("transformExpr"), LocalDateTime.now());
                }
            }
        }
        return M.of("target", target, "sources", new ArrayList<>(sources), "columnMappings", columnEdges,
                "parsed", true, "persisted", persist && target != null,
                "engine", "DGP-SQL-Lineage-Lite 1.0");
    }

    private String normalize(String t) {
        String s = t.replace("`", "").replace("\"", "").trim();
        int dot = s.lastIndexOf('.');
        return dot > 0 ? s.substring(dot + 1) : s;
    }

    public List<Map<String, Object>> upstreamTables(String table) {
        Deque<String> q = new ArrayDeque<>();
        q.add(table);
        Set<String> seen = new LinkedHashSet<>();
        while (!q.isEmpty()) {
            String cur = q.poll();
            for (Map<String, Object> e : db.list("SELECT src_table FROM md_lineage_edge WHERE dst_table=? AND granularity='TABLE'", cur)) {
                String src = String.valueOf(e.get("srcTable"));
                if (seen.add(src)) q.add(src);
            }
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (String s : seen) out.add(M.of("table", s));
        return out;
    }
}
