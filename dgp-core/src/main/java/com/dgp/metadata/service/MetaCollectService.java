package com.dgp.metadata.service;

import com.dgp.security.AuthContext;
import com.dgp.common.Db;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 元数据采集器（对应 engine-collect 的本地实现）。
 *
 * <p>真实读取 information_schema 的表/列/PK/注释，计算行数与存储估算，
 * 与已有元数据做差异比对，输出新增表、变更列。采集结果直接回写 md_table / md_column。</p>
 */
@Service
public class MetaCollectService {

    private final Db db;

    /** 元数据采集读取的系统库 schema：H2/PostgreSQL 为 public，MySQL 为数据库名（dgp）。 */
    @org.springframework.beans.factory.annotation.Value("${dgp.meta.schema:public}")
    private String metaSchema = "public";

    private static final Map<String, String> TABLE_CN = new LinkedHashMap<>();
    private static final Map<String, String> TABLE_DOMAIN = new HashMap<>();
    private static final Map<String, String> TABLE_FREQ = new HashMap<>();
    private static final Map<String, String> COL_CN = new HashMap<>();
    private static final Map<String, String> SENSITIVE = new HashMap<>();

    static {
        TABLE_CN.put("ods_customer", "客户基础信息表");
        TABLE_CN.put("ods_order", "订单明细表");
        TABLE_CN.put("ods_product", "产品主数据表");
        TABLE_CN.put("ods_payment", "支付流水表");
        TABLE_CN.put("ods_device_metric", "设备时序指标表");
        TABLE_CN.put("ods_gov_population", "政务人口基础表");
        TABLE_CN.put("dwd_customer_dim", "客户维度表");
        TABLE_CN.put("dwd_order_fact", "订单事实表");
        TABLE_CN.put("dwd_device_fact", "设备运行事实表");
        TABLE_CN.put("dws_order_summary", "订单日汇总表");
        TABLE_CN.put("dws_customer_value", "客户价值汇总表");
        TABLE_CN.put("dws_region_sales", "区域销售汇总表");
        TABLE_CN.put("ads_sales_dashboard", "销售驾驶舱指标表");

        TABLE_DOMAIN.put("ods_customer", "客户域");
        TABLE_DOMAIN.put("dwd_customer_dim", "客户域");
        TABLE_DOMAIN.put("dws_customer_value", "客户域");
        TABLE_DOMAIN.put("ods_order", "交易域");
        TABLE_DOMAIN.put("dwd_order_fact", "交易域");
        TABLE_DOMAIN.put("dws_order_summary", "交易域");
        TABLE_DOMAIN.put("dws_region_sales", "交易域");
        TABLE_DOMAIN.put("ads_sales_dashboard", "经营分析域");
        TABLE_DOMAIN.put("ods_payment", "交易域");
        TABLE_DOMAIN.put("ods_product", "产品域");
        TABLE_DOMAIN.put("ods_device_metric", "设备域");
        TABLE_DOMAIN.put("dwd_device_fact", "设备域");
        TABLE_DOMAIN.put("ods_gov_population", "政务域");

        TABLE_FREQ.put("ods_order", "实时");
        TABLE_FREQ.put("ods_payment", "实时");
        TABLE_FREQ.put("ods_device_metric", "分钟级");
        TABLE_FREQ.put("ods_customer", "每日");
        TABLE_FREQ.put("ods_product", "每日");
        TABLE_FREQ.put("ods_gov_population", "每周");
        TABLE_FREQ.put("dwd_customer_dim", "每日");
        TABLE_FREQ.put("dwd_order_fact", "每小时");
        TABLE_FREQ.put("dwd_device_fact", "每小时");
        TABLE_FREQ.put("dws_order_summary", "每日");
        TABLE_FREQ.put("dws_customer_value", "每日");
        TABLE_FREQ.put("dws_region_sales", "每日");
        TABLE_FREQ.put("ads_sales_dashboard", "每日");

        String[][] cols = {
                {"customer_id", "客户标识"}, {"cust_name", "客户名称"}, {"gender", "性别"}, {"birthday", "出生日期"},
                {"phone", "手机号码"}, {"email", "电子邮箱"}, {"id_card", "证件号码"}, {"province", "省份"},
                {"city", "城市"}, {"addr", "详细地址"}, {"reg_channel", "注册渠道"}, {"reg_time", "注册时间"},
                {"status", "状态"}, {"member_level", "会员等级"}, {"order_id", "订单编号"}, {"product_id", "产品标识"},
                {"product_name", "产品名称"}, {"amount", "订单金额"}, {"quantity", "数量"}, {"discount", "折扣"},
                {"order_time", "下单时间"}, {"pay_time", "支付时间"}, {"channel", "渠道"}, {"region", "区域"},
                {"sales_rep", "销售代表"}, {"price", "单价"}, {"cost", "成本价"}, {"supplier", "供应商"},
                {"shelf_date", "上架日期"}, {"category", "产品分类"}, {"pay_id", "支付流水号"}, {"pay_channel", "支付方式"},
                {"pay_amount", "支付金额"}, {"trade_no", "交易号"}, {"device_id", "设备标识"}, {"device_name", "设备名称"},
                {"workshop", "车间"}, {"metric_name", "指标名称"}, {"metric_value", "指标值"}, {"unit", "计量单位"},
                {"collect_time", "采集时间"}, {"person_name", "姓名"}, {"street", "街道"}, {"customer_key", "客户代理键"},
                {"age", "年龄"}, {"reg_date", "注册日期"}, {"is_valid", "是否有效"}, {"order_key", "订单代理键"},
                {"order_date", "下单日期"}, {"pay_date", "支付日期"}, {"is_refund", "是否退款"}, {"device_key", "设备代理键"},
                {"collect_date", "采集日期"}, {"stat_date", "统计日期"}, {"order_cnt", "订单数"}, {"refund_amt", "退款金额"},
                {"total_amount", "累计消费金额"}, {"last_order_date", "最近下单日期"}, {"value_level", "价值等级"},
                {"sales_amount", "销售额"}, {"target_amount", "目标金额"}, {"completion_rate", "完成率"},
                {"customer_cnt", "客户数"}, {"avg_order_amount", "客单价"}, {"refund_rate", "退款率"}
        };
        for (String[] c : cols) {
            COL_CN.put(c[0], c[1]);
        }

        SENSITIVE.put("phone", "L3");
        SENSITIVE.put("id_card", "L4");
        SENSITIVE.put("email", "L3");
        SENSITIVE.put("addr", "L3");
        SENSITIVE.put("cust_name", "L2");
        SENSITIVE.put("person_name", "L2");
        SENSITIVE.put("trade_no", "L2");
        SENSITIVE.put("pay_amount", "L2");
        SENSITIVE.put("amount", "L2");
    }

    public MetaCollectService(Db db) {
        this.db = db;
    }

    /** 执行一次采集，返回统计。 */
    @Transactional
    public Map<String, Object> collect(long datasourceId, String mode, String operator) {
        long start = System.currentTimeMillis();
        Long taskId = null;
        Map<String, Object> task = db.one("SELECT * FROM md_collect_task WHERE datasource_id=? ORDER BY id LIMIT 1", datasourceId);
        if (task != null) {
            taskId = ((Number) task.get("id")).longValue();
            db.update("UPDATE md_collect_task SET last_run_time=?, next_run_time=? WHERE id=?",
                    LocalDateTime.now(), LocalDateTime.now().plusHours(6), taskId);
        }
        // 修正：原实现 db.scalar(sql, id, "未知数据源") 多传一个参数 → 异常被吞 → ds_name 写成字符串 "null"
        Object dsNameObj = db.scalar("SELECT name FROM md_datasource WHERE id=?", datasourceId);
        String dsName = dsNameObj == null ? "未知数据源" : String.valueOf(dsNameObj);
        int newTables = 0;
        int changedCols = 0;

        List<Map<String, Object>> tables = db.list(
                "SELECT table_name FROM information_schema.tables WHERE table_schema=? "
                        + "AND (table_name LIKE 'ods%' OR table_name LIKE 'dwd%' "
                        + "OR table_name LIKE 'dws%' OR table_name LIKE 'ads%') ORDER BY table_name", metaSchema);
        Map<String, Object> pkMap = new HashMap<>();
        for (Map<String, Object> r : db.list(
                "SELECT kcu.table_name AS tn, kcu.column_name AS cn FROM information_schema.table_constraints tc "
                        + "JOIN information_schema.key_column_usage kcu ON tc.constraint_name = kcu.constraint_name "
                        + "AND tc.table_schema = kcu.table_schema AND tc.table_name = kcu.table_name "
                        + "WHERE tc.constraint_type='PRIMARY KEY' AND tc.table_schema=?", metaSchema)) {
            pkMap.put(r.get("tn") + "." + r.get("cn"), true);
        }

        for (Map<String, Object> t : tables) {
            String name = String.valueOf(t.get("tableName")).toLowerCase();
            String layer = name.substring(0, 3).toUpperCase();
            long rowCount = db.count("SELECT COUNT(*) FROM " + name);
            BigDecimal sizeMb = BigDecimal.valueOf(rowCount * 210.0 / 1048576.0).setScale(2, RoundingMode.HALF_UP);
            Map<String, Object> exist = db.one("SELECT id FROM md_table WHERE name=?", name);
            long tableId;
            if (exist == null) {
                tableId = db.insert("INSERT INTO md_table(tenant_id,datasource_id,name,cn_name,obj_type,layer,domain,row_count,data_size_mb,"
                                + "owner,description,biz_desc,update_freq,last_ddl_time,status,tags,quality_score,access_count,svc_calls,"
                                + "sensitive_level,industry,create_time) VALUES(1,?,?,?,'TABLE',?,?,?,?,'数据治理组',?,?,?,?,'已发布',?,?,?,0,?,?,?)",
                        datasourceId, name, TABLE_CN.getOrDefault(name, name), layer,
                        TABLE_DOMAIN.getOrDefault(name, "公共域"), rowCount, sizeMb,
                        TABLE_CN.getOrDefault(name, name) + "，由元数据采集任务自动登记。",
                        "支撑" + TABLE_DOMAIN.getOrDefault(name, "公共域") + "分析与稽核场景。",
                        TABLE_FREQ.getOrDefault(name, "每日"), LocalDateTime.now(),
                        layer.equals("ODS") ? "原始层,自动采集" : layer + "层,加工产出",
                        computeQuality(name), 120 + (int) (rowCount % 800), "L2", "GOV", LocalDateTime.now());
                newTables++;
            } else {
                tableId = ((Number) exist.get("id")).longValue();
                db.update("UPDATE md_table SET row_count=?, data_size_mb=?, last_ddl_time=? WHERE id=?",
                        rowCount, sizeMb, LocalDateTime.now(), tableId);
            }

            List<Map<String, Object>> cols = db.list(
                    "SELECT column_name, data_type, character_maximum_length, numeric_precision, numeric_scale, is_nullable, ordinal_position "
                            + "FROM information_schema.columns WHERE table_schema=? AND table_name=? ORDER BY ordinal_position",
                    metaSchema, name);
            Set<String> scanned = new HashSet<>();
            for (Map<String, Object> c : cols) {
                String colName = String.valueOf(c.get("columnName")).toLowerCase();
                scanned.add(colName);
                String dataType = String.valueOf(c.get("dataType"));
                String len = c.get("characterMaximumLength") == null
                        ? (c.get("numericPrecision") == null ? "" : String.valueOf(c.get("numericPrecision")))
                        : String.valueOf(c.get("characterMaximumLength"));
                Map<String, Object> cExist = db.one("SELECT * FROM md_column WHERE table_id=? AND name=?", tableId, colName);
                int isPk = pkMap.containsKey(name + "." + colName) ? 1 : 0;
                int nullable = "YES".equals(c.get("isNullable")) ? 1 : 0;
                int order = ((Number) c.get("ordinalPosition")).intValue();
                if (cExist == null) {
                    db.insert("INSERT INTO md_column(table_id,name,cn_name,data_type,data_len,nullable,is_pk,column_order,comment,"
                                    + "std_code,sensitive_level,mask_algo) VALUES(?,?,?,?,?,?,?,?,?,?,?,?)",
                            tableId, colName, COL_CN.getOrDefault(colName, colName), dataType, len,
                            nullable, isPk, order, COL_CN.getOrDefault(colName, colName),
                            guessStdCode(colName), SENSITIVE.getOrDefault(colName, "L1"), maskAlgo(colName));
                    changedCols++;
                } else {
                    // 仅在结构确实变化时计一次"变更列"（原实现 changed 恒 +0，改列永远统计不到）
                    boolean diff = !Objects.equals(String.valueOf(cExist.get("dataType")), dataType)
                            || !Objects.equals(String.valueOf(cExist.get("dataLen")), len)
                            || !Objects.equals(Db.longVal(cExist.get("nullable"), -1), (long) nullable)
                            || !Objects.equals(Db.longVal(cExist.get("isPk"), -1), (long) isPk);
                    db.update("UPDATE md_column SET data_type=?, data_len=?, nullable=?, is_pk=? WHERE id=?",
                            dataType, len, nullable, isPk, cExist.get("id"));
                    if (diff) changedCols++;
                }
            }
            // 物理表已删除的列同步清理，避免元数据只增不减
            for (Map<String, Object> stale : db.list("SELECT id,name FROM md_column WHERE table_id=?", tableId)) {
                if (!scanned.contains(String.valueOf(stale.get("name")))) {
                    db.update("DELETE FROM md_column WHERE id=?", stale.get("id"));
                    changedCols++;
                }
            }
        }

        long duration = System.currentTimeMillis() - start;
        db.insert("INSERT INTO md_collect_instance(task_id,datasource_id,ds_name,status,start_time,end_time,duration_ms,"
                        + "new_tables,changed_cols,message,operator) VALUES(?,?,?,'SUCCESS',?,?,?,?,?,?,?)",
                taskId, datasourceId, dsName, LocalDateTime.now().minusNanos(duration * 1_000_000),
                LocalDateTime.now(), duration, newTables, changedCols,
                "扫描表 " + tables.size() + " 张，新增表 " + newTables + " 张，新增列 " + changedCols + " 个", operator);
        if (taskId != null) {
            db.update("UPDATE md_collect_task SET new_tables=?, changed_cols=? WHERE id=?", newTables, changedCols, taskId);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("datasourceId", datasourceId);
        out.put("tableCount", tables.size());
        out.put("newTables", newTables);
        out.put("changedColumns", changedCols);
        out.put("durationMs", duration);
        out.put("operator", operator == null ? AuthContext.username() : operator);
        return out;
    }

    /**
     * 采集时不给"没有规则的表"编造质量分（原实现返回 88 + 表名长度 % 9 的伪随机值，
     * 会把假数据当作真实治理指标展示）。有规则时才用真实规则通过率。
     */
    private BigDecimal computeQuality(String table) {
        Object v = db.scalar("SELECT AVG(last_pass_rate) FROM qa_rule WHERE table_name=?", table);
        if (v == null) return null;
        return new BigDecimal(v.toString()).setScale(2, RoundingMode.HALF_UP);
    }

    static String guessStdCode(String col) {
        return switch (col) {
            case "customer_id", "cust_id" -> "DE_CUST_ID";
            case "cust_name", "person_name" -> "DE_CUST_NAME";
            case "phone" -> "DE_MOBILE";
            case "id_card" -> "DE_ID_CARD";
            case "email" -> "DE_EMAIL";
            case "amount", "pay_amount", "total_amount" -> "DE_AMOUNT";
            case "order_time", "order_date" -> "DE_ORDER_TIME";
            case "gender" -> "DE_GENDER";
            case "city" -> "DE_CITY";
            case "addr" -> "DE_ADDRESS";
            default -> null;
        };
    }

    static String maskAlgo(String col) {
        return switch (col) {
            case "phone" -> "MASK";
            case "id_card" -> "MASK";
            case "email" -> "MASK";
            case "addr" -> "TRUNCATE";
            case "pay_amount", "amount" -> "GENERALIZE";
            default -> null;
        };
    }

    public List<String> sourceTables() {
        List<String> out = new ArrayList<>();
        for (Map<String, Object> r : db.list("SELECT name FROM md_table ORDER BY name")) {
            out.add(String.valueOf(r.get("name")));
        }
        return out;
    }
}
