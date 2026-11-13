package com.dgp.model;

import com.dgp.common.BizException;
import com.dgp.common.Db;
import com.dgp.common.ErrorCode;
import com.dgp.common.M;
import com.dgp.common.Result;
import com.dgp.security.RequirePerm;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** BF-11：类型映射与库间字段映射配置。 */
@RestController
@RequestMapping("/api/v1/model/mappings")
public class ModelMappingController {

    private final Db db;
    private final TypeMappingService typeMapping;

    public ModelMappingController(Db db, TypeMappingService typeMapping) {
        this.db = db;
        this.typeMapping = typeMapping;
    }

    // ---------------- 类型映射 ----------------
    @GetMapping("/types")
    @RequirePerm("model:mapping:view")
    public Result<Object> types(@RequestParam(required = false) String dialect,
                                @RequestParam(required = false) String keyword) {
        try { typeMapping.ensureDefaults(); } catch (Exception ignore) { }
        StringBuilder sql = new StringBuilder("SELECT * FROM mdl_type_mapping WHERE 1=1");
        List<Object> args = new ArrayList<>();
        if (dialect != null && !dialect.isBlank()) { sql.append(" AND target_dialect=?"); args.add(dialect); }
        if (keyword != null && !keyword.isBlank()) {
            sql.append(" AND (source_type LIKE ? OR target_type LIKE ?)");
            args.add("%" + keyword.trim() + "%"); args.add("%" + keyword.trim() + "%");
        }
        sql.append(" ORDER BY target_dialect, source_type");
        return Result.ok(M.of("records", db.list(sql.toString(), args.toArray())));
    }

    @GetMapping("/types/resolve")
    @RequirePerm("model:mapping:view")
    public Result<Object> resolve(@RequestParam String sourceType,
                                  @RequestParam(defaultValue = "MYSQL") String targetDialect) {
        return Result.ok(M.of("sourceType", sourceType, "targetDialect", targetDialect,
                "targetType", typeMapping.resolve(sourceType, targetDialect, "VARCHAR(255)"),
                "family", typeMapping.family(sourceType)));
    }

    @PostMapping("/types")
    @RequirePerm("model:mapping:edit")
    public Result<Object> createType(@RequestBody Map<String, Object> b) {
        String source = Db.str(b.get("sourceType"));
        String dialect = Db.str(b.get("targetDialect"));
        String target = Db.str(b.get("targetType"));
        if (source == null || source.isBlank() || dialect == null || dialect.isBlank()
                || target == null || target.isBlank()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "源类型/目标方言/目标类型不能为空");
        }
        if (db.one("SELECT id FROM mdl_type_mapping WHERE target_dialect=? AND LOWER(source_type)=LOWER(?)",
                dialect.toUpperCase(), source.trim()) != null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "该方言下类型映射已存在");
        }
        long id = db.insert("INSERT INTO mdl_type_mapping(source_type,target_dialect,target_type,status,remark,create_time,update_time) "
                        + "VALUES(?,?,?,?,?,?,?)",
                source.trim(), dialect.toUpperCase(), target.trim(),
                b.getOrDefault("status", "ENABLED"), b.get("remark"), LocalDateTime.now(), LocalDateTime.now());
        return Result.ok(M.of("id", id));
    }

    @PutMapping("/types/{id}")
    @RequirePerm("model:mapping:edit")
    public Result<Object> updateType(@PathVariable long id, @RequestBody Map<String, Object> b) {
        Map<String, Object> row = db.one("SELECT * FROM mdl_type_mapping WHERE id=?", id);
        if (row == null) throw new BizException(ErrorCode.NOT_FOUND, "类型映射不存在");
        db.update("UPDATE mdl_type_mapping SET target_type=?,status=?,remark=?,update_time=? WHERE id=?",
                b.containsKey("targetType") ? b.get("targetType") : row.get("targetType"),
                b.containsKey("status") ? b.get("status") : row.get("status"),
                b.containsKey("remark") ? b.get("remark") : row.get("remark"),
                LocalDateTime.now(), id);
        return Result.ok(M.of("id", id));
    }

    @DeleteMapping("/types/{id}")
    @RequirePerm("model:mapping:edit")
    public Result<Object> deleteType(@PathVariable long id) {
        db.update("DELETE FROM mdl_type_mapping WHERE id=?", id);
        return Result.ok(M.of("deleted", true));
    }

    // ---------------- 库间字段映射 ----------------
    @GetMapping("/columns")
    @RequirePerm("model:mapping:view")
    public Result<Object> columns(@RequestParam(required = false) Long modelId,
                                  @RequestParam(required = false) String keyword) {
        StringBuilder sql = new StringBuilder("SELECT * FROM mdl_column_mapping WHERE 1=1");
        List<Object> args = new ArrayList<>();
        if (modelId != null) { sql.append(" AND model_id=?"); args.add(modelId); }
        if (keyword != null && !keyword.isBlank()) {
            sql.append(" AND (source_table LIKE ? OR source_column LIKE ? OR target_table LIKE ? OR target_column LIKE ?)");
            for (int i = 0; i < 4; i++) args.add("%" + keyword.trim() + "%");
        }
        sql.append(" ORDER BY model_id, source_table, source_column");
        return Result.ok(M.of("records", db.list(sql.toString(), args.toArray())));
    }

    @PostMapping("/columns")
    @RequirePerm("model:mapping:edit")
    public Result<Object> createColumn(@RequestBody Map<String, Object> b) {
        String sourceTable = Db.str(b.get("sourceTable"));
        String sourceColumn = Db.str(b.get("sourceColumn"));
        String targetTable = Db.str(b.get("targetTable"));
        String targetColumn = Db.str(b.get("targetColumn"));
        if (sourceTable == null || sourceColumn == null || targetTable == null || targetColumn == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "源/目标表与字段不能为空");
        }
        long id = db.insert("INSERT INTO mdl_column_mapping(model_id,source_table,source_column,target_table,target_column,"
                        + "source_type,target_type,expression,status,remark,create_time) VALUES(?,?,?,?,?,?,?,?,?,?,?)",
                b.get("modelId"), sourceTable, sourceColumn, targetTable, targetColumn,
                b.get("sourceType"), b.get("targetType"), b.get("expression"),
                b.getOrDefault("status", "ENABLED"), b.get("remark"), LocalDateTime.now());
        return Result.ok(M.of("id", id));
    }

    @PutMapping("/columns/{id}")
    @RequirePerm("model:mapping:edit")
    public Result<Object> updateColumn(@PathVariable long id, @RequestBody Map<String, Object> b) {
        Map<String, Object> row = db.one("SELECT * FROM mdl_column_mapping WHERE id=?", id);
        if (row == null) throw new BizException(ErrorCode.NOT_FOUND, "字段映射不存在");
        db.update("UPDATE mdl_column_mapping SET source_table=?,source_column=?,target_table=?,target_column=?,"
                        + "source_type=?,target_type=?,expression=?,status=?,remark=? WHERE id=?",
                b.containsKey("sourceTable") ? b.get("sourceTable") : row.get("sourceTable"),
                b.containsKey("sourceColumn") ? b.get("sourceColumn") : row.get("sourceColumn"),
                b.containsKey("targetTable") ? b.get("targetTable") : row.get("targetTable"),
                b.containsKey("targetColumn") ? b.get("targetColumn") : row.get("targetColumn"),
                b.containsKey("sourceType") ? b.get("sourceType") : row.get("sourceType"),
                b.containsKey("targetType") ? b.get("targetType") : row.get("targetType"),
                b.containsKey("expression") ? b.get("expression") : row.get("expression"),
                b.containsKey("status") ? b.get("status") : row.get("status"),
                b.containsKey("remark") ? b.get("remark") : row.get("remark"), id);
        return Result.ok(M.of("id", id));
    }

    @DeleteMapping("/columns/{id}")
    @RequirePerm("model:mapping:edit")
    public Result<Object> deleteColumn(@PathVariable long id) {
        db.update("DELETE FROM mdl_column_mapping WHERE id=?", id);
        return Result.ok(M.of("deleted", true));
    }
}
