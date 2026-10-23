package com.dgp.common;

import com.dgp.security.AuthContext;
import com.dgp.security.AuthUser;

import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 租户行级隔离：非平台管理员（ROLE_GOV_ADMIN）的读/改/删请求，自动为「单表」SQL 追加 tenant_id 条件。
 *
 * <p>设计取舍：只改写可安全识别的单表查询（无 JOIN/UNION/子查询），避免误改复杂 SQL；
 * 多表查询由各业务显式带 tenant_id。平台管理员与系统内部查询（无登录上下文）不受影响。
 * 可用 {@code /*tenant:off*}{@code /} 注释显式跳过（如跨租户报表）。</p>
 */
public final class TenantGuard {

    private static final Pattern FROM = Pattern.compile(
            "(?is)\\bfrom\\s+([a-z0-9_]+)(?:\\s+(?:as\\s+)?([a-z0-9_]+))?");
    private static final Pattern UPDATE = Pattern.compile(
            "(?is)^\\s*update\\s+([a-z0-9_]+)(?:\\s+(?:as\\s+)?([a-z0-9_]+))?");
    private static final Pattern DELETE = Pattern.compile(
            "(?is)^\\s*delete\\s+from\\s+([a-z0-9_]+)(?:\\s+(?:as\\s+)?([a-z0-9_]+))?");
    private static final Set<String> KEYWORDS = Set.of(
            "where", "group", "order", "limit", "having", "offset", "join", "left", "right",
            "inner", "outer", "on", "using", "union", "set", "values", "as", "for", "fetch", "window");

    private static volatile Set<String> tenantTables;
    private static volatile JdbcHolder holder;
    private static final Set<String> SKIP = ConcurrentHashMap.newKeySet();

    private TenantGuard() {
    }

    /** 由 Db 构造时绑定，用于查询 information_schema（避免循环依赖）。 */
    public static void bind(JdbcHolder h) {
        holder = h;
    }

    public interface JdbcHolder {
        java.util.List<java.util.Map<String, Object>> query(String sql);
    }

    public static String rewrite(String sql) {
        if (sql == null) return null;
        AuthUser u = AuthContext.get();
        if (u == null || u.getTenantId() == null) return sql;
        if ("ROLE_GOV_ADMIN".equalsIgnoreCase(u.getRoleCode())) return sql;
        String s = sql.trim();
        if (s.contains("/*tenant:off*/")) return sql.replace("/*tenant:off*/", "");
        String lower = s.toLowerCase(Locale.ROOT);
        String table;
        String alias;
        Matcher m;
        if (lower.startsWith("select")) {
            // 派生表/UNION 无法安全改写；普通 JOIN 只过滤 FROM 主表（别名条件追加到 WHERE）
            if (lower.contains(" union ") || lower.contains("from (") || lower.contains("join (")) return sql;
            Matcher outer = outerFrom(s);
            if (outer == null) return sql;
            table = outer.group(1);
            alias = outer.group(2);
        } else if (lower.startsWith("update")) {
            if (lower.contains(" join ")) return sql;
            m = UPDATE.matcher(s);
            if (!m.find()) return sql;
            table = m.group(1);
            alias = m.group(2);
        } else if (lower.startsWith("delete")) {
            if (lower.contains(" join ")) return sql;
            m = DELETE.matcher(s);
            if (!m.find()) return sql;
            table = m.group(1);
            alias = m.group(2);
        } else {
            return sql;
        }
        if (alias != null && KEYWORDS.contains(alias.toLowerCase(Locale.ROOT))) alias = null;
        if (table == null) return sql;
        String t = table.toLowerCase(Locale.ROOT);
        if (!tables().contains(t)) return sql;
        if (lower.contains("tenant_id")) return sql;
        String cond = (alias == null ? "" : alias + ".") + "tenant_id = " + u.getTenantId();
        return appendCondition(s, cond);
    }

    private static Set<String> tables() {
        Set<String> s = tenantTables;
        if (s != null) return s;
        synchronized (TenantGuard.class) {
            if (tenantTables != null) return tenantTables;
            try {
                java.util.List<java.util.Map<String, Object>> rows = holder == null ? null
                        : holder.query("SELECT table_name AS t FROM information_schema.columns "
                        + "WHERE table_schema=DATABASE() AND column_name='tenant_id'");
                Set<String> set = ConcurrentHashMap.newKeySet();
                if (rows != null) {
                    for (java.util.Map<String, Object> r : rows) {
                        Object v = r.get("t") == null ? r.get("T") : r.get("t");
                        if (v != null) set.add(String.valueOf(v).toLowerCase(Locale.ROOT));
                    }
                }
                tenantTables = set;
            } catch (Exception e) {
                tenantTables = Set.of();
            }
            return tenantTables;
        }
    }

    /** 找到最外层 FROM（括号深度 0，跳过引号），避免把 SELECT 列表子查询里的 FROM 当成主表。 */
    private static Matcher outerFrom(String s) {
        int depth = 0;
        char quote = 0;
        for (int i = 0; i + 4 <= s.length(); i++) {
            char c = s.charAt(i);
            if (quote != 0) {
                if (c == quote && (i == 0 || s.charAt(i - 1) != '\\')) quote = 0;
                continue;
            }
            if (c == '\'' || c == '"') {
                quote = c;
                continue;
            }
            if (c == '(') {
                depth++;
                continue;
            }
            if (c == ')') {
                depth--;
                continue;
            }
            if (depth == 0 && s.regionMatches(true, i, "from", 0, 4)
                    && (i == 0 || !Character.isLetterOrDigit(s.charAt(i - 1)))
                    && (i + 4 >= s.length() || !Character.isLetterOrDigit(s.charAt(i + 4)))) {
                Matcher m = FROM.matcher(s.substring(i));
                if (m.find()) return m;
            }
        }
        return null;
    }

    /** 把条件追加到 WHERE 子句末尾（或新增 WHERE），不影响 GROUP BY/ORDER BY/LIMIT 等尾部子句。 */
    private static String appendCondition(String sql, String cond) {
        String lower = sql.toLowerCase(Locale.ROOT);
        int tail = sql.length();
        for (String kw : new String[]{" group by ", " order by ", " limit ", " having ", " offset ", " for update"}) {
            int i = lower.indexOf(kw);
            if (i >= 0 && i < tail) tail = i;
        }
        int where = lower.indexOf(" where ");
        if (where >= 0 && where < tail) {
            return sql.substring(0, tail).trim() + " AND " + cond + " " + sql.substring(tail).trim();
        }
        return sql.substring(0, tail).trim() + " WHERE " + cond + " " + sql.substring(tail).trim();
    }
}
