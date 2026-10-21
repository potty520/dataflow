package com.dgp.seed;

import com.dgp.common.Db;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * 源数据层构造：ODS / DWD / DWS / ADS 真实业务表。
 *
 * <p>刻意注入可量化的质量缺陷（空值、重复、格式非法、越界、时效滞后、跨表不一致），
 * 使质量稽核、评分、问题闭环、脱敏预览等能力运行在真实数据之上。</p>
 */
@Component
public class SourceDataSeeder {

    private final Db db;
    private final Random rnd = new Random(20261017L);

    private static final String[] SURNAME = {"王", "李", "张", "刘", "陈", "杨", "赵", "黄", "周", "吴", "徐", "孙", "胡", "朱", "高", "林", "何", "郭", "马", "罗"};
    private static final String[] GIVEN = {"伟", "芳", "娜", "敏", "静", "丽", "强", "磊", "军", "洋", "勇", "艳", "杰", "娟", "涛", "明", "超", "秀英", "霞", "平", "刚", "桂英"};
    private static final String[] PROVINCE = {"甘肃省", "陕西省", "青海省", "宁夏回族自治区", "新疆维吾尔自治区", "四川省", "河南省"};
    private static final String[] CITY = {"张掖市", "兰州市", "西安市", "西宁市", "银川市", "乌鲁木齐市", "成都市", "郑州市"};
    private static final String[] CHANNEL = {"APP", "小程序", "WEB", "线下门店", "电话销售", "分销商"};
    private static final String[] REGION = {"河西片区", "陇中片区", "陇东片区", "省会直营", "省外经销"};
    private static final String[] CATEGORY = {"智能终端", "工业传感器", "网络设备", "配套耗材", "软件许可", "运维服务"};
    private static final String[] METRIC = {"温度", "压力", "振动", "电流", "流量"};
    private static final String[] WORKSHOP = {"一号轧钢车间", "二号冷轧车间", "能源动力站", "成品包装线"};
    private static final String[] ORDER_STATUS = {"待支付", "已支付", "已发货", "已完成", "已取消", "退款中"};
    private static final String[] PAY_CHANNEL = {"微信支付", "支付宝", "银联", "对公转账", "余额支付"};

    public SourceDataSeeder(Db db) {
        this.db = db;
    }

    public void seed() {
        createTables();
        if (db.count("SELECT COUNT(*) FROM ods_customer") > 0) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        List<Object[]> customers = buildCustomers(now);
        db.batch("INSERT INTO ods_customer(customer_id,cust_name,gender,birthday,phone,email,id_card,province,city,addr,reg_channel,reg_time,status,member_level) "
                + "VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?)", customers);

        List<String[]> custKeys = new ArrayList<>();
        for (Object[] c : customers) {
            custKeys.add(new String[]{String.valueOf(c[0]), String.valueOf(c[8])});
        }

        List<Object[]> products = buildProducts(now);
        db.batch("INSERT INTO ods_product(product_id,product_name,category,price,cost,supplier,shelf_date,status) VALUES(?,?,?,?,?,?,?,?)", products);

        List<Object[]> orders = buildOrders(now, custKeys, products);
        db.batch("INSERT INTO ods_order(order_id,customer_id,product_id,product_name,amount,quantity,discount,order_time,pay_time,status,channel,region,sales_rep) "
                + "VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?)", orders);

        db.batch("INSERT INTO ods_payment(pay_id,order_id,pay_channel,pay_amount,pay_time,trade_no,status) VALUES(?,?,?,?,?,?,?)",
                buildPayments(now, orders));

        db.batch("INSERT INTO ods_device_metric(device_id,device_name,workshop,metric_name,metric_value,unit,collect_time) VALUES(?,?,?,?,?,?,?)",
                buildMetrics(now));

        db.batch("INSERT INTO ods_gov_population(person_name,id_card,gender,birthday,province,city,street,phone,update_time) VALUES(?,?,?,?,?,?,?,?,?)",
                buildPopulation(now));

        buildDwd(now, customers, orders);
        buildDws(now);
    }

    private void createTables() {
        String[] ddl = {
                "CREATE TABLE IF NOT EXISTS ods_customer(customer_id VARCHAR(32) PRIMARY KEY, cust_name VARCHAR(64), gender VARCHAR(8), "
                        + "birthday DATE, phone VARCHAR(32), email VARCHAR(128), id_card VARCHAR(32), province VARCHAR(32), city VARCHAR(32), "
                        + "addr VARCHAR(255), reg_channel VARCHAR(32), reg_time DATETIME, status VARCHAR(16), member_level VARCHAR(16))",
                "CREATE TABLE IF NOT EXISTS ods_order(order_id VARCHAR(32) PRIMARY KEY, customer_id VARCHAR(32), product_id VARCHAR(32), "
                        + "product_name VARCHAR(128), amount DECIMAL(12,2), quantity INT, discount DECIMAL(6,2), order_time DATETIME, "
                        + "pay_time DATETIME, status VARCHAR(16), channel VARCHAR(32), region VARCHAR(32), sales_rep VARCHAR(64))",
                "CREATE TABLE IF NOT EXISTS ods_product(product_id VARCHAR(32) PRIMARY KEY, product_name VARCHAR(128), category VARCHAR(32), "
                        + "price DECIMAL(12,2), cost DECIMAL(12,2), supplier VARCHAR(128), shelf_date DATE, status VARCHAR(16))",
                "CREATE TABLE IF NOT EXISTS ods_payment(pay_id VARCHAR(32) PRIMARY KEY, order_id VARCHAR(32), pay_channel VARCHAR(32), "
                        + "pay_amount DECIMAL(12,2), pay_time DATETIME, trade_no VARCHAR(64), status VARCHAR(16), "
                        + "KEY idx_pay_order(order_id, status))",
                "CREATE TABLE IF NOT EXISTS ods_device_metric(id BIGINT AUTO_INCREMENT PRIMARY KEY, device_id VARCHAR(32), "
                        + "device_name VARCHAR(64), workshop VARCHAR(64), metric_name VARCHAR(32), metric_value DECIMAL(12,3), unit VARCHAR(16), "
                        + "collect_time DATETIME)",
                "CREATE TABLE IF NOT EXISTS ods_gov_population(id BIGINT AUTO_INCREMENT PRIMARY KEY, person_name VARCHAR(64), "
                        + "id_card VARCHAR(32), gender VARCHAR(8), birthday DATE, province VARCHAR(32), city VARCHAR(32), street VARCHAR(128), "
                        + "phone VARCHAR(32), update_time DATETIME)",

                "CREATE TABLE IF NOT EXISTS dwd_customer_dim(customer_key VARCHAR(64) PRIMARY KEY, customer_id VARCHAR(32), cust_name VARCHAR(64), "
                        + "gender VARCHAR(8), age INT, phone VARCHAR(32), email VARCHAR(128), city VARCHAR(32), reg_date DATE, "
                        + "member_level VARCHAR(16), is_valid INT)",
                "CREATE TABLE IF NOT EXISTS dwd_order_fact(order_key VARCHAR(64) PRIMARY KEY, order_id VARCHAR(32), customer_id VARCHAR(32), "
                        + "product_id VARCHAR(32), amount DECIMAL(12,2), quantity INT, order_date DATE, pay_date DATE, status VARCHAR(16), "
                        + "region VARCHAR(32), channel VARCHAR(32), is_refund INT, "
                        + "KEY idx_dwd_order(order_id), KEY idx_dwd_status(status))",
                "CREATE TABLE IF NOT EXISTS dwd_device_fact(device_key VARCHAR(64) PRIMARY KEY, device_id VARCHAR(32), workshop VARCHAR(64), "
                        + "metric_name VARCHAR(32), metric_value DECIMAL(12,3), collect_date DATE)",
                "CREATE TABLE IF NOT EXISTS dws_order_summary(stat_date DATE, region VARCHAR(32), channel VARCHAR(32), order_cnt INT, "
                        + "amount DECIMAL(14,2), refund_amt DECIMAL(14,2), PRIMARY KEY(stat_date,region,channel))",
                "CREATE TABLE IF NOT EXISTS dws_customer_value(customer_id VARCHAR(32) PRIMARY KEY, total_amount DECIMAL(14,2), order_cnt INT, "
                        + "last_order_date DATE, value_level VARCHAR(16))",
                "CREATE TABLE IF NOT EXISTS dws_region_sales(stat_date DATE, region VARCHAR(32), sales_amount DECIMAL(14,2), "
                        + "target_amount DECIMAL(14,2), completion_rate DECIMAL(8,4), PRIMARY KEY(stat_date,region))",
                "CREATE TABLE IF NOT EXISTS ads_sales_dashboard(stat_date DATE PRIMARY KEY, total_amount DECIMAL(14,2), order_cnt INT, "
                        + "customer_cnt INT, avg_order_amount DECIMAL(12,2), refund_rate DECIMAL(8,4))"
        };
        for (String s : ddl) {
            db.update(s);
        }
    }

    private String phone() {
        return "1" + (rnd.nextBoolean() ? "3" : "8") + String.format("%09d", rnd.nextInt(1000000000));
    }

    private String idCard(LocalDate birthday, boolean male) {
        StringBuilder sb = new StringBuilder("622201");
        sb.append(birthday.toString().replace("-", ""));
        sb.append(String.format("%03d", rnd.nextInt(999) + 1));
        sb.append(male ? (rnd.nextInt(5) * 2 + 1) : (rnd.nextInt(5) * 2));
        return sb.toString();
    }

    private List<Object[]> buildCustomers(LocalDateTime now) {
        List<Object[]> list = new ArrayList<>(6200);
        Set<String> usedCards = new HashSet<>();
        for (int i = 1; i <= 6000; i++) {
            boolean male = rnd.nextBoolean();
            String name = SURNAME[rnd.nextInt(SURNAME.length)] + GIVEN[rnd.nextInt(GIVEN.length)];
            LocalDate birth = LocalDate.of(1955 + rnd.nextInt(48), 1 + rnd.nextInt(12), 1 + rnd.nextInt(28));
            String card = idCard(birth, male);
            // 缺陷 1：证件号重复（90 个）
            if (i > 500 && i % 63 == 0 && !usedCards.isEmpty()) {
                card = usedCards.iterator().next();
            } else {
                usedCards.add(card);
            }
            String ph = phone();
            // 缺陷 2：手机号为空（180 个）
            if (i % 33 == 0) ph = null;
            String email = "user" + i + "@dgp-demo.com";
            // 缺陷 3：邮箱格式非法（120 个）
            if (i % 47 == 0) email = "user" + i + "dgp-demo.com";
            // 缺陷 4：生日越界（未来日期，60 个）
            LocalDate useBirth = i % 97 == 0 ? LocalDate.now().plusDays(30 + rnd.nextInt(200)) : birth;
            String city = CITY[rnd.nextInt(CITY.length)];
            // 缺陷 5：城市为空（150 个）
            if (i % 40 == 0) city = null;
            String gender = male ? "男" : "女";
            // 缺陷 6：枚举非法（45 个）
            if (i % 133 == 0) gender = "未知X";
            LocalDateTime reg = now.minusDays(rnd.nextInt(900)).minusMinutes(rnd.nextInt(1440));
            // 缺陷 7：注册时间未来（40 个）
            if (i % 151 == 0) reg = now.plusDays(5);
            String level = rnd.nextInt(10) < 2 ? "钻石" : rnd.nextInt(10) < 5 ? "黄金" : "普通";
            list.add(new Object[]{"C" + String.format("%07d", i), name, gender, useBirth, ph, email, card,
                    PROVINCE[rnd.nextInt(PROVINCE.length)], city, CITY[rnd.nextInt(CITY.length)] + "甘州区" + (rnd.nextInt(200) + 1) + "号",
                    CHANNEL[rnd.nextInt(CHANNEL.length)], reg, rnd.nextInt(50) == 0 ? "停用" : "正常", level});
        }
        return list;
    }

    private List<Object[]> buildProducts(LocalDateTime now) {
        List<Object[]> list = new ArrayList<>();
        for (int i = 1; i <= 320; i++) {
            String cat = CATEGORY[rnd.nextInt(CATEGORY.length)];
            BigDecimal price = BigDecimal.valueOf(500 + rnd.nextInt(48000)).setScale(2, RoundingMode.HALF_UP);
            BigDecimal cost = price.multiply(BigDecimal.valueOf(0.55 + rnd.nextDouble() * 0.3)).setScale(2, RoundingMode.HALF_UP);
            // 缺陷：成本高于售价（12 个）
            if (i % 27 == 0) cost = price.multiply(BigDecimal.valueOf(1.15)).setScale(2, RoundingMode.HALF_UP);
            String supplier = "供应商-" + (char) ('A' + rnd.nextInt(12)) + (rnd.nextInt(20) + 1);
            // 缺陷：分类为空（20 个）
            String category = i % 16 == 0 ? null : cat;
            list.add(new Object[]{"P" + String.format("%05d", i), cat + "-" + (rnd.nextInt(30) + 1) + "型", category, price, cost,
                    supplier, LocalDate.now().minusDays(rnd.nextInt(700)), rnd.nextInt(30) == 0 ? "下架" : "在售"});
        }
        return list;
    }

    private List<Object[]> buildOrders(LocalDateTime now, List<String[]> custs, List<Object[]> products) {
        List<Object[]> list = new ArrayList<>(24000);
        for (int i = 1; i <= 24000; i++) {
            String[] c = custs.get(rnd.nextInt(custs.size()));
            Object[] p = products.get(rnd.nextInt(products.size()));
            int qty = 1 + rnd.nextInt(6);
            BigDecimal price = (BigDecimal) p[3];
            BigDecimal amount = price.multiply(BigDecimal.valueOf(qty)).setScale(2, RoundingMode.HALF_UP);
            // 缺陷：金额为负/为零（220 个）
            if (i % 109 == 0) amount = amount.negate();
            // 缺陷：金额超业务上限（80 个）
            if (i % 301 == 0) amount = BigDecimal.valueOf(100000 + rnd.nextInt(900000)).setScale(2, RoundingMode.HALF_UP);
            LocalDateTime orderTime = now.minusDays(rnd.nextInt(120)).minusMinutes(rnd.nextInt(1440));
            // 缺陷：下单时间未来（150 个）
            if (i % 161 == 0) orderTime = now.plusDays(3);
            String status = ORDER_STATUS[rnd.nextInt(ORDER_STATUS.length)];
            LocalDateTime payTime = null;
            if (!"待支付".equals(status) && !"已取消".equals(status)) {
                payTime = orderTime.plusMinutes(3 + rnd.nextInt(600));
                // 缺陷：已支付但支付时间为空（300 个）
                if (i % 81 == 0) payTime = null;
            }
            // 缺陷：状态枚举非法（60 个）
            if (i % 401 == 0) status = "异常状态";
            list.add(new Object[]{"O" + String.format("%09d", i), c[0], p[0], p[1], amount, qty,
                    BigDecimal.valueOf(rnd.nextInt(10)).setScale(2, RoundingMode.HALF_UP), orderTime, payTime, status,
                    CHANNEL[rnd.nextInt(CHANNEL.length)], REGION[rnd.nextInt(REGION.length)],
                    SURNAME[rnd.nextInt(SURNAME.length)] + (rnd.nextBoolean() ? "经理" : "主管")});
        }
        return list;
    }

    private List<Object[]> buildPayments(LocalDateTime now, List<Object[]> orders) {
        List<Object[]> list = new ArrayList<>();
        int idx = 0;
        Set<String> tradeNos = new HashSet<>();
        for (Object[] o : orders) {
            String status = String.valueOf(o[9]);
            if ("待支付".equals(status) || "已取消".equals(status)) continue;
            idx++;
            String payId = "PAY" + String.format("%09d", idx);
            BigDecimal amount = (BigDecimal) o[4];
            // 缺陷：支付金额与订单金额不一致（90 个）
            if (idx % 187 == 0) amount = amount.subtract(BigDecimal.valueOf(10));
            String tradeNo = "TN" + String.format("%012d", 100000 + idx);
            // 缺陷：交易号重复（160 个）
            if (idx % 103 == 0 && !tradeNos.isEmpty()) {
                tradeNo = tradeNos.iterator().next();
            } else {
                tradeNos.add(tradeNo);
            }
            LocalDateTime payTime = o[8] instanceof LocalDateTime ldt ? ldt : now.minusDays(rnd.nextInt(30));
            list.add(new Object[]{payId, o[0], PAY_CHANNEL[rnd.nextInt(PAY_CHANNEL.length)], amount,
                    Timestamp.valueOf(payTime), tradeNo, rnd.nextInt(60) == 0 ? "已退款" : "成功"});
        }
        return list;
    }

    private List<Object[]> buildMetrics(LocalDateTime now) {
        List<Object[]> list = new ArrayList<>(12000);
        for (int d = 1; d <= 40; d++) {
            String workshop = WORKSHOP[(d - 1) % WORKSHOP.length];
            String deviceId = "DEV" + String.format("%04d", d);
            for (int t = 0; t < 300; t++) {
                String metric = METRIC[(d + t) % METRIC.length];
                double base = switch (metric) {
                    case "温度" -> 60 + rnd.nextDouble() * 40;
                    case "压力" -> 1.2 + rnd.nextDouble() * 0.8;
                    case "振动" -> 0.1 + rnd.nextDouble() * 0.6;
                    case "电流" -> 8 + rnd.nextDouble() * 12;
                    default -> 20 + rnd.nextDouble() * 30;
                };
                BigDecimal v = BigDecimal.valueOf(base).setScale(3, RoundingMode.HALF_UP);
                // 缺陷：量程越界（400 个）与空值（200 个）
                if ((d * 300 + t) % 30 == 0) v = BigDecimal.valueOf(-999).setScale(3, RoundingMode.HALF_UP);
                if ((d * 300 + t) % 60 == 0) v = null;
                LocalDateTime ts = now.minusMinutes((long) t * 5 + rnd.nextInt(3));
                // 缺陷：部分设备采集滞后（时效性）
                if (d % 13 == 0) ts = now.minusHours(6).minusMinutes(t * 5L);
                list.add(new Object[]{deviceId, workshop + "-设备" + d, workshop, metric, v,
                        metric.equals("温度") ? "℃" : metric.equals("压力") ? "MPa" : metric.equals("电流") ? "A" : "m³/h", ts});
            }
        }
        return list;
    }

    private List<Object[]> buildPopulation(LocalDateTime now) {
        List<Object[]> list = new ArrayList<>();
        for (int i = 1; i <= 5000; i++) {
            boolean male = rnd.nextBoolean();
            LocalDate birth = LocalDate.of(1945 + rnd.nextInt(58), 1 + rnd.nextInt(12), 1 + rnd.nextInt(28));
            String card = idCard(birth, male);
            // 缺陷：证件号为空（120 个）与长度非法（80 个）
            if (i % 42 == 0) card = null;
            if (i % 63 == 0) card = card == null ? null : card.substring(0, 15);
            list.add(new Object[]{SURNAME[rnd.nextInt(SURNAME.length)] + GIVEN[rnd.nextInt(GIVEN.length)],
                    card, male ? "男" : "女", birth, "甘肃省", "张掖市", "甘州区" + (rnd.nextInt(30) + 1) + "街道",
                    phone(), now.minusDays(rnd.nextInt(30))});
        }
        return list;
    }

    private void buildDwd(LocalDateTime now, List<Object[]> customers, List<Object[]> orders) {
        List<Object[]> dim = new ArrayList<>();
        for (Object[] c : customers) {
            LocalDate birth = (LocalDate) c[3];
            int age = Math.max(0, LocalDate.now().getYear() - birth.getYear());
            String phone = (String) c[4];
            dim.add(new Object[]{"CK" + c[0], c[0], c[1], c[2], age, phone, c[5], c[8],
                    java.sql.Date.valueOf((LocalDateTime) c[11] == null ? now.toLocalDate() : ((LocalDateTime) c[11]).toLocalDate()),
                    c[13], phone == null || c[8] == null ? 0 : 1});
        }
        db.batch("INSERT INTO dwd_customer_dim(customer_key,customer_id,cust_name,gender,age,phone,email,city,reg_date,member_level,is_valid) "
                + "VALUES(?,?,?,?,?,?,?,?,?,?,?)", dim);

        List<Object[]> fact = new ArrayList<>();
        List<Object[]> value = new ArrayList<>();
        java.util.Map<String, BigDecimal> sums = new java.util.HashMap<>();
        java.util.Map<String, Integer> counts = new java.util.HashMap<>();
        java.util.Map<String, LocalDate> last = new java.util.HashMap<>();
        for (Object[] o : orders) {
            String status = String.valueOf(o[9]);
            LocalDateTime ot = (LocalDateTime) o[7];
            LocalDateTime pt = (LocalDateTime) o[8];
            fact.add(new Object[]{"OK" + o[0], o[0], o[1], o[2], o[4], o[5],
                    java.sql.Date.valueOf(ot.toLocalDate()), pt == null ? null : java.sql.Date.valueOf(pt.toLocalDate()),
                    status, o[11], o[10], "退款中".equals(status) || "已退款".equals(status) ? 1 : 0});
            if (!"已取消".equals(status) && !"待支付".equals(status)) {
                String cid = String.valueOf(o[1]);
                sums.merge(cid, (BigDecimal) o[4], BigDecimal::add);
                counts.merge(cid, 1, Integer::sum);
                last.merge(cid, ot.toLocalDate(), (a, b) -> a.isAfter(b) ? a : b);
            }
        }
        db.batch("INSERT INTO dwd_order_fact(order_key,order_id,customer_id,product_id,amount,quantity,order_date,pay_date,status,region,channel,is_refund) "
                + "VALUES(?,?,?,?,?,?,?,?,?,?,?,?)", fact);
        for (var e : counts.entrySet()) {
            value.add(new Object[]{e.getKey(), sums.get(e.getKey()), e.getValue(),
                    java.sql.Date.valueOf(last.get(e.getKey())), e.getValue() > 20 ? "高价值" : e.getValue() > 8 ? "中价值" : "普通"});
        }
        db.batch("INSERT INTO dws_customer_value(customer_id,total_amount,order_cnt,last_order_date,value_level) VALUES(?,?,?,?,?)", value);

        List<Object[]> devFact = new ArrayList<>();
        for (var r : db.list("SELECT device_id,workshop,metric_name,metric_value,collect_time FROM ods_device_metric WHERE metric_value IS NOT NULL")) {
            Object ts = r.get("collectTime");
            LocalDate d = ts instanceof LocalDateTime ldt ? ldt.toLocalDate() : LocalDate.now();
            devFact.add(new Object[]{"DK" + r.get("deviceId") + "-" + d + "-" + r.get("metricName") + "-" + devFact.size(),
                    r.get("deviceId"), r.get("workshop"), r.get("metricName"), r.get("metricValue"), java.sql.Date.valueOf(d)});
        }
        db.batch("INSERT INTO dwd_device_fact(device_key,device_id,workshop,metric_name,metric_value,collect_date) VALUES(?,?,?,?,?,?)", devFact);
    }

    private void buildDws(LocalDateTime now) {
        List<java.util.Map<String, Object>> agg = db.list(
                "SELECT order_date AS d, region, channel, COUNT(*) AS cnt, SUM(amount) AS amt, "
                        + "SUM(CASE WHEN is_refund=1 THEN amount ELSE 0 END) AS refund FROM dwd_order_fact "
                        + "WHERE order_date >= ? GROUP BY order_date, region, channel", java.sql.Date.valueOf(LocalDate.now().minusDays(60)));
        List<Object[]> sumRows = new ArrayList<>();
        for (var r : agg) {
            sumRows.add(new Object[]{r.get("d"), r.get("region"), r.get("channel"), r.get("cnt"), r.get("amt"), r.get("refund")});
        }
        db.batch("INSERT INTO dws_order_summary(stat_date,region,channel,order_cnt,amount,refund_amt) VALUES(?,?,?,?,?,?)", sumRows);

        List<java.util.Map<String, Object>> region = db.list(
                "SELECT order_date AS d, region, SUM(amount) AS amt FROM dwd_order_fact WHERE order_date >= ? "
                        + "GROUP BY order_date, region ORDER BY d", java.sql.Date.valueOf(LocalDate.now().minusDays(60)));
        List<Object[]> regionRows = new ArrayList<>();
        for (var r : region) {
            BigDecimal amt = (BigDecimal) r.get("amt");
            BigDecimal target = amt.multiply(BigDecimal.valueOf(0.85 + rnd.nextDouble() * 0.4)).setScale(2, RoundingMode.HALF_UP);
            BigDecimal rate = target.signum() == 0 ? BigDecimal.ZERO
                    : amt.divide(target, 4, RoundingMode.HALF_UP);
            regionRows.add(new Object[]{r.get("d"), r.get("region"), amt, target, rate});
        }
        db.batch("INSERT INTO dws_region_sales(stat_date,region,sales_amount,target_amount,completion_rate) VALUES(?,?,?,?,?)", regionRows);

        List<java.util.Map<String, Object>> ads = db.list(
                "SELECT order_date AS d, SUM(amount) AS amt, COUNT(*) AS cnt, COUNT(DISTINCT customer_id) AS cust, "
                        + "SUM(CASE WHEN is_refund=1 THEN amount ELSE 0 END) AS refund FROM dwd_order_fact "
                        + "WHERE order_date >= ? GROUP BY order_date ORDER BY d", java.sql.Date.valueOf(LocalDate.now().minusDays(60)));
        List<Object[]> adsRows = new ArrayList<>();
        for (var r : ads) {
            BigDecimal amt = (BigDecimal) r.get("amt");
            BigDecimal refund = r.get("refund") == null ? BigDecimal.ZERO : (BigDecimal) r.get("refund");
            int cnt = ((Number) r.get("cnt")).intValue();
            adsRows.add(new Object[]{r.get("d"), amt, cnt, r.get("cust"),
                    cnt == 0 ? BigDecimal.ZERO : amt.divide(BigDecimal.valueOf(cnt), 2, RoundingMode.HALF_UP),
                    amt.signum() == 0 ? BigDecimal.ZERO : refund.divide(amt, 4, RoundingMode.HALF_UP)});
        }
        db.batch("INSERT INTO ads_sales_dashboard(stat_date,total_amount,order_cnt,customer_cnt,avg_order_amount,refund_rate) VALUES(?,?,?,?,?,?)", adsRows);
    }

    public static Timestamp ts(LocalDateTime t) {
        return Timestamp.valueOf(t);
    }
}
