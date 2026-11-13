package com.dgp.migration;

import com.dgp.common.BizException;
import com.dgp.common.Db;
import com.dgp.common.ErrorCode;
import com.dgp.common.M;
import com.dgp.security.AuthContext;
import com.dgp.security.MaskService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 远程（跨网络）数据源：支持直连、SOCKS5 代理、HTTP(CONNECT) 代理。
 *
 * <p>代理访问 JDBC 的标准做法：在本地起端口转发（ProxyTunnel.forward），
 * 驱动连接 127.0.0.1:本地端口，由隧道把流量送到代理后的目标库。
 */
@Service
public class RemoteDsService {

    private static final Logger log = LoggerFactory.getLogger(RemoteDsService.class);
    private static final int TIMEOUT = 10_000;

    private final Db db;
    private final MaskService mask;

    public RemoteDsService(Db db, MaskService mask) {
        this.db = db;
        this.mask = mask;
    }

    // ------------------------------------------------------------ CRUD
    public Map<String, Object> create(Map<String, Object> b) {
        String code = Db.str(b.get("code"));
        if (code == null || code.isBlank()) throw new BizException(ErrorCode.PARAM_INVALID, "编码不能为空");
        if (db.one("SELECT id FROM mig_datasource WHERE code=?", code) != null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "远程数据源编码已存在：" + code);
        }
        String dbType = "POSTGRESQL".equalsIgnoreCase(Db.str(b.get("dbType"))) ? "POSTGRESQL" : "MYSQL";
        String proxyType = proxyType(b);
        long id = db.insert("INSERT INTO mig_datasource(tenant_id,code,name,db_type,host,port,db_name,username,"
                        + "password_enc,proxy_type,proxy_host,proxy_port,proxy_user,proxy_password_enc,status,create_time,update_time) "
                        + "VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                1L, code, Db.str(b.getOrDefault("name", code)), dbType, Db.str(b.get("host")),
                nvl(b.get("port"), 3306), Db.str(b.get("dbName")), Db.str(b.get("username")),
                enc(Db.str(b.get("password"))), proxyType, Db.str(b.get("proxyHost")), nvl(b.get("proxyPort"), null),
                Db.str(b.get("proxyUser")), enc(Db.str(b.get("proxyPassword"))),
                Db.str(b.getOrDefault("status", "ENABLED")), LocalDateTime.now(), LocalDateTime.now());
        return M.of("id", id);
    }

    public Map<String, Object> update(long id, Map<String, Object> b) {
        Map<String, Object> old = require(id);
        String dbType = "POSTGRESQL".equalsIgnoreCase(Db.str(pick(b, old, "dbType"))) ? "POSTGRESQL" : "MYSQL";
        String passwordEnc = b.containsKey("password")
                ? enc(Db.str(b.get("password"))) : Db.str(old.get("passwordEnc"));
        String proxyPassEnc = b.containsKey("proxyPassword")
                ? enc(Db.str(b.get("proxyPassword"))) : Db.str(old.get("proxyPasswordEnc"));
        db.update("UPDATE mig_datasource SET name=?,db_type=?,host=?,port=?,db_name=?,username=?,password_enc=?,"
                        + "proxy_type=?,proxy_host=?,proxy_port=?,proxy_user=?,proxy_password_enc=?,status=?,update_time=? WHERE id=?",
                Db.str(pick(b, old, "name")), dbType, Db.str(pick(b, old, "host")),
                nvl(pick(b, old, "port"), 3306), Db.str(pick(b, old, "dbName")), Db.str(pick(b, old, "username")),
                passwordEnc, proxyType(b, old), Db.str(pick(b, old, "proxyHost")),
                nvl(pick(b, old, "proxyPort"), null), Db.str(pick(b, old, "proxyUser")), proxyPassEnc,
                Db.str(pick(b, old, "status")), LocalDateTime.now(), id);
        return M.of("id", id);
    }

    public void delete(long id) {
        db.update("DELETE FROM mig_datasource WHERE id=?", id);
    }

    public List<Map<String, Object>> list(String keyword) {
        String sql = "SELECT * FROM mig_datasource";
        List<Map<String, Object>> rows = (keyword == null || keyword.isBlank())
                ? db.list(sql + " ORDER BY id DESC")
                : db.list(sql + " WHERE code LIKE ? OR name LIKE ? OR host LIKE ? ORDER BY id DESC",
                "%" + keyword + "%", "%" + keyword + "%", "%" + keyword + "%");
        for (Map<String, Object> r : rows) {
            r.remove("passwordEnc");
            r.remove("proxyPasswordEnc");
            r.put("hasPassword", true);
        }
        return rows;
    }

    public Map<String, Object> detail(long id) {
        Map<String, Object> r = require(id);
        r.remove("passwordEnc");
        r.remove("proxyPasswordEnc");
        return r;
    }

    private Map<String, Object> require(long id) {
        Map<String, Object> r = db.one("SELECT * FROM mig_datasource WHERE id=?", id);
        if (r == null) throw new BizException(ErrorCode.NOT_FOUND, "远程数据源不存在：" + id);
        return r;
    }

    // ------------------------------------------------------------ 连接与测试
    /** 测试连接（不落库配置）或测试已保存的数据源。 */
    public Map<String, Object> test(long id) {
        Map<String, Object> ds = require(id);
        return doTest(ds);
    }

    public Map<String, Object> testAdhoc(Map<String, Object> b) {
        String proxyType = proxyType(b);
        Map<String, Object> ds = M.of(
                "host", Db.str(b.get("host")), "port", nvl(b.get("port"), 3306),
                "dbName", Db.str(b.get("dbName")), "username", Db.str(b.get("username")),
                "passwordEnc", enc(Db.str(b.get("password"))),
                "dbType", "POSTGRESQL".equalsIgnoreCase(Db.str(b.get("dbType"))) ? "POSTGRESQL" : "MYSQL",
                "proxyType", proxyType, "proxyHost", Db.str(b.get("proxyHost")),
                "proxyPort", nvl(b.get("proxyPort"), null), "proxyUser", Db.str(b.get("proxyUser")),
                "proxyPasswordEnc", enc(Db.str(b.get("proxyPassword"))));
        return doTest(ds);
    }

    public Map<String, Object> doTest(Map<String, Object> ds) {
        long t0 = System.currentTimeMillis();
        try (Conn conn = open(ds)) {
            String version = conn.connection.getMetaData().getDatabaseProductVersion();
            long ms = System.currentTimeMillis() - t0;
            db.update("UPDATE mig_datasource SET last_test_ok=1,last_test_time=?,last_test_msg=? WHERE id=?",
                    LocalDateTime.now(), "连接成功", ds.get("id") == null ? 0L : Db.longVal(ds.get("id"), 0L));
            return M.of("ok", true, "latencyMs", ms, "version", version,
                    "proxyType", ds.get("proxyType"));
        } catch (Exception e) {
            String msg = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            log.warn("远程数据源连接测试失败: {}", msg);
            if (ds.get("id") != null) {
                db.update("UPDATE mig_datasource SET last_test_ok=0,last_test_time=?,last_test_msg=? WHERE id=?",
                        LocalDateTime.now(), msg.length() > 400 ? msg.substring(0, 400) : msg,
                        Db.longVal(ds.get("id"), 0L));
            }
            throw new BizException(ErrorCode.DATASOURCE_CONNECT_FAIL, "连接失败：" + msg);
        }
    }

    /** 打开一个远程连接（含代理隧道）；调用方负责 close。 */
    public Conn open(long dsId) {
        return open(require(dsId));
    }

    public Conn open(Map<String, Object> ds) {
        String proxyType = Db.str(ds.get("proxyType"));
        String proxyHost = Db.str(ds.get("proxyHost"));
        Integer proxyPort = ds.get("proxyPort") == null ? null : Db.num(ds.get("proxyPort")).intValue();
        boolean useProxy = proxyHost != null && !proxyHost.isBlank() && proxyPort != null
                && !"DIRECT".equalsIgnoreCase(proxyType);
        String host = Db.str(ds.get("host"));
        int port = ds.get("port") == null ? 3306 : Db.num(ds.get("port")).intValue();
        ProxyTunnel.Forwarder tunnel = null;
        String jdbcHost = host;
        int jdbcPort = port;
        try {
            if (useProxy) {
                tunnel = ProxyTunnel.forward(host, port, proxyType, proxyHost, proxyPort,
                        Db.str(ds.get("proxyUser")), dec(Db.str(ds.get("proxyPasswordEnc"))), TIMEOUT);
                jdbcHost = "127.0.0.1";
                jdbcPort = tunnel.port();
            }
            String dbType = "POSTGRESQL".equalsIgnoreCase(Db.str(ds.get("dbType"))) ? "POSTGRESQL" : "MYSQL";
            String url;
            if ("POSTGRESQL".equals(dbType)) {
                url = "jdbc:postgresql://" + jdbcHost + ":" + jdbcPort + "/" + Db.str(ds.get("dbName"));
            } else {
                // useCursorFetch：按 fetchSize 流式取数（避免整表进内存）；rewriteBatchedStatements：批量写提速
                url = "jdbc:mysql://" + jdbcHost + ":" + jdbcPort + "/" + Db.str(ds.get("dbName"))
                        + "?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai"
                        + "&useSSL=false&allowPublicKeyRetrieval=true&connectTimeout=8000&socketTimeout=600000"
                        + "&useCursorFetch=true&useServerPrepStmts=true&rewriteBatchedStatements=true";
            }
            Connection conn = DriverManager.getConnection(url, Db.str(ds.get("username")),
                    dec(Db.str(ds.get("passwordEnc"))));
            return new Conn(conn, tunnel);
        } catch (Exception e) {
            if (tunnel != null) tunnel.close();
            throw new BizException(ErrorCode.DATASOURCE_CONNECT_FAIL,
                    "远程连接失败：" + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()));
        }
    }

    /** 远程表字段（information_schema，跨 MySQL/PG 兼容）。 */
    public List<Map<String, Object>> columnsOf(long dsId, String table) {
        String t = Db.identifier(table);
        try (Conn c = open(dsId)) {
            return columnsOf(c.connection, t);
        } catch (Exception e) {
            throw new BizException(ErrorCode.DATASOURCE_CONNECT_FAIL, "读取远程字段失败：" + e.getMessage());
        }
    }

    public static List<Map<String, Object>> columnsOf(Connection conn, String table) throws Exception {
        List<Map<String, Object>> out = new ArrayList<>();
        String sql = "SELECT column_name AS name, data_type AS type FROM information_schema.columns "
                + "WHERE LOWER(table_name)=LOWER(?) ORDER BY ordinal_position";
        try (var ps = conn.prepareStatement(sql)) {
            ps.setString(1, table);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("name", rs.getString(1));
                    m.put("type", rs.getString(2));
                    out.add(m);
                }
            }
        }
        if (out.isEmpty()) {
            // PostgreSQL 的 information_schema 需要 schema 过滤；退化到 pg_catalog
            String pg = "SELECT a.attname AS name, format_type(a.atttypid, a.atttypmod) AS type "
                    + "FROM pg_attribute a JOIN pg_class c ON c.oid=a.attrelid "
                    + "WHERE c.relname=? AND a.attnum>0 AND NOT a.attisdropped ORDER BY a.attnum";
            try (var ps = conn.prepareStatement(pg)) {
                ps.setString(1, table);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        Map<String, Object> m = new LinkedHashMap<>();
                        m.put("name", rs.getString(1));
                        m.put("type", rs.getString(2));
                        out.add(m);
                    }
                }
            } catch (Exception ignore) {
                // MySQL 上不存在 pg_catalog，忽略
            }
        }
        return out;
    }

    /** 连通性/列信息的临时探测（供预检使用，不保存）。 */
    public Map<String, Object> probe(Map<String, Object> ds, String table) {
        try (Conn c = open(ds)) {
            List<Map<String, Object>> cols = columnsOf(c.connection, Db.identifier(table));
            return M.of("ok", true, "columns", cols);
        } catch (Exception e) {
            throw new BizException(ErrorCode.DATASOURCE_CONNECT_FAIL, "远程探测失败：" + e.getMessage());
        }
    }

    // ------------------------------------------------------------ helpers
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

    private static Integer nvl(Object v, Integer def) {
        Double d = Db.num(v);
        if (d == null) return def;
        return d.intValue();
    }

    /** 远程连接 + 可选代理隧道，close 时一并释放。 */
    public static final class Conn implements AutoCloseable {
        public final Connection connection;
        private final ProxyTunnel.Forwarder tunnel;

        Conn(Connection connection, ProxyTunnel.Forwarder tunnel) {
            this.connection = connection;
            this.tunnel = tunnel;
        }

        @Override
        public void close() {
            try {
                connection.close();
            } catch (Exception ignore) {
                // ignore
            }
            if (tunnel != null) tunnel.close();
        }
    }
}
