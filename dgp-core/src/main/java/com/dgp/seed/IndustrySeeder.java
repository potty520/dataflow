package com.dgp.seed;

import com.dgp.common.Db;
import com.dgp.common.JsonUtil;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 行业包种子（Req-7.6）：按 manifest.json 规范构建 5 个行业包，
 * 每个包包含模型模板 / 质量规则库 / 术语表 / 血缘规则 / 报表模板 / 敏感规则 / 数据元。
 */
@Component
public class IndustrySeeder {

    private final Db db;

    public IndustrySeeder(Db db) {
        this.db = db;
    }

    public void seed() {
        if (db.count("SELECT COUNT(*) FROM ind_package") > 0) return;

        pkg("TELECOM_BSS", "通信行业 BSS 域治理包", "TELECOM", "通信", "1.2.0",
                "包含 B域/O域/M域 元数据分类模板、用户一致性/网络指标时效性质量规则、术语表与月报模板",
                new String[][]{
                        {"DES", "客户标识", "STRING", "32", "true", "DE_CUST_ID", "L2"},
                        {"DES", "用户号码", "STRING", "15", "false", "DE_MOBILE", "L3"},
                        {"DES", "套餐编码", "STRING", "32", "false", null, "L1"},
                        {"DES", "ARPU", "DECIMAL", "12,2", "false", "DE_AMOUNT", "L2"}
                },
                new String[][]{
                        {"用户数据一致性", "CONSISTENCY", "SELECT COUNT(*) FROM ${table} WHERE cust_id IS NULL", ".*cust.*", "0.01", "CRITICAL"},
                        {"网络指标时效性", "TIMELINESS", "SELECT COUNT(*) FROM ${table} WHERE collect_time < ${now-minus-minutes:15}", ".*metric.*", "0.02", "MAJOR"},
                        {"经营分析准确性", "ACCURACY", "SELECT COUNT(*) FROM ${table} WHERE amount < 0", ".*amount.*", "0.005", "MAJOR"}
                },
                "TEL_MSISDN,移动用户号码,MSISDN,移动用户号码,(?i).*(msisdn|phone|mobile).*,DE_MOBILE,\"手机号;号码\",L3,行标\n"
                        + "TEL_ARPU,每用户平均收入,ARPU,统计周期内总收入除以用户数,(?i).*arpu.*,DE_AMOUNT,\"单用户收入\",L2,行标\n"
                        + "TEL_IMSI,国际移动用户识别码,IMSI,用户身份识别码,(?i).*imsi.*,DE_CUST_ID,用户标识,L4,国标",
                "{\"rules\":[{\"name\":\"B域到DWD汇聚\",\"strategy\":\"按套餐编码补齐域标识\",\"level\":\"TABLE\"}]}",
                "客户数据一致性,网络指标时效性,经营分析准确性");

        pkg("FINANCE_REG", "金融监管报送治理包", "FINANCE", "金融", "1.1.0",
                "内置监管报送校验规则库，支持一表通常态化报送的自动取数、校验、溯源；覆盖客户/账户/交易主题",
                new String[][]{
                        {"ACCT", "账户标识", "STRING", "32", "true", "DE_CUST_ID", "L3"},
                        {"ACCT", "账户余额", "DECIMAL", "18,2", "false", "DE_AMOUNT", "L3"},
                        {"CUST", "客户风险等级", "STRING", "8", "false", null, "L3"},
                        {"TXN", "交易流水号", "STRING", "64", "true", "DE_TRADE_NO", "L2"}
                },
                new String[][]{
                        {"监管报送数据准确性", "ACCURACY", "SELECT COUNT(*) FROM ${table} WHERE balance < 0", ".*acct.*", "0.001", "CRITICAL"},
                        {"客户信息完整性", "COMPLETENESS", "SELECT COUNT(*) FROM ${table} WHERE risk_level IS NULL", ".*cust.*", "0.005", "CRITICAL"},
                        {"交易数据一致性", "CONSISTENCY", "SELECT COUNT(*) FROM ${table} a WHERE NOT EXISTS(SELECT 1 FROM ${ref_table} b WHERE b.txn_no=a.txn_no)", ".*txn.*", "0.002", "CRITICAL"}
                },
                "FIN_ACCT,账户,Account,客户在金融机构开立的资金账户,(?i).*(acct|account).*,DE_CUST_ID,\"账号;户头\",L3,行标\n"
                        + "FIN_TXN,交易流水,Transaction,账户发生的资金变动记录,(?i).*(txn|trade|trans).*,DE_TRADE_NO,流水;交易,L3,行标\n"
                        + "FIN_REPORT,监管报送,Regulatory Report,按监管要求定期报送的数据集,(?i).*(report|reg).*,DE_STAT_DATE,一表通;报送,L2,监管",
                "{\"rules\":[{\"name\":\"报送口径映射\",\"strategy\":\"按监管字段字典映射\",\"level\":\"COLUMN\"}]}",
                "监管报送数据准确性,客户信息完整性,交易数据一致性");

        pkg("INDUSTRY_MFG", "工业制造设备治理包", "INDUSTRY", "工业与制造", "1.3.0",
                "工业协议接入（PLC/DCS/SCADA/BMS）元数据模板，设备主数据与能源介质模型，时序数据质量规则",
                new String[][]{
                        {"DEV", "设备编码", "STRING", "32", "true", "DE_DEVICE_ID", "L1"},
                        {"DEV", "设备名称", "STRING", "128", "false", null, "L1"},
                        {"METRIC", "指标值", "DECIMAL", "12,3", "false", "DE_METRIC_VALUE", "L1"},
                        {"ENERGY", "能源介质类型", "STRING", "16", "false", null, "L1"}
                },
                new String[][]{
                        {"能耗数据完整性", "COMPLETENESS", "SELECT COUNT(*) FROM ${table} WHERE energy_type IS NULL", ".*energy.*", "0.01", "MAJOR"},
                        {"设备运行参数有效性", "VALIDITY", "SELECT COUNT(*) FROM ${table} WHERE metric_value < -100 OR metric_value > 200", ".*metric.*", "0.02", "MAJOR"},
                        {"产品质量指标一致性", "CONSISTENCY", "SELECT COUNT(*) FROM ${table} WHERE quality_flag NOT IN ('合格','不合格')", ".*quality.*", "0.005", "MAJOR"}
                },
                "IND_DEVICE,设备,Device,产线上可采集运行参数的生产装备,(?i).*(device|equip|plc).*,DE_DEVICE_ID,机组;装置,L1,行标\n"
                        + "IND_ENERGY,能源介质,Energy Medium,电、气、水、热等能源载体,(?i).*(energy|power|gas|water).*,DE_METRIC_VALUE,能耗介质,L1,国标\n"
                        + "IND_OEE,设备综合效率,OEE,时间开动率×性能开动率×合格品率,(?i).*oee.*,DE_METRIC_VALUE,综合效率,L1,行标",
                "{\"rules\":[{\"name\":\"设备-工序-产品血缘\",\"strategy\":\"按产线工单串联设备指标与成品批次\",\"level\":\"PROCESS\"}]}",
                "能耗数据完整性,设备运行参数有效性,产品质量指标一致性");

        pkg("GOV_PUBLIC", "政务公共数据治理包", "GOV", "政府与公共事业", "1.0.2",
                "公共数据目录主题域（人口/法人/空间地理/经济），政务数据标准模板与国标映射，跨部门一致性规则",
                new String[][]{
                        {"POP", "自然人标识", "STRING", "32", "true", "DE_CUST_ID", "L2"},
                        {"POP", "证件号码", "STRING", "18", "false", "DE_ID_CARD", "L4"},
                        {"POP", "行政区划", "STRING", "12", "false", "DE_CITY", "L1"},
                        {"LEGAL", "统一社会信用代码", "STRING", "18", "true", "DE_CUST_ID", "L2"}
                },
                new String[][]{
                        {"政务数据完整性", "COMPLETENESS", "SELECT COUNT(*) FROM ${table} WHERE id_card IS NULL", ".*pop.*", "0.01", "CRITICAL"},
                        {"跨部门数据一致性", "CONSISTENCY", "SELECT COUNT(*) FROM ${table} a WHERE NOT EXISTS(SELECT 1 FROM ${ref_table} b WHERE b.id_card=a.id_card)", ".*pop.*", "0.005", "CRITICAL"},
                        {"数据更新时效性", "TIMELINESS", "SELECT COUNT(*) FROM ${table} WHERE update_time < ${now-minus-minutes:1440}", ".*pop.*", "0.05", "MAJOR"}
                },
                "GOV_POP,常住人口,Resident Population,在统计区域内居住满六个月以上的人口,(?i).*(person|pop|cust).*,DE_CUST_NAME,\"户籍人口;实有人口\",L2,国标\n"
                        + "GOV_GRID,网格,Grid,基层治理的最小管理单元,(?i).*(grid|area).*,DE_CITY,管区;片区,L1,地标\n"
                        + "GOV_LICENSE,统一社会信用代码,USCC,法人和其他组织的唯一身份标识,(?i).*(credit|license|uscc).*,DE_CUST_ID,信用代码,L2,国标",
                "{\"rules\":[{\"name\":\"人口-法人-空间贯通\",\"strategy\":\"按行政区划与网格编码关联\",\"level\":\"TABLE\"}]}",
                "政务数据完整性,跨部门数据一致性,数据更新时效性");

        pkg("RETAIL_SUPPLY", "零售供应链治理包", "RETAIL", "零售与消费品", "1.0.0",
                "供应链数据模型（采购/生产/库存/销售），能源与碳排数据模型，库存一致性与时效规则",
                new String[][]{
                        {"SKU", "单品编码", "STRING", "32", "true", "DE_PRODUCT_ID", "L1"},
                        {"STOCK", "库存数量", "INT", "10", "false", null, "L1"},
                        {"STORE", "门店编码", "STRING", "32", "true", null, "L1"},
                        {"CARBON", "碳排放量", "DECIMAL", "12,3", "false", "DE_METRIC_VALUE", "L1"}
                },
                new String[][]{
                        {"库存数据一致性", "CONSISTENCY", "SELECT COUNT(*) FROM ${table} WHERE stock_qty < 0", ".*stock.*", "0.005", "MAJOR"},
                        {"能耗数据准确性", "ACCURACY", "SELECT COUNT(*) FROM ${table} WHERE carbon < 0", ".*carbon.*", "0.01", "MINOR"},
                        {"供应链时效性", "TIMELINESS", "SELECT COUNT(*) FROM ${table} WHERE update_time < ${now-minus-minutes:720}", ".*supply.*", "0.05", "MAJOR"}
                },
                "RET_SKU,单品,SKU,库存管理的最小产品单元,(?i).*(sku|product|item).*,DE_PRODUCT_ID,货品;商品,L1,行标\n"
                        + "RET_STORE,门店,Store,开展零售经营的实体或虚拟门店,(?i).*(store|shop).*,DE_CITY,网点;店铺,L1,行标\n"
                        + "RET_GMV,商品交易总额,GMV,统计周期内成交订单金额合计,(?i).*(gmv|amount|sales).*,DE_AMOUNT,成交额,L2,行标",
                "{\"rules\":[{\"name\":\"总部-门店-单品血缘\",\"strategy\":\"按门店编码与单品编码聚合\",\"level\":\"TABLE\"}]}",
                "库存数据一致性,能耗数据准确性,供应链时效性");
    }

    private void pkg(String code, String name, String industry, String industryName, String version, String desc,
                     String[][] entities, String[][] rules, String termsCsv, String lineage, String reportSections) {
        List<Map<String, Object>> contents = new ArrayList<>();
        List<Object[]> items = new ArrayList<>();

        Map<String, Object> modelContent = new LinkedHashMap<>();
        modelContent.put("schemaVersion", "1.0");
        modelContent.put("modelCode", code + "_MODEL");
        modelContent.put("modelName", name.replace("治理包", "") + "模型模板");
        modelContent.put("modelType", "LOGICAL");
        modelContent.put("layer", "DWD");
        List<Map<String, Object>> ents = new ArrayList<>();
        for (String[] e : entities) {
            Map<String, Object> ent = new LinkedHashMap<>();
            ent.put("code", code.toLowerCase() + "_" + e[0].toLowerCase());
            ent.put("name", e[1]);
            ent.put("entityType", "DIM");
            ent.put("attributes", List.of(Map.of(
                    "code", e[0].toLowerCase() + "_id", "name", e[1], "dataType", e[2], "length", e[3],
                    "isPk", Boolean.parseBoolean(e[4]), "stdElementCode", e[5] == null ? "" : e[5],
                    "securityLevel", e[6])));
            ents.add(ent);
        }
        modelContent.put("entities", ents);
        modelContent.put("metadataTags", Map.of("industry", industry, "source", "industry-package"));
        items.add(item("MODEL_TEMPLATE", code + "_MODEL", modelContent, "1.2.0"));

        List<Map<String, Object>> ruleList = new ArrayList<>();
        for (String[] r : rules) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("code", code + "_" + r[0].hashCode());
            m.put("name", r[0]);
            m.put("dimension", r[1]);
            m.put("ruleType", r[1]);
            m.put("expression", r[2]);
            m.put("appliesTo", r[3]);
            m.put("tolerance", Double.parseDouble(r[4]));
            m.put("severity", r[5]);
            ruleList.add(m);
        }
        items.add(item("RULE_TEMPLATE", code + "_RULES", Map.of("schemaVersion", "1.1", "rules", ruleList), version));

        items.add(item("TERMINOLOGY", code + "_TERMS", Map.of("schemaVersion", "1.0", "csv", termsCsv), "1.0.3"));
        items.add(item("LINEAGE_RULE", code + "_LINEAGE", JsonUtil.parse(lineage), "1.0.0"));
        items.add(item("REPORT_TEMPLATE", code + "_REPORT",
                Map.of("schemaVersion", "1.0", "sections", List.of(reportSections.split(",")), "engine", "HTML"),
                "1.0.0"));
        items.add(item("SENSITIVE_RULE", code + "_SENSITIVE",
                Map.of("rules", List.of(
                        Map.of("name", "身份证号", "matchType", "CONTENT", "pattern", "^\\d{17}[\\dXx]$", "level", "L4", "algorithm", "MASK"),
                        Map.of("name", "手机号", "matchType", "NAME", "pattern", "(?i).*(phone|mobile).*", "level", "L3", "algorithm", "MASK"))),
                "1.0.1"));
        items.add(item("DATA_ELEMENT", code + "_ELEMENTS",
                Map.of("elements", List.of(
                        Map.of("code", "DE_CUST_ID", "name", "客户标识", "dataType", "STRING", "length", 32),
                        Map.of("code", "DE_AMOUNT", "name", "金额", "dataType", "DECIMAL", "length", "12,2"))),
                "1.2.0"));

        Map<String, Object> manifest = new LinkedHashMap<>();
        manifest.put("packageCode", code);
        manifest.put("packageName", name);
        manifest.put("industryCode", industry);
        manifest.put("industryName", industryName);
        manifest.put("version", version);
        manifest.put("minPlatformVersion", "1.0.0");
        manifest.put("maxPlatformVersion", "2.0.0");
        manifest.put("dslVersion", "1.0");
        manifest.put("vendor", "DGP Industry Team");
        manifest.put("createdAt", LocalDateTime.now().minusDays(20).toString());
        manifest.put("description", desc);
        List<Map<String, Object>> cs = new ArrayList<>();
        for (Object[] it : items) {
            cs.add(Map.of("type", it[0], "code", it[1], "version", it[2],
                    "checksum", "sha256:" + Integer.toHexString(String.valueOf(it[1]).hashCode())));
        }
        manifest.put("contents", cs);
        manifest.put("enableScope", Map.of("supportTenantLevel", List.of(1, 2, 3), "defaultEnabled", false));

        long pkgId = db.insert("INSERT INTO ind_package(tenant_id,package_code,package_name,industry_code,industry_name,version,status,"
                        + "min_platform_version,max_platform_version,description,vendor,manifest,item_cnt,applied,create_time) "
                        + "VALUES(1,?,?,?,?,?,'INSTALLED','1.0.0','2.0.0',?,?,?,?,?,?)",
                code, name, industry, industryName, version, desc, "DGP Industry Team",
                JsonUtil.toJson(manifest), items.size(), "GOV_PUBLIC".equals(code) ? 1 : 0,
                LocalDateTime.now().minusDays(20));
        for (Object[] it : items) {
            db.insert("INSERT INTO ind_package_item(package_id,item_type,code,name,version,content) VALUES(?,?,?,?,?,?)",
                    pkgId, it[0], it[1], String.valueOf(it[1]), it[2], JsonUtil.toJson(it[3]));
        }
        db.insert("INSERT INTO ind_package_version(package_code,version,manifest,operator,create_time) VALUES(?,?,?,'陈顾问',?)",
                code, version, JsonUtil.toJson(manifest), LocalDateTime.now().minusDays(20));
    }

    private Object[] item(String type, String code, Object content, String version) {
        return new Object[]{type, code, version, content};
    }
}
