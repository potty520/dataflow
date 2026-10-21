package com.dgp.seed;

import com.dgp.common.Db;
import com.dgp.security.SensitiveScanService;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** 安全域种子：敏感识别规则、真实扫描、脱敏策略、访问申请、审计与告警。 */
@Component
public class SecuritySeeder {

    private final Db db;
    private final SensitiveScanService scanner;

    public SecuritySeeder(Db db, SensitiveScanService scanner) {
        this.db = db;
        this.scanner = scanner;
    }

    public void seed() {
        if (db.count("SELECT COUNT(*) FROM sec_sensitive_rule") > 0) return;
        rules();
        scanner.scan(null, "赵安全");
        policies();
        access();
        alerts();
    }

    private void rules() {
        Object[][] rules = {
                {"SR_NAME_PHONE", "手机号-字段名识别", "NAME", "(?i).*(phone|mobile|msisdn|tel).*", "个人信息", "L3", "MASK"},
                {"SR_NAME_IDCARD", "证件号-字段名识别", "NAME", "(?i).*(id_card|idcard|identity|credit_no).*", "个人身份", "L4", "MASK"},
                {"SR_NAME_EMAIL", "邮箱-字段名识别", "NAME", "(?i).*(email|mail).*", "个人信息", "L3", "MASK"},
                {"SR_NAME_ADDR", "地址-字段名识别", "NAME", "(?i).*(addr|address).*", "个人信息", "L3", "TRUNCATE"},
                {"SR_NAME_NAME", "姓名-字段名识别", "NAME", "(?i).*(cust_name|person_name|real_name).*", "个人信息", "L2", "REPLACE"},
                {"SR_CONTENT_IDCARD", "证件号-内容识别", "CONTENT", "^\\d{17}[\\dXx]$", "个人身份", "L4", "MASK"},
                {"SR_CONTENT_PHONE", "手机号-内容识别", "CONTENT", "^1[3-9]\\d{9}$", "个人信息", "L3", "MASK"},
                {"SR_CONTENT_EMAIL", "邮箱-内容识别", "CONTENT", "^[\\w.+-]+@[\\w-]+\\.[\\w.]+$", "个人信息", "L3", "MASK"},
                {"SR_COMMENT_IDCARD", "证件号码-注释识别", "COMMENT", "(?i).*(身份证|证件号|证件号码).*", "个人身份", "L4", "MASK"},
                {"SR_COMMENT_ADDR", "地址-注释识别", "COMMENT", "(?i).*(地址|住址|场所).*", "个人信息", "L3", "TRUNCATE"},
                {"SR_NAME_AMOUNT", "金额-字段名识别", "NAME", "(?i).*(amount|pay_amount|total_amount).*", "经营数据", "L2", "GENERALIZE"}
        };
        for (Object[] r : rules) {
            db.insert("INSERT INTO sec_sensitive_rule(tenant_id,code,name,match_type,pattern,category,security_level,mask_algo,status,create_time) "
                    + "VALUES(1,?,?,?,?,?,?,?,'ENABLED',?)", r[0], r[1], r[2], r[3], r[4], r[5], r[6], LocalDateTime.now().minusDays(12));
        }
    }

    private void policies() {
        List<Map<String, Object>> cols = db.list(
                "SELECT c.table_id, c.name AS col, c.sensitive_level, c.mask_algo, t.name AS tbl FROM md_column c "
                        + "JOIN md_table t ON t.id=c.table_id WHERE c.sensitive_level IN ('L3','L4') ORDER BY t.name, c.column_order");
        int i = 0;
        for (Map<String, Object> c : cols) {
            String algo = c.get("maskAlgo") == null ? "MASK" : String.valueOf(c.get("maskAlgo"));
            db.insert("INSERT INTO sec_mask_policy(tenant_id,table_id,table_name,column_name,algorithm,security_level,role_codes,"
                            + "status,approver,approve_time,create_by,create_time) VALUES(1,?,?,?,?,?,'ROLE_ANALYST,ROLE_DATA_DEV',?,?,?,?,?)",
                    c.get("tableId"), c.get("tbl"), c.get("col"), algo, c.get("sensitiveLevel"),
                    i % 9 == 4 ? "PENDING" : "ENABLED", i % 9 == 4 ? null : "赵安全",
                    i % 9 == 4 ? null : LocalDateTime.now().minusDays(8), "赵安全", LocalDateTime.now().minusDays(10));
            i++;
        }
        db.insert("INSERT INTO sec_mask_policy(tenant_id,table_id,table_name,column_name,algorithm,security_level,role_codes,"
                        + "status,approver,approve_time,create_by,create_time) VALUES(1,?,'ods_customer','phone','ENCRYPT','L3',"
                        + "'ROLE_ANALYST','PENDING',NULL,NULL,'赵安全',?)",
                db.scalar("SELECT id FROM md_table WHERE name='ods_customer'"), LocalDateTime.now().minusHours(6));
    }

    private void access() {
        Object[][] applies = {
                {"wangwu", "TABLE", "dwd_customer_dim", "经营分析需要按客户维度下钻", 30, "APPROVED", "赵安全"},
                {"liuqin", "TABLE", "ods_payment", "质量稽核需要核对支付流水", 90, "APPROVED", "赵安全"},
                {"zhangsan", "TABLE", "ods_gov_population", "政务人口数据接入开发", 15, "PENDING", null},
                {"consultant", "TABLE", "ods_device_metric", "行业模板落地验证", 7, "REJECTED", "赵安全"}
        };
        for (Object[] a : applies) {
            long id = db.insert("INSERT INTO sec_access_apply(tenant_id,applicant,asset_type,asset_id,asset_name,reason,duration_days,"
                            + "status,approver,approve_time,create_time) VALUES(1,?,?,?,?,?,?,?,?,?,?)",
                    a[0], a[1], db.scalar("SELECT id FROM md_table WHERE name=?", a[2]), a[2], a[3], a[4],
                    a[5], a[6], "APPROVED".equals(a[5]) ? LocalDateTime.now().minusDays(2) : null,
                    LocalDateTime.now().minusDays(3));
            if ("APPROVED".equals(a[5])) {
                db.insert("INSERT INTO sec_access_grant(tenant_id,apply_id,subject,asset_name,scope,expire_time,status) "
                                + "VALUES(1,?,?,?,'READ',?, 'ACTIVE')",
                        id, a[0], a[2], LocalDateTime.now().plusDays((Integer) a[4]));
            }
        }
    }

    private void alerts() {
        Object[][] r = {
                {"夜间批量导出敏感数据", "export.row.count", ">5000", 60, "HIGH", "站内信+邮件"},
                {"非白名单 IP 访问客户表", "access.ip.whitelist", "violate", 5, "CRITICAL", "站内信+短信"},
                {"脱敏策略被关闭", "mask.policy.disable", ">0", 10, "HIGH", "站内信"},
                {"同一账号连续登录失败", "login.fail.count", ">5", 5, "MEDIUM", "邮件"}
        };
        for (Object[] x : r) {
            db.insert("INSERT INTO sec_alert_rule(name,metric,threshold,window_min,severity,notify_channel,status) "
                    + "VALUES(?,?,?,?,?,?,'ENABLED')", x[0], x[1], x[2], x[3], x[4], x[5]);
        }
        Object[][] alerts = {
                {"SENSITIVE_EXPORT", "检测到夜间批量导出客户敏感字段", "账号 wangwu 于 02:14 通过 /open/v1/customer/profile 连续拉取 6,200 条含手机号记录", "HIGH", "OPEN", null},
                {"MASK_POLICY_OFF", "ods_customer.phone 脱敏策略曾短暂失效", "策略状态在 10:22-10:26 间为 DISABLED，已自动恢复", "HIGH", "HANDLED", "赵安全"},
                {"ABNORMAL_IP", "异常 IP 访问政务人口表", "IP 203.0.113.77 首次访问 ods_gov_population，已加入观察名单", "MEDIUM", "OPEN", null},
                {"LOGIN_FAIL", "账号 liuqin 连续登录失败 6 次", "来源 IP 200.10.3.88，疑似口令暴力尝试", "MEDIUM", "HANDLED", "赵安全"}
        };
        for (Object[] a : alerts) {
            boolean handled = "HANDLED".equals(a[4]);
            db.insert("INSERT INTO sec_alert(tenant_id,alert_time,alert_type,title,detail,severity,status,handler,handle_time) "
                            + "VALUES(1,?,?,?,?,?,?,?,?)",
                    LocalDateTime.now().minusHours(3 + (int) (Math.random() * 40)), a[0], a[1], a[2], a[3], a[4],
                    handled ? a[5] : null, handled ? LocalDateTime.now().minusHours(2) : null);
        }
    }
}
