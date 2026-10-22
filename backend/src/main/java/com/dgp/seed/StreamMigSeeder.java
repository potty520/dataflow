package com.dgp.seed;

import com.dgp.common.Db;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 演示数据：流式（微批）质检源/规则 + 数据迁移项目/字段映射/对账项。
 * 幂等：按表是否为空判断，已有数据不重复写入（已有库升级时也会补种）。
 */
@Component
public class StreamMigSeeder {

    private static final Logger log = LoggerFactory.getLogger(StreamMigSeeder.class);

    private final Db db;

    public StreamMigSeeder(Db db) {
        this.db = db;
    }

    public void seed() {
        try {
            seedStreamQuality();
            seedMigration();
        } catch (Exception e) {
            log.warn("流式质检/数据迁移演示数据装载失败：{}", e.getMessage());
        }
    }

    private void seedStreamQuality() {
        if (db.count("SELECT COUNT(*) FROM qa_stream_source") > 0) return;
        long device = db.insert("INSERT INTO qa_stream_source(tenant_id,code,name,table_name,watermark_col,"
                        + "watermark_type,window_seconds,batch_limit,status,owner,create_time) VALUES(1,?,?,?,?,?,?,?,?,?,?)",
                "SS-DEVICE-METRIC", "设备指标实时流", "ods_device_metric", "collect_time", "TIMESTAMP",
                60, 5000, "ENABLED", "zhangsan", LocalDateTime.now().minusHours(2));
        long order = db.insert("INSERT INTO qa_stream_source(tenant_id,code,name,table_name,watermark_col,"
                        + "watermark_type,window_seconds,batch_limit,status,owner,create_time) VALUES(1,?,?,?,?,?,?,?,?,?,?)",
                "SS-ORDER-STREAM", "订单交易实时流", "ods_order", "order_time", "TIMESTAMP",
                300, 5000, "ENABLED", "zhangsan", LocalDateTime.now().minusHours(2));

        // 设备流规则：不合格条件（布尔谓词）
        rule(device, "SR-METRIC-RANGE", "指标值越界（-100~200 之外）", "VALIDITY",
                "metric_value < -100 OR metric_value > 200", 0.05, "MAJOR");
        rule(device, "SR-DEVICE-NULL", "设备编号为空", "COMPLETENESS",
                "device_id IS NULL OR device_id = ''", 0.01, "CRITICAL");
        rule(device, "SR-COLLECT-FUTURE", "采集时间超前于当前时间", "TIMELINESS",
                "collect_time > CURRENT_TIMESTAMP", 0.0, "MINOR");
        // 订单流规则
        rule(order, "SR-AMOUNT-POS", "订单金额非正", "ACCURACY",
                "amount <= 0", 0.02, "MAJOR");
        rule(order, "SR-CUST-NULL", "订单缺少客户编号", "COMPLETENESS",
                "customer_id IS NULL OR customer_id = ''", 0.01, "CRITICAL");
        log.info("已装载流式质检演示数据：2 个流源 / 5 条规则");
    }

    private void rule(long sourceId, String code, String name, String dimension,
                      String expression, double tolerance, String severity) {
        db.insert("INSERT INTO qa_stream_rule(source_id,code,name,dimension,expression,tolerance,severity,status,create_time) "
                        + "VALUES(?,?,?,?,?,?,?,?,?)",
                sourceId, code, name, dimension, expression, java.math.BigDecimal.valueOf(tolerance),
                severity, "ENABLED", LocalDateTime.now().minusHours(2));
    }

    private void seedMigration() {
        if (db.count("SELECT COUNT(*) FROM mig_project") > 0) return;
        // 项目一：ODS 客户 → DWD 客户维度表
        long cust = db.insert("INSERT INTO mig_project(tenant_id,code,name,source_table,target_table,mode,replace_target,"
                        + "status,description,owner,create_time,update_time) VALUES(1,?,?,?,?,?,?,?,?,?,?,?)",
                "MIG-CUST-DIM", "客户数据迁移（ODS → DWD）", "ods_customer", "dwd_customer_dim",
                "FULL", 1, "READY", "示范：字段映射+表达式转换+迁移后对账", "zhangsan",
                LocalDateTime.now().minusHours(3), LocalDateTime.now().minusHours(3));
        mapping(cust, "customer_id", "customer_key", "EXPR", "CONCAT('CK', customer_id)", null, 0);
        mapping(cust, "customer_id", "customer_id", "DIRECT", null, null, 1);
        mapping(cust, "cust_name", "cust_name", "DIRECT", null, null, 2);
        mapping(cust, "gender", "gender", "DIRECT", null, null, 3);
        mapping(cust, "birthday", "age", "EXPR", "EXTRACT(YEAR FROM CURRENT_DATE) - EXTRACT(YEAR FROM birthday)", null, 4);
        mapping(cust, "phone", "phone", "DIRECT", null, null, 5);
        mapping(cust, "email", "email", "DIRECT", null, null, 6);
        mapping(cust, "city", "city", "DIRECT", null, null, 7);
        mapping(cust, "reg_time", "reg_date", "EXPR", "CAST(reg_time AS DATE)", null, 8);
        mapping(cust, "member_level", "member_level", "DIRECT", null, null, 9);
        mapping(cust, "phone", "is_valid", "EXPR",
                "CASE WHEN phone IS NULL OR city IS NULL THEN 0 ELSE 1 END", null, 10);
        check(cust, "ROW_COUNT", null, 0);
        check(cust, "PK_DUP", "customer_key", 0);
        check(cust, "NULL_RATE", "cust_name", 0);
        check(cust, "SUM", "age", 0.01);

        // 项目二：ODS 订单 → DWD 订单事实表
        long order = db.insert("INSERT INTO mig_project(tenant_id,code,name,source_table,target_table,mode,replace_target,"
                        + "status,description,owner,create_time,update_time) VALUES(1,?,?,?,?,?,?,?,?,?,?,?)",
                "MIG-ORDER-FACT", "订单数据迁移（ODS → DWD）", "ods_order", "dwd_order_fact",
                "FULL", 1, "READY", "示范：多字段映射与状态派生", "zhangsan",
                LocalDateTime.now().minusHours(3), LocalDateTime.now().minusHours(3));
        mapping(order, "order_id", "order_key", "EXPR", "CONCAT('OK', order_id)", null, 0);
        mapping(order, "order_id", "order_id", "DIRECT", null, null, 1);
        mapping(order, "customer_id", "customer_id", "DIRECT", null, null, 2);
        mapping(order, "product_id", "product_id", "DIRECT", null, null, 3);
        mapping(order, "amount", "amount", "DIRECT", null, null, 4);
        mapping(order, "quantity", "quantity", "DIRECT", null, null, 5);
        mapping(order, "order_time", "order_date", "EXPR", "CAST(order_time AS DATE)", null, 6);
        mapping(order, "pay_time", "pay_date", "EXPR", "CAST(pay_time AS DATE)", null, 7);
        mapping(order, "status", "status", "DIRECT", null, null, 8);
        mapping(order, "region", "region", "DIRECT", null, null, 9);
        mapping(order, "channel", "channel", "DIRECT", null, null, 10);
        mapping(order, "status", "is_refund", "EXPR", "CASE WHEN status = '退款中' THEN 1 ELSE 0 END", null, 11);
        check(order, "ROW_COUNT", null, 0);
        check(order, "PK_DUP", "order_key", 0);
        check(order, "SUM", "amount", 0.01);
        log.info("已装载数据迁移演示数据：2 个项目 / 23 条字段映射 / 7 个对账项");
    }

    private void mapping(long projectId, String sourceCol, String targetCol, String transformType,
                         String expr, String defaultValue, int sortNo) {
        db.insert("INSERT INTO mig_mapping(project_id,source_column,target_column,transform_type,transform_expr,"
                        + "default_value,sort_no) VALUES(?,?,?,?,?,?,?)",
                projectId, sourceCol, targetCol, transformType, expr, defaultValue, sortNo);
    }

    private void check(long projectId, String type, String column, double tolerance) {
        db.insert("INSERT INTO mig_check(project_id,check_type,column_name,tolerance) VALUES(?,?,?,?)",
                projectId, type, column, java.math.BigDecimal.valueOf(tolerance));
    }
}
