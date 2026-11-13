package com.dgp.seed;

import com.dgp.common.Db;
import com.dgp.common.Ids;
import com.dgp.common.JsonUtil;
import com.dgp.quality.service.QualityEngine;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/** 质量域种子：规则模板、规则、稽核任务、真实执行、评分历史、问题闭环样本。 */
@Component
public class QualitySeeder {

    private final Db db;
    private final QualityEngine engine;
    private final Random rnd = new Random(77);

    public QualitySeeder(Db db, QualityEngine engine) {
        this.db = db;
        this.engine = engine;
    }

    public void seed() {
        if (db.count("SELECT COUNT(*) FROM qa_rule") > 0) return;
        templates();
        rules();

        long chkCust = check("CHK-CUST-DAILY", "客户域每日稽核", "覆盖客户主数据的完整性、唯一性、有效性", "DAILY", "0 0 1 * * ?");
        long chkTrade = check("CHK-TRADE-DAILY", "交易域每日稽核", "覆盖订单金额、支付一致性、状态枚举", "DAILY", "0 30 1 * * ?");
        long chkInd = check("CHK-DEVICE-HOURLY", "设备与政务指标稽核", "设备量程、采集时效与政务人口证件合规", "HOURLY", "0 10 * * * ?");
        link(chkCust, "ods_customer");
        link(chkCust, "dwd_customer_dim");
        link(chkTrade, "ods_order");
        link(chkTrade, "ods_payment");
        link(chkTrade, "ods_product");
        link(chkInd, "ods_device_metric");
        link(chkInd, "ods_gov_population");

        engine.runCheck(chkCust, "MANUAL", "刘琴");
        engine.runCheck(chkTrade, "MANUAL", "刘琴");
        engine.runCheck(chkInd, "MANUAL", "刘琴");

        for (Map<String, Object> t : db.list("SELECT name FROM md_table")) {
            engine.tableScore(String.valueOf(t.get("name")));
        }
        history();
        enrichIssues();
    }

    private void templates() {
        Object[][] tpl = {
                {"TPL_NOT_NULL", "非空校验模板", "*", "COMPLETENESS", "NOT_NULL",
                        "SELECT COUNT(*) FROM ${table} WHERE ${column} IS NULL OR TRIM(${column})=''", ".*", "核心字段不允许为空"},
                {"TPL_UNIQUE", "唯一性校验模板", "*", "UNIQUENESS", "UNIQUE",
                        "SELECT COUNT(*) FROM (SELECT ${column} FROM ${table} WHERE ${column} IS NOT NULL GROUP BY ${column} HAVING COUNT(*)>1) t",
                        ".*_id|.*_no", "业务主键不得重复"},
                {"TPL_REGEX_MOBILE", "手机号格式模板", "*", "VALIDITY", "REGEX",
                        "SELECT COUNT(*) FROM ${table} WHERE ${column} IS NOT NULL AND ${column} NOT LIKE '1%'", ".*phone.*", "符合 1[3-9] 号段"},
                {"TPL_ID_CARD", "证件号码合规模板", "GOV", "VALIDITY", "REGEX",
                        "SELECT COUNT(*) FROM ${table} WHERE ${column} IS NOT NULL AND LENGTH(${column})<>18", ".*id_card.*", "18 位居民身份证"},
                {"TPL_AMOUNT_RANGE", "金额区间模板", "FINANCE", "ACCURACY", "RANGE",
                        "SELECT COUNT(*) FROM ${table} WHERE ${column} < 0 OR ${column} > 100000", ".*amount.*", "金额 0~100000"},
                {"TPL_ENUM", "枚举取值模板", "*", "VALIDITY", "ENUM",
                        "SELECT COUNT(*) FROM ${table} WHERE ${column} NOT IN ('男','女')", ".*gender.*", "取值必须在枚举内"},
                {"TPL_TIMELINESS", "时效性模板", "INDUSTRY", "TIMELINESS", "TIMELINESS",
                        "SELECT COUNT(*) FROM ${table} WHERE ${column} < ${now-minus-minutes:60}", ".*_time", "采集/入库延迟≤60分钟"},
                {"TPL_CONSISTENCY", "跨表一致性模板", "FINANCE", "CONSISTENCY", "CONSISTENCY",
                        "SELECT CASE WHEN a<>b THEN 1 ELSE 0 END FROM (SELECT (SELECT COUNT(*) FROM ${table}) a, (SELECT COUNT(*) FROM ${ref_table}) b) t",
                        ".*_fact", "上下游行数一致"},
                {"TPL_PRICE_COST", "价格成本模板", "RETAIL", "ACCURACY", "RANGE",
                        "SELECT COUNT(*) FROM ${table} WHERE price < cost", ".*product.*", "售价不得低于成本"},
                {"TPL_FUTURE_DATE", "未来日期模板", "*", "VALIDITY", "RANGE",
                        "SELECT COUNT(*) FROM ${table} WHERE ${column} > CURRENT_DATE", ".*birthday|.*_date", "不允许未来日期"}
        };
        for (Object[] t : tpl) {
            db.insert("INSERT INTO qa_rule_template(code,name,industry,dimension,rule_type,expression,applies_to,description,status) "
                    + "VALUES(?,?,?,?,?,?,?,?,'ENABLED')", t[0], t[1], t[2], t[3], t[4], t[5], t[6], t[7]);
        }
    }

    private void rules() {
        rule("客户手机号非空", "ods_customer", "phone", "COMPLETENESS", "NOT_NULL",
                "SELECT COUNT(*) FROM ods_customer WHERE phone IS NULL OR TRIM(phone)=''", 0.16, 2, "MAJOR", "刘琴");
        rule("客户城市非空", "ods_customer", "city", "COMPLETENESS", "NOT_NULL",
                "SELECT COUNT(*) FROM ods_customer WHERE city IS NULL OR TRIM(city)=''", 0.24, 1, "MINOR", "刘琴");
        rule("已支付订单支付时间非空", "ods_order", "pay_time", "COMPLETENESS", "NOT_NULL",
                "SELECT COUNT(*) FROM ods_order WHERE status IN ('已支付','已发货','已完成') AND pay_time IS NULL", 0.08, 2, "CRITICAL", "张三");
        rule("政务人口证件号非空", "ods_gov_population", "id_card", "COMPLETENESS", "NOT_NULL",
                "SELECT COUNT(*) FROM ods_gov_population WHERE id_card IS NULL OR TRIM(id_card)=''", 0.16, 2, "CRITICAL", "陈顾问");
        rule("设备指标值非空", "ods_device_metric", "metric_value", "COMPLETENESS", "NOT_NULL",
                "SELECT COUNT(*) FROM ods_device_metric WHERE metric_value IS NULL", 0.24, 1, "MAJOR", "李工");
        rule("产品分类非空", "ods_product", "category", "COMPLETENESS", "NOT_NULL",
                "SELECT COUNT(*) FROM ods_product WHERE category IS NULL OR TRIM(category)=''", 0.16, 1, "MINOR", "王武");

        rule("客户证件号唯一", "ods_customer", "id_card", "UNIQUENESS", "UNIQUE",
                "SELECT COUNT(*) FROM (SELECT id_card FROM ods_customer WHERE id_card IS NOT NULL GROUP BY id_card HAVING COUNT(*)>1) t",
                0.04, 3, "CRITICAL", "刘琴");
        rule("支付交易号唯一", "ods_payment", "trade_no", "UNIQUENESS", "UNIQUE",
                "SELECT COUNT(*) FROM (SELECT trade_no FROM ods_payment WHERE trade_no IS NOT NULL GROUP BY trade_no HAVING COUNT(*)>1) t",
                0.04, 3, "CRITICAL", "赵安全");
        rule("设备采集时点唯一", "ods_device_metric", "collect_time", "UNIQUENESS", "UNIQUE",
                "SELECT COUNT(*) FROM (SELECT device_id,collect_time FROM ods_device_metric GROUP BY device_id,collect_time HAVING COUNT(*)>1) t",
                0.08, 1, "MINOR", "李工");
        rule("客户标识唯一", "ods_customer", "customer_id", "UNIQUENESS", "UNIQUE",
                "SELECT COUNT(*) FROM (SELECT customer_id FROM ods_customer GROUP BY customer_id HAVING COUNT(*)>1) t",
                0.05, 3, "CRITICAL", "刘琴");

        rule("订单金额必须为正", "ods_order", "amount", "ACCURACY", "RANGE",
                "SELECT COUNT(*) FROM ods_order WHERE amount <= 0", 0.04, 3, "CRITICAL", "王武");
        rule("订单金额业务上限", "ods_order", "amount", "ACCURACY", "RANGE",
                "SELECT COUNT(*) FROM ods_order WHERE amount > 100000", 0.04, 2, "MAJOR", "王武");
        rule("支付金额与订单一致", "ods_payment", "pay_amount", "ACCURACY", "CONSISTENCY",
                "SELECT COUNT(*) FROM ods_payment p JOIN ods_order o ON p.order_id=o.order_id WHERE ABS(p.pay_amount-o.amount) > 0.01",
                0.08, 2, "MAJOR", "赵安全");
        rule("产品售价不低于成本", "ods_product", "price", "ACCURACY", "RANGE",
                "SELECT COUNT(*) FROM ods_product WHERE price < cost", 0.08, 1, "MINOR", "王武");

        rule("客户邮箱格式合法", "ods_customer", "email", "VALIDITY", "REGEX",
                "SELECT COUNT(*) FROM ods_customer WHERE email IS NOT NULL AND email NOT LIKE '%_@_%._%'", 0.16, 1, "MINOR", "刘琴");
        rule("客户性别枚举合法", "ods_customer", "gender", "VALIDITY", "ENUM",
                "SELECT COUNT(*) FROM ods_customer WHERE gender NOT IN ('男','女')", 0.08, 1, "MAJOR", "刘琴");
        rule("政务证件号长度合法", "ods_gov_population", "id_card", "VALIDITY", "REGEX",
                "SELECT COUNT(*) FROM ods_gov_population WHERE id_card IS NOT NULL AND LENGTH(id_card)<>18", 0.16, 2, "CRITICAL", "陈顾问");
        rule("客户生日不得超当前日期", "ods_customer", "birthday", "VALIDITY", "RANGE",
                "SELECT COUNT(*) FROM ods_customer WHERE birthday > CURRENT_DATE", 0.04, 1, "MAJOR", "刘琴");
        rule("订单状态枚举合法", "ods_order", "status", "VALIDITY", "ENUM",
                "SELECT COUNT(*) FROM ods_order WHERE status NOT IN ('待支付','已支付','已发货','已完成','已取消','退款中')", 0.04, 1, "MAJOR", "王武");

        rule("设备指标量程合法", "ods_device_metric", "metric_value", "ACCURACY", "RANGE",
                "SELECT COUNT(*) FROM ods_device_metric WHERE metric_value < -100 OR metric_value > 200", 0.16, 2, "MAJOR", "李工");
        rule("设备采集时效", "ods_device_metric", "collect_time", "TIMELINESS", "TIMELINESS",
                "SELECT COUNT(*) FROM ods_device_metric WHERE collect_time < ${now-minus-minutes:60} AND collect_time > ${now-minus-minutes:1440}",
                0.4, 2, "MAJOR", "李工");
        rule("订单入库时效", "ods_order", "order_time", "TIMELINESS", "TIMELINESS",
                "SELECT CASE WHEN EXISTS(SELECT 1 FROM ods_order WHERE order_time > ${now-minus-minutes:120}) THEN 0 ELSE 1 END",
                0.4, 1, "MINOR", "张三");
        rule("订单事实与源表行数一致", "dwd_order_fact", "order_id", "CONSISTENCY", "CONSISTENCY",
                "SELECT CASE WHEN a<>b THEN 1 ELSE 0 END FROM (SELECT (SELECT COUNT(*) FROM dwd_order_fact) a, "
                        + "(SELECT COUNT(*) FROM ods_order WHERE status<>'已取消') b) t", 0.02, 2, "CRITICAL", "张三");
        rule("客户维度与源表行数一致", "dwd_customer_dim", "customer_id", "CONSISTENCY", "CONSISTENCY",
                "SELECT CASE WHEN a<>b THEN 1 ELSE 0 END FROM (SELECT (SELECT COUNT(*) FROM dwd_customer_dim) a, "
                        + "(SELECT COUNT(*) FROM ods_customer) b) t", 0.02, 2, "MAJOR", "张三");
    }

    private void rule(String name, String table, String column, String dim, String type, String expr,
                      double tol, double weight, String severity, String owner) {
        Object tableId = db.scalar("SELECT id FROM md_table WHERE name=?", table);
        db.insert("INSERT INTO qa_rule(tenant_id,code,name,table_id,table_name,column_name,dimension,rule_type,expression,tolerance,"
                        + "weight,severity,status,industry,owner,cycle,check_mode,auto_issue,create_time) "
                        + "VALUES(1,?,?,?,?,?,?,?,?,?,?,?,'ENABLED','GOV',?,'DAILY','FULL',1,?)",
                Ids.code("QR"), name, tableId, table, column, dim, type, expr,
                BigDecimal.valueOf(tol), BigDecimal.valueOf(weight), severity, owner, LocalDateTime.now().minusDays(20));
    }

    private long check(String code, String name, String desc, String cycle, String cron) {
        return db.insert("INSERT INTO qa_check(tenant_id,code,name,description,check_mode,schedule_type,cron,status,owner,"
                        + "next_run_time,create_time) VALUES(1,?,?,?,'FULL','CRON',?,'ENABLED','刘琴',?,?)",
                code, name, desc, cron, LocalDateTime.now().plusHours(6), LocalDateTime.now().minusDays(15));
    }

    private void link(long checkId, String table) {
        for (Map<String, Object> r : db.list("SELECT id FROM qa_rule WHERE table_name=? AND status='ENABLED'", table)) {
            db.insert("INSERT INTO qa_check_rule(check_id,rule_id) VALUES(?,?)", checkId, r.get("id"));
        }
    }

    /** 14 天评分历史（基于当前真实分数做可解释的波动，用于趋势图与月报对比）。 */
    private void history() {
        List<Map<String, Object>> tables = db.list("SELECT name, quality_score FROM md_table WHERE quality_score > 0");
        for (int d = 13; d >= 0; d--) {
            LocalDate day = LocalDate.now().minusDays(d);
            for (Map<String, Object> t : tables) {
                double base = ((BigDecimal) t.get("qualityScore")).doubleValue();
                double jitter = (rnd.nextDouble() - 0.5) * 3.2 + (d > 10 ? -1.5 : 0.4);
                double score = Math.min(100, Math.max(55, base + jitter));
                db.insert("INSERT INTO qa_score(tenant_id,object_type,object_id,object_name,stat_date,total_score,score_level,"
                                + "dimension_scores,passed_rules,total_rules) VALUES(1,'TABLE',?,?,?,?,?,?,?,?)",
                        db.scalar("SELECT id FROM md_table WHERE name=?", t.get("name")), t.get("name"), day,
                        BigDecimal.valueOf(score).setScale(2, java.math.RoundingMode.HALF_UP),
                        QualityEngine.levelOf(score),
                        JsonUtil.toJson(Map.of("COMPLETENESS", score + 1.2, "UNIQUENESS", score - 2.1,
                                "ACCURACY", score + 0.6, "VALIDITY", score - 1.4,
                                "CONSISTENCY", score + 2.0, "TIMELINESS", score - 3.1)),
                        4 + (int) (score / 12), 8);
            }
            int open = 18 - d / 2;
            int closed = 6 + d;
            db.insert("INSERT INTO qa_issue_metric_daily(stat_date,new_cnt,closed_cnt,open_cnt,overdue_cnt,closed_rate) "
                            + "VALUES(?,?,?,?,?,?)", day, 1 + rnd.nextInt(6), 2 + rnd.nextInt(5), Math.max(3, open),
                    rnd.nextInt(3), BigDecimal.valueOf(60 + rnd.nextDouble() * 25).setScale(2, java.math.RoundingMode.HALF_UP));
        }
    }

    /** 让问题单覆盖 DISCOVERED/DISPATCHED/PROCESSING/REVIEWING/CLOSED 全状态，闭环率指标可验证。 */
    private void enrichIssues() {
        List<Map<String, Object>> issues = db.list("SELECT * FROM qa_issue ORDER BY id");
        int i = 0;
        for (Map<String, Object> issue : issues) {
            long id = ((Number) issue.get("id")).longValue();
            String status;
            if (i % 7 == 0) status = "CLOSED";
            else if (i % 7 == 1) status = "PROCESSING";
            else if (i % 7 == 2) status = "DISPATCHED";
            else if (i % 7 == 3) status = "REVIEWING";
            else if (i % 7 == 4) status = "OVERDUE";
            else status = "DISCOVERED";
            LocalDateTime found = LocalDateTime.now().minusDays(6 - Math.min(6, i / 4)).minusHours(i % 12);
            LocalDateTime due = found.plusHours(72);
            LocalDateTime closed = "CLOSED".equals(status) ? found.plusHours(20 + rnd.nextInt(40)) : null;
            db.update("UPDATE qa_issue SET status=?, discover_time=?, due_time=?, close_time=?, handle_result=?, review_result=? WHERE id=?",
                    status, found, due, closed,
                    "CLOSED".equals(status) || "REVIEWING".equals(status) ? "已修复源系统校验逻辑并回刷历史数据" : null,
                    "CLOSED".equals(status) ? "复核通过" : null, id);
            db.insert("INSERT INTO qa_issue_flow(issue_id,from_status,to_status,operator,comment,create_time) VALUES(?,?,?,?,?,?)",
                    id, "DISCOVERED", status, "刘琴", "种子流程初始化", found.plusHours(1));
            i++;
        }
        Map<String, Object> reportCtx = new LinkedHashMap<>();
        double avg = 0;
        List<Map<String, Object>> tables = db.list("SELECT quality_score FROM md_table WHERE quality_score>0");
        for (Map<String, Object> t : tables) avg += ((BigDecimal) t.get("qualityScore")).doubleValue();
        avg = tables.isEmpty() ? 0 : avg / tables.size();
        reportCtx.put("tableCount", tables.size());
        reportCtx.put("avgScore", Math.round(avg * 100) / 100d);
        db.insert("INSERT INTO qa_report(tenant_id,code,title,report_type,scope,period,total_score,score_level,context,status,creator,create_time) "
                        + "VALUES(1,?,?,'QUALITY_TABLE_REPORT','全域核心表',?,?,?,'{}','GENERATED','刘琴',?)",
                Ids.code("QRP"), "数据质量月度报告（" + LocalDate.now().minusMonths(0).withDayOfMonth(1) + "）",
                LocalDate.now().toString().substring(0, 7), BigDecimal.valueOf(avg).setScale(2, java.math.RoundingMode.HALF_UP),
                QualityEngine.levelOf(avg), LocalDateTime.now().minusDays(1));
    }
}
