package com.dgp.migration;

import com.dgp.common.BizException;
import com.dgp.common.Db;
import com.dgp.common.ErrorCode;
import com.dgp.common.M;
import com.dgp.security.AuthContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 数据迁移映射服务：字段映射推荐 → 预检（DDL/类型/样例）→ 执行（INSERT ... SELECT）→ 对账（差异）。
 *
 * <p>源表与目标表位于当前数据源内；跨实例迁移只需把取数/写入换成对应连接（接口已按 DataSource 抽象预留）。
 */
@Service
public class MigrationService {

    private static final Logger log = LoggerFactory.getLogger(MigrationService.class);
    private static final Pattern DANGEROUS = Pattern.compile(
            "csvread|read_csv|link_schema|file_read|load_file|xp_cmdshell|into\\s+outfile|into\\s+dumpfile");

    private final Db db;
    private final CrossMigrationService cross;

    public MigrationService(Db db, CrossMigrationService cross) {
        this.db = db;
        this.cross = cross;
    }

    // ------------------------------------------------------------ 基础校验
    public void assertReadable(String table) {
        String t = Db.identifier(table);
        try {
            db.list("SELECT * FROM " + t + " LIMIT 1");
        } catch (Exception e) {
            throw new BizException(ErrorCode.PARAM_INVALID, "表不存在或不可读：" + t);
        }
    }

    /** 值表达式安全校验（允许函数/CASE，禁止多语句、注释、写操作、文件函数）。 */
    public static void assertSafeExpr(String expr) {
        if (expr == null || expr.isBlank()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "转换表达式不能为空");
        }
        if (expr.length() > 500) {
            throw new BizException(ErrorCode.PARAM_INVALID, "转换表达式过长（≤500 字符）");
        }
        String s = expr.toLowerCase();
        if (s.contains(";") || s.contains("--") || s.contains("/*")) {
            throw new BizException(ErrorCode.PARAM_INVALID, "转换表达式禁止包含多语句或注释");
        }
        for (String bad : new String[]{" insert ", " update ", " delete ", " drop ", " alter ", " truncate ",
                " create ", " merge ", " grant ", " revoke ", " into "}) {
            if ((" " + s.trim() + " ").contains(bad)) {
                throw new BizException(ErrorCode.PARAM_INVALID, "转换表达式禁止包含写操作/DDL");
            }
        }
        if (DANGEROUS.matcher(s).find()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "转换表达式禁止调用文件/外部访问类函数");
        }
    }

    /** 读取表字段（跨 H2/MySQL 的 information_schema 查询）。 */
    public List<Map<String, Object>> columnsOf(String table) {
        String t = Db.identifier(table);
        List<Map<String, Object>> rows = db.list(
                "SELECT column_name AS name, data_type AS type FROM information_schema.columns "
                        + "WHERE LOWER(table_name)=LOWER(?) ORDER BY ordinal_position", t);
        if (rows.isEmpty()) {
            // H2 的 information_schema 列名大小写差异兜底：用元数据表
            rows = db.list("SELECT c.name AS name, c.data_type AS type FROM md_column c "
                    + "JOIN md_table t ON t.id=c.table_id WHERE t.name=?", t);
        }
        return rows;
    }

    private List<String> columnNames(String table) {
        List<String> out = new ArrayList<>();
        for (Map<String, Object> c : columnsOf(table)) out.add(String.valueOf(c.get("name")));
        return out;
    }

    // ------------------------------------------------------------ 映射推荐
    /** 基于名称/类型相似度为源字段推荐目标字段（已用目标字段会跳过）。 */
    public Map<String, Object> recommend(long projectId) {
        Map<String, Object> p = requireProject(projectId);
        String source = String.valueOf(p.get("sourceTable"));
        String target = String.valueOf(p.get("targetTable"));
        List<Map<String, Object>> srcCols = srcColumns(p, source);
        List<Map<String, Object>> tgtCols = tgtColumns(p, target);
        // 推荐是"重新生成一套映射"，因此不按已保存映射排除目标字段（否则二次推荐会全部为空）
        Set<String> used = new LinkedHashSet<>();
        long existing = db.count("SELECT COUNT(*) FROM mig_mapping WHERE project_id=?", projectId);
        List<Map<String, Object>> out = new ArrayList<>();
        int matched = 0;
        for (Map<String, Object> s : srcCols) {
            String sc = String.valueOf(s.get("name"));
            String st = String.valueOf(s.get("type"));
            String best = null;
            int bestScore = 0;
            for (Map<String, Object> t : tgtCols) {
                String tc = String.valueOf(t.get("name"));
                if (used.contains(tc.toLowerCase(Locale.ROOT))) continue;
                int score = score(sc, tc);
                if (score > bestScore) {
                    bestScore = score;
                    best = tc;
                }
            }
            Map<String, Object> row = M.of("sourceColumn", sc, "sourceType", st);
            if (best != null && bestScore >= 60) {
                used.add(best.toLowerCase(Locale.ROOT));
                matched++;
                row.put("targetColumn", best);
                row.put("targetType", typeOf(tgtCols, best));
                row.put("score", bestScore);
                row.put("transformType", "DIRECT");
                // 类型族不同则建议显式转换表达式
                String bt = typeOf(tgtCols, best);
                if (!sameFamily(st, bt)) {
                    row.put("transformType", "EXPR");
                    row.put("transformExpr", "CAST(" + Db.identifier(sc) + " AS " + castType(bt) + ")");
                }
            } else {
                row.put("targetColumn", "");
                row.put("targetType", "");
                row.put("score", 0);
                row.put("transformType", "DIRECT");
            }
            out.add(row);
        }
        return M.of("sourceTable", source, "targetTable", target, "sourceColumns", srcCols,
                "targetColumns", tgtCols, "matched", matched, "existingMappingCount", existing, "candidates", out);
    }

    private static String typeOf(List<Map<String, Object>> cols, String name) {
        for (Map<String, Object> c : cols) {
            if (String.valueOf(c.get("name")).equalsIgnoreCase(name)) return String.valueOf(c.get("type"));
        }
        return "varchar";
    }

    private static int score(String a, String b) {
        String x = a.toLowerCase(Locale.ROOT);
        String y = b.toLowerCase(Locale.ROOT);
        if (x.equals(y)) return 100;
        String nx = x.replace("_", "");
        String ny = y.replace("_", "");
        if (nx.equals(ny)) return 95;
        if (nx.endsWith(ny) || ny.endsWith(nx)) return 80;
        if (nx.contains(ny) || ny.contains(nx)) return 70;
        return 0;
    }

    private static boolean sameFamily(String a, String b) {
        String x = a.toLowerCase(Locale.ROOT);
        String y = b.toLowerCase(Locale.ROOT);
        return family(x).equals(family(y));
    }

    private static String family(String t) {
        if (t.contains("char") || t.contains("text") || t.contains("clob")) return "str";
        if (t.contains("int")) return "int";
        if (t.contains("decimal") || t.contains("numeric") || t.contains("double") || t.contains("float")
                || t.contains("real")) return "num";
        if (t.contains("date") || t.contains("time")) return "time";
        if (t.contains("bool")) return "bool";
        return "other";
    }

    private static String castType(String targetType) {
        String f = family(targetType.toLowerCase(Locale.ROOT));
        return switch (f) {
            case "str" -> "CHAR";
            case "int" -> "SIGNED";
            case "num" -> "DECIMAL(18,2)";
            case "time" -> "DATETIME";
            case "bool" -> "SIGNED";
            default -> "CHAR";
        };
    }

    /** 目标端建表语句建议（目标表不存在时使用）。 */
    private String suggestDdl(String targetTable, List<Map<String, Object>> mappings) {
        StringBuilder sb = new StringBuilder("CREATE TABLE IF NOT EXISTS ").append(Db.identifier(targetTable)).append(" (");
        List<String> cols = new ArrayList<>();
        for (Map<String, Object> m : mappings) {
            String c = Db.identifier(String.valueOf(m.get("targetColumn")));
            String type = ddlType(String.valueOf(m.getOrDefault("targetType", "")),
                    String.valueOf(m.getOrDefault("sourceType", "")));
            cols.add(c + " " + type);
        }
        sb.append(String.join(", ", cols)).append(")");
        return sb.toString();
    }

    private static String ddlType(String targetType, String sourceType) {
        String t = (targetType == null || targetType.isBlank()) ? sourceType : targetType;
        String f = family(t == null ? "" : t.toLowerCase(Locale.ROOT));
        return switch (f) {
            case "str" -> "VARCHAR(255)";
            case "int" -> "BIGINT";
            case "num" -> "DECIMAL(18,2)";
            case "time" -> "DATETIME";
            case "bool" -> "TINYINT";
            default -> "VARCHAR(255)";
        };
    }

    // ------------------------------------------------------------ 映射维护
    public void saveMappings(long projectId, List<Map<String, Object>> items) {
        requireProject(projectId);
        db.update("DELETE FROM mig_mapping WHERE project_id=?", projectId);
        int i = 0;
        Set<String> targets = new LinkedHashSet<>();
        for (Map<String, Object> m : items) {
            String target = String.valueOf(m.getOrDefault("targetColumn", "")).trim();
            if (target.isBlank()) continue;
            if (!Db.isIdentifier(target)) {
                throw new BizException(ErrorCode.PARAM_INVALID, "非法目标字段名：" + target);
            }
            if (!targets.add(target.toLowerCase(Locale.ROOT))) {
                throw new BizException(ErrorCode.PARAM_INVALID, "目标字段重复映射：" + target);
            }
            String stype = "".equals(String.valueOf(m.getOrDefault("transformType", "DIRECT")))
                    ? "DIRECT" : String.valueOf(m.get("transformType")).toUpperCase(Locale.ROOT);
            String expr = Db.str(m.get("transformExpr"));
            if ("EXPR".equals(stype)) assertSafeExpr(expr);
            String src = Db.str(m.get("sourceColumn"));
            if (src != null && !src.isBlank()) Db.identifier(src);
            db.insert("INSERT INTO mig_mapping(project_id,source_column,source_type,target_column,target_type,"
                            + "transform_type,transform_expr,default_value,sort_no) VALUES(?,?,?,?,?,?,?,?,?)",
                    projectId, src, Db.str(m.get("sourceType")), target, Db.str(m.get("targetType")),
                    stype, expr, Db.str(m.get("defaultValue")), i++);
        }
        if (i == 0) throw new BizException(ErrorCode.PARAM_INVALID, "至少配置一条字段映射");
        db.update("UPDATE mig_project SET status='READY',update_time=? WHERE id=?", LocalDateTime.now(), projectId);
    }


    // ------------------------------------------------------------ 预检
    public Map<String, Object> precheck(long projectId) {
        Map<String, Object> p = requireProject(projectId);
        String source = String.valueOf(p.get("sourceTable"));
        String target = String.valueOf(p.get("targetTable"));
        List<Map<String, Object>> mappings = db.list(
                "SELECT * FROM mig_mapping WHERE project_id=? ORDER BY sort_no,id", projectId);
        List<String> srcCols = names(srcColumns(p, source));
        List<String> tgtCols = names(tgtColumns(p, target));
        boolean targetExists = !tgtCols.isEmpty();
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        if (mappings.isEmpty()) errors.add("尚未配置字段映射");
        for (Map<String, Object> m : mappings) {
            String sc = Db.str(m.get("sourceColumn"));
            String tc = Db.str(m.get("targetColumn"));
            if (sc != null && !sc.isBlank() && !srcCols.stream().anyMatch(c -> c.equalsIgnoreCase(sc))) {
                errors.add("源字段不存在：" + sc);
            }
            if (targetExists && tgtCols.stream().noneMatch(c -> c.equalsIgnoreCase(tc))) {
                warnings.add("目标表暂无该字段（执行前需先建列）：" + tc);
            }
        }
        List<Map<String, Object>> sample = new ArrayList<>();
        String ddl = targetExists ? "" : suggestDdl(target, mappings);
        try {
            if (CrossMigrationService.needsCross(p)) {
                sample = cross.sample(p, mappings, 3);
            } else {
                sample = db.list("SELECT " + selectList(mappings) + " FROM " + Db.identifier(source) + " LIMIT 3");
            }
        } catch (Exception e) {
            errors.add("样例取数失败：" + e.getMessage());
        }
        return M.of("projectId", projectId, "sourceTable", source, "targetTable", target,
                "targetExists", targetExists, "mappingCount", mappings.size(),
                "errors", errors, "warnings", warnings,
                "suggestDdl", ddl, "sample", sample,
                "srcColumns", srcCols, "tgtColumns", tgtCols,
                "ready", errors.isEmpty());
    }


    /** 源端字段：远程数据源走远程连接，否则本库 information_schema。 */
    private List<Map<String, Object>> srcColumns(Map<String, Object> p, String table) {
        if (CrossMigrationService.needsCross(p)) return cross.columnsOf(p, table, true);
        return columnsOf(table);
    }

    private List<Map<String, Object>> tgtColumns(Map<String, Object> p, String table) {
        if (CrossMigrationService.needsCross(p)) return cross.columnsOf(p, table, false);
        return columnsOf(table);
    }

    private static List<String> names(List<Map<String, Object>> cols) {
        List<String> out = new ArrayList<>();
        for (Map<String, Object> c : cols) out.add(String.valueOf(c.get("name")));
        return out;
    }

    /** 生成 SELECT 表达式列表（映射 → SQL）。 */
    private String selectList(List<Map<String, Object>> mappings) {
        List<String> exprs = new ArrayList<>();
        for (Map<String, Object> m : mappings) {
            exprs.add(valueExpr(m));
        }
        return String.join(", ", exprs);
    }

    private String valueExpr(Map<String, Object> m) {
        String type = String.valueOf(m.getOrDefault("transformType", "DIRECT")).toUpperCase(Locale.ROOT);
        String src = Db.str(m.get("sourceColumn"));
        String srcId = (src == null || src.isBlank()) ? null : Db.identifier(src);
        return switch (type) {
            case "EXPR" -> {
                String expr = Db.str(m.get("transformExpr"));
                assertSafeExpr(expr);
                yield "(" + expr + ")";
            }
            case "CONST" -> literal(Db.str(m.get("defaultValue")));
            case "DEFAULT" -> "COALESCE(" + (srcId == null ? "NULL" : srcId) + ", "
                    + literal(Db.str(m.get("defaultValue"))) + ")";
            default -> srcId == null ? "NULL" : srcId;
        };
    }

    private static String literal(String v) {
        return "'" + (v == null ? "" : v.replace("'", "''")) + "'";
    }

    /** 水位值统一转字符串：LocalDateTime 用 "yyyy-MM-dd HH:mm:ss"，避免写出带 T 的 ISO 串。 */
    private static String wmText(Object v) {
        if (v == null) return null;
        if (v instanceof java.time.LocalDateTime ldt) {
            return ldt.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        }
        if (v instanceof java.time.LocalDate ld) {
            return ld.atStartOfDay().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        }
        return String.valueOf(v);
    }

    // ------------------------------------------------------------ 执行（分批 / 断点续传 / 失败回滚）
    public Map<String, Object> run(long projectId, boolean replace, int batchSize, String operator) {
        Map<String, Object> p = requireProject(projectId);
        if (CrossMigrationService.needsCross(p)) {
            return cross.run(p, replace, batchSize, operator);
        }
        String source = Db.identifier(String.valueOf(p.get("sourceTable")));
        String target = Db.identifier(String.valueOf(p.get("targetTable")));
        List<Map<String, Object>> mappings = db.list(
                "SELECT * FROM mig_mapping WHERE project_id=? ORDER BY sort_no,id", projectId);
        if (mappings.isEmpty()) throw new BizException(ErrorCode.PARAM_INVALID, "尚未配置字段映射，无法执行");

        List<String> targets = new ArrayList<>();
        for (Map<String, Object> m : mappings) {
            targets.add(Db.identifier(String.valueOf(m.get("targetColumn"))));
        }
        boolean incremental = "INCREMENTAL".equalsIgnoreCase(String.valueOf(p.get("mode")));
        String wm = Db.str(p.get("watermarkCol"));
        if (incremental && (wm == null || wm.isBlank())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "增量模式必须配置水位字段");
        }
        String wmId = incremental ? Db.identifier(wm) : null;
        int bs = (int) Math.max(1L, Math.min(batchSize <= 0 ? Db.longVal(p.get("batchSize"), 5000) : (long) batchSize, 100000L));
        boolean rollbackOnFail = Db.longVal(p.get("rollbackOnFail"), 0) == 1;
        String startWm = incremental ? Db.str(p.get("lastWatermark")) : null;

        long sourceRows = incremental
                ? (startWm == null ? db.count("SELECT COUNT(*) FROM " + source)
                : db.count("SELECT COUNT(*) FROM " + source + " WHERE " + wmId + " > ?", startWm))
                : db.count("SELECT COUNT(*) FROM " + source);

        long runId = db.insert("INSERT INTO mig_run(project_id,run_mode,status,start_time,source_rows,operator,start_watermark) "
                        + "VALUES(?,?,'RUNNING',?,?,?,?)",
                projectId, "MANUAL", LocalDateTime.now(), sourceRows, operator, startWm);
        long inserted = 0;
        int chunks = 0;
        String endWm = startWm;
        String status = "DONE";
        String message = "";
        boolean rolledBack = false;
        try {
            if (replace && !incremental) {
                db.update("DELETE FROM " + target);
            }
            if (!incremental) {
                inserted = db.update("INSERT INTO " + target + " (" + String.join(",", targets) + ") SELECT "
                        + selectList(mappings) + " FROM " + source);
                chunks = 1;
            } else {
                String from = startWm;
                while (true) {
                    String chunkSql = "SELECT MAX(" + wmId + ") FROM (SELECT " + wmId + " FROM " + source
                            + (from == null ? "" : " WHERE " + wmId + " > ?")
                            + " ORDER BY " + wmId + " LIMIT " + bs + ") t";
                    Object chunkMaxObj = from == null ? db.scalar(chunkSql) : db.scalar(chunkSql, from);
                    if (chunkMaxObj == null) break;
                    String chunkMax = wmText(chunkMaxObj);
                    int affected = from == null
                            ? db.update("INSERT INTO " + target + " (" + String.join(",", targets) + ") SELECT "
                            + selectList(mappings) + " FROM " + source + " WHERE " + wmId + " <= ?", chunkMax)
                            : db.update("INSERT INTO " + target + " (" + String.join(",", targets) + ") SELECT "
                            + selectList(mappings) + " FROM " + source + " WHERE " + wmId + " > ? AND " + wmId + " <= ?",
                            from, chunkMax);
                    inserted += affected;
                    chunks++;
                    endWm = chunkMax;
                    from = chunkMax;
                    db.update("UPDATE mig_project SET last_watermark=? WHERE id=?", chunkMax, projectId);
                    if (affected == 0) break;
                }
            }
            db.update("UPDATE mig_project SET last_run_time=?,status='DONE',update_time=? WHERE id=?",
                    LocalDateTime.now(), LocalDateTime.now(), projectId);
            message = "写入成功" + (incremental ? "（分 " + chunks + " 批）" : "");
        } catch (Exception e) {
            log.warn("数据迁移执行失败 project={}: {}", projectId, e.getMessage());
            status = "FAILED";
            message = e.getMessage() == null ? "执行失败" : e.getMessage();
            if (rollbackOnFail && incremental) {
                try {
                    long deleted;
                    if (startWm != null) {
                        deleted = db.update("DELETE FROM " + target + " WHERE " + wmId + " > ?", startWm);
                    } else if (endWm != null) {
                        deleted = db.update("DELETE FROM " + target + " WHERE " + wmId + " <= ?", endWm);
                    } else {
                        deleted = db.update("DELETE FROM " + target);
                    }
                    rolledBack = true;
                    message += "；已回滚本次写入 " + deleted + " 行";
                } catch (Exception re) {
                    message += "；回滚失败：" + re.getMessage();
                }
            }
        }
        long targetRows;
        try {
            targetRows = db.count("SELECT COUNT(*) FROM " + target);
        } catch (Exception e) {
            targetRows = 0;
        }
        db.update("UPDATE mig_run SET status=?,end_time=?,target_rows=?,inserted_rows=?,failed_rows=?,message=?,"
                        + "start_watermark=?,end_watermark=?,chunk_count=?,rollback_status=? WHERE id=?",
                status, LocalDateTime.now(), targetRows, inserted,
                "DONE".equals(status) ? 0 : Math.max(0, sourceRows - inserted),
                message.length() > 480 ? message.substring(0, 480) : message,
                startWm, endWm, chunks, rolledBack ? "ROLLED_BACK" : null, runId);
        if ("FAILED".equals(status)) {
            db.update("UPDATE mig_project SET status='FAILED',update_time=? WHERE id=?", LocalDateTime.now(), projectId);
            throw new BizException(ErrorCode.SQL_INVALID, "迁移执行失败：" + message);
        }
        return M.of("runId", runId, "projectId", projectId, "status", status, "sourceRows", sourceRows,
                "targetRows", targetRows, "insertedRows", inserted, "chunkCount", chunks,
                "startWatermark", startWm, "endWatermark", endWm, "message", message);
    }

    /** 回滚：仅增量模式且记录了起始水位的批次，删除该水位之后的写入并恢复项目水位。 */
    public Map<String, Object> rollback(long runId) {
        Map<String, Object> run = db.one("SELECT * FROM mig_run WHERE id=?", runId);
        if (run == null) throw new BizException(ErrorCode.NOT_FOUND, "迁移批次不存在");
        long projectId = Db.longVal(run.get("projectId"), 0L);
        Map<String, Object> p = requireProject(projectId);
        if (CrossMigrationService.needsCross(p)) {
            return cross.rollback(run);
        }
        String wm = Db.str(p.get("watermarkCol"));
        if (wm == null || wm.isBlank()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "仅增量模式支持回滚");
        }
        String start = Db.str(run.get("startWatermark"));
        String end = Db.str(run.get("endWatermark"));
        if ((start == null || start.isBlank()) && (end == null || end.isBlank())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "该批次未记录水位区间，无法回滚");
        }
        String target = Db.identifier(String.valueOf(p.get("targetTable")));
        // 水位字段在映射中可能被改名（如 order_time -> ts），按映射解析目标端实际列名
        String targetWm = null;
        for (Map<String, Object> mm : db.list(
                "SELECT * FROM mig_mapping WHERE project_id=? ORDER BY sort_no,id", projectId)) {
            String sc = Db.str(mm.get("sourceColumn"));
            if (sc != null && sc.equalsIgnoreCase(wm)) {
                targetWm = Db.str(mm.get("targetColumn"));
                break;
            }
        }
        if (targetWm == null) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "字段映射需包含水位字段 " + wm + " 才能按水位回滚");
        }
        String wmId = Db.identifier(targetWm);
        boolean hasWmCol = false;
        for (Map<String, Object> c : columnsOf(target)) {
            if (String.valueOf(c.get("name")).equalsIgnoreCase(targetWm)) {
                hasWmCol = true;
                break;
            }
        }
        if (!hasWmCol) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "目标表缺少水位字段 " + targetWm + "，无法按水位回滚；请检查字段映射");
        }
        int deleted;
        if (start != null && !start.isBlank()) {
            deleted = db.update("DELETE FROM " + target + " WHERE " + wmId + " > ?", start);
        } else {
            // 首个增量批次：目标表在本次执行前应为空，回滚=删除该区间内的数据
            deleted = db.update("DELETE FROM " + target + " WHERE " + wmId + " <= ?", end);
        }
        db.update("UPDATE mig_run SET rollback_status='ROLLED_BACK' WHERE id=?", runId);
        db.update("UPDATE mig_project SET last_watermark=?,status='READY',update_time=? WHERE id=?",
                start == null || start.isBlank() ? null : start, LocalDateTime.now(), projectId);
        return M.of("runId", runId, "deletedRows", deleted,
                "restoredWatermark", start == null || start.isBlank() ? null : start,
                "rollbackStatus", "ROLLED_BACK");
    }


    /** 按映射生成并执行目标表 DDL（目标表不存在时使用，表名/列名均经标识符校验）。 */
    public Map<String, Object> createTargetTable(long projectId) {
        Map<String, Object> p = requireProject(projectId);
        String target = Db.identifier(String.valueOf(p.get("targetTable")));
        if (!columnsOf(target).isEmpty()) {
            return M.of("created", false, "targetTable", target, "message", "目标表已存在，无需创建");
        }
        List<Map<String, Object>> mappings = db.list(
                "SELECT * FROM mig_mapping WHERE project_id=? ORDER BY sort_no,id", projectId);
        if (mappings.isEmpty()) throw new BizException(ErrorCode.PARAM_INVALID, "尚未配置字段映射");
        String ddl = suggestDdl(target, mappings);
        try {
            db.update(ddl);
        } catch (Exception e) {
            throw new BizException(ErrorCode.SQL_INVALID, "建表失败：" + e.getMessage());
        }
        return M.of("created", true, "targetTable", target, "ddl", ddl);
    }

    // ------------------------------------------------------------ 对账
    public Map<String, Object> reconcile(long runId) {
        Map<String, Object> run = db.one("SELECT * FROM mig_run WHERE id=?", runId);
        if (run == null) throw new BizException(ErrorCode.NOT_FOUND, "迁移批次不存在");
        long projectId = Db.longVal(run.get("projectId"), 0L);
        Map<String, Object> p = requireProject(projectId);
        if (CrossMigrationService.needsCross(p)) {
            return cross.rollback(run);
        }
        String source = Db.identifier(String.valueOf(p.get("sourceTable")));
        String target = Db.identifier(String.valueOf(p.get("targetTable")));

        db.update("DELETE FROM mig_diff WHERE run_id=?", runId);
        List<Map<String, Object>> checks = db.list("SELECT * FROM mig_check WHERE project_id=? ORDER BY id", projectId);
        if (checks.isEmpty()) {
            // 默认对账：行数 + 主键重复（若有映射主键）
            checks = List.of(M.of("checkType", "ROW_COUNT"), M.of("checkType", "PK_DUP"));
        }
        List<Map<String, Object>> diffs = new ArrayList<>();
        int failed = 0;
        for (Map<String, Object> c : checks) {
            String type = String.valueOf(c.get("checkType")).toUpperCase(Locale.ROOT);
            String col = Db.str(c.get("columnName"));
            double tol = Db.num(c.get("tolerance")) == null ? 0 : Db.num(c.get("tolerance"));
            String expected = "";
            String actual = "";
            String diff = "";
            String status = "PASS";
            String detail = "";
            try {
                switch (type) {
                    case "ROW_COUNT" -> {
                        long s = db.count("SELECT COUNT(*) FROM " + source);
                        long t = db.count("SELECT COUNT(*) FROM " + target);
                        expected = String.valueOf(s);
                        actual = String.valueOf(t);
                        diff = String.valueOf(t - s);
                        if (s == 0) {
                            status = t == 0 ? "PASS" : "FAIL";
                        } else if (Math.abs(t - s) * 1.0 / s > tol) {
                            status = "FAIL";
                        }
                        detail = "源表 " + s + " 行 / 目标表 " + t + " 行";
                    }
                    case "PK_DUP" -> {
                        String key = col == null || col.isBlank() ? target + "_key" : Db.identifier(col);
                        long dup = db.count("SELECT COUNT(*)-COUNT(DISTINCT " + key + ") FROM " + target);
                        expected = "0";
                        actual = String.valueOf(dup);
                        diff = String.valueOf(dup);
                        status = dup == 0 ? "PASS" : "FAIL";
                        detail = "目标字段 " + key + " 重复行数 " + dup;
                    }
                    case "NULL_RATE" -> {
                        if (col == null || col.isBlank()) throw new BizException(ErrorCode.PARAM_INVALID, "空值率检查需指定字段");
                        String cid = Db.identifier(col);
                        Double sRate = Db.num(db.scalar("SELECT ROUND(SUM(CASE WHEN " + cid + " IS NULL THEN 1 ELSE 0 END)*100.0"
                                + "/NULLIF(COUNT(*),0),2) FROM " + source));
                        Double tRate = Db.num(db.scalar("SELECT ROUND(SUM(CASE WHEN " + cid + " IS NULL THEN 1 ELSE 0 END)*100.0"
                                + "/NULLIF(COUNT(*),0),2) FROM " + target));
                        expected = String.valueOf(sRate == null ? 0 : sRate);
                        actual = String.valueOf(tRate == null ? 0 : tRate);
                        double d = (tRate == null ? 0 : tRate) - (sRate == null ? 0 : sRate);
                        diff = String.valueOf(Math.round(d * 100) / 100.0);
                        status = Math.abs(d) <= tol * 100 ? "PASS" : "FAIL";
                        detail = "源空值率 " + expected + "% / 目标空值率 " + actual + "%";
                    }
                    case "SUM" -> {
                        if (col == null || col.isBlank()) throw new BizException(ErrorCode.PARAM_INVALID, "SUM 检查需指定字段");
                        String cid = Db.identifier(col);
                        Double s = Db.num(db.scalar("SELECT COALESCE(SUM(" + cid + "),0) FROM " + source));
                        Double t = Db.num(db.scalar("SELECT COALESCE(SUM(" + cid + "),0) FROM " + target));
                        expected = String.valueOf(s == null ? 0 : s);
                        actual = String.valueOf(t == null ? 0 : t);
                        double d = Math.abs((t == null ? 0 : t) - (s == null ? 0 : s));
                        diff = String.valueOf(Math.round(d * 100) / 100.0);
                        status = (s == null || s == 0) ? (d == 0 ? "PASS" : "FAIL") : (d / Math.abs(s) <= tol ? "PASS" : "FAIL");
                        detail = "源合计 " + expected + " / 目标合计 " + actual;
                    }
                    default -> {
                        expected = "-";
                        actual = "-";
                        diff = "-";
                        status = "SKIP";
                        detail = "未实现对账类型：" + type;
                    }
                }
            } catch (Exception e) {
                status = "ERROR";
                detail = e.getMessage();
            }
            if ("FAIL".equals(status) || "ERROR".equals(status)) failed++;
            db.insert("INSERT INTO mig_diff(run_id,check_type,column_name,expected_val,actual_val,diff_val,status,detail) "
                            + "VALUES(?,?,?,?,?,?,?,?)",
                    runId, type, col, expected, actual, diff, status,
                    detail.length() > 480 ? detail.substring(0, 480) : detail);
            diffs.add(M.of("checkType", type, "columnName", col, "expected", expected, "actual", actual,
                    "diff", diff, "status", status, "detail", detail));
        }
        return M.of("runId", runId, "projectId", projectId, "checkCount", diffs.size(),
                "failedCount", failed, "status", failed == 0 ? "PASS" : "FAIL", "diffs", diffs);
    }

    // ------------------------------------------------------------ 查询/统计
    public Map<String, Object> projectDetail(long projectId) {
        Map<String, Object> p = requireProject(projectId);
        p.put("mappings", db.list("SELECT * FROM mig_mapping WHERE project_id=? ORDER BY sort_no,id", projectId));
        p.put("checks", db.list("SELECT * FROM mig_check WHERE project_id=? ORDER BY id", projectId));
        p.put("runs", db.list("SELECT * FROM mig_run WHERE project_id=? ORDER BY id DESC LIMIT 10", projectId));
        return p;
    }

    public Map<String, Object> overview() {
        LocalDateTime dayAgo = LocalDateTime.now().minusDays(1);
        return M.of("projectCount", db.count("SELECT COUNT(*) FROM mig_project"),
                "readyCount", db.count("SELECT COUNT(*) FROM mig_project WHERE status='READY'"),
                "doneCount", db.count("SELECT COUNT(*) FROM mig_project WHERE status='DONE'"),
                "failedCount", db.count("SELECT COUNT(*) FROM mig_project WHERE status='FAILED'"),
                "runCount24h", db.count("SELECT COUNT(*) FROM mig_run WHERE start_time>=?", dayAgo),
                "inserted24h", db.count("SELECT COALESCE(SUM(inserted_rows),0) FROM mig_run WHERE start_time>=?", dayAgo),
                "failedChecks24h", db.count("SELECT COUNT(*) FROM mig_diff WHERE status IN ('FAIL','ERROR')"),
                "mappingCount", db.count("SELECT COUNT(*) FROM mig_mapping"),
                "recentRuns", db.list("SELECT r.*, p.name AS project_name, p.code AS project_code FROM mig_run r "
                        + "LEFT JOIN mig_project p ON p.id=r.project_id ORDER BY r.id DESC LIMIT 8"),
                "recentDiffs", db.list("SELECT d.*, r.project_id FROM mig_diff d JOIN mig_run r ON r.id=d.run_id "
                        + "WHERE d.status<>'PASS' ORDER BY d.id DESC LIMIT 8"));
    }

    private Map<String, Object> requireProject(long id) {
        Map<String, Object> p = db.one("SELECT * FROM mig_project WHERE id=?", id);
        if (p == null) throw new BizException(ErrorCode.NOT_FOUND, "迁移项目不存在：" + id);
        return p;
    }

    public Object maxWatermark(long projectId) {
        Map<String, Object> p = requireProject(projectId);
        String wm = Db.str(p.get("watermarkCol"));
        if (wm == null || wm.isBlank()) return null;
        return db.scalar("SELECT MAX(" + Db.identifier(wm) + ") FROM "
                + Db.identifier(String.valueOf(p.get("sourceTable"))));
    }

    public Map<String, Object> statement(long projectId) {
        Map<String, Object> p = requireProject(projectId);
        List<Map<String, Object>> mappings = db.list(
                "SELECT * FROM mig_mapping WHERE project_id=? ORDER BY sort_no,id", projectId);
        List<String> targets = new ArrayList<>();
        for (Map<String, Object> m : mappings) targets.add(Db.identifier(String.valueOf(m.get("targetColumn"))));
        String sql = "INSERT INTO " + Db.identifier(String.valueOf(p.get("targetTable")))
                + " (" + String.join(",", targets) + ") SELECT " + selectList(mappings)
                + " FROM " + Db.identifier(String.valueOf(p.get("sourceTable")));
        return M.of("sql", sql, "mappingCount", mappings.size());
    }

    public BigDecimal sumOf(String table, String column) {
        Double v = Db.num(db.scalar("SELECT COALESCE(SUM(" + Db.identifier(column) + "),0) FROM "
                + Db.identifier(table)));
        return BigDecimal.valueOf(v == null ? 0 : v);
    }
}
