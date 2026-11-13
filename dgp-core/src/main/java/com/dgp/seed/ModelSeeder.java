package com.dgp.seed;

import com.dgp.common.Db;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** 建模域示例数据：概念/逻辑/物理模型、实体、属性、关系、版本与发布单。 */
@Component
public class ModelSeeder {

    private final Db db;

    public ModelSeeder(Db db) {
        this.db = db;
    }

    public void seed() {
        if (db.count("SELECT COUNT(*) FROM mdl_model") > 0) return;

        long cust = model("MDL_CUST_MDM", "customer_mdm", "客户主数据模型", "PHYSICAL", "DWD", "RETAIL", "客户域",
                "PUBLISHED", "1.2.0", "张三", "面向客户主数据管理的物理模型，统一客户标识、联系方式与等级口径。");
        long e1 = entity(cust, "dwd_customer_dim", "dim_customer", "客户维度", "DIM", "客户域", "客户主数据维度表", 120, 120);
        attr(e1, "customer_key", "customer_key", "客户代理键", "STRING", "64", 1, 0, "DE_CUST_ID", "L1", "数仓代理键");
        attr(e1, "customer_id", "customer_id", "客户标识", "STRING", "32", 0, 0, "DE_CUST_ID", "L2", "源系统客户号");
        attr(e1, "cust_name", "cust_name", "客户名称", "STRING", "64", 0, 1, "DE_CUST_NAME", "L2", null);
        attr(e1, "gender", "gender", "性别", "STRING", "8", 0, 1, "DE_GENDER", "L1", null);
        attr(e1, "age", "age", "年龄", "INT", "3", 0, 1, null, "L1", "由出生日期推导");
        attr(e1, "phone", "phone", "手机号码", "STRING", "11", 0, 1, "DE_MOBILE", "L3", "需脱敏输出");
        attr(e1, "email", "email", "电子邮箱", "STRING", "128", 0, 1, "DE_EMAIL", "L3", null);
        attr(e1, "city", "city", "城市", "STRING", "32", 0, 1, "DE_CITY", "L1", null);
        attr(e1, "member_level", "member_level", "会员等级", "STRING", "16", 0, 1, null, "L1", "钻石/黄金/普通");
        long e2 = entity(cust, "dwd_customer_tag", "dim_customer_tag", "客户标签", "DIM", "客户域", "客户行为标签宽表", 480, 200);
        attr(e2, "customer_id", "customer_id", "客户标识", "STRING", "32", 1, 0, "DE_CUST_ID", "L2", null);
        attr(e2, "tag_code", "tag_code", "标签编码", "STRING", "64", 1, 0, null, "L1", null);
        attr(e2, "tag_value", "tag_value", "标签值", "STRING", "255", 0, 1, null, "L1", null);
        relation(cust, "dwd_customer_dim", "dwd_customer_tag", "1:N", "dwd_customer_dim.customer_id = dwd_customer_tag.customer_id");

        long trade = model("MDL_TRADE_LOGICAL", "trade_logical", "交易主题逻辑模型", "LOGICAL", "DWD", "RETAIL", "交易域",
                "REVIEWING", "1.1.0", "张三", "沉淀订单、支付、产品三条主线的逻辑实体，作为下游集市与指标的统一口径来源。");
        long t1 = entity(trade, "dwd_order_fact", "fact_order", "订单事实", "FACT", "交易域", "订单粒度事实表", 120, 320);
        attr(t1, "order_key", "order_key", "订单代理键", "STRING", "64", 1, 0, "DE_ORDER_ID", "L1", null);
        attr(t1, "order_id", "order_id", "订单编号", "STRING", "32", 0, 0, "DE_ORDER_ID", "L1", null);
        attr(t1, "customer_id", "customer_id", "客户标识", "STRING", "32", 0, 0, "DE_CUST_ID", "L2", null);
        attr(t1, "amount", "amount", "订单金额", "DECIMAL", "12,2", 0, 0, "DE_AMOUNT", "L2", "含税金额");
        attr(t1, "order_date", "order_date", "下单日期", "DATE", "10", 0, 1, "DE_ORDER_TIME", "L1", null);
        attr(t1, "status", "status", "订单状态", "STRING", "16", 0, 1, "DE_ORDER_STATUS", "L1", null);
        attr(t1, "is_refund", "is_refund", "是否退款", "INT", "1", 0, 1, "DE_ACTIVE_FLAG", "L1", null);
        long t2 = entity(trade, "dwd_payment_fact", "fact_payment", "支付事实", "FACT", "交易域", "支付粒度事实表", 120, 520);
        attr(t2, "pay_id", "pay_id", "支付流水号", "STRING", "32", 1, 0, "DE_TRADE_NO", "L2", null);
        attr(t2, "order_id", "order_id", "订单编号", "STRING", "32", 0, 0, "DE_ORDER_ID", "L1", null);
        attr(t2, "pay_amount", "pay_amount", "支付金额", "DECIMAL", "12,2", 0, 0, "DE_AMOUNT", "L2", null);
        attr(t2, "pay_time", "pay_time", "支付时间", "TIMESTAMP", "19", 0, 1, "DE_PAY_TIME", "L1", null);
        long t3 = entity(trade, "dim_product", "dim_product", "产品维度", "DIM", "产品域", "产品主数据维度", 480, 320);
        attr(t3, "product_id", "product_id", "产品标识", "STRING", "32", 1, 0, "DE_PRODUCT_ID", "L1", null);
        attr(t3, "product_name", "product_name", "产品名称", "STRING", "128", 0, 1, "DE_PRODUCT_NAME", "L1", null);
        attr(t3, "category", "category", "产品分类", "STRING", "32", 0, 1, "DE_CATEGORY", "L1", null);
        attr(t3, "price", "price", "单价", "DECIMAL", "12,2", 0, 0, "DE_PRICE", "L2", null);
        relation(trade, "dwd_order_fact", "dim_product", "N:1", "dwd_order_fact.product_id = dim_product.product_id");
        relation(trade, "dwd_order_fact", "dwd_payment_fact", "1:N", "dwd_order_fact.order_id = dwd_payment_fact.order_id");

        long dev = model("MDL_DEVICE_PHYSICAL", "device_physical", "工业设备时序模型", "PHYSICAL", "DWD", "INDUSTRY", "设备域",
                "PUBLISHED", "1.0.3", "李工", "覆盖 PLC/DCS 采集的温度、压力、振动、电流、流量指标，支撑设备健康与能耗分析。");
        long d1 = entity(dev, "dwd_device_fact", "fact_device", "设备运行事实", "FACT", "设备域", "设备指标粒度事实表", 120, 160);
        attr(d1, "device_key", "device_key", "设备代理键", "STRING", "64", 1, 0, "DE_DEVICE_ID", "L1", null);
        attr(d1, "device_id", "device_id", "设备标识", "STRING", "32", 0, 0, "DE_DEVICE_ID", "L1", null);
        attr(d1, "workshop", "workshop", "车间", "STRING", "64", 0, 1, null, "L1", null);
        attr(d1, "metric_name", "metric_name", "指标名称", "STRING", "32", 0, 0, null, "L1", null);
        attr(d1, "metric_value", "metric_value", "指标值", "DECIMAL", "12,3", 0, 1, "DE_METRIC_VALUE", "L1", "需量程校验");
        attr(d1, "collect_date", "collect_date", "采集日期", "DATE", "10", 0, 0, "DE_COLLECT_TIME", "L1", null);

        long gov = model("MDL_GOV_CONCEPT", "gov_concept", "政务人口概念模型", "CONCEPT", "ODS", "GOV", "政务域",
                "PUBLISHED", "1.0.0", "陈顾问", "面向公共数据目录的自然人、户籍、网格三个概念实体，作为政务数据标准化的起点。");
        long g1 = entity(gov, "自然人", "person", "自然人", "CONCEPT", "人口", "政务人口基础对象", 100, 120);
        attr(g1, "person_id", "person_id", "自然人标识", "STRING", "32", 1, 0, "DE_CUST_ID", "L2", null);
        attr(g1, "person_name", "person_name", "姓名", "STRING", "64", 0, 0, "DE_CUST_NAME", "L2", null);
        attr(g1, "id_card", "id_card", "证件号码", "STRING", "18", 0, 0, "DE_ID_CARD", "L4", "最高敏感级");
        attr(g1, "gender", "gender", "性别", "STRING", "8", 0, 1, "DE_GENDER", "L1", null);
        long g2 = entity(gov, "户籍", "household", "户籍", "CONCEPT", "人口", "户籍登记关系", 380, 120);
        attr(g2, "household_id", "household_id", "户籍标识", "STRING", "32", 1, 0, null, "L1", null);
        attr(g2, "person_id", "person_id", "自然人标识", "STRING", "32", 0, 0, "DE_CUST_ID", "L2", null);
        attr(g2, "city", "city", "所属城市", "STRING", "32", 0, 1, "DE_CITY", "L1", null);
        long g3 = entity(gov, "网格", "grid", "网格", "CONCEPT", "城市治理", "基层治理单元", 380, 300);
        attr(g3, "grid_code", "grid_code", "网格编码", "STRING", "32", 1, 0, null, "L1", null);
        attr(g3, "grid_name", "grid_name", "网格名称", "STRING", "128", 0, 1, null, "L1", null);

        long ads = model("MDL_SALES_ADS", "sales_ads", "经营分析集市模型", "PHYSICAL", "ADS", "RETAIL", "经营分析域",
                "PUBLISHED", "1.0.1", "王武", "面向管理驾驶舱的销售、区域、客户价值三类指标集市。");
        long a1 = entity(ads, "ads_sales_dashboard", "dws_sales", "销售驾驶舱", "FACT", "经营分析", "按日销售核心指标", 120, 120);
        attr(a1, "stat_date", "stat_date", "统计日期", "DATE", "10", 1, 0, "DE_STAT_DATE", "L1", null);
        attr(a1, "total_amount", "total_amount", "销售总额", "DECIMAL", "14,2", 0, 1, "DE_AMOUNT", "L2", null);
        attr(a1, "order_cnt", "order_cnt", "订单数", "INT", "10", 0, 1, null, "L1", null);
        attr(a1, "customer_cnt", "customer_cnt", "客户数", "INT", "10", 0, 1, null, "L1", null);
        attr(a1, "avg_order_amount", "avg_order_amount", "客单价", "DECIMAL", "12,2", 0, 1, "DE_AMOUNT", "L2", null);
        attr(a1, "refund_rate", "refund_rate", "退款率", "DECIMAL", "8,4", 0, 1, "DE_REFUND_RATE", "L1", null);
        long a2 = entity(ads, "dws_region_sales", "dws_region", "区域达成", "FACT", "经营分析", "区域销售目标达成", 480, 120);
        attr(a2, "stat_date", "stat_date", "统计日期", "DATE", "10", 1, 0, "DE_STAT_DATE", "L1", null);
        attr(a2, "region", "region", "区域", "STRING", "32", 1, 0, "DE_REGION", "L1", null);
        attr(a2, "sales_amount", "sales_amount", "销售额", "DECIMAL", "14,2", 0, 1, "DE_AMOUNT", "L2", null);
        attr(a2, "completion_rate", "completion_rate", "完成率", "DECIMAL", "8,4", 0, 1, null, "L1", null);

        version(cust, "1.1.0", "初始发布：客户维度 + 客户标签", "王武");
        version(cust, "1.2.0", "新增 member_level 属性，phone 标注 L3 及掩码算法", "张三");
        version(trade, "1.0.0", "初始逻辑实体：订单/支付/产品", "张三");
        version(trade, "1.1.0", "订单事实新增 is_refund，支付事实补充 pay_time 标准映射", "张三");
        version(dev, "1.0.3", "指标值精度提升到三位小数，补充量程校验规则", "李工");
        version(gov, "1.0.0", "概念模型初版", "陈顾问");
        version(ads, "1.0.1", "补充退款率指标口径", "王武");

        db.insert("INSERT INTO mdl_publish_flow(model_id,model_name,version,applicant,auditor,status,comment,create_time) "
                + "VALUES(?,?,?,?,'张治平','PENDING','交易模型口径变更需评审',?)",
                trade, "交易主题逻辑模型", "1.1.0", "张三", LocalDateTime.now().minusHours(5));
        db.insert("INSERT INTO mdl_publish_flow(model_id,model_name,version,applicant,auditor,status,comment,create_time,finish_time) "
                + "VALUES(?,?,?,?,'张治平','APPROVED','评审通过',?,?)",
                cust, "客户主数据模型", "1.2.0", "张三", LocalDateTime.now().minusDays(3), LocalDateTime.now().minusDays(2));
    }

    private long model(String code, String name, String cn, String type, String layer, String industry, String domain,
                       String status, String version, String owner, String desc) {
        LocalDateTime now = LocalDateTime.now();
        return db.insert("INSERT INTO mdl_model(tenant_id,code,name,cn_name,model_type,layer,industry,domain,status,version,owner,"
                        + "description,create_time,update_time,publish_time) VALUES(1,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                code, name, cn, type, layer, industry, domain, status, version, owner, desc, now,
                now, "PUBLISHED".equals(status) ? now.minusDays(2) : null);
    }

    private long entity(long modelId, String code, String name, String cn, String type, String domain, String desc, double x, double y) {
        return db.insert("INSERT INTO mdl_entity(model_id,code,name,cn_name,entity_type,subject_domain,description,pos_x,pos_y) "
                + "VALUES(?,?,?,?,?,?,?,?,?)", modelId, code, name, cn, type, domain, desc, x, y);
    }

    private void attr(long entityId, String code, String name, String cn, String type, String len, int pk, int nullable,
                      String stdCode, String level, String desc) {
        Object max = db.scalar("SELECT COALESCE(MAX(attr_order),0)+1 FROM mdl_attribute WHERE entity_id=?", entityId);
        db.insert("INSERT INTO mdl_attribute(entity_id,code,name,cn_name,data_type,data_len,is_pk,nullable,std_element_code,"
                        + "security_level,description,attr_order) VALUES(?,?,?,?,?,?,?,?,?,?,?,?)",
                entityId, code, name, cn, type, len, pk, nullable, stdCode, level, desc,
                max == null ? 1 : ((Number) max).intValue());
    }

    private void relation(long modelId, String src, String dst, String type, String cond) {
        db.insert("INSERT INTO mdl_relation(model_id,source_entity,target_entity,relation_type,join_condition) VALUES(?,?,?,?,?)",
                modelId, src, dst, type, cond);
    }

    private void version(long modelId, String version, String note, String operator) {
        Map<String, Object> m = db.one("SELECT * FROM mdl_model WHERE id=?", modelId);
        List<Map<String, Object>> ents = db.list("SELECT * FROM mdl_entity WHERE model_id=?", modelId);
        StringBuilder sb = new StringBuilder("{\"model\":\"").append(m.get("cnName")).append("\",\"entities\":[");
        for (int i = 0; i < ents.size(); i++) {
            if (i > 0) sb.append(",");
            Map<String, Object> e = ents.get(i);
            sb.append("{\"code\":\"").append(e.get("code")).append("\",\"attributes\":")
                    .append(db.count("SELECT COUNT(*) FROM mdl_attribute WHERE entity_id=?", e.get("id"))).append("}");
        }
        sb.append("]}");
        db.insert("INSERT INTO mdl_version(model_id,version,snapshot,change_note,operator,create_time) VALUES(?,?,?,?,?,?)",
                modelId, version, sb.toString(), note, operator, LocalDateTime.now().minusDays(30 - (int) (modelId % 20)));
    }
}
