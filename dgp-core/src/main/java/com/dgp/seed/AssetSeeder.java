package com.dgp.seed;

import com.dgp.common.Db;
import com.dgp.common.JsonUtil;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/** 资产域种子：资产目录、分类、地图快照、运营指标、价值评估与访问日志。 */
@Component
public class AssetSeeder {

    private final Db db;
    private final Random rnd = new Random(19);

    public AssetSeeder(Db db) {
        this.db = db;
    }

    public void seed() {
        if (db.count("SELECT COUNT(*) FROM ast_catalog") > 0) return;
        categories();
        catalog();
        metrics();
        visits();
    }

    private void categories() {
        long base = db.insert("INSERT INTO ast_category(parent_id,code,name,sort_no) VALUES(0,'BASE','基础数据',1)");
        db.insert("INSERT INTO ast_category(parent_id,code,name,sort_no) VALUES(?,'CUST','客户主题',1)", base);
        db.insert("INSERT INTO ast_category(parent_id,code,name,sort_no) VALUES(?,'TRADE','交易主题',2)", base);
        db.insert("INSERT INTO ast_category(parent_id,code,name,sort_no) VALUES(?,'PROD','产品主题',3)", base);
        db.insert("INSERT INTO ast_category(parent_id,code,name,sort_no) VALUES(?,'DEVICE','设备主题',4)", base);
        db.insert("INSERT INTO ast_category(parent_id,code,name,sort_no) VALUES(?,'GOV','政务主题',5)", base);
        long ana = db.insert("INSERT INTO ast_category(parent_id,code,name,sort_no) VALUES(0,'ANALYTICS','分析数据',2)");
        db.insert("INSERT INTO ast_category(parent_id,code,name,sort_no) VALUES(?,'ADS','经营分析',1)", ana);
    }

    private void catalog() {
        Map<String, String> cat = new java.util.HashMap<>();
        cat.put("ods_customer", "客户主题");
        cat.put("dwd_customer_dim", "客户主题");
        cat.put("dws_customer_value", "客户主题");
        cat.put("ods_order", "交易主题");
        cat.put("dwd_order_fact", "交易主题");
        cat.put("ods_payment", "交易主题");
        cat.put("dws_order_summary", "交易主题");
        cat.put("dws_region_sales", "交易主题");
        cat.put("ods_product", "产品主题");
        cat.put("ods_device_metric", "设备主题");
        cat.put("dwd_device_fact", "设备主题");
        cat.put("ods_gov_population", "政务主题");
        cat.put("ads_sales_dashboard", "经营分析");
        for (Map<String, Object> t : db.list("SELECT * FROM md_table ORDER BY layer, name")) {
            String name = String.valueOf(t.get("name"));
            double quality = t.get("qualityScore") == null ? 80 : ((BigDecimal) t.get("qualityScore")).doubleValue();
            int access = 300 + rnd.nextInt(4200);
            int calls = 100 + rnd.nextInt(2600);
            double value = Math.min(100, quality * 0.45 + Math.min(30, access / 140.0) + Math.min(25, calls / 100.0));
            db.update("UPDATE md_table SET access_count=?, svc_calls=? WHERE id=?", access, calls, t.get("id"));
            long catalogId = db.insert("INSERT INTO ast_catalog(tenant_id,table_id,table_name,asset_name,asset_type,category,domain,"
                            + "business_desc,cover_scope,update_cycle,owner,publish_status,favorite_cnt,view_cnt,value_score,sensitivity_level,create_time) "
                            + "VALUES(1,?,?,?,'TABLE',?,?,?,?,?,?,?,?,?,?,?,?)",
                    t.get("id"), name, t.get("cnName"), cat.getOrDefault(name, "公共数据"), t.get("domain"),
                    t.get("bizDesc") == null ? t.get("description") : t.get("bizDesc"),
                    "全域 " + t.get("rowCount") + " 行", t.get("updateFreq"),
                    t.get("owner"), "PUBLISHED", rnd.nextInt(40), access, BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP),
                    t.get("sensitiveLevel"), t.get("createTime"));
            db.insert("INSERT INTO ast_value_score(table_name,score,dim_json,calc_time) VALUES(?,?,?,?)", name,
                    BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP),
                    JsonUtil.toJson(Map.of("quality", quality, "usage", access, "service", calls,
                            "coverage", 60 + rnd.nextInt(40))), LocalDateTime.now().minusHours(2));
            db.insert("INSERT INTO ast_visit_log(visit_time,username,asset_id,asset_name,action,ip) VALUES(?,?,?,?,'VIEW',?)",
                    LocalDateTime.now().minusHours(rnd.nextInt(72)), "wangwu", catalogId, name, "200.10.3.51");
        }
    }

    private void metrics() {
        long tables = db.count("SELECT COUNT(*) FROM md_table");
        long rows = db.count("SELECT COALESCE(SUM(row_count),0) FROM md_table");
        BigDecimal size = (BigDecimal) db.scalar("SELECT COALESCE(SUM(data_size_mb),0) FROM md_table");
        Map<String, Object> domainAgg = new LinkedHashMap<>();
        for (Map<String, Object> r : db.list("SELECT domain, COUNT(*) c, SUM(row_count) rc FROM md_table GROUP BY domain")) {
            domainAgg.put(String.valueOf(r.get("domain")), Map.of("tables", r.get("c"), "rows", r.get("rc")));
        }
        Map<String, Object> layerAgg = new LinkedHashMap<>();
        for (Map<String, Object> r : db.list("SELECT layer, COUNT(*) c, SUM(row_count) rc FROM md_table GROUP BY layer")) {
            layerAgg.put(String.valueOf(r.get("layer")), Map.of("tables", r.get("c"), "rows", r.get("rc")));
        }
        Map<String, Object> freqAgg = new LinkedHashMap<>();
        for (Map<String, Object> r : db.list("SELECT update_freq, COUNT(*) c FROM md_table GROUP BY update_freq")) {
            freqAgg.put(String.valueOf(r.get("updateFreq")), r.get("c"));
        }
        db.insert("INSERT INTO ast_map_snapshot(stat_date,total_tables,total_rows,total_size_mb,ds_cnt,domain_json,layer_json,freq_json) "
                        + "VALUES(?,?,?,?,?,?,?,?)", LocalDate.now(), tables, rows, size,
                db.count("SELECT COUNT(*) FROM md_datasource"), JsonUtil.toJson(domainAgg), JsonUtil.toJson(layerAgg),
                JsonUtil.toJson(freqAgg));
        for (int d = 29; d >= 0; d--) {
            long calls = db.count("SELECT COALESCE(SUM(call_cnt),0) FROM svc_call_stat WHERE stat_date=?", LocalDate.now().minusDays(d));
            db.insert("INSERT INTO ast_metric_daily(stat_date,table_cnt,storage_mb,access_cnt,svc_call_cnt,query_cnt,active_users,asset_value) "
                            + "VALUES(?,?,?,?,?,?,?,?)", LocalDate.now().minusDays(d),
                    tables - (d > 20 ? 1 : 0), size,
                    2400 + rnd.nextInt(2600) + (30 - d) * 40, calls,
                    5200 + rnd.nextInt(4200), 18 + rnd.nextInt(16),
                    BigDecimal.valueOf(1260 + rnd.nextInt(220)).setScale(2, RoundingMode.HALF_UP));
        }
    }

    private void visits() {
        List<Map<String, Object>> assets = db.list("SELECT id, asset_name FROM ast_catalog LIMIT 8");
        String[] users = {"wangwu", "liuqin", "zhangsan", "consultant", "admin"};
        for (int i = 0; i < 120; i++) {
            Map<String, Object> a = assets.get(rnd.nextInt(assets.size()));
            db.insert("INSERT INTO ast_visit_log(visit_time,username,asset_id,asset_name,action,ip) VALUES(?,?,?,?,?,?)",
                    LocalDateTime.now().minusHours(rnd.nextInt(240)), users[rnd.nextInt(users.length)],
                    a.get("id"), a.get("assetName"), rnd.nextBoolean() ? "VIEW" : "QUERY", "200.10.3." + (50 + rnd.nextInt(30)));
        }
    }
}
