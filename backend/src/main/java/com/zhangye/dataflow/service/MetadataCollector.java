package com.zhangye.dataflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhangye.dataflow.entity.*;
import com.zhangye.dataflow.mapper.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Pattern;

@Service
public class MetadataCollector {

    private static final Logger log = LoggerFactory.getLogger(MetadataCollector.class);

    private final AssetTableMapper tableMapper;
    private final AssetFieldMapper fieldMapper;
    private final AssetLineageMapper lineageMapper;
    private final MetaCollectorLogMapper logMapper;
    private final MetaCollectorTaskMapper taskMapper;
    private final AggDatasourceMapper datasourceMapper;

    public MetadataCollector(AssetTableMapper tableMapper, AssetFieldMapper fieldMapper,
                              AssetLineageMapper lineageMapper, MetaCollectorLogMapper logMapper,
                              MetaCollectorTaskMapper taskMapper, AggDatasourceMapper datasourceMapper) {
        this.tableMapper = tableMapper;
        this.fieldMapper = fieldMapper;
        this.lineageMapper = lineageMapper;
        this.logMapper = logMapper;
        this.taskMapper = taskMapper;
        this.datasourceMapper = datasourceMapper;
    }

    public MetaCollectorLog runCollect(MetaCollectorTask task) {
        MetaCollectorLog execLog = new MetaCollectorLog();
        execLog.setTaskId(task.getId());
        execLog.setCreateTime(LocalDateTime.now());
        long start = System.currentTimeMillis();

        try {
            AggDatasource ds = datasourceMapper.selectById(task.getDatasourceId());
            if (ds == null) {
                execLog.setStatus("FAILED");
                execLog.setErrorLog("数据源不存在");
                logMapper.insert(execLog);
                return execLog;
            }

            String dbType = ds.getDbType() != null ? ds.getDbType().toUpperCase() : "MYSQL";
            int tableCount = 0;
            int fieldCount = 0;

            switch (dbType) {
                case "MYSQL":
                    int[] mysqlResult = collectFromMysql(ds, task.getTargetPattern(), task.getTenantId());
                    tableCount = mysqlResult[0];
                    fieldCount = mysqlResult[1];
                    break;
                case "HIVE":
                    int[] hiveResult = collectFromHive(ds, task.getTargetPattern(), task.getTenantId());
                    tableCount = hiveResult[0];
                    fieldCount = hiveResult[1];
                    break;
                default:
                    execLog.setStatus("FAILED");
                    execLog.setErrorLog("不支持的数据源类型: " + dbType);
                    logMapper.insert(execLog);
                    return execLog;
            }

            execLog.setStatus("SUCCESS");
            execLog.setTableCount(tableCount);
            execLog.setFieldCount(fieldCount);
        } catch (Exception e) {
            log.error("Metadata collect failed for task {}", task.getId(), e);
            execLog.setStatus("FAILED");
            execLog.setErrorLog(e.getMessage());
        } finally {
            execLog.setDurationMs(System.currentTimeMillis() - start);
            logMapper.insert(execLog);
            task.setLastRunTime(LocalDateTime.now());
            task.setUpdateTime(LocalDateTime.now());
            taskMapper.updateById(task);
        }

        return execLog;
    }

    private int[] collectFromMysql(AggDatasource ds, String pattern, Long tenantId) {
        int tableCount = 0;
        int fieldCount = 0;

        try {
            // Connect via JDBC
            String url = "jdbc:mysql://" + ds.getHost() + ":" + (ds.getPort() != null ? ds.getPort() : 3306)
                    + "/" + ds.getDatabaseName() + "?useSSL=false&serverTimezone=Asia/Shanghai";
            java.sql.Connection conn = java.sql.DriverManager.getConnection(url, ds.getUsername(), ds.getPassword());

            // Get tables
            java.sql.Statement stmt = conn.createStatement();
            String tableSql = "SELECT TABLE_NAME, TABLE_COMMENT FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA='" + ds.getDatabaseName() + "'";
            java.sql.ResultSet tablesRs = stmt.executeQuery(tableSql);

            Pattern patternObj = (pattern != null && !pattern.isEmpty()) ? Pattern.compile(pattern) : null;
            List<String[]> tableInfos = new ArrayList<>();

            while (tablesRs.next()) {
                String tableName = tablesRs.getString("TABLE_NAME");
                if (patternObj != null && !patternObj.matcher(tableName).matches()) continue;
                String tableComment = tablesRs.getString("TABLE_COMMENT");
                tableInfos.add(new String[]{tableName, tableComment});
            }
            tablesRs.close();

            for (String[] tableInfo : tableInfos) {
                String tableName = tableInfo[0];
                String tableComment = tableInfo[1];

                // Upsert asset_table
                AssetTable table = tableMapper.selectOne(new LambdaQueryWrapper<AssetTable>()
                        .eq(AssetTable::getTableEn, tableName)
                        .eq(AssetTable::getDatasourceId, ds.getId()));
                if (table == null) {
                    table = new AssetTable();
                    table.setTenantId(tenantId);
                    table.setTableEn(tableName);
                    table.setTableCn(tableComment);
                    table.setDatabaseName(ds.getDatabaseName());
                    table.setDatasourceId(ds.getId());
                    table.setLayerCode("ODS");
                    table.setCreateTime(LocalDateTime.now());
                    table.setUpdateTime(LocalDateTime.now());
                    tableMapper.insert(table);
                } else {
                    table.setTableCn(tableComment);
                    table.setUpdateTime(LocalDateTime.now());
                    tableMapper.updateById(table);
                }

                // Clear old fields for this table and re-collect
                fieldMapper.delete(new LambdaQueryWrapper<AssetField>().eq(AssetField::getTableId, table.getId()));

                // Get columns
                String colSql = "SELECT COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE, COLUMN_DEFAULT, COLUMN_COMMENT " +
                        "FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA='" + ds.getDatabaseName() +
                        "' AND TABLE_NAME='" + tableName + "' ORDER BY ORDINAL_POSITION";
                java.sql.ResultSet colsRs = stmt.executeQuery(colSql);

                long rowCount = 0;
                try {
                    java.sql.Statement countStmt = conn.createStatement();
                    java.sql.ResultSet countRs = countStmt.executeQuery("SELECT COUNT(*) FROM `" +
                            ds.getDatabaseName() + "`.`" + tableName + "`");
                    if (countRs.next()) rowCount = countRs.getLong(1);
                    countRs.close();
                    countStmt.close();
                } catch (Exception ignored) {}

                while (colsRs.next()) {
                    AssetField field = new AssetField();
                    field.setTableId(table.getId());
                    field.setFieldEn(colsRs.getString("COLUMN_NAME"));
                    field.setFieldType(colsRs.getString("COLUMN_TYPE"));
                    field.setFieldCn(colsRs.getString("COLUMN_COMMENT"));
                    field.setDescription(colsRs.getString("COLUMN_COMMENT"));
                    field.setIsSensitive(detectSensitive(colsRs.getString("COLUMN_NAME")));
                    field.setCreateTime(LocalDateTime.now());
                    fieldMapper.insert(field);
                    fieldCount++;
                }
                colsRs.close();

                table.setRowCount(rowCount);
                table.setUpdateTime(LocalDateTime.now());
                tableMapper.updateById(table);
                tableCount++;
            }

            // Auto-lineage from MySQL views
            collectMysqlViewLineage(conn, ds, tenantId);

            stmt.close();
            conn.close();
        } catch (Exception e) {
            log.error("MySQL metadata collect failed", e);
            throw new RuntimeException("MySQL metadata collect failed: " + e.getMessage(), e);
        }

        return new int[]{tableCount, fieldCount};
    }

    private void collectMysqlViewLineage(java.sql.Connection conn, AggDatasource ds, Long tenantId) throws Exception {
        String viewSql = "SELECT TABLE_NAME, VIEW_DEFINITION FROM INFORMATION_SCHEMA.VIEWS WHERE TABLE_SCHEMA='" + ds.getDatabaseName() + "'";
        java.sql.Statement stmt = conn.createStatement();
        java.sql.ResultSet rs = stmt.executeQuery(viewSql);

        while (rs.next()) {
            String viewName = rs.getString("TABLE_NAME");
            String viewDef = rs.getString("VIEW_DEFINITION");
            if (viewDef == null) continue;

            // Parse FROM/JOIN clauses to find referenced tables
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("(?:FROM|JOIN)\\s+`?(\\w+)`?\\s*\\.?\\s*`?(\\w+)?`?",
                    java.util.regex.Pattern.CASE_INSENSITIVE).matcher(viewDef);
            while (m.find()) {
                String schema = m.group(1);
                String refTable = m.group(2) != null ? m.group(2) : m.group(1);

                AssetTable sourceTable = tableMapper.selectOne(new LambdaQueryWrapper<AssetTable>()
                        .eq(AssetTable::getTableEn, refTable)
                        .eq(AssetTable::getDatasourceId, ds.getId()));
                AssetTable targetTable = tableMapper.selectOne(new LambdaQueryWrapper<AssetTable>()
                        .eq(AssetTable::getTableEn, viewName)
                        .eq(AssetTable::getDatasourceId, ds.getId()));

                if (sourceTable != null && targetTable != null) {
                    long count = lineageMapper.selectCount(new LambdaQueryWrapper<AssetLineage>()
                            .eq(AssetLineage::getSourceTableId, sourceTable.getId())
                            .eq(AssetLineage::getTargetTableId, targetTable.getId()));
                    if (count == 0) {
                        AssetLineage lineage = new AssetLineage();
                        lineage.setTenantId(tenantId);
                        lineage.setSourceTableId(sourceTable.getId());
                        lineage.setTargetTableId(targetTable.getId());
                        lineage.setRelationType("VIEW");
                        lineage.setCreateTime(LocalDateTime.now());
                        lineageMapper.insert(lineage);
                    }
                }
            }
        }
        rs.close();
        stmt.close();
    }

    private int[] collectFromHive(AggDatasource ds, String pattern, Long tenantId) {
        // Simulated Hive collection - in production, use Hive JDBC
        int tableCount = 0;
        int fieldCount = 0;
        log.info("Hive metadata collection from {}:{} — simulated mode", ds.getHost(), ds.getPort());
        return new int[]{tableCount, fieldCount};
    }

    private Integer detectSensitive(String colName) {
        if (colName == null) return 0;
        String lower = colName.toLowerCase();
        String[] sensitive = {"mobile", "phone", "id_card", "idcard", "name", "address",
                "password", "passwd", "secret", "email", "card_no", "bank"};
        for (String s : sensitive) {
            if (lower.contains(s)) return 1;
        }
        return 0;
    }
}
