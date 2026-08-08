package com.zhangye.dataflow.util;

import com.zhangye.dataflow.entity.AggDatasource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

public final class JdbcConnectionFactory {

    private static final Logger log = LoggerFactory.getLogger(JdbcConnectionFactory.class);
    private static final String HIVE_DRIVER_CLASS = "org.apache.hive.jdbc.HiveDriver";
    private static volatile Driver hiveDriver;

    private JdbcConnectionFactory() {
    }

    public static Connection getConnection(AggDatasource ds, String password) throws Exception {
        String dbType = ds.getDbType() != null ? ds.getDbType().toLowerCase() : "mysql";
        String url = buildJdbcUrl(ds);

        if ("hive".equals(dbType)) {
            return getHiveConnection(url, ds.getUsername(), password);
        }

        loadDriver(dbType);
        return DriverManager.getConnection(url, ds.getUsername(), password);
    }

    public static String buildJdbcUrl(AggDatasource ds) {
        String dbType = ds.getDbType() != null ? ds.getDbType().toLowerCase() : "mysql";
        String host = ds.getHost();
        Integer port = ds.getPort();
        String db = ds.getDatabaseName();

        switch (dbType) {
            case "mysql":
                return "jdbc:mysql://" + host + ":" + (port != null ? port : 3306) + "/" + db
                        + "?useUnicode=true&characterEncoding=UTF-8&connectionCollation=utf8mb4_unicode_ci"
                        + "&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&useSSL=false&characterSetResults=utf8";
            case "postgresql":
            case "pg":
                return "jdbc:postgresql://" + host + ":" + (port != null ? port : 5432) + "/" + db;
            case "oracle":
                return "jdbc:oracle:thin:@" + host + ":" + (port != null ? port : 1521) + ":" + db;
            case "sqlserver":
                return "jdbc:sqlserver://" + host + ":" + (port != null ? port : 1433) + ";databaseName=" + db;
            case "h2":
                return "jdbc:h2:" + (ds.getFilePath() != null ? ds.getFilePath() : db);
            case "hive":
                return "jdbc:hive2://" + host + ":" + (port != null ? port : 10000) + "/" + (db != null ? db : "default");
            default:
                return "jdbc:mysql://" + host + ":" + (port != null ? port : 3306) + "/" + db
                        + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai";
        }
    }

    private static void loadDriver(String dbType) {
        String driver;
        switch (dbType) {
            case "mysql":
                driver = "com.mysql.cj.jdbc.Driver";
                break;
            case "postgresql":
            case "pg":
                driver = "org.postgresql.Driver";
                break;
            case "oracle":
                driver = "oracle.jdbc.OracleDriver";
                break;
            case "sqlserver":
                driver = "com.microsoft.sqlserver.jdbc.SQLServerDriver";
                break;
            case "h2":
                driver = "org.h2.Driver";
                break;
            default:
                driver = null;
        }

        if (driver == null) {
            return;
        }
        try {
            Class.forName(driver);
        } catch (ClassNotFoundException e) {
            log.warn("JDBC driver {} not found, DriverManager will try registered drivers", driver);
        }
    }

    private static Connection getHiveConnection(String url, String username, String password) throws Exception {
        try {
            Class.forName(HIVE_DRIVER_CLASS);
            return DriverManager.getConnection(url, username, password);
        } catch (ClassNotFoundException | SQLException primaryError) {
            Driver driver = loadHiveDriverFromStandaloneJar();
            Properties props = new Properties();
            if (username != null) {
                props.put("user", username);
            }
            if (password != null) {
                props.put("password", password);
            }
            Connection conn = driver.connect(url, props);
            if (conn == null) {
                throw new SQLException("Hive JDBC driver rejected URL: " + url);
            }
            return conn;
        }
    }

    private static Driver loadHiveDriverFromStandaloneJar() throws Exception {
        Driver cached = hiveDriver;
        if (cached != null) {
            return cached;
        }
        synchronized (JdbcConnectionFactory.class) {
            if (hiveDriver != null) {
                return hiveDriver;
            }
            File jar = findHiveStandaloneJar();
            if (jar == null) {
                throw new ClassNotFoundException("Hive JDBC driver not found. Set HIVE_JDBC_STANDALONE_JAR to hive-jdbc-*-standalone.jar.");
            }
            URLClassLoader loader = new URLClassLoader(new URL[]{jar.toURI().toURL()}, JdbcConnectionFactory.class.getClassLoader());
            Class<?> driverClass = Class.forName(HIVE_DRIVER_CLASS, true, loader);
            hiveDriver = (Driver) driverClass.newInstance();
            log.info("Loaded Hive JDBC driver from {}", jar.getAbsolutePath());
            return hiveDriver;
        }
    }

    private static File findHiveStandaloneJar() {
        List<String> candidates = new ArrayList<>();
        addCandidate(candidates, System.getProperty("hive.jdbc.standalone.jar"));
        addCandidate(candidates, System.getenv("HIVE_JDBC_STANDALONE_JAR"));
        addCandidate(candidates, "E:/MES/.m2/org/apache/hive/hive-jdbc/3.1.3/hive-jdbc-3.1.3-standalone.jar");
        addCandidate(candidates, "../.m2/org/apache/hive/hive-jdbc/3.1.3/hive-jdbc-3.1.3-standalone.jar");
        addCandidate(candidates, "../../.m2/org/apache/hive/hive-jdbc/3.1.3/hive-jdbc-3.1.3-standalone.jar");

        for (String candidate : candidates) {
            File file = new File(candidate);
            if (file.isFile() && file.length() > 0) {
                return file;
            }
        }
        return null;
    }

    private static void addCandidate(List<String> candidates, String candidate) {
        if (candidate != null && !candidate.trim().isEmpty()) {
            candidates.add(candidate);
        }
    }
}
