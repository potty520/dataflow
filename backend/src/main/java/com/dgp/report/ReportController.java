package com.dgp.report;

import com.dgp.common.*;
import com.dgp.security.AuthContext;
import com.dgp.security.RequirePerm;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** dgp-report:8092：报表模板、报告生成任务与文件下载。 */
@RestController
@RequestMapping("/api/v1/report")
public class ReportController {

    private final Db db;
    private final ReportService reportService;

    public ReportController(Db db, ReportService reportService) {
        this.db = db;
        this.reportService = reportService;
    }

    @GetMapping("/templates")
    @RequirePerm("report:template:view")
    public Result<Object> templates() {
        return Result.ok(M.of("records", db.list("SELECT * FROM rpt_template ORDER BY id")));
    }

    @PostMapping("/templates")
    @RequirePerm("report:template:edit")
    public Result<Object> createTemplate(@RequestBody Map<String, Object> b) {
        long id = db.insert("INSERT INTO rpt_template(code,name,report_type,industry,engine,content,status,create_time) "
                        + "VALUES(?,?,?,?,?,?,'ENABLED',?)",
                b.getOrDefault("code", Ids.code("TPL")), b.get("name"), b.getOrDefault("reportType", "QUALITY"),
                b.getOrDefault("industry", "GOV"), b.getOrDefault("engine", "HTML"),
                JsonUtil.toJson(b.getOrDefault("sections", List.of())), LocalDateTime.now());
        return Result.ok(M.of("id", id));
    }

    @PostMapping("/generate")
    @RequirePerm("report:generate")
    public Result<Object> generate(@RequestBody Map<String, Object> b) {
        String templateCode = String.valueOf(b.getOrDefault("templateCode", "QUALITY_TABLE_REPORT"));
        String title = b.get("title") == null ? "数据质量报告" : String.valueOf(b.get("title"));
        String taskId = Ids.code("RT");
        Map<String, Object> gen = reportService.generateQualityReport(Req.str(b.get("scope")), Req.str(b.get("period")),
                title, AuthContext.username());
        // formats 支持 html/csv/md，逐个真实导出并落 rpt_file
        List<String> formats = new java.util.ArrayList<>();
        Object f = b.get("formats");
        if (f instanceof List<?> l) {
            for (Object o : l) formats.add(String.valueOf(o).toLowerCase());
        } else if (f != null && !String.valueOf(f).isBlank()) {
            for (String s : String.valueOf(f).split(",")) if (!s.isBlank()) formats.add(s.trim().toLowerCase());
        }
        if (formats.isEmpty()) formats.add("html");
        List<Map<String, Object>> exported = new java.util.ArrayList<>();
        for (String fmt : formats) {
            try {
                exported.add(reportService.export(((Number) gen.get("reportId")).longValue(), fmt));
            } catch (Exception e) {
                exported.add(M.of("format", fmt, "error", "导出失败"));
            }
        }
        db.insert("INSERT INTO rpt_task(task_id,template_code,title,formats,status,start_time,end_time,result,operator) "
                        + "VALUES(?,?,?,?,'SUCCESS',?,?,?,?)",
                taskId, templateCode, title, String.join(",", formats), LocalDateTime.now(), LocalDateTime.now(),
                "报告编号 " + gen.get("code"), AuthContext.username());
        return Result.ok(M.of("taskId", taskId, "status", "SUCCESS", "reportId", gen.get("reportId"),
                "score", gen.get("score"), "fileId", gen.get("fileId"), "fileKey", gen.get("fileKey"),
                "formats", formats, "files", exported));
    }

    @GetMapping("/tasks/{taskId}")
    @RequirePerm("report:template:view")
    public Result<Object> task(@PathVariable String taskId) {
        Map<String, Object> t = db.one("SELECT * FROM rpt_task WHERE task_id=?", taskId);
        if (t == null) throw new BizException(ErrorCode.NOT_FOUND);
        return Result.ok(M.of("task", t, "files", db.list("SELECT * FROM rpt_file WHERE task_id=?", taskId)));
    }

    @GetMapping("/tasks/{taskId}/files")
    @RequirePerm("report:download")
    public Result<Object> taskFiles(@PathVariable String taskId) {
        return Result.ok(M.of("records", db.list("SELECT * FROM rpt_file WHERE task_id=?", taskId)));
    }

    @GetMapping("/files/{id}/download")
    @RequirePerm("report:download")
    public ResponseEntity<Resource> download(@PathVariable long id) {
        Map<String, Object> f = db.one("SELECT * FROM rpt_file WHERE id=?", id);
        if (f == null) throw new BizException(ErrorCode.NOT_FOUND);
        Path path = reportService.safeReportPath(String.valueOf(f.get("filePath")));
        if (path == null) {
            // 修正：拒绝访问 data/reports 之外的路径（原实现直接按库中路径读任意文件）
            throw new BizException(ErrorCode.FORBIDDEN, "文件路径非法，已拒绝访问");
        }
        if (!path.toFile().exists()) {
            throw new BizException(ErrorCode.NOT_FOUND, "报告文件已丢失，请重新生成");
        }
        String fn = path.getFileName().toString();
        MediaType type = "HTML".equalsIgnoreCase(String.valueOf(f.get("fmt"))) ? MediaType.TEXT_HTML
                : "CSV".equalsIgnoreCase(String.valueOf(f.get("fmt"))) ? MediaType.parseMediaType("text/csv")
                : MediaType.APPLICATION_OCTET_STREAM;
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fn.replace("\"", "") + "\"")
                .header("X-Content-Type-Options", "nosniff")
                .contentType(type)
                .body(new FileSystemResource(path));
    }

    @GetMapping("/stats/overview")
    @RequirePerm("report:template:view")
    public Result<Object> stats() {
        return Result.ok(M.of("templateCount", db.count("SELECT COUNT(*) FROM rpt_template"),
                "taskCount", db.count("SELECT COUNT(*) FROM rpt_task"),
                "fileCount", db.count("SELECT COUNT(*) FROM rpt_file"),
                "recentTasks", db.list("SELECT * FROM rpt_task ORDER BY id DESC LIMIT 10")));
    }

    @GetMapping("/files")
    @RequirePerm("report:template:view")
    public Result<Object> files(@RequestParam(required = false) String keyword) {
        return Result.ok(M.of("records", reportService.files(keyword)));
    }
}
