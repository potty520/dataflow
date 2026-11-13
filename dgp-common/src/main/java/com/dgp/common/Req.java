package com.dgp.common;

/** 分页/排序请求参数解析。 */
public class Req {

    public static int pageNum(Object v) {
        return Math.max(1, toInt(v, 1));
    }

    public static int pageSize(Object v) {
        int n = toInt(v, 20);
        return Math.min(200, Math.max(1, n));
    }

    public static int toInt(Object v, int def) {
        if (v == null) return def;
        try {
            return Integer.parseInt(String.valueOf(v));
        } catch (NumberFormatException e) {
            return def;
        }
    }

    public static String str(Object v) {
        return v == null ? null : String.valueOf(v);
    }

    public static boolean blank(String s) {
        return s == null || s.isBlank();
    }

    /** 将前端 sort 参数（如 -updateTime）转换为 SQL 片段，仅允许白名单列。 */
    public static String order(String sort, String def, String... allowed) {
        if (sort == null || sort.isBlank()) return def;
        String dir = sort.startsWith("-") ? "DESC" : "ASC";
        String col = sort.startsWith("-") ? sort.substring(1) : sort;
        for (String a : allowed) {
            if (a.equalsIgnoreCase(col)) return a + " " + dir;
        }
        return def;
    }
}
