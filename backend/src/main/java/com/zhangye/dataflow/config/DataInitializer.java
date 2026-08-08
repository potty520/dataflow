package com.zhangye.dataflow.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhangye.dataflow.entity.SysMenu;
import com.zhangye.dataflow.entity.SysUser;
import com.zhangye.dataflow.mapper.SysMenuMapper;
import com.zhangye.dataflow.mapper.SysUserMapper;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Objects;

@Component
public class DataInitializer implements CommandLineRunner {

    private final SysUserMapper userMapper;
    private final SysMenuMapper menuMapper;
    private final PasswordEncoder passwordEncoder;
    private final JdbcTemplate jdbcTemplate;

    public DataInitializer(SysUserMapper userMapper, SysMenuMapper menuMapper, PasswordEncoder passwordEncoder,
                           JdbcTemplate jdbcTemplate) {
        this.userMapper = userMapper;
        this.menuMapper = menuMapper;
        this.passwordEncoder = passwordEncoder;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(String... args) {
        ensureMigrationMappingTable();
        ensureInfrastructureExtensionTables();
        ensureInfrastructureSeedData();
        ensureInfrastructureMenus();
        ensureMigrationDemoData();

        SysUser admin = userMapper.selectOne(new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, "admin"));
        if (admin != null) {
            admin.setPassword(passwordEncoder.encode("admin123"));
            userMapper.updateById(admin);
        }
    }

    private void ensureInfrastructureExtensionTables() {
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS infra_repository (" +
                "id BIGINT AUTO_INCREMENT PRIMARY KEY," +
                "tenant_id BIGINT NOT NULL," +
                "component_type VARCHAR(64)," +
                "package_name VARCHAR(128) NOT NULL," +
                "version VARCHAR(64)," +
                "file_path VARCHAR(512)," +
                "package_size_mb DECIMAL(12,2) DEFAULT 0," +
                "checksum VARCHAR(128)," +
                "compatible_os VARCHAR(128)," +
                "status VARCHAR(32) DEFAULT 'READY'," +
                "remark VARCHAR(512)," +
                "create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                "update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                "deleted TINYINT DEFAULT 0" +
                ")");
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS infra_alert (" +
                "id BIGINT AUTO_INCREMENT PRIMARY KEY," +
                "tenant_id BIGINT NOT NULL," +
                "alert_name VARCHAR(128) NOT NULL," +
                "alert_level VARCHAR(32)," +
                "alert_type VARCHAR(64)," +
                "target_type VARCHAR(64)," +
                "target_name VARCHAR(128)," +
                "rule_expr VARCHAR(512)," +
                "message VARCHAR(1024)," +
                "status VARCHAR(32) DEFAULT 'ACTIVE'," +
                "handle_suggestion VARCHAR(1024)," +
                "last_trigger_time TIMESTAMP," +
                "create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                "update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                "deleted TINYINT DEFAULT 0" +
                ")");
    }

    private void ensureInfrastructureSeedData() {
        ensureConfig("infra.config.core-site.xml", "基础支撑配置文件", "DEV",
                "<configuration>\n  <property>\n    <name>fs.defaultFS</name>\n    <value>hdfs://hadoop132:9000</value>\n  </property>\n</configuration>");
        ensureConfig("infra.config.hdfs-site.xml", "基础支撑配置文件", "DEV",
                "<configuration>\n  <property>\n    <name>dfs.replication</name>\n    <value>3</value>\n  </property>\n</configuration>");
        ensureConfig("infra.config.yarn-site.xml", "基础支撑配置文件", "DEV",
                "<configuration>\n  <property>\n    <name>yarn.scheduler.maximum-allocation-mb</name>\n    <value>8192</value>\n  </property>\n</configuration>");
        ensureConfig("infra.config.hive-site.xml", "基础支撑配置文件", "DEV",
                "<configuration>\n  <property>\n    <name>hive.metastore.uris</name>\n    <value>thrift://192.168.184.132:9083</value>\n  </property>\n</configuration>");

        ensureIfEmpty("SELECT COUNT(1) FROM infra_repository",
                "INSERT INTO infra_repository (tenant_id, component_type, package_name, version, file_path, package_size_mb, checksum, compatible_os, status, remark) VALUES (1, 'HDFS', 'hadoop-3.3.4.tar.gz', '3.3.4', '/opt/packages/hadoop-3.3.4.tar.gz', 528.5, 'c8d5d7f4b2', 'CentOS 7.6', 'READY', 'NameNode 和 DataNode 部署包')");
        ensureIfEmpty("SELECT COUNT(1) FROM infra_repository WHERE package_name = 'flink-1.17.1.tar.gz'",
                "INSERT INTO infra_repository (tenant_id, component_type, package_name, version, file_path, package_size_mb, checksum, compatible_os, status, remark) VALUES (1, 'Flink', 'flink-1.17.1.tar.gz', '1.17.1', '/opt/packages/flink-1.17.1.tar.gz', 312.8, 'f3ac92b7a1', 'CentOS 7.6', 'DISTRIBUTED', 'Flink SQL 运行包')");
        ensureIfEmpty("SELECT COUNT(1) FROM infra_alert",
                "INSERT INTO infra_alert (tenant_id, alert_name, alert_level, alert_type, target_type, target_name, rule_expr, message, status, handle_suggestion, last_trigger_time) VALUES (1, 'HDFS 容量预警', 'WARN', 'CAPACITY', 'CLUSTER', '张掖大数据集群', 'disk_usage > 80', 'HDFS 存储空间使用率超过阈值', 'ACTIVE', '扩容数据盘或清理历史文件', CURRENT_TIMESTAMP)");
        ensureIfEmpty("SELECT COUNT(1) FROM infra_alert WHERE alert_name = 'YARN 资源池告警'",
                "INSERT INTO infra_alert (tenant_id, alert_name, alert_level, alert_type, target_type, target_name, rule_expr, message, status, handle_suggestion, last_trigger_time) VALUES (1, 'YARN 资源池告警', 'INFO', 'RESOURCE_POOL', 'QUEUE', 'default', 'pending_app > 20', 'YARN 队列积压任务较多', 'ACKED', '检查调度优先级和资源池配额', CURRENT_TIMESTAMP)");
        ensureIfEmpty("SELECT COUNT(1) FROM infra_resource_group",
                "INSERT INTO infra_resource_group (tenant_id, group_name, resource_type, resource_key, description, max_cores, max_memory_gb, status) VALUES (1, 'default', 'YARN_QUEUE', 'root.default', '默认计算资源池', 24, 96, 'ENABLED')");
        ensureIfEmpty("SELECT COUNT(1) FROM storage_quota",
                "INSERT INTO storage_quota (tenant_id, datasource_id, database_name, max_size_gb, current_usage_gb, alert_threshold_pct, status) VALUES (1, NULL, 'dw_db', 1000, 580, 80, 'NORMAL')");
    }

    private void ensureInfrastructureMenus() {
        ensureMenu(70L, 0L, "infrastructure", "基础支撑", "/infrastructure", null, 1,
                "infrastructure", "Monitor", 7, 1);
        ensureMenu(81L, 70L, "infra_overview", "基础支撑总览", "/infrastructure/overview", "infrastructure/overview/index", 2,
                "infrastructure", "DataBoard", 1, 1);
        ensureMenu(82L, 70L, "infra_host", "主机管理", "/infrastructure/host", "infrastructure/host/index", 2,
                "infrastructure", "Platform", 2, 1);
        ensureMenu(71L, 70L, "component", "组件管理", "/infrastructure/component", "infrastructure/component/index", 2,
                "infrastructure", "Setting", 3, 1);
        ensureMenu(72L, 70L, "cluster", "集群管理", "/infrastructure/cluster", "infrastructure/cluster/index", 2,
                "infrastructure", "Collection", 4, 1);
        ensureMenu(83L, 70L, "infra_repository", "组件仓库", "/infrastructure/repository", "infrastructure/repository/index", 2,
                "infrastructure", "FolderOpened", 5, 1);
        ensureMenu(84L, 70L, "infra_config", "服务配置", "/infrastructure/config", "infrastructure/config/index", 2,
                "infrastructure", "Document", 6, 1);
        ensureMenu(85L, 70L, "infra_alert", "告警中心", "/infrastructure/alert", "infrastructure/alert/index", 2,
                "infrastructure", "WarningFilled", 7, 1);
        ensureMenu(86L, 70L, "infra_monitor", "资源监控", "/infrastructure/monitor", "infrastructure/monitor/index", 2,
                "infrastructure", "Monitor", 8, 1);
        ensureMenu(87L, 70L, "infra_resource_group", "资源组", "/infrastructure/resource-group", "infrastructure/resource-group/index", 2,
                "infrastructure", "Grid", 9, 1);
        ensureMenu(88L, 70L, "infra_storage_quota", "存储配额", "/infrastructure/storage-quota", "infrastructure/storage-quota/index", 2,
                "infrastructure", "Coin", 10, 1);
        ensureMenu(74L, 70L, "gov_proxy", "代理节点", "/infrastructure/proxynode", "governance/proxynode/index", 2,
                "infrastructure", "Connection", 11, 1);
        ensureMenu(89L, 70L, "infra_log", "运维日志", "/infrastructure/log", "portal/log/index", 2,
                "infrastructure", "Tickets", 12, 1);
        ensureMenu(35L, 30L, "cleaning", "数据清洗", "/governance/cleaning", "governance/cleaning/index", 2,
                "governance", "MagicStick", 4, 1);
        ensureMenu(34L, 30L, "indicator", "指标管理", "/governance/indicator", "governance/indicator/index", 2,
                "governance", null, 5, 1);
        ensureMenu(73L, 10L, "portal_profile", "个人中心", "/portal/profile", "portal/profile/index", 2,
                "portal", "UserFilled", 99, 0);
        ensureMenu(79L, 10L, "portal_security", "安全管理", "/portal/security", "portal/security/index", 2,
                "portal", "Lock", 7, 1);
        ensureMenu(75L, 30L, "gov_monitor", "治理监控", "/governance/monitor", "governance/monitor/index", 2,
                "governance", "Monitor", 7, 1);
        ensureMenu(76L, 50L, "svc_unit", "服务单元", "/service/unit", "service/unit/index", 2,
                "service", "Grid", 5, 1);
        ensureMenu(77L, 50L, "svc_ratelimit", "限流配置", "/service/ratelimit", "service/ratelimit/index", 2,
                "service", "Timer", 6, 1);
        ensureMenu(78L, 50L, "svc_monitor", "服务监控", "/service/monitor", "service/monitor/index", 2,
                "service", "DataLine", 7, 1);
        ensureMenu(80L, 20L, "migration_map", "数据迁移映射", "/aggregation/migration-map",
                "aggregation/migration-map/index", 2, "aggregation", "Connection", 7, 1);
    }

    private void ensureMigrationDemoData() {
        jdbcTemplate.update("UPDATE asset_table SET datasource_id = ? WHERE id = ? AND (datasource_id IS NULL OR datasource_id = 0)", 1L, 1L);
        jdbcTemplate.update("UPDATE asset_table SET datasource_id = ? WHERE id = ? AND (datasource_id IS NULL OR datasource_id = 0)", 2L, 2L);
        ensureAssetField(1L, "user_id", "用户ID", "BIGINT", 0);
        ensureAssetField(1L, "phone", "手机号", "VARCHAR", 1);
        ensureAssetField(1L, "id_card", "身份证号", "VARCHAR", 1);
        ensureAssetField(2L, "user_id", "用户ID", "BIGINT", 0);
        ensureAssetField(2L, "phone", "手机号", "VARCHAR", 1);
        ensureAssetField(2L, "id_card", "身份证号", "VARCHAR", 1);
        ensureAssetField(2L, "clean_time", "清洗时间", "TIMESTAMP", 0);
    }

    private void ensureAssetField(Long tableId, String fieldEn, String fieldCn, String fieldType, Integer sensitive) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM asset_field WHERE table_id = ? AND field_en = ?",
                Integer.class, tableId, fieldEn);
        if (count == null || count == 0) {
            jdbcTemplate.update(
                    "INSERT INTO asset_field (table_id, field_en, field_cn, field_type, is_sensitive, create_time) VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP)",
                    tableId, fieldEn, fieldCn, fieldType, sensitive);
        }
    }

    private void ensureConfig(String key, String description, String env, String value) {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(1) FROM sys_config WHERE config_key = ?", Integer.class, key);
        if (count == null || count == 0) {
            jdbcTemplate.update(
                    "INSERT INTO sys_config (config_key, config_value, description, env, update_time) VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP)",
                    key, value, description, env);
        }
    }

    private void ensureIfEmpty(String countSql, String insertSql) {
        Integer count = jdbcTemplate.queryForObject(countSql, Integer.class);
        if (count == null || count == 0) {
            jdbcTemplate.execute(insertSql);
        }
    }

    private void ensureMigrationMappingTable() {
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS agg_migration_mapping (" +
                "id BIGINT AUTO_INCREMENT PRIMARY KEY," +
                "tenant_id BIGINT NOT NULL," +
                "name VARCHAR(128) NOT NULL," +
                "source_table_id BIGINT NOT NULL," +
                "target_table_id BIGINT NOT NULL," +
                "write_mode VARCHAR(32) DEFAULT 'insert'," +
                "fault_tolerance INT DEFAULT 0," +
                "pre_sql LONGTEXT," +
                "field_mapping LONGTEXT," +
                "remark VARCHAR(512)," +
                "status VARCHAR(32) DEFAULT 'DRAFT'," +
                "create_by VARCHAR(64)," +
                "create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                "update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                "deleted TINYINT DEFAULT 0" +
                ")");
    }

    private void ensureMenu(Long id, Long parentId, String menuCode, String menuName, String path,
                            String component, Integer menuType, String serviceName, String icon,
                            Integer sortOrder, Integer visible) {
        SysMenu menu = menuMapper.selectOne(new LambdaQueryWrapper<SysMenu>().eq(SysMenu::getMenuCode, menuCode));
        LocalDateTime now = LocalDateTime.now();
        if (menu == null) {
            menu = new SysMenu();
            menu.setId(id);
            menu.setParentId(parentId);
            menu.setMenuCode(menuCode);
            menu.setMenuName(menuName);
            menu.setPath(path);
            menu.setComponent(component);
            menu.setMenuType(menuType);
            menu.setServiceName(serviceName);
            menu.setIcon(icon);
            menu.setSortOrder(sortOrder);
            menu.setVisible(visible);
            menu.setCreateTime(now);
            menu.setUpdateTime(now);
            menuMapper.insert(menu);
            ensureAdminRoleMenu(menu.getId());
            return;
        }

        boolean changed = false;
        if (!Objects.equals(menu.getParentId(), parentId)) {
            menu.setParentId(parentId);
            changed = true;
        }
        if (!Objects.equals(menu.getMenuName(), menuName)) {
            menu.setMenuName(menuName);
            changed = true;
        }
        if (!Objects.equals(menu.getPath(), path)) {
            menu.setPath(path);
            changed = true;
        }
        if (!Objects.equals(menu.getComponent(), component)) {
            menu.setComponent(component);
            changed = true;
        }
        if (!Objects.equals(menu.getMenuType(), menuType)) {
            menu.setMenuType(menuType);
            changed = true;
        }
        if (!Objects.equals(menu.getServiceName(), serviceName)) {
            menu.setServiceName(serviceName);
            changed = true;
        }
        if (!Objects.equals(menu.getIcon(), icon)) {
            menu.setIcon(icon);
            changed = true;
        }
        if (!Objects.equals(menu.getSortOrder(), sortOrder)) {
            menu.setSortOrder(sortOrder);
            changed = true;
        }
        if (!Objects.equals(menu.getVisible(), visible)) {
            menu.setVisible(visible);
            changed = true;
        }
        if (changed) {
            menu.setUpdateTime(now);
            menuMapper.updateById(menu);
        }
        ensureAdminRoleMenu(menu.getId());
    }

    private void ensureAdminRoleMenu(Long menuId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM sys_role_menu WHERE role_id = ? AND menu_id = ?",
                Integer.class, 1L, menuId);
        if (count == null || count == 0) {
            jdbcTemplate.update("INSERT INTO sys_role_menu (role_id, menu_id) VALUES (?, ?)", 1L, menuId);
        }
    }
}
