package com.dgp.report;

import com.dgp.common.Db;
import com.dgp.common.Ids;
import com.dgp.common.JsonUtil;
import com.dgp.common.M;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 报告生成服务（对应 dgp-report 的本地实现）。
 *
 * <p>生成 HTML / CSV / Markdown 三种格式；docx/pdf 需 POI/OpenPDF，本环境未引入，
 * 以 HTML（可用 Word 直接打开）与 CSV 交付，差异登记于 README。</p>
 *
 * <p>修正点：所有入库数据做 HTML 转义与 CSV 转义（防存储型 XSS / 公式注入）；
 * scope 参数真正参与取数；文件先写临时文件再原子替换；downloadUrl 与实际接口一致。</p>
 */
@Service
public class ReportService {

    private final Db db;
    private final Path reportDir = Paths.get("data", "reports");

    public ReportService(Db db) {
        this.db = db;
    }

    public Map<String, Object> generateQualityReport(String scope, String period, String creator) {
        return generateQualityReport(scope, period, null, creator);
    }

    public Map<String, Object> generateQualityReport(String scope, String period, String title, String creator) {
        List<Map<String, Object>> tables = loadTables(scope);
        double avg = 0;
        int counted = 0;
        for (Map<String, Object> t : tables) {
            Double v = Db.num(t.get("qualityScore"));
            if (v != null && v > 0) {
                avg += v;
                counted++;
            }
        }
        double finalAvg = counted == 0 ? 0 : Math.round(avg / counted * 100) / 100d;
        List<Map<String, Object>> dims = db.list("SELECT dimension, ROUND(AVG(last_pass_rate),2) AS pass_rate, COUNT(*) AS rules "
                + "FROM qa_rule WHERE last_pass_rate IS NOT NULL GROUP BY dimension");
        List<Map<String, Object>> topIssues = db.list(
                "SELECT rule_name,table_name,column_name,severity,status,recur_cnt FROM qa_issue ORDER BY id DESC LIMIT 20");
        List<Map<String, Object>> trend = db.list(
                "SELECT stat_date, ROUND(AVG(total_score),2) AS score FROM qa_score WHERE object_type='TABLE' "
                        + "GROUP BY stat_date ORDER BY stat_date DESC LIMIT 14");
        Map<String, Object> ctx = new LinkedHashMap<>();
        ctx.put("scope", scope == null ? "全域核心表" : scope);
        String reportTitle = (title == null || title.isBlank())
                ? "数据质量分析报告（" + (period == null ? LocalDate.now().toString().substring(0, 7) : period) + "）"
                : title.trim();
        ctx.put("title", reportTitle);
        ctx.put("period", period == null ? LocalDate.now().toString().substring(0, 7) : period);
        ctx.put("avgScore", finalAvg);
        ctx.put("level", levelOf(finalAvg));
        ctx.put("tableCount", tables.size());
        ctx.put("dimensions", dims);
        ctx.put("tables", tables);
        ctx.put("topIssues", topIssues);
        ctx.put("trend", trend);

        String code = Ids.code("QRP");
        long id = db.insert("INSERT INTO qa_report(tenant_id,code,title,report_type,scope,period,total_score,score_level,context,"
                        + "status,creator,create_time) VALUES(?,?,?,'QUALITY_TABLE_REPORT',?,?,?,?,?,'GENERATED',?,?)",
                com.dgp.security.AuthContext.tenantId(), code, reportTitle,
                ctx.get("scope"), ctx.get("period"),
                BigDecimal.valueOf(finalAvg).setScale(2, RoundingMode.HALF_UP), levelOf(finalAvg),
                JsonUtil.toJson(ctx), creator, LocalDateTime.now());
        Path path = Paths.get(writeHtml(code, ctx, tables, dims, topIssues, trend));
        db.update("UPDATE qa_report SET file_key=? WHERE id=?", path.toString().replace('\\', '/'), id);
        long fileId = db.insert("INSERT INTO rpt_file(task_id,report_id,file_name,file_path,fmt,size_kb,create_time) "
                        + "VALUES(?,?,?,?,'HTML',?,?)",
                code, id, code + ".html", path.toString().replace('\\', '/'),
                (int) Math.max(1, sizeKb(path)), LocalDateTime.now());
        return M.of("reportId", id, "code", code, "score", finalAvg, "level", levelOf(finalAvg),
                "fileKey", path.toString().replace('\\', '/'), "fileId", fileId, "context", ctx);
    }

    private List<Map<String, Object>> loadTables(String scope) {
        if (scope == null || scope.isBlank()) {
            return db.list("SELECT name,cn_name,quality_score,row_count,owner,sensitive_level FROM md_table "
                    + "WHERE quality_score>0 ORDER BY quality_score ASC");
        }
        String s = scope.trim();
        return db.list("SELECT name,cn_name,quality_score,row_count,owner,sensitive_level FROM md_table "
                + "WHERE quality_score>0 AND (name=? OR domain=? OR layer=? OR name LIKE ?) ORDER BY quality_score ASC",
                s, s, s, "%" + s + "%");
    }

    private static double sizeKb(Path p) {
        try {
            return Math.max(1, (Files.size(p) + 1023) / 1024);
        } catch (IOException e) {
            return 1;
        }
    }

    private String writeHtml(String code, Map<String, Object> ctx, List<Map<String, Object>> tables,
                             List<Map<String, Object>> dims, List<Map<String, Object>> issues, List<Map<String, Object>> trend) {
        try {
            Files.createDirectories(reportDir);
            StringBuilder h = new StringBuilder();
            h.append("<!DOCTYPE html><html lang=\"zh-CN\"><head><meta charset=\"utf-8\"><title>").append(esc(ctx.get("title"))).append("</title>");
            h.append("<style>body{font-family:'Microsoft YaHei',sans-serif;margin:40px;color:#1f2937}")
                    .append("h1{font-size:26px;border-bottom:3px solid #2563eb;padding-bottom:10px}")
                    .append("h2{font-size:18px;margin-top:28px;color:#1e40af}")
                    .append("table{border-collapse:collapse;width:100%;margin:12px 0;font-size:13px}")
                    .append("th,td{border:1px solid #d1d5db;padding:7px 10px;text-align:left}")
                    .append("th{background:#eff6ff}.kpi{display:inline-block;margin-right:26px}")
                    .append(".kpi b{font-size:30px;color:#2563eb}.muted{color:#6b7280;font-size:12px}</style></head><body>");
            h.append("<h1>").append(esc(ctx.get("title"))).append("</h1>");
            h.append("<p class=\"muted\">报告编号：").append(esc(code)).append(" ｜ 统计周期：").append(esc(ctx.get("period")))
                    .append(" ｜ 编制时间：").append(esc(LocalDateTime.now().withNano(0))).append("</p>");
            h.append("<h2>一、总体评分</h2><div>");
            h.append("<div class=\"kpi\">综合得分<br><b>").append(esc(ctx.get("avgScore"))).append("</b></div>");
            h.append("<div class=\"kpi\">质量等级<br><b>").append(esc(ctx.get("level"))).append("</b></div>");
            h.append("<div class=\"kpi\">纳入评估表数<br><b>").append(esc(ctx.get("tableCount"))).append("</b></div>");
            h.append("<div class=\"kpi\">统计范围<br><b style=\"font-size:18px\">").append(esc(ctx.get("scope"))).append("</b></div>");
            h.append("</div>");
            h.append("<h2>二、维度得分</h2><table><tr><th>质量维度</th><th>规则数</th><th>平均通过率(%)</th></tr>");
            for (Map<String, Object> d : dims) {
                h.append("<tr><td>").append(esc(dimName(String.valueOf(d.get("dimension"))))).append("</td><td>")
                        .append(esc(d.get("rules"))).append("</td><td>").append(esc(d.get("passRate"))).append("</td></tr>");
            }
            h.append("</table>");
            h.append("<h2>三、数据表评分明细</h2><table><tr><th>表名</th><th>中文名</th><th>行数</th><th>评分</th><th>等级</th><th>责任人</th></tr>");
            for (Map<String, Object> t : tables) {
                double s = Db.num(t.get("qualityScore")) == null ? 0 : Db.num(t.get("qualityScore"));
                h.append("<tr><td>").append(esc(t.get("name"))).append("</td><td>").append(esc(t.get("cnName"))).append("</td><td>")
                        .append(esc(t.get("rowCount"))).append("</td><td>").append(String.format("%.2f", s)).append("</td><td>")
                        .append(levelOf(s)).append("</td><td>").append(esc(t.get("owner"))).append("</td></tr>");
            }
            h.append("</table>");
            h.append("<h2>四、问题清单（Top 20）</h2><table><tr><th>规则</th><th>表</th><th>字段</th><th>级别</th><th>状态</th><th>复发次数</th></tr>");
            for (Map<String, Object> i : issues) {
                h.append("<tr><td>").append(esc(i.get("ruleName"))).append("</td><td>").append(esc(i.get("tableName")))
                        .append("</td><td>").append(esc(i.get("columnName"))).append("</td><td>").append(esc(i.get("severity")))
                        .append("</td><td>").append(esc(statusCn(String.valueOf(i.get("status"))))).append("</td><td>")
                        .append(esc(i.get("recurCnt"))).append("</td></tr>");
            }
            h.append("</table><h2>五、趋势</h2><table><tr><th>日期</th><th>平均得分</th></tr>");
            for (int i = trend.size() - 1; i >= 0; i--) {
                Map<String, Object> t = trend.get(i);
                h.append("<tr><td>").append(esc(t.get("statDate"))).append("</td><td>").append(esc(t.get("score"))).append("</td></tr>");
            }
            h.append("</table>");
            h.append("<h2>六、整改建议</h2><ol>")
                    .append("<li>对评分低于 85 的表优先补充完整性、唯一性规则并纳入每日稽核。</li>")
                    .append("<li>关键字段（证件号、手机号、金额）必须在建模阶段绑定标准数据元。</li>")
                    .append("<li>对复发次数≥2 的规则，推动源系统在入口处增加校验，而非依赖事后稽核。</li>")
                    .append("<li>问题单闭环率纳入运营看板，超期 72 小时自动升级至数据治理管理员。</li></ol>");
            h.append("<p class=\"muted\">本报告由 DGP 数据质量引擎自动生成，数据来源为最近一次稽核实例的真实执行结果。</p>");
            h.append("</body></html>");
            return writeAtomic(code + ".html", h.toString());
        } catch (IOException e) {
            throw new IllegalStateException("报告生成失败", e);
        }
    }

    public Map<String, Object> export(long reportId, String format) {
        Map<String, Object> r = db.one("SELECT * FROM qa_report WHERE id=?", reportId);
        if (r == null) {
            throw new com.dgp.common.BizException(com.dgp.common.ErrorCode.NOT_FOUND);
        }
        String fileKey = r.get("fileKey") == null ? null : String.valueOf(r.get("fileKey"));
        String fmt = format == null ? "html" : format.toLowerCase();
        if (!"csv".equals(fmt) && !"md".equals(fmt) && !"html".equals(fmt)) {
            fmt = "html"; // 未支持的格式统一回落 HTML，保证响应里的 format 与实际产物一致
        }
        try {
            Files.createDirectories(reportDir);
            Path target;
            if ("csv".equals(fmt)) {
                StringBuilder sb = new StringBuilder("\uFEFF").append(csv(r.get("title"))).append("\n表名,中文名,行数,质量评分,等级,责任人\n");
                for (Map<String, Object> t : loadTables(null)) {
                    sb.append(csv(t.get("name"))).append(",").append(csv(t.get("cnName"))).append(",")
                            .append(csv(t.get("rowCount"))).append(",").append(csv(t.get("qualityScore"))).append(",")
                            .append(csv(levelOf(Db.num(t.get("qualityScore")) == null ? 0 : Db.num(t.get("qualityScore"))))).append(",")
                            .append(csv(t.get("owner"))).append("\n");
                }
                target = Paths.get(writeAtomic(r.get("code") + ".csv", sb.toString()));
            } else if ("md".equals(fmt)) {
                StringBuilder sb = new StringBuilder("# ").append(mdText(r.get("title"))).append("\n\n")
                        .append("- 报告编号：").append(mdText(r.get("code"))).append("\n- 统计周期：").append(mdText(r.get("period")))
                        .append("\n- 综合得分：").append(mdText(r.get("totalScore"))).append("（").append(mdText(r.get("scoreLevel"))).append("）\n\n")
                        .append("## 表评分明细\n\n| 表名 | 评分 | 等级 |\n|---|---|---|\n");
                for (Map<String, Object> t : loadTables(null)) {
                    double s = Db.num(t.get("qualityScore")) == null ? 0 : Db.num(t.get("qualityScore"));
                    sb.append("| ").append(mdText(t.get("name"))).append(" | ").append(String.format("%.2f", s)).append(" | ")
                            .append(levelOf(s)).append(" |\n");
                }
                target = Paths.get(writeAtomic(r.get("code") + ".md", sb.toString()));
            } else {
                target = safeReportPath(fileKey);
                if (target == null || !Files.exists(target)) {
                    Map<String, Object> ctx = JsonUtil.parse(String.valueOf(r.get("context")));
                    target = Paths.get(writeHtml(String.valueOf(r.get("code")), ctx,
                            loadTables(null),
                            db.list("SELECT dimension, ROUND(AVG(last_pass_rate),2) AS pass_rate, COUNT(*) AS rules FROM qa_rule GROUP BY dimension"),
                            db.list("SELECT * FROM qa_issue ORDER BY id DESC LIMIT 20"),
                            db.list("SELECT stat_date, ROUND(AVG(total_score),2) AS score FROM qa_score GROUP BY stat_date ORDER BY stat_date DESC LIMIT 14")));
                }
            }
            long size = Math.max(1, (Files.size(target) + 1023) / 1024);
            long fileId = db.insert("INSERT INTO rpt_file(task_id,report_id,file_name,file_path,fmt,size_kb,create_time) VALUES(?,?,?,?,?,?,?)",
                    r.get("code"), reportId, target.getFileName().toString(), target.toString().replace('\\', '/'),
                    fmt.toUpperCase(), (int) size, LocalDateTime.now());
            db.insert("INSERT INTO rpt_export_log(report_id,fmt,operator,ts,file_path) VALUES(?,?,?,?,?)",
                    reportId, fmt, com.dgp.security.AuthContext.username(), LocalDateTime.now(),
                    target.toString().replace('\\', '/'));
            return M.of("reportId", reportId, "format", fmt, "fileName", target.getFileName().toString(),
                    "filePath", target.toString().replace('\\', '/'), "fileId", fileId, "sizeKb", size,
                    // 修正：原实现返回 /report/files/{文件名}，而下载接口是 /files/{id}
                    "downloadUrl", "/api/v1/report/files/" + fileId + "/download");
        } catch (IOException e) {
            throw new IllegalStateException("报告导出失败", e);
        }
    }

    /** 只允许访问 data/reports 目录内的文件，阻断路径穿越。 */
    public Path safeReportPath(String filePath) {
        if (filePath == null || filePath.isBlank()) return null;
        try {
            Path base = reportDir.toAbsolutePath().normalize();
            Path p = Paths.get(filePath).toAbsolutePath().normalize();
            return p.startsWith(base) ? p : null;
        } catch (Exception e) {
            return null;
        }
    }

    private String writeAtomic(String fileName, String content) throws IOException {
        Files.createDirectories(reportDir);
        Path tmp = reportDir.resolve(fileName + ".tmp");
        Path target = reportDir.resolve(fileName);
        Files.writeString(tmp, content, StandardCharsets.UTF_8);
        try {
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (Exception e) {
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
        }
        return target.toString().replace('\\', '/');
    }

    /** HTML 转义：报告内容全部来自库表字段（表名/中文名/责任人/规则名均可被用户写入）。 */
    public static String esc(Object o) {
        if (o == null) return "-";
        return String.valueOf(o)
                .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }

    /** CSV 转义 + 公式注入防护。 */
    private static String csv(Object o) {
        if (o == null) return "";
        String s = String.valueOf(o);
        if (!s.isEmpty() && "=+-@".indexOf(s.charAt(0)) >= 0) s = "'" + s;
        return "\"" + s.replace("\"", "\"\"") + "\"";
    }

    private static String mdText(Object o) {
        if (o == null) return "-";
        return String.valueOf(o).replace("|", "\\|").replace("\n", " ");
    }

    public static String levelOf(double score) {
        if (score >= 95) return "优秀";
        if (score >= 85) return "良好";
        if (score >= 70) return "一般";
        if (score >= 60) return "较差";
        return "差";
    }

    private static String dimName(String d) {
        return switch (d) {
            case "COMPLETENESS" -> "完整性";
            case "UNIQUENESS" -> "唯一性";
            case "ACCURACY" -> "准确性";
            case "VALIDITY" -> "有效性";
            case "CONSISTENCY" -> "一致性";
            case "TIMELINESS" -> "时效性";
            default -> d;
        };
    }

    private static String statusCn(String s) {
        return switch (s) {
            case "DISCOVERED" -> "待派单";
            case "DISPATCHED" -> "已派单";
            case "PROCESSING" -> "处理中";
            case "REVIEWING" -> "待复核";
            case "CLOSED" -> "已关闭";
            case "OVERDUE" -> "已超期";
            case "IGNORED" -> "已忽略";
            case "RECURRED" -> "已复发";
            default -> s;
        };
    }

    public List<Map<String, Object>> files(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return db.list("SELECT * FROM rpt_file ORDER BY id DESC LIMIT 50");
        }
        return db.list("SELECT * FROM rpt_file WHERE file_name LIKE ? ORDER BY id DESC LIMIT 50", "%" + keyword + "%");
    }

    public List<Map<String, Object>> trend(int days) {
        return db.list("SELECT stat_date, ROUND(AVG(total_score),2) AS score FROM qa_score WHERE stat_date>=? "
                + "GROUP BY stat_date ORDER BY stat_date", LocalDate.now().minusDays(days));
    }

    public List<Map<String, Object>> dirFiles() {
        return new ArrayList<>();
    }
}
