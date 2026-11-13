package com.dgp.seed;

import com.dgp.common.Db;
import com.dgp.common.Ids;
import com.dgp.common.JsonUtil;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/** 服务域种子：应用、开放 API、调用日志与统计、配额、黑白名单、编排。 */
@Component
public class ServiceSeeder {

    private final Db db;
    private final Random rnd = new Random(97);

    public ServiceSeeder(Db db) {
        this.db = db;
    }

    public void seed() {
        if (db.count("SELECT COUNT(*) FROM svc_api") > 0) return;
        apps();
        apis();
        calls();
        orchestration();
    }

    private void apps() {
        Object[][] apps = {
                {"APP-OPS", "经营分析平台", "运营分析部", "APP-OPS-SECRET-2026"},
                {"APP-GOV", "政务数据共享门户", "政务数据组", "APP-GOV-SECRET-2026"},
                {"APP-FIN", "财务对账系统", "财务共享中心", "APP-FIN-SECRET-2026"}
        };
        for (Object[] a : apps) {
            db.insert("INSERT INTO svc_app(tenant_id,app_code,app_name,app_secret,owner,status,quota_day,create_time) "
                    + "VALUES(1,?,?,?,?,'ENABLED',20000,?)", a[0], a[1], a[3], a[2], LocalDateTime.now().minusDays(60));
            db.insert("INSERT INTO svc_quota(tenant_id,app_code,quota_day,used_today,qps_limit) VALUES(1,?,?,?,?)",
                    a[0], 20000, 1200 + rnd.nextInt(6000), 50);
        }
        db.insert("INSERT INTO svc_blacklist(ip,reason,status,create_time) VALUES('203.0.113.77','多次尝试越权访问客户敏感字段','ENABLED',?)",
                LocalDateTime.now().minusDays(2));
    }

    private void apis() {
        api("API-CUST-PROFILE", "客户画像查询", "/customer/profile", "GET",
                "SELECT customer_id, cust_name, gender, city, phone, email, member_level, reg_time FROM ods_customer WHERE customer_id='${customerId}'",
                "[{\"name\":\"customerId\",\"type\":\"string\",\"required\":true,\"desc\":\"客户标识\"}]",
                200, "客户服务", "PUBLISHED", "APP-OPS", "按客户标识返回画像信息，phone/email 按脱敏策略输出");
        api("API-SALES-REGION", "区域销售达成查询", "/sales/region", "GET",
                "SELECT stat_date, region, sales_amount, target_amount, completion_rate FROM dws_region_sales WHERE stat_date='${statDate}' ORDER BY region",
                "[{\"name\":\"statDate\",\"type\":\"date\",\"required\":true,\"desc\":\"统计日期 yyyy-MM-dd\"}]",
                500, "经营分析", "PUBLISHED", "APP-OPS", "返回指定日期的区域销售与目标达成");
        api("API-ORDER-LIST", "订单列表查询", "/order/list", "GET",
                "SELECT order_id, customer_id, product_name, amount, status, region, order_time FROM ods_order ORDER BY order_time DESC LIMIT ${limit}",
                "[{\"name\":\"limit\",\"type\":\"int\",\"required\":false,\"desc\":\"返回条数，默认50，上限500\"}]",
                100, "交易查询", "PUBLISHED", "APP-OPS", "按时间倒序返回订单明细，供对账与运营使用");
        api("API-DEVICE-METRIC", "设备时序指标查询", "/device/metrics", "GET",
                "SELECT device_id, workshop, metric_name, metric_value, unit, collect_time FROM ods_device_metric "
                        + "WHERE device_id='${deviceId}' AND metric_name='${metric}' ORDER BY collect_time DESC LIMIT 100",
                "[{\"name\":\"deviceId\",\"type\":\"string\",\"required\":true,\"desc\":\"设备编码\"},"
                        + "{\"name\":\"metric\",\"type\":\"string\",\"required\":true,\"desc\":\"指标名称\"}]",
                300, "工业互联", "PUBLISHED", "APP-FIN", "设备实时指标，供能耗分析与预测性维护");
        api("API-QUALITY-TOP", "数据质量排名", "/quality/top", "GET",
                "SELECT t.name AS table_name, t.cn_name, t.quality_score, t.sensitive_level FROM md_table t ORDER BY t.quality_score DESC LIMIT ${limit}",
                "[{\"name\":\"limit\",\"type\":\"int\",\"required\":false,\"desc\":\"返回条数\"}]",
                60, "质量运营", "PUBLISHED", "APP-GOV", "按质量评分返回资产排名，供治理运营看板");
        api("API-GOV-POP", "政务人口核查", "/population/check", "GET",
                "SELECT COUNT(*) AS total, SUM(CASE WHEN id_card IS NULL THEN 1 ELSE 0 END) AS missing_id_card, "
                        + "SUM(CASE WHEN LENGTH(id_card)<>18 THEN 1 ELSE 0 END) AS invalid_id_card FROM ods_gov_population",
                "[]", 30, "政务共享", "PUBLISHED", "APP-GOV", "人口基础库证件完备性核查结果");
        api("API-CUST-SCORE", "客户价值分层查询", "/customer/value-level", "POST",
                "SELECT customer_id, total_amount, order_cnt, value_level FROM dws_customer_value WHERE value_level='${level}' ORDER BY total_amount DESC LIMIT 200",
                "[{\"name\":\"level\",\"type\":\"string\",\"required\":true,\"desc\":\"高价值/中价值/普通\"}]",
                120, "客户运营", "PENDING", "APP-OPS", "按价值分层拉取客户清单，待安全评审");
        api("API-RAW-SQL", "自助取数（草稿）", "/adhoc/query", "POST",
                "SELECT * FROM ${table} LIMIT 100",
                "[{\"name\":\"table\",\"type\":\"string\",\"required\":true,\"desc\":\"表名\"}]",
                10, "自助分析", "DRAFT", "APP-OPS", "自助取数能力，需接入沙箱与字段权限后发布");
    }

    private void api(String code, String name, String path, String method, String sql, String params, int rate,
                     String category, String status, String app, String desc) {
        long id = db.insert("INSERT INTO svc_api(tenant_id,code,name,api_path,method,version,status,category,sql_text,params,owner,"
                        + "app_code,auth_type,rate_limit,timeout_ms,mask_enabled,description,create_time,publish_time,industry) "
                        + "VALUES(1,?,?,?,?,'v1',?,?,?,?,'数据服务组',?,'AKSK',?,3000,1,?,?,?,'GOV')",
                code, name, path, method, status, category, sql, params, app, rate, desc,
                LocalDateTime.now().minusDays(20), "PUBLISHED".equals(status) ? LocalDateTime.now().minusDays(15) : null);
        db.insert("INSERT INTO svc_api_version(api_id,version,sql_text,params,change_note,operator,create_time) "
                + "VALUES(?, 'v1', ?, ?, '初始版本', '数据服务组', ?)", id, sql, params, LocalDateTime.now().minusDays(20));
    }

    private void calls() {
        List<Map<String, Object>> apis = db.list("SELECT * FROM svc_api WHERE status='PUBLISHED'");
        String[] appCodes = {"APP-OPS", "APP-GOV", "APP-FIN"};
        String[] ips = {"200.10.3.51", "200.10.3.52", "200.10.20.31", "10.20.5.18", "203.0.113.77"};
        for (int d = 29; d >= 0; d--) {
            LocalDate day = LocalDate.now().minusDays(d);
            for (Map<String, Object> api : apis) {
                int weight = 1 + (int) (((Number) api.get("id")).longValue() % 5);
                int base = (110 + rnd.nextInt(260)) * weight + (d < 7 ? 120 : 0);
                long sumMs = 0;
                long success = 0;
                int samples = 4 + weight * 2;
                for (int i = 0; i < samples; i++) {
                    int code = rnd.nextInt(40) == 0 ? 500 : 200;
                    long ms = 12 + rnd.nextInt(180);
                    sumMs += ms;
                    if (code == 200) success++;
                    LocalDateTime ts = day.atTime(8 + rnd.nextInt(12), rnd.nextInt(60), rnd.nextInt(60));
                    db.insert("INSERT INTO svc_call_log(api_id,api_code,api_name,call_time,status_code,duration_ms,app_code,ip,row_cnt,error_msg,trace_id) "
                                    + "VALUES(?,?,?,?,?,?,?,?,?,?,?)",
                            api.get("id"), api.get("code"), api.get("name"), ts, code, ms,
                            appCodes[rnd.nextInt(appCodes.length)], ips[rnd.nextInt(ips.length)],
                            code == 200 ? 1 + rnd.nextInt(100) : 0,
                            code == 200 ? null : "执行超时：query exceeded 3000ms", "sim-" + Ids.next());
                }
                long avg = sumMs / samples;
                db.insert("INSERT INTO svc_call_stat(stat_date,api_id,call_cnt,success_cnt,fail_cnt,avg_ms,p99_ms) VALUES(?,?,?,?,?,?,?)",
                        day, api.get("id"), base, base - (samples - success), samples - success,
                        BigDecimal.valueOf(avg), BigDecimal.valueOf(avg * 2.4));
            }
        }
        for (Map<String, Object> api : apis) {
            Map<String, Object> agg = db.one("SELECT COUNT(*) c, AVG(duration_ms) a FROM svc_call_log WHERE api_id=?", api.get("id"));
            db.update("UPDATE svc_api SET call_count=?, avg_ms=? WHERE id=?",
                    agg.get("c"), BigDecimal.valueOf(((Number) agg.get("a")).doubleValue()).setScale(2, RoundingMode.HALF_UP),
                    api.get("id"));
        }
    }

    private void orchestration() {
        List<Map<String, Object>> apis = db.list("SELECT id,code,name FROM svc_api WHERE status='PUBLISHED' LIMIT 3");
        List<Map<String, Object>> steps = new ArrayList<>();
        int i = 1;
        for (Map<String, Object> a : apis) {
            Map<String, Object> s = new LinkedHashMap<>();
            s.put("step", i++);
            s.put("apiCode", a.get("code"));
            s.put("apiName", a.get("name"));
            s.put("outputKey", "step" + (i - 1));
            steps.add(s);
        }
        db.insert("INSERT INTO svc_orchestration(tenant_id,code,name,status,description,steps,owner,call_cnt,create_time) "
                        + "VALUES(1,?,?,'PUBLISHED','一次调用聚合客户画像、订单列表与质量排名，形成客户 360 视图',?,'数据服务组',864,?)",
                Ids.code("ORCH"), "客户360视图服务", JsonUtil.toJson(steps), LocalDateTime.now().minusDays(9));
    }
}
