package com.dgp.migration;

import com.dgp.common.BizException;
import com.dgp.common.Db;
import com.dgp.common.ErrorCode;
import com.dgp.common.M;
import com.dgp.security.AuthContext;
import com.dgp.security.MaskService;
import org.apache.commons.net.ftp.FTPClient;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * FTP 文件迁移：表 → CSV → FTP → 远端；FTP → CSV → 表。
 *
 * <p>跨网络：FTP 连接可走 SOCKS5/HTTP 代理（见 FtpTransferService）；源/目标表也可来自远程数据源。
 * 文件中转保证在两侧网络互不可达时仍能通过文件服务器完成迁移（摆渡）。
 */
@Service
public class FileMigrationService {

    private final Db db;
    private final MaskService mask;
    private final FtpTransferService ftp;
    private final RemoteDsService remoteDs;

    public FileMigrationService(Db db, MaskService mask, FtpTransferService ftp, RemoteDsService remoteDs) {
        this.db = db;
        this.mask = mask;
        this.ftp = ftp;
        this.remoteDs = remoteDs;
    }

    // ------------------------------------------------------------ 任务 CRUD
    public Map<String, Object> createTask(Map<String, Object> b) {
        String code = Db.str(b.get("code"));
        if (code == null || code.isBlank()) throw new BizException(ErrorCode.PARAM_INVALID, "任务编码不能为空");
        if (db.one("SELECT id FROM mig_file_task WHERE code=?", code) != null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "任务编码已存在：" + code);
        }
        String direction = "IMPORT".equalsIgnoreCase(Db.str(b.get("direction"))) ? "IMPORT" : "EXPORT";
        long id = db.insert("INSERT INTO mig_file_task(tenant_id,code,name,direction,project_id,source_table,target_table,"
                        + "file_format,remote_type,remote_host,remote_port,remote_user,remote_password_enc,remote_dir,remote_file,"
                        + "proxy_type,proxy_host,proxy_port,proxy_user,proxy_password_enc,compress,status,create_time,update_time) "
                        + "VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                1L, code, Db.str(b.getOrDefault("name", code)), direction,
                b.get("projectId") == null ? null : Db.longVal(b.get("projectId"), 0L),
                Db.str(b.get("sourceTable")), Db.str(b.get("targetTable")),
                Db.str(b.getOrDefault("fileFormat", "CSV")), "FTP",
                Db.str(b.get("remoteHost")), Db.num(b.get("remotePort")) == null ? 21 : Db.num(b.get("remotePort")).intValue(),
                Db.str(b.get("remoteUser")), enc(Db.str(b.get("remotePassword"))),
                Db.str(b.getOrDefault("remoteDir", "/")), Db.str(b.get("remoteFile")),
                proxyType(b), Db.str(b.get("proxyHost")),
                Db.num(b.get("proxyPort")) == null ? null : Db.num(b.get("proxyPort")).intValue(),
                Db.str(b.get("proxyUser")), enc(Db.str(b.get("proxyPassword"))),
                Db.num(b.get("compress")) == null ? 0 : Db.num(b.get("compress")).intValue(),
                Db.str(b.getOrDefault("status", "ENABLED")), LocalDateTime.now(), LocalDateTime.now());
        return M.of("id", id);
    }

    public Map<String, Object> updateTask(long id, Map<String, Object> b) {
        Map<String, Object> old = requireTask(id);
        db.update("UPDATE mig_file_task SET name=?,direction=?,project_id=?,source_table=?,target_table=?,"
                        + "remote_host=?,remote_port=?,remote_user=?,remote_password_enc=?,remote_dir=?,remote_file=?,"
                        + "proxy_type=?,proxy_host=?,proxy_port=?,proxy_user=?,proxy_password_enc=?,compress=?,status=?,update_time=? WHERE id=?",
                Db.str(pick(b, old, "name")), Db.str(pick(b, old, "direction")),
                b.containsKey("projectId") ? (b.get("projectId") == null ? null : Db.longVal(b.get("projectId"), 0L)) : old.get("projectId"),
                Db.str(pick(b, old, "sourceTable")), Db.str(pick(b, old, "targetTable")),
                Db.str(pick(b, old, "remoteHost")),
                Db.num(pick(b, old, "remotePort")) == null ? 21 : Db.num(pick(b, old, "remotePort")).intValue(),
                Db.str(pick(b, old, "remoteUser")),
                b.containsKey("remotePassword") ? enc(Db.str(b.get("remotePassword"))) : Db.str(old.get("remotePasswordEnc")),
                Db.str(pick(b, old, "remoteDir")), Db.str(pick(b, old, "remoteFile")),
                proxyType(b, old), Db.str(pick(b, old, "proxyHost")),
                Db.num(pick(b, old, "proxyPort")) == null ? null : Db.num(pick(b, old, "proxyPort")).intValue(),
                Db.str(pick(b, old, "proxyUser")),
                b.containsKey("proxyPassword") ? enc(Db.str(b.get("proxyPassword"))) : Db.str(old.get("proxyPasswordEnc")),
                Db.num(pick(b, old, "compress")) == null ? 0 : Db.num(pick(b, old, "compress")).intValue(),
                Db.str(pick(b, old, "status")), LocalDateTime.now(), id);
        return M.of("id", id);
    }

    public void deleteTask(long id) {
        db.update("DELETE FROM mig_file_run WHERE task_id=?", id);
        db.update("DELETE FROM mig_file_task WHERE id=?", id);
    }

    public List<Map<String, Object>> listTasks(String keyword) {
        List<Map<String, Object>> rows = (keyword == null || keyword.isBlank())
                ? db.list("SELECT t.*, (SELECT COUNT(*) FROM mig_file_run r WHERE r.task_id=t.id) AS run_count "
                + "FROM mig_file_task t ORDER BY t.id DESC")
                : db.list("SELECT t.*, (SELECT COUNT(*) FROM mig_file_run r WHERE r.task_id=t.id) AS run_count "
                + "FROM mig_file_task t WHERE t.code LIKE ? OR t.name LIKE ? OR t.remote_host LIKE ? ORDER BY t.id DESC",
                "%" + keyword + "%", "%" + keyword + "%", "%" + keyword + "%");
        for (Map<String, Object> r : rows) {
            r.remove("remotePasswordEnc");
            r.remove("proxyPasswordEnc");
        }
        return rows;
    }

    public Map<String, Object> taskDetail(long id) {
        Map<String, Object> t = requireTask(id);
        t.remove("remotePasswordEnc");
        t.remove("proxyPasswordEnc");
        t.put("runs", db.list("SELECT * FROM mig_file_run WHERE task_id=? ORDER BY id DESC LIMIT 10", id));
        return t;
    }

    public List<Map<String, Object>> runs(long taskId) {
        return db.list("SELECT * FROM mig_file_run WHERE task_id=? ORDER BY id DESC LIMIT 50", taskId);
    }

    public Map<String, Object> testFtp(Map<String, Object> b) {
        Map<String, Object> task = M.of(
                "remoteHost", Db.str(b.get("remoteHost")),
                "remotePort", Db.num(b.get("remotePort")) == null ? 21 : Db.num(b.get("remotePort")).intValue(),
                "remoteUser", Db.str(b.get("remoteUser")),
                "proxyType", proxyType(b), "proxyHost", Db.str(b.get("proxyHost")),
                "proxyPort", Db.num(b.get("proxyPort")) == null ? null : Db.num(b.get("proxyPort")).intValue(),
                "proxyUser", Db.str(b.get("proxyUser")), "proxyPassword", Db.str(b.get("proxyPassword")));
        return ftp.test(task, Db.str(b.get("remotePassword")));
    }

    // ------------------------------------------------------------ 执行
    public Map<String, Object> run(long taskId, String operator) {
        Map<String, Object> task = requireTask(taskId);
        boolean export = !"IMPORT".equalsIgnoreCase(Db.str(task.get("direction")));
        long runId = db.insert("INSERT INTO mig_file_run(task_id,direction,status,start_time,operator) VALUES(?,?, 'RUNNING',?,?)",
                taskId, export ? "EXPORT" : "IMPORT", LocalDateTime.now(), operator);
        long rows = 0;
        long bytes = 0;
        String checksum = null;
        String status = "DONE";
        String message = "";
        Path local = null;
        try {
            String remoteFile = Db.str(task.get("remoteFile"));
            if (remoteFile == null || remoteFile.isBlank()) {
                throw new BizException(ErrorCode.PARAM_INVALID, "远端文件名不能为空");
            }
            boolean gz = Db.longVal(task.get("compress"), 0) == 1;
            String fileName = gz && !remoteFile.endsWith(".gz") ? remoteFile + ".gz" : remoteFile;
            local = Files.createTempFile("dgp-mig-", gz ? ".csv.gz" : ".csv");
            FTPClient client = ftp.connect(task, dec(Db.str(task.get("remotePasswordEnc"))));
            try {
                if (export) {
                    rows = exportToCsv(task, local, gz);
                    bytes = Files.size(local);
                    checksum = sha256(local);
                    ftp.upload(client, local, Db.str(task.get("remoteDir")), fileName);
                    message = "导出并上传成功：" + fileName;
                } else {
                    ftp.download(client, Db.str(task.get("remoteDir")), fileName, local);
                    bytes = Files.size(local);
                    checksum = sha256(local);
                    rows = importFromCsv(task, local, gz);
                    message = "下载并导入成功：" + fileName;
                }
            } finally {
                try {
                    client.disconnect();
                } catch (IOException ignore) {
                    // ignore
                }
            }
        } catch (Exception e) {
            status = "FAILED";
            message = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
        } finally {
            if (local != null) {
                try {
                    Files.deleteIfExists(local);
                } catch (IOException ignore) {
                    // ignore
                }
            }
            db.update("UPDATE mig_file_run SET status=?,end_time=?,rows_count=?,bytes_count=?,checksum=?,message=? WHERE id=?",
                    status, LocalDateTime.now(), rows, bytes, checksum,
                    message.length() > 480 ? message.substring(0, 480) : message, runId);
        }
        if ("FAILED".equals(status)) {
            throw new BizException(ErrorCode.SQL_INVALID, "文件迁移失败：" + message);
        }
        return M.of("runId", runId, "taskId", taskId, "direction", export ? "EXPORT" : "IMPORT",
                "status", status, "rows", rows, "bytes", bytes, "checksum", checksum, "message", message);
    }

    // ------------------------------------------------------------ 导出（表 → CSV）
    private long exportToCsv(Map<String, Object> task, Path local, boolean gz) throws Exception {
        Long projectId = task.get("projectId") == null ? null : Db.longVal(task.get("projectId"), 0L);
        String source = Db.str(task.get("sourceTable"));
        List<Map<String, Object>> mappings = projectId == null ? null
                : db.list("SELECT * FROM mig_mapping WHERE project_id=? ORDER BY sort_no,id", projectId);
        if (mappings == null || mappings.isEmpty()) {
            if (source == null || source.isBlank()) throw new BizException(ErrorCode.PARAM_INVALID, "导出需指定源表或迁移项目");
            mappings = new ArrayList<>();
            for (Map<String, Object> c : columnsOfForProject(projectId, source)) {
                mappings.add(M.of("sourceColumn", c.get("name"), "targetColumn", c.get("name"), "transformType", "DIRECT"));
            }
        }
        String table = source != null && !source.isBlank() ? source : sourceOfProject(projectId);
        Db.identifier(table);
        List<String> headers = new ArrayList<>();
        List<String> exprs = new ArrayList<>();
        for (Map<String, Object> m : mappings) {
            headers.add(String.valueOf(m.get("targetColumn")));
            exprs.add(valueExpr(m));
        }
        String sql = "SELECT " + String.join(", ", exprs) + " FROM " + table;
        long count = 0;
        try (Connection conn = openSource(projectId, task);
             Writer out = gz
                     ? new BufferedWriter(new OutputStreamWriter(new GZIPOutputStream(Files.newOutputStream(local)), StandardCharsets.UTF_8))
                     : Files.newBufferedWriter(local, StandardCharsets.UTF_8)) {
            String fmt = formatOf(task);
            count = writeDelimited(conn, sql, headers, out, fmt);
        }
        return count;
    }

    /** 按任务配置的文件格式写分隔文本：CSV（逗号+引号）或 TXT（制表符/竖线+反斜杠转义）。 */
    private long writeDelimited(Connection conn, String sql, List<String> headers, Writer out, String fmt) throws Exception {
        char delim = delimiterOf(fmt);
        boolean txt = isTxt(fmt);
        boolean cursorFetch = false;
        try {
            String u = conn.getMetaData().getURL();
            cursorFetch = u != null && u.toLowerCase().contains("usecursorfetch=true");
        } catch (Exception ignore) {
            // ignore
        }
        try (Statement st = conn.createStatement()) {
            try {
                // MySQL：URL 已启用 useCursorFetch 时用正数 fetchSize；否则用 Integer.MIN_VALUE 流式逐行取
                if (conn.getMetaData().getDatabaseProductName().toLowerCase().contains("mysql") && !cursorFetch) {
                    st.setFetchSize(Integer.MIN_VALUE);
                } else {
                    st.setFetchSize(500);
                }
            } catch (Exception ignore) {
                // 部分驱动不支持
            }
            try (ResultSet rs = st.executeQuery(sql)) {
                int n = rs.getMetaData().getColumnCount();
                for (int i = 1; i <= n; i++) {
                    if (i > 1) out.write(delim);
                    String h = headers.size() >= i ? headers.get(i - 1) : rs.getMetaData().getColumnLabel(i);
                    out.write(txt ? txtEscape(h) : csvEscape(h));
                }
                out.write('\n');
                long rows = 0;
                while (rs.next()) {
                    for (int i = 1; i <= n; i++) {
                        if (i > 1) out.write(delim);
                        Object v = rs.getObject(i);
                        String s = v == null ? "" : String.valueOf(v);
                        out.write(txt ? txtEscape(s) : csvEscape(s));
                    }
                    out.write('\n');
                    rows++;
                }
                out.flush();
                return rows;
            }
        }
    }

    // ------------------------------------------------------------ 导入（CSV → 表）
    private long importFromCsv(Map<String, Object> task, Path local, boolean gz) throws Exception {
        Long projectId = task.get("projectId") == null ? null : Db.longVal(task.get("projectId"), 0L);
        String target = Db.str(task.get("targetTable"));
        if ((target == null || target.isBlank()) && projectId != null) target = targetOfProject(projectId);
        if (target == null || target.isBlank()) throw new BizException(ErrorCode.PARAM_INVALID, "导入需指定目标表");
        Db.identifier(target);
        long count = 0;
        try (BufferedReader reader = gz
                ? new BufferedReader(new InputStreamReader(new GZIPInputStream(Files.newInputStream(local)), StandardCharsets.UTF_8))
                : Files.newBufferedReader(local, StandardCharsets.UTF_8);
             Connection conn = openTarget(projectId, task)) {
            String header = reader.readLine();
            if (header == null) throw new BizException(ErrorCode.PARAM_INVALID, "文件为空");
            String fmt = formatOf(task);
            List<String> cols = parseLine(header, fmt);
            for (String c : cols) Db.identifier(c);
            String sql = "INSERT INTO " + target + " (" + String.join(",", cols) + ") VALUES ("
                    + String.join(",", java.util.Collections.nCopies(cols.size(), "?")) + ")";
            boolean auto = conn.getAutoCommit();
            conn.setAutoCommit(false);
            int chunk = 500;
            List<List<Object>> buf = new ArrayList<>(chunk);
            try {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.isBlank()) continue;
                    List<String> vals = parseLine(line, fmt);
                    List<Object> row = new ArrayList<>(cols.size());
                    for (int i = 0; i < cols.size(); i++) {
                        String v = i < vals.size() ? vals.get(i) : null;
                        row.add((v == null || v.isEmpty()) ? null : v);
                    }
                    buf.add(row);
                    count++;
                    if (buf.size() >= chunk) {
                        execMultiRowInsert(conn, target, cols, buf);
                        buf.clear();
                    }
                }
                if (!buf.isEmpty()) {
                    execMultiRowInsert(conn, target, cols, buf);
                    buf.clear();
                }
                conn.commit();
            } catch (Exception e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(auto);
            }
        }
        return count;
    }

    // ------------------------------------------------------------ 连接/辅助
    private Connection openSource(Long projectId, Map<String, Object> task) throws Exception {
        Long dsId = dsIdOfProject(projectId, "sourceDsId");
        if (dsId != null && dsId > 0) return remoteDs.open(dsId).connection;
        return JdbcHolder.local(db);
    }

    private Connection openTarget(Long projectId, Map<String, Object> task) throws Exception {
        Long dsId = dsIdOfProject(projectId, "targetDsId");
        if (dsId != null && dsId > 0) return remoteDs.open(dsId).connection;
        return JdbcHolder.local(db);
    }

    private Long dsIdOfProject(Long projectId, String field) {
        if (projectId == null) return null;
        Map<String, Object> p = db.one("SELECT * FROM mig_project WHERE id=?", projectId);
        return p == null ? null : Db.longVal(p.get(field), 0L);
    }

    private String sourceOfProject(Long projectId) {
        Map<String, Object> p = db.one("SELECT source_table FROM mig_project WHERE id=?", projectId);
        return p == null ? null : Db.str(p.get("sourceTable"));
    }

    private String targetOfProject(Long projectId) {
        Map<String, Object> p = db.one("SELECT target_table FROM mig_project WHERE id=?", projectId);
        return p == null ? null : Db.str(p.get("targetTable"));
    }

    private List<Map<String, Object>> columnsOfForProject(Long projectId, String table) {
        Long dsId = dsIdOfProject(projectId, "sourceDsId");
        return columnsOf(dsId, table);
    }

    private List<Map<String, Object>> columnsOf(Long dsId, String table) {
        if (dsId != null && dsId > 0) return remoteDs.columnsOf(dsId, table);
        try (Connection c = JdbcHolder.local(db)) {
            return RemoteDsService.columnsOf(c, table);
        } catch (Exception e) {
            throw new BizException(ErrorCode.DATASOURCE_CONNECT_FAIL, "读取字段失败：" + e.getMessage());
        }
    }

    private String valueExpr(Map<String, Object> m) {
        String type = String.valueOf(m.getOrDefault("transformType", "DIRECT")).toUpperCase();
        String src = Db.str(m.get("sourceColumn"));
        String srcId = (src == null || src.isBlank()) ? null : Db.identifier(src);
        return switch (type) {
            case "EXPR" -> "(" + Db.str(m.get("transformExpr")) + ")";
            case "CONST" -> "'" + (Db.str(m.get("defaultValue")) == null ? "" : Db.str(m.get("defaultValue")).replace("'", "''")) + "'";
            case "DEFAULT" -> "COALESCE(" + (srcId == null ? "NULL" : srcId) + ", '"
                    + (Db.str(m.get("defaultValue")) == null ? "" : Db.str(m.get("defaultValue")).replace("'", "''")) + "')";
            default -> srcId == null ? "NULL" : srcId;
        };
    }


    /** 任务文件格式：CSV（默认）/ TXT（制表符）/ TXT_PIPE（竖线）。 */
    private static String formatOf(Map<String, Object> task) {
        String f = Db.str(task.get("fileFormat"));
        return (f == null || f.isBlank()) ? "CSV" : f.toUpperCase();
    }

    private static boolean isTxt(String fmt) {
        return fmt.startsWith("TXT");
    }

    private static char delimiterOf(String fmt) {
        return switch (fmt) {
            case "TXT", "TXT_TAB" -> '\t';
            case "TXT_PIPE", "TXT_PIPE_SEPARATED" -> '|';
            default -> ',';
        };
    }

    /** TXT 字段转义：反斜杠、制表符、换行。 */
    static String txtEscape(String v) {
        if (v == null) return "";
        return v.replace("\\", "\\\\").replace("\t", "\\t").replace("\n", "\\n").replace("\r", "\\r");
    }

    /** TXT 行解析：按分隔符切分并还原转义。 */
    static List<String> parseTxtLine(String line, char delim) {
        List<String> out = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '\\' && i + 1 < line.length()) {
                char nx = line.charAt(++i);
                switch (nx) {
                    case 't' -> sb.append('\t');
                    case 'n' -> sb.append('\n');
                    case 'r' -> sb.append('\r');
                    case '\\' -> sb.append('\\');
                    default -> sb.append(nx);
                }
            } else if (c == delim) {
                out.add(sb.toString());
                sb.setLength(0);
            } else {
                sb.append(c);
            }
        }
        out.add(sb.toString());
        return out;
    }

    /** 按格式解析一行（CSV 走引号解析，TXT 走转义解析）。 */
    static List<String> parseLine(String line, String fmt) {
        if (isTxt(fmt)) return parseTxtLine(line, delimiterOf(fmt));
        char delim = delimiterOf(fmt);
        return parseCsvLineWithDelim(line, delim);
    }

    /** 带自定义分隔符的 CSV 解析（默认逗号；兼容竖线等）。 */
    static List<String> parseCsvLineWithDelim(String line, char delim) {
        if (delim == ',') return parseCsvLine(line);
        List<String> out = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        boolean inQuote = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (inQuote) {
                if (c == '"') {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
                        sb.append('"');
                        i++;
                    } else {
                        inQuote = false;
                    }
                } else {
                    sb.append(c);
                }
            } else if (c == '"') {
                inQuote = true;
            } else if (c == delim) {
                out.add(sb.toString());
                sb.setLength(0);
            } else {
                sb.append(c);
            }
        }
        out.add(sb.toString());
        return out;
    }


    /** 多行 INSERT：一条语句写多行，导入速度显著提升。 */
    private void execMultiRowInsert(Connection conn, String target, List<String> cols, List<List<Object>> rows) throws Exception {
        if (rows.isEmpty()) return;
        StringBuilder sql = new StringBuilder("INSERT INTO ").append(target).append(" (")
                .append(String.join(",", cols)).append(") VALUES ");
        for (int r = 0; r < rows.size(); r++) {
            if (r > 0) sql.append(',');
            sql.append('(').append(String.join(",", java.util.Collections.nCopies(cols.size(), "?"))).append(')');
        }
        try (PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            int idx = 1;
            for (List<Object> row : rows) {
                for (Object v : row) ps.setObject(idx++, v);
            }
            ps.executeUpdate();
        }
    }

    private static String csvEscape(String v) {
        if (v == null) return "";
        boolean need = v.contains(",") || v.contains("\"") || v.contains("\n") || v.contains("\r");
        if (!need) return v;
        return "\"" + v.replace("\"", "\"\"") + "\"";
    }

    /** 简单 RFC4180 解析（支持引号、逗号、换行、"" 转义）。 */
    static List<String> parseCsvLine(String line) {
        List<String> out = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        boolean inQuote = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (inQuote) {
                if (c == '"') {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
                        sb.append('"');
                        i++;
                    } else {
                        inQuote = false;
                    }
                } else {
                    sb.append(c);
                }
            } else if (c == '"') {
                inQuote = true;
            } else if (c == ',') {
                out.add(sb.toString());
                sb.setLength(0);
            } else {
                sb.append(c);
            }
        }
        out.add(sb.toString());
        return out;
    }

    private String sha256(Path file) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        try (var in = Files.newInputStream(file)) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) md.update(buf, 0, n);
        }
        return HexFormat.of().formatHex(md.digest());
    }

    private Map<String, Object> requireTask(long id) {
        Map<String, Object> t = db.one("SELECT * FROM mig_file_task WHERE id=?", id);
        if (t == null) throw new BizException(ErrorCode.NOT_FOUND, "文件迁移任务不存在：" + id);
        return t;
    }

    private String enc(String plain) {
        return (plain == null || plain.isEmpty()) ? null : mask.mask("ENCRYPT", plain);
    }

    private String dec(String encrypted) {
        return encrypted == null ? null : mask.decrypt(encrypted);
    }

    private static String proxyType(Map<String, Object> b) {
        String v = Db.str(b.get("proxyType"));
        return v == null ? "DIRECT" : v.toUpperCase();
    }

    private static String proxyType(Map<String, Object> b, Map<String, Object> old) {
        return b.containsKey("proxyType") ? proxyType(b) : Db.str(old.get("proxyType"));
    }

    private static Object pick(Map<String, Object> b, Map<String, Object> old, String k) {
        return b.containsKey(k) ? b.get(k) : old.get(k);
    }

    /** 本地连接持有器：复用 Db 的数据源，且不关闭池连接。 */
    static final class JdbcHolder {
        static Connection local(Db db) throws Exception {
            return db.jdbc().getDataSource().getConnection();
        }

    }
}
