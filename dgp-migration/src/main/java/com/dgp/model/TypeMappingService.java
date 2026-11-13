package com.dgp.model;

import com.dgp.common.Db;
import com.dgp.common.M;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * BF-11：可配置的类型映射（源类型 → 目标方言类型）。
 * 先按“精确类型”匹配，再按类型族（字符串/整数/数值/日期/布尔）匹配，最后回退到调用方默认值。
 */
@Service
public class TypeMappingService {

    private final Db db;

    public TypeMappingService(Db db) {
        this.db = db;
    }

    public String resolve(String sourceType, String targetDialect, String fallback) {
        if (sourceType == null || sourceType.isBlank()) return fallback;
        String dialect = (targetDialect == null || targetDialect.isBlank()) ? "MYSQL" : targetDialect.toUpperCase(Locale.ROOT);
        try {
            Map<String, Object> exact = db.one(
                    "SELECT target_type FROM mdl_type_mapping WHERE target_dialect=? AND status='ENABLED' AND LOWER(source_type)=LOWER(?)",
                    dialect, sourceType.trim());
            if (exact != null) return String.valueOf(exact.get("targetType"));
            String family = family(sourceType);
            if (family != null) {
                Map<String, Object> fam = db.one(
                        "SELECT target_type FROM mdl_type_mapping WHERE target_dialect=? AND status='ENABLED' AND LOWER(source_type)=LOWER(?)",
                        dialect, family);
                if (fam != null) return String.valueOf(fam.get("targetType"));
            }
        } catch (Exception ignore) {
        }
        return fallback;
    }

    public String family(String raw) {
        String t = raw == null ? "" : raw.toLowerCase(Locale.ROOT);
        if (t.contains("char") || t.contains("text") || t.contains("string")) return "STRING";
        if (t.contains("bigint")) return "BIGINT";
        if (t.contains("int")) return "INT";
        if (t.contains("decimal") || t.contains("numeric") || t.contains("number") || t.contains("double") || t.contains("float")) return "DECIMAL";
        if (t.contains("timestamp") || t.contains("datetime")) return "TIMESTAMP";
        if (t.contains("date") || t.contains("time")) return "DATE";
        if (t.contains("bool")) return "BOOL";
        return null;
    }

    public void ensureDefaults() {
        if (db.count("SELECT COUNT(*) FROM mdl_type_mapping") > 0) return;
        Object[][] rows = {
                {"STRING", "MYSQL", "VARCHAR(255)"}, {"VARCHAR", "MYSQL", "VARCHAR(255)"},
                {"INT", "MYSQL", "INT"}, {"BIGINT", "MYSQL", "BIGINT"},
                {"DECIMAL", "MYSQL", "DECIMAL(18,2)"}, {"DATE", "MYSQL", "DATE"},
                {"TIMESTAMP", "MYSQL", "DATETIME"}, {"BOOL", "MYSQL", "TINYINT"},
                {"STRING", "POSTGRESQL", "VARCHAR(255)"}, {"VARCHAR", "POSTGRESQL", "VARCHAR(255)"},
                {"INT", "POSTGRESQL", "BIGINT"}, {"BIGINT", "POSTGRESQL", "BIGINT"},
                {"DECIMAL", "POSTGRESQL", "NUMERIC(18,2)"}, {"DATE", "POSTGRESQL", "DATE"},
                {"TIMESTAMP", "POSTGRESQL", "TIMESTAMP"}, {"BOOL", "POSTGRESQL", "BOOLEAN"}
        };
        for (Object[] r : rows) {
            db.insert("INSERT INTO mdl_type_mapping(source_type,target_dialect,target_type,status,remark,create_time,update_time) "
                            + "VALUES(?,?,?,'ENABLED','系统默认',?,?)",
                    r[0], r[1], r[2], LocalDateTime.now(), LocalDateTime.now());
        }
    }
}
