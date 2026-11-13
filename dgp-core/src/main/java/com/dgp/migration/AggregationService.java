package com.dgp.migration;

import com.dgp.common.BizException;
import com.dgp.common.Db;
import com.dgp.common.ErrorCode;
import com.dgp.common.JsonUtil;
import com.dgp.common.M;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.sql.Connection;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 聚合汇总：把「源表 GROUP BY 聚合」产品化为两种能力。
 *
 * <p>1) 聚合视图迁移向导：根据维度/指标自动在源库建视图，并生成迁移项目把视图迁到目标汇总表；
 * <p>2) 汇总刷新作业：手动或定时刷新汇总表，支持 FULL（清空重写）与 UPSERT
 * （同实例内 INSERT ... ON DUPLICATE KEY UPDATE / ON CONFLICT DO UPDATE）。
 */
@Service
public class AggregationService {

    private static final Logger log = LoggerFactory.getLogger(AggregationService.class);

    private final Db db;
    private final MigrationService migration;
    private final RemoteDsService remoteDs;

    public AggregationService(Db db, MigrationService migration, RemoteDsService remoteDs) {
        this.db = db;
        this.migration = migration;
        this.remoteDs = remoteDs;
    }

    // ------------------------------------------------------------ CRUD
    public List<Map<String, Object>> list(String keyword) {
        String sql = "SELECT a.*, (SELECT COUNT(*) FROM mig_agg_run r WHERE r.agg_id=a.id) AS run_count "
                + "FROM mig_agg_def a";
        List<Map<String, Object>> rows = (keyword == null || keyword.isBlank())
                ? db.list(sql + " ORDER BY a.id DESC")
                : db.list(sql + " WHERE a.code LIKE ? OR a.name LIKE ? OR a.source_table LIKE ? ORDER BY a.id DESC",
                "%" + keyword + "%", "%" + keyword + "%", "%" + keyword + "%");
        for (Map<String, Object> r : rows) {
            r.put("dimList", dims(r));
            r.put("metricList", metrics(r));
        }
        return rows;
    }

    public Map<String, Object> detail(long id) {
        Map<String, Object> a = require(id);
        a.put("dimList", dims(a));
        a.put("metricList", metrics(a));
        a.put("viewSql", viewSql(a));
        a.put("targetDdl", targetDdl(a));
        a.put("runs", db.list("SELECT * FROM mig_agg_run WHERE agg_id=? ORDER BY id DESC LIMIT 20", id));
        if (a.get("projectId") != null) {
            a.put("project", db.one("SELECT * FROM mig_project WHERE id=?", a.get("projectId")));
        }
        return a;
    }

    public Map<String, Object> create(Map<String, Object> b) {
        String code = Db.str(b.get("code"));
        if (code == null || code.isBlank()) throw new BizException(ErrorCode.PARAM_INVALID, "编码不能为空");
        if (db.one("SELECT id FROM mig_agg_def WHERE code=?", code) != null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "聚合定义编码已存在：" + code);
        }
        Agg agg = parse(b, null);
        long id = db.insert("INSERT INTO mig_agg_def(tenant_id,code,name,source_ds_id,target_ds_id,source_table,target_table,"
                        + "dims,metrics,where_clause,refresh_mode,refresh_minutes,status,next_run_time,create_time,update_time) "
                        + "VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                1L, code, agg.name, agg.sourceDsId, agg.targetDsId, agg.sourceTable, agg.targetTable,
                agg.dimsJson, agg.metricsJson, agg.where, agg.refreshMode, agg.refreshMinutes,
                "ENABLED", nextTime(agg.refreshMinutes), LocalDateTime.now(), LocalDateTime.now());
        return M.of("id", id);
    }

    public Map<String, Object> update(long id, Map<String, Object> b) {
        Map<String, Object> before = require(id);
        Agg agg = parse(b, before);
        db.update("UPDATE mig_agg_def SET name=?,source_ds_id=?,target_ds_id=?,source_table=?,target_table=?,"
                        + "dims=?,metrics=?,where_clause=?,refresh_mode=?,refresh_minutes=?,status=?,"
                        + "next_run_time=?,update_time=? WHERE id=?",
                agg.name, agg.sourceDsId, agg.targetDsId, agg.sourceTable, agg.targetTable,
                agg.dimsJson, agg.metricsJson, agg.where, agg.refreshMode, agg.refreshMinutes,
                Db.str(b.containsKey("status") ? b.get("status") : before.get("status")),
                nextTime(agg.refreshMinutes), LocalDateTime.now(), id);
        return M.of("id", id);
    }

    public void delete(long id) {
        db.update("DELETE FROM mig_agg_run WHERE agg_id=?", id);
        db.update("DELETE FROM mig_agg_def WHERE id=?", id);
    }

    // ------------------------------------------------------------ 预览/生成
    public Map<String, Object> preview(long id) {
        Map<String, Object> a = require(id);
        String view = viewName(a);
        return M.of("viewName", view, "viewSql", viewSql(a),
                "viewDdl", "CREATE OR REPLACE VIEW " + Db.identifier(view) + " AS " + viewSql(a),
                "targetTable", a.get("targetTable"), "targetDdl", targetDdl(a),
                "refreshMode", a.get("refreshMode"));
    }

    /** 一键生成：源库视图 + 目标汇总表 + 迁移项目（幂等）。 */
    public Map<String, Object> build(long id) {
        Map<String, Object> a = require(id);
        String view = viewName(a);
        String table = Db.identifier(String.valueOf(a.get("targetTable")));
        String vSql = viewSql(a);

        // 1) 源库建/替换视图（viewSql 只是 SELECT，必须包成 CREATE VIEW）
        String viewDdl = "CREATE OR REPLACE VIEW " + Db.identifier(view) + " AS " + vSql;
        try (RemoteDsService.Conn sc = sourceConn(a)) {
            exec(sc.connection, viewDdl);
        } catch (Exception e) {
            throw new BizException(ErrorCode.SQL_INVALID, "创建源视图失败：" + e.getMessage());
        }

        // 2) 目标汇总表（不存在才建）
        boolean targetExists = !targetColumns(a).isEmpty();
        String ddl = targetDdl(a);
        if (!targetExists) {
            try (RemoteDsService.Conn tc = targetConn(a)) {
                exec(tc.connection, ddl);
            } catch (Exception e) {
                throw new BizException(ErrorCode.SQL_INVALID, "创建目标表失败：" + e.getMessage());
            }
        }

        // 3) 迁移项目 + 映射（视图 -> 汇总表）；已存在同编码项目则复用，避免唯一约束
        Long projectId = a.get("projectId") == null ? null : Db.longVal(a.get("projectId"), 0L);
        Map<String, Object> project = projectId == null ? null : db.one("SELECT * FROM mig_project WHERE id=?", projectId);
        if (project == null) {
            project = db.one("SELECT * FROM mig_project WHERE code=?", "AGG-" + a.get("code"));
        }
        if (project == null) {
            projectId = db.insert("INSERT INTO mig_project(tenant_id,code,name,source_ds_id,target_ds_id,source_table,"
                            + "target_table,mode,replace_target,batch_size,status,owner,create_time,update_time) "
                            + "VALUES(1,?,?,?,?,?,?,'FULL',1,5000,'READY','system',?,?)",
                    "AGG-" + a.get("code"), "聚合汇总：" + a.get("name"),
                    a.get("sourceDsId"), a.get("targetDsId"), view, String.valueOf(a.get("targetTable")),
                    LocalDateTime.now(), LocalDateTime.now());
        } else {
            projectId = Db.longVal(project.get("id"), 0L);
            db.update("UPDATE mig_project SET source_ds_id=?,target_ds_id=?,source_table=?,target_table=?,update_time=? WHERE id=?",
                    a.get("sourceDsId"), a.get("targetDsId"), view, String.valueOf(a.get("targetTable")),
                    LocalDateTime.now(), projectId);
        }
        db.update("UPDATE mig_agg_def SET project_id=?,view_name=? WHERE id=?", projectId, view, id);
        db.update("DELETE FROM mig_mapping WHERE project_id=?", projectId);
        int sort = 0;
        for (String dim : dims(a)) {
            db.insert("INSERT INTO mig_mapping(project_id,source_column,target_column,transform_type,sort_no) "
                    + "VALUES(?,?,?,'DIRECT',?)", projectId, dim, dim, sort++);
        }
        for (Map<String, Object> m : metrics(a)) {
            String alias = String.valueOf(m.get("alias"));
            db.insert("INSERT INTO mig_mapping(project_id,source_column,source_type,target_column,target_type,"
                            + "transform_type,sort_no) VALUES(?,?,?,?,?,'DIRECT',?)",
                    projectId, alias, null, alias, metricType(a, m), sort++);
        }

        // 4) UPSERT 需要维度唯一索引
        boolean uniqueIndex = false;
        if ("UPSERT".equalsIgnoreCase(String.valueOf(a.get("refreshMode")))) {
            uniqueIndex = ensureUniqueIndex(a, table);
        }
        db.update("UPDATE mig_agg_def SET last_status='READY',last_message=? WHERE id=?",
                targetExists ? "目标表已存在，视图与项目已更新" : "已生成视图/目标表/迁移项目", id);
        return M.of("aggId", id, "viewName", view, "viewSql", vSql, "targetTable", table,
                "targetDdl", ddl, "targetCreated", !targetExists, "projectId", projectId,
                "mappingCount", dims(a).size() + metrics(a).size(), "uniqueIndex", uniqueIndex);
    }

    // ------------------------------------------------------------ 刷新
    public Map<String, Object> refresh(long id, String trigger, String operator) {
        Map<String, Object> a = require(id);
        Long projectId = a.get("projectId") == null ? null : Db.longVal(a.get("projectId"), 0L);
        if (projectId == null || db.one("SELECT id FROM mig_project WHERE id=?", projectId) == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "尚未生成视图/迁移项目，请先点“生成”");
        }
        String mode = String.valueOf(a.getOrDefault("refreshMode", "FULL")).toUpperCase(Locale.ROOT);
        long runId = db.insert("INSERT INTO mig_agg_run(agg_id,trigger_type,status,start_time,operator) "
                + "VALUES(?,?,'RUNNING',?,?)", id, trigger, LocalDateTime.now(), operator);
        long rows = 0;
        String status = "DONE";
        String message = "";
        String actualMode = "FULL";
        try {
            if ("UPSERT".equals(mode) && sameInstance(a)) {
                rows = upsert(a);
                actualMode = "UPSERT";
                message = "UPSERT 刷新完成";
            } else {
                Map<String, Object> r = migration.run(projectId, true, 0, operator);
                rows = Db.longVal(r.get("insertedRows"), 0L);
                message = "FULL 刷新完成（清空重写）";
            }
        } catch (Exception e) {
            status = "FAILED";
            message = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            log.warn("聚合刷新失败 agg={}: {}", id, message);
        }
        db.update("UPDATE mig_agg_run SET status=?,end_time=?,rows_count=?,message=? WHERE id=?",
                status, LocalDateTime.now(), rows, cut(message), runId);
        db.update("UPDATE mig_agg_def SET last_run_time=?,last_status=?,last_rows=?,last_message=?,"
                        + "next_run_time=?,update_time=? WHERE id=?",
                LocalDateTime.now(), status, rows, cut(message),
                nextTime(Db.longVal(a.get("refreshMinutes"), 0L)), LocalDateTime.now(), id);
        if ("FAILED".equals(status)) {
            throw new BizException(ErrorCode.SQL_INVALID, "汇总刷新失败：" + message);
        }
        return M.of("aggId", id, "runId", runId, "status", status, "rows", rows,
                "mode", actualMode, "message", message);
    }

    public List<Map<String, Object>> runs(long id) {
        return db.list("SELECT * FROM mig_agg_run WHERE agg_id=? ORDER BY id DESC LIMIT 50", id);
    }

    /** 定时任务调用：处理到期的汇总刷新。 */
    public int refreshDue() {
        List<Map<String, Object>> due = db.list(
                "SELECT id,code FROM mig_agg_def WHERE status='ENABLED' AND refresh_minutes>0 "
                        + "AND next_run_time IS NOT NULL AND next_run_time <= ? LIMIT 5", LocalDateTime.now());
        int n = 0;
        for (Map<String, Object> a : due) {
            long id = Db.longVal(a.get("id"), 0L);
            try {
                refresh(id, "SCHEDULED", "scheduler");
                n++;
            } catch (Exception e) {
                log.warn("定时汇总刷新失败 agg={}: {}", a.get("code"), e.getMessage());
                db.update("UPDATE mig_agg_def SET last_status='FAILED',last_message=?,next_run_time=? WHERE id=?",
                        cut(String.valueOf(e.getMessage())), LocalDateTime.now().plusMinutes(1), id);
            }
        }
        return n;
    }

    // ------------------------------------------------------------ 内部实现
    private Map<String, Object> require(long id) {
        Map<String, Object> a = db.one("SELECT * FROM mig_agg_def WHERE id=?", id);
        if (a == null) throw new BizException(ErrorCode.NOT_FOUND, "聚合定义不存在：" + id);
        return a;
    }

    private String viewSql(Map<String, Object> a) {
        List<String> dims = dims(a);
        List<Map<String, Object>> metrics = metrics(a);
        List<String> exprs = new ArrayList<>();
        for (String d : dims) exprs.add(Db.identifier(d));
        for (Map<String, Object> m : metrics) {
            String fn = String.valueOf(m.get("fn")).toUpperCase(Locale.ROOT);
            String rawCol = m.get("column") == null ? "" : String.valueOf(m.get("column"));
            String col = rawCol.isBlank() || "*".equals(rawCol) ? "*" : Db.identifier(rawCol);
            String alias = Db.identifier(String.valueOf(m.get("alias")));
            if ("COUNT".equals(fn) && "*".equals(col)) exprs.add("COUNT(*) AS " + alias);
            else if ("COUNT_DISTINCT".equals(fn)) exprs.add("COUNT(DISTINCT " + col + ") AS " + alias);
            else exprs.add(fn + "(" + col + ") AS " + alias);
        }
        StringBuilder sql = new StringBuilder("SELECT ").append(String.join(", ", exprs))
                .append(" FROM ").append(Db.identifier(String.valueOf(a.get("sourceTable"))));
        String where = Db.str(a.get("whereClause"));
        if (where != null && !where.isBlank()) {
            assertSafeWhere(where);
            sql.append(" WHERE ").append(where);
        }
        List<String> g = new ArrayList<>();
        for (String d : dims) g.add(Db.identifier(d));
        sql.append(" GROUP BY ").append(String.join(", ", g));
        return sql.toString();
    }

    private String targetDdl(Map<String, Object> a) {
        String table = Db.identifier(String.valueOf(a.get("targetTable")));
        List<String> cols = new ArrayList<>();
        List<Map<String, Object>> srcCols = sourceColumnMap(a);
        for (String dim : dims(a)) {
            String type = ddlType(colType(srcCols, dim), targetDialect(a));
            cols.add(Db.identifier(dim) + " " + type);
        }
        for (Map<String, Object> m : metrics(a)) {
            String alias = Db.identifier(String.valueOf(m.get("alias")));
            String type;
            String fn = String.valueOf(m.get("fn")).toUpperCase(Locale.ROOT);
            if ("COUNT".equals(fn) || "COUNT_DISTINCT".equals(fn)) type = "BIGINT";
            else if ("SUM".equals(fn) || "AVG".equals(fn)) type = pg(a) ? "NUMERIC(18,2)" : "DECIMAL(18,2)";
            else {
                String colType = colType(srcCols, String.valueOf(m.get("column")));
                type = "COUNT".equals(fn) ? "BIGINT" : ddlType(colType, targetDialect(a));
            }
            cols.add(alias + " " + type);
        }
        return "CREATE TABLE IF NOT EXISTS " + table + " (" + String.join(", ", cols) + ")";
    }

    private List<Map<String, Object>> sourceColumnMap(Map<String, Object> a) {
        try {
            return columnsOf(a, String.valueOf(a.get("sourceTable")), true);
        } catch (Exception e) {
            return List.of();
        }
    }

    private List<Map<String, Object>> targetColumns(Map<String, Object> a) {
        try {
            return columnsOf(a, String.valueOf(a.get("targetTable")), false);
        } catch (Exception e) {
            return List.of();
        }
    }

    private List<Map<String, Object>> columnsOf(Map<String, Object> a, String table, boolean source) {
        long dsId = Db.longVal(a.get(source ? "sourceDsId" : "targetDsId"), 0L);
        if (dsId > 0) return remoteDs.columnsOf(dsId, table);
        return migration.columnsOf(table);
    }

    private RemoteDsService.Conn sourceConn(Map<String, Object> a) throws Exception {
        long dsId = Db.longVal(a.get("sourceDsId"), 0L);
        if (dsId > 0) return remoteDs.open(dsId);
        return new RemoteDsService.Conn(db.jdbc().getDataSource().getConnection(), null);
    }

    private RemoteDsService.Conn targetConn(Map<String, Object> a) throws Exception {
        long dsId = Db.longVal(a.get("targetDsId"), 0L);
        if (dsId > 0) return remoteDs.open(dsId);
        return new RemoteDsService.Conn(db.jdbc().getDataSource().getConnection(), null);
    }

    private static void exec(Connection c, String sql) throws Exception {
        try (Statement st = c.createStatement()) {
            st.execute(sql);
        }
    }

    private boolean sameInstance(Map<String, Object> a) {
        return Db.longVal(a.get("sourceDsId"), 0L) == Db.longVal(a.get("targetDsId"), 0L);
    }

    /** UPSERT：同一实例内由目标端 SQL 完成（MySQL ON DUPLICATE KEY / PG ON CONFLICT）。 */
    private long upsert(Map<String, Object> a) {
        String dialect = targetDialect(a);
        List<String> dims = dims(a);
        List<Map<String, Object>> metrics = metrics(a);
        List<String> cols = new ArrayList<>(dims);
        for (Map<String, Object> m : metrics) cols.add(String.valueOf(m.get("alias")));
        List<String> idcols = new ArrayList<>();
        for (String c : cols) idcols.add(Db.identifier(c));
        String targetTable = Db.identifier(String.valueOf(a.get("targetTable")));
        StringBuilder sql = new StringBuilder();
        if ("H2".equals(dialect)) {
            List<String> keyCols = new ArrayList<>();
            for (String d : dims) keyCols.add(Db.identifier(d));
            sql.append("MERGE INTO ").append(targetTable).append(" (").append(String.join(",", idcols))
                    .append(") KEY (").append(String.join(",", keyCols)).append(") ").append(viewSql(a));
        } else {
            sql.append("INSERT INTO ").append(targetTable).append(" (").append(String.join(",", idcols))
                    .append(") ").append(viewSql(a));
        }
        List<String> sets = new ArrayList<>();
        for (Map<String, Object> m : metrics) {
            String alias = Db.identifier(String.valueOf(m.get("alias")));
            sets.add("POSTGRESQL".equals(dialect) ? alias + "=EXCLUDED." + alias : alias + "=VALUES(" + alias + ")");
        }
        if ("H2".equals(dialect)) {
            // H2 使用 MERGE ... KEY，无需追加 ON DUPLICATE/ON CONFLICT 子句。
        } else if ("POSTGRESQL".equals(dialect)) {
            List<String> dcols = new ArrayList<>();
            for (String d : dims) dcols.add(Db.identifier(d));
            sql.append(" ON CONFLICT (").append(String.join(",", dcols)).append(") DO UPDATE SET ")
                    .append(String.join(", ", sets));
        } else {
            sql.append(" ON DUPLICATE KEY UPDATE ").append(String.join(", ", sets));
        }
        try (RemoteDsService.Conn tc = targetConn(a)) {
            try (Statement st = tc.connection.createStatement()) {
                return st.executeUpdate(sql.toString());
            }
        } catch (Exception e) {
            throw new BizException(ErrorCode.SQL_INVALID, "UPSERT 刷新失败：" + e.getMessage());
        }
    }

    private boolean ensureUniqueIndex(Map<String, Object> a, String table) {
        List<String> dims = dims(a);
        String idx = ("ux_" + table + "_" + String.join("_", dims)).toLowerCase(Locale.ROOT);
        if (idx.length() > 60) idx = idx.substring(0, 60);
        List<String> cols = new ArrayList<>();
        for (String d : dims) cols.add(Db.identifier(d));
        String sql = "CREATE UNIQUE INDEX " + idx + " ON " + table + " (" + String.join(",", cols) + ")";
        try (RemoteDsService.Conn tc = targetConn(a)) {
            exec(tc.connection, sql);
            return true;
        } catch (Exception e) {
            log.debug("创建唯一索引跳过: {}", e.getMessage());
            return false;
        }
    }

    private String metricType(Map<String, Object> a, Map<String, Object> m) {
        String fn = String.valueOf(m.get("fn")).toUpperCase(Locale.ROOT);
        if ("COUNT".equals(fn) || "COUNT_DISTINCT".equals(fn)) return "BIGINT";
        if ("SUM".equals(fn) || "AVG".equals(fn)) return pg(a) ? "NUMERIC(18,2)" : "DECIMAL(18,2)";
        return ddlType(colType(sourceColumnMap(a), String.valueOf(m.get("column"))), targetDialect(a));
    }

    private static String colType(List<Map<String, Object>> cols, String name) {
        for (Map<String, Object> c : cols) {
            if (String.valueOf(c.get("name")).equalsIgnoreCase(name)) {
                return String.valueOf(c.get("type"));
            }
        }
        return "";
    }

    private static String ddlType(String srcType, String dialect) {
        String t = srcType == null ? "" : srcType.toLowerCase(Locale.ROOT);
        boolean pg = "POSTGRESQL".equalsIgnoreCase(dialect);
        if (t.contains("char") || t.contains("text") || t.contains("string") || t.contains("json")) return "VARCHAR(255)";
        if (t.contains("bigint") || t.contains("int") || t.contains("serial")) return "BIGINT";
        if (t.contains("decimal") || t.contains("numeric") || t.contains("double") || t.contains("float") || t.contains("number")) {
            return pg ? "NUMERIC(18,2)" : "DECIMAL(18,2)";
        }
        if (t.contains("date") || t.contains("time")) return pg ? "TIMESTAMP" : "DATETIME";
        if (t.contains("bool")) return pg ? "BOOLEAN" : "TINYINT";
        return "VARCHAR(255)";
    }

    private boolean pg(Map<String, Object> a) {
        return "POSTGRESQL".equalsIgnoreCase(targetDialect(a));
    }

    private String targetDialect(Map<String, Object> a) {
        long dsId = Db.longVal(a.get("targetDsId"), 0L);
        if (dsId > 0) {
            String t = Db.str(db.scalar("SELECT db_type FROM mig_datasource WHERE id=?", dsId));
            if (t != null && !t.isBlank()) return t.toUpperCase(Locale.ROOT);
        }
        try (Connection c = db.jdbc().getDataSource().getConnection()) {
            String p = c.getMetaData().getDatabaseProductName().toUpperCase(Locale.ROOT);
            if (p.contains("H2")) return "H2";
            if (p.contains("POSTGRES")) return "POSTGRESQL";
            if (p.contains("MYSQL")) return "MYSQL";
        } catch (Exception e) {
            log.debug("无法识别目标数据库方言，按 MySQL 处理: {}", e.getMessage());
        }
        return "MYSQL";
    }

    private String viewName(Map<String, Object> a) {
        String v = Db.str(a.get("viewName"));
        if (v != null && !v.isBlank()) return v;
        String code = String.valueOf(a.get("code")).toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]", "_");
        return "v_agg_" + code;
    }

    private static void assertSafeWhere(String where) {
        if (where.length() > 500) throw new BizException(ErrorCode.PARAM_INVALID, "过滤条件过长（≤500）");
        String s = where.toLowerCase(Locale.ROOT);
        if (s.contains(";") || s.contains("--") || s.contains("/*")) {
            throw new BizException(ErrorCode.PARAM_INVALID, "过滤条件禁止包含多语句或注释");
        }
        for (String bad : new String[]{" insert ", " update ", " delete ", " drop ", " alter ", " truncate ",
                " create ", " grant ", " revoke ", " into "}) {
            if ((" " + s.trim() + " ").contains(bad)) {
                throw new BizException(ErrorCode.PARAM_INVALID, "过滤条件禁止包含写操作");
            }
        }
    }

    private static String cut(String s) {
        if (s == null) return null;
        return s.length() > 480 ? s.substring(0, 480) : s;
    }

    private static LocalDateTime nextTime(long minutes) {
        return minutes <= 0 ? null : LocalDateTime.now().plusMinutes(minutes);
    }

    @SuppressWarnings("unchecked")
    private static List<String> dims(Map<String, Object> a) {
        Object o = parseAny(String.valueOf(a.get("dims")));
        List<String> out = new ArrayList<>();
        if (o instanceof List<?> l) for (Object x : l) out.add(String.valueOf(x));
        return out;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> metrics(Map<String, Object> a) {
        Object o = parseAny(String.valueOf(a.get("metrics")));
        List<Map<String, Object>> out = new ArrayList<>();
        if (o instanceof List<?> l) {
            for (Object x : l) if (x instanceof Map) out.add((Map<String, Object>) x);
        }
        return out;
    }

    /** 解析任意 JSON（对象或数组）；JsonUtil.parse 只支持对象，这里支持数组。 */
    private static Object parseAny(String json) {
        if (json == null || json.isBlank() || "null".equals(json)) return null;
        try {
            return JsonUtil.mapper().readValue(json, Object.class);
        } catch (Exception e) {
            return null;
        }
    }

    private Agg parse(Map<String, Object> b, Map<String, Object> old) {
        Agg a = new Agg();
        a.name = Db.str(b.containsKey("name") ? b.get("name") : old == null ? null : old.get("name"));
        a.sourceDsId = Db.longVal(b.containsKey("sourceDsId") ? b.get("sourceDsId") : old == null ? 0 : old.get("sourceDsId"), 0L);
        a.targetDsId = Db.longVal(b.containsKey("targetDsId") ? b.get("targetDsId") : old == null ? 0 : old.get("targetDsId"), 0L);
        a.sourceTable = Db.str(b.containsKey("sourceTable") ? b.get("sourceTable") : old == null ? null : old.get("sourceTable"));
        a.targetTable = Db.str(b.containsKey("targetTable") ? b.get("targetTable") : old == null ? null : old.get("targetTable"));
        if (a.sourceTable == null || a.targetTable == null || a.name == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "名称/源表/目标表不能为空");
        }
        Object dimsObj = b.containsKey("dims") ? b.get("dims") : old == null ? null : old.get("dims");
        List<String> dimList = new ArrayList<>();
        if (dimsObj instanceof List<?> l) for (Object x : l) dimList.add(String.valueOf(x));
        else dimList = dims(Map.of("dims", String.valueOf(dimsObj)));
        if (dimList.isEmpty()) throw new BizException(ErrorCode.PARAM_INVALID, "至少选择一个维度字段");
        for (String d : dimList) Db.identifier(d);
        a.dimsJson = JsonUtil.toJson(dimList);
        Object metricsObj = b.containsKey("metrics") ? b.get("metrics") : old == null ? null : old.get("metrics");
        a.metricsJson = metricsObj instanceof List<?> ? JsonUtil.toJson(metricsObj) : String.valueOf(metricsObj);
        List<Map<String, Object>> ms = metrics(Map.of("metrics", a.metricsJson));
        if (ms.isEmpty()) throw new BizException(ErrorCode.PARAM_INVALID, "至少配置一个指标");
        for (Map<String, Object> m : ms) {
            String fn = String.valueOf(m.get("fn")).toUpperCase(Locale.ROOT);
            if (!List.of("COUNT", "COUNT_DISTINCT", "SUM", "AVG", "MAX", "MIN").contains(fn)) {
                throw new BizException(ErrorCode.PARAM_INVALID, "不支持的聚合函数：" + fn);
            }
            String alias = Db.str(m.get("alias"));
            if (alias == null || alias.isBlank()) throw new BizException(ErrorCode.PARAM_INVALID, "指标别名不能为空");
            Db.identifier(alias);
        }
        a.where = Db.str(b.containsKey("whereClause") ? b.get("whereClause") : old == null ? null : old.get("whereClause"));
        a.refreshMode = String.valueOf(b.containsKey("refreshMode") ? b.get("refreshMode")
                : old == null ? "FULL" : old.get("refreshMode")).toUpperCase(Locale.ROOT);
        if (!List.of("FULL", "UPSERT").contains(a.refreshMode)) a.refreshMode = "FULL";
        a.refreshMinutes = Db.longVal(b.containsKey("refreshMinutes") ? b.get("refreshMinutes")
                : old == null ? 0 : old.get("refreshMinutes"), 0L);
        return a;
    }

    private static final class Agg {
        String name;
        long sourceDsId;
        long targetDsId;
        String sourceTable;
        String targetTable;
        String dimsJson;
        String metricsJson;
        String where;
        String refreshMode;
        long refreshMinutes;
    }
}
