-- =============================================================================
-- DGP 数据治理平台 · 数据结构（MySQL 8）
-- 生成时间：2026-10-21　数据库：MySQL 8.0（utf8mb4 / InnoDB）
-- 来源：运行实例 mysqldump，已剔除运行时日志与访问 IP、已排除演示明细数据
-- 配套文件：sql/schema-mysql.sql（结构）、sql/init-data-mysql.sql（初始化数据）
--
-- 导入方式：
--   mysql -u root -p -e "CREATE DATABASE dgp DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci"
--   mysql -u root -p dgp < sql/schema-mysql.sql
--   mysql -u root -p dgp < sql/init-data-mysql.sql
-- 演示账号：admin / zhangsan / liuqin / wangwu / secadmin / consultant，口令均为 Dgp@2026（仅演示用）
-- =============================================================================

-- MySQL dump 10.13  Distrib 8.0.46, for Linux (x86_64)
--
-- Host: localhost    Database: dgp
-- ------------------------------------------------------
-- Server version	8.0.46-0ubuntu0.22.04.4

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!50503 SET NAMES utf8mb4 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;

--
-- Table structure for table `ads_sales_dashboard`
--

DROP TABLE IF EXISTS `ads_sales_dashboard`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ads_sales_dashboard` (
  `stat_date` date NOT NULL,
  `total_amount` decimal(14,2) DEFAULT NULL,
  `order_cnt` int DEFAULT NULL,
  `customer_cnt` int DEFAULT NULL,
  `avg_order_amount` decimal(12,2) DEFAULT NULL,
  `refund_rate` decimal(8,4) DEFAULT NULL,
  PRIMARY KEY (`stat_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `ast_catalog`
--

DROP TABLE IF EXISTS `ast_catalog`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ast_catalog` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint DEFAULT '1',
  `table_id` bigint DEFAULT NULL,
  `table_name` varchar(128) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `asset_name` varchar(128) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `asset_type` varchar(16) COLLATE utf8mb4_general_ci DEFAULT 'TABLE',
  `category` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `domain` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `business_desc` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `cover_scope` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `update_cycle` varchar(32) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `owner` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `publish_status` varchar(16) COLLATE utf8mb4_general_ci DEFAULT 'DRAFT',
  `favorite_cnt` int DEFAULT '0',
  `view_cnt` int DEFAULT '0',
  `value_score` decimal(6,2) DEFAULT '0.00',
  `sensitivity_level` varchar(8) COLLATE utf8mb4_general_ci DEFAULT 'L1',
  `create_time` datetime DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=14 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `ast_category`
--

DROP TABLE IF EXISTS `ast_category`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ast_category` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `parent_id` bigint DEFAULT '0',
  `code` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `name` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `sort_no` int DEFAULT '0',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=9 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `ast_map_snapshot`
--

DROP TABLE IF EXISTS `ast_map_snapshot`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ast_map_snapshot` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `stat_date` date DEFAULT NULL,
  `total_tables` int DEFAULT NULL,
  `total_rows` bigint DEFAULT NULL,
  `total_size_mb` decimal(14,2) DEFAULT NULL,
  `ds_cnt` int DEFAULT NULL,
  `domain_json` text COLLATE utf8mb4_general_ci,
  `layer_json` text COLLATE utf8mb4_general_ci,
  `freq_json` text COLLATE utf8mb4_general_ci,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `ast_metric_daily`
--

DROP TABLE IF EXISTS `ast_metric_daily`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ast_metric_daily` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `stat_date` date DEFAULT NULL,
  `table_cnt` int DEFAULT NULL,
  `storage_mb` decimal(14,2) DEFAULT NULL,
  `access_cnt` bigint DEFAULT NULL,
  `svc_call_cnt` bigint DEFAULT NULL,
  `query_cnt` bigint DEFAULT NULL,
  `active_users` int DEFAULT NULL,
  `asset_value` decimal(14,2) DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=31 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `ast_value_score`
--

DROP TABLE IF EXISTS `ast_value_score`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ast_value_score` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `table_name` varchar(128) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `score` decimal(6,2) DEFAULT NULL,
  `dim_json` text COLLATE utf8mb4_general_ci,
  `calc_time` datetime DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=14 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `ast_visit_log`
--

DROP TABLE IF EXISTS `ast_visit_log`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ast_visit_log` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `visit_time` datetime DEFAULT NULL,
  `username` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `asset_id` bigint DEFAULT NULL,
  `asset_name` varchar(128) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `action` varchar(32) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `ip` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=185 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `dwd_customer_dim`
--

DROP TABLE IF EXISTS `dwd_customer_dim`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `dwd_customer_dim` (
  `customer_key` varchar(64) COLLATE utf8mb4_general_ci NOT NULL,
  `customer_id` varchar(32) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `cust_name` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `gender` varchar(8) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `age` int DEFAULT NULL,
  `phone` varchar(32) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `email` varchar(128) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `city` varchar(32) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `reg_date` date DEFAULT NULL,
  `member_level` varchar(16) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `is_valid` int DEFAULT NULL,
  PRIMARY KEY (`customer_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `dwd_device_fact`
--

DROP TABLE IF EXISTS `dwd_device_fact`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `dwd_device_fact` (
  `device_key` varchar(64) COLLATE utf8mb4_general_ci NOT NULL,
  `device_id` varchar(32) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `workshop` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `metric_name` varchar(32) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `metric_value` decimal(12,3) DEFAULT NULL,
  `collect_date` date DEFAULT NULL,
  PRIMARY KEY (`device_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `dwd_order_fact`
--

DROP TABLE IF EXISTS `dwd_order_fact`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `dwd_order_fact` (
  `order_key` varchar(64) COLLATE utf8mb4_general_ci NOT NULL,
  `order_id` varchar(32) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `customer_id` varchar(32) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `product_id` varchar(32) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `amount` decimal(12,2) DEFAULT NULL,
  `quantity` int DEFAULT NULL,
  `order_date` date DEFAULT NULL,
  `pay_date` date DEFAULT NULL,
  `status` varchar(16) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `region` varchar(32) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `channel` varchar(32) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `is_refund` int DEFAULT NULL,
  PRIMARY KEY (`order_key`),
  KEY `idx_dwd_order` (`order_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `dws_customer_value`
--

DROP TABLE IF EXISTS `dws_customer_value`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `dws_customer_value` (
  `customer_id` varchar(32) COLLATE utf8mb4_general_ci NOT NULL,
  `total_amount` decimal(14,2) DEFAULT NULL,
  `order_cnt` int DEFAULT NULL,
  `last_order_date` date DEFAULT NULL,
  `value_level` varchar(16) COLLATE utf8mb4_general_ci DEFAULT NULL,
  PRIMARY KEY (`customer_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `dws_order_summary`
--

DROP TABLE IF EXISTS `dws_order_summary`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `dws_order_summary` (
  `stat_date` date NOT NULL,
  `region` varchar(32) COLLATE utf8mb4_general_ci NOT NULL,
  `channel` varchar(32) COLLATE utf8mb4_general_ci NOT NULL,
  `order_cnt` int DEFAULT NULL,
  `amount` decimal(14,2) DEFAULT NULL,
  `refund_amt` decimal(14,2) DEFAULT NULL,
  PRIMARY KEY (`stat_date`,`region`,`channel`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `dws_region_sales`
--

DROP TABLE IF EXISTS `dws_region_sales`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `dws_region_sales` (
  `stat_date` date NOT NULL,
  `region` varchar(32) COLLATE utf8mb4_general_ci NOT NULL,
  `sales_amount` decimal(14,2) DEFAULT NULL,
  `target_amount` decimal(14,2) DEFAULT NULL,
  `completion_rate` decimal(8,4) DEFAULT NULL,
  PRIMARY KEY (`stat_date`,`region`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `ind_enable_scope`
--

DROP TABLE IF EXISTS `ind_enable_scope`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ind_enable_scope` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `package_code` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `tenant_id` bigint DEFAULT NULL,
  `status` varchar(16) COLLATE utf8mb4_general_ci DEFAULT 'ENABLED',
  `enable_time` datetime DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=8 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `ind_industry`
--

DROP TABLE IF EXISTS `ind_industry`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ind_industry` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `code` varchar(32) COLLATE utf8mb4_general_ci NOT NULL,
  `name` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `description` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `code` (`code`)
) ENGINE=InnoDB AUTO_INCREMENT=6 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `ind_package`
--

DROP TABLE IF EXISTS `ind_package`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ind_package` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint DEFAULT '1',
  `package_code` varchar(64) COLLATE utf8mb4_general_ci NOT NULL,
  `package_name` varchar(128) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `industry_code` varchar(32) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `industry_name` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `version` varchar(32) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `status` varchar(16) COLLATE utf8mb4_general_ci DEFAULT 'INSTALLED',
  `min_platform_version` varchar(32) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `max_platform_version` varchar(32) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `description` varchar(1000) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `vendor` varchar(128) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `manifest` text COLLATE utf8mb4_general_ci,
  `item_cnt` int DEFAULT '0',
  `applied` int DEFAULT '0',
  `create_time` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `package_code` (`package_code`)
) ENGINE=InnoDB AUTO_INCREMENT=6 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `ind_package_item`
--

DROP TABLE IF EXISTS `ind_package_item`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ind_package_item` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `package_id` bigint DEFAULT NULL,
  `item_type` varchar(32) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `code` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `name` varchar(128) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `version` varchar(32) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `content` text COLLATE utf8mb4_general_ci,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=36 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `ind_package_version`
--

DROP TABLE IF EXISTS `ind_package_version`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ind_package_version` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `package_code` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `version` varchar(32) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `manifest` text COLLATE utf8mb4_general_ci,
  `operator` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `create_time` datetime DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=6 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `int_job`
--

DROP TABLE IF EXISTS `int_job`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `int_job` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint DEFAULT '1',
  `code` varchar(64) COLLATE utf8mb4_general_ci NOT NULL,
  `name` varchar(128) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `cn_name` varchar(128) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `job_type` varchar(16) COLLATE utf8mb4_general_ci DEFAULT 'ETL',
  `engine` varchar(16) COLLATE utf8mb4_general_ci DEFAULT 'BATCH',
  `status` varchar(16) COLLATE utf8mb4_general_ci DEFAULT 'DRAFT',
  `schedule_type` varchar(16) COLLATE utf8mb4_general_ci DEFAULT 'MANUAL',
  `cron` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `interval_min` int DEFAULT NULL,
  `priority` int DEFAULT '5',
  `timeout_min` int DEFAULT '60',
  `retry` int DEFAULT '0',
  `industry` varchar(32) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `dsl` text COLLATE utf8mb4_general_ci,
  `script_lang` varchar(16) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `script` text COLLATE utf8mb4_general_ci,
  `owner` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `description` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `version` varchar(32) COLLATE utf8mb4_general_ci DEFAULT '1.0.0',
  `last_run_time` datetime DEFAULT NULL,
  `next_run_time` datetime DEFAULT NULL,
  `success_count` int DEFAULT '0',
  `fail_count` int DEFAULT '0',
  `create_time` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `code` (`code`)
) ENGINE=InnoDB AUTO_INCREMENT=16 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `int_job_instance`
--

DROP TABLE IF EXISTS `int_job_instance`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `int_job_instance` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `job_id` bigint DEFAULT NULL,
  `job_name` varchar(128) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `status` varchar(16) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `trigger_type` varchar(16) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `start_time` datetime DEFAULT NULL,
  `end_time` datetime DEFAULT NULL,
  `duration_ms` bigint DEFAULT NULL,
  `rows_written` bigint DEFAULT '0',
  `message` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `operator` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `trace_id` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_int_inst_job` (`job_id`)
) ENGINE=InnoDB AUTO_INCREMENT=117 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `int_job_log`
--

DROP TABLE IF EXISTS `int_job_log`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `int_job_log` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `instance_id` bigint DEFAULT NULL,
  `log_time` datetime DEFAULT NULL,
  `log_level` varchar(8) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `message` varchar(1000) COLLATE utf8mb4_general_ci DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=233 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `int_sandbox_policy`
--

DROP TABLE IF EXISTS `int_sandbox_policy`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `int_sandbox_policy` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(128) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `allow_sql` int DEFAULT '1',
  `allow_python` int DEFAULT '0',
  `allow_shell` int DEFAULT '0',
  `max_runtime_min` int DEFAULT '30',
  `max_memory_mb` int DEFAULT '1024',
  `network_whitelist` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `status` varchar(16) COLLATE utf8mb4_general_ci DEFAULT 'ENABLED',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `md_biz_term`
--

DROP TABLE IF EXISTS `md_biz_term`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `md_biz_term` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint DEFAULT '1',
  `industry` varchar(32) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `term_code` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `term_name` varchar(128) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `term_name_en` varchar(128) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `definition` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `column_pattern` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `std_element_code` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `synonyms` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `security_level` varchar(8) COLLATE utf8mb4_general_ci DEFAULT 'L1',
  `source` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=21 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `md_collect_instance`
--

DROP TABLE IF EXISTS `md_collect_instance`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `md_collect_instance` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `task_id` bigint DEFAULT NULL,
  `datasource_id` bigint DEFAULT NULL,
  `ds_name` varchar(128) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `status` varchar(16) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `start_time` datetime DEFAULT NULL,
  `end_time` datetime DEFAULT NULL,
  `duration_ms` bigint DEFAULT NULL,
  `new_tables` int DEFAULT '0',
  `changed_cols` int DEFAULT '0',
  `message` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `operator` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=8 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `md_collect_task`
--

DROP TABLE IF EXISTS `md_collect_task`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `md_collect_task` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint DEFAULT '1',
  `datasource_id` bigint DEFAULT NULL,
  `name` varchar(128) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `mode` varchar(16) COLLATE utf8mb4_general_ci DEFAULT 'AUTO',
  `cron` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `status` varchar(16) COLLATE utf8mb4_general_ci DEFAULT 'ENABLED',
  `last_run_time` datetime DEFAULT NULL,
  `next_run_time` datetime DEFAULT NULL,
  `new_tables` int DEFAULT '0',
  `changed_cols` int DEFAULT '0',
  `owner` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `create_time` datetime DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `md_column`
--

DROP TABLE IF EXISTS `md_column`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `md_column` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `table_id` bigint NOT NULL,
  `name` varchar(128) COLLATE utf8mb4_general_ci NOT NULL,
  `cn_name` varchar(128) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `data_type` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `data_len` varchar(32) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `nullable` int DEFAULT '1',
  `is_pk` int DEFAULT '0',
  `column_order` int DEFAULT '0',
  `comment` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `std_code` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `sensitive_level` varchar(8) COLLATE utf8mb4_general_ci DEFAULT 'L1',
  `mask_algo` varchar(32) COLLATE utf8mb4_general_ci DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `ux_md_col_name` (`table_id`,`name`),
  KEY `idx_md_col_table` (`table_id`)
) ENGINE=InnoDB AUTO_INCREMENT=112 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `md_datasource`
--

DROP TABLE IF EXISTS `md_datasource`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `md_datasource` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint DEFAULT '1',
  `code` varchar(64) COLLATE utf8mb4_general_ci NOT NULL,
  `name` varchar(128) COLLATE utf8mb4_general_ci NOT NULL,
  `dtype` varchar(32) COLLATE utf8mb4_general_ci NOT NULL,
  `host` varchar(128) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `port` varchar(16) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `db_name` varchar(128) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `username` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `password_enc` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `industry` varchar(32) COLLATE utf8mb4_general_ci DEFAULT 'GOV',
  `domain` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `owner` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `conn_status` varchar(16) COLLATE utf8mb4_general_ci DEFAULT '未测试',
  `last_test_time` datetime DEFAULT NULL,
  `latency_ms` int DEFAULT NULL,
  `collect_mode` varchar(16) COLLATE utf8mb4_general_ci DEFAULT 'AUTO',
  `remark` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `create_time` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `code` (`code`)
) ENGINE=InnoDB AUTO_INCREMENT=14 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `md_lineage_edge`
--

DROP TABLE IF EXISTS `md_lineage_edge`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `md_lineage_edge` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint DEFAULT '1',
  `src_table` varchar(128) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `src_column` varchar(128) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `dst_table` varchar(128) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `dst_column` varchar(128) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `job_name` varchar(128) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `transform_type` varchar(32) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `transform_expr` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `granularity` varchar(16) COLLATE utf8mb4_general_ci DEFAULT 'TABLE',
  `create_time` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_md_lin_src` (`src_table`),
  KEY `idx_md_lin_dst` (`dst_table`),
  KEY `idx_md_lin_edge` (`src_table`,`dst_table`)
) ENGINE=InnoDB AUTO_INCREMENT=42 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `md_object_version`
--

DROP TABLE IF EXISTS `md_object_version`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `md_object_version` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `object_type` varchar(32) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `object_id` bigint DEFAULT NULL,
  `object_name` varchar(128) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `version` varchar(32) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `snapshot` text COLLATE utf8mb4_general_ci,
  `change_note` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `operator` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `create_time` datetime DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=13 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `md_table`
--

DROP TABLE IF EXISTS `md_table`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `md_table` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint DEFAULT '1',
  `datasource_id` bigint NOT NULL,
  `name` varchar(128) COLLATE utf8mb4_general_ci NOT NULL,
  `cn_name` varchar(128) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `obj_type` varchar(16) COLLATE utf8mb4_general_ci DEFAULT 'TABLE',
  `layer` varchar(16) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `domain` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `row_count` bigint DEFAULT '0',
  `data_size_mb` decimal(14,2) DEFAULT '0.00',
  `owner` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `description` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `biz_desc` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `update_freq` varchar(32) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `last_ddl_time` datetime DEFAULT NULL,
  `status` varchar(16) COLLATE utf8mb4_general_ci DEFAULT '已发布',
  `tags` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `quality_score` decimal(6,2) DEFAULT '0.00',
  `access_count` bigint DEFAULT '0',
  `svc_calls` bigint DEFAULT '0',
  `sensitive_level` varchar(8) COLLATE utf8mb4_general_ci DEFAULT 'L1',
  `industry` varchar(32) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `create_time` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `ux_md_table_name` (`name`),
  KEY `idx_md_table_ds` (`datasource_id`),
  KEY `idx_md_table_layer` (`layer`)
) ENGINE=InnoDB AUTO_INCREMENT=35 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `mdl_attribute`
--

DROP TABLE IF EXISTS `mdl_attribute`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `mdl_attribute` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `entity_id` bigint NOT NULL,
  `code` varchar(128) COLLATE utf8mb4_general_ci NOT NULL,
  `name` varchar(128) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `cn_name` varchar(128) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `data_type` varchar(32) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `data_len` varchar(32) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `is_pk` int DEFAULT '0',
  `nullable` int DEFAULT '1',
  `std_element_code` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `security_level` varchar(8) COLLATE utf8mb4_general_ci DEFAULT 'L1',
  `description` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `attr_order` int DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_mdl_attr_entity` (`entity_id`)
) ENGINE=InnoDB AUTO_INCREMENT=61 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `mdl_ddl_record`
--

DROP TABLE IF EXISTS `mdl_ddl_record`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `mdl_ddl_record` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `model_id` bigint DEFAULT NULL,
  `entity_id` bigint DEFAULT NULL,
  `table_name` varchar(128) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `ddl_text` text COLLATE utf8mb4_general_ci,
  `status` varchar(16) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `operator` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `create_time` datetime DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=13 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `mdl_entity`
--

DROP TABLE IF EXISTS `mdl_entity`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `mdl_entity` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `model_id` bigint NOT NULL,
  `code` varchar(128) COLLATE utf8mb4_general_ci NOT NULL,
  `name` varchar(128) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `cn_name` varchar(128) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `entity_type` varchar(16) COLLATE utf8mb4_general_ci DEFAULT 'DIM',
  `subject_domain` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `description` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `pos_x` decimal(10,2) DEFAULT '0.00',
  `pos_y` decimal(10,2) DEFAULT '0.00',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=20 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `mdl_model`
--

DROP TABLE IF EXISTS `mdl_model`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `mdl_model` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint DEFAULT '1',
  `code` varchar(64) COLLATE utf8mb4_general_ci NOT NULL,
  `name` varchar(128) COLLATE utf8mb4_general_ci NOT NULL,
  `cn_name` varchar(128) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `model_type` varchar(16) COLLATE utf8mb4_general_ci NOT NULL,
  `layer` varchar(16) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `industry` varchar(32) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `domain` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `status` varchar(16) COLLATE utf8mb4_general_ci DEFAULT 'DRAFT',
  `version` varchar(32) COLLATE utf8mb4_general_ci DEFAULT '1.0.0',
  `owner` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `description` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `create_time` datetime DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  `publish_time` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `code` (`code`)
) ENGINE=InnoDB AUTO_INCREMENT=14 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `mdl_publish_flow`
--

DROP TABLE IF EXISTS `mdl_publish_flow`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `mdl_publish_flow` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `model_id` bigint DEFAULT NULL,
  `model_name` varchar(128) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `version` varchar(32) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `applicant` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `auditor` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `status` varchar(16) COLLATE utf8mb4_general_ci DEFAULT 'PENDING',
  `comment` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `create_time` datetime DEFAULT NULL,
  `finish_time` datetime DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `mdl_relation`
--

DROP TABLE IF EXISTS `mdl_relation`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `mdl_relation` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `model_id` bigint DEFAULT NULL,
  `source_entity` varchar(128) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `target_entity` varchar(128) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `relation_type` varchar(16) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `join_condition` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `mdl_version`
--

DROP TABLE IF EXISTS `mdl_version`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `mdl_version` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `model_id` bigint DEFAULT NULL,
  `version` varchar(32) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `snapshot` text COLLATE utf8mb4_general_ci,
  `change_note` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `operator` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `create_time` datetime DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=8 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `ods_customer`
--

DROP TABLE IF EXISTS `ods_customer`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ods_customer` (
  `customer_id` varchar(32) COLLATE utf8mb4_general_ci NOT NULL,
  `cust_name` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `gender` varchar(8) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `birthday` date DEFAULT NULL,
  `phone` varchar(32) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `email` varchar(128) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `id_card` varchar(32) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `province` varchar(32) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `city` varchar(32) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `addr` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `reg_channel` varchar(32) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `reg_time` datetime DEFAULT NULL,
  `status` varchar(16) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `member_level` varchar(16) COLLATE utf8mb4_general_ci DEFAULT NULL,
  PRIMARY KEY (`customer_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `ods_device_metric`
--

DROP TABLE IF EXISTS `ods_device_metric`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ods_device_metric` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `device_id` varchar(32) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `device_name` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `workshop` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `metric_name` varchar(32) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `metric_value` decimal(12,3) DEFAULT NULL,
  `unit` varchar(16) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `collect_time` datetime DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=12001 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `ods_gov_population`
--

DROP TABLE IF EXISTS `ods_gov_population`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ods_gov_population` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `person_name` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `id_card` varchar(32) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `gender` varchar(8) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `birthday` date DEFAULT NULL,
  `province` varchar(32) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `city` varchar(32) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `street` varchar(128) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `phone` varchar(32) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=5001 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `ods_order`
--

DROP TABLE IF EXISTS `ods_order`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ods_order` (
  `order_id` varchar(32) COLLATE utf8mb4_general_ci NOT NULL,
  `customer_id` varchar(32) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `product_id` varchar(32) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `product_name` varchar(128) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `amount` decimal(12,2) DEFAULT NULL,
  `quantity` int DEFAULT NULL,
  `discount` decimal(6,2) DEFAULT NULL,
  `order_time` datetime DEFAULT NULL,
  `pay_time` datetime DEFAULT NULL,
  `status` varchar(16) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `channel` varchar(32) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `region` varchar(32) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `sales_rep` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  PRIMARY KEY (`order_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `ods_payment`
--

DROP TABLE IF EXISTS `ods_payment`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ods_payment` (
  `pay_id` varchar(32) COLLATE utf8mb4_general_ci NOT NULL,
  `order_id` varchar(32) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `pay_channel` varchar(32) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `pay_amount` decimal(12,2) DEFAULT NULL,
  `pay_time` datetime DEFAULT NULL,
  `trade_no` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `status` varchar(16) COLLATE utf8mb4_general_ci DEFAULT NULL,
  PRIMARY KEY (`pay_id`),
  KEY `idx_pay_order` (`order_id`,`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `ods_product`
--

DROP TABLE IF EXISTS `ods_product`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ods_product` (
  `product_id` varchar(32) COLLATE utf8mb4_general_ci NOT NULL,
  `product_name` varchar(128) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `category` varchar(32) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `price` decimal(12,2) DEFAULT NULL,
  `cost` decimal(12,2) DEFAULT NULL,
  `supplier` varchar(128) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `shelf_date` date DEFAULT NULL,
  `status` varchar(16) COLLATE utf8mb4_general_ci DEFAULT NULL,
  PRIMARY KEY (`product_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `qa_check`
--

DROP TABLE IF EXISTS `qa_check`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `qa_check` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint DEFAULT '1',
  `code` varchar(64) COLLATE utf8mb4_general_ci NOT NULL,
  `name` varchar(128) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `description` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `check_mode` varchar(16) COLLATE utf8mb4_general_ci DEFAULT 'FULL',
  `schedule_type` varchar(16) COLLATE utf8mb4_general_ci DEFAULT 'MANUAL',
  `cron` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `status` varchar(16) COLLATE utf8mb4_general_ci DEFAULT 'ENABLED',
  `owner` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `last_run_time` datetime DEFAULT NULL,
  `next_run_time` datetime DEFAULT NULL,
  `create_time` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `code` (`code`)
) ENGINE=InnoDB AUTO_INCREMENT=12 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `qa_check_instance`
--

DROP TABLE IF EXISTS `qa_check_instance`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `qa_check_instance` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `check_id` bigint DEFAULT NULL,
  `check_name` varchar(128) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `status` varchar(32) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `start_time` datetime DEFAULT NULL,
  `end_time` datetime DEFAULT NULL,
  `duration_ms` bigint DEFAULT NULL,
  `rule_total` int DEFAULT NULL,
  `rule_passed` int DEFAULT NULL,
  `score` decimal(6,2) DEFAULT NULL,
  `trigger_type` varchar(16) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `operator` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_qa_inst_check` (`check_id`)
) ENGINE=InnoDB AUTO_INCREMENT=21 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `qa_check_result`
--

DROP TABLE IF EXISTS `qa_check_result`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `qa_check_result` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `instance_id` bigint DEFAULT NULL,
  `rule_id` bigint DEFAULT NULL,
  `rule_name` varchar(128) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `table_name` varchar(128) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `column_name` varchar(128) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `dimension` varchar(16) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `total_cnt` bigint DEFAULT NULL,
  `fail_cnt` bigint DEFAULT NULL,
  `pass_rate` decimal(6,2) DEFAULT NULL,
  `score` decimal(6,2) DEFAULT NULL,
  `result_status` varchar(16) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `run_time` datetime DEFAULT NULL,
  `duration_ms` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_qa_res_inst` (`instance_id`)
) ENGINE=InnoDB AUTO_INCREMENT=104 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `qa_check_rule`
--

DROP TABLE IF EXISTS `qa_check_rule`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `qa_check_rule` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `check_id` bigint DEFAULT NULL,
  `rule_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=32 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `qa_issue`
--

DROP TABLE IF EXISTS `qa_issue`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `qa_issue` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint DEFAULT '1',
  `code` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `rule_id` bigint DEFAULT NULL,
  `rule_name` varchar(128) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `table_id` bigint DEFAULT NULL,
  `table_name` varchar(128) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `column_name` varchar(128) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `dimension` varchar(16) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `severity` varchar(8) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `assignee` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `status` varchar(16) COLLATE utf8mb4_general_ci DEFAULT 'DISCOVERED',
  `discover_time` datetime DEFAULT NULL,
  `due_time` datetime DEFAULT NULL,
  `close_time` datetime DEFAULT NULL,
  `description` varchar(1000) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `sample_data` varchar(1000) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `handle_result` varchar(1000) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `review_result` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `recur_cnt` int DEFAULT '0',
  `biz_date` date DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_qa_issue_status` (`status`),
  KEY `idx_qa_issue_assignee` (`assignee`),
  KEY `idx_qa_issue_rule` (`rule_id`)
) ENGINE=InnoDB AUTO_INCREMENT=29 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `qa_issue_flow`
--

DROP TABLE IF EXISTS `qa_issue_flow`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `qa_issue_flow` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `issue_id` bigint DEFAULT NULL,
  `from_status` varchar(16) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `to_status` varchar(16) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `operator` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `comment` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `create_time` datetime DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=66 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `qa_issue_metric_daily`
--

DROP TABLE IF EXISTS `qa_issue_metric_daily`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `qa_issue_metric_daily` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `stat_date` date DEFAULT NULL,
  `new_cnt` int DEFAULT '0',
  `closed_cnt` int DEFAULT '0',
  `open_cnt` int DEFAULT '0',
  `overdue_cnt` int DEFAULT '0',
  `closed_rate` decimal(6,2) DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=15 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `qa_report`
--

DROP TABLE IF EXISTS `qa_report`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `qa_report` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint DEFAULT '1',
  `code` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `title` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `report_type` varchar(32) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `scope` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `period` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `total_score` decimal(6,2) DEFAULT NULL,
  `score_level` varchar(16) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `context` text COLLATE utf8mb4_general_ci,
  `file_key` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `status` varchar(16) COLLATE utf8mb4_general_ci DEFAULT 'GENERATED',
  `creator` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `create_time` datetime DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=16 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `qa_rule`
--

DROP TABLE IF EXISTS `qa_rule`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `qa_rule` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint DEFAULT '1',
  `code` varchar(64) COLLATE utf8mb4_general_ci NOT NULL,
  `name` varchar(128) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `table_id` bigint DEFAULT NULL,
  `table_name` varchar(128) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `column_name` varchar(128) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `dimension` varchar(16) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `rule_type` varchar(32) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `expression` varchar(1000) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `tolerance` decimal(6,4) DEFAULT '0.0100',
  `weight` decimal(6,2) DEFAULT '1.00',
  `severity` varchar(8) COLLATE utf8mb4_general_ci DEFAULT 'MAJOR',
  `status` varchar(16) COLLATE utf8mb4_general_ci DEFAULT 'ENABLED',
  `industry` varchar(32) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `owner` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `cycle` varchar(16) COLLATE utf8mb4_general_ci DEFAULT 'DAILY',
  `check_mode` varchar(16) COLLATE utf8mb4_general_ci DEFAULT 'FULL',
  `auto_issue` int DEFAULT '1',
  `last_total` bigint DEFAULT NULL,
  `last_failed` bigint DEFAULT NULL,
  `last_pass_rate` decimal(6,2) DEFAULT NULL,
  `last_run_time` datetime DEFAULT NULL,
  `create_time` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `code` (`code`),
  KEY `idx_qa_rule_obj` (`table_name`,`column_name`)
) ENGINE=InnoDB AUTO_INCREMENT=38 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `qa_rule_template`
--

DROP TABLE IF EXISTS `qa_rule_template`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `qa_rule_template` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `code` varchar(64) COLLATE utf8mb4_general_ci NOT NULL,
  `name` varchar(128) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `industry` varchar(32) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `dimension` varchar(16) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `rule_type` varchar(32) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `expression` varchar(1000) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `applies_to` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `description` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `status` varchar(16) COLLATE utf8mb4_general_ci DEFAULT 'ENABLED',
  PRIMARY KEY (`id`),
  UNIQUE KEY `code` (`code`)
) ENGINE=InnoDB AUTO_INCREMENT=17 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `qa_score`
--

DROP TABLE IF EXISTS `qa_score`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `qa_score` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint DEFAULT '1',
  `object_type` varchar(16) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `object_id` bigint DEFAULT NULL,
  `object_name` varchar(128) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `stat_date` date DEFAULT NULL,
  `total_score` decimal(6,2) DEFAULT NULL,
  `score_level` varchar(16) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `dimension_scores` text COLLATE utf8mb4_general_ci,
  `passed_rules` int DEFAULT NULL,
  `total_rules` int DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=159 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `rpt_export_log`
--

DROP TABLE IF EXISTS `rpt_export_log`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `rpt_export_log` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `report_id` bigint DEFAULT NULL,
  `fmt` varchar(16) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `operator` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `ts` datetime DEFAULT NULL,
  `file_path` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=43 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `rpt_file`
--

DROP TABLE IF EXISTS `rpt_file`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `rpt_file` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `task_id` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `report_id` bigint DEFAULT NULL,
  `file_name` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `file_path` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `fmt` varchar(16) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `size_kb` int DEFAULT NULL,
  `create_time` datetime DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=57 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `rpt_task`
--

DROP TABLE IF EXISTS `rpt_task`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `rpt_task` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `task_id` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `template_code` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `title` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `formats` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `status` varchar(16) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `start_time` datetime DEFAULT NULL,
  `end_time` datetime DEFAULT NULL,
  `result` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `operator` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=15 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `rpt_template`
--

DROP TABLE IF EXISTS `rpt_template`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `rpt_template` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `code` varchar(64) COLLATE utf8mb4_general_ci NOT NULL,
  `name` varchar(128) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `report_type` varchar(32) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `industry` varchar(32) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `engine` varchar(32) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `content` text COLLATE utf8mb4_general_ci,
  `status` varchar(16) COLLATE utf8mb4_general_ci DEFAULT 'ENABLED',
  `create_time` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `code` (`code`)
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `sec_access_apply`
--

DROP TABLE IF EXISTS `sec_access_apply`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sec_access_apply` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint DEFAULT '1',
  `applicant` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `asset_type` varchar(16) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `asset_id` bigint DEFAULT NULL,
  `asset_name` varchar(128) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `reason` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `duration_days` int DEFAULT NULL,
  `status` varchar(16) COLLATE utf8mb4_general_ci DEFAULT 'PENDING',
  `approver` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `approve_time` datetime DEFAULT NULL,
  `create_time` datetime DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=12 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `sec_access_grant`
--

DROP TABLE IF EXISTS `sec_access_grant`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sec_access_grant` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint DEFAULT '1',
  `apply_id` bigint DEFAULT NULL,
  `subject` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `asset_name` varchar(128) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `scope` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `expire_time` datetime DEFAULT NULL,
  `status` varchar(16) COLLATE utf8mb4_general_ci DEFAULT 'ACTIVE',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=10 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `sec_alert`
--

DROP TABLE IF EXISTS `sec_alert`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sec_alert` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint DEFAULT '1',
  `alert_time` datetime DEFAULT NULL,
  `alert_type` varchar(32) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `title` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `detail` varchar(1000) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `severity` varchar(8) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `status` varchar(16) COLLATE utf8mb4_general_ci DEFAULT 'OPEN',
  `handler` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `handle_time` datetime DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `sec_alert_rule`
--

DROP TABLE IF EXISTS `sec_alert_rule`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sec_alert_rule` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(128) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `metric` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `threshold` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `window_min` int DEFAULT NULL,
  `severity` varchar(8) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `notify_channel` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `status` varchar(16) COLLATE utf8mb4_general_ci DEFAULT 'ENABLED',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `sec_audit_chain`
--

DROP TABLE IF EXISTS `sec_audit_chain`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sec_audit_chain` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `batch_no` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `start_id` bigint DEFAULT NULL,
  `end_id` bigint DEFAULT NULL,
  `chain_hash` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `verify_status` varchar(16) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `create_time` datetime DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=45 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `sec_audit_log`
--

DROP TABLE IF EXISTS `sec_audit_log`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sec_audit_log` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint DEFAULT '1',
  `log_time` datetime DEFAULT NULL,
  `username` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `action` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `module` varchar(32) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `resource` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `detail` varchar(1000) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `ip` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `result` varchar(16) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `trace_id` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `hash` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `prev_hash` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_sec_audit_time` (`log_time`),
  KEY `idx_audit_user_time` (`username`,`log_time`)
) ENGINE=InnoDB AUTO_INCREMENT=2608 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `sec_mask_policy`
--

DROP TABLE IF EXISTS `sec_mask_policy`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sec_mask_policy` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint DEFAULT '1',
  `table_id` bigint DEFAULT NULL,
  `table_name` varchar(128) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `column_id` bigint DEFAULT NULL,
  `column_name` varchar(128) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `algorithm` varchar(32) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `security_level` varchar(8) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `role_codes` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `status` varchar(16) COLLATE utf8mb4_general_ci DEFAULT 'PENDING',
  `approver` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `approve_time` datetime DEFAULT NULL,
  `create_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `create_time` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_sec_mask_table` (`table_name`)
) ENGINE=InnoDB AUTO_INCREMENT=15 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `sec_sensitive_result`
--

DROP TABLE IF EXISTS `sec_sensitive_result`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sec_sensitive_result` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint DEFAULT '1',
  `rule_id` bigint DEFAULT NULL,
  `table_id` bigint DEFAULT NULL,
  `table_name` varchar(128) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `column_id` bigint DEFAULT NULL,
  `column_name` varchar(128) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `match_type` varchar(16) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `security_level` varchar(8) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `sample_value` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `hit_rate` decimal(6,2) DEFAULT NULL,
  `status` varchar(16) COLLATE utf8mb4_general_ci DEFAULT 'CONFIRMED',
  `scan_time` datetime DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=196 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `sec_sensitive_rule`
--

DROP TABLE IF EXISTS `sec_sensitive_rule`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sec_sensitive_rule` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint DEFAULT '1',
  `code` varchar(64) COLLATE utf8mb4_general_ci NOT NULL,
  `name` varchar(128) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `match_type` varchar(16) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `pattern` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `category` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `security_level` varchar(8) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `mask_algo` varchar(32) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `status` varchar(16) COLLATE utf8mb4_general_ci DEFAULT 'ENABLED',
  `hit_columns` int DEFAULT '0',
  `create_time` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `code` (`code`)
) ENGINE=InnoDB AUTO_INCREMENT=12 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `std_compliance_check`
--

DROP TABLE IF EXISTS `std_compliance_check`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `std_compliance_check` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `check_time` datetime DEFAULT NULL,
  `scope` varchar(128) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `total_cols` int DEFAULT NULL,
  `compliant_cols` int DEFAULT NULL,
  `compliant_rate` decimal(6,2) DEFAULT NULL,
  `detail` text COLLATE utf8mb4_general_ci,
  `operator` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=7 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `std_element`
--

DROP TABLE IF EXISTS `std_element`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `std_element` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint DEFAULT '1',
  `code` varchar(64) COLLATE utf8mb4_general_ci NOT NULL,
  `name` varchar(128) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `cn_name` varchar(128) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `category` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `data_type` varchar(32) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `data_len` varchar(32) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `fmt` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `business_desc` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `rule_desc` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `source` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `industry` varchar(32) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `status` varchar(16) COLLATE utf8mb4_general_ci DEFAULT 'PUBLISHED',
  `version` varchar(32) COLLATE utf8mb4_general_ci DEFAULT '1.0.0',
  `owner` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `create_time` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `code` (`code`)
) ENGINE=InnoDB AUTO_INCREMENT=39 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `std_field_mapping`
--

DROP TABLE IF EXISTS `std_field_mapping`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `std_field_mapping` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint DEFAULT '1',
  `table_id` bigint DEFAULT NULL,
  `table_name` varchar(128) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `column_id` bigint DEFAULT NULL,
  `column_name` varchar(128) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `std_element_code` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `match_type` varchar(16) COLLATE utf8mb4_general_ci DEFAULT 'AUTO',
  `score` decimal(6,2) DEFAULT NULL,
  `status` varchar(16) COLLATE utf8mb4_general_ci DEFAULT 'CONFIRMED',
  `create_time` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_std_map_col` (`column_id`)
) ENGINE=InnoDB AUTO_INCREMENT=7 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `std_standard`
--

DROP TABLE IF EXISTS `std_standard`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `std_standard` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint DEFAULT '1',
  `code` varchar(64) COLLATE utf8mb4_general_ci NOT NULL,
  `name` varchar(128) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `cn_name` varchar(128) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `category` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `industry` varchar(32) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `source` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `status` varchar(16) COLLATE utf8mb4_general_ci DEFAULT 'PUBLISHED',
  `version` varchar(32) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `element_codes` text COLLATE utf8mb4_general_ci,
  `description` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `create_time` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `code` (`code`)
) ENGINE=InnoDB AUTO_INCREMENT=6 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `svc_api`
--

DROP TABLE IF EXISTS `svc_api`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `svc_api` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint DEFAULT '1',
  `code` varchar(64) COLLATE utf8mb4_general_ci NOT NULL,
  `name` varchar(128) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `api_path` varchar(128) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `method` varchar(8) COLLATE utf8mb4_general_ci DEFAULT 'GET',
  `version` varchar(32) COLLATE utf8mb4_general_ci DEFAULT 'v1',
  `status` varchar(16) COLLATE utf8mb4_general_ci DEFAULT 'DRAFT',
  `category` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `sql_text` text COLLATE utf8mb4_general_ci,
  `params` text COLLATE utf8mb4_general_ci,
  `owner` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `app_code` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `auth_type` varchar(16) COLLATE utf8mb4_general_ci DEFAULT 'AKSK',
  `rate_limit` int DEFAULT '100',
  `timeout_ms` int DEFAULT '3000',
  `mask_enabled` int DEFAULT '1',
  `call_count` bigint DEFAULT '0',
  `avg_ms` decimal(10,2) DEFAULT '0.00',
  `description` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `create_time` datetime DEFAULT NULL,
  `publish_time` datetime DEFAULT NULL,
  `industry` varchar(32) COLLATE utf8mb4_general_ci DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `code` (`code`)
) ENGINE=InnoDB AUTO_INCREMENT=19 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `svc_api_version`
--

DROP TABLE IF EXISTS `svc_api_version`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `svc_api_version` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `api_id` bigint DEFAULT NULL,
  `version` varchar(32) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `sql_text` text COLLATE utf8mb4_general_ci,
  `params` text COLLATE utf8mb4_general_ci,
  `change_note` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `operator` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `create_time` datetime DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=10 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `svc_app`
--

DROP TABLE IF EXISTS `svc_app`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `svc_app` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint DEFAULT '1',
  `app_code` varchar(64) COLLATE utf8mb4_general_ci NOT NULL,
  `app_name` varchar(128) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `app_secret` varchar(128) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `owner` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `status` varchar(16) COLLATE utf8mb4_general_ci DEFAULT 'ENABLED',
  `quota_day` int DEFAULT '10000',
  `create_time` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `app_code` (`app_code`)
) ENGINE=InnoDB AUTO_INCREMENT=9 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `svc_blacklist`
--

DROP TABLE IF EXISTS `svc_blacklist`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `svc_blacklist` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `ip` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `reason` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `status` varchar(16) COLLATE utf8mb4_general_ci DEFAULT 'ENABLED',
  `create_time` datetime DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=7 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `svc_call_log`
--

DROP TABLE IF EXISTS `svc_call_log`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `svc_call_log` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `api_id` bigint DEFAULT NULL,
  `api_code` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `api_name` varchar(128) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `call_time` datetime DEFAULT NULL,
  `status_code` int DEFAULT NULL,
  `duration_ms` bigint DEFAULT NULL,
  `app_code` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `ip` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `row_cnt` int DEFAULT NULL,
  `error_msg` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `trace_id` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_svc_call_api_time` (`api_id`,`call_time`)
) ENGINE=InnoDB AUTO_INCREMENT=1784 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `svc_call_stat`
--

DROP TABLE IF EXISTS `svc_call_stat`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `svc_call_stat` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `stat_date` date DEFAULT NULL,
  `api_id` bigint DEFAULT NULL,
  `call_cnt` bigint DEFAULT NULL,
  `success_cnt` bigint DEFAULT NULL,
  `fail_cnt` bigint DEFAULT NULL,
  `avg_ms` decimal(10,2) DEFAULT NULL,
  `p99_ms` decimal(10,2) DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=181 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `svc_orchestration`
--

DROP TABLE IF EXISTS `svc_orchestration`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `svc_orchestration` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint DEFAULT '1',
  `code` varchar(64) COLLATE utf8mb4_general_ci NOT NULL,
  `name` varchar(128) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `status` varchar(16) COLLATE utf8mb4_general_ci DEFAULT 'DRAFT',
  `description` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `steps` text COLLATE utf8mb4_general_ci,
  `owner` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `call_cnt` int DEFAULT '0',
  `create_time` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `code` (`code`)
) ENGINE=InnoDB AUTO_INCREMENT=7 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `svc_orchestration_run`
--

DROP TABLE IF EXISTS `svc_orchestration_run`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `svc_orchestration_run` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `orch_id` bigint DEFAULT NULL,
  `status` varchar(16) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `start_time` datetime DEFAULT NULL,
  `end_time` datetime DEFAULT NULL,
  `result` text COLLATE utf8mb4_general_ci,
  `operator` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=6 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `svc_quota`
--

DROP TABLE IF EXISTS `svc_quota`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `svc_quota` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint DEFAULT '1',
  `app_code` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `quota_day` int DEFAULT NULL,
  `used_today` int DEFAULT '0',
  `qps_limit` int DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `ux_svc_quota_app` (`app_code`)
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `sys_async_task`
--

DROP TABLE IF EXISTS `sys_async_task`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_async_task` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `task_id` varchar(64) COLLATE utf8mb4_general_ci NOT NULL,
  `task_type` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `status` varchar(16) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `progress` int DEFAULT '0',
  `result` text COLLATE utf8mb4_general_ci,
  `error` text COLLATE utf8mb4_general_ci,
  `start_time` datetime DEFAULT NULL,
  `end_time` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `ux_svc_oauth_token` (`task_id`)
) ENGINE=InnoDB AUTO_INCREMENT=15 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `sys_config`
--

DROP TABLE IF EXISTS `sys_config`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_config` (
  `config_key` varchar(128) COLLATE utf8mb4_general_ci NOT NULL,
  `config_value` text COLLATE utf8mb4_general_ci,
  `description` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  PRIMARY KEY (`config_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `sys_data_scope`
--

DROP TABLE IF EXISTS `sys_data_scope`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_data_scope` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint DEFAULT '1',
  `role_code` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `scope_type` varchar(16) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `scope_value` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `create_time` datetime DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=16 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `sys_dict`
--

DROP TABLE IF EXISTS `sys_dict`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_dict` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `dict_code` varchar(64) COLLATE utf8mb4_general_ci NOT NULL,
  `dict_name` varchar(128) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `items` text COLLATE utf8mb4_general_ci,
  `update_time` datetime DEFAULT NULL,
  `create_time` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `dict_code` (`dict_code`)
) ENGINE=InnoDB AUTO_INCREMENT=7 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `sys_login_log`
--

DROP TABLE IF EXISTS `sys_login_log`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_login_log` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `username` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `ip` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `status` varchar(16) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `message` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `user_agent` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `login_time` datetime DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=1431 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `sys_org`
--

DROP TABLE IF EXISTS `sys_org`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_org` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint DEFAULT '1',
  `parent_id` bigint DEFAULT '0',
  `code` varchar(64) COLLATE utf8mb4_general_ci NOT NULL,
  `name` varchar(128) COLLATE utf8mb4_general_ci NOT NULL,
  `sort_no` int DEFAULT '0',
  `create_time` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `code` (`code`)
) ENGINE=InnoDB AUTO_INCREMENT=7 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `sys_role`
--

DROP TABLE IF EXISTS `sys_role`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_role` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `code` varchar(64) COLLATE utf8mb4_general_ci NOT NULL,
  `name` varchar(64) COLLATE utf8mb4_general_ci NOT NULL,
  `perms` text COLLATE utf8mb4_general_ci,
  `description` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `code` (`code`)
) ENGINE=InnoDB AUTO_INCREMENT=7 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `sys_tenant`
--

DROP TABLE IF EXISTS `sys_tenant`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_tenant` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `code` varchar(64) COLLATE utf8mb4_general_ci NOT NULL,
  `name` varchar(128) COLLATE utf8mb4_general_ci NOT NULL,
  `parent_id` bigint DEFAULT '0',
  `tenant_path` varchar(255) COLLATE utf8mb4_general_ci DEFAULT '/',
  `tenant_level` int DEFAULT '1',
  `industry_code` varchar(32) COLLATE utf8mb4_general_ci DEFAULT 'GOV',
  `isolation_mode` varchar(16) COLLATE utf8mb4_general_ci DEFAULT 'SHARED',
  `status` varchar(16) COLLATE utf8mb4_general_ci DEFAULT 'ENABLED',
  `create_time` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `code` (`code`)
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `sys_token_blacklist`
--

DROP TABLE IF EXISTS `sys_token_blacklist`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_token_blacklist` (
  `jti` varchar(64) COLLATE utf8mb4_general_ci NOT NULL,
  `username` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `expire_at` bigint DEFAULT NULL,
  `create_time` datetime DEFAULT NULL,
  PRIMARY KEY (`jti`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `sys_user`
--

DROP TABLE IF EXISTS `sys_user`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_user` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint DEFAULT '1',
  `username` varchar(64) COLLATE utf8mb4_general_ci NOT NULL,
  `password` varchar(255) COLLATE utf8mb4_general_ci NOT NULL,
  `real_name` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `role_code` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `dept` varchar(128) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `email` varchar(128) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `phone` varchar(32) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `status` varchar(16) COLLATE utf8mb4_general_ci DEFAULT '启用',
  `last_login_time` datetime DEFAULT NULL,
  `create_time` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `username` (`username`),
  KEY `idx_sys_user_role` (`role_code`)
) ENGINE=InnoDB AUTO_INCREMENT=13 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-10-21 15:29:30
