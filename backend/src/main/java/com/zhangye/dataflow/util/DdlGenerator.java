package com.zhangye.dataflow.util;

import com.zhangye.dataflow.entity.AssetField;
import com.zhangye.dataflow.entity.GovModelTable;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
public class DdlGenerator {

    public String generateHiveDdl(GovModelTable table, List<AssetField> fields) {
        StringBuilder sb = new StringBuilder();
        sb.append("-- Hive DDL for ").append(table.getTableEn()).append("\n");
        sb.append("CREATE TABLE IF NOT EXISTS ").append(table.getTableEn()).append(" (\n");

        String columns = fields.stream()
                .map(f -> "  " + f.getFieldEn() + " " + toHiveType(f.getFieldType()) + " COMMENT '" + nvl(f.getFieldCn()) + "'")
                .collect(Collectors.joining(",\n"));
        sb.append(columns).append("\n)");
        if (table.getTableCn() != null && !table.getTableCn().isEmpty()) {
            sb.append(" COMMENT '").append(table.getTableCn()).append("'");
        }
        sb.append("\n");
        sb.append("PARTITIONED BY (dt STRING COMMENT '分区日期')\n");
        sb.append("STORED AS PARQUET\n");
        sb.append("LOCATION '/user/hive/warehouse/").append(table.getTableEn()).append("';\n");
        return sb.toString();
    }

    public String generateMysqlDdl(GovModelTable table, List<AssetField> fields) {
        StringBuilder sb = new StringBuilder();
        sb.append("-- MySQL DDL for ").append(table.getTableEn()).append("\n");
        sb.append("CREATE TABLE IF NOT EXISTS `").append(table.getTableEn()).append("` (\n");

        // PK detection: field_en starts with 'id' is the convention
        String columns = fields.stream()
                .map(f -> {
                    String colDef = "  `" + f.getFieldEn() + "` " + toMysqlType(f.getFieldType());
                    if ("id".equals(f.getFieldEn())) {
                        colDef += " NOT NULL AUTO_INCREMENT";
                    }
                    if (f.getFieldCn() != null && !f.getFieldCn().isEmpty()) {
                        colDef += " COMMENT '" + f.getFieldCn() + "'";
                    }
                    return colDef;
                })
                .collect(Collectors.joining(",\n"));
        sb.append(columns);

        // Primary key
        boolean hasId = fields.stream().anyMatch(f -> "id".equals(f.getFieldEn()));
        if (hasId) {
            sb.append(",\n  PRIMARY KEY (`id`)");
        }

        sb.append("\n) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
        if (table.getTableCn() != null && !table.getTableCn().isEmpty()) {
            sb.append(" COMMENT='").append(table.getTableCn()).append("'");
        }
        sb.append(";\n");
        return sb.toString();
    }

    public String generateSparkDdl(GovModelTable table, List<AssetField> fields) {
        StringBuilder sb = new StringBuilder();
        sb.append("-- Spark SQL DDL for ").append(table.getTableEn()).append("\n");
        sb.append("CREATE TABLE IF NOT EXISTS ").append(table.getTableEn()).append(" (\n");

        String columns = fields.stream()
                .map(f -> "  " + f.getFieldEn() + " " + toSparkType(f.getFieldType()) + " COMMENT '" + nvl(f.getFieldCn()) + "'")
                .collect(Collectors.joining(",\n"));
        sb.append(columns).append("\n)");
        if (table.getTableCn() != null && !table.getTableCn().isEmpty()) {
            sb.append(" COMMENT '").append(table.getTableCn()).append("'");
        }
        sb.append("\n");
        sb.append("USING PARQUET\n");
        sb.append("PARTITIONED BY (dt)\n");
        sb.append("LOCATION '/warehouse/").append(table.getTableEn()).append("';\n");
        return sb.toString();
    }

    private String toHiveType(String type) {
        if (type == null) return "STRING";
        switch (type.toUpperCase()) {
            case "STRING": case "VARCHAR": case "TEXT": case "CHAR": return "STRING";
            case "INT": case "INTEGER": return "INT";
            case "BIGINT": return "BIGINT";
            case "DOUBLE": case "FLOAT": return "DOUBLE";
            case "DECIMAL": return "DECIMAL(18,6)";
            case "DATE": return "DATE";
            case "TIMESTAMP": case "DATETIME": return "TIMESTAMP";
            case "BOOLEAN": return "BOOLEAN";
            default: return "STRING";
        }
    }

    private String toMysqlType(String type) {
        if (type == null) return "VARCHAR(255)";
        switch (type.toUpperCase()) {
            case "STRING": case "VARCHAR": return "VARCHAR(255)";
            case "TEXT": return "TEXT";
            case "CHAR": return "CHAR(64)";
            case "INT": case "INTEGER": return "INT";
            case "BIGINT": return "BIGINT";
            case "DOUBLE": return "DOUBLE";
            case "FLOAT": return "FLOAT";
            case "DECIMAL": return "DECIMAL(18,6)";
            case "DATE": return "DATE";
            case "TIMESTAMP": case "DATETIME": return "DATETIME";
            case "BOOLEAN": return "TINYINT(1)";
            default: return "VARCHAR(255)";
        }
    }

    private String toSparkType(String type) {
        if (type == null) return "STRING";
        switch (type.toUpperCase()) {
            case "STRING": case "VARCHAR": case "TEXT": case "CHAR": return "STRING";
            case "INT": case "INTEGER": return "INT";
            case "BIGINT": return "BIGINT";
            case "DOUBLE": return "DOUBLE";
            case "FLOAT": return "FLOAT";
            case "DECIMAL": return "DECIMAL(18,6)";
            case "DATE": return "DATE";
            case "TIMESTAMP": case "DATETIME": return "TIMESTAMP";
            case "BOOLEAN": return "BOOLEAN";
            default: return "STRING";
        }
    }

    private String nvl(String s) {
        return s == null ? "" : s;
    }
}
