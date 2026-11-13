package com.dgp.seed;

import com.dgp.common.Db;
import com.dgp.common.JsonUtil;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/** 集成与调度种子：ETL 作业、运行实例、加工级血缘、工作流、沙箱策略。 */
@Component
public class IntegrationSeeder {

    private final Db db;
    private final Random rnd = new Random(31);

    public IntegrationSeeder(Db db) {
        this.db = db;
    }

    public void seed() {
        if (db.count("SELECT COUNT(*) FROM int_job") > 0) return;
        sandbox();
        long cust = job("JOB-CUST-DIM", "客户维度加工", "ETL", "每日 02:15 将 ODS 客户加工为客户维度", 1440,
                dsl(new String[][]{{"src", "SOURCE", "ods_customer"}, {"f1", "FILTER", "过滤无效客户"}, {"m1", "FIELD_MAP", "字段映射与派生"}},
                        new String[][]{{"src", "f1"}, {"f1", "m1"}, {"m1", "sink"}}, "dwd_customer_dim"),
                "DELETE FROM dwd_customer_dim;"
                        + "INSERT INTO dwd_customer_dim(customer_key,customer_id,cust_name,gender,age,phone,email,city,reg_date,member_level,is_valid) "
                        // 注意：MySQL 的 CAST 目标类型不含 INT，这里直接做减法由数据库隐式转数值（H2/MySQL 均可用）
                        + "SELECT CONCAT('CK', customer_id), customer_id, cust_name, gender, EXTRACT(YEAR FROM CURRENT_DATE) - EXTRACT(YEAR FROM birthday), phone, email, city, "
                        + "CAST(reg_time AS DATE), member_level, CASE WHEN phone IS NULL OR city IS NULL THEN 0 ELSE 1 END FROM ods_customer");
        long order = job("JOB-ORDER-FACT", "订单事实加工", "ETL", "每小时加工订单事实并关联产品维度", 60,
                dsl(new String[][]{{"src", "SOURCE", "ods_order"}, {"j1", "JOIN", "关联产品维"}, {"m1", "FIELD_MAP", "金额与日期标准化"}},
                        new String[][]{{"src", "j1"}, {"j1", "m1"}, {"m1", "sink"}}, "dwd_order_fact"),
                "DELETE FROM dwd_order_fact;"
                        + "INSERT INTO dwd_order_fact(order_key,order_id,customer_id,product_id,amount,quantity,order_date,pay_date,status,region,channel,is_refund) "
                        + "SELECT CONCAT('OK', order_id), order_id, customer_id, product_id, amount, quantity, CAST(order_time AS DATE), CAST(pay_time AS DATE), "
                        + "status, region, channel, CASE WHEN status='退款中' THEN 1 ELSE 0 END FROM ods_order");
        long pay = job("JOB-PAY-SYNC", "支付流水同步", "ETL", "准实时同步支付流水并回写订单状态", 5,
                dsl(new String[][]{{"src", "SOURCE", "ods_payment"}, {"v1", "VALIDATE", "支付金额一致性校验"}},
                        new String[][]{{"src", "v1"}, {"v1", "sink"}}, "dwd_order_fact"),
                "UPDATE dwd_order_fact SET status='已支付' WHERE status='待支付' "
                        + "AND order_id IN (SELECT order_id FROM ods_payment WHERE status='成功')");
        long sum = job("JOB-ORDER-SUM", "订单日汇总", "ETL", "按区域/渠道汇总订单与退款", 1440,
                dsl(new String[][]{{"src", "SOURCE", "dwd_order_fact"}, {"a1", "AGGREGATE", "按日-区域-渠道聚合"}},
                        new String[][]{{"src", "a1"}, {"a1", "sink"}}, "dws_order_summary"),
                "DELETE FROM dws_order_summary;"
                        + "INSERT INTO dws_order_summary(stat_date,region,channel,order_cnt,amount,refund_amt) "
                        + "SELECT order_date, region, channel, COUNT(*), SUM(amount), SUM(CASE WHEN is_refund=1 THEN amount ELSE 0 END) "
                        + "FROM dwd_order_fact GROUP BY order_date, region, channel");
        long value = job("JOB-CUST-VALUE", "客户价值计算", "ETL", "计算客户累计消费、订单数与价值分层", 1440,
                dsl(new String[][]{{"src", "SOURCE", "dwd_order_fact"}, {"j1", "JOIN", "关联客户维度"}, {"a1", "AGGREGATE", "客户粒度聚合"}},
                        new String[][]{{"src", "j1"}, {"j1", "a1"}, {"a1", "sink"}}, "dws_customer_value"),
                "DELETE FROM dws_customer_value;"
                        + "INSERT INTO dws_customer_value(customer_id,total_amount,order_cnt,last_order_date,value_level) "
                        + "SELECT customer_id, SUM(amount), COUNT(*), MAX(order_date), "
                        + "CASE WHEN COUNT(*)>20 THEN '高价值' WHEN COUNT(*)>8 THEN '中价值' ELSE '普通' END "
                        + "FROM dwd_order_fact GROUP BY customer_id");
        long region = job("JOB-REGION-SALES", "区域销售与驾驶舱", "ETL", "生成区域达成与销售驾驶舱指标", 1440,
                dsl(new String[][]{{"src", "SOURCE", "dwd_order_fact"}, {"a1", "AGGREGATE", "区域达成"}, {"a2", "AGGREGATE", "驾驶舱指标"}},
                        new String[][]{{"src", "a1"}, {"src", "a2"}, {"a1", "sink"}, {"a2", "sink2"}}, "dws_region_sales"),
                "DELETE FROM dws_region_sales;"
                        + "INSERT INTO dws_region_sales(stat_date,region,sales_amount,target_amount,completion_rate) "
                        + "SELECT order_date, region, SUM(amount), SUM(amount)*1.05, SUM(amount)/(SUM(amount)*1.05) "
                        + "FROM dwd_order_fact GROUP BY order_date, region;"
                        + "DELETE FROM ads_sales_dashboard;"
                        + "INSERT INTO ads_sales_dashboard(stat_date,total_amount,order_cnt,customer_cnt,avg_order_amount,refund_rate) "
                        + "SELECT order_date, SUM(amount), COUNT(*), COUNT(DISTINCT customer_id), SUM(amount)/COUNT(*), "
                        + "SUM(CASE WHEN is_refund=1 THEN amount ELSE 0 END)/SUM(amount) FROM dwd_order_fact GROUP BY order_date");
        long dev = job("JOB-DEVICE-FACT", "设备事实加工", "ETL", "分钟级清洗设备时序并落入设备事实表", 5,
                dsl(new String[][]{{"src", "SOURCE", "ods_device_metric"}, {"f1", "FILTER", "剔除空值与越界"}, {"m1", "TYPE_CONVERT", "精度归一"}},
                        new String[][]{{"src", "f1"}, {"f1", "m1"}, {"m1", "sink"}}, "dwd_device_fact"),
                "DELETE FROM dwd_device_fact WHERE collect_date = CURRENT_DATE;"
                        + "INSERT INTO dwd_device_fact(device_key,device_id,workshop,metric_name,metric_value,collect_date) "
                        // 设备键 = 'DK' + 设备 + 指标 + 当日分钟偏移：用 TIMESTAMPDIFF 替代 CAST AS VARCHAR，
                        // 避免 H2(VARCHAR) 与 MySQL(CHAR) 的方言差异
                        + "SELECT CONCAT(CONCAT(CONCAT('DK', device_id), '-'), CONCAT(metric_name, CONCAT('-', "
                        + "TIMESTAMPDIFF(MINUTE, CAST(collect_time AS DATE), collect_time)))), device_id, workshop, metric_name, "
                        + "metric_value, CAST(collect_time AS DATE) FROM ods_device_metric "
                        + "WHERE metric_value BETWEEN -100 AND 200 AND CAST(collect_time AS DATE)=CURRENT_DATE");

        workflow();
        lineage();
        instances(new long[]{cust, order, pay, sum, value, region, dev});
    }

    private void sandbox() {
        db.insert("INSERT INTO int_sandbox_policy(name,allow_sql,allow_python,allow_shell,max_runtime_min,max_memory_mb,network_whitelist,status) "
                + "VALUES('默认 SQL 沙箱',1,0,0,30,1024,'',?)", "ENABLED");
        db.insert("INSERT INTO int_sandbox_policy(name,allow_sql,allow_python,allow_shell,max_runtime_min,max_memory_mb,network_whitelist,status) "
                + "VALUES('高级脚本沙箱（需审批）',1,1,1,60,2048,'10.10.0.0/16',?)", "DISABLED");
    }

    private String dsl(String[][] nodes, String[][] edges, String sink) {
        List<Map<String, Object>> ns = new ArrayList<>();
        for (String[] n : nodes) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", n[0]);
            m.put("type", n[1]);
            m.put("name", n[2]);
            if ("SOURCE".equals(n[1])) m.put("table", n[2]);
            ns.add(m);
        }
        Map<String, Object> sinkNode = new LinkedHashMap<>();
        sinkNode.put("id", "sink");
        sinkNode.put("type", "SINK");
        sinkNode.put("name", sink);
        sinkNode.put("table", sink);
        ns.add(sinkNode);
        List<Map<String, Object>> es = new ArrayList<>();
        for (String[] e : edges) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("from", e[0]);
            m.put("to", e[1]);
            es.add(m);
        }
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("dslVersion", "1.0");
        root.put("nodes", ns);
        root.put("edges", es);
        return JsonUtil.toJson(root);
    }

    private long job(String code, String name, String type, String desc, int interval, String dsl, String script) {
        LocalDateTime now = LocalDateTime.now();
        return db.insert("INSERT INTO int_job(tenant_id,code,name,cn_name,job_type,engine,status,schedule_type,cron,interval_min,priority,"
                        + "timeout_min,retry,industry,dsl,script_lang,script,owner,description,version,last_run_time,next_run_time,"
                        + "success_count,fail_count,create_time) VALUES(1,?,?,?,?,'BATCH','PUBLISHED','CRON',?,?,5,60,2,'GOV',?, 'SQL',?,"
                        + "'张三',?,'1.0.0',?,?,46,3,?)",
                code, name, name, type, String.valueOf(interval), interval, dsl, script, desc,
                now.minusHours(1), now.plusMinutes(interval), now.minusDays(30));
    }

    private void workflow() {
        String nodes = JsonUtil.toJson(List.of(
                Map.of("key", "n1", "jobCode", "JOB-CUST-DIM", "upstream", List.of()),
                Map.of("key", "n2", "jobCode", "JOB-ORDER-FACT", "upstream", List.of()),
                Map.of("key", "n3", "jobCode", "JOB-PAY-SYNC", "upstream", List.of("n2")),
                Map.of("key", "n4", "jobCode", "JOB-ORDER-SUM", "upstream", List.of("n2", "n3")),
                Map.of("key", "n5", "jobCode", "JOB-CUST-VALUE", "upstream", List.of("n1", "n2")),
                Map.of("key", "n6", "jobCode", "JOB-REGION-SALES", "upstream", List.of("n4"))));
        db.insert("INSERT INTO int_job(tenant_id,code,name,cn_name,job_type,engine,status,schedule_type,cron,interval_min,priority,"
                        + "timeout_min,retry,industry,dsl,script_lang,owner,description,version,next_run_time,success_count,fail_count,create_time) "
                        + "VALUES(1,'WF-SALES-DAILY','销售域日批工作流','销售域日批工作流','WORKFLOW','BATCH','PUBLISHED','CRON','0 0 2 * * ?',1440,8,180,1,'GOV',?,"
                        + "'SHELL','张三','客户-订单-支付-汇总-驾驶舱的依赖编排，失败自动重试 1 次，超时 180 分钟','1.0.0',?,58,2,?)",
                nodes, LocalDateTime.now().plusHours(3), LocalDateTime.now().minusDays(40));
    }

    private void lineage() {
        columnEdge("ods_customer", "customer_id", "dwd_customer_dim", "customer_id", "JOB-CUST-DIM", "DIRECT", "o.customer_id");
        columnEdge("ods_customer", "cust_name", "dwd_customer_dim", "cust_name", "JOB-CUST-DIM", "DIRECT", "o.cust_name");
        columnEdge("ods_customer", "gender", "dwd_customer_dim", "gender", "JOB-CUST-DIM", "DIRECT", "o.gender");
        columnEdge("ods_customer", "phone", "dwd_customer_dim", "phone", "JOB-CUST-DIM", "MASK", "mask(o.phone)");
        columnEdge("ods_customer", "email", "dwd_customer_dim", "email", "JOB-CUST-DIM", "DIRECT", "o.email");
        columnEdge("ods_customer", "city", "dwd_customer_dim", "city", "JOB-CUST-DIM", "DIRECT", "o.city");
        columnEdge("ods_customer", "birthday", "dwd_customer_dim", "age", "JOB-CUST-DIM", "DERIVE",
                "EXTRACT(YEAR FROM CURRENT_DATE) - EXTRACT(YEAR FROM o.birthday)");
        columnEdge("ods_customer", "reg_time", "dwd_customer_dim", "reg_date", "JOB-CUST-DIM", "TRUNCATE", "CAST(o.reg_time AS DATE)");
        columnEdge("ods_customer", "member_level", "dwd_customer_dim", "member_level", "JOB-CUST-DIM", "DIRECT", "o.member_level");

        columnEdge("ods_order", "order_id", "dwd_order_fact", "order_id", "JOB-ORDER-FACT", "DIRECT", "o.order_id");
        columnEdge("ods_order", "customer_id", "dwd_order_fact", "customer_id", "JOB-ORDER-FACT", "DIRECT", "o.customer_id");
        columnEdge("ods_order", "product_id", "dwd_order_fact", "product_id", "JOB-ORDER-FACT", "DIRECT", "o.product_id");
        columnEdge("ods_order", "amount", "dwd_order_fact", "amount", "JOB-ORDER-FACT", "DIRECT", "o.amount");
        columnEdge("ods_order", "quantity", "dwd_order_fact", "quantity", "JOB-ORDER-FACT", "DIRECT", "o.quantity");
        columnEdge("ods_order", "order_time", "dwd_order_fact", "order_date", "JOB-ORDER-FACT", "TRUNCATE", "CAST(o.order_time AS DATE)");
        columnEdge("ods_order", "pay_time", "dwd_order_fact", "pay_date", "JOB-ORDER-FACT", "TRUNCATE", "CAST(o.pay_time AS DATE)");
        columnEdge("ods_order", "status", "dwd_order_fact", "status", "JOB-ORDER-FACT", "DIRECT", "o.status");
        columnEdge("ods_product", "product_id", "dwd_order_fact", "product_id", "JOB-ORDER-FACT", "JOIN_KEY", "o.product_id=p.product_id");

        columnEdge("ods_payment", "pay_amount", "dwd_order_fact", "amount", "JOB-PAY-SYNC", "VALIDATE", "校验 p.pay_amount=o.amount");
        columnEdge("ods_payment", "trade_no", "dwd_order_fact", "order_id", "JOB-PAY-SYNC", "JOIN_KEY", "p.order_id=o.order_id");

        columnEdge("dwd_order_fact", "amount", "dws_order_summary", "amount", "JOB-ORDER-SUM", "AGGREGATE", "SUM(amount)");
        columnEdge("dwd_order_fact", "order_id", "dws_order_summary", "order_cnt", "JOB-ORDER-SUM", "AGGREGATE", "COUNT(order_id)");
        columnEdge("dwd_order_fact", "amount", "dws_customer_value", "total_amount", "JOB-CUST-VALUE", "AGGREGATE", "SUM(amount)");
        columnEdge("dwd_customer_dim", "customer_id", "dws_customer_value", "customer_id", "JOB-CUST-VALUE", "JOIN_KEY", "c.customer_id=f.customer_id");
        columnEdge("dwd_order_fact", "amount", "dws_region_sales", "sales_amount", "JOB-REGION-SALES", "AGGREGATE", "SUM(amount)");
        columnEdge("dws_region_sales", "sales_amount", "ads_sales_dashboard", "total_amount", "JOB-REGION-SALES", "AGGREGATE", "SUM(sales_amount)");
        columnEdge("dws_order_summary", "order_cnt", "ads_sales_dashboard", "order_cnt", "JOB-REGION-SALES", "AGGREGATE", "SUM(order_cnt)");

        columnEdge("ods_device_metric", "metric_value", "dwd_device_fact", "metric_value", "JOB-DEVICE-FACT", "FILTER", "value BETWEEN -100 AND 200");
        columnEdge("ods_device_metric", "collect_time", "dwd_device_fact", "collect_date", "JOB-DEVICE-FACT", "TRUNCATE", "CAST(collect_time AS DATE)");
        columnEdge("ods_device_metric", "workshop", "dwd_device_fact", "workshop", "JOB-DEVICE-FACT", "DIRECT", "workshop");
    }

    private void columnEdge(String st, String sc, String dt, String dc, String job, String type, String expr) {
        db.insert("INSERT INTO md_lineage_edge(tenant_id,src_table,src_column,dst_table,dst_column,job_name,transform_type,"
                        + "transform_expr,granularity,create_time) VALUES(1,?,?,?,?,?,?,?,'COLUMN',?)",
                st, sc, dt, dc, job, type, expr, LocalDateTime.now());
        // 同时补一条表级边
        Object existsVal = db.scalar("SELECT COUNT(*) FROM md_lineage_edge WHERE src_table=? AND dst_table=? AND granularity='TABLE'",
                st, dt);
        long exists = existsVal == null ? 0L : ((Number) existsVal).longValue();
        if (exists == 0) {
            db.insert("INSERT INTO md_lineage_edge(tenant_id,src_table,dst_table,job_name,transform_type,granularity,create_time) "
                    + "VALUES(1,?,?,?,'JOB','TABLE',?)", st, dt, job, LocalDateTime.now());
        }
    }

    private void instances(long[] jobIds) {
        String[] triggers = {"CRON", "MANUAL", "CRON", "CRON", "RETRY"};
        for (int d = 6; d >= 0; d--) {
            for (long jid : jobIds) {
                Map<String, Object> j = db.one("SELECT * FROM int_job WHERE id=?", jid);
                boolean fail = rnd.nextInt(12) == 0;
                LocalDateTime start = LocalDateTime.now().minusDays(d).minusHours(2 + rnd.nextInt(6));
                long dur = 3000 + rnd.nextInt(90000);
                db.insert("INSERT INTO int_job_instance(job_id,job_name,status,trigger_type,start_time,end_time,duration_ms,"
                                + "rows_written,message,operator,trace_id) VALUES(?,?,?,?,?,?,?,?,?,?,?)",
                        jid, j.get("cnName"), fail ? "FAILED" : "SUCCESS", triggers[rnd.nextInt(triggers.length)],
                        start, start.plusNanos(dur * 1_000_000), dur,
                        fail ? 0 : 800 + rnd.nextInt(24000),
                        fail ? "目标表写入失败：唯一键冲突" : "执行成功，写出 " + (800 + rnd.nextInt(24000)) + " 行",
                        "调度器", "sim-" + jid + "-" + d);
            }
        }
    }
}
