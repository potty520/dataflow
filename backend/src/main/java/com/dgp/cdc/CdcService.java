package com.dgp.cdc;

import com.dgp.common.BizException;
import com.dgp.common.Db;
import com.dgp.common.ErrorCode;
import com.dgp.common.M;
import com.dgp.migration.RemoteDsService;
import com.dgp.security.AuthContext;
import org.springframework.stereotype.Service;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * P1 CDC / 整库同步（单机版）：
 * 全量搬迁 + 基于增量字段（时间戳/自增 ID）的水位轮询同步；
 * 支持源/目标数据源与自动建表，位点持久化在 cdc_task.watermark。
 */
@Service
public class CdcService {

    private final Db db;
    private final RemoteDsService remoteDs;

    public CdcService(Db db, RemoteDsService remoteDs) {
        this.db = db;
        this.remoteDs = remoteDs;
    }

    public List<Map<String, Object>> tasks() {
        return db.list("SELECT * FROM cdc_task ORDER BY id DESC");
    }

    public Map<String, Object> save(Map<String, Object> b) {
        String code = Db.str(b.get("code"));
        String src = Db.str(b.get("sourceTable"));
        String tgt = Db.str(b.get("targetTable"));
        if (code == null || code.isBlank() || src == null || tgt == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "编码、源表、目标表不能为空");
        }
        Db.identifier(src);
        Db.identifier(tgt);
        String mode = String.valueOf(b.getOrDefault("syncMode", "FULL"));
        if (!"FULL".equals(mode) && !"INCR".equals(mode)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "同步模式仅支持 FULL/INCR");
        }
        Map<String, Object> exist = db.one("SELECT * FROM cdc_task WHERE code=?", code);
        if (exist == null) {
            long id = db.insert("INSERT INTO cdc_task(tenant_id,code,name,source_ds_id,source_table,target_ds_id,target_table,"
                            + "sync_mode,incr_column,batch_size,auto_create,conflict_strategy,status,watermark,create_time,update_time) "
                            + "VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                    AuthContext.tenantId(), code, b.getOrDefault("name", code), b.getOrDefault("sourceDsId", 0),
                    src, b.getOrDefault("targetDsId", 0), tgt, mode, b.get("incrColumn"),
                    b.getOrDefault("batchSize", 1000), b.getOrDefault("autoCreate", 1),
                    b.getOrDefault("conflictStrategy", "OVERWRITE"), b.getOrDefault("status", "ENABLED"),
                    b.get("watermark"), LocalDateTime.now(), LocalDateTime.now());
            return M.of("id", id);
        }
        db.update("UPDATE cdc_task SET name=?,source_ds_id=?,source_table=?,target_ds_id=?,target_table=?,sync_mode=?,"
                        + "incr_column=?,batch_size=?,auto_create=?,conflict_strategy=?,status=?,watermark=?,update_time=? WHERE code=?",
                b.containsKey("name") ? b.get("name") : exist.get("name"),
                b.containsKey("sourceDsId") ? b.get("sourceDsId") : exist.get("sourceDsId"),
                src, b.containsKey("targetDsId") ? b.get("targetDsId") : exist.get("targetDsId"),
                tgt, mode, b.containsKey("incrColumn") ? b.get("incrColumn") : exist.get("incrColumn"),
                b.containsKey("batchSize") ? b.get("batchSize") : exist.get("batchSize"),
                b.containsKey("autoCreate") ? b.get("autoCreate") : exist.get("autoCreate"),
                b.containsKey("conflictStrategy") ? b.get("conflictStrategy") : exist.get("conflictStrategy"),
                b.containsKey("status") ? b.get("status") : exist.get("status"),
                b.containsKey("watermark") ? b.get("watermark") : exist.get("watermark"),
                LocalDateTime.now(), code);
        return M.of("id", exist.get("id"));
    }

    public void delete(long id) { db.update("DELETE FROM cdc_task WHERE id=?", id); }

    public List<Map<String, Object>> runs(Long taskId) {
        return taskId == null ? db.list("SELECT * FROM cdc_run ORDER BY id DESC LIMIT 100")
                : db.list("SELECT * FROM cdc_run WHERE task_id=? ORDER BY id DESC LIMIT 100", taskId);
    }

    public Map<String, Object> run(long id) {
        Map<String, Object> task = db.one("SELECT * FROM cdc_task WHERE id=?", id);
        if (task == null) throw new BizException(ErrorCode.NOT_FOUND, "同步任务不存在");
        if (!"ENABLED".equals(task.get("status"))) throw new BizException(ErrorCode.PARAM_INVALID, "任务已暂停");
        String srcTable = String.valueOf(task.get("sourceTable"));
        String tgtTable = String.valueOf(task.get("targetTable"));
        String mode = String.valueOf(task.get("syncMode"));
        String incrCol = task.get("incrColumn") == null ? null : String.valueOf(task.get("incrColumn"));
        String before = task.get("watermark") == null ? null : String.valueOf(task.get("watermark"));
        if ("INCR".equals(mode) && (incrCol == null || incrCol.isBlank())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "增量同步必须配置增量字段");
        }
        long runId = db.insert("INSERT INTO cdc_run(task_id,status,watermark_before,start_time) VALUES(?,?,?,?)",
                id, "RUNNING", before, LocalDateTime.now());
        long read = 0, written = 0;
        String after = before;
        try (Connection sc = open(Db.longVal(task.get("sourceDsId"), 0L));
             Connection tc = open(Db.longVal(task.get("targetDsId"), 0L))) {
            List<Map<String, Object>> cols = columns(sc, srcTable);
            if (cols.isEmpty()) throw new BizException(ErrorCode.PARAM_INVALID, "源表不存在或没有字段：" + srcTable);
            if (Db.longVal(task.get("autoCreate"), 1) == 1) createTarget(tc, tgtTable, cols);
            tc.setAutoCommit(false);
            if ("FULL".equals(mode) && "OVERWRITE".equals(task.get("conflictStrategy"))) {
                try (Statement st = tc.createStatement()) { st.executeUpdate("DELETE FROM " + Db.identifier(tgtTable)); }
            }
            int batch = Math.max(1, (int) Db.longVal(task.get("batchSize"), 1000));
            boolean loop = "INCR".equals(mode);
            String key = before;
            do {
                String sql = "SELECT * FROM " + Db.identifier(srcTable)
                        + (loop && key != null ? " WHERE " + Db.identifier(incrCol) + " > ?" : "")
                        + (loop ? " ORDER BY " + Db.identifier(incrCol) + " LIMIT " + batch : "");
                int n = 0;
                try (PreparedStatement ps = sc.prepareStatement(sql)) {
                    if (loop && key != null) ps.setString(1, key);
                    try (ResultSet rs = ps.executeQuery()) {
                        ResultSetMetaData md = rs.getMetaData();
                        String insert = insertSql(tgtTable, cols);
                        while (rs.next()) {
                            n++;
                            read++;
                            try (PreparedStatement ins = tc.prepareStatement(insert)) {
                                int i = 1;
                                for (Map<String, Object> c : cols) {
                                    ins.setObject(i++, rs.getObject(String.valueOf(c.get("name"))));
                                }
                                ins.executeUpdate();
                                written++;
                            }
                            if (loop) {
                                Object v = rs.getObject(incrCol);
                                if (v != null) key = String.valueOf(v);
                            }
                        }
                    }
                }
                if (!loop) break;
                after = key;
                if (n < batch) break;
            } while (true);
            tc.commit();
            db.update("UPDATE cdc_task SET watermark=?,last_run_time=?,update_time=? WHERE id=?",
                    after, LocalDateTime.now(), LocalDateTime.now(), id);
            db.update("UPDATE cdc_run SET status='SUCCESS',rows_read=?,rows_written=?,watermark_after=?,end_time=?,message=? WHERE id=?",
                    read, written, after, LocalDateTime.now(), "完成：读取 " + read + "，写入 " + written, runId);
            return M.of("id", runId, "status", "SUCCESS", "rowsRead", read, "rowsWritten", written, "watermark", after);
        } catch (Exception e) {
            db.update("UPDATE cdc_run SET status='FAIL',rows_read=?,rows_written=?,end_time=?,message=? WHERE id=?",
                    read, written, LocalDateTime.now(), String.valueOf(e.getMessage()).substring(0, Math.min(900, String.valueOf(e.getMessage()).length())), runId);
            throw new BizException(ErrorCode.DATASOURCE_CONNECT_FAIL, "同步失败：" + e.getMessage());
        }
    }

    private Connection open(long dsId) throws Exception {
        if (dsId > 0) return remoteDs.open(dsId).connection;
        return db.jdbc().getDataSource().getConnection();
    }

    private List<Map<String, Object>> columns(Connection c, String table) throws Exception {
        List<Map<String, Object>> out = new ArrayList<>();
        DatabaseMetaData md = c.getMetaData();
        String schema = c.getSchema();
        try (ResultSet rs = md.getColumns(null, schema, table, "%")) {
            while (rs.next()) {
                out.add(M.of("name", rs.getString("COLUMN_NAME"), "type", rs.getString("TYPE_NAME"),
                        "size", rs.getInt("COLUMN_SIZE")));
            }
        }
        return out;
    }

    private void createTarget(Connection tc, String table, List<Map<String, Object>> cols) throws Exception {
        boolean exists;
        try (ResultSet rs = tc.getMetaData().getTables(null, tc.getSchema(), table, null)) {
            exists = rs.next();
        }
        if (exists) return;
        StringBuilder ddl = new StringBuilder("CREATE TABLE IF NOT EXISTS ").append(Db.identifier(table)).append(" (");
        for (int i = 0; i < cols.size(); i++) {
            Map<String, Object> c = cols.get(i);
            if (i > 0) ddl.append(", ");
            ddl.append(Db.identifier(String.valueOf(c.get("name")))).append(" ").append(sqlType(c));
        }
        ddl.append(")");
        try (Statement st = tc.createStatement()) { st.execute(ddl.toString()); }
    }

    private static String sqlType(Map<String, Object> c) {
        String type = String.valueOf(c.get("type")).toUpperCase();
        int size = (int) Db.longVal(c.get("size"), 0L);
        if (type.contains("CHAR") || type.contains("VARCHAR")) return type + "(" + Math.max(size, 255) + ")";
        if (type.contains("DECIMAL") || type.contains("NUMERIC")) return type + "(18,2)";
        return type;
    }

    private static String insertSql(String table, List<Map<String, Object>> cols) {
        StringBuilder sql = new StringBuilder("INSERT INTO ").append(Db.identifier(table)).append(" (");
        StringBuilder q = new StringBuilder();
        for (int i = 0; i < cols.size(); i++) {
            if (i > 0) { sql.append(","); q.append(","); }
            sql.append(Db.identifier(String.valueOf(cols.get(i).get("name"))));
            q.append("?");
        }
        return sql.append(") VALUES(").append(q).append(")").toString();
    }
}
