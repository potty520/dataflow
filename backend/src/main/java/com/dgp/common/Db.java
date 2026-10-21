package com.dgp.common;

import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.sql.Clob;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 轻量数据访问门面。统一处理：
 * 1) 列名 snake_case → camelCase（对齐 00 基线 §6.8 前端命名）；
 * 2) Timestamp/Date/Clob 归一化为可 JSON 序列化类型；
 * 3) 分页与计数。
 */
@Component
public class Db {

    /** 合法 SQL 标识符（表名/列名），用于阻断字符串拼接型 SQL 注入。 */
    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z_][A-Za-z0-9_]{0,63}");
    /** ORDER BY 片段白名单字符（列名、ASC/DESC、逗号、点号、空格）。 */
    private static final Pattern ORDER_BY = Pattern.compile("[A-Za-z0-9_.,\\s]{1,200}");

    private final JdbcTemplate jdbc;

    public Db(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public JdbcTemplate jdbc() {
        return jdbc;
    }

    public List<Map<String, Object>> list(String sql, Object... args) {
        List<Map<String, Object>> rows = jdbc.queryForList(sql, args);
        List<Map<String, Object>> out = new ArrayList<>(rows.size());
        for (Map<String, Object> r : rows) {
            out.add(normalize(r));
        }
        return out;
    }

    public Map<String, Object> one(String sql, Object... args) {
        List<Map<String, Object>> rows = jdbc.queryForList(sql, args);
        return rows.isEmpty() ? null : normalize(rows.get(0));
    }

    /**
     * 单值查询。仅"无结果"返回 null；SQL/参数错误必须抛出，避免把参数错配静默降级成"数据为空"。
     */
    public Object scalar(String sql, Object... args) {
        try {
            Object v = jdbc.queryForObject(sql, Object.class, args);
            if (v instanceof Timestamp ts) return ts.toLocalDateTime();
            if (v instanceof java.sql.Date d) return d.toLocalDate();
            return v;
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }

    public long count(String sql, Object... args) {
        Object v = scalar(sql, args);
        return v == null ? 0L : ((Number) v).longValue();
    }

    public int update(String sql, Object... args) {
        return jdbc.update(sql, args);
    }

    public long insert(String sql, Object... args) {
        KeyHolder kh = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            for (int i = 0; i < args.length; i++) {
                ps.setObject(i + 1, args[i]);
            }
            return ps;
        }, kh);
        Number key = kh.getKey();
        return key == null ? 0L : key.longValue();
    }

    public long insertReturningId(String sql, Object... args) {
        return insert(sql, args);
    }

    public void batch(String sql, List<Object[]> args) {
        jdbc.batchUpdate(sql, args);
    }

    /** 分页查询：baseSql 不带 order by / limit。 */
    public PageResult page(String baseSql, String orderBy, int pageNum, int pageSize, Object... args) {
        int pn = Math.max(1, pageNum);
        int ps = Math.min(200, Math.max(1, pageSize));
        long total = count("SELECT COUNT(*) FROM (" + baseSql + ") t", args);
        String sql = baseSql + (orderBy == null || orderBy.isBlank() ? "" : " ORDER BY " + safeOrder(orderBy))
                + " LIMIT " + ps + " OFFSET " + (long) (pn - 1) * ps;
        return PageResult.of(list(sql, args), total, pn, ps);
    }

    /** ORDER BY 片段只允许列名/方向/逗号，杜绝注入。 */
    public static String safeOrder(String orderBy) {
        if (orderBy == null || orderBy.isBlank()) return "id";
        if (!ORDER_BY.matcher(orderBy).matches()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "非法的排序参数");
        }
        return orderBy;
    }

    /** 校验并返回合法标识符，用于必须拼接表名/列名的少数场景（血缘、规则表达式等）。 */
    public static String identifier(String name) {
        if (name == null || !IDENTIFIER.matcher(name).matches()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "非法的标识符：" + name);
        }
        return name;
    }

    public static boolean isIdentifier(String name) {
        return name != null && IDENTIFIER.matcher(name).matches();
    }

    /** 安全取数字（H2/PostgreSQL 对 DECIMAL 的 Java 类型可能不同）。 */
    public static Double num(Object v) {
        if (v == null) return null;
        if (v instanceof Number n) return n.doubleValue();
        try {
            return Double.parseDouble(String.valueOf(v));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static BigDecimal decimal(Object v) {
        if (v == null) return null;
        if (v instanceof BigDecimal b) return b;
        if (v instanceof Number n) return BigDecimal.valueOf(n.doubleValue());
        try {
            return new BigDecimal(String.valueOf(v));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static long longVal(Object v, long def) {
        Double d = num(v);
        return d == null ? def : d.longValue();
    }

    public static String str(Object v) {
        return v == null ? null : String.valueOf(v);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> normalize(Map<String, Object> row) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : row.entrySet()) {
            String k = camel(e.getKey());
            Object v = e.getValue();
            if (v instanceof Timestamp ts) {
                v = ts.toLocalDateTime();
            } else if (v instanceof java.sql.Date d) {
                v = d.toLocalDate();
            } else if (v instanceof Clob c) {
                try {
                    v = c.getSubString(1, (int) c.length());
                } catch (Exception ex) {
                    v = null;
                }
            }
            out.put(k, v);
        }
        return out;
    }

    public static String camel(String s) {
        if (s == null || s.isEmpty() || s.indexOf('_') < 0) {
            // 无下划线时保持原样：数据库标识符已被 DATABASE_TO_LOWER 归一，强行小写会破坏驼峰别名
            return s;
        }
        StringBuilder sb = new StringBuilder();
        boolean up = false;
        for (char c : s.toLowerCase().toCharArray()) {
            if (c == '_') {
                up = true;
            } else if (up) {
                sb.append(Character.toUpperCase(c));
                up = false;
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    public static LocalDateTime now() {
        return LocalDateTime.now();
    }

    public static LocalDate today() {
        return LocalDate.now();
    }
}
