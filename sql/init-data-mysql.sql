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
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;

--
-- Dumping data for table `agg_cdc_task`
--

LOCK TABLES `agg_cdc_task` WRITE;
/*!40000 ALTER TABLE `agg_cdc_task` DISABLE KEYS */;
/*!40000 ALTER TABLE `agg_cdc_task` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Dumping data for table `agg_dataflow`
--

LOCK TABLES `agg_dataflow` WRITE;
/*!40000 ALTER TABLE `agg_dataflow` DISABLE KEYS */;
INSERT INTO `agg_dataflow` (`id`, `tenant_id`, `name`, `source_id`, `source_name`, `target_id`, `target_name`, `write_mode`, `fault_tolerance`, `pre_sql`, `field_mapping`, `schedule_cron`, `schedule_enabled`, `increment_field_id`, `status`, `read_count`, `write_count`, `last_run_time`, `last_run_status`, `create_by`, `create_time`, `update_time`, `deleted`) VALUES (1,1,'测试流',1,NULL,2,NULL,'insert',0,NULL,NULL,NULL,0,NULL,'NOT_STARTED',0,0,NULL,NULL,'admin','2026-06-30 22:20:29','2026-06-30 22:20:29',0);
INSERT INTO `agg_dataflow` (`id`, `tenant_id`, `name`, `source_id`, `source_name`, `target_id`, `target_name`, `write_mode`, `fault_tolerance`, `pre_sql`, `field_mapping`, `schedule_cron`, `schedule_enabled`, `increment_field_id`, `status`, `read_count`, `write_count`, `last_run_time`, `last_run_status`, `create_by`, `create_time`, `update_time`, `deleted`) VALUES (2,1,'测试流2',2,NULL,1,NULL,'insert',0,NULL,NULL,NULL,0,NULL,'NOT_STARTED',0,0,NULL,NULL,'admin','2026-06-30 22:20:29','2026-06-30 22:20:29',0);
/*!40000 ALTER TABLE `agg_dataflow` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Dumping data for table `agg_datasource`
--

LOCK TABLES `agg_datasource` WRITE;
/*!40000 ALTER TABLE `agg_datasource` DISABLE KEYS */;
INSERT INTO `agg_datasource` (`id`, `tenant_id`, `name`, `db_type`, `host`, `port`, `database_name`, `username`, `password`, `file_path`, `config_json`, `locked`, `create_by`, `create_time`, `update_time`, `deleted`) VALUES (1,1,'业务MySQL库','MySQL','127.0.0.1',3306,'business_db','root','******',NULL,NULL,1,'admin','2026-06-30 07:29:27','2026-06-30 07:29:27',0);
INSERT INTO `agg_datasource` (`id`, `tenant_id`, `name`, `db_type`, `host`, `port`, `database_name`, `username`, `password`, `file_path`, `config_json`, `locked`, `create_by`, `create_time`, `update_time`, `deleted`) VALUES (2,1,'数据仓库PG','PostgreSQL','127.0.0.1',5432,'dw_db','postgres','******',NULL,NULL,1,'admin','2026-06-30 07:29:27','2026-06-30 07:29:27',0);
INSERT INTO `agg_datasource` (`id`, `tenant_id`, `name`, `db_type`, `host`, `port`, `database_name`, `username`, `password`, `file_path`, `config_json`, `locked`, `create_by`, `create_time`, `update_time`, `deleted`) VALUES (3,1,'测试MySQL','MYSQL','192.168.1.1',3306,'test','test','test',NULL,NULL,0,'admin','2026-06-30 22:21:38','2026-06-30 22:21:38',0);
INSERT INTO `agg_datasource` (`id`, `tenant_id`, `name`, `db_type`, `host`, `port`, `database_name`, `username`, `password`, `file_path`, `config_json`, `locked`, `create_by`, `create_time`, `update_time`, `deleted`) VALUES (4,1,'测试MySQL','MYSQL','192.168.1.1',3306,'test','test','test',NULL,NULL,0,'admin','2026-06-30 22:23:41','2026-06-30 22:23:41',0);
/*!40000 ALTER TABLE `agg_datasource` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Dumping data for table `agg_full_sync`
--

LOCK TABLES `agg_full_sync` WRITE;
/*!40000 ALTER TABLE `agg_full_sync` DISABLE KEYS */;
/*!40000 ALTER TABLE `agg_full_sync` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Dumping data for table `agg_increment_field`
--

LOCK TABLES `agg_increment_field` WRITE;
/*!40000 ALTER TABLE `agg_increment_field` DISABLE KEYS */;
/*!40000 ALTER TABLE `agg_increment_field` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Dumping data for table `asset_favorite`
--

LOCK TABLES `asset_favorite` WRITE;
/*!40000 ALTER TABLE `asset_favorite` DISABLE KEYS */;
/*!40000 ALTER TABLE `asset_favorite` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Dumping data for table `asset_field`
--

LOCK TABLES `asset_field` WRITE;
/*!40000 ALTER TABLE `asset_field` DISABLE KEYS */;
INSERT INTO `asset_field` (`id`, `table_id`, `field_en`, `field_cn`, `field_type`, `description`, `is_sensitive`, `create_time`) VALUES (1,1,'user_id','用户ID','BIGINT',NULL,0,'2026-06-30 07:29:27');
INSERT INTO `asset_field` (`id`, `table_id`, `field_en`, `field_cn`, `field_type`, `description`, `is_sensitive`, `create_time`) VALUES (2,1,'phone','手机号','VARCHAR',NULL,1,'2026-06-30 07:29:27');
INSERT INTO `asset_field` (`id`, `table_id`, `field_en`, `field_cn`, `field_type`, `description`, `is_sensitive`, `create_time`) VALUES (3,1,'id_card','身份证号','VARCHAR',NULL,1,'2026-06-30 07:29:27');
/*!40000 ALTER TABLE `asset_field` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Dumping data for table `asset_follow`
--

LOCK TABLES `asset_follow` WRITE;
/*!40000 ALTER TABLE `asset_follow` DISABLE KEYS */;
/*!40000 ALTER TABLE `asset_follow` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Dumping data for table `asset_lineage`
--

LOCK TABLES `asset_lineage` WRITE;
/*!40000 ALTER TABLE `asset_lineage` DISABLE KEYS */;
INSERT INTO `asset_lineage` (`id`, `tenant_id`, `source_table_id`, `source_field_id`, `target_table_id`, `target_field_id`, `relation_type`, `create_time`) VALUES (1,1,1,NULL,2,NULL,'ETL','2026-06-30 07:29:27');
INSERT INTO `asset_lineage` (`id`, `tenant_id`, `source_table_id`, `source_field_id`, `target_table_id`, `target_field_id`, `relation_type`, `create_time`) VALUES (2,1,2,NULL,3,NULL,'CLEAN','2026-06-30 07:29:27');
INSERT INTO `asset_lineage` (`id`, `tenant_id`, `source_table_id`, `source_field_id`, `target_table_id`, `target_field_id`, `relation_type`, `create_time`) VALUES (3,1,3,NULL,4,NULL,'AGG','2026-06-30 07:29:27');
INSERT INTO `asset_lineage` (`id`, `tenant_id`, `source_table_id`, `source_field_id`, `target_table_id`, `target_field_id`, `relation_type`, `create_time`) VALUES (4,1,4,NULL,5,NULL,'REPORT','2026-06-30 07:29:27');
/*!40000 ALTER TABLE `asset_lineage` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Dumping data for table `asset_table`
--

LOCK TABLES `asset_table` WRITE;
/*!40000 ALTER TABLE `asset_table` DISABLE KEYS */;
INSERT INTO `asset_table` (`id`, `tenant_id`, `table_en`, `table_cn`, `database_name`, `datasource_id`, `layer_code`, `row_count`, `is_key_asset`, `create_by`, `create_time`, `update_time`, `deleted`) VALUES (1,1,'ods_user_info','用户信息表','dw_db',NULL,'ODS',10000,1,'admin','2026-06-30 07:29:27','2026-06-30 07:29:27',0);
INSERT INTO `asset_table` (`id`, `tenant_id`, `table_en`, `table_cn`, `database_name`, `datasource_id`, `layer_code`, `row_count`, `is_key_asset`, `create_by`, `create_time`, `update_time`, `deleted`) VALUES (2,1,'std_user_info','用户标准表','dw_db',NULL,'STD',9800,0,'admin','2026-06-30 07:29:27','2026-06-30 07:29:27',0);
INSERT INTO `asset_table` (`id`, `tenant_id`, `table_en`, `table_cn`, `database_name`, `datasource_id`, `layer_code`, `row_count`, `is_key_asset`, `create_by`, `create_time`, `update_time`, `deleted`) VALUES (3,1,'dwd_user_detail','用户明细表','dw_db',NULL,'DWD',9500,0,'admin','2026-06-30 07:29:27','2026-06-30 07:29:27',0);
INSERT INTO `asset_table` (`id`, `tenant_id`, `table_en`, `table_cn`, `database_name`, `datasource_id`, `layer_code`, `row_count`, `is_key_asset`, `create_by`, `create_time`, `update_time`, `deleted`) VALUES (4,1,'dws_user_daily','用户日汇总','dw_db',NULL,'DWS',3650,0,'admin','2026-06-30 07:29:27','2026-06-30 07:29:27',0);
INSERT INTO `asset_table` (`id`, `tenant_id`, `table_en`, `table_cn`, `database_name`, `datasource_id`, `layer_code`, `row_count`, `is_key_asset`, `create_by`, `create_time`, `update_time`, `deleted`) VALUES (5,1,'ads_user_report','用户分析报表','dw_db',NULL,'ADS',120,1,'admin','2026-06-30 07:29:27','2026-06-30 07:29:27',0);
/*!40000 ALTER TABLE `asset_table` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Dumping data for table `asset_tag`
--

LOCK TABLES `asset_tag` WRITE;
/*!40000 ALTER TABLE `asset_tag` DISABLE KEYS */;
/*!40000 ALTER TABLE `asset_tag` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Dumping data for table `asset_tag_relation`
--

LOCK TABLES `asset_tag_relation` WRITE;
/*!40000 ALTER TABLE `asset_tag_relation` DISABLE KEYS */;
/*!40000 ALTER TABLE `asset_tag_relation` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Dumping data for table `dev_file_watcher`
--

LOCK TABLES `dev_file_watcher` WRITE;
/*!40000 ALTER TABLE `dev_file_watcher` DISABLE KEYS */;
/*!40000 ALTER TABLE `dev_file_watcher` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Dumping data for table `dev_hdfs_file`
--

LOCK TABLES `dev_hdfs_file` WRITE;
/*!40000 ALTER TABLE `dev_hdfs_file` DISABLE KEYS */;
INSERT INTO `dev_hdfs_file` (`id`, `tenant_id`, `file_name`, `file_path`, `is_dir`, `file_size`, `content_type`, `parent_path`, `create_by`, `create_time`, `update_time`, `deleted`) VALUES (1,1,'user','/user',1,0,'','/','admin','2026-06-30 12:48:58','2026-06-30 12:48:58',0);
INSERT INTO `dev_hdfs_file` (`id`, `tenant_id`, `file_name`, `file_path`, `is_dir`, `file_size`, `content_type`, `parent_path`, `create_by`, `create_time`, `update_time`, `deleted`) VALUES (2,1,'admin','/user/admin',1,0,'','/user','admin','2026-06-30 12:48:58','2026-06-30 12:48:58',0);
INSERT INTO `dev_hdfs_file` (`id`, `tenant_id`, `file_name`, `file_path`, `is_dir`, `file_size`, `content_type`, `parent_path`, `create_by`, `create_time`, `update_time`, `deleted`) VALUES (3,1,'data','/user/admin/data',1,0,'','/user/admin','admin','2026-06-30 12:48:58','2026-06-30 12:48:58',0);
INSERT INTO `dev_hdfs_file` (`id`, `tenant_id`, `file_name`, `file_path`, `is_dir`, `file_size`, `content_type`, `parent_path`, `create_by`, `create_time`, `update_time`, `deleted`) VALUES (4,1,'scripts','/user/admin/scripts',1,0,'','/user/admin','admin','2026-06-30 12:48:58','2026-06-30 12:48:58',0);
/*!40000 ALTER TABLE `dev_hdfs_file` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Dumping data for table `dev_project`
--

LOCK TABLES `dev_project` WRITE;
/*!40000 ALTER TABLE `dev_project` DISABLE KEYS */;
INSERT INTO `dev_project` (`id`, `tenant_id`, `name`, `domain`, `description`, `create_by`, `create_time`, `deleted`) VALUES (1,1,'政务数据工程','政务','政务数据ETL工程','admin','2026-06-30 07:29:27',0);
INSERT INTO `dev_project` (`id`, `tenant_id`, `name`, `domain`, `description`, `create_by`, `create_time`, `deleted`) VALUES (2,1,'测试项目',NULL,'业务测试','admin','2026-06-30 22:20:30',0);
INSERT INTO `dev_project` (`id`, `tenant_id`, `name`, `domain`, `description`, `create_by`, `create_time`, `deleted`) VALUES (3,1,'测试项目2',NULL,'测试','admin','2026-06-30 22:20:30',0);
/*!40000 ALTER TABLE `dev_project` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Dumping data for table `dev_quality_rule`
--

LOCK TABLES `dev_quality_rule` WRITE;
/*!40000 ALTER TABLE `dev_quality_rule` DISABLE KEYS */;
INSERT INTO `dev_quality_rule` (`id`, `tenant_id`, `name`, `dimension`, `rule_level`, `target_table_id`, `target_field`, `rule_expr`, `rule_config`, `template_type`, `severity`, `create_time`, `update_time`, `deleted`) VALUES (1,1,'用户ID唯一性','唯一性','FIELD',NULL,NULL,'user_id IS UNIQUE',NULL,'唯一性_TEMPLATE','WARNING','2026-06-30 07:29:27','2026-06-30 12:48:03',0);
/*!40000 ALTER TABLE `dev_quality_rule` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Dumping data for table `dev_quality_score_history`
--

LOCK TABLES `dev_quality_score_history` WRITE;
/*!40000 ALTER TABLE `dev_quality_score_history` DISABLE KEYS */;
/*!40000 ALTER TABLE `dev_quality_score_history` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Dumping data for table `dev_quality_task`
--

LOCK TABLES `dev_quality_task` WRITE;
/*!40000 ALTER TABLE `dev_quality_task` DISABLE KEYS */;
/*!40000 ALTER TABLE `dev_quality_task` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Dumping data for table `dev_schedule_execution_log`
--

LOCK TABLES `dev_schedule_execution_log` WRITE;
/*!40000 ALTER TABLE `dev_schedule_execution_log` DISABLE KEYS */;
/*!40000 ALTER TABLE `dev_schedule_execution_log` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Dumping data for table `dev_script`
--

LOCK TABLES `dev_script` WRITE;
/*!40000 ALTER TABLE `dev_script` DISABLE KEYS */;
INSERT INTO `dev_script` (`id`, `tenant_id`, `project_id`, `name`, `script_type`, `datasource_id`, `content`, `description`, `status`, `last_run_time`, `create_by`, `create_time`, `update_time`, `deleted`) VALUES (1,1,NULL,'用户统计SQL','SQL',NULL,'SELECT COUNT(*) FROM ods_user_info',NULL,'DRAFT',NULL,'admin','2026-06-30 07:29:27','2026-06-30 07:29:27',0);
INSERT INTO `dev_script` (`id`, `tenant_id`, `project_id`, `name`, `script_type`, `datasource_id`, `content`, `description`, `status`, `last_run_time`, `create_by`, `create_time`, `update_time`, `deleted`) VALUES (2,1,1,'test.sql','SQL',NULL,'SELECT 1',NULL,'DRAFT',NULL,'admin','2026-06-30 22:20:30','2026-06-30 22:20:30',0);
INSERT INTO `dev_script` (`id`, `tenant_id`, `project_id`, `name`, `script_type`, `datasource_id`, `content`, `description`, `status`, `last_run_time`, `create_by`, `create_time`, `update_time`, `deleted`) VALUES (3,1,1,'test2.sql','SQL',NULL,'SELECT 2',NULL,'DRAFT',NULL,'admin','2026-06-30 22:20:31','2026-06-30 22:20:31',0);
/*!40000 ALTER TABLE `dev_script` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Dumping data for table `dev_script_execution_log`
--

LOCK TABLES `dev_script_execution_log` WRITE;
/*!40000 ALTER TABLE `dev_script_execution_log` DISABLE KEYS */;
/*!40000 ALTER TABLE `dev_script_execution_log` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Dumping data for table `dev_script_version`
--

LOCK TABLES `dev_script_version` WRITE;
/*!40000 ALTER TABLE `dev_script_version` DISABLE KEYS */;
/*!40000 ALTER TABLE `dev_script_version` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Dumping data for table `dev_udf`
--

LOCK TABLES `dev_udf` WRITE;
/*!40000 ALTER TABLE `dev_udf` DISABLE KEYS */;
INSERT INTO `dev_udf` (`id`, `tenant_id`, `udf_name`, `udf_type`, `owner`, `class_name`, `jar_path`, `create_sql`, `return_type`, `param_types`, `description`, `status`, `create_by`, `create_time`, `update_time`, `deleted`) VALUES (1,1,'parse_json_object','HIVE','admin','com.zhangye.udf.ParseJsonObject','/lib/zhangye-udf-1.0.jar','CREATE TEMPORARY FUNCTION parse_json_object AS \'com.zhangye.udf.ParseJsonObject\'',NULL,NULL,'JSON对象解析函数','PUBLISHED','admin','2026-06-30 12:48:58','2026-06-30 14:31:06',0);
INSERT INTO `dev_udf` (`id`, `tenant_id`, `udf_name`, `udf_type`, `owner`, `class_name`, `jar_path`, `create_sql`, `return_type`, `param_types`, `description`, `status`, `create_by`, `create_time`, `update_time`, `deleted`) VALUES (2,1,'format_id_card','SPARK','admin','com.zhangye.udf.FormatIdCard','/lib/zhangye-spark-udf-1.0.jar','CREATE TEMPORARY FUNCTION format_id_card AS \'com.zhangye.udf.FormatIdCard\'',NULL,NULL,'身份证号格式化脱敏函数','DRAFT','admin','2026-06-30 12:48:58','2026-06-30 14:31:06',0);
/*!40000 ALTER TABLE `dev_udf` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Dumping data for table `dev_workflow`
--

LOCK TABLES `dev_workflow` WRITE;
/*!40000 ALTER TABLE `dev_workflow` DISABLE KEYS */;
INSERT INTO `dev_workflow` (`id`, `tenant_id`, `project_id`, `name`, `description`, `dag_json`, `status`, `last_run_time`, `last_run_status`, `schedule_cron`, `schedule_enabled`, `parallel_strategy`, `job_type`, `dep_workflow_ids`, `retry_count`, `retry_interval_sec`, `on_failure`, `alert_enabled`, `alert_channels`, `alert_conditions`, `priority`, `create_by`, `create_time`, `update_time`, `deleted`) VALUES (1,1,1,'用户数据同步流',NULL,'{\"nodes\":[{\"id\":\"1\",\"type\":\"extract\",\"label\":\"数据抽取\",\"position\":{\"x\":80,\"y\":120}},{\"id\":\"2\",\"type\":\"transform\",\"label\":\"数据转换\",\"position\":{\"x\":320,\"y\":120}},{\"id\":\"3\",\"type\":\"quality\",\"label\":\"质量检查\",\"position\":{\"x\":560,\"y\":120}},{\"id\":\"4\",\"type\":\"load\",\"label\":\"数据加载\",\"position\":{\"x\":800,\"y\":120}}],\"edges\":[{\"id\":\"e1-2\",\"source\":\"1\",\"target\":\"2\"},{\"id\":\"e2-3\",\"source\":\"2\",\"target\":\"3\"},{\"id\":\"e3-4\",\"source\":\"3\",\"target\":\"4\"}]}','DRAFT',NULL,NULL,NULL,0,'SKIP','SQL',NULL,0,60,'CONTINUE',0,NULL,NULL,3,'admin','2026-06-30 07:29:27','2026-06-30 07:29:27',0);
INSERT INTO `dev_workflow` (`id`, `tenant_id`, `project_id`, `name`, `description`, `dag_json`, `status`, `last_run_time`, `last_run_status`, `schedule_cron`, `schedule_enabled`, `parallel_strategy`, `job_type`, `dep_workflow_ids`, `retry_count`, `retry_interval_sec`, `on_failure`, `alert_enabled`, `alert_channels`, `alert_conditions`, `priority`, `create_by`, `create_time`, `update_time`, `deleted`) VALUES (2,1,NULL,'测试工作流',NULL,'{\"nodes\":[],\"edges\":[]}','DRAFT',NULL,NULL,NULL,0,'SKIP','SQL',NULL,0,60,'CONTINUE',0,NULL,NULL,3,'admin','2026-06-30 22:20:31','2026-06-30 22:20:31',0);
INSERT INTO `dev_workflow` (`id`, `tenant_id`, `project_id`, `name`, `description`, `dag_json`, `status`, `last_run_time`, `last_run_status`, `schedule_cron`, `schedule_enabled`, `parallel_strategy`, `job_type`, `dep_workflow_ids`, `retry_count`, `retry_interval_sec`, `on_failure`, `alert_enabled`, `alert_channels`, `alert_conditions`, `priority`, `create_by`, `create_time`, `update_time`, `deleted`) VALUES (3,1,NULL,'测试工作流2',NULL,'{\"nodes\":[],\"edges\":[]}','DRAFT',NULL,NULL,NULL,0,'SKIP','SQL',NULL,0,60,'CONTINUE',0,NULL,NULL,3,'admin','2026-06-30 22:20:31','2026-06-30 22:20:31',0);
/*!40000 ALTER TABLE `dev_workflow` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Dumping data for table `gov_atomic_indicator`
--

LOCK TABLES `gov_atomic_indicator` WRITE;
/*!40000 ALTER TABLE `gov_atomic_indicator` DISABLE KEYS */;
INSERT INTO `gov_atomic_indicator` (`id`, `tenant_id`, `indicator_code`, `indicator_type`, `name`, `source_table_id`, `calc_logic`, `agg_function`, `unit`, `parent_id`, `source_field`, `status`, `create_by`, `create_time`, `update_time`, `deleted`) VALUES (1,1,NULL,NULL,'用户数',NULL,'COUNT(user_id)',NULL,NULL,NULL,'user_id',1,NULL,'2026-06-30 07:29:27','2026-06-30 12:48:04',0);
/*!40000 ALTER TABLE `gov_atomic_indicator` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Dumping data for table `gov_business_domain`
--

LOCK TABLES `gov_business_domain` WRITE;
/*!40000 ALTER TABLE `gov_business_domain` DISABLE KEYS */;
/*!40000 ALTER TABLE `gov_business_domain` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Dumping data for table `gov_business_limit`
--

LOCK TABLES `gov_business_limit` WRITE;
/*!40000 ALTER TABLE `gov_business_limit` DISABLE KEYS */;
/*!40000 ALTER TABLE `gov_business_limit` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Dumping data for table `gov_code_set`
--

LOCK TABLES `gov_code_set` WRITE;
/*!40000 ALTER TABLE `gov_code_set` DISABLE KEYS */;
INSERT INTO `gov_code_set` (`id`, `tenant_id`, `catalog_id`, `code`, `name`, `code_value`, `version`, `description`, `create_time`, `deleted`) VALUES (1,1,0,'GENDER','性别','1=男,2=女','1.0','性别代码集','2026-06-30 07:29:27',0);
INSERT INTO `gov_code_set` (`id`, `tenant_id`, `catalog_id`, `code`, `name`, `code_value`, `version`, `description`, `create_time`, `deleted`) VALUES (2,1,0,'STATUS','状态','0=禁用,1=启用','1.0','通用状态','2026-06-30 07:29:27',0);
/*!40000 ALTER TABLE `gov_code_set` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Dumping data for table `gov_composite_indicator`
--

LOCK TABLES `gov_composite_indicator` WRITE;
/*!40000 ALTER TABLE `gov_composite_indicator` DISABLE KEYS */;
/*!40000 ALTER TABLE `gov_composite_indicator` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Dumping data for table `gov_cross_domain_metadata`
--

LOCK TABLES `gov_cross_domain_metadata` WRITE;
/*!40000 ALTER TABLE `gov_cross_domain_metadata` DISABLE KEYS */;
INSERT INTO `gov_cross_domain_metadata` (`id`, `tenant_id`, `source_name`, `source_type`, `export_format`, `metadata_json`, `sync_status`, `version`, `create_by`, `create_time`, `update_time`, `deleted`) VALUES (1,1,'å…¬å®‰äººå£åº“','MYSQL','JSON','{\"tables\":[\"person_info\",\"household\"],\"fields\":{\"person_info\":[\"id\",\"name\",\"id_card\",\"address\"],\"household\":[\"id\",\"household_no\",\"address\",\"member_count\"]}}','SYNCED','1.0','admin','2026-06-30 12:48:58','2026-06-30 12:48:58',0);
INSERT INTO `gov_cross_domain_metadata` (`id`, `tenant_id`, `source_name`, `source_type`, `export_format`, `metadata_json`, `sync_status`, `version`, `create_by`, `create_time`, `update_time`, `deleted`) VALUES (2,1,'æ°‘æ”¿æ•‘åŠ©ç³»ç»Ÿ','ORACLE','JSON','{\"tables\":[\"subsidy_record\",\"low_income_family\"]}','PENDING','1.0','admin','2026-06-30 12:48:58','2026-06-30 12:48:58',0);
/*!40000 ALTER TABLE `gov_cross_domain_metadata` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Dumping data for table `gov_derived_indicator`
--

LOCK TABLES `gov_derived_indicator` WRITE;
/*!40000 ALTER TABLE `gov_derived_indicator` DISABLE KEYS */;
/*!40000 ALTER TABLE `gov_derived_indicator` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Dumping data for table `gov_model_table`
--

LOCK TABLES `gov_model_table` WRITE;
/*!40000 ALTER TABLE `gov_model_table` DISABLE KEYS */;
INSERT INTO `gov_model_table` (`id`, `tenant_id`, `layer_id`, `catalog_id`, `layer_code`, `table_en`, `table_cn`, `table_type`, `source_table`, `etl_sql`, `etl_filter`, `datasource_id`, `clean_rules`, `problem_table_enabled`, `problem_write_mode`, `status`, `remark`, `create_by`, `create_time`, `update_time`, `deleted`) VALUES (1,1,NULL,NULL,'ODS','ods_user_info','用户信息贴源表',NULL,NULL,NULL,NULL,NULL,NULL,0,NULL,'DRAFT',NULL,'admin','2026-06-30 07:29:27','2026-06-30 07:29:27',0);
INSERT INTO `gov_model_table` (`id`, `tenant_id`, `layer_id`, `catalog_id`, `layer_code`, `table_en`, `table_cn`, `table_type`, `source_table`, `etl_sql`, `etl_filter`, `datasource_id`, `clean_rules`, `problem_table_enabled`, `problem_write_mode`, `status`, `remark`, `create_by`, `create_time`, `update_time`, `deleted`) VALUES (2,1,NULL,NULL,'STD','std_user_info','用户信息标准表',NULL,NULL,NULL,NULL,NULL,'空值过滤,除去空白',0,NULL,'DRAFT',NULL,'admin','2026-06-30 07:29:27','2026-06-30 07:29:27',0);
/*!40000 ALTER TABLE `gov_model_table` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Dumping data for table `gov_proxy_node`
--

LOCK TABLES `gov_proxy_node` WRITE;
/*!40000 ALTER TABLE `gov_proxy_node` DISABLE KEYS */;
INSERT INTO `gov_proxy_node` (`id`, `tenant_id`, `name`, `host`, `port`, `node_type`, `description`, `status`, `create_by`, `remark`, `create_time`, `update_time`, `deleted`) VALUES (1,1,'主数据代理节点','10.0.1.100',9090,'DATA_PROXY','主数据代理服务器','RUNNING','admin',NULL,'2026-06-30 12:48:58','2026-06-30 12:48:58',0);
INSERT INTO `gov_proxy_node` (`id`, `tenant_id`, `name`, `host`, `port`, `node_type`, `description`, `status`, `create_by`, `remark`, `create_time`, `update_time`, `deleted`) VALUES (2,1,'备份数据代理节点','10.0.1.101',9090,'DATA_PROXY','备份数据代理服务器','STOPPED','admin',NULL,'2026-06-30 12:48:58','2026-06-30 12:48:58',0);
/*!40000 ALTER TABLE `gov_proxy_node` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Dumping data for table `gov_standard_catalog`
--

LOCK TABLES `gov_standard_catalog` WRITE;
/*!40000 ALTER TABLE `gov_standard_catalog` DISABLE KEYS */;
/*!40000 ALTER TABLE `gov_standard_catalog` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Dumping data for table `gov_stat_period`
--

LOCK TABLES `gov_stat_period` WRITE;
/*!40000 ALTER TABLE `gov_stat_period` DISABLE KEYS */;
INSERT INTO `gov_stat_period` (`id`, `tenant_id`, `name`, `cron_expr`, `status`, `create_time`, `update_time`, `deleted`) VALUES (1,1,'日统计','0 0 1 * * ?',1,'2026-06-30 07:29:27','2026-06-30 07:29:27',0);
INSERT INTO `gov_stat_period` (`id`, `tenant_id`, `name`, `cron_expr`, `status`, `create_time`, `update_time`, `deleted`) VALUES (2,1,'月统计','0 0 1 1 * ?',1,'2026-06-30 07:29:27','2026-06-30 07:29:27',0);
/*!40000 ALTER TABLE `gov_stat_period` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Dumping data for table `gov_warehouse_catalog`
--

LOCK TABLES `gov_warehouse_catalog` WRITE;
/*!40000 ALTER TABLE `gov_warehouse_catalog` DISABLE KEYS */;
/*!40000 ALTER TABLE `gov_warehouse_catalog` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Dumping data for table `gov_warehouse_layer`
--

LOCK TABLES `gov_warehouse_layer` WRITE;
/*!40000 ALTER TABLE `gov_warehouse_layer` DISABLE KEYS */;
INSERT INTO `gov_warehouse_layer` (`id`, `tenant_id`, `layer_code`, `layer_name`, `description`, `sort_order`, `create_time`, `deleted`) VALUES (1,1,'ODS','贴源层','原始数据层',1,'2026-06-30 07:29:27',0);
INSERT INTO `gov_warehouse_layer` (`id`, `tenant_id`, `layer_code`, `layer_name`, `description`, `sort_order`, `create_time`, `deleted`) VALUES (2,1,'STD','标准层','数据标准层',2,'2026-06-30 07:29:27',0);
INSERT INTO `gov_warehouse_layer` (`id`, `tenant_id`, `layer_code`, `layer_name`, `description`, `sort_order`, `create_time`, `deleted`) VALUES (3,1,'DWD','明细层','明细数据层',3,'2026-06-30 07:29:27',0);
INSERT INTO `gov_warehouse_layer` (`id`, `tenant_id`, `layer_code`, `layer_name`, `description`, `sort_order`, `create_time`, `deleted`) VALUES (4,1,'DWS','汇总层','轻度汇总层',4,'2026-06-30 07:29:27',0);
INSERT INTO `gov_warehouse_layer` (`id`, `tenant_id`, `layer_code`, `layer_name`, `description`, `sort_order`, `create_time`, `deleted`) VALUES (5,1,'ADS','应用层','应用数据层',5,'2026-06-30 07:29:27',0);
/*!40000 ALTER TABLE `gov_warehouse_layer` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Dumping data for table `gov_word`
--

LOCK TABLES `gov_word` WRITE;
/*!40000 ALTER TABLE `gov_word` DISABLE KEYS */;
/*!40000 ALTER TABLE `gov_word` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Dumping data for table `infra_cluster`
--

LOCK TABLES `infra_cluster` WRITE;
/*!40000 ALTER TABLE `infra_cluster` DISABLE KEYS */;
INSERT INTO `infra_cluster` (`id`, `tenant_id`, `name`, `description`, `host_count`, `status`, `create_time`, `deleted`) VALUES (1,1,'张掖大数据集群','生产环境主集群',2,'NORMAL','2026-06-30 07:29:27',0);
/*!40000 ALTER TABLE `infra_cluster` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Dumping data for table `infra_component`
--

LOCK TABLES `infra_component` WRITE;
/*!40000 ALTER TABLE `infra_component` DISABLE KEYS */;
INSERT INTO `infra_component` (`id`, `tenant_id`, `host_id`, `component_type`, `version`, `status`, `create_time`, `update_time`, `deleted`) VALUES (1,1,1,'HDFS','3.3.4','RUNNING','2026-06-30 07:29:27','2026-06-30 07:29:27',0);
INSERT INTO `infra_component` (`id`, `tenant_id`, `host_id`, `component_type`, `version`, `status`, `create_time`, `update_time`, `deleted`) VALUES (2,1,1,'YARN','3.3.4','RUNNING','2026-06-30 07:29:27','2026-06-30 07:29:27',0);
INSERT INTO `infra_component` (`id`, `tenant_id`, `host_id`, `component_type`, `version`, `status`, `create_time`, `update_time`, `deleted`) VALUES (3,1,2,'Kafka','3.4.0','RUNNING','2026-06-30 07:29:27','2026-06-30 07:29:27',0);
/*!40000 ALTER TABLE `infra_component` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Dumping data for table `infra_host`
--

LOCK TABLES `infra_host` WRITE;
/*!40000 ALTER TABLE `infra_host` DISABLE KEYS */;
INSERT INTO `infra_host` (`id`, `tenant_id`, `hostname`, `ip`, `cpu_cores`, `memory_gb`, `disk_gb`, `status`, `create_time`, `deleted`) VALUES (1,1,'node-master','192.168.1.101',16,64,2000,'ONLINE','2026-06-30 07:29:27',0);
INSERT INTO `infra_host` (`id`, `tenant_id`, `hostname`, `ip`, `cpu_cores`, `memory_gb`, `disk_gb`, `status`, `create_time`, `deleted`) VALUES (2,1,'node-worker-1','192.168.1.102',32,128,4000,'ONLINE','2026-06-30 07:29:27',0);
/*!40000 ALTER TABLE `infra_host` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Dumping data for table `infra_resource_group`
--

LOCK TABLES `infra_resource_group` WRITE;
/*!40000 ALTER TABLE `infra_resource_group` DISABLE KEYS */;
/*!40000 ALTER TABLE `infra_resource_group` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Dumping data for table `meta_collector_log`
--

LOCK TABLES `meta_collector_log` WRITE;
/*!40000 ALTER TABLE `meta_collector_log` DISABLE KEYS */;
/*!40000 ALTER TABLE `meta_collector_log` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Dumping data for table `meta_collector_task`
--

LOCK TABLES `meta_collector_task` WRITE;
/*!40000 ALTER TABLE `meta_collector_task` DISABLE KEYS */;
/*!40000 ALTER TABLE `meta_collector_task` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Dumping data for table `storage_quota`
--

LOCK TABLES `storage_quota` WRITE;
/*!40000 ALTER TABLE `storage_quota` DISABLE KEYS */;
/*!40000 ALTER TABLE `storage_quota` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Dumping data for table `svc_api`
--

LOCK TABLES `svc_api` WRITE;
/*!40000 ALTER TABLE `svc_api` DISABLE KEYS */;
/*!40000 ALTER TABLE `svc_api` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Dumping data for table `svc_api_call_log`
--

LOCK TABLES `svc_api_call_log` WRITE;
/*!40000 ALTER TABLE `svc_api_call_log` DISABLE KEYS */;
/*!40000 ALTER TABLE `svc_api_call_log` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Dumping data for table `svc_application`
--

LOCK TABLES `svc_application` WRITE;
/*!40000 ALTER TABLE `svc_application` DISABLE KEYS */;
INSERT INTO `svc_application` (`id`, `tenant_id`, `app_name`, `app_key`, `app_secret`, `description`, `status`, `create_time`, `deleted`) VALUES (1,1,'数字政府门户','app_gov_portal','sec_gov_portal_2026','张掖市数字政府门户应用',1,'2026-06-30 12:48:58',0);
INSERT INTO `svc_application` (`id`, `tenant_id`, `app_name`, `app_key`, `app_secret`, `description`, `status`, `create_time`, `deleted`) VALUES (2,1,'政务数据共享平台','app_data_share','sec_data_share_2026','政务数据共享交换应用',1,'2026-06-30 12:48:58',0);
/*!40000 ALTER TABLE `svc_application` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Dumping data for table `svc_catalog`
--

LOCK TABLES `svc_catalog` WRITE;
/*!40000 ALTER TABLE `svc_catalog` DISABLE KEYS */;
INSERT INTO `svc_catalog` (`id`, `tenant_id`, `project_id`, `parent_id`, `name`, `enabled`, `create_time`, `deleted`) VALUES (1,1,1,0,'人口服务',1,'2026-06-30 07:29:27',0);
INSERT INTO `svc_catalog` (`id`, `tenant_id`, `project_id`, `parent_id`, `name`, `enabled`, `create_time`, `deleted`) VALUES (2,1,1,1,'用户信息',1,'2026-06-30 07:29:27',0);
/*!40000 ALTER TABLE `svc_catalog` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Dumping data for table `svc_catalog_project`
--

LOCK TABLES `svc_catalog_project` WRITE;
/*!40000 ALTER TABLE `svc_catalog_project` DISABLE KEYS */;
INSERT INTO `svc_catalog_project` (`id`, `tenant_id`, `project_name`, `description`, `enabled`, `create_time`, `deleted`) VALUES (1,1,'政务服务目录','政务数据API服务目录',1,'2026-06-30 07:29:27',0);
/*!40000 ALTER TABLE `svc_catalog_project` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Dumping data for table `svc_rate_limit`
--

LOCK TABLES `svc_rate_limit` WRITE;
/*!40000 ALTER TABLE `svc_rate_limit` DISABLE KEYS */;
/*!40000 ALTER TABLE `svc_rate_limit` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Dumping data for table `svc_service_unit`
--

LOCK TABLES `svc_service_unit` WRITE;
/*!40000 ALTER TABLE `svc_service_unit` DISABLE KEYS */;
INSERT INTO `svc_service_unit` (`id`, `tenant_id`, `unit_name`, `unit_type`, `datasource_id`, `query_template`, `description`, `status`, `create_by`, `create_time`, `update_time`, `deleted`) VALUES (1,1,'用户信息服务单元',NULL,NULL,NULL,'提供用户基础信息查询',1,NULL,'2026-06-30 07:29:27','2026-06-30 07:29:27',0);
/*!40000 ALTER TABLE `svc_service_unit` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Dumping data for table `svc_workorder`
--

LOCK TABLES `svc_workorder` WRITE;
/*!40000 ALTER TABLE `svc_workorder` DISABLE KEYS */;
/*!40000 ALTER TABLE `svc_workorder` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Dumping data for table `sys_config`
--

LOCK TABLES `sys_config` WRITE;
/*!40000 ALTER TABLE `sys_config` DISABLE KEYS */;
/*!40000 ALTER TABLE `sys_config` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Dumping data for table `sys_dept`
--

LOCK TABLES `sys_dept` WRITE;
/*!40000 ALTER TABLE `sys_dept` DISABLE KEYS */;
INSERT INTO `sys_dept` (`id`, `tenant_id`, `parent_id`, `dept_name`, `sort_order`, `create_time`, `update_time`, `deleted`) VALUES (1,1,0,'张掖市政府',1,'2026-06-30 07:29:26','2026-06-30 07:29:26',0);
INSERT INTO `sys_dept` (`id`, `tenant_id`, `parent_id`, `dept_name`, `sort_order`, `create_time`, `update_time`, `deleted`) VALUES (2,1,1,'大数据中心',1,'2026-06-30 07:29:26','2026-06-30 07:29:26',0);
INSERT INTO `sys_dept` (`id`, `tenant_id`, `parent_id`, `dept_name`, `sort_order`, `create_time`, `update_time`, `deleted`) VALUES (3,1,1,'信息中心',2,'2026-06-30 07:29:26','2026-06-30 07:29:26',0);
/*!40000 ALTER TABLE `sys_dept` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Dumping data for table `sys_menu`
--

LOCK TABLES `sys_menu` WRITE;
/*!40000 ALTER TABLE `sys_menu` DISABLE KEYS */;
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_code`, `menu_name`, `description`, `path`, `component`, `menu_type`, `service_name`, `icon`, `sort_order`, `visible`, `create_time`, `update_time`, `deleted`) VALUES (1,0,'dashboard','首页',NULL,'/dashboard','dashboard/index',2,'portal','HomeFilled',0,1,'2026-06-30 07:29:26','2026-06-30 07:29:26',0);
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_code`, `menu_name`, `description`, `path`, `component`, `menu_type`, `service_name`, `icon`, `sort_order`, `visible`, `create_time`, `update_time`, `deleted`) VALUES (10,0,'portal','统一门户',NULL,'/portal',NULL,1,'portal','User',1,1,'2026-06-30 07:29:26','2026-06-30 07:29:26',0);
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_code`, `menu_name`, `description`, `path`, `component`, `menu_type`, `service_name`, `icon`, `sort_order`, `visible`, `create_time`, `update_time`, `deleted`) VALUES (11,10,'tenant','租户管理',NULL,'/portal/tenant','portal/tenant/index',2,'portal',NULL,1,1,'2026-06-30 07:29:26','2026-06-30 07:29:26',0);
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_code`, `menu_name`, `description`, `path`, `component`, `menu_type`, `service_name`, `icon`, `sort_order`, `visible`, `create_time`, `update_time`, `deleted`) VALUES (12,10,'user','用户管理',NULL,'/portal/user','portal/user/index',2,'portal',NULL,2,1,'2026-06-30 07:29:26','2026-06-30 07:29:26',0);
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_code`, `menu_name`, `description`, `path`, `component`, `menu_type`, `service_name`, `icon`, `sort_order`, `visible`, `create_time`, `update_time`, `deleted`) VALUES (13,10,'role','角色管理',NULL,'/portal/role','portal/role/index',2,'portal',NULL,3,1,'2026-06-30 07:29:26','2026-06-30 07:29:26',0);
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_code`, `menu_name`, `description`, `path`, `component`, `menu_type`, `service_name`, `icon`, `sort_order`, `visible`, `create_time`, `update_time`, `deleted`) VALUES (14,10,'dept','部门管理',NULL,'/portal/dept','portal/dept/index',2,'portal',NULL,4,1,'2026-06-30 07:29:26','2026-06-30 07:29:26',0);
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_code`, `menu_name`, `description`, `path`, `component`, `menu_type`, `service_name`, `icon`, `sort_order`, `visible`, `create_time`, `update_time`, `deleted`) VALUES (15,10,'menu','菜单管理',NULL,'/portal/menu','portal/menu/index',2,'portal',NULL,5,1,'2026-06-30 07:29:26','2026-06-30 07:29:26',0);
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_code`, `menu_name`, `description`, `path`, `component`, `menu_type`, `service_name`, `icon`, `sort_order`, `visible`, `create_time`, `update_time`, `deleted`) VALUES (16,10,'log','日志管理',NULL,'/portal/log','portal/log/index',2,'portal',NULL,6,1,'2026-06-30 07:29:26','2026-06-30 07:29:26',0);
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_code`, `menu_name`, `description`, `path`, `component`, `menu_type`, `service_name`, `icon`, `sort_order`, `visible`, `create_time`, `update_time`, `deleted`) VALUES (20,0,'aggregation','数据汇聚',NULL,'/aggregation',NULL,1,'aggregation','Connection',2,1,'2026-06-30 07:29:26','2026-06-30 07:29:26',0);
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_code`, `menu_name`, `description`, `path`, `component`, `menu_type`, `service_name`, `icon`, `sort_order`, `visible`, `create_time`, `update_time`, `deleted`) VALUES (21,20,'datasource','数据源管理',NULL,'/aggregation/datasource','aggregation/datasource/index',2,'aggregation',NULL,1,1,'2026-06-30 07:29:26','2026-06-30 07:29:26',0);
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_code`, `menu_name`, `description`, `path`, `component`, `menu_type`, `service_name`, `icon`, `sort_order`, `visible`, `create_time`, `update_time`, `deleted`) VALUES (22,20,'increment','增量字段',NULL,'/aggregation/increment','aggregation/increment/index',2,'aggregation',NULL,2,1,'2026-06-30 07:29:26','2026-06-30 07:29:26',0);
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_code`, `menu_name`, `description`, `path`, `component`, `menu_type`, `service_name`, `icon`, `sort_order`, `visible`, `create_time`, `update_time`, `deleted`) VALUES (23,20,'dataflow','数据流管理',NULL,'/aggregation/dataflow','aggregation/dataflow/index',2,'aggregation',NULL,3,1,'2026-06-30 07:29:26','2026-06-30 07:29:26',0);
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_code`, `menu_name`, `description`, `path`, `component`, `menu_type`, `service_name`, `icon`, `sort_order`, `visible`, `create_time`, `update_time`, `deleted`) VALUES (24,20,'agg_monitor','数据流监控',NULL,'/aggregation/monitor','aggregation/monitor/index',2,'aggregation',NULL,4,1,'2026-06-30 07:29:26','2026-06-30 07:29:26',0);
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_code`, `menu_name`, `description`, `path`, `component`, `menu_type`, `service_name`, `icon`, `sort_order`, `visible`, `create_time`, `update_time`, `deleted`) VALUES (25,20,'fullsync','整库同步',NULL,'/aggregation/fullsync','aggregation/fullsync/index',2,'aggregation',NULL,5,1,'2026-06-30 07:29:26','2026-06-30 07:29:26',0);
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_code`, `menu_name`, `description`, `path`, `component`, `menu_type`, `service_name`, `icon`, `sort_order`, `visible`, `create_time`, `update_time`, `deleted`) VALUES (26,20,'cdc','CDC实时同步',NULL,'/aggregation/cdc','aggregation/cdc/index',2,'aggregation',NULL,6,1,'2026-06-30 07:29:26','2026-06-30 07:29:26',0);
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_code`, `menu_name`, `description`, `path`, `component`, `menu_type`, `service_name`, `icon`, `sort_order`, `visible`, `create_time`, `update_time`, `deleted`) VALUES (30,0,'governance','数据治理',NULL,'/governance',NULL,1,'governance','DataAnalysis',3,1,'2026-06-30 07:29:26','2026-06-30 07:29:26',0);
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_code`, `menu_name`, `description`, `path`, `component`, `menu_type`, `service_name`, `icon`, `sort_order`, `visible`, `create_time`, `update_time`, `deleted`) VALUES (31,30,'standard','数据标准',NULL,'/governance/standard','governance/standard/index',2,'governance',NULL,1,1,'2026-06-30 07:29:26','2026-06-30 07:29:26',0);
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_code`, `menu_name`, `description`, `path`, `component`, `menu_type`, `service_name`, `icon`, `sort_order`, `visible`, `create_time`, `update_time`, `deleted`) VALUES (32,30,'warehouse','仓库分层',NULL,'/governance/warehouse','governance/warehouse/index',2,'governance',NULL,2,1,'2026-06-30 07:29:26','2026-06-30 07:29:26',0);
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_code`, `menu_name`, `description`, `path`, `component`, `menu_type`, `service_name`, `icon`, `sort_order`, `visible`, `create_time`, `update_time`, `deleted`) VALUES (33,30,'modeling','数据建模',NULL,'/governance/modeling','governance/modeling/index',2,'governance',NULL,3,1,'2026-06-30 07:29:26','2026-06-30 07:29:26',0);
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_code`, `menu_name`, `description`, `path`, `component`, `menu_type`, `service_name`, `icon`, `sort_order`, `visible`, `create_time`, `update_time`, `deleted`) VALUES (34,30,'indicator','指标管理',NULL,'/governance/indicator','governance/indicator/index',2,'governance',NULL,4,1,'2026-06-30 07:29:26','2026-06-30 07:29:26',0);
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_code`, `menu_name`, `description`, `path`, `component`, `menu_type`, `service_name`, `icon`, `sort_order`, `visible`, `create_time`, `update_time`, `deleted`) VALUES (40,0,'development','数据开发',NULL,'/development',NULL,1,'development','Edit',4,1,'2026-06-30 07:29:26','2026-06-30 07:29:26',0);
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_code`, `menu_name`, `description`, `path`, `component`, `menu_type`, `service_name`, `icon`, `sort_order`, `visible`, `create_time`, `update_time`, `deleted`) VALUES (41,40,'workflow','工作流开发',NULL,'/development/workflow','development/workflow/index',2,'development',NULL,1,1,'2026-06-30 07:29:26','2026-06-30 07:29:26',0);
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_code`, `menu_name`, `description`, `path`, `component`, `menu_type`, `service_name`, `icon`, `sort_order`, `visible`, `create_time`, `update_time`, `deleted`) VALUES (42,40,'script','脚本开发',NULL,'/development/script','development/script/index',2,'development',NULL,2,1,'2026-06-30 07:29:26','2026-06-30 07:29:26',0);
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_code`, `menu_name`, `description`, `path`, `component`, `menu_type`, `service_name`, `icon`, `sort_order`, `visible`, `create_time`, `update_time`, `deleted`) VALUES (43,40,'quality','数据质量',NULL,'/development/quality','development/quality/index',2,'development',NULL,3,1,'2026-06-30 07:29:26','2026-06-30 07:29:26',0);
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_code`, `menu_name`, `description`, `path`, `component`, `menu_type`, `service_name`, `icon`, `sort_order`, `visible`, `create_time`, `update_time`, `deleted`) VALUES (44,40,'schedule','数据调度',NULL,'/development/schedule','development/schedule/index',2,'development',NULL,4,1,'2026-06-30 07:29:26','2026-06-30 07:29:26',0);
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_code`, `menu_name`, `description`, `path`, `component`, `menu_type`, `service_name`, `icon`, `sort_order`, `visible`, `create_time`, `update_time`, `deleted`) VALUES (50,0,'service','数据服务',NULL,'/service',NULL,1,'service','Share',5,1,'2026-06-30 07:29:26','2026-06-30 07:29:26',0);
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_code`, `menu_name`, `description`, `path`, `component`, `menu_type`, `service_name`, `icon`, `sort_order`, `visible`, `create_time`, `update_time`, `deleted`) VALUES (51,50,'application','应用管理',NULL,'/service/application','service/application/index',2,'service',NULL,1,1,'2026-06-30 07:29:26','2026-06-30 07:29:26',0);
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_code`, `menu_name`, `description`, `path`, `component`, `menu_type`, `service_name`, `icon`, `sort_order`, `visible`, `create_time`, `update_time`, `deleted`) VALUES (52,50,'api','API管理',NULL,'/service/api','service/api/index',2,'service',NULL,2,1,'2026-06-30 07:29:26','2026-06-30 07:29:26',0);
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_code`, `menu_name`, `description`, `path`, `component`, `menu_type`, `service_name`, `icon`, `sort_order`, `visible`, `create_time`, `update_time`, `deleted`) VALUES (53,50,'catalog','服务目录',NULL,'/service/catalog','service/catalog/index',2,'service',NULL,3,1,'2026-06-30 07:29:26','2026-06-30 07:29:26',0);
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_code`, `menu_name`, `description`, `path`, `component`, `menu_type`, `service_name`, `icon`, `sort_order`, `visible`, `create_time`, `update_time`, `deleted`) VALUES (54,50,'workorder','工单管理',NULL,'/service/workorder','service/workorder/index',2,'service',NULL,4,1,'2026-06-30 07:29:26','2026-06-30 07:29:26',0);
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_code`, `menu_name`, `description`, `path`, `component`, `menu_type`, `service_name`, `icon`, `sort_order`, `visible`, `create_time`, `update_time`, `deleted`) VALUES (60,0,'asset','数据资产',NULL,'/asset',NULL,1,'asset','PieChart',6,1,'2026-06-30 07:29:26','2026-06-30 07:29:26',0);
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_code`, `menu_name`, `description`, `path`, `component`, `menu_type`, `service_name`, `icon`, `sort_order`, `visible`, `create_time`, `update_time`, `deleted`) VALUES (61,60,'overview','数据总览',NULL,'/asset/overview','asset/overview/index',2,'asset',NULL,1,1,'2026-06-30 07:29:26','2026-06-30 07:29:26',0);
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_code`, `menu_name`, `description`, `path`, `component`, `menu_type`, `service_name`, `icon`, `sort_order`, `visible`, `create_time`, `update_time`, `deleted`) VALUES (62,60,'map','数据地图',NULL,'/asset/map','asset/map/index',2,'asset',NULL,2,1,'2026-06-30 07:29:26','2026-06-30 07:29:26',0);
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_code`, `menu_name`, `description`, `path`, `component`, `menu_type`, `service_name`, `icon`, `sort_order`, `visible`, `create_time`, `update_time`, `deleted`) VALUES (63,60,'asset_monitor','资产监控',NULL,'/asset/monitor','asset/monitor/index',2,'asset',NULL,3,1,'2026-06-30 07:29:26','2026-06-30 07:29:26',0);
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_code`, `menu_name`, `description`, `path`, `component`, `menu_type`, `service_name`, `icon`, `sort_order`, `visible`, `create_time`, `update_time`, `deleted`) VALUES (70,0,'infrastructure','基础支撑',NULL,'/infrastructure',NULL,1,'infrastructure','Monitor',7,1,'2026-06-30 07:29:26','2026-06-30 07:29:26',0);
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_code`, `menu_name`, `description`, `path`, `component`, `menu_type`, `service_name`, `icon`, `sort_order`, `visible`, `create_time`, `update_time`, `deleted`) VALUES (71,70,'component','组件管理',NULL,'/infrastructure/component','infrastructure/component/index',2,'infrastructure',NULL,1,1,'2026-06-30 07:29:26','2026-06-30 07:29:26',0);
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_code`, `menu_name`, `description`, `path`, `component`, `menu_type`, `service_name`, `icon`, `sort_order`, `visible`, `create_time`, `update_time`, `deleted`) VALUES (72,70,'cluster','集群管理',NULL,'/infrastructure/cluster','infrastructure/cluster/index',2,'infrastructure',NULL,2,1,'2026-06-30 07:29:26','2026-06-30 07:29:26',0);
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_code`, `menu_name`, `description`, `path`, `component`, `menu_type`, `service_name`, `icon`, `sort_order`, `visible`, `create_time`, `update_time`, `deleted`) VALUES (73,0,'portal_profile','个人中心',NULL,'/portal/profile','portal/profile/index',2,NULL,'UserFilled',99,1,'2026-06-30 12:48:58','2026-06-30 12:48:58',0);
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_code`, `menu_name`, `description`, `path`, `component`, `menu_type`, `service_name`, `icon`, `sort_order`, `visible`, `create_time`, `update_time`, `deleted`) VALUES (74,0,'gov_proxy','代理节点',NULL,'/governance/proxynode','governance/proxynode/index',2,NULL,'Connection',40,1,'2026-06-30 12:48:58','2026-06-30 12:48:58',0);
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_code`, `menu_name`, `description`, `path`, `component`, `menu_type`, `service_name`, `icon`, `sort_order`, `visible`, `create_time`, `update_time`, `deleted`) VALUES (75,0,'gov_monitor','治理监控',NULL,'/governance/monitor','governance/monitor/index',2,NULL,'Monitor',50,1,'2026-06-30 12:48:58','2026-06-30 12:48:58',0);
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_code`, `menu_name`, `description`, `path`, `component`, `menu_type`, `service_name`, `icon`, `sort_order`, `visible`, `create_time`, `update_time`, `deleted`) VALUES (76,0,'svc_unit','服务单元',NULL,'/service/unit','service/unit/index',2,NULL,'Grid',30,1,'2026-06-30 12:48:58','2026-06-30 12:48:58',0);
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_code`, `menu_name`, `description`, `path`, `component`, `menu_type`, `service_name`, `icon`, `sort_order`, `visible`, `create_time`, `update_time`, `deleted`) VALUES (77,0,'svc_ratelimit','流控策略',NULL,'/service/ratelimit','service/ratelimit/index',2,NULL,'Timer',40,1,'2026-06-30 12:48:58','2026-06-30 12:48:58',0);
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_code`, `menu_name`, `description`, `path`, `component`, `menu_type`, `service_name`, `icon`, `sort_order`, `visible`, `create_time`, `update_time`, `deleted`) VALUES (78,0,'svc_monitor','服务监控',NULL,'/service/monitor','service/monitor/index',2,NULL,'DataLine',50,1,'2026-06-30 12:48:58','2026-06-30 12:48:58',0);
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_code`, `menu_name`, `description`, `path`, `component`, `menu_type`, `service_name`, `icon`, `sort_order`, `visible`, `create_time`, `update_time`, `deleted`) VALUES (79,0,'portal_security','安全管理',NULL,'/portal/security','portal/security/index',2,NULL,'Lock',98,1,'2026-06-30 12:48:58','2026-06-30 12:48:58',0);
/*!40000 ALTER TABLE `sys_menu` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Dumping data for table `sys_oper_log`
--

LOCK TABLES `sys_oper_log` WRITE;
/*!40000 ALTER TABLE `sys_oper_log` DISABLE KEYS */;
INSERT INTO `sys_oper_log` (`id`, `tenant_id`, `user_id`, `username`, `event_name`, `result`, `method`, `resource_type`, `resource_name`, `ip`, `detail`, `create_time`) VALUES (1,NULL,NULL,NULL,'用户登录',1,'POST','认证','admin','127.0.0.1','登录成功','2026-06-30 21:45:46');
INSERT INTO `sys_oper_log` (`id`, `tenant_id`, `user_id`, `username`, `event_name`, `result`, `method`, `resource_type`, `resource_name`, `ip`, `detail`, `create_time`) VALUES (2,NULL,NULL,NULL,'用户登录',1,'POST','认证','admin','127.0.0.1','登录成功','2026-06-30 21:45:52');
INSERT INTO `sys_oper_log` (`id`, `tenant_id`, `user_id`, `username`, `event_name`, `result`, `method`, `resource_type`, `resource_name`, `ip`, `detail`, `create_time`) VALUES (3,NULL,NULL,NULL,'用户登录',1,'POST','认证','admin','127.0.0.1','登录成功','2026-06-30 21:45:57');
INSERT INTO `sys_oper_log` (`id`, `tenant_id`, `user_id`, `username`, `event_name`, `result`, `method`, `resource_type`, `resource_name`, `ip`, `detail`, `create_time`) VALUES (4,NULL,NULL,NULL,'用户登录',1,'POST','认证','admin','127.0.0.1','登录成功','2026-06-30 21:46:32');
INSERT INTO `sys_oper_log` (`id`, `tenant_id`, `user_id`, `username`, `event_name`, `result`, `method`, `resource_type`, `resource_name`, `ip`, `detail`, `create_time`) VALUES (5,NULL,NULL,NULL,'用户登录',1,'POST','认证','admin','127.0.0.1','登录成功','2026-06-30 21:46:40');
INSERT INTO `sys_oper_log` (`id`, `tenant_id`, `user_id`, `username`, `event_name`, `result`, `method`, `resource_type`, `resource_name`, `ip`, `detail`, `create_time`) VALUES (6,NULL,NULL,NULL,'用户登录',1,'POST','认证','admin','127.0.0.1','登录成功','2026-06-30 21:46:46');
INSERT INTO `sys_oper_log` (`id`, `tenant_id`, `user_id`, `username`, `event_name`, `result`, `method`, `resource_type`, `resource_name`, `ip`, `detail`, `create_time`) VALUES (7,NULL,NULL,NULL,'用户登录',1,'POST','认证','admin','127.0.0.1','登录成功','2026-06-30 21:47:30');
INSERT INTO `sys_oper_log` (`id`, `tenant_id`, `user_id`, `username`, `event_name`, `result`, `method`, `resource_type`, `resource_name`, `ip`, `detail`, `create_time`) VALUES (8,NULL,NULL,NULL,'用户登录',1,'POST','认证','admin','127.0.0.1','登录成功','2026-06-30 21:48:03');
INSERT INTO `sys_oper_log` (`id`, `tenant_id`, `user_id`, `username`, `event_name`, `result`, `method`, `resource_type`, `resource_name`, `ip`, `detail`, `create_time`) VALUES (9,NULL,NULL,NULL,'用户登录',1,'POST','认证','admin','127.0.0.1','登录成功','2026-06-30 21:48:09');
INSERT INTO `sys_oper_log` (`id`, `tenant_id`, `user_id`, `username`, `event_name`, `result`, `method`, `resource_type`, `resource_name`, `ip`, `detail`, `create_time`) VALUES (10,NULL,NULL,NULL,'用户登录',1,'POST','认证','admin','127.0.0.1','登录成功','2026-06-30 21:48:25');
INSERT INTO `sys_oper_log` (`id`, `tenant_id`, `user_id`, `username`, `event_name`, `result`, `method`, `resource_type`, `resource_name`, `ip`, `detail`, `create_time`) VALUES (11,NULL,NULL,NULL,'用户登录',1,'POST','认证','admin','127.0.0.1','登录成功','2026-06-30 21:48:35');
INSERT INTO `sys_oper_log` (`id`, `tenant_id`, `user_id`, `username`, `event_name`, `result`, `method`, `resource_type`, `resource_name`, `ip`, `detail`, `create_time`) VALUES (12,NULL,NULL,NULL,'用户登录',1,'POST','认证','admin','0:0:0:0:0:0:0:1','登录成功','2026-06-30 22:10:47');
INSERT INTO `sys_oper_log` (`id`, `tenant_id`, `user_id`, `username`, `event_name`, `result`, `method`, `resource_type`, `resource_name`, `ip`, `detail`, `create_time`) VALUES (13,NULL,NULL,NULL,'用户登录',1,'POST','认证','admin','0:0:0:0:0:0:0:1','登录成功','2026-06-30 22:11:03');
INSERT INTO `sys_oper_log` (`id`, `tenant_id`, `user_id`, `username`, `event_name`, `result`, `method`, `resource_type`, `resource_name`, `ip`, `detail`, `create_time`) VALUES (14,NULL,NULL,NULL,'用户登录',1,'POST','认证','admin','0:0:0:0:0:0:0:1','登录成功','2026-06-30 22:13:43');
INSERT INTO `sys_oper_log` (`id`, `tenant_id`, `user_id`, `username`, `event_name`, `result`, `method`, `resource_type`, `resource_name`, `ip`, `detail`, `create_time`) VALUES (15,NULL,NULL,NULL,'用户登录',1,'POST','认证','admin','0:0:0:0:0:0:0:1','登录成功','2026-06-30 22:16:03');
INSERT INTO `sys_oper_log` (`id`, `tenant_id`, `user_id`, `username`, `event_name`, `result`, `method`, `resource_type`, `resource_name`, `ip`, `detail`, `create_time`) VALUES (16,NULL,NULL,NULL,'用户登录',1,'POST','认证','admin','0:0:0:0:0:0:0:1','登录成功','2026-06-30 22:16:06');
INSERT INTO `sys_oper_log` (`id`, `tenant_id`, `user_id`, `username`, `event_name`, `result`, `method`, `resource_type`, `resource_name`, `ip`, `detail`, `create_time`) VALUES (17,NULL,NULL,NULL,'用户登录',1,'POST','认证','admin','127.0.0.1','登录成功','2026-06-30 22:18:00');
INSERT INTO `sys_oper_log` (`id`, `tenant_id`, `user_id`, `username`, `event_name`, `result`, `method`, `resource_type`, `resource_name`, `ip`, `detail`, `create_time`) VALUES (18,NULL,NULL,NULL,'用户登录',1,'POST','认证','admin','0:0:0:0:0:0:0:1','登录成功','2026-06-30 22:20:06');
INSERT INTO `sys_oper_log` (`id`, `tenant_id`, `user_id`, `username`, `event_name`, `result`, `method`, `resource_type`, `resource_name`, `ip`, `detail`, `create_time`) VALUES (19,NULL,NULL,NULL,'用户登录',1,'POST','认证','admin','0:0:0:0:0:0:0:1','登录成功','2026-06-30 22:20:27');
INSERT INTO `sys_oper_log` (`id`, `tenant_id`, `user_id`, `username`, `event_name`, `result`, `method`, `resource_type`, `resource_name`, `ip`, `detail`, `create_time`) VALUES (20,1,1,'admin','新建数据流',1,'POST','数据流管理','测试流','0:0:0:0:0:0:0:1',NULL,'2026-06-30 22:20:29');
INSERT INTO `sys_oper_log` (`id`, `tenant_id`, `user_id`, `username`, `event_name`, `result`, `method`, `resource_type`, `resource_name`, `ip`, `detail`, `create_time`) VALUES (21,1,1,'admin','新建数据流',1,'POST','数据流管理','测试流2','0:0:0:0:0:0:0:1',NULL,'2026-06-30 22:20:29');
INSERT INTO `sys_oper_log` (`id`, `tenant_id`, `user_id`, `username`, `event_name`, `result`, `method`, `resource_type`, `resource_name`, `ip`, `detail`, `create_time`) VALUES (22,NULL,NULL,NULL,'用户登录',1,'POST','认证','admin','0:0:0:0:0:0:0:1','登录成功','2026-06-30 22:20:40');
INSERT INTO `sys_oper_log` (`id`, `tenant_id`, `user_id`, `username`, `event_name`, `result`, `method`, `resource_type`, `resource_name`, `ip`, `detail`, `create_time`) VALUES (23,NULL,NULL,NULL,'用户登录',1,'POST','认证','admin','0:0:0:0:0:0:0:1','登录成功','2026-06-30 22:21:31');
INSERT INTO `sys_oper_log` (`id`, `tenant_id`, `user_id`, `username`, `event_name`, `result`, `method`, `resource_type`, `resource_name`, `ip`, `detail`, `create_time`) VALUES (24,NULL,NULL,NULL,'用户登录',1,'POST','认证','admin','0:0:0:0:0:0:0:1','登录成功','2026-06-30 22:21:38');
INSERT INTO `sys_oper_log` (`id`, `tenant_id`, `user_id`, `username`, `event_name`, `result`, `method`, `resource_type`, `resource_name`, `ip`, `detail`, `create_time`) VALUES (25,1,1,'admin','新增数据源',1,'POST','数据源管理','测试MySQL','127.0.0.1',NULL,'2026-06-30 22:21:38');
INSERT INTO `sys_oper_log` (`id`, `tenant_id`, `user_id`, `username`, `event_name`, `result`, `method`, `resource_type`, `resource_name`, `ip`, `detail`, `create_time`) VALUES (26,NULL,NULL,NULL,'用户登录',1,'POST','认证','admin','0:0:0:0:0:0:0:1','登录成功','2026-06-30 22:23:34');
INSERT INTO `sys_oper_log` (`id`, `tenant_id`, `user_id`, `username`, `event_name`, `result`, `method`, `resource_type`, `resource_name`, `ip`, `detail`, `create_time`) VALUES (27,NULL,NULL,NULL,'用户登录',1,'POST','认证','admin','0:0:0:0:0:0:0:1','登录成功','2026-06-30 22:23:35');
INSERT INTO `sys_oper_log` (`id`, `tenant_id`, `user_id`, `username`, `event_name`, `result`, `method`, `resource_type`, `resource_name`, `ip`, `detail`, `create_time`) VALUES (28,NULL,NULL,NULL,'用户登录',1,'POST','认证','admin','0:0:0:0:0:0:0:1','登录成功','2026-06-30 22:23:41');
INSERT INTO `sys_oper_log` (`id`, `tenant_id`, `user_id`, `username`, `event_name`, `result`, `method`, `resource_type`, `resource_name`, `ip`, `detail`, `create_time`) VALUES (29,1,1,'admin','新增数据源',1,'POST','数据源管理','测试MySQL','127.0.0.1',NULL,'2026-06-30 22:23:41');
INSERT INTO `sys_oper_log` (`id`, `tenant_id`, `user_id`, `username`, `event_name`, `result`, `method`, `resource_type`, `resource_name`, `ip`, `detail`, `create_time`) VALUES (30,NULL,NULL,NULL,'用户登录',1,'POST','认证','admin','0:0:0:0:0:0:0:1','登录成功','2026-06-30 22:24:52');
INSERT INTO `sys_oper_log` (`id`, `tenant_id`, `user_id`, `username`, `event_name`, `result`, `method`, `resource_type`, `resource_name`, `ip`, `detail`, `create_time`) VALUES (31,NULL,NULL,NULL,'用户登录',1,'POST','认证','admin','0:0:0:0:0:0:0:1','登录成功','2026-06-30 22:24:56');
INSERT INTO `sys_oper_log` (`id`, `tenant_id`, `user_id`, `username`, `event_name`, `result`, `method`, `resource_type`, `resource_name`, `ip`, `detail`, `create_time`) VALUES (32,NULL,NULL,NULL,'用户登录',1,'POST','认证','admin','0:0:0:0:0:0:0:1','登录成功','2026-06-30 22:25:02');
INSERT INTO `sys_oper_log` (`id`, `tenant_id`, `user_id`, `username`, `event_name`, `result`, `method`, `resource_type`, `resource_name`, `ip`, `detail`, `create_time`) VALUES (33,NULL,NULL,NULL,'用户登录',1,'POST','认证','admin','0:0:0:0:0:0:0:1','登录成功','2026-06-30 22:25:08');
INSERT INTO `sys_oper_log` (`id`, `tenant_id`, `user_id`, `username`, `event_name`, `result`, `method`, `resource_type`, `resource_name`, `ip`, `detail`, `create_time`) VALUES (34,NULL,NULL,NULL,'用户登录',1,'POST','认证','admin','0:0:0:0:0:0:0:1','登录成功','2026-06-30 22:25:23');
INSERT INTO `sys_oper_log` (`id`, `tenant_id`, `user_id`, `username`, `event_name`, `result`, `method`, `resource_type`, `resource_name`, `ip`, `detail`, `create_time`) VALUES (35,NULL,NULL,NULL,'用户登录',1,'POST','认证','admin','0:0:0:0:0:0:0:1','登录成功','2026-06-30 22:25:36');
INSERT INTO `sys_oper_log` (`id`, `tenant_id`, `user_id`, `username`, `event_name`, `result`, `method`, `resource_type`, `resource_name`, `ip`, `detail`, `create_time`) VALUES (36,NULL,NULL,NULL,'用户登录',1,'POST','认证','admin','0:0:0:0:0:0:0:1','登录成功','2026-06-30 22:26:17');
INSERT INTO `sys_oper_log` (`id`, `tenant_id`, `user_id`, `username`, `event_name`, `result`, `method`, `resource_type`, `resource_name`, `ip`, `detail`, `create_time`) VALUES (37,NULL,NULL,NULL,'用户登录',1,'POST','认证','admin','0:0:0:0:0:0:0:1','登录成功','2026-06-30 22:26:39');
INSERT INTO `sys_oper_log` (`id`, `tenant_id`, `user_id`, `username`, `event_name`, `result`, `method`, `resource_type`, `resource_name`, `ip`, `detail`, `create_time`) VALUES (38,NULL,NULL,NULL,'用户登录',1,'POST','认证','admin','0:0:0:0:0:0:0:1','登录成功','2026-06-30 22:27:21');
INSERT INTO `sys_oper_log` (`id`, `tenant_id`, `user_id`, `username`, `event_name`, `result`, `method`, `resource_type`, `resource_name`, `ip`, `detail`, `create_time`) VALUES (39,NULL,NULL,NULL,'用户登录',1,'POST','认证','admin','0:0:0:0:0:0:0:1','登录成功','2026-06-30 22:31:11');
INSERT INTO `sys_oper_log` (`id`, `tenant_id`, `user_id`, `username`, `event_name`, `result`, `method`, `resource_type`, `resource_name`, `ip`, `detail`, `create_time`) VALUES (40,NULL,NULL,NULL,'用户登录',1,'POST','认证','admin','0:0:0:0:0:0:0:1','登录成功','2026-06-30 22:31:24');
INSERT INTO `sys_oper_log` (`id`, `tenant_id`, `user_id`, `username`, `event_name`, `result`, `method`, `resource_type`, `resource_name`, `ip`, `detail`, `create_time`) VALUES (41,NULL,NULL,NULL,'用户登录',1,'POST','认证','admin','0:0:0:0:0:0:0:1','登录成功','2026-06-30 22:31:31');
/*!40000 ALTER TABLE `sys_oper_log` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Dumping data for table `sys_role`
--

LOCK TABLES `sys_role` WRITE;
/*!40000 ALTER TABLE `sys_role` DISABLE KEYS */;
INSERT INTO `sys_role` (`id`, `tenant_id`, `role_name`, `role_key`, `description`, `data_scope`, `create_time`, `update_time`, `deleted`) VALUES (1,1,'超级管理员','admin','系统超级管理员',1,'2026-06-30 07:29:26','2026-06-30 07:29:26',0);
INSERT INTO `sys_role` (`id`, `tenant_id`, `role_name`, `role_key`, `description`, `data_scope`, `create_time`, `update_time`, `deleted`) VALUES (2,1,'租户管理员','tenant_admin','租户管理员',1,'2026-06-30 07:29:26','2026-06-30 07:29:26',0);
INSERT INTO `sys_role` (`id`, `tenant_id`, `role_name`, `role_key`, `description`, `data_scope`, `create_time`, `update_time`, `deleted`) VALUES (3,1,'普通用户','user','普通开发用户',4,'2026-06-30 07:29:26','2026-06-30 07:29:26',0);
/*!40000 ALTER TABLE `sys_role` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Dumping data for table `sys_role_menu`
--

LOCK TABLES `sys_role_menu` WRITE;
/*!40000 ALTER TABLE `sys_role_menu` DISABLE KEYS */;
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`) VALUES (1,1);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`) VALUES (1,10);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`) VALUES (1,11);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`) VALUES (1,12);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`) VALUES (1,13);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`) VALUES (1,14);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`) VALUES (1,15);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`) VALUES (1,16);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`) VALUES (1,20);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`) VALUES (1,21);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`) VALUES (1,22);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`) VALUES (1,23);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`) VALUES (1,24);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`) VALUES (1,25);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`) VALUES (1,26);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`) VALUES (1,30);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`) VALUES (1,31);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`) VALUES (1,32);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`) VALUES (1,33);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`) VALUES (1,34);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`) VALUES (1,40);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`) VALUES (1,41);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`) VALUES (1,42);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`) VALUES (1,43);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`) VALUES (1,44);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`) VALUES (1,50);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`) VALUES (1,51);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`) VALUES (1,52);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`) VALUES (1,53);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`) VALUES (1,54);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`) VALUES (1,60);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`) VALUES (1,61);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`) VALUES (1,62);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`) VALUES (1,63);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`) VALUES (1,70);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`) VALUES (1,71);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`) VALUES (1,72);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`) VALUES (1,73);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`) VALUES (1,74);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`) VALUES (1,75);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`) VALUES (1,76);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`) VALUES (1,77);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`) VALUES (1,78);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`) VALUES (1,79);
/*!40000 ALTER TABLE `sys_role_menu` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Dumping data for table `sys_tenant`
--

LOCK TABLES `sys_tenant` WRITE;
/*!40000 ALTER TABLE `sys_tenant` DISABLE KEYS */;
INSERT INTO `sys_tenant` (`id`, `tenant_code`, `tenant_name`, `status`, `start_time`, `end_time`, `create_by`, `create_time`, `update_time`, `deleted`) VALUES (1,'T001','张掖市大数据平台',1,'2026-06-30 07:29:26','2031-06-30 07:29:26','system','2026-06-30 07:29:26','2026-06-30 07:29:26',0);
/*!40000 ALTER TABLE `sys_tenant` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Dumping data for table `sys_user`
--

LOCK TABLES `sys_user` WRITE;
/*!40000 ALTER TABLE `sys_user` DISABLE KEYS */;
INSERT INTO `sys_user` (`id`, `tenant_id`, `dept_id`, `username`, `password`, `nickname`, `phone`, `avatar`, `status`, `create_time`, `update_time`, `deleted`) VALUES (1,1,2,'admin','$2a$10$ffMssiZfQrgQGFOgsE4WfejobSSZEOLNQkUxUfJ9hgnj6wFYRw6Fu','系统管理员','13800000000',NULL,1,'2026-06-30 07:29:26','2026-06-30 07:29:26',0);
/*!40000 ALTER TABLE `sys_user` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Dumping data for table `sys_user_role`
--

LOCK TABLES `sys_user_role` WRITE;
/*!40000 ALTER TABLE `sys_user_role` DISABLE KEYS */;
INSERT INTO `sys_user_role` (`user_id`, `role_id`) VALUES (1,1);
/*!40000 ALTER TABLE `sys_user_role` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Dumping data for table `user_activity_log`
--

LOCK TABLES `user_activity_log` WRITE;
/*!40000 ALTER TABLE `user_activity_log` DISABLE KEYS */;
/*!40000 ALTER TABLE `user_activity_log` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-06-30 22:33:25
