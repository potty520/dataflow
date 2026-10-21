package com.dgp.metadata;

import com.dgp.common.*;
import com.dgp.metadata.service.LineageService;
import com.dgp.metadata.service.MetaCollectService;
import com.dgp.security.AuthContext;
import com.dgp.security.RequirePerm;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** dgp-metadata:8082：数据源、元数据检索与维护、采集、血缘、术语。 */
@RestController
@RequestMapping("/api/v1/metadata")
public class MetadataController {

    private final Db db;
    private final MetaCollectService collectService;
    private final LineageService lineageService;

    @Value("${dgp.datasource.allow-private-probe:true}")
    private boolean allowPrivateProbe;

    public MetadataController(Db db, MetaCollectService collectService, LineageService lineageService) {
        this.db = db;
        this.collectService = collectService;
        this.lineageService = lineageService;
    }

    // ================= 数据源 =================
    @GetMapping("/datasources")
    @RequirePerm("metadata:datasource:view")
    public Result<Object> datasources(@RequestParam(required = false) String keyword,
                                      @RequestParam(required = false) String dtype) {
        StringBuilder where = new StringBuilder(" WHERE 1=1");
        List<Object> args = new ArrayList<>();
        if (!Req.blank(keyword)) {
            where.append(" AND (name LIKE ? OR code LIKE ?)");
            args.add("%" + keyword + "%");
            args.add("%" + keyword + "%");
        }
        if (!Req.blank(dtype)) {
            where.append(" AND dtype=?");
            args.add(dtype);
        }
        List<Map<String, Object>> list = db.list("SELECT * FROM md_datasource" + where + " ORDER BY id", args.toArray());
        for (Map<String, Object> d : list) {
            // 敏感字段不下发（原实现 SELECT * 会把 password_enc 一并返回）
            d.remove("passwordEnc");
            d.put("tableCount", db.count("SELECT COUNT(*) FROM md_table WHERE datasource_id=?", d.get("id")));
        }
        return Result.ok(M.of("records", list, "total", list.size()));
    }

    @PostMapping("/datasources")
    @RequirePerm("metadata:datasource:create")
    public Result<Object> createDatasource(@RequestBody Map<String, Object> b) {
        String code = Req.str(b.get("code"));
        String name = Req.str(b.get("name"));
        if (Req.blank(code) || Req.blank(name)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "数据源编码与名称不能为空");
        }
        if (db.one("SELECT id FROM md_datasource WHERE code=?", code) != null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "数据源编码已存在：" + code);
        }
        long id = db.insert("INSERT INTO md_datasource(tenant_id,code,name,dtype,host,port,db_name,username,industry,domain,owner,"
                        + "conn_status,collect_mode,remark,create_time) VALUES(?,?,?,?,?,?,?,?,?,?,?,'未测试','AUTO',?,?)",
                AuthContext.tenantId(), code, name, b.getOrDefault("dtype", "H2"), b.get("host"), b.get("port"),
                b.get("dbName"), b.get("username"), b.getOrDefault("industry", "GOV"), b.get("domain"), b.get("owner"),
                b.get("remark"), LocalDateTime.now());
        return Result.ok(M.of("id", id));
    }

    @PutMapping("/datasources/{id}")
    @RequirePerm("metadata:datasource:edit")
    public Result<Object> updateDatasource(@PathVariable long id, @RequestBody Map<String, Object> b) {
        Map<String, Object> before = db.one("SELECT * FROM md_datasource WHERE id=?", id);
        if (before == null) throw new BizException(ErrorCode.NOT_FOUND, "数据源不存在");
        // 部分更新：未提供的字段保持原值（原实现缺字段会写 NULL，把 host/port/db_name 清空）
        db.update("UPDATE md_datasource SET name=?,dtype=?,host=?,port=?,db_name=?,username=?,owner=?,remark=? WHERE id=?",
                pick(b, before, "name"), pick(b, before, "dtype"), pick(b, before, "host"), pick(b, before, "port"),
                pick(b, before, "dbName"), pick(b, before, "username"), pick(b, before, "owner"),
                pick(b, before, "remark"), id);
        return Result.ok(M.of("id", id));
    }

    @DeleteMapping("/datasources/{id}")
    @RequirePerm("metadata:datasource:delete")
    public Result<Object> deleteDatasource(@PathVariable long id) {
        if (db.count("SELECT COUNT(*) FROM md_table WHERE datasource_id=?", id) > 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "数据源下仍有元数据对象，禁止删除");
        }
        db.update("DELETE FROM md_datasource WHERE id=?", id);
        return Result.ok(M.of("deleted", true));
    }

    /** 真实连接测试：H2 执行探针 SQL，其他类型做 TCP 连通性探测并记录时延。 */
    @PostMapping("/datasources/{id}/test")
    @RequirePerm("metadata:datasource:test")
    public Result<Object> test(@PathVariable long id) {
        Map<String, Object> d = db.one("SELECT * FROM md_datasource WHERE id=?", id);
        if (d == null) throw new BizException(ErrorCode.NOT_FOUND);
        long start = System.currentTimeMillis();
        String status;
        String message;
        Integer latency = null;
        if ("H2".equals(String.valueOf(d.get("dtype")))) {
            try {
                db.scalar("SELECT 1");
                latency = (int) (System.currentTimeMillis() - start);
                status = "已连接";
                message = "平台内置 H2 数据湖探针执行成功（本地进程内连接）";
            } catch (Exception e) {
                status = "异常";
                message = "探针失败：" + e.getClass().getSimpleName();
            }
        } else {
            String host = String.valueOf(d.get("host"));
            int port = Req.toInt(d.get("port"), 0);
            String blocked = probeBlockedReason(host);
            if (port <= 0) {
                status = "异常";
                message = "端口未配置";
            } else if (blocked != null) {
                status = "异常";
                message = blocked;
            } else {
                try (Socket socket = new Socket()) {
                    socket.connect(new InetSocketAddress(host, port), 1200);
                    latency = (int) (System.currentTimeMillis() - start);
                    status = "已连接";
                    message = "TCP " + host + ":" + port + " 可达（仅连通性探测，未校验账号权限）";
                } catch (Exception e) {
                    status = "异常";
                    message = "TCP " + host + ":" + port + " 不可达（" + e.getClass().getSimpleName() + "）";
                }
            }
        }
        db.update("UPDATE md_datasource SET conn_status=?,last_test_time=?,latency_ms=? WHERE id=?",
                status, LocalDateTime.now(), latency, id);
        return Result.ok(M.of("id", id, "status", status, "latencyMs", latency, "message", message, "testTime", LocalDateTime.now()));
    }

    /**
     * 数据源探测的 SSRF 收口：云元数据地址一律拒绝；私网/回环是否允许由配置决定。
     */
    private String probeBlockedReason(String host) {
        if (Req.blank(host)) return "主机地址为空";
        String h = host.trim().toLowerCase();
        if ("169.254.169.254".equals(h) || h.startsWith("169.254.") || "metadata.google.internal".equals(h)) {
            return "出于安全策略，禁止探测云元数据地址";
        }
        boolean loopback = h.equals("localhost") || h.startsWith("127.") || "::1".equals(h) || "0.0.0.0".equals(h);
        if (loopback || isPrivate(h)) {
            if (!allowPrivateProbe) {
                return "当前配置禁止探测私网地址（dgp.datasource.allow-private-probe=false）";
            }
        }
        return null;
    }

    private static boolean isPrivate(String h) {
        return h.startsWith("10.") || h.startsWith("192.168.") || h.startsWith("172.16.")
                || h.startsWith("172.17.") || h.startsWith("172.18.") || h.startsWith("172.19.")
                || h.startsWith("172.2") || h.startsWith("172.30.") || h.startsWith("172.31.");
    }

    @GetMapping("/datasources/{id}/status")
    @RequirePerm("metadata:datasource:view")
    public Result<Object> status(@PathVariable long id) {
        Map<String, Object> d = db.one("SELECT id,name,dtype,conn_status,last_test_time,latency_ms FROM md_datasource WHERE id=?", id);
        if (d == null) throw new BizException(ErrorCode.NOT_FOUND);
        return Result.ok(d);
    }

    // ================= 检索 =================
    @GetMapping("/search")
    @RequirePerm("metadata:search")
    public Result<Object> search(@RequestParam(required = false) String keyword,
                                 @RequestParam(required = false) Long datasourceId,
                                 @RequestParam(required = false) String layer,
                                 @RequestParam(required = false) String domain,
                                 @RequestParam(required = false) String sensitiveLevel,
                                 @RequestParam(defaultValue = "1") int pageNum,
                                 @RequestParam(defaultValue = "20") int pageSize) {
        long t0 = System.currentTimeMillis();
        StringBuilder where = new StringBuilder(" WHERE 1=1");
        List<Object> args = new ArrayList<>();
        if (!Req.blank(keyword)) {
            // 修正：EXISTS 子查询含 2 个占位符，原实现只补 5 个参数 → 带 keyword 必 500
            where.append(" AND (t.name LIKE ? OR t.cn_name LIKE ? OR t.description LIKE ? OR t.tags LIKE ? OR EXISTS("
                    + "SELECT 1 FROM md_column c WHERE c.table_id=t.id AND (c.name LIKE ? OR c.cn_name LIKE ?)))");
            for (int i = 0; i < 6; i++) args.add("%" + keyword + "%");
        }
        if (datasourceId != null) {
            where.append(" AND t.datasource_id=?");
            args.add(datasourceId);
        }
        if (!Req.blank(layer)) {
            where.append(" AND t.layer=?");
            args.add(layer);
        }
        if (!Req.blank(domain)) {
            where.append(" AND t.domain=?");
            args.add(domain);
        }
        if (!Req.blank(sensitiveLevel)) {
            where.append(" AND t.sensitive_level=?");
            args.add(sensitiveLevel);
        }
        String base = "SELECT t.id,t.name,t.cn_name,t.layer,t.domain,t.obj_type,t.row_count,t.data_size_mb,t.quality_score,"
                + "t.owner,t.update_freq,t.sensitive_level,t.description,t.tags,d.name AS ds_name FROM md_table t "
                + "LEFT JOIN md_datasource d ON d.id=t.datasource_id" + where;
        PageResult p = db.page(base, "t.quality_score DESC, t.name", pageNum, pageSize, args.toArray());
        Map<String, Object> out = p.toMap();
        out.put("costMs", System.currentTimeMillis() - t0);
        return Result.ok(out);
    }

    @GetMapping("/tables")
    @RequirePerm("metadata:table:view")
    public Result<Object> tables(@RequestParam(required = false) String keyword,
                                 @RequestParam(required = false) String layer,
                                 @RequestParam(required = false) String domain,
                                 @RequestParam(defaultValue = "1") int pageNum,
                                 @RequestParam(defaultValue = "20") int pageSize) {
        StringBuilder where = new StringBuilder(" WHERE 1=1");
        List<Object> args = new ArrayList<>();
        if (!Req.blank(keyword)) {
            where.append(" AND (t.name LIKE ? OR t.cn_name LIKE ?)");
            args.add("%" + keyword + "%");
            args.add("%" + keyword + "%");
        }
        if (!Req.blank(layer)) {
            where.append(" AND t.layer=?");
            args.add(layer);
        }
        if (!Req.blank(domain)) {
            where.append(" AND t.domain=?");
            args.add(domain);
        }
        String base = "SELECT t.*, d.name AS ds_name, (SELECT COUNT(*) FROM md_column c WHERE c.table_id=t.id) AS column_count "
                + "FROM md_table t LEFT JOIN md_datasource d ON d.id=t.datasource_id" + where;
        return Result.ok(db.page(base, "t.layer, t.name", pageNum, pageSize, args.toArray()).toMap());
    }

    @GetMapping("/tables/{id}")
    @RequirePerm("metadata:table:view")
    public Result<Object> tableDetail(@PathVariable long id) {
        Map<String, Object> t = db.one("SELECT t.*, d.name AS ds_name, d.dtype FROM md_table t "
                + "LEFT JOIN md_datasource d ON d.id=t.datasource_id WHERE t.id=?", id);
        if (t == null) throw new BizException(ErrorCode.METADATA_NOT_FOUND);
        String name = String.valueOf(t.get("name"));
        List<Map<String, Object>> columns = db.list("SELECT * FROM md_column WHERE table_id=? ORDER BY column_order", id);
        Map<String, Object> out = new LinkedHashMap<>(t);
        out.put("columns", columns);
        out.put("columnCount", columns.size());
        out.put("upstream", db.list("SELECT DISTINCT src_table AS table_name, job_name FROM md_lineage_edge "
                + "WHERE dst_table=? AND granularity='TABLE'", name));
        out.put("downstream", db.list("SELECT DISTINCT dst_table AS table_name, job_name FROM md_lineage_edge "
                + "WHERE src_table=? AND granularity='TABLE'", name));
        out.put("rules", db.list("SELECT id,code,name,dimension,last_pass_rate,last_failed,severity FROM qa_rule WHERE table_name=?", name));
        out.put("issues", db.list("SELECT id,code,rule_name,status,severity,discover_time FROM qa_issue WHERE table_name=? ORDER BY id DESC LIMIT 10", name));
        out.put("sensitiveColumns", db.list("SELECT r.column_name,r.security_level,r.match_type,p.algorithm FROM sec_sensitive_result r "
                + "LEFT JOIN sec_mask_policy p ON p.table_name=r.table_name AND p.column_name=r.column_name AND p.status='ENABLED' "
                + "WHERE r.table_name=?", name));
        out.put("catalog", db.one("SELECT * FROM ast_catalog WHERE table_name=?", name));
        // 仅当表名是合法标识符时才生成样例 SQL，避免把注入载荷回显给前端
        out.put("sampleSql", Db.isIdentifier(name) ? "SELECT * FROM " + name + " LIMIT 20" : "-- 表名非法，无法生成样例");
        return Result.ok(out);
    }

    @PostMapping("/tables")
    @RequirePerm("metadata:table:create")
    public Result<Object> createTable(@RequestBody Map<String, Object> b) {
        String name = Req.str(b.get("name"));
        if (!Db.isIdentifier(name)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "表名必须为字母/数字/下划线组合，且以字母或下划线开头");
        }
        if (db.one("SELECT id FROM md_table WHERE name=?", name) != null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "表名已存在：" + name);
        }
        long id = db.insert("INSERT INTO md_table(tenant_id,datasource_id,name,cn_name,obj_type,layer,domain,owner,description,"
                        + "biz_desc,update_freq,status,tags,sensitive_level,industry,create_time) "
                        + "VALUES(?,?,?,?,?,?,?,?,?,?,?,'已发布',?,'L1',?,?)",
                AuthContext.tenantId(), Db.longVal(b.getOrDefault("datasourceId", 1L), 1L), name, b.get("cnName"),
                b.getOrDefault("objType", "TABLE"), b.getOrDefault("layer", "ODS"), b.get("domain"), b.get("owner"),
                b.get("description"), b.get("bizDesc"), b.getOrDefault("updateFreq", "每日"), b.get("tags"),
                b.getOrDefault("industry", "GOV"), LocalDateTime.now());
        db.insert("INSERT INTO md_object_version(object_type,object_id,object_name,version,snapshot,change_note,operator,create_time) "
                        + "VALUES('TABLE',?,?,'1.0.0',?,'手工登记元数据',?,?)",
                id, name, JsonUtil.toJson(b), AuthContext.username(), LocalDateTime.now());
        return Result.ok(M.of("id", id));
    }

    @PutMapping("/tables/{id}")
    @RequirePerm("metadata:table:edit")
    public Result<Object> updateTable(@PathVariable long id, @RequestBody Map<String, Object> b) {
        Map<String, Object> before = db.one("SELECT * FROM md_table WHERE id=?", id);
        if (before == null) throw new BizException(ErrorCode.METADATA_NOT_FOUND);
        db.update("UPDATE md_table SET cn_name=?,domain=?,owner=?,description=?,biz_desc=?,update_freq=?,tags=?,sensitive_level=? WHERE id=?",
                pick(b, before, "cnName"), pick(b, before, "domain"), pick(b, before, "owner"),
                pick(b, before, "description"), pick(b, before, "bizDesc"), pick(b, before, "updateFreq"),
                pick(b, before, "tags"), pick(b, before, "sensitiveLevel"), id);
        long ver = db.count("SELECT COUNT(*) FROM md_object_version WHERE object_type='TABLE' AND object_id=?", id);
        db.insert("INSERT INTO md_object_version(object_type,object_id,object_name,version,snapshot,change_note,operator,create_time) "
                        + "VALUES('TABLE',?,?,?,?,'元数据变更',?,?)",
                id, before.get("name"), "1.0." + ver, JsonUtil.toJson(b), AuthContext.username(), LocalDateTime.now());
        return Result.ok(M.of("id", id));
    }

    @DeleteMapping("/tables/{id}")
    @RequirePerm("metadata:table:delete")
    @Transactional
    public Result<Object> deleteTable(@PathVariable long id) {
        // 修正：原实现 db.scalar(sql, id, "") 多传参数 → 异常被吞 → name="null" → 引用检查永远失效
        Object nameObj = db.scalar("SELECT name FROM md_table WHERE id=?", id);
        if (nameObj == null) throw new BizException(ErrorCode.METADATA_NOT_FOUND);
        String name = String.valueOf(nameObj);
        long ref = db.count("SELECT COUNT(*) FROM svc_api WHERE sql_text LIKE ?", "%" + name + "%");
        if (ref > 0) throw new BizException(ErrorCode.MODEL_REFERENCED, "该表被 " + ref + " 个数据服务引用，禁止删除");
        // 级联清理关联元数据，避免孤儿行（原实现只删了 md_column/md_table）
        db.update("DELETE FROM md_column WHERE table_id=?", id);
        db.update("DELETE FROM md_object_version WHERE object_type='TABLE' AND object_id=?", id);
        db.update("DELETE FROM sec_sensitive_result WHERE table_name=?", name);
        db.update("DELETE FROM sec_mask_policy WHERE table_name=?", name);
        db.update("DELETE FROM md_lineage_edge WHERE src_table=? OR dst_table=?", name, name);
        db.update("DELETE FROM qa_rule WHERE table_name=?", name);
        db.update("DELETE FROM ast_catalog WHERE table_name=?", name);
        db.update("DELETE FROM md_table WHERE id=?", id);
        return Result.ok(M.of("deleted", true));
    }

    @GetMapping("/tables/{id}/columns")
    @RequirePerm("metadata:column:view")
    public Result<Object> columns(@PathVariable long id) {
        return Result.ok(M.of("records", db.list("SELECT * FROM md_column WHERE table_id=? ORDER BY column_order", id)));
    }

    @PutMapping("/columns/{id}")
    @RequirePerm("metadata:column:edit")
    public Result<Object> updateColumn(@PathVariable long id, @RequestBody Map<String, Object> b) {
        Map<String, Object> before = db.one("SELECT * FROM md_column WHERE id=?", id);
        if (before == null) throw new BizException(ErrorCode.NOT_FOUND, "字段不存在");
        db.update("UPDATE md_column SET cn_name=?,comment=?,std_code=?,sensitive_level=?,mask_algo=? WHERE id=?",
                pick(b, before, "cnName"), pick(b, before, "comment"), pick(b, before, "stdCode"),
                pick(b, before, "sensitiveLevel"), pick(b, before, "maskAlgo"), id);
        return Result.ok(M.of("id", id));
    }

    // ================= 版本 =================
    @GetMapping("/versions")
    @RequirePerm("metadata:version:view")
    public Result<Object> versions(@RequestParam String objectType, @RequestParam long objectId) {
        return Result.ok(M.of("records", db.list("SELECT * FROM md_object_version WHERE object_type=? AND object_id=? ORDER BY id DESC",
                objectType, objectId)));
    }

    @GetMapping("/versions/diff")
    @RequirePerm("metadata:version:view")
    public Result<Object> versionDiff(@RequestParam String objectType, @RequestParam long objectId,
                                      @RequestParam long versionId1, @RequestParam long versionId2) {
        Map<String, Object> v1 = db.one("SELECT * FROM md_object_version WHERE id=? AND object_type=? AND object_id=?",
                versionId1, objectType, objectId);
        Map<String, Object> v2 = db.one("SELECT * FROM md_object_version WHERE id=? AND object_type=? AND object_id=?",
                versionId2, objectType, objectId);
        if (v1 == null || v2 == null) throw new BizException(ErrorCode.NOT_FOUND, "版本不存在或不属于该对象");
        Map<String, Object> a = JsonUtil.parse(v1.get("snapshot") == null ? null : String.valueOf(v1.get("snapshot")));
        Map<String, Object> b = JsonUtil.parse(v2.get("snapshot") == null ? null : String.valueOf(v2.get("snapshot")));
        List<Map<String, Object>> diffs = new ArrayList<>();
        for (String k : b.keySet()) {
            Object oldV = a.get(k);
            Object newV = b.get(k);
            if (!String.valueOf(newV).equals(String.valueOf(oldV))) {
                diffs.add(M.of("field", k, "before", oldV, "after", newV));
            }
        }
        for (String k : a.keySet()) {
            if (!b.containsKey(k)) diffs.add(M.of("field", k, "before", a.get(k), "after", null));
        }
        return Result.ok(M.of("objectType", objectType, "objectId", objectId,
                "left", v1.get("version"), "right", v2.get("version"),
                "changes", diffs, "changeCount", diffs.size()));
    }

    // ================= 导入 =================
    @GetMapping("/import/template")
    @RequirePerm("metadata:import:view")
    public Result<Object> importTemplate() {
        return Result.ok(M.of("columns", List.of("name", "cnName", "layer", "domain", "owner", "description", "updateFreq"),
                "sample", List.of(M.of("name", "ods_demo", "cnName", "示例表", "layer", "ODS", "domain", "客户域",
                        "owner", "张三", "description", "批量导入示例", "updateFreq", "每日"))));
    }

    @PostMapping("/import")
    @RequirePerm("metadata:import:execute")
    @Transactional
    public Result<Object> importMeta(@RequestBody Map<String, Object> body) {
        Object rowsObj = body.get("rows");
        if (!(rowsObj instanceof List<?> rows)) throw new BizException(ErrorCode.PARAM_INVALID, "rows 必须为数组");
        long datasourceId = Db.longVal(body.getOrDefault("datasourceId", 1L), 1L);
        int ok = 0;
        List<String> skipped = new ArrayList<>();
        for (Object o : rows) {
            if (!(o instanceof Map<?, ?> raw)) continue;
            Map<String, Object> row = new LinkedHashMap<>();
            raw.forEach((k, v) -> row.put(String.valueOf(k), v));
            String name = Req.str(row.get("name"));
            if (!Db.isIdentifier(name)) {
                skipped.add(String.valueOf(row.get("name")) + "(表名非法)");
                continue;
            }
            if (db.one("SELECT id FROM md_table WHERE name=?", name) != null) {
                skipped.add(name + "(已存在)");
                continue;
            }
            // 修正：列与值错位（layer 被写成 'TABLE'、create_time 拿到 updateFreq）导致批量导入必 500
            db.insert("INSERT INTO md_table(tenant_id,datasource_id,name,cn_name,obj_type,layer,domain,owner,description,"
                            + "update_freq,status,sensitive_level,industry,create_time) "
                            + "VALUES(?,?,?,?,'TABLE',?,?,?,?,?,'已发布','L1','GOV',?)",
                    AuthContext.tenantId(), datasourceId, name, row.get("cnName"),
                    row.getOrDefault("layer", "ODS"), row.get("domain"), row.get("owner"), row.get("description"),
                    row.getOrDefault("updateFreq", "每日"), LocalDateTime.now());
            ok++;
        }
        return Result.ok(M.of("imported", ok, "total", rows.size(), "skipped", skipped));
    }

    // ================= 采集 =================
    @GetMapping("/collect/tasks")
    @RequirePerm("metadata:collect:view")
    public Result<Object> collectTasks() {
        List<Map<String, Object>> list = db.list("SELECT t.*, d.name AS ds_name FROM md_collect_task t "
                + "LEFT JOIN md_datasource d ON d.id=t.datasource_id ORDER BY t.id");
        return Result.ok(M.of("records", list, "total", list.size()));
    }

    @PostMapping("/collect/tasks")
    @RequirePerm("metadata:collect:create")
    public Result<Object> createCollectTask(@RequestBody Map<String, Object> b) {
        long id = db.insert("INSERT INTO md_collect_task(tenant_id,datasource_id,name,mode,cron,status,next_run_time,owner,create_time) "
                        + "VALUES(?,?,?,?,?,'ENABLED',?,?,?)",
                AuthContext.tenantId(), b.get("datasourceId"), b.get("name"), b.getOrDefault("mode", "AUTO"),
                b.getOrDefault("cron", "0 0 2 * * ?"), LocalDateTime.now().plusHours(6), AuthContext.username(), LocalDateTime.now());
        return Result.ok(M.of("id", id));
    }

    @PutMapping("/collect/tasks/{id}")
    @RequirePerm("metadata:collect:edit")
    public Result<Object> updateCollectTask(@PathVariable long id, @RequestBody Map<String, Object> b) {
        Map<String, Object> before = db.one("SELECT * FROM md_collect_task WHERE id=?", id);
        if (before == null) throw new BizException(ErrorCode.NOT_FOUND, "采集任务不存在");
        db.update("UPDATE md_collect_task SET name=?,mode=?,cron=?,status=? WHERE id=?",
                pick(b, before, "name"), pick(b, before, "mode"), pick(b, before, "cron"),
                pick(b, before, "status"), id);
        return Result.ok(M.of("id", id));
    }

    @PostMapping("/collect/tasks/{id}/run")
    @RequirePerm("metadata:collect:exec")
    public Result<Object> runCollect(@PathVariable long id) {
        Map<String, Object> t = db.one("SELECT * FROM md_collect_task WHERE id=?", id);
        if (t == null) throw new BizException(ErrorCode.NOT_FOUND);
        Map<String, Object> out = collectService.collect(((Number) t.get("datasourceId")).longValue(), "MANUAL",
                AuthContext.username());
        return Result.ok(out);
    }

    @GetMapping("/collect/instances")
    @RequirePerm("metadata:collect:view")
    public Result<Object> collectInstances(@RequestParam(defaultValue = "1") int pageNum,
                                           @RequestParam(defaultValue = "20") int pageSize) {
        return Result.ok(db.page("SELECT * FROM md_collect_instance", "id DESC", pageNum, pageSize).toMap());
    }

    // ================= 血缘 =================
    @GetMapping("/lineage/graph")
    @RequirePerm("metadata:lineage:view")
    public Result<Object> lineageGraph(@RequestParam(required = false) String objectId,
                                       @RequestParam(defaultValue = "TABLE") String granularity,
                                       @RequestParam(defaultValue = "downstream") String direction,
                                       @RequestParam(defaultValue = "2") int level) {
        return Result.ok(lineageService.graph(objectId, granularity, direction, level));
    }

    @GetMapping("/lineage/impact")
    @RequirePerm("metadata:lineage:view")
    public Result<Object> lineageImpact(@RequestParam String objectId, @RequestParam(defaultValue = "3") int level) {
        return Result.ok(lineageService.impact(objectId, level));
    }

    @PostMapping("/lineage/parse")
    @RequirePerm("metadata:lineage:parse")
    public Result<Object> lineageParse(@RequestBody Map<String, Object> b) {
        boolean persist = !Boolean.FALSE.equals(b.get("persist"));
        return Result.ok(lineageService.parse(String.valueOf(b.getOrDefault("sql", "")),
                Req.str(b.get("jobName")), persist));
    }

    @GetMapping("/lineage/edges/{id}/transform")
    @RequirePerm("metadata:lineage:view")
    public Result<Object> edgeTransform(@PathVariable long id) {
        Map<String, Object> e = db.one("SELECT * FROM md_lineage_edge WHERE id=?", id);
        if (e == null) throw new BizException(ErrorCode.NOT_FOUND);
        return Result.ok(M.of("id", id, "source", e.get("srcTable") + "." + e.get("srcColumn"),
                "target", e.get("dstTable") + "." + e.get("dstColumn"),
                "transformType", e.get("transformType"), "transformExpr", e.get("transformExpr"),
                "job", e.get("jobName"), "description", "加工逻辑：" + e.get("transformType") + " · " + e.get("transformExpr")));
    }

    // ================= 术语 =================
    @GetMapping("/terminology")
    @RequirePerm("metadata:term:view")
    public Result<Object> terminology(@RequestParam(required = false) String industry,
                                      @RequestParam(required = false) String keyword) {
        StringBuilder where = new StringBuilder(" WHERE 1=1");
        List<Object> args = new ArrayList<>();
        if (!Req.blank(industry)) {
            where.append(" AND industry=?");
            args.add(industry);
        }
        if (!Req.blank(keyword)) {
            where.append(" AND (term_name LIKE ? OR definition LIKE ? OR synonyms LIKE ?)");
            for (int i = 0; i < 3; i++) args.add("%" + keyword + "%");
        }
        return Result.ok(M.of("records", db.list("SELECT * FROM md_biz_term" + where + " ORDER BY industry, term_code", args.toArray())));
    }

    @PostMapping("/terminology")
    @RequirePerm("metadata:term:edit")
    public Result<Object> saveTerm(@RequestBody Map<String, Object> b) {
        long id = db.insert("INSERT INTO md_biz_term(tenant_id,industry,term_code,term_name,term_name_en,definition,column_pattern,"
                        + "std_element_code,synonyms,security_level,source) VALUES(?,?,?,?,?,?,?,?,?,?,?)",
                AuthContext.tenantId(), b.getOrDefault("industry", "GOV"), b.get("termCode"), b.get("termName"),
                b.get("termNameEn"), b.get("definition"), b.get("columnPattern"), b.get("stdElementCode"),
                b.get("synonyms"), b.getOrDefault("securityLevel", "L1"), b.get("source"));
        return Result.ok(M.of("id", id));
    }

    // ================= 概览 =================
    @GetMapping("/stats/overview")
    @RequirePerm("metadata:overview:view")
    public Result<Object> statsOverview() {
        return com.dgp.common.TtlCache.get("metadata:stats:" + com.dgp.security.AuthContext.tenantId(), 15_000,
                this::computeStatsOverview);
    }

    private Result<Object> computeStatsOverview() {
        // 注意：MySQL 8 中 ROWS 是保留字，别名不能直接用 rows
        List<Map<String, Object>> byLayer = db.list("SELECT layer, COUNT(*) AS cnt, SUM(row_count) AS row_cnt FROM md_table GROUP BY layer ORDER BY layer");
        List<Map<String, Object>> byDomain = db.list("SELECT domain, COUNT(*) AS cnt FROM md_table GROUP BY domain ORDER BY cnt DESC");
        List<Map<String, Object>> bySensitive = db.list("SELECT sensitive_level, COUNT(*) AS cnt FROM md_table GROUP BY sensitive_level ORDER BY sensitive_level");
        return Result.ok(M.of(
                "tableCount", db.count("SELECT COUNT(*) FROM md_table"),
                "columnCount", db.count("SELECT COUNT(*) FROM md_column"),
                "datasourceCount", db.count("SELECT COUNT(*) FROM md_datasource"),
                "connectedDatasource", db.count("SELECT COUNT(*) FROM md_datasource WHERE conn_status='已连接'"),
                "lineageEdgeCount", db.count("SELECT COUNT(*) FROM md_lineage_edge"),
                "collectInstanceCount", db.count("SELECT COUNT(*) FROM md_collect_instance"),
                "lastCollectTime", db.scalar("SELECT MAX(end_time) FROM md_collect_instance"),
                "byLayer", byLayer, "byDomain", byDomain, "bySensitive", bySensitive,
                "totalRows", db.scalar("SELECT COALESCE(SUM(row_count),0) FROM md_table")));
    }

    /** 部分更新取值：body 未包含该键时沿用库中原值。 */
    private static Object pick(Map<String, Object> body, Map<String, Object> before, String key) {
        return body.containsKey(key) ? body.get(key) : before.get(key);
    }
}
