package com.dgp.seed;

import com.dgp.common.Db;
import com.dgp.security.AuthService;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 基础域数据：租户、角色、用户、数据源、标准数据元、行业术语。 */
@Component
public class MetaSeeder {

    private final Db db;

    public MetaSeeder(Db db) {
        this.db = db;
    }

    public void seed() {
        tenants();
        roles();
        users();
        datasources();
        collectTasks();
        standards();
        terms();
        configs();
        industries();
    }

    private void tenants() {
        if (db.count("SELECT COUNT(*) FROM sys_tenant") > 0) return;
        db.insert("INSERT INTO sys_tenant(code,name,parent_id,tenant_path,tenant_level,industry_code,isolation_mode,status,create_time) "
                + "VALUES('GROUP','张掖数智集团',0,'/1/',1,'GOV','SHARED','ENABLED',?)", LocalDateTime.now());
        db.insert("INSERT INTO sys_tenant(code,name,parent_id,tenant_path,tenant_level,industry_code,isolation_mode,status,create_time) "
                + "VALUES('GS','甘肃分公司',1,'/1/2/',2,'GOV','SHARED','ENABLED',?)", LocalDateTime.now());
        db.insert("INSERT INTO sys_tenant(code,name,parent_id,tenant_path,tenant_level,industry_code,isolation_mode,status,create_time) "
                + "VALUES('ZY','张掖市分公司',2,'/1/2/3/',3,'GOV','SHARED','ENABLED',?)", LocalDateTime.now());
    }

    private void roles() {
        if (db.count("SELECT COUNT(*) FROM sys_role") > 0) {
            syncRolePerms();
            return;
        }
        for (Object[] r : roleDefinitions()) {
            db.insert("INSERT INTO sys_role(code,name,perms,description) VALUES(?,?,?,?)", r[0], r[1], r[2], r[3]);
        }
        db.update("INSERT INTO sys_data_scope(tenant_id,role_code,scope_type,scope_value,create_time) VALUES(1,'ROLE_GOV_ADMIN','ALL','*',?)", LocalDateTime.now());
        db.update("INSERT INTO sys_data_scope(tenant_id,role_code,scope_type,scope_value,create_time) VALUES(1,'ROLE_QUALITY','DOMAIN','客户域,交易域,设备域',?)", LocalDateTime.now());
        db.update("INSERT INTO sys_data_scope(tenant_id,role_code,scope_type,scope_value,create_time) VALUES(1,'ROLE_ANALYST','DOMAIN','经营分析域,客户域',?)", LocalDateTime.now());
    }

    private void users() {
        if (db.count("SELECT COUNT(*) FROM sys_user") > 0) return;
        String pwd = AuthService.hash("Dgp@2026");
        Object[][] rows = {
                {"admin", "张治平", "ROLE_GOV_ADMIN", "数据治理中心", "zhangzp@dgp-demo.com", 1L},
                {"zhangsan", "张三", "ROLE_DATA_DEV", "数据开发组", "zhangsan@dgp-demo.com", 2L},
                {"liuqin", "刘琴", "ROLE_QUALITY", "数据质量组", "liuqin@dgp-demo.com", 2L},
                {"wangwu", "王武", "ROLE_ANALYST", "经营分析部", "wangwu@dgp-demo.com", 3L},
                {"secadmin", "赵安全", "ROLE_SEC_ADMIN", "安全合规组", "zhaoaq@dgp-demo.com", 1L},
                {"consultant", "陈顾问", "ROLE_INDUSTRY_CONSULTANT", "行业实施组", "chengw@dgp-demo.com", 1L}
        };
        for (Object[] r : rows) {
            db.insert("INSERT INTO sys_user(tenant_id,username,password,real_name,role_code,dept,email,status,create_time) "
                    + "VALUES(?,?,?,?,?,?,?,'启用',?)", r[5], r[0], pwd, r[1], r[2], r[3], r[4], LocalDateTime.now());
        }
    }

    private void datasources() {
        if (db.count("SELECT COUNT(*) FROM md_datasource") > 0) return;
        Object[][] ds = {
                {"DS-H2-LAKE", "本地数据湖（H2）", "H2", "127.0.0.1", "0", "dgp", "sa", "已连接", 3, "GOV", "公共域", "数据治理组", "平台内置源数据层，元数据采集与质量稽核的真实执行目标"},
                {"DS-MYSQL-CRM", "CRM 业务库", "MySQL", "200.10.3.21", "3306", "crm", "dgp_reader", "已连接", 18, "RETAIL", "客户域", "CRM 运维组", "客户主数据、会员与积分，日增约 3 万条"},
                {"DS-PG-ERP", "ERP 系统库", "PostgreSQL", "200.10.3.22", "5432", "erp", "dgp_reader", "已连接", 24, "INDUSTRY", "交易域", "ERP 运维组", "订单、库存、采购与财务凭证"},
                {"DS-KAFKA-IOT", "设备物联网消息", "Kafka", "200.10.3.31", "9092", "iot-metrics", "dgp", "已连接", 12, "INDUSTRY", "设备域", "工业互联组", "PLC/DCS 实时指标，峰值 8 万 TPS"},
                {"DS-HIVE-DW", "数据仓库（Hive）", "Hive", "200.10.3.40", "10000", "dwd", "dgp", "未测试", null, "GOV", "公共域", "数仓组", "离线数仓，ODS/DWD/DWS/ADS 四层"},
                {"DS-OPCUA-01", "一号轧钢车间 OPC UA", "OPC_UA", "200.10.20.15", "4840", "line1", "opc", "已连接", 45, "INDUSTRY", "设备域", "工业互联组", "工业协议采集，含 40 台设备 5 类指标"},
                {"DS-API-GOV", "政务数据共享交换", "API", "gov.example.gov.cn", "443", "open", "appkey", "未测试", null, "GOV", "政务域", "政务数据组", "人口、法人、空间地理基础库对接"}
        };
        for (Object[] d : ds) {
            db.insert("INSERT INTO md_datasource(tenant_id,code,name,dtype,host,port,db_name,username,industry,domain,owner,conn_status,"
                            + "last_test_time,latency_ms,collect_mode,remark,create_time) VALUES(1,?,?,?,?,?,?,?,?,?,?,?,?,?,'AUTO',?,?)",
                    d[0], d[1], d[2], d[3], d[4], d[5], d[6], d[9], d[10], d[11], d[7],
                    LocalDateTime.now().minusHours(2), d[8], d[12], LocalDateTime.now());
        }
    }

    private void collectTasks() {
        if (db.count("SELECT COUNT(*) FROM md_collect_task") > 0) return;
        Long h2 = ((Number) db.scalar("SELECT id FROM md_datasource WHERE code='DS-H2-LAKE'")).longValue();
        Long hive = ((Number) db.scalar("SELECT id FROM md_datasource WHERE code='DS-HIVE-DW'")).longValue();
        db.insert("INSERT INTO md_collect_task(tenant_id,datasource_id,name,mode,cron,status,next_run_time,owner,create_time) "
                + "VALUES(1,?,'数据湖元数据每日采集','AUTO','0 0 2 * * ?','ENABLED',?,'数据治理组',?)",
                h2, LocalDateTime.now().plusHours(6), LocalDateTime.now());
        db.insert("INSERT INTO md_collect_task(tenant_id,datasource_id,name,mode,cron,status,next_run_time,owner,create_time) "
                + "VALUES(1,?,'离线数仓元数据采集','AUTO','0 30 2 * * ?','ENABLED',?,'数仓组',?)",
                hive, LocalDateTime.now().plusHours(7), LocalDateTime.now());
    }

    private void standards() {
        if (db.count("SELECT COUNT(*) FROM std_element") > 0) return;
        Object[][] elements = {
                {"DE_CUST_ID", "customer_id", "客户标识", "客户", "STRING", "32", "字母数字", "客户在全域内的唯一标识", "非空、全局唯一", "GB/T 36344", "GOV"},
                {"DE_CUST_NAME", "cust_name", "客户名称", "客户", "STRING", "64", "中文/英文", "客户的法定或登记名称", "长度≤64", "行标", "GOV"},
                {"DE_MOBILE", "phone", "手机号码", "客户", "STRING", "11", "^1[3-9]\\d{9}$", "客户实名登记手机号", "11 位、符合号段规则", "国标GB/T 2260", "GOV"},
                {"DE_ID_CARD", "id_card", "证件号码", "客户", "STRING", "18", "GB11643", "居民身份证号码", "18 位、校验位正确", "国标GB11643", "GOV"},
                {"DE_EMAIL", "email", "电子邮箱", "客户", "STRING", "128", "RFC5322", "客户联系邮箱", "格式合法", "行标", "GOV"},
                {"DE_GENDER", "gender", "性别", "客户", "STRING", "8", "枚举", "客户性别", "男/女/未知", "国标GB/T 2261.1", "GOV"},
                {"DE_CITY", "city", "城市", "公共", "STRING", "32", "行政区划", "客户所在城市", "引用行政区划代码", "国标GB/T 2260", "GOV"},
                {"DE_ADDRESS", "addr", "详细地址", "客户", "STRING", "255", "自由文本", "客户居住或经营地址", "长度≤255", "行标", "GOV"},
                {"DE_BIRTHDAY", "birthday", "出生日期", "客户", "DATE", "10", "yyyy-MM-dd", "客户出生日期", "不得晚于当前日期", "国标", "GOV"},
                {"DE_AMOUNT", "amount", "金额", "交易", "DECIMAL", "12,2", "非负小数", "订单或支付的金额", "≥0，精度两位", "国标GB/T 36344", "FINANCE"},
                {"DE_ORDER_ID", "order_id", "订单编号", "交易", "STRING", "32", "字母数字", "订单唯一编号", "非空、唯一", "行标", "RETAIL"},
                {"DE_ORDER_TIME", "order_time", "下单时间", "交易", "TIMESTAMP", "19", "yyyy-MM-dd HH:mm:ss", "订单创建时间", "不得晚于当前时间", "行标", "RETAIL"},
                {"DE_ORDER_STATUS", "status", "订单状态", "交易", "STRING", "16", "枚举", "订单生命周期状态", "取值在枚举内", "行标", "RETAIL"},
                {"DE_PAY_TIME", "pay_time", "支付时间", "交易", "TIMESTAMP", "19", "yyyy-MM-dd HH:mm:ss", "订单支付完成时间", "已支付订单必填", "行标", "FINANCE"},
                {"DE_PRODUCT_ID", "product_id", "产品标识", "产品", "STRING", "32", "字母数字", "产品唯一标识", "非空", "行标", "RETAIL"},
                {"DE_PRODUCT_NAME", "product_name", "产品名称", "产品", "STRING", "128", "中文/英文", "产品对外名称", "长度≤128", "行标", "RETAIL"},
                {"DE_CATEGORY", "category", "产品分类", "产品", "STRING", "32", "枚举", "产品所属分类", "引用分类字典", "行标", "RETAIL"},
                {"DE_PRICE", "price", "单价", "产品", "DECIMAL", "12,2", "非负小数", "产品标准售价", "≥0", "行标", "RETAIL"},
                {"DE_COST", "cost", "成本价", "产品", "DECIMAL", "12,2", "非负小数", "产品单位成本", "≤售价", "行标", "RETAIL"},
                {"DE_DEVICE_ID", "device_id", "设备标识", "设备", "STRING", "32", "字母数字", "工业设备唯一编码", "非空、唯一", "行标", "INDUSTRY"},
                {"DE_METRIC_VALUE", "metric_value", "指标值", "设备", "DECIMAL", "12,3", "数值", "设备采集的实时指标值", "在量程范围内", "行标", "INDUSTRY"},
                {"DE_COLLECT_TIME", "collect_time", "采集时间", "设备", "TIMESTAMP", "19", "yyyy-MM-dd HH:mm:ss", "指标采集时间戳", "延迟≤5分钟", "行标", "INDUSTRY"},
                {"DE_TRADE_NO", "trade_no", "交易号", "交易", "STRING", "64", "字母数字", "支付渠道交易流水号", "唯一", "行标", "FINANCE"},
                {"DE_REG_TIME", "reg_time", "注册时间", "客户", "TIMESTAMP", "19", "yyyy-MM-dd HH:mm:ss", "客户注册时间", "不得晚于当前时间", "行标", "RETAIL"},
                {"DE_STAT_DATE", "stat_date", "统计日期", "公共", "DATE", "10", "yyyy-MM-dd", "汇总数据的统计日期", "非空", "国标", "GOV"},
                {"DE_REGION", "region", "区域", "公共", "STRING", "32", "枚举", "经营区域划分", "引用区域字典", "行标", "RETAIL"},
                {"DE_CHANNEL", "channel", "渠道", "公共", "STRING", "32", "枚举", "业务发生渠道", "引用渠道字典", "行标", "RETAIL"},
                {"DE_REFUND_RATE", "refund_rate", "退款率", "交易", "DECIMAL", "8,4", "比率", "退款金额占销售额比例", "0~1", "行标", "FINANCE"},
                {"DE_ACTIVE_FLAG", "is_valid", "有效标志", "公共", "INT", "1", "0/1", "记录是否有效", "仅取 0 或 1", "行标", "GOV"},
                {"DE_OWNER", "owner", "责任人", "公共", "STRING", "64", "中文", "数据对象责任人", "非空", "行标", "GOV"}
        };
        for (Object[] e : elements) {
            db.insert("INSERT INTO std_element(tenant_id,code,name,cn_name,category,data_type,data_len,fmt,business_desc,rule_desc,source,"
                            + "industry,status,version,owner,create_time) VALUES(1,?,?,?,?,?,?,?,?,?,?,?,'PUBLISHED','1.0.0','数据标准组',?)",
                    e[0], e[1], e[2], e[3], e[4], e[5], e[6], e[7], e[8], e[9], e[10], LocalDateTime.now());
        }
        Object[][] stds = {
                {"STD_CUST_BASE", "客户基础信息标准", "客户", "GOV", "企业标准", "DE_CUST_ID,DE_CUST_NAME,DE_MOBILE,DE_ID_CARD,DE_GENDER"},
                {"STD_TRADE_BASE", "交易基础信息标准", "交易", "FINANCE", "企业标准", "DE_ORDER_ID,DE_AMOUNT,DE_ORDER_TIME,DE_PAY_TIME,DE_TRADE_NO"},
                {"STD_PROD_BASE", "产品主数据标准", "产品", "RETAIL", "企业标准", "DE_PRODUCT_ID,DE_PRODUCT_NAME,DE_CATEGORY,DE_PRICE,DE_COST"},
                {"STD_DEVICE_METRIC", "工业设备指标标准", "设备", "INDUSTRY", "行标", "DE_DEVICE_ID,DE_METRIC_VALUE,DE_COLLECT_TIME"},
                {"STD_GOV_POPULATION", "政务人口数据标准", "公共", "GOV", "国标", "DE_CUST_NAME,DE_ID_CARD,DE_MOBILE,DE_CITY,DE_ADDRESS"}
        };
        for (Object[] s : stds) {
            db.insert("INSERT INTO std_standard(tenant_id,code,name,cn_name,category,industry,source,status,version,element_codes,description,create_time) "
                            + "VALUES(1,?,?,?,?,?,?,'PUBLISHED','1.0.0',?,?,?)",
                    s[0], s[1], s[2], s[1], s[3], s[4], s[5], s[2] + "，共 " + String.valueOf(s[5]).split(",").length + " 个数据元", LocalDateTime.now());
        }
    }

    private void terms() {
        if (db.count("SELECT COUNT(*) FROM md_biz_term") > 0) return;
        Object[][] terms = {
                {"GOV", "GOV_POP", "常住人口", "在统计区域内居住满六个月以上的人口", "(?i).*(person|pop|cust).*", "DE_CUST_NAME", "户籍人口;实有人口", "L2", "国标"},
                {"GOV", "GOV_GRID", "网格", "基层治理的最小管理单元", "(?i).*(grid|area).*", "DE_CITY", "管区;片区", "L1", "地标"},
                {"GOV", "GOV_LICENSE", "统一社会信用代码", "法人和其他组织的唯一身份标识", "(?i).*(credit|license|uscc).*", "DE_CUST_ID", "信用代码", "L2", "国标"},
                {"GOV", "GOV_EVENT", "工单事件", "城市治理中发现并流转处置的问题记录", "(?i).*(event|order|ticket).*", "DE_ORDER_ID", "事件;案卷", "L1", "行标"},
                {"TELECOM", "TEL_CUST", "客户", "使用通信服务的个人或组织", "(?i).*(cust|customer).*", "DE_CUST_ID", "用户;客户群", "L2", "行标YD/T"},
                {"TELECOM", "TEL_ARPU", "每用户平均收入", "统计周期内总收入除以用户数", "(?i).*arpu.*", "DE_AMOUNT", "单用户收入", "L2", "行标"},
                {"TELECOM", "TEL_MSISDN", "移动用户号码", "移动台国际 ISDN 号码", "(?i).*(msisdn|phone|mobile).*", "DE_MOBILE", "手机号;号码", "L3", "国标"},
                {"TELECOM", "TEL_IMSI", "国际移动用户识别码", "用户身份识别码", "(?i).*imsi.*", "DE_CUST_ID", "用户标识", "L4", "国标"},
                {"FINANCE", "FIN_ACCT", "账户", "客户在金融机构开立的资金账户", "(?i).*(acct|account).*", "DE_CUST_ID", "账号;户头", "L3", "行标"},
                {"FINANCE", "FIN_TXN", "交易流水", "账户发生的资金变动记录", "(?i).*(txn|trade|trans).*", "DE_TRADE_NO", "流水;交易", "L3", "行标"},
                {"FINANCE", "FIN_LOAN", "贷款余额", "借款人尚未偿还的贷款本金", "(?i).*(loan|balance).*", "DE_AMOUNT", "余额", "L3", "行标"},
                {"FINANCE", "FIN_REPORT", "监管报送", "按监管要求定期报送的数据集", "(?i).*(report|reg).*", "DE_STAT_DATE", "一表通;报送", "L2", "监管"},
                {"INDUSTRY", "IND_DEVICE", "设备", "产线上可采集运行参数的生产装备", "(?i).*(device|equip|plc).*", "DE_DEVICE_ID", "机组;装置", "L1", "行标"},
                {"INDUSTRY", "IND_ENERGY", "能源介质", "电、气、水、热等能源载体", "(?i).*(energy|power|gas|water).*", "DE_METRIC_VALUE", "能耗介质", "L1", "国标"},
                {"INDUSTRY", "IND_BATCH", "批次", "同一工艺条件下连续生产的产品集合", "(?i).*(batch|lot).*", "DE_PRODUCT_ID", "炉次;工单", "L1", "行标"},
                {"INDUSTRY", "IND_OEE", "设备综合效率", "时间开动率×性能开动率×合格品率", "(?i).*oee.*", "DE_METRIC_VALUE", "综合效率", "L1", "行标"},
                {"RETAIL", "RET_SKU", "单品", "库存管理的最小产品单元", "(?i).*(sku|product|item).*", "DE_PRODUCT_ID", "货品;商品", "L1", "行标"},
                {"RETAIL", "RET_STORE", "门店", "开展零售经营的实体或虚拟门店", "(?i).*(store|shop).*", "DE_CITY", "网点;店铺", "L1", "行标"},
                {"RETAIL", "RET_GMV", "商品交易总额", "统计周期内成交订单金额合计", "(?i).*(gmv|amount|sales).*", "DE_AMOUNT", "成交额", "L2", "行标"},
                {"RETAIL", "RET_STOCK", "库存周转天数", "平均库存除以日均销售成本", "(?i).*(stock|inventory).*", "DE_PRODUCT_ID", "周转;库存", "L1", "行标"}
        };
        for (Object[] t : terms) {
            db.insert("INSERT INTO md_biz_term(tenant_id,industry,term_code,term_name,definition,column_pattern,std_element_code,synonyms,"
                            + "security_level,source) VALUES(1,?,?,?,?,?,?,?,?,?)",
                    t[0], t[1], t[2], t[3], t[4], t[5], t[6], t[7], t[8]);
        }
    }

    private void configs() {
        if (db.count("SELECT COUNT(*) FROM sys_config") > 0) return;
        Object[][] cfg = {
                {"quality.dimension.weight", "{\"COMPLETENESS\":0.25,\"UNIQUENESS\":0.15,\"ACCURACY\":0.20,\"VALIDITY\":0.15,\"CONSISTENCY\":0.15,\"TIMELINESS\":0.10}", "质量评分维度权重"},
                {"quality.issue.autoCreate", "true", "稽核发现异常自动建单"},
                {"quality.issue.overdueHours", "72", "问题单默认处理时限（小时）"},
                {"metadata.collect.cron", "0 0 2 * * ?", "元数据采集默认周期"},
                {"service.quota.defaultDay", "10000", "开放 API 默认日配额"},
                {"security.audit.retainDays", "190", "审计日志保留天数（等保要求≥6个月）"},
                {"platform.version", "1.0.0", "平台版本"},
                {"security.mask.defaultRoles", "ROLE_ANALYST,ROLE_DATA_DEV", "默认脱敏适用角色"}
        };
        for (Object[] c : cfg) {
            db.update("INSERT INTO sys_config(config_key,config_value,description,update_time) VALUES(?,?,?,?)",
                    c[0], c[1], c[2], LocalDateTime.now());
        }
    }

    private void industries() {
        if (db.count("SELECT COUNT(*) FROM ind_industry") > 0) return;
        Object[][] inds = {
                {"TELECOM", "通信", "数据体量大，B/O/M 域划分清晰，集团-省-市多级租户"},
                {"FINANCE", "金融", "监管报送严格，口径统一，信创改造加速"},
                {"INDUSTRY", "工业与制造", "设备实时采集，OT/IT 融合，能源管理突出"},
                {"GOV", "政府与公共事业", "多源异构汇聚，公共数据目录与要素化"},
                {"RETAIL", "零售与消费品", "供应链分散，多组织协同，碳排管理"}
        };
        for (Object[] i : inds) {
            db.insert("INSERT INTO ind_industry(code,name,description) VALUES(?,?,?)", i[0], i[1], i[2]);
        }
    }

    public Map<String, Object> summary() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("users", db.count("SELECT COUNT(*) FROM sys_user"));
        m.put("datasources", db.count("SELECT COUNT(*) FROM md_datasource"));
        m.put("standards", db.count("SELECT COUNT(*) FROM std_element"));
        m.put("terms", db.count("SELECT COUNT(*) FROM md_biz_term"));
        return m;
    }

    // ================= 权限修复：内置角色权限随版本同步（幂等，与演示数据开关无关） =================

    /**
     * 内置角色定义。修正点：原种子数据未授予任何 {@code *:overview:view}，导致
     * 非管理员角色连 /overview 落地页都被导航守卫拒绝，KPI 接口也全部 403。
     */
    private static Object[][] roleDefinitions() {
        String admin = "*";
        String dev = "metadata:overview:view,metadata:datasource:view,metadata:table:view,metadata:column:view,metadata:search,"
                + "metadata:lineage:view,metadata:collect:view,metadata:collect:exec,metadata:version:view,metadata:import:view,"
                + "metadata:import:execute,metadata:term:view,"
                + "model:model:view,model:model:create,model:model:edit,model:model:publish,model:ddl:preview,model:ddl:execute,"
                + "model:overview:view,model:template:view,"
                + "standard:element:view,standard:mapping:view,standard:mapping:recommend,standard:mapping:batch,standard:compliance:check,"
                + "integration:datasource:view,integration:datasource:test,integration:job:view,integration:job:create,integration:job:edit,"
                + "integration:job:run,integration:job:script,integration:sql:edit,integration:adhoc:query,integration:overview:view,"
                + "integration:sandbox:view,workflow:definition:view,workflow:definition:trigger,workflow:instance:view,workflow:overview:view,"
                + "quality:stream:view,quality:stream:create,quality:stream:edit,quality:stream:exec,"
                + "migration:project:view,migration:project:create,migration:project:edit,migration:project:exec,migration:project:reconcile,migration:ds:view,migration:ds:edit,migration:ds:test,migration:file:view,migration:file:edit,migration:file:exec,"
                + "asset:catalog:view,asset:map:view,asset:category:view,quality:rule:view,quality:check:view,quality:report:view,"
                + "report:template:view";
        String quality = "metadata:overview:view,metadata:table:view,metadata:column:view,metadata:search,metadata:lineage:view,"
                + "quality:rule:view,quality:rule:create,quality:rule:edit,quality:rule:audit,quality:rule:exec,quality:template:view,"
                + "quality:rule:batch,quality:check:view,quality:check:create,quality:check:exec,quality:report:view,quality:report:export,"
                + "quality:score:view,quality:score:config,quality:issue:view,quality:issue:dispatch,quality:issue:handle,quality:issue:review,"
                + "quality:overview:view,quality:stream:view,quality:stream:create,quality:stream:edit,quality:stream:exec,quality:stream:handle,"
                + "standard:element:view,standard:mapping:view,asset:catalog:view,asset:map:view,asset:category:view,"
                + "report:template:view,report:download";
        String analyst = "metadata:overview:view,metadata:table:view,metadata:column:view,metadata:search,metadata:lineage:view,"
                + "asset:catalog:view,asset:map:view,asset:metrics:view,asset:label:view,asset:category:view,asset:overview:view,asset:value:view,"
                + "service:api:view,service:api:test,service:overview:view,quality:report:view,quality:score:view,quality:stream:view,"
                + "standard:element:view,security:mask:view,security:access:apply,security:access:view,report:template:view";
        String sec = "metadata:overview:view,metadata:table:view,metadata:column:view,metadata:search,metadata:lineage:view,"
                + "security:sensitive:view,security:sensitive:edit,security:sensitive:scan,security:mask:view,security:mask:create,"
                + "security:mask:edit,security:mask:approve,security:access:view,security:access:approve,security:access:revoke,"
                + "security:audit:view,security:audit:export,security:alert:view,security:alert:handle,security:alert:edit,"
                + "security:overview:view,asset:catalog:view,asset:category:view,report:template:view,report:download,quality:stream:view";
        String consultant = "metadata:overview:view,metadata:table:view,metadata:column:view,metadata:search,metadata:lineage:view,"
                + "industry:package:view,industry:package:import,industry:package:export,industry:package:instantiate,"
                + "industry:package:enable,industry:terminology:view,industry:terminology:edit,industry:lineage:view,industry:lineage:edit,"
                + "industry:overview:view,standard:element:view,standard:element:create,standard:element:edit,standard:element:publish,"
                + "standard:mapping:view,standard:compliance:view,model:model:view,quality:rule:view,quality:template:view,"
                + "quality:check:view,quality:score:view,quality:overview:view,quality:issue:view,quality:report:view,quality:stream:view,"
                + "migration:project:view,migration:project:create,migration:project:edit,migration:project:exec,migration:project:reconcile,migration:ds:view,migration:ds:edit,migration:ds:test,migration:file:view,migration:file:edit,migration:file:exec,"
                + "asset:catalog:view,asset:category:view,report:template:view";
        return new Object[][]{
                {"ROLE_GOV_ADMIN", "数据治理管理员", admin, "平台配置、权限管理、治理策略制定"},
                {"ROLE_DATA_DEV", "数据开发工程师", dev, "数据采集、加工、建模、任务调度"},
                {"ROLE_QUALITY", "数据质量专员", quality, "质量规则定义、稽核执行、问题闭环"},
                {"ROLE_ANALYST", "分析师/业务用户", analyst, "资产查找、血缘分析、服务申请"},
                {"ROLE_SEC_ADMIN", "安全管理员", sec, "脱敏策略、访问审计、安全告警"},
                {"ROLE_INDUSTRY_CONSULTANT", "行业实施顾问", consultant, "行业包导入、标准映射、术语维护"}
        };
    }

    /** 幂等同步内置角色权限：已有库升级时也能拿到修复后的权限编码。 */
    public void syncRolePerms() {
        for (Object[] r : roleDefinitions()) {
            if (db.one("SELECT code FROM sys_role WHERE code=?", r[0]) == null) {
                db.insert("INSERT INTO sys_role(code,name,perms,description) VALUES(?,?,?,?)", r[0], r[1], r[2], r[3]);
            } else {
                db.update("UPDATE sys_role SET perms=?,name=?,description=? WHERE code=?", r[2], r[1], r[3], r[0]);
            }
        }
    }

    /** 幂等：组织树与字典基础数据（此前 /auth/orgs、/admin/dicts 是硬编码假数据）。 */
    public void seedOrgAndDict() {
        if (db.count("SELECT COUNT(*) FROM sys_org") == 0) {
            db.insert("INSERT INTO sys_org(tenant_id,parent_id,code,name,sort_no,create_time) VALUES(1,0,'ORG-GOV','数据治理中心',1,?)", LocalDateTime.now());
            Object govObj = db.scalar("SELECT id FROM sys_org WHERE code='ORG-GOV'");
            long gov = govObj == null ? 0L : ((Number) govObj).longValue();
            db.insert("INSERT INTO sys_org(tenant_id,parent_id,code,name,sort_no,create_time) VALUES(1,?,'ORG-DEV','数据开发组',1,?)", gov, LocalDateTime.now());
            db.insert("INSERT INTO sys_org(tenant_id,parent_id,code,name,sort_no,create_time) VALUES(1,?,'ORG-QUALITY','数据质量组',2,?)", gov, LocalDateTime.now());
            db.insert("INSERT INTO sys_org(tenant_id,parent_id,code,name,sort_no,create_time) VALUES(1,?,'ORG-SEC','安全合规组',3,?)", gov, LocalDateTime.now());
            db.insert("INSERT INTO sys_org(tenant_id,parent_id,code,name,sort_no,create_time) VALUES(1,0,'ORG-BIZ','经营分析部',2,?)", LocalDateTime.now());
            db.insert("INSERT INTO sys_org(tenant_id,parent_id,code,name,sort_no,create_time) VALUES(1,0,'ORG-IND','行业实施组',3,?)", LocalDateTime.now());
        }
        if (db.count("SELECT COUNT(*) FROM sys_dict") == 0) {
            dict("quality_dimension", "质量维度",
                    "[\"COMPLETENESS\",\"UNIQUENESS\",\"ACCURACY\",\"VALIDITY\",\"CONSISTENCY\",\"TIMELINESS\"]");
            dict("issue_status", "问题单状态",
                    "[\"DISCOVERED\",\"DISPATCHED\",\"PROCESSING\",\"REVIEWING\",\"CLOSED\",\"OVERDUE\",\"IGNORED\",\"RECURRED\"]");
            dict("security_level", "敏感等级", "[\"L1\",\"L2\",\"L3\",\"L4\",\"L5\"]");
            dict("industry", "行业编码", "[\"TELECOM\",\"FIN\",\"MFG\",\"GOV\",\"RETAIL\"]");
            dict("data_layer", "数据分层", "[\"ODS\",\"DWD\",\"DWS\",\"ADS\"]");
            dict("job_status", "任务状态", "[\"DRAFT\",\"PUBLISHED\",\"RUNNING\",\"SUCCESS\",\"FAILED\",\"KILLED\",\"OFFLINE\"]");
        }
    }

    private void dict(String code, String name, String itemsJson) {
        db.insert("INSERT INTO sys_dict(dict_code,dict_name,items,update_time,create_time) VALUES(?,?,?,?,?)",
                code, name, itemsJson, LocalDateTime.now(), LocalDateTime.now());
    }
}
