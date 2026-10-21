package com.dgp.admin;

import com.dgp.common.Db;
import com.dgp.common.M;
import com.dgp.common.Result;
import com.dgp.security.RequirePerm;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/** 治理概览（门户首页）：跨域 KPI、趋势与待办聚合。 */
@RestController
@RequestMapping("/api/v1/overview")
public class OverviewController {

    private final Db db;

    public OverviewController(Db db) {
        this.db = db;
    }

    @GetMapping("/dashboard")
    @RequirePerm("metadata:overview:view")
    public Result<Object> dashboard() {
        // 该接口一次要发 30 条聚合 SQL，是高并发下的首要瓶颈；看板允许 15s 级延迟，故整体缓存。
        // 任何写操作都不影响"看板数据必须分秒不差"的验收口径：关键写接口会 evict("overview")。
        Object cached = com.dgp.common.TtlCache.get("overview:dashboard:" + com.dgp.security.AuthContext.tenantId(), 15_000,
                () -> M.of(
                        "kpi", M.of(
                                "tableCount", db.count("SELECT COUNT(*) FROM md_table"),
                                "columnCount", db.count("SELECT COUNT(*) FROM md_column"),
                                "totalRows", db.scalar("SELECT COALESCE(SUM(row_count),0) FROM md_table"),
                                "datasourceCount", db.count("SELECT COUNT(*) FROM md_datasource"),
                                "qualityScore", db.scalar("SELECT ROUND(AVG(quality_score),2) FROM md_table WHERE quality_score>0"),
                                "ruleCount", db.count("SELECT COUNT(*) FROM qa_rule"),
                                "issueOpen", db.count("SELECT COUNT(*) FROM qa_issue WHERE status NOT IN ('CLOSED','IGNORED')"),
                                "issueClosed", db.count("SELECT COUNT(*) FROM qa_issue WHERE status='CLOSED'"),
                                "apiCount", db.count("SELECT COUNT(*) FROM svc_api WHERE status='PUBLISHED'"),
                                "callCount", db.count("SELECT COUNT(*) FROM svc_call_log"),
                                "jobCount", db.count("SELECT COUNT(*) FROM int_job WHERE job_type<>'WORKFLOW'"),
                                "sensitiveColumns", db.count("SELECT COUNT(*) FROM sec_sensitive_result"),
                                "assetValue", db.scalar("SELECT ROUND(COALESCE(SUM(value_score),0),2) FROM ast_catalog"),
                                "packageCount", db.count("SELECT COUNT(*) FROM ind_package")),
                        "qualityTrend", db.list("SELECT stat_date, ROUND(AVG(total_score),2) AS score FROM qa_score "
                                + "WHERE stat_date>=? GROUP BY stat_date ORDER BY stat_date", LocalDate.now().minusDays(14)),
                        // 修正：原实现读 svc_call_stat（只有种子数据写入），真实调用趋势永远不变
                        "callTrend", db.list("SELECT CAST(call_time AS DATE) AS stat_date, COUNT(*) AS calls, "
                                + "SUM(CASE WHEN status_code>=400 THEN 1 ELSE 0 END) AS fails FROM svc_call_log "
                                + "WHERE call_time>=? GROUP BY CAST(call_time AS DATE) ORDER BY CAST(call_time AS DATE)",
                                LocalDate.now().minusDays(14).atStartOfDay()),
                        "byDimension", db.list("SELECT dimension, ROUND(AVG(last_pass_rate),2) AS pass_rate, COUNT(*) AS rules "
                                + "FROM qa_rule GROUP BY dimension"),
                        "byDomain", db.list("SELECT domain AS name, COUNT(*) AS cnt FROM md_table GROUP BY domain"),
                        "byLayer", db.list("SELECT layer AS name, COUNT(*) AS cnt FROM md_table GROUP BY layer"),
                        "issueByStatus", db.list("SELECT status, COUNT(*) AS cnt FROM qa_issue GROUP BY status"),
                        "recentIssues", db.list("SELECT id,code,rule_name,table_name,severity,status,assignee,discover_time "
                                + "FROM qa_issue ORDER BY id DESC LIMIT 6"),
                        "recentJobs", db.list("SELECT job_name,status,start_time,duration_ms,rows_written FROM int_job_instance ORDER BY id DESC LIMIT 6"),
                        "recentAlerts", db.list("SELECT id,title,severity,status,alert_time FROM sec_alert ORDER BY id DESC LIMIT 4"),
                        "alerts", M.of(
                                "pendingApprovals", db.count("SELECT COUNT(*) FROM mdl_publish_flow WHERE status='PENDING'"),
                                "pendingPolicies", db.count("SELECT COUNT(*) FROM sec_mask_policy WHERE status='PENDING'"),
                                "overdueIssues", db.count("SELECT COUNT(*) FROM qa_issue WHERE status='OVERDUE'"),
                                "failedJobs", db.count("SELECT COUNT(*) FROM int_job_instance WHERE status='FAILED'"),
                                "openAlerts", db.count("SELECT COUNT(*) FROM sec_alert WHERE status='OPEN'")),
                        "industryContext", db.list("SELECT code,name FROM ind_industry"),
                        "collectHealth", M.of(
                                "coverage", db.scalar("SELECT ROUND(COUNT(DISTINCT datasource_id)*100.0/(SELECT COUNT(*) FROM md_datasource),2) FROM md_collect_task"),
                                "lastCollect", db.scalar("SELECT MAX(end_time) FROM md_collect_instance"))));
        return Result.ok(cached);
    }
}
