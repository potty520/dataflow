package com.dgp.common;

import com.dgp.security.AuthContext;
import com.dgp.security.AuthUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 租户行级隔离：非平台管理员的读/改/删请求，自动为可识别 SQL 追加 tenant_id。
 *
 * <p>第二期加固：
 * <ul>
 *   <li>tenant:off 注释仅平台管理员可用，业务账号使用直接拒绝</li>
 *   <li>严格模式（dgp.tenant.strict=true）下，命中租户表却无法安全改写且未显式带 tenant_id 的 SQL 抛错</li>
 * </ul>
 */
public final class TenantGuard {

    private static final Logger log = LoggerFactory.getLogger(TenantGuard.class);

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
    /** 严格模式：无法改写的租户表 SQL 直接失败。 */
    private static volatile boolean strict = true;

    private TenantGuard() {
    }

    public static void bind(JdbcHolder h) {
        holder = h;
    }

    public static void setStrict(boolean enabled) {
        strict = enabled;
    }

    public static boolean isStrict() {
        return strict;
    }

    public interface JdbcHolder {
        java.util.List<java.util.Map<String, Object>> query(String sql);
    }

    public static String rewrite(String sql) {
        if (sql == null) return null;
        AuthUser u = AuthContext.get();
        if (u == null || u.getTenantId() == null) return sql;
        boolean admin = u.isAdmin() || "ROLE_GOV_ADMIN".equalsIgnoreCase(u.getRoleCode());
        String s = sql.trim();
        if (s.contains("/*tenant:off*/")) {
            if (!admin) {
                throw new BizException(ErrorCode.TENANT_ISOLATION,
                        "非平台管理员禁止使用 /*tenant:off*/ 绕过租户隔离");
            }
            return sql.replace("/*tenant:off*/", "");
        }
        if (admin) return sql;

        String lower = s.toLowerCase(Locale.ROOT);
        boolean complex = lower.contains(" union ") || lower.contains("from (") || lower.contains("join (")
                || lower.contains(" join ");
        String table = null;
        String alias = null;
        Matcher m;
        if (lower.startsWith("select")) {
            // 严格模式：JOIN/UNION/派生表无法保证全表租户过滤，必须显式带 tenant_id
            if (lower.contains(" union ") || lower.contains("from (") || lower.contains(" join ")) {
                return rejectOrPass(s, lower, true);
            }
            Matcher outer = outerFrom(s);
            if (outer == null) return rejectOrPass(s, lower, complex);
            table = outer.group(1);
            alias = outer.group(2);
        } else if (lower.startsWith("update")) {
            if (lower.contains(" join ")) return rejectOrPass(s, lower, true);
            m = UPDATE.matcher(s);
            if (!m.find()) return rejectOrPass(s, lower, false);
            table = m.group(1);
            alias = m.group(2);
        } else if (lower.startsWith("delete")) {
            if (lower.contains(" join ")) return rejectOrPass(s, lower, true);
            m = DELETE.matcher(s);
            if (!m.find()) return rejectOrPass(s, lower, false);
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

    /**
     * 复杂 SQL 且涉及租户表：严格模式拒绝；非严格模式仅告警并原样返回（兼容过渡）。
     */
    private static String rejectOrPass(String sql, String lower, boolean complex) {
        if (!complex && !looksLikeTenantTouch(lower)) return sql;
        if (!looksLikeTenantTouch(lower) && !complex) return sql;
        // 已显式带 tenant_id 的复杂 SQL 放行
        if (lower.contains("tenant_id")) return sql;
        if (strict && looksLikeTenantTouch(lower)) {
            throw new BizException(ErrorCode.TENANT_ISOLATION,
                    "严格租户隔离：该 SQL 无法自动注入 tenant_id，请改写为显式租户条件或由平台管理员执行");
        }
        if (looksLikeTenantTouch(lower)) {
            log.warn("tenant-guard skip complex SQL without tenant_id: {}",
                    sql.length() > 180 ? sql.substring(0, 180) + "..." : sql);
        }
        return sql;
    }

    private static boolean looksLikeTenantTouch(String lower) {
        Set<String> ts = tables();
        if (ts.isEmpty()) return false;
        for (String t : ts) {
            if (lower.contains(t)) return true;
        }
        return false;
    }

    private static Set<String> tables() {
        Set<String> s = tenantTables;
        if (s != null) return s;
        synchronized (TenantGuard.class) {
            if (tenantTables != null) return tenantTables;
            try {
                java.util.List<java.util.Map<String, Object>> rows = holder == null ? null
                        : holder.query("SELECT table_name AS t FROM information_schema.columns "
                        + "WHERE LOWER(column_name)='tenant_id' "
                        + "AND LOWER(table_schema) NOT IN ('information_schema','pg_catalog','sys')");
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

    /** 测试辅助：重置表缓存。 */
    public static void resetTableCacheForTest(Set<String> tables) {
        tenantTables = tables == null ? null : ConcurrentHashMap.newKeySet();
        if (tables != null) tenantTables.addAll(tables);
    }

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

    private static String appendCondition(String sql, String cond) {
        String lower = sql.toLowerCase(Locale.ROOT);
        int tail = sql.length();
        for (String kw : new String[]{" group by ", " order by ", " limit ", " having ", " offset ", " for update"}) {
            int i = outerKeyword(lower, kw);
            if (i >= 0 && i < tail) tail = i;
        }
        int where = outerKeyword(lower, " where ");
        if (where >= 0 && where < tail) {
            return sql.substring(0, tail).trim() + " AND " + cond + " " + sql.substring(tail).trim();
        }
        return sql.substring(0, tail).trim() + " WHERE " + cond + " " + sql.substring(tail).trim();
    }

    private static int outerKeyword(String s, String kw) {
        int depth = 0;
        char quote = 0;
        for (int i = 0; i + kw.length() <= s.length(); i++) {
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
            if (depth == 0 && s.startsWith(kw, i)) return i;
        }
        return -1;
    }
}
