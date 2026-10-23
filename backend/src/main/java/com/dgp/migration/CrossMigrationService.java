package com.dgp.migration;

import com.dgp.common.BizException;
import com.dgp.common.Db;
import com.dgp.common.ErrorCode;
import com.dgp.common.M;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 跨网络/跨实例数据迁移：源库（远程）→ 目标库（本地或远程），两侧均支持代理隧道。
 *
 * <p>与同库迁移的区别：不能再用 INSERT…SELECT 一条 SQL 完成，改为
 * 「源连接流式读 → 目标连接批量写」，按水位分页以支持断点续传与回滚。
 */
@Service
public class CrossMigrationService {

    private static final Logger log = LoggerFactory.getLogger(CrossMigrationService.class);

    private final Db db;
    private final RemoteDsService remoteDs;

    public CrossMigrationService(Db db, RemoteDsService remoteDs) {
        this.db = db;
        this.remoteDs = remoteDs;
    }

    public static boolean needsCross(Map<String, Object> project) {
        return Db.longVal(project.get("sourceDsId"), 0L) > 0 || Db.longVal(project.get("targetDsId"), 0L) > 0;
    }

    /** 源/目标连接（远程或本地池）。 */
    public Connection sourceConnection(Map<String, Object> project) throws Exception {
        long dsId = Db.longVal(project.get("sourceDsId"), 0L);
        return dsId > 0 ? remoteDs.open(dsId).connection : db.jdbc().getDataSource().getConnection();
    }

    public Connection targetConnection(Map<String, Object> project) throws Exception {
        long dsId = Db.longVal(project.get("targetDsId"), 0L);
        return dsId > 0 ? remoteDs.open(dsId).connection : db.jdbc().getDataSource().getConnection();
    }

    public List<Map<String, Object>> columnsOf(Map<String, Object> project, String table, boolean source) {
        long dsId = Db.longVal(project.get(source ? "sourceDsId" : "targetDsId"), 0L);
        try {
            if (dsId > 0) return remoteDs.columnsOf(dsId, table);
            try (Connection c = db.jdbc().getDataSource().getConnection()) {
                return RemoteDsService.columnsOf(c, table);
            }
        } catch (Exception e) {
            throw new BizException(ErrorCode.DATASOURCE_CONNECT_FAIL, "读取字段失败：" + e.getMessage());
        }
    }

    // ------------------------------------------------------------ 执行
    public Map<String, Object> run(Map<String, Object> p, boolean replace, int batchSize, String operator) {
        long projectId = Db.longVal(p.get("id"), 0L);
        String source = Db.identifier(String.valueOf(p.get("sourceTable")));
        String target = Db.identifier(String.valueOf(p.get("targetTable")));
        List<Map<String, Object>> mappings = db.list(
                "SELECT * FROM mig_mapping WHERE project_id=? ORDER BY sort_no,id", projectId);
        if (mappings.isEmpty()) throw new BizException(ErrorCode.PARAM_INVALID, "尚未配置字段映射，无法执行");
        List<String> targets = new ArrayList<>();
        List<String> exprs = new ArrayList<>();
        for (Map<String, Object> m : mappings) {
            targets.add(Db.identifier(String.valueOf(m.get("targetColumn"))));
            exprs.add(valueExpr(m));
        }
        boolean incremental = "INCREMENTAL".equalsIgnoreCase(String.valueOf(p.get("mode")));
        String wm = Db.str(p.get("watermarkCol"));
        if (incremental && (wm == null || wm.isBlank())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "增量模式必须配置水位字段");
        }
        String wmId = incremental ? Db.identifier(wm) : null;
        int bs = (int) Math.max(1L, Math.min(batchSize <= 0 ? Db.longVal(p.get("batchSize"), 5000) : (long) batchSize, 100000L));
        boolean rollbackOnFail = Db.longVal(p.get("rollbackOnFail"), 0) == 1;
        String startWm = incremental ? Db.str(p.get("lastWatermark")) : null;

        long sourceRows;
        try (Connection sc = sourceConnection(p)) {
            String countSql = !incremental || startWm == null ? "SELECT COUNT(*) FROM " + source
                    : "SELECT COUNT(*) FROM " + source + " WHERE " + wmId + " > ?";
            try (PreparedStatement ps = sc.prepareStatement(countSql)) {
                if (incremental && startWm != null) ps.setString(1, startWm);
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    sourceRows = rs.getLong(1);
                }
            }
        } catch (Exception e) {
            throw new BizException(ErrorCode.DATASOURCE_CONNECT_FAIL, "源库统计失败：" + e.getMessage());
        }

        long runId = db.insert("INSERT INTO mig_run(project_id,run_mode,status,start_time,source_rows,operator,start_watermark) "
                        + "VALUES(?,?,'RUNNING',?,?,?,?)",
                projectId, "CROSS", LocalDateTime.now(), sourceRows, operator, startWm);
        long inserted = 0;
        int chunks = 0;
        String endWm = startWm;
        String status = "DONE";
        String message = "";
        boolean rolledBack = false;
        try (Connection sc = sourceConnection(p); Connection tc = targetConnection(p)) {
            if (replace && !incremental) {
                try (Statement st = tc.createStatement()) {
                    st.executeUpdate("DELETE FROM " + target);
                }
            }
            String selectCols = String.join(", ", exprs);
            List<Object> insertVals = new ArrayList<>();
            if (!incremental) {
                inserted = copy(sc, tc, "SELECT " + selectCols + " FROM " + source, null, target, targets, bs);
                chunks = 1;
            } else {
                String from = startWm;
                while (true) {
                    String chunkSql = "SELECT MAX(" + wmId + ") FROM (SELECT " + wmId + " FROM " + source
                            + (from == null ? "" : " WHERE " + wmId + " > ?")
                            + " ORDER BY " + wmId + " LIMIT " + bs + ") t";
                    String chunkMax;
                    try (PreparedStatement ps = sc.prepareStatement(chunkSql)) {
                        if (from != null) ps.setString(1, from);
                        try (ResultSet rs = ps.executeQuery()) {
                            rs.next();
                            chunkMax = rs.getString(1);
                        }
                    }
                    if (chunkMax == null) break;
                    String where = from == null ? " WHERE " + wmId + " <= ?" : " WHERE " + wmId + " > ? AND " + wmId + " <= ?";
                    String sql = "SELECT " + selectCols + " FROM " + source + where + " ORDER BY " + wmId;
                    long affected = from == null
                            ? copy(sc, tc, sql, new Object[]{chunkMax}, target, targets, bs)
                            : copy(sc, tc, sql, new Object[]{from, chunkMax}, target, targets, bs);
                    inserted += affected;
                    chunks++;
                    endWm = chunkMax;
                    from = chunkMax;
                    db.update("UPDATE mig_project SET last_watermark=? WHERE id=?", chunkMax, projectId);
                }
            }
            db.update("UPDATE mig_project SET last_run_time=?,status='DONE',update_time=? WHERE id=?",
                    LocalDateTime.now(), LocalDateTime.now(), projectId);
            message = "跨库写入成功" + (incremental ? "（分 " + chunks + " 批）" : "");
        } catch (Exception e) {
            log.warn("跨库迁移失败 project={}: {}", projectId, e.getMessage());
            status = "FAILED";
            message = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            if (rollbackOnFail && incremental) {
                try (Connection tc = targetConnection(p)) {
                    long deleted;
                    String tWm = mappedTargetWm(projectId, wm);
                    if (tWm == null) {
                        deleted = 0;
                    } else if (startWm != null) {
                        deleted = deleteWhere(tc, target, tWm + " > ?", startWm);
                    } else if (endWm != null) {
                        deleted = deleteWhere(tc, target, tWm + " <= ?", endWm);
                    } else {
                        deleted = deleteWhere(tc, target, null, null);
                    }
                    rolledBack = true;
                    message += "；已回滚 " + deleted + " 行";
                } catch (Exception re) {
                    message += "；回滚失败：" + re.getMessage();
                }
            }
        }
        long targetRows = 0;
        try (Connection tc = targetConnection(p)) {
            try (Statement st = tc.createStatement(); ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM " + target)) {
                rs.next();
                targetRows = rs.getLong(1);
            }
        } catch (Exception ignore) {
            // 目标统计失败不影响结果
        }
        db.update("UPDATE mig_run SET status=?,end_time=?,target_rows=?,inserted_rows=?,failed_rows=?,message=?,"
                        + "start_watermark=?,end_watermark=?,chunk_count=?,rollback_status=? WHERE id=?",
                status, LocalDateTime.now(), targetRows, inserted, "DONE".equals(status) ? 0 : Math.max(0, sourceRows - inserted),
                message.length() > 480 ? message.substring(0, 480) : message, startWm, endWm, chunks,
                rolledBack ? "ROLLED_BACK" : null, runId);
        if ("FAILED".equals(status)) {
            throw new BizException(ErrorCode.SQL_INVALID, "跨库迁移失败：" + message);
        }
        return M.of("runId", runId, "projectId", projectId, "status", status, "sourceRows", sourceRows,
                "targetRows", targetRows, "insertedRows", inserted, "chunkCount", chunks,
                "startWatermark", startWm, "endWatermark", endWm, "message", message, "cross", true);
    }

    /** 回滚跨库批次：按映射解析目标端水位列，删除该区间写入。 */
    public Map<String, Object> rollback(Map<String, Object> run) {
        long projectId = Db.longVal(run.get("projectId"), 0L);
        Map<String, Object> p = db.one("SELECT * FROM mig_project WHERE id=?", projectId);
        if (p == null) throw new BizException(ErrorCode.NOT_FOUND, "迁移项目不存在");
        String wm = Db.str(p.get("watermarkCol"));
        if (wm == null || wm.isBlank()) throw new BizException(ErrorCode.PARAM_INVALID, "仅增量模式支持回滚");
        String tWm = mappedTargetWm(projectId, wm);
        if (tWm == null) throw new BizException(ErrorCode.PARAM_INVALID, "字段映射需包含水位字段 " + wm + " 才能回滚");
        String start = Db.str(run.get("startWatermark"));
        String end = Db.str(run.get("endWatermark"));
        String target = Db.identifier(String.valueOf(p.get("targetTable")));
        long deleted;
        try (Connection tc = targetConnection(p)) {
            if (start != null && !start.isBlank()) {
                deleted = deleteWhere(tc, target, Db.identifier(tWm) + " > ?", start);
            } else if (end != null && !end.isBlank()) {
                deleted = deleteWhere(tc, target, Db.identifier(tWm) + " <= ?", end);
            } else {
                throw new BizException(ErrorCode.PARAM_INVALID, "该批次未记录水位区间，无法回滚");
            }
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException(ErrorCode.SQL_INVALID, "回滚失败：" + e.getMessage());
        }
        db.update("UPDATE mig_run SET rollback_status='ROLLED_BACK' WHERE id=?", Db.longVal(run.get("id"), 0L));
        db.update("UPDATE mig_project SET last_watermark=?,status='READY',update_time=? WHERE id=?",
                start == null || start.isBlank() ? null : start, LocalDateTime.now(), projectId);
        return M.of("runId", Db.longVal(run.get("id"), 0L), "deletedRows", deleted,
                "restoredWatermark", start, "rollbackStatus", "ROLLED_BACK", "cross", true);
    }

    // ------------------------------------------------------------ 预检样例
    /** 从源连接取样例数据（跨库预检用）。 */
    public List<Map<String, Object>> sample(Map<String, Object> p, List<Map<String, Object>> mappings, int limit) {
        List<String> exprs = new ArrayList<>();
        for (Map<String, Object> m : mappings) exprs.add(valueExpr(m));
        String sql = "SELECT " + String.join(", ", exprs) + " FROM " + Db.identifier(String.valueOf(p.get("sourceTable")))
                + " LIMIT " + Math.max(1, Math.min(limit, 20));
        List<Map<String, Object>> out = new ArrayList<>();
        try (Connection c = sourceConnection(p); Statement st = c.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            int n = rs.getMetaData().getColumnCount();
            List<String> labels = new ArrayList<>();
            for (int i = 1; i <= n; i++) labels.add(rs.getMetaData().getColumnLabel(i).toLowerCase());
            while (rs.next()) {
                Map<String, Object> row = new LinkedHashMap<>();
                for (int i = 1; i <= n; i++) row.put(labels.get(i - 1), rs.getObject(i));
                out.add(row);
            }
        } catch (Exception e) {
            throw new BizException(ErrorCode.DATASOURCE_CONNECT_FAIL, "样例取数失败：" + e.getMessage());
        }
        return out;
    }


    // ------------------------------------------------------------ 跨库对账
    /** 跨库对账：源侧统计走源连接，目标侧统计走目标连接，结果写 mig_diff（本库）。 */
    public Map<String, Object> reconcile(Map<String, Object> run) {
        long runId = Db.longVal(run.get("id"), 0L);
        long projectId = Db.longVal(run.get("projectId"), 0L);
        Map<String, Object> p = db.one("SELECT * FROM mig_project WHERE id=?", projectId);
        if (p == null) throw new BizException(ErrorCode.NOT_FOUND, "迁移项目不存在");
        String source = Db.identifier(String.valueOf(p.get("sourceTable")));
        String target = Db.identifier(String.valueOf(p.get("targetTable")));
        List<Map<String, Object>> checks = db.list("SELECT * FROM mig_check WHERE project_id=? ORDER BY id", projectId);
        if (checks.isEmpty()) {
            checks = List.of(M.of("checkType", "ROW_COUNT"), M.of("checkType", "PK_DUP"));
        }
        db.update("DELETE FROM mig_diff WHERE run_id=?", runId);
        List<Map<String, Object>> out = new ArrayList<>();
        int failed = 0;
        try (Connection sc = sourceConnection(p); Connection tc = targetConnection(p)) {
            for (Map<String, Object> c : checks) {
                String type = String.valueOf(c.get("checkType")).toUpperCase(Locale.ROOT);
                String col = Db.str(c.get("columnName"));
                Double tolObj = Db.num(c.get("tolerance"));
                double tol = tolObj == null ? 0 : tolObj;
                String expected = "";
                String actual = "";
                String diff = "";
                String status = "PASS";
                String detail = "";
                try {
                    switch (type) {
                        case "ROW_COUNT" -> {
                            long s = count(sc, "SELECT COUNT(*) FROM " + source);
                            long t = count(tc, "SELECT COUNT(*) FROM " + target);
                            expected = String.valueOf(s);
                            actual = String.valueOf(t);
                            diff = String.valueOf(t - s);
                            if (s == 0) status = t == 0 ? "PASS" : "FAIL";
                            else if (Math.abs(t - s) * 1.0 / s > tol) status = "FAIL";
                            detail = "源表 " + s + " 行 / 目标表 " + t + " 行";
                        }
                        case "PK_DUP" -> {
                            String key = col == null || col.isBlank() ? null : Db.identifier(col);
                            if (key == null) {
                                expected = "0";
                                actual = "0";
                                detail = "未指定主键字段，跳过";
                            } else {
                                long dup = count(tc, "SELECT COUNT(*)-COUNT(DISTINCT " + key + ") FROM " + target);
                                expected = "0";
                                actual = String.valueOf(dup);
                                diff = String.valueOf(dup);
                                status = dup == 0 ? "PASS" : "FAIL";
                                detail = "目标字段 " + key + " 重复行数 " + dup;
                            }
                        }
                        case "NULL_RATE" -> {
                            String cid = Db.identifier(col);
                            Double sRate = queryNum(sc, "SELECT ROUND(SUM(CASE WHEN " + cid + " IS NULL THEN 1 ELSE 0 END)*100.0"
                                    + "/NULLIF(COUNT(*),0),2) FROM " + source);
                            Double tRate = queryNum(tc, "SELECT ROUND(SUM(CASE WHEN " + cid + " IS NULL THEN 1 ELSE 0 END)*100.0"
                                    + "/NULLIF(COUNT(*),0),2) FROM " + target);
                            expected = String.valueOf(sRate == null ? 0 : sRate);
                            actual = String.valueOf(tRate == null ? 0 : tRate);
                            double d = (tRate == null ? 0 : tRate) - (sRate == null ? 0 : sRate);
                            diff = String.valueOf(Math.round(d * 100) / 100.0);
                            status = Math.abs(d) <= tol * 100 ? "PASS" : "FAIL";
                            detail = "源空值率 " + expected + "% / 目标空值率 " + actual + "%";
                        }
                        case "SUM" -> {
                            String cid = Db.identifier(col);
                            Double s = queryNum(sc, "SELECT COALESCE(SUM(" + cid + "),0) FROM " + source);
                            Double t = queryNum(tc, "SELECT COALESCE(SUM(" + cid + "),0) FROM " + target);
                            expected = String.valueOf(s == null ? 0 : s);
                            actual = String.valueOf(t == null ? 0 : t);
                            double d = (t == null ? 0 : t) - (s == null ? 0 : s);
                            double base = Math.max(Math.abs(s == null ? 0 : s), 1e-9);
                            diff = String.valueOf(d);
                            status = Math.abs(d) / base <= tol ? "PASS" : "FAIL";
                            detail = "源合计 " + expected + " / 目标合计 " + actual;
                        }
                        default -> detail = "不支持的检查类型：" + type;
                    }
                } catch (Exception e) {
                    status = "FAIL";
                    detail = "检查执行失败：" + e.getMessage();
                }
                if ("FAIL".equals(status)) failed++;
                db.insert("INSERT INTO mig_diff(run_id,check_type,column_name,expected_val,actual_val,diff_val,status,detail) "
                                + "VALUES(?,?,?,?,?,?,?,?)",
                        runId, type, col, expected, actual, diff, status, detail);
                out.add(M.of("checkType", type, "columnName", col, "expectedVal", expected, "actualVal", actual,
                        "diff", diff, "status", status, "detail", detail));
            }
        } catch (Exception e) {
            throw new BizException(ErrorCode.SQL_INVALID, "跨库对账失败：" + e.getMessage());
        }
        String overall = failed == 0 ? "PASS" : "FAIL";
        db.update("UPDATE mig_run SET status=? WHERE id=?", "DONE".equals(String.valueOf(run.get("status"))) ? "DONE" : overall, runId);
        return M.of("runId", runId, "checkCount", out.size(), "failedCount", failed,
                "status", overall, "checks", out, "cross", true);
    }

    private long count(Connection c, String sql) throws Exception {
        try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            rs.next();
            return rs.getLong(1);
        }
    }

    private Double queryNum(Connection c, String sql) throws Exception {
        try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            if (!rs.next()) return null;
            double v = rs.getDouble(1);
            return rs.wasNull() ? null : v;
        }
    }

    // ------------------------------------------------------------ 内部（复制）
    private long copy(Connection sc, Connection tc, String sql, Object[] params,
                      String target, List<String> targets, int batchSize) throws Exception {
        int n = targets.size();
        int chunk = Math.max(50, Math.min(batchSize, 2000));   // 每条多行 INSERT 的行数
        long rows = 0;
        boolean auto = tc.getAutoCommit();
        tc.setAutoCommit(false);
        try (PreparedStatement ps = sc.prepareStatement(sql)) {
            if (params != null) {
                for (int i = 0; i < params.length; i++) ps.setObject(i + 1, params[i]);
            }
            try {
                // MySQL 已启用 useCursorFetch 时按 fetchSize 流式取数；其它驱动按批取
                ps.setFetchSize(chunk);
            } catch (Exception ignore) {
                // 驱动不支持
            }
            try (ResultSet rs = ps.executeQuery()) {
                List<Object[]> buf = new ArrayList<>(chunk);
                while (rs.next()) {
                    Object[] row = new Object[n];
                    for (int i = 1; i <= n; i++) row[i - 1] = rs.getObject(i);
                    buf.add(row);
                    if (buf.size() >= chunk) {
                        execMultiRow(tc, target, targets, buf);
                        rows += buf.size();
                        buf.clear();
                    }
                }
                if (!buf.isEmpty()) {
                    execMultiRow(tc, target, targets, buf);
                    rows += buf.size();
                    buf.clear();
                }
                tc.commit();
            } catch (Exception e) {
                tc.rollback();
                throw e;
            } finally {
                tc.setAutoCommit(auto);
            }
        }
        return rows;
    }

    /** 多行 INSERT：一条语句写入多行，减少提交与网络往返。 */
    private void execMultiRow(Connection conn, String target, List<String> cols, List<Object[]> rows) throws Exception {
        if (rows.isEmpty()) return;
        StringBuilder sql = new StringBuilder("INSERT INTO ").append(target).append(" (")
                .append(String.join(",", cols)).append(") VALUES ");
        for (int r = 0; r < rows.size(); r++) {
            if (r > 0) sql.append(',');
            sql.append('(').append(String.join(",", Collections.nCopies(cols.size(), "?"))).append(')');
        }
        try (PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            int idx = 1;
            for (Object[] row : rows) {
                for (Object v : row) ps.setObject(idx++, v);
            }
            ps.executeUpdate();
        }
    }

    private String mappedTargetWm(long projectId, String wm) {
        for (Map<String, Object> m : db.list("SELECT * FROM mig_mapping WHERE project_id=? ORDER BY sort_no,id", projectId)) {
            String sc = Db.str(m.get("sourceColumn"));
            if (sc != null && sc.equalsIgnoreCase(wm)) return Db.str(m.get("targetColumn"));
        }
        return null;
    }

    private long deleteWhere(Connection tc, String target, String cond, Object val) throws Exception {
        String sql = "DELETE FROM " + target + (cond == null ? "" : " WHERE " + cond);
        try (PreparedStatement ps = tc.prepareStatement(sql)) {
            if (cond != null) ps.setObject(1, val);
            return ps.executeUpdate();
        }
    }

    private String valueExpr(Map<String, Object> m) {
        String type = String.valueOf(m.getOrDefault("transformType", "DIRECT")).toUpperCase();
        String src = Db.str(m.get("sourceColumn"));
        String srcId = (src == null || src.isBlank()) ? null : Db.identifier(src);
        String def = Db.str(m.get("defaultValue"));
        String defSql = "'" + (def == null ? "" : def.replace("'", "''")) + "'";
        return switch (type) {
            case "EXPR" -> "(" + Db.str(m.get("transformExpr")) + ")";
            case "CONST" -> defSql;
            case "DEFAULT" -> "COALESCE(" + (srcId == null ? "NULL" : srcId) + ", " + defSql + ")";
            default -> srcId == null ? "NULL" : srcId;
        };
    }
}
