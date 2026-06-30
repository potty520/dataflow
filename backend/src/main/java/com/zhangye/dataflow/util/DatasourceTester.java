package com.zhangye.dataflow.util;

import com.zhangye.dataflow.entity.AggDatasource;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.util.HashMap;
import java.util.Map;

@Component
public class DatasourceTester {

    public Map<String, Object> testConnection(AggDatasource ds, String plainPwd) {
        Map<String, Object> result = new HashMap<>();
        long start = System.currentTimeMillis();
        try {
            String url = buildJdbcUrl(ds);
            try (Connection conn = DriverManager.getConnection(url, ds.getUsername(), plainPwd)) {
                DatabaseMetaData meta = conn.getMetaData();
                result.put("success", true);
                result.put("engineVersion", meta.getDatabaseProductVersion());
                result.put("driverVersion", meta.getDriverVersion());
                result.put("responseTimeMs", System.currentTimeMillis() - start);
            }
        } catch (Exception e) {
            result.put("success", false);
            result.put("errorDetail", e.getMessage());
            result.put("responseTimeMs", System.currentTimeMillis() - start);
        }
        return result;
    }

    private String buildJdbcUrl(AggDatasource ds) {
        String type = ds.getDbType().toUpperCase();
        String host = ds.getHost();
        String port = String.valueOf(ds.getPort());
        String db = ds.getDatabaseName();

        switch (type) {
            case "MYSQL":
                return "jdbc:mysql://" + host + ":" + port + "/" + db + "?useSSL=false&serverTimezone=Asia/Shanghai";
            case "POSTGRESQL":
            case "PG":
                return "jdbc:postgresql://" + host + ":" + port + "/" + db;
            case "ORACLE":
                return "jdbc:oracle:thin:@" + host + ":" + port + ":" + db;
            case "SQLSERVER":
                return "jdbc:sqlserver://" + host + ":" + port + ";databaseName=" + db;
            case "HIVE":
                return "jdbc:hive2://" + host + ":" + port + "/" + db;
            default:
                return "jdbc:mysql://" + host + ":" + port + "/" + db;
        }
    }

    public Map<String, Object> getTableFields(AggDatasource ds, String plainPwd, String tableName) {
        Map<String, Object> result = new HashMap<>();
        result.put("tableName", tableName);
        try {
            String url = buildJdbcUrl(ds);
            try (Connection conn = DriverManager.getConnection(url, ds.getUsername(), plainPwd)) {
                DatabaseMetaData meta = conn.getMetaData();
                java.util.List<Map<String, String>> fields = new java.util.ArrayList<>();
                try (java.sql.ResultSet rs = meta.getColumns(null, null, tableName, null)) {
                    while (rs.next()) {
                        Map<String, String> field = new HashMap<>();
                        field.put("name", rs.getString("COLUMN_NAME"));
                        field.put("type", rs.getString("TYPE_NAME"));
                        field.put("size", String.valueOf(rs.getInt("COLUMN_SIZE")));
                        field.put("nullable", rs.getInt("NULLABLE") == 1 ? "YES" : "NO");
                        field.put("comment", rs.getString("REMARKS"));
                        fields.add(field);
                    }
                }
                result.put("fields", fields);
                result.put("count", fields.size());
            }
        } catch (Exception e) {
            result.put("error", e.getMessage());
        }
        return result;
    }
}
