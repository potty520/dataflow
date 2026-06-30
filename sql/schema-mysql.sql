-- MySQL 版本数据库脚本（与 schema-h2.sql 结构一致，语法适配 MySQL）

CREATE DATABASE IF NOT EXISTS dataflow DEFAULT CHARACTER SET utf8mb4;
USE dataflow;

-- 复制 backend/src/main/resources/schema-h2.sql 内容并替换：
-- CLOB -> TEXT
-- TINYINT -> TINYINT
-- TIMESTAMP DEFAULT CURRENT_TIMESTAMP -> DATETIME DEFAULT CURRENT_TIMESTAMP
-- 移除 H2 特有语法

SOURCE schema-h2.sql;
