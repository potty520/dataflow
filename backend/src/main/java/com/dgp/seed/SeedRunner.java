package com.dgp.seed;

import com.dgp.common.Db;
import com.dgp.metadata.service.MetaCollectService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/** 演示数据装载（幂等：已存在用户则跳过）。 */
@Component
@Order(1)
public class SeedRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(SeedRunner.class);

    private final Db db;
    private final MetaSeeder metaSeeder;
    private final SourceDataSeeder sourceDataSeeder;
    private final ModelSeeder modelSeeder;
    private final IntegrationSeeder integrationSeeder;
    private final QualitySeeder qualitySeeder;
    private final SecuritySeeder securitySeeder;
    private final ServiceSeeder serviceSeeder;
    private final AssetSeeder assetSeeder;
    private final IndustrySeeder industrySeeder;
    private final StreamMigSeeder streamMigSeeder;
    private final MetaCollectService collectService;

    @Value("${dgp.seed-demo-data:true}")
    private boolean enabled;

    public SeedRunner(Db db, MetaSeeder metaSeeder, SourceDataSeeder sourceDataSeeder, ModelSeeder modelSeeder,
                      IntegrationSeeder integrationSeeder, QualitySeeder qualitySeeder, SecuritySeeder securitySeeder,
                      ServiceSeeder serviceSeeder, AssetSeeder assetSeeder, IndustrySeeder industrySeeder,
                      StreamMigSeeder streamMigSeeder, MetaCollectService collectService) {
        this.db = db;
        this.metaSeeder = metaSeeder;
        this.sourceDataSeeder = sourceDataSeeder;
        this.modelSeeder = modelSeeder;
        this.integrationSeeder = integrationSeeder;
        this.qualitySeeder = qualitySeeder;
        this.securitySeeder = securitySeeder;
        this.serviceSeeder = serviceSeeder;
        this.assetSeeder = assetSeeder;
        this.industrySeeder = industrySeeder;
        this.streamMigSeeder = streamMigSeeder;
        this.collectService = collectService;
    }

    @Override
    public void run(String... args) {
        // 权限/组织/字典修复与演示数据开关无关，始终幂等执行
        try {
            metaSeeder.syncRolePerms();
            metaSeeder.seedOrgAndDict();
            // 流式质检/数据迁移演示数据：幂等，已有库升级时也会补种
            streamMigSeeder.seed();
        } catch (Exception e) {
            log.warn("内置角色权限/基础数据同步失败：{}", e.getMessage());
        }
        if (!enabled) {
            log.info("演示数据装载已关闭（dgp.seed-demo-data=false）");
            return;
        }
        // 用完成标记判定，避免"种子中途失败 → 下次启动因 sys_user>0 静默跳过"造成半装载状态
        Object marker = db.scalar("SELECT config_value FROM sys_config WHERE config_key='seed.completed'");
        if (marker != null) {
            log.info("检测到 seed.completed 标记，跳过演示数据装载");
            return;
        }
        long userCount = db.count("SELECT COUNT(*) FROM sys_user");
        if (userCount > 0) {
            log.error("检测到未完成的半装载数据（sys_user={} 但缺少 seed.completed 标记）。"
                    + "请清空业务库后重启，本次跳过装载以免产生重复数据。", userCount);
            return;
        }
        long t0 = System.currentTimeMillis();
        log.info("开始装载治理平台演示数据 ...");
        metaSeeder.seed();
        sourceDataSeeder.seed();
        Object dsObj = db.scalar("SELECT id FROM md_datasource WHERE code='DS-H2-LAKE'");
        if (dsObj == null) {
            log.warn("未找到内置 H2 数据源，跳过首次元数据采集");
        } else {
            collectService.collect(((Number) dsObj).longValue(), "AUTO", "system");
        }
        integrationSeeder.seed();
        modelSeeder.seed();
        qualitySeeder.seed();
        securitySeeder.seed();
        serviceSeeder.seed();
        assetSeeder.seed();
        industrySeeder.seed();
        reportTemplates();
        // 演示数据装载完成后再次同步，确保角色权限是最新定义
        metaSeeder.syncRolePerms();
        db.update("DELETE FROM sys_config WHERE config_key='seed.completed'");
        db.insert("INSERT INTO sys_config(config_key,config_value,description,update_time) VALUES('seed.completed','1','演示数据装载完成标记',?)",
                LocalDateTime.now());
        log.info("演示数据装载完成，耗时 {} ms；表 {} 张、列 {} 个、规则 {} 条、稽核结果 {} 条、问题单 {} 张",
                System.currentTimeMillis() - t0,
                db.count("SELECT COUNT(*) FROM md_table"),
                db.count("SELECT COUNT(*) FROM md_column"),
                db.count("SELECT COUNT(*) FROM qa_rule"),
                db.count("SELECT COUNT(*) FROM qa_check_result"),
                db.count("SELECT COUNT(*) FROM qa_issue"));
    }

    private void reportTemplates() {
        if (db.count("SELECT COUNT(*) FROM rpt_template") > 0) return;
        String[][] tpl = {
                {"QUALITY_TABLE_REPORT", "数据质量分析报告", "QUALITY", "全域", "HTML",
                        "封面,评分总览,维度分析,TopN问题,规则明细,整改建议"},
                {"QUALITY_MONTHLY", "数据质量月报", "QUALITY", "GOV", "HTML",
                        "月度概览,趋势对比,闭环率,复发分析,下月计划"},
                {"ASSET_OPS_REPORT", "数据资产运营报告", "ASSET", "GOV", "HTML",
                        "资产总览,热度榜,服务调用,价值评估"},
                {"REG_REPORT_CHECK", "监管报送质量核查报告", "QUALITY", "FINANCE", "HTML",
                        "报送范围,校验结果,差异明细,溯源路径"}
        };
        for (String[] t : tpl) {
            db.insert("INSERT INTO rpt_template(code,name,report_type,industry,engine,content,status,create_time) "
                    + "VALUES(?,?,?,?,?,?,'ENABLED',?)", t[0], t[1], t[2], t[3], t[4],
                    "{\"sections\":[" + quoteJoin(t[5]) + "]}", LocalDateTime.now().minusDays(15));
        }
    }

    private String quoteJoin(String csv) {
        StringBuilder sb = new StringBuilder();
        String[] parts = csv.split(",");
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) sb.append(",");
            sb.append("\"").append(parts[i]).append("\"");
        }
        return sb.toString();
    }
}
