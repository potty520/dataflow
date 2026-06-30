package com.zhangye.dataflow.util;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ApiParamParser {

    private static final Pattern PARAM_PATTERN = Pattern.compile("\\$\\{(\\w+)}");

    private ApiParamParser() {}

    /**
     * Extract parameter placeholders from SQL.
     * Returns list of {name, type(default VARCHAR), required(true), defaultValue, description}
     */
    public static List<Map<String, String>> extractParams(String sql) {
        List<Map<String, String>> params = new ArrayList<>();
        if (sql == null || sql.isEmpty()) return params;
        Set<String> seen = new LinkedHashSet<>();
        Matcher m = PARAM_PATTERN.matcher(sql);
        while (m.find()) {
            String name = m.group(1);
            if (seen.add(name)) {
                Map<String, String> param = new LinkedHashMap<>();
                param.put("name", name);
                param.put("type", inferType(name));
                param.put("required", "true");
                param.put("defaultValue", "");
                param.put("description", "");
                params.add(param);
            }
        }
        return params;
    }

    private static String inferType(String name) {
        String lower = name.toLowerCase();
        if (lower.contains("id") || lower.contains("count") || lower.contains("num") || lower.contains("age")) {
            return "BIGINT";
        }
        if (lower.contains("date") || lower.contains("time")) {
            return "VARCHAR";
        }
        if (lower.contains("amount") || lower.contains("price") || lower.contains("rate") || lower.contains("score")) {
            return "DECIMAL";
        }
        return "VARCHAR";
    }

    public static String buildApiDoc(String sql, List<Map<String, String>> params) {
        StringBuilder sb = new StringBuilder();
        sb.append("# API参数说明\n\n");
        sb.append("## SQL\n```sql\n").append(sql).append("\n```\n\n");
        sb.append("## 参数列表\n\n");
        sb.append("| 参数名 | 类型 | 必填 | 默认值 | 说明 |\n");
        sb.append("|--------|------|------|--------|------|\n");
        for (Map<String, String> p : params) {
            sb.append("| ").append(p.get("name"))
              .append(" | ").append(p.get("type"))
              .append(" | ").append(p.get("required"))
              .append(" | ").append(p.get("defaultValue"))
              .append(" | ").append(p.get("description"))
              .append(" |\n");
        }
        return sb.toString();
    }

    /**
     * Replace placeholders with actual values and return executable SQL.
     */
    public static String bindParams(String sql, Map<String, String> paramValues) {
        if (sql == null || paramValues == null) return sql;
        String result = sql;
        for (Map.Entry<String, String> entry : paramValues.entrySet()) {
            String key = "${" + entry.getKey() + "}";
            String value = entry.getValue();
            // basic SQL injection guard: quote string values
            if (value != null && !value.matches("\\d+(\\.\\d+)?")) {
                value = "'" + value.replace("'", "''") + "'";
            }
            result = result.replace(key, value != null ? value : "");
        }
        return result;
    }
}
