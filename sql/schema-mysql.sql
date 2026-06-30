/*M!999999\- enable the sandbox mode */ 
-- MariaDB dump 10.19  Distrib 10.11.14-MariaDB, for debian-linux-gnu (x86_64)
--
-- Host: 103.236.96.82    Database: dataflow
-- ------------------------------------------------------
-- Server version	8.0.46-0ubuntu0.22.04.3

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!40101 SET NAMES utf8mb4 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;

--
-- Table structure for table `agg_cdc_task`
--

DROP TABLE IF EXISTS `agg_cdc_task`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `agg_cdc_task` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `name` varchar(128) NOT NULL,
  `source_id` bigint NOT NULL,
  `target_id` bigint NOT NULL,
  `operation_mode` varchar(32) DEFAULT NULL,
  `field_mapping` longtext,
  `remark` varchar(512) DEFAULT NULL,
  `status` varchar(32) DEFAULT 'STOPPED',
  `start_time` timestamp NULL DEFAULT NULL,
  `create_by` varchar(64) DEFAULT NULL,
  `create_time` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `deleted` tinyint DEFAULT '0',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `agg_dataflow`
--

DROP TABLE IF EXISTS `agg_dataflow`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `agg_dataflow` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `name` varchar(128) NOT NULL,
  `source_id` bigint NOT NULL,
  `source_name` varchar(128) DEFAULT NULL COMMENT 'æºåç§°',
  `target_id` bigint NOT NULL,
  `target_name` varchar(128) DEFAULT NULL COMMENT 'ç›®æ ‡åç§°',
  `write_mode` varchar(32) DEFAULT 'insert',
  `fault_tolerance` int DEFAULT '0',
  `pre_sql` longtext,
  `field_mapping` longtext,
  `schedule_cron` varchar(64) DEFAULT NULL,
  `schedule_enabled` tinyint DEFAULT '0',
  `increment_field_id` bigint DEFAULT NULL,
  `status` varchar(32) DEFAULT 'NOT_STARTED',
  `read_count` bigint DEFAULT '0',
  `write_count` bigint DEFAULT '0',
  `last_run_time` timestamp NULL DEFAULT NULL,
  `last_run_status` varchar(32) DEFAULT NULL,
  `create_by` varchar(64) DEFAULT NULL,
  `create_time` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `deleted` tinyint DEFAULT '0',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `agg_dataflow_log`
--

DROP TABLE IF EXISTS `agg_dataflow_log`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `agg_dataflow_log` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `dataflow_id` bigint DEFAULT '0',
  `run_id` varchar(64) DEFAULT NULL,
  `log_level` varchar(16) DEFAULT NULL,
  `message` text,
  `record_count` bigint DEFAULT '0',
  `create_time` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `agg_datasource`
--

DROP TABLE IF EXISTS `agg_datasource`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `agg_datasource` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `name` varchar(128) NOT NULL,
  `db_type` varchar(32) NOT NULL,
  `host` varchar(128) DEFAULT NULL,
  `port` int DEFAULT NULL,
  `database_name` varchar(128) DEFAULT NULL,
  `username` varchar(128) DEFAULT NULL,
  `password` varchar(256) DEFAULT NULL,
  `file_path` varchar(512) DEFAULT NULL,
  `config_json` longtext,
  `locked` tinyint DEFAULT '0' COMMENT '是否被数据流锁定',
  `create_by` varchar(64) DEFAULT NULL,
  `create_time` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `deleted` tinyint DEFAULT '0',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `agg_full_sync`
--

DROP TABLE IF EXISTS `agg_full_sync`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `agg_full_sync` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `name` varchar(128) NOT NULL,
  `sync_type` varchar(32) DEFAULT NULL,
  `source_id` bigint NOT NULL,
  `target_id` bigint NOT NULL,
  `tables_json` longtext,
  `fault_limit` int DEFAULT '0',
  `target_action` varchar(32) DEFAULT NULL,
  `remark` varchar(512) DEFAULT NULL,
  `status` varchar(32) DEFAULT 'NOT_STARTED',
  `table_total` int DEFAULT '0',
  `success_count` int DEFAULT '0',
  `fail_count` int DEFAULT '0',
  `running_count` int DEFAULT '0',
  `last_run_time` timestamp NULL DEFAULT NULL,
  `create_by` varchar(64) DEFAULT NULL,
  `create_time` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `deleted` tinyint DEFAULT '0',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `agg_increment_field`
--

DROP TABLE IF EXISTS `agg_increment_field`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `agg_increment_field` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `table_name` varchar(128) NOT NULL,
  `field_name` varchar(128) NOT NULL,
  `field_position` int DEFAULT NULL,
  `field_type` varchar(64) DEFAULT NULL,
  `field_comment` varchar(256) DEFAULT NULL,
  `is_increment` tinyint DEFAULT '1',
  `increment_value` varchar(256) DEFAULT NULL,
  `create_time` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `deleted` tinyint DEFAULT '0',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `asset_favorite`
--

DROP TABLE IF EXISTS `asset_favorite`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `asset_favorite` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `user_id` bigint DEFAULT NULL,
  `asset_table_id` bigint DEFAULT NULL,
  `create_time` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `asset_field`
--

DROP TABLE IF EXISTS `asset_field`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `asset_field` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `table_id` bigint NOT NULL,
  `field_en` varchar(128) NOT NULL,
  `field_cn` varchar(128) DEFAULT NULL,
  `field_type` varchar(64) DEFAULT NULL,
  `description` varchar(256) DEFAULT NULL,
  `is_sensitive` tinyint DEFAULT '0',
  `create_time` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `asset_follow`
--

DROP TABLE IF EXISTS `asset_follow`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `asset_follow` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `user_id` bigint DEFAULT NULL,
  `asset_table_id` bigint DEFAULT NULL,
  `create_time` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `asset_lineage`
--

DROP TABLE IF EXISTS `asset_lineage`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `asset_lineage` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `source_table_id` bigint NOT NULL,
  `source_field_id` bigint DEFAULT NULL COMMENT 'æºå­—æ®µID',
  `target_table_id` bigint NOT NULL,
  `target_field_id` bigint DEFAULT NULL COMMENT 'ç›®æ ‡å­—æ®µID',
  `relation_type` varchar(32) DEFAULT 'DERIVE',
  `create_time` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `asset_table`
--

DROP TABLE IF EXISTS `asset_table`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `asset_table` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `table_en` varchar(128) NOT NULL,
  `table_cn` varchar(128) DEFAULT NULL,
  `database_name` varchar(128) DEFAULT NULL,
  `datasource_id` bigint DEFAULT NULL,
  `layer_code` varchar(16) DEFAULT NULL,
  `row_count` bigint DEFAULT '0',
  `is_key_asset` tinyint DEFAULT '0',
  `create_by` varchar(64) DEFAULT NULL,
  `create_time` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `deleted` tinyint DEFAULT '0',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=6 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `asset_tag`
--

DROP TABLE IF EXISTS `asset_tag`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `asset_tag` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `tag_name` varchar(128) NOT NULL,
  `tag_type` varchar(32) DEFAULT NULL,
  `tag_color` varchar(32) DEFAULT NULL,
  `create_time` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `deleted` tinyint DEFAULT '0',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `asset_tag_relation`
--

DROP TABLE IF EXISTS `asset_tag_relation`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `asset_tag_relation` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `asset_table_id` bigint DEFAULT NULL,
  `tag_id` bigint DEFAULT NULL,
  `create_time` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `dev_file_watcher`
--

DROP TABLE IF EXISTS `dev_file_watcher`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `dev_file_watcher` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `name` varchar(128) NOT NULL,
  `watch_directory` varchar(512) DEFAULT NULL,
  `file_name_pattern` varchar(256) DEFAULT NULL,
  `target_workflow_id` bigint DEFAULT NULL,
  `status` varchar(32) DEFAULT 'STOPPED',
  `create_by` varchar(64) DEFAULT NULL,
  `create_time` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `deleted` tinyint DEFAULT '0',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `dev_hdfs_file`
--

DROP TABLE IF EXISTS `dev_hdfs_file`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `dev_hdfs_file` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `file_name` varchar(256) NOT NULL,
  `file_path` varchar(1024) NOT NULL,
  `is_dir` tinyint DEFAULT '0',
  `file_size` bigint DEFAULT '0',
  `content_type` varchar(128) DEFAULT NULL,
  `parent_path` varchar(1024) DEFAULT NULL,
  `create_by` varchar(64) DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint DEFAULT '0',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `dev_project`
--

DROP TABLE IF EXISTS `dev_project`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `dev_project` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `name` varchar(128) NOT NULL,
  `domain` varchar(64) DEFAULT NULL,
  `description` varchar(512) DEFAULT NULL,
  `create_by` varchar(64) DEFAULT NULL,
  `create_time` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `deleted` tinyint DEFAULT '0',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `dev_quality_rule`
--

DROP TABLE IF EXISTS `dev_quality_rule`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `dev_quality_rule` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `name` varchar(128) NOT NULL,
  `dimension` varchar(32) DEFAULT NULL,
  `rule_level` varchar(32) DEFAULT 'FIELD',
  `target_table_id` bigint DEFAULT NULL,
  `target_field` varchar(128) DEFAULT NULL,
  `rule_expr` longtext,
  `rule_config` text,
  `template_type` varchar(64) DEFAULT NULL,
  `severity` varchar(32) DEFAULT 'WARNING',
  `create_time` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint DEFAULT '0',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `dev_quality_score_history`
--

DROP TABLE IF EXISTS `dev_quality_score_history`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `dev_quality_score_history` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `task_id` bigint DEFAULT NULL,
  `score` decimal(5,2) DEFAULT NULL,
  `issue_count` int DEFAULT '0',
  `check_time` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `dev_quality_task`
--

DROP TABLE IF EXISTS `dev_quality_task`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `dev_quality_task` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `name` varchar(128) DEFAULT NULL,
  `target_table_id` bigint DEFAULT NULL,
  `rule_ids` text,
  `schedule_cron` varchar(64) DEFAULT NULL,
  `alert_enabled` tinyint DEFAULT '0',
  `alert_config` text,
  `rule_id` bigint NOT NULL,
  `target_table` varchar(128) DEFAULT NULL,
  `status` varchar(32) DEFAULT 'PENDING',
  `score` decimal(5,2) DEFAULT NULL,
  `issue_count` int DEFAULT '0',
  `problem_count` bigint DEFAULT '0',
  `last_run_time` timestamp NULL DEFAULT NULL,
  `last_run_status` varchar(32) DEFAULT NULL,
  `create_by` varchar(64) DEFAULT NULL,
  `create_time` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint DEFAULT '0',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `dev_schedule_execution_log`
--

DROP TABLE IF EXISTS `dev_schedule_execution_log`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `dev_schedule_execution_log` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `task_id` bigint NOT NULL,
  `task_type` varchar(32) NOT NULL,
  `tenant_id` bigint NOT NULL,
  `status` varchar(32) DEFAULT 'PENDING' COMMENT 'PENDING/RUNNING/SUCCESS/FAILED/TIMEOUT',
  `output` text,
  `error_log` text,
  `start_time` datetime DEFAULT NULL,
  `end_time` datetime DEFAULT NULL,
  `duration_ms` bigint DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `dev_schedule_task`
--

DROP TABLE IF EXISTS `dev_schedule_task`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `dev_schedule_task` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `name` varchar(128) NOT NULL,
  `target_type` varchar(32) NOT NULL DEFAULT 'WORKFLOW',
  `target_id` bigint NOT NULL DEFAULT '0',
  `workflow_id` bigint DEFAULT NULL,
  `trigger_type` varchar(32) DEFAULT 'CRON',
  `cron_expr` varchar(64) DEFAULT NULL,
  `schedule_type` varchar(32) DEFAULT 'FULL',
  `start_time` datetime DEFAULT NULL,
  `increment_period` varchar(32) DEFAULT NULL,
  `global_params` text,
  `alert_enabled` tinyint DEFAULT '0',
  `timeout_seconds` int DEFAULT '3600',
  `retry_count` int DEFAULT '0',
  `skip_windows` text,
  `status` varchar(32) DEFAULT 'STOPPED',
  `last_run_time` timestamp NULL DEFAULT NULL,
  `next_run_time` datetime DEFAULT NULL,
  `create_by` varchar(64) DEFAULT NULL,
  `create_time` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `deleted` tinyint DEFAULT '0',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `dev_script`
--

DROP TABLE IF EXISTS `dev_script`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `dev_script` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `project_id` bigint DEFAULT NULL,
  `name` varchar(128) NOT NULL,
  `script_type` varchar(32) DEFAULT 'SQL',
  `datasource_id` bigint DEFAULT NULL,
  `content` longtext,
  `description` varchar(256) DEFAULT NULL,
  `status` varchar(32) DEFAULT 'DRAFT',
  `last_run_time` timestamp NULL DEFAULT NULL,
  `create_by` varchar(64) DEFAULT NULL,
  `create_time` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `deleted` tinyint DEFAULT '0',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `dev_script_execution_log`
--

DROP TABLE IF EXISTS `dev_script_execution_log`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `dev_script_execution_log` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `script_id` bigint NOT NULL,
  `tenant_id` bigint NOT NULL,
  `status` varchar(32) DEFAULT 'PENDING' COMMENT 'PENDING/RUNNING/SUCCESS/FAILED/TIMEOUT',
  `output` text,
  `error_log` text,
  `exit_code` int DEFAULT NULL,
  `duration_ms` bigint DEFAULT NULL,
  `trigger_by` varchar(64) DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `dev_script_version`
--

DROP TABLE IF EXISTS `dev_script_version`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `dev_script_version` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `script_id` bigint NOT NULL,
  `version_num` int DEFAULT '1',
  `content` mediumtext,
  `change_note` varchar(512) DEFAULT NULL,
  `create_by` varchar(64) DEFAULT NULL,
  `create_time` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `dev_udf`
--

DROP TABLE IF EXISTS `dev_udf`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `dev_udf` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `udf_name` varchar(128) NOT NULL,
  `udf_type` varchar(32) NOT NULL COMMENT 'HIVE/SPARK',
  `owner` varchar(64) DEFAULT NULL,
  `class_name` varchar(256) NOT NULL,
  `jar_path` varchar(512) DEFAULT NULL,
  `create_sql` text,
  `return_type` varchar(64) DEFAULT NULL,
  `param_types` varchar(256) DEFAULT NULL,
  `description` text,
  `status` varchar(32) DEFAULT 'DRAFT',
  `create_by` varchar(64) DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint DEFAULT '0',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `dev_workflow`
--

DROP TABLE IF EXISTS `dev_workflow`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `dev_workflow` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `project_id` bigint DEFAULT NULL,
  `name` varchar(128) NOT NULL,
  `description` text,
  `dag_json` longtext,
  `status` varchar(32) DEFAULT 'DRAFT',
  `last_run_time` datetime DEFAULT NULL,
  `last_run_status` varchar(32) DEFAULT NULL,
  `schedule_cron` varchar(64) DEFAULT NULL,
  `schedule_enabled` tinyint DEFAULT '0',
  `parallel_strategy` varchar(32) DEFAULT 'SKIP',
  `job_type` varchar(32) DEFAULT 'SQL' COMMENT 'ä»»åŠ¡ç±»åž‹ SQL/SPARK/SHELL',
  `dep_workflow_ids` text COMMENT 'ä¾èµ–å·¥ä½œæµIDåˆ—è¡¨',
  `retry_count` int DEFAULT '0',
  `retry_interval_sec` int DEFAULT '60',
  `on_failure` varchar(32) DEFAULT 'CONTINUE' COMMENT 'å¤±è´¥ç­–ç•¥',
  `alert_enabled` tinyint DEFAULT '0',
  `alert_channels` varchar(255) DEFAULT NULL COMMENT 'å‘Šè­¦æ¸ é“',
  `alert_conditions` varchar(255) DEFAULT NULL COMMENT 'å‘Šè­¦æ¡ä»¶',
  `priority` int DEFAULT '3' COMMENT 'ä¼˜å…ˆçº§ 1-5',
  `create_by` varchar(64) DEFAULT NULL,
  `create_time` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `deleted` tinyint DEFAULT '0',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `gov_atomic_indicator`
--

DROP TABLE IF EXISTS `gov_atomic_indicator`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `gov_atomic_indicator` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `indicator_code` varchar(128) DEFAULT NULL,
  `indicator_type` varchar(32) DEFAULT NULL,
  `name` varchar(128) NOT NULL,
  `source_table_id` bigint DEFAULT NULL,
  `calc_logic` longtext,
  `agg_function` varchar(32) DEFAULT NULL,
  `unit` varchar(64) DEFAULT NULL,
  `parent_id` bigint DEFAULT NULL,
  `source_field` varchar(128) DEFAULT NULL,
  `status` tinyint DEFAULT '0',
  `create_by` varchar(64) DEFAULT NULL,
  `create_time` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint DEFAULT '0',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `gov_business_domain`
--

DROP TABLE IF EXISTS `gov_business_domain`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `gov_business_domain` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `layer_id` bigint DEFAULT NULL,
  `parent_id` bigint DEFAULT '0',
  `domain_name` varchar(128) NOT NULL,
  `domain_code` varchar(64) DEFAULT NULL,
  `description` varchar(512) DEFAULT NULL,
  `sort_order` int DEFAULT '0',
  `create_time` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `deleted` tinyint DEFAULT '0',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `gov_business_limit`
--

DROP TABLE IF EXISTS `gov_business_limit`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `gov_business_limit` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `name` varchar(128) NOT NULL,
  `source_field` varchar(128) DEFAULT NULL,
  `calc_logic` longtext,
  `create_time` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `deleted` tinyint DEFAULT '0',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `gov_code_set`
--

DROP TABLE IF EXISTS `gov_code_set`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `gov_code_set` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `catalog_id` bigint DEFAULT '0',
  `code` varchar(64) NOT NULL,
  `name` varchar(128) NOT NULL,
  `code_value` varchar(256) DEFAULT NULL,
  `version` varchar(32) DEFAULT '1.0',
  `description` varchar(512) DEFAULT NULL,
  `create_time` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `deleted` tinyint DEFAULT '0',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `gov_composite_indicator`
--

DROP TABLE IF EXISTS `gov_composite_indicator`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `gov_composite_indicator` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `indicator_code` varchar(64) DEFAULT NULL,
  `indicator_name` varchar(128) NOT NULL,
  `expression` varchar(512) DEFAULT NULL,
  `indicator_ids` varchar(512) DEFAULT NULL,
  `unit` varchar(64) DEFAULT NULL,
  `description` varchar(512) DEFAULT NULL,
  `status` varchar(32) DEFAULT 'DRAFT',
  `create_by` varchar(64) DEFAULT NULL,
  `create_time` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `deleted` tinyint DEFAULT '0',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `gov_cross_domain_metadata`
--

DROP TABLE IF EXISTS `gov_cross_domain_metadata`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `gov_cross_domain_metadata` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `source_name` varchar(128) NOT NULL,
  `source_type` varchar(64) DEFAULT NULL,
  `export_format` varchar(32) DEFAULT NULL,
  `metadata_json` text,
  `sync_status` varchar(32) DEFAULT 'PENDING',
  `version` varchar(32) DEFAULT NULL,
  `create_by` varchar(64) DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint DEFAULT '0',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `gov_derived_indicator`
--

DROP TABLE IF EXISTS `gov_derived_indicator`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `gov_derived_indicator` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `name` varchar(128) NOT NULL,
  `atomic_id` bigint DEFAULT NULL,
  `period_id` bigint DEFAULT NULL,
  `limit_id` bigint DEFAULT NULL,
  `granularity` varchar(64) DEFAULT NULL,
  `data_theme` varchar(64) DEFAULT NULL,
  `status` tinyint DEFAULT '0',
  `create_time` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `deleted` tinyint DEFAULT '0',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `gov_model_table`
--

DROP TABLE IF EXISTS `gov_model_table`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `gov_model_table` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `layer_id` bigint DEFAULT NULL,
  `catalog_id` bigint DEFAULT NULL,
  `layer_code` varchar(16) NOT NULL,
  `table_en` varchar(128) NOT NULL,
  `table_cn` varchar(128) DEFAULT NULL,
  `table_type` varchar(32) DEFAULT NULL,
  `source_table` varchar(128) DEFAULT NULL,
  `etl_sql` text,
  `etl_filter` text,
  `datasource_id` bigint DEFAULT NULL,
  `clean_rules` longtext,
  `problem_table_enabled` tinyint DEFAULT '0',
  `problem_write_mode` varchar(32) DEFAULT NULL,
  `status` varchar(32) DEFAULT 'DRAFT',
  `remark` varchar(512) DEFAULT NULL,
  `create_by` varchar(64) DEFAULT NULL,
  `create_time` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `deleted` tinyint DEFAULT '0',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `gov_proxy_node`
--

DROP TABLE IF EXISTS `gov_proxy_node`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `gov_proxy_node` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `name` varchar(128) NOT NULL,
  `host` varchar(128) DEFAULT NULL,
  `port` int DEFAULT NULL,
  `node_type` varchar(32) DEFAULT 'DATA_PROXY',
  `description` text,
  `status` varchar(32) DEFAULT 'STOPPED',
  `create_by` varchar(64) DEFAULT NULL,
  `remark` varchar(512) DEFAULT NULL,
  `create_time` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `deleted` tinyint DEFAULT '0',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `gov_standard_catalog`
--

DROP TABLE IF EXISTS `gov_standard_catalog`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `gov_standard_catalog` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `parent_id` bigint DEFAULT '0',
  `name` varchar(128) NOT NULL,
  `catalog_type` varchar(32) DEFAULT 'TABLE',
  `create_time` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `deleted` tinyint DEFAULT '0',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `gov_stat_period`
--

DROP TABLE IF EXISTS `gov_stat_period`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `gov_stat_period` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `name` varchar(128) NOT NULL,
  `cron_expr` varchar(64) DEFAULT NULL,
  `status` tinyint DEFAULT '0' COMMENT '0草稿 1发布 2下线',
  `create_time` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `deleted` tinyint DEFAULT '0',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `gov_warehouse_catalog`
--

DROP TABLE IF EXISTS `gov_warehouse_catalog`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `gov_warehouse_catalog` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `layer_id` bigint NOT NULL,
  `parent_id` bigint DEFAULT '0',
  `name` varchar(128) NOT NULL,
  `create_time` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `deleted` tinyint DEFAULT '0',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `gov_warehouse_layer`
--

DROP TABLE IF EXISTS `gov_warehouse_layer`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `gov_warehouse_layer` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `layer_code` varchar(16) NOT NULL,
  `layer_name` varchar(64) NOT NULL,
  `description` varchar(256) DEFAULT NULL,
  `sort_order` int DEFAULT '0',
  `create_time` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `deleted` tinyint DEFAULT '0',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=6 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `gov_word`
--

DROP TABLE IF EXISTS `gov_word`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `gov_word` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `catalog_id` bigint DEFAULT '0',
  `word_en` varchar(128) NOT NULL,
  `word_cn` varchar(128) DEFAULT NULL,
  `definition` varchar(512) DEFAULT NULL,
  `version` varchar(32) DEFAULT '1.0',
  `create_time` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `deleted` tinyint DEFAULT '0',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `infra_cluster`
--

DROP TABLE IF EXISTS `infra_cluster`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `infra_cluster` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `name` varchar(128) NOT NULL,
  `description` varchar(512) DEFAULT NULL,
  `host_count` int DEFAULT '0',
  `status` varchar(32) DEFAULT 'NORMAL',
  `create_time` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `deleted` tinyint DEFAULT '0',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `infra_component`
--

DROP TABLE IF EXISTS `infra_component`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `infra_component` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `host_id` bigint DEFAULT NULL,
  `component_type` varchar(32) NOT NULL,
  `version` varchar(32) DEFAULT NULL,
  `status` varchar(32) DEFAULT 'RUNNING',
  `create_time` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `deleted` tinyint DEFAULT '0',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `infra_host`
--

DROP TABLE IF EXISTS `infra_host`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `infra_host` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `hostname` varchar(128) NOT NULL,
  `ip` varchar(64) DEFAULT NULL,
  `cpu_cores` int DEFAULT NULL,
  `memory_gb` int DEFAULT NULL,
  `disk_gb` int DEFAULT NULL,
  `status` varchar(32) DEFAULT 'ONLINE',
  `create_time` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `deleted` tinyint DEFAULT '0',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `infra_resource_group`
--

DROP TABLE IF EXISTS `infra_resource_group`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `infra_resource_group` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `group_name` varchar(128) NOT NULL,
  `resource_type` varchar(64) DEFAULT NULL,
  `resource_key` varchar(256) DEFAULT NULL,
  `description` varchar(512) DEFAULT NULL,
  `max_cores` int DEFAULT '0',
  `max_memory_gb` decimal(10,2) DEFAULT '0.00',
  `status` varchar(32) DEFAULT 'NORMAL',
  `create_time` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `deleted` tinyint DEFAULT '0',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `meta_collector_log`
--

DROP TABLE IF EXISTS `meta_collector_log`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `meta_collector_log` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `task_id` bigint DEFAULT NULL,
  `status` varchar(32) DEFAULT NULL,
  `table_count` int DEFAULT '0',
  `field_count` int DEFAULT '0',
  `error_log` text,
  `duration_ms` bigint DEFAULT '0',
  `create_time` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `meta_collector_task`
--

DROP TABLE IF EXISTS `meta_collector_task`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `meta_collector_task` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `task_name` varchar(128) NOT NULL,
  `datasource_id` bigint DEFAULT NULL,
  `collect_type` varchar(32) DEFAULT NULL,
  `target_pattern` varchar(512) DEFAULT NULL,
  `cron_expr` varchar(64) DEFAULT NULL,
  `trigger_on_change` tinyint DEFAULT '0',
  `status` varchar(32) DEFAULT 'STOPPED',
  `last_run_time` timestamp NULL DEFAULT NULL,
  `create_time` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `deleted` tinyint DEFAULT '0',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `storage_quota`
--

DROP TABLE IF EXISTS `storage_quota`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `storage_quota` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `datasource_id` bigint DEFAULT NULL,
  `database_name` varchar(128) DEFAULT NULL,
  `max_size_gb` decimal(12,2) DEFAULT '0.00',
  `current_usage_gb` decimal(12,2) DEFAULT '0.00',
  `alert_threshold_pct` int DEFAULT '80',
  `status` varchar(32) DEFAULT 'NORMAL',
  `create_time` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `deleted` tinyint DEFAULT '0',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `svc_api`
--

DROP TABLE IF EXISTS `svc_api`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `svc_api` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `api_name` varchar(128) NOT NULL,
  `api_path` varchar(256) DEFAULT NULL,
  `request_method` varchar(16) DEFAULT 'GET',
  `update_frequency` varchar(32) DEFAULT NULL,
  `description` varchar(512) DEFAULT NULL,
  `catalog_id` bigint DEFAULT NULL,
  `service_unit_id` bigint DEFAULT NULL,
  `create_type` varchar(32) DEFAULT 'WIZARD',
  `sql_content` longtext,
  `api_sql` text,
  `proxy_url` varchar(512) DEFAULT NULL,
  `request_params` text,
  `response_example` text,
  `status` tinyint DEFAULT '0' COMMENT '0草稿 1已发布 2已下线',
  `create_by` varchar(64) DEFAULT NULL,
  `create_time` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `deleted` tinyint DEFAULT '0',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `svc_api_call_log`
--

DROP TABLE IF EXISTS `svc_api_call_log`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `svc_api_call_log` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `api_id` bigint DEFAULT NULL,
  `api_name` varchar(128) DEFAULT NULL,
  `app_name` varchar(128) DEFAULT NULL,
  `success` tinyint DEFAULT '1',
  `response_ms` int DEFAULT NULL,
  `create_time` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `svc_application`
--

DROP TABLE IF EXISTS `svc_application`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `svc_application` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `app_name` varchar(128) NOT NULL,
  `app_key` varchar(64) NOT NULL,
  `app_secret` varchar(128) DEFAULT NULL,
  `description` varchar(512) DEFAULT NULL,
  `status` tinyint DEFAULT '1',
  `create_time` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `deleted` tinyint DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `app_name` (`app_name`),
  UNIQUE KEY `app_key` (`app_key`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `svc_catalog`
--

DROP TABLE IF EXISTS `svc_catalog`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `svc_catalog` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `project_id` bigint DEFAULT '0',
  `parent_id` bigint DEFAULT '0',
  `name` varchar(128) NOT NULL,
  `enabled` tinyint DEFAULT '1',
  `create_time` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `deleted` tinyint DEFAULT '0',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `svc_catalog_project`
--

DROP TABLE IF EXISTS `svc_catalog_project`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `svc_catalog_project` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `project_name` varchar(128) NOT NULL,
  `description` varchar(512) DEFAULT NULL,
  `enabled` tinyint DEFAULT '1',
  `create_time` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `deleted` tinyint DEFAULT '0',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `svc_rate_limit`
--

DROP TABLE IF EXISTS `svc_rate_limit`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `svc_rate_limit` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `api_id` bigint DEFAULT NULL,
  `api_name` varchar(128) DEFAULT NULL,
  `time_window_sec` int DEFAULT '60',
  `max_requests` int DEFAULT '100',
  `enabled` tinyint DEFAULT '1',
  `create_time` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `deleted` tinyint DEFAULT '0',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `svc_service_unit`
--

DROP TABLE IF EXISTS `svc_service_unit`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `svc_service_unit` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `unit_name` varchar(128) NOT NULL,
  `unit_type` varchar(32) DEFAULT NULL,
  `datasource_id` bigint DEFAULT NULL,
  `query_template` text,
  `description` varchar(512) DEFAULT NULL,
  `status` tinyint DEFAULT '0' COMMENT '0草稿 1发布 2下线',
  `create_by` varchar(64) DEFAULT NULL,
  `create_time` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `deleted` tinyint DEFAULT '0',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `svc_workorder`
--

DROP TABLE IF EXISTS `svc_workorder`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `svc_workorder` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `api_id` bigint DEFAULT NULL,
  `app_id` bigint DEFAULT NULL,
  `api_name` varchar(128) DEFAULT NULL,
  `app_name` varchar(128) DEFAULT NULL,
  `applicant` varchar(64) DEFAULT NULL,
  `apply_type` varchar(32) DEFAULT NULL,
  `reason` varchar(512) DEFAULT NULL,
  `expire_time` timestamp NULL DEFAULT NULL,
  `status` tinyint DEFAULT '0' COMMENT '0待审 1通过 2拒绝',
  `approve_reason` varchar(512) DEFAULT NULL,
  `create_time` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `sys_config`
--

DROP TABLE IF EXISTS `sys_config`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_config` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `config_key` varchar(128) NOT NULL,
  `config_value` text,
  `description` varchar(512) DEFAULT NULL,
  `env` varchar(32) DEFAULT 'DEV',
  `update_time` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `sys_dept`
--

DROP TABLE IF EXISTS `sys_dept`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_dept` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `parent_id` bigint DEFAULT '0',
  `dept_name` varchar(128) NOT NULL,
  `sort_order` int DEFAULT '0',
  `create_time` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `deleted` tinyint DEFAULT '0',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `sys_menu`
--

DROP TABLE IF EXISTS `sys_menu`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_menu` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `parent_id` bigint DEFAULT '0',
  `menu_code` varchar(64) NOT NULL,
  `menu_name` varchar(128) NOT NULL,
  `description` varchar(256) DEFAULT NULL,
  `path` varchar(256) DEFAULT NULL,
  `component` varchar(256) DEFAULT NULL,
  `menu_type` tinyint DEFAULT '1' COMMENT '1目录 2菜单 3按钮',
  `service_name` varchar(64) DEFAULT NULL,
  `icon` varchar(64) DEFAULT NULL,
  `sort_order` int DEFAULT '0',
  `visible` tinyint DEFAULT '1',
  `create_time` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `deleted` tinyint DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `menu_code` (`menu_code`)
) ENGINE=InnoDB AUTO_INCREMENT=80 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `sys_oper_log`
--

DROP TABLE IF EXISTS `sys_oper_log`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_oper_log` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint DEFAULT NULL,
  `user_id` bigint DEFAULT NULL,
  `username` varchar(64) DEFAULT NULL,
  `event_name` varchar(128) DEFAULT NULL,
  `result` tinyint DEFAULT '1' COMMENT '0失败 1成功',
  `method` varchar(16) DEFAULT NULL,
  `resource_type` varchar(64) DEFAULT NULL,
  `resource_name` varchar(128) DEFAULT NULL,
  `ip` varchar(64) DEFAULT NULL,
  `detail` longtext,
  `create_time` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=42 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `sys_role`
--

DROP TABLE IF EXISTS `sys_role`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_role` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `role_name` varchar(64) NOT NULL,
  `role_key` varchar(64) NOT NULL,
  `description` varchar(256) DEFAULT NULL,
  `data_scope` tinyint DEFAULT '1' COMMENT '1全部 2自定义 3本级及子级 4本级',
  `create_time` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `deleted` tinyint DEFAULT '0',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `sys_role_menu`
--

DROP TABLE IF EXISTS `sys_role_menu`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_role_menu` (
  `role_id` bigint NOT NULL,
  `menu_id` bigint NOT NULL,
  PRIMARY KEY (`role_id`,`menu_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `sys_tenant`
--

DROP TABLE IF EXISTS `sys_tenant`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_tenant` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_code` varchar(64) NOT NULL,
  `tenant_name` varchar(128) NOT NULL,
  `status` tinyint DEFAULT '1' COMMENT '0冻结 1正常',
  `start_time` timestamp NULL DEFAULT NULL,
  `end_time` timestamp NULL DEFAULT NULL,
  `create_by` varchar(64) DEFAULT NULL,
  `create_time` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `deleted` tinyint DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `tenant_code` (`tenant_code`),
  UNIQUE KEY `tenant_name` (`tenant_name`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `sys_user`
--

DROP TABLE IF EXISTS `sys_user`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_user` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `dept_id` bigint DEFAULT NULL,
  `username` varchar(64) NOT NULL,
  `password` varchar(128) NOT NULL,
  `nickname` varchar(64) DEFAULT NULL,
  `phone` varchar(20) DEFAULT NULL,
  `avatar` varchar(256) DEFAULT NULL,
  `status` tinyint DEFAULT '1' COMMENT '0锁定 1正常',
  `create_time` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `deleted` tinyint DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `username` (`username`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `sys_user_role`
--

DROP TABLE IF EXISTS `sys_user_role`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_user_role` (
  `user_id` bigint NOT NULL,
  `role_id` bigint NOT NULL,
  PRIMARY KEY (`user_id`,`role_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `user_activity_log`
--

DROP TABLE IF EXISTS `user_activity_log`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `user_activity_log` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `user_id` bigint DEFAULT NULL,
  `username` varchar(64) DEFAULT NULL,
  `activity_type` varchar(64) DEFAULT NULL,
  `target_desc` varchar(256) DEFAULT NULL,
  `create_time` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping routines for database 'dataflow'
--
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-06-30 22:32:54
