package com.zhangye.dataflow.util;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * SQL格式化工具 - 关键字大写、缩进、换行 (F-DEV-005)
 */
public class SqlFormatter {

    private static final Set<String> KEYWORDS = new HashSet<>(Arrays.asList(
            "SELECT", "FROM", "WHERE", "AND", "OR", "NOT", "IN", "EXISTS", "BETWEEN", "LIKE",
            "INSERT", "INTO", "VALUES", "UPDATE", "SET", "DELETE",
            "CREATE", "ALTER", "DROP", "TABLE", "INDEX", "VIEW",
            "JOIN", "LEFT", "RIGHT", "INNER", "OUTER", "FULL", "CROSS", "ON",
            "GROUP", "BY", "ORDER", "ASC", "DESC", "HAVING",
            "UNION", "ALL", "DISTINCT", "AS", "CASE", "WHEN", "THEN", "ELSE", "END",
            "LIMIT", "OFFSET", "FETCH", "NEXT", "ROWS", "ONLY",
            "NULL", "IS", "COUNT", "SUM", "AVG", "MAX", "MIN",
            "CAST", "COALESCE", "NULLIF", "TRIM", "UPPER", "LOWER",
            "BEGIN", "COMMIT", "ROLLBACK", "WITH", "RECURSIVE",
            "OVER", "PARTITION", "ROW_NUMBER", "RANK", "DENSE_RANK",
            "INTERSECT", "EXCEPT", "TRUNCATE", "MERGE"
    ));

    private static final Set<String> NEWLINE_BEFORE = new HashSet<>(Arrays.asList(
            "SELECT", "FROM", "WHERE", "AND", "OR", "GROUP", "ORDER", "HAVING",
            "LIMIT", "UNION", "INTERSECT", "EXCEPT", "JOIN", "LEFT", "RIGHT",
            "INNER", "OUTER", "FULL", "CROSS", "ON", "SET", "INSERT", "VALUES",
            "UPDATE", "DELETE", "CREATE", "ALTER", "DROP", "WITH", "BEGIN", "COMMIT",
            "ROLLBACK"
    ));

    public static String format(String sql) {
        if (sql == null || sql.trim().isEmpty()) {
            return sql;
        }

        StringBuilder result = new StringBuilder();
        String[] tokens = tokenize(sql);
        int indent = 0;
        boolean newLine = true;

        for (int i = 0; i < tokens.length; i++) {
            String token = tokens[i];
            String upper = token.toUpperCase().trim();

            // Check if this is a keyword
            if (KEYWORDS.contains(upper)) {
                if (NEWLINE_BEFORE.contains(upper) && !newLine) {
                    result.append("\n");
                    result.append("  ".repeat(Math.max(0, indent)));
                    newLine = true;
                }
                if (upper.equals("SELECT") || upper.equals("INSERT") || upper.equals("UPDATE")
                        || upper.equals("DELETE") || upper.equals("CREATE") || upper.equals("WITH")
                        || upper.equals("BEGIN")) {
                    indent = 1;
                }
                if (!newLine) {
                    result.append(" ");
                }
                result.append(upper);
                newLine = false;
            } else if (token.equals(",") || token.equals(";")) {
                result.append(token);
                result.append("\n");
                result.append("  ".repeat(Math.max(0, indent)));
                newLine = true;
            } else if (token.equals("(")) {
                result.append("(");
                indent++;
                newLine = false;
            } else if (token.equals(")")) {
                indent = Math.max(0, indent - 1);
                result.append(")");
                newLine = false;
            } else {
                if (!newLine && !"(".equals(result.length() > 0 ? result.substring(result.length() - 1) : "")) {
                    result.append(" ");
                }
                result.append(upper);
                newLine = false;
            }
        }

        return result.toString().trim();
    }

    private static String[] tokenize(String sql) {
        // Simple tokenizer: split on whitespace, but keep punctuation separate
        java.util.List<String> tokens = new java.util.ArrayList<>();
        Pattern pattern = Pattern.compile(
                "\\b\\w+\\b|[*]|[=<>!]+|[,;()]|'[^']*'|\"[^\"]*\"|\\d+(?:\\.\\d+)?"
        );
        Matcher matcher = pattern.matcher(sql);
        while (matcher.find()) {
            tokens.add(matcher.group());
        }
        return tokens.toArray(new String[0]);
    }
}
