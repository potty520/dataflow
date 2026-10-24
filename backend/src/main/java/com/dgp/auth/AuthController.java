package com.dgp.auth;

import com.dgp.common.BizException;
import com.dgp.common.Db;
import com.dgp.common.ErrorCode;
import com.dgp.common.M;
import com.dgp.common.PageResult;
import com.dgp.common.Req;
import com.dgp.common.Result;
import com.dgp.security.AuthContext;
import com.dgp.security.AuthInterceptor;
import com.dgp.security.AuthService;
import com.dgp.security.AuthUser;
import com.dgp.security.AuditService;
import com.dgp.security.RequirePerm;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** dgp-auth：登录认证、用户、角色、租户、数据权限。 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;
    private final Db db;
    private final AuditService audit;

    public AuthController(AuthService authService, Db db, AuditService audit) {
        this.authService = authService;
        this.db = db;
        this.audit = audit;
    }

    private static AuthUser me() {
        AuthUser u = AuthContext.get();
        if (u == null) throw new BizException(ErrorCode.UNAUTHORIZED);
        return u;
    }

    private static void requireAdmin() {
        if (!me().isAdmin()) {
            throw new BizException(ErrorCode.FORBIDDEN, "该操作仅限数据治理管理员");
        }
    }

    // ---------------- 认证 ----------------
    @PostMapping("/login")
    public Result<Object> login(@RequestBody(required = false) Map<String, String> body, HttpServletRequest req) {
        if (body == null) throw new BizException(ErrorCode.PARAM_INVALID, "请求体不能为空");
        String username = body.get("username");
        String password = body.get("password");
        if (Req.blank(username) || Req.blank(password)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "用户名与口令不能为空");
        }
        // 登录日志统一在 AuthService 内记录一次（原实现外层再写一次导致重复计数）
        return Result.ok(authService.login(username.trim(), password,
                AuthInterceptor.clientIp(req), req.getHeader("User-Agent")));
    }

    @PostMapping("/refresh")
    public Result<Object> refresh(@RequestBody(required = false) Map<String, String> body) {
        if (body == null) throw new BizException(ErrorCode.PARAM_INVALID, "请求体不能为空");
        return Result.ok(authService.refresh(body.get("refreshToken")));
    }

    /** 登出：令牌进入黑名单，立即失效（原实现只写一条审计，令牌继续有效）。 */
    @PostMapping("/logout")
    public Result<Object> logout(HttpServletRequest req) {
        String auth = req.getHeader("Authorization");
        String token = auth != null && auth.startsWith("Bearer ") ? auth.substring(7) : null;
        String user = AuthContext.username();
        authService.logout(token);
        audit.record(user, "LOGOUT", "auth", "/api/v1/auth/logout", null,
                AuthInterceptor.clientIp(req), "SUCCESS");
        return Result.ok(M.of("logout", true));
    }

    @GetMapping("/me")
    public Result<Object> me(@SuppressWarnings("unused") HttpServletRequest req) {
        return Result.ok(authService.profile(me()));
    }

    /** 用户自助修改口令（校验原口令）。 */
    @PutMapping("/password")
    public Result<Object> changeOwnPassword(@RequestBody Map<String, Object> body) {
        // 用户/角色/权限变更后清空鉴权缓存，避免旧权限在缓存窗口内继续生效
        authService.invalidateCaches();
        authService.changePassword(me().getUsername(), Req.str(body.get("oldPassword")),
                Req.str(body.get("newPassword")));
        return Result.ok(M.of("changed", true));
    }

    @GetMapping("/menus")
    public Result<Object> menus() {
        List<M> filtered = new ArrayList<>();
        for (M m : MenuCatalog.all()) {
            Object perm = m.get("perm");
            if (perm == null || me().has(String.valueOf(perm))) {
                filtered.add(m);
            }
        }
        return Result.ok(M.of("menus", filtered, "industryCode", tenantIndustry()));
    }

    private String tenantIndustry() {
        Object v = db.scalar("SELECT industry_code FROM sys_tenant WHERE id=?", AuthContext.tenantId());
        return v == null ? "GOV" : String.valueOf(v);
    }

    @GetMapping("/jwks")
    public Result<Object> jwks() {
        return Result.ok(M.of("alg", "HS256", "keys", List.of(), "note", "单进程形态使用 HS256，公钥集为空"));
    }

    // ---------------- 用户 ----------------
    @GetMapping("/users")
    @RequirePerm("auth:user:view")
    public Result<Object> users(@RequestParam(required = false) String keyword,
                                @RequestParam(required = false) String roleCode,
                                @RequestParam(defaultValue = "1") int pageNum,
                                @RequestParam(defaultValue = "20") int pageSize) {
        StringBuilder where = new StringBuilder(" WHERE 1=1");
        List<Object> args = new ArrayList<>();
        if (!Req.blank(keyword)) {
            where.append(" AND (username LIKE ? OR real_name LIKE ?)");
            args.add("%" + keyword + "%");
            args.add("%" + keyword + "%");
        }
        if (!Req.blank(roleCode)) {
            where.append(" AND role_code=?");
            args.add(roleCode);
        }
        String base = "SELECT u.id,u.username,u.real_name,u.role_code,r.name AS role_name,u.dept,u.email,u.status,"
                + "u.last_login_time,u.create_time FROM sys_user u LEFT JOIN sys_role r ON r.code=u.role_code" + where;
        PageResult p = db.page(base, "u.id ASC", pageNum, pageSize, args.toArray());
        return Result.ok(p.toMap());
    }

    /** 责任人下拉等场景使用的最小用户清单（登录即可访问，不暴露权限与联系方式）。 */
    @GetMapping("/users/options")
    public Result<Object> userOptions() {
        return Result.ok(M.of("records",
                db.list("SELECT id,username,real_name,role_code FROM sys_user WHERE status='启用' ORDER BY id")));
    }

    @PostMapping("/users")
    @RequirePerm("auth:user:create")
    public Result<Object> createUser(@RequestBody Map<String, Object> body) {
        // 用户/角色/权限变更后清空鉴权缓存，避免旧权限在缓存窗口内继续生效
        authService.invalidateCaches();
        String username = Req.str(body.get("username"));
        if (Req.blank(username)) throw new BizException(ErrorCode.PARAM_INVALID, "用户名不能为空");
        if (!Db.isIdentifier(username)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "用户名只能由字母、数字、下划线组成");
        }
        if (db.one("SELECT id FROM sys_user WHERE username=?", username) != null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "用户名已存在");
        }
        String roleCode = Req.str(body.get("roleCode"));
        if (Req.blank(roleCode) || db.one("SELECT code FROM sys_role WHERE code=?", roleCode) == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "角色不存在：" + roleCode);
        }
        String pwd = body.get("password") == null ? "Dgp@2026" : String.valueOf(body.get("password"));
        AuthService.assertPasswordPolicy(pwd);
        long id = db.insert("INSERT INTO sys_user(tenant_id,username,password,real_name,role_code,dept,email,phone,status,create_time) "
                        + "VALUES(?,?,?,?,?,?,?,?,'启用',?)",
                Db.longVal(body.getOrDefault("tenantId", AuthContext.tenantId()), 1L), username, AuthService.hash(pwd),
                body.get("realName"), roleCode, body.get("dept"), body.get("email"), body.get("phone"),
                LocalDateTime.now());
        audit.record(me().getUsername(), "USER_CREATE", "auth", username, "role=" + roleCode, "-", "SUCCESS");
        return Result.ok(M.of("id", id));
    }

    @PutMapping("/users/{id}")
    @RequirePerm("auth:user:edit")
    public Result<Object> updateUser(@PathVariable long id, @RequestBody Map<String, Object> body) {
        // 用户/角色/权限变更后清空鉴权缓存，避免旧权限在缓存窗口内继续生效
        authService.invalidateCaches();
        Map<String, Object> user = db.one("SELECT * FROM sys_user WHERE id=?", id);
        if (user == null) throw new BizException(ErrorCode.NOT_FOUND, "用户不存在");
        String newRole = Req.str(body.get("roleCode"));
        String oldRole = Req.str(user.get("roleCode"));
        if (newRole != null && !newRole.equals(oldRole) && !me().isAdmin()) {
            throw new BizException(ErrorCode.FORBIDDEN, "只有数据治理管理员可以变更用户角色");
        }
        String newStatus = body.get("status") == null ? null : String.valueOf(body.get("status"));
        if (id == me().getUserId() && newStatus != null && !"启用".equals(newStatus)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "不能停用当前登录账号");
        }
        // 部分更新：未传的字段保持原值（原实现缺字段即置 NULL）
        db.update("UPDATE sys_user SET real_name=?,role_code=?,dept=?,email=?,phone=?,status=? WHERE id=?",
                body.containsKey("realName") ? body.get("realName") : user.get("realName"),
                newRole == null ? oldRole : newRole,
                body.containsKey("dept") ? body.get("dept") : user.get("dept"),
                body.containsKey("email") ? body.get("email") : user.get("email"),
                body.containsKey("phone") ? body.get("phone") : user.get("phone"),
                newStatus == null ? user.getOrDefault("status", "启用") : newStatus, id);
        return Result.ok(M.of("id", id));
    }

    @DeleteMapping("/users/{id}")
    @RequirePerm("auth:user:delete")
    public Result<Object> deleteUser(@PathVariable long id) {
        // 用户/角色/权限变更后清空鉴权缓存，避免旧权限在缓存窗口内继续生效
        authService.invalidateCaches();
        requireAdmin();
        Map<String, Object> user = db.one("SELECT username FROM sys_user WHERE id=?", id);
        if (user == null) throw new BizException(ErrorCode.NOT_FOUND, "用户不存在");
        if ("admin".equals(user.get("username"))) {
            throw new BizException(ErrorCode.PARAM_INVALID, "内置管理员不允许删除");
        }
        if (id == me().getUserId()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "不能删除当前登录账号");
        }
        db.update("DELETE FROM sys_user WHERE id=?", id);
        audit.record(me().getUsername(), "USER_DELETE", "auth", String.valueOf(user.get("username")), null, "-", "SUCCESS");
        return Result.ok(M.of("deleted", true));
    }

    @PostMapping("/users/{id}/roles")
    @RequirePerm("auth:role:assign")
    public Result<Object> assignRole(@PathVariable long id, @RequestBody Map<String, Object> body) {
        // 用户/角色/权限变更后清空鉴权缓存，避免旧权限在缓存窗口内继续生效
        authService.invalidateCaches();
        requireAdmin();
        String roleCode = Req.str(body.get("roleCode"));
        if (Req.blank(roleCode) || db.one("SELECT code FROM sys_role WHERE code=?", roleCode) == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "角色不存在：" + roleCode);
        }
        db.update("UPDATE sys_user SET role_code=? WHERE id=?", roleCode, id);
        audit.record(me().getUsername(), "ROLE_ASSIGN", "auth", String.valueOf(id), "role=" + roleCode, "-", "SUCCESS");
        return Result.ok(M.of("id", id, "roleCode", roleCode));
    }

    @PutMapping("/users/{id}/password")
    @RequirePerm("auth:user:edit")
    public Result<Object> resetPassword(@PathVariable long id, @RequestBody Map<String, Object> body) {
        // 用户/角色/权限变更后清空鉴权缓存，避免旧权限在缓存窗口内继续生效
        authService.invalidateCaches();
        Map<String, Object> user = db.one("SELECT username FROM sys_user WHERE id=?", id);
        if (user == null) throw new BizException(ErrorCode.NOT_FOUND, "用户不存在");
        boolean self = id == me().getUserId();
        if (!self) {
            // 重置他人（含管理员）口令必须由管理员执行
            requireAdmin();
            authService.resetPassword(id, Req.str(body.get("password")));
        } else {
            // 本人改密必须提供原口令
            authService.changePassword(me().getUsername(), Req.str(body.get("oldPassword")),
                    Req.str(body.get("newPassword")));
        }
        audit.record(me().getUsername(), "PASSWORD_RESET", "auth", String.valueOf(user.get("username")),
                self ? "self" : "by-admin", "-", "SUCCESS");
        return Result.ok(M.of("reset", true));
    }

    // ---------------- 角色 ----------------
    @GetMapping("/roles")
    @RequirePerm("auth:role:view")
    public Result<Object> roles() {
        List<Map<String, Object>> list = db.list("SELECT * FROM sys_role ORDER BY id");
        for (Map<String, Object> r : list) {
            r.put("userCount", db.count("SELECT COUNT(*) FROM sys_user WHERE role_code=?", r.get("code")));
        }
        return Result.ok(M.of("records", list, "total", list.size()));
    }

    @PostMapping("/roles")
    @RequirePerm("auth:role:create")
    public Result<Object> createRole(@RequestBody Map<String, Object> body) {
        // 用户/角色/权限变更后清空鉴权缓存，避免旧权限在缓存窗口内继续生效
        authService.invalidateCaches();
        requireAdmin();
        String code = Req.str(body.get("code"));
        if (Req.blank(code) || !Db.isIdentifier(code)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "角色编码不合法");
        }
        long id = db.insert("INSERT INTO sys_role(code,name,perms,description) VALUES(?,?,?,?)",
                code, body.get("name"), "", body.get("description"));
        return Result.ok(M.of("id", id));
    }

    @PutMapping("/roles/{id}")
    @RequirePerm("auth:role:create")
    public Result<Object> updateRole(@PathVariable long id, @RequestBody Map<String, Object> body) {
        authService.invalidateCaches();
        requireAdmin();
        Map<String, Object> role = db.one("SELECT * FROM sys_role WHERE id=?", id);
        if (role == null) throw new BizException(ErrorCode.NOT_FOUND, "角色不存在");
        if ("ROLE_GOV_ADMIN".equals(String.valueOf(role.get("code")))) {
            throw new BizException(ErrorCode.FORBIDDEN, "内置管理员角色不可修改");
        }
        db.update("UPDATE sys_role SET name=?,description=? WHERE id=?",
                body.containsKey("name") ? body.get("name") : role.get("name"),
                body.containsKey("description") ? body.get("description") : role.get("description"), id);
        audit.record(me().getUsername(), "ROLE_UPDATE", "auth", String.valueOf(role.get("code")),
                "name=" + body.get("name"), "-", "SUCCESS");
        return Result.ok(M.of("id", id));
    }

    @PostMapping("/roles/{id}/permissions")
    @RequirePerm("auth:role:grant")
    public Result<Object> grant(@PathVariable long id, @RequestBody Map<String, Object> body) {
        // 用户/角色/权限变更后清空鉴权缓存，避免旧权限在缓存窗口内继续生效
        authService.invalidateCaches();
        requireAdmin();
        String roleCode = Req.str(db.scalar("SELECT code FROM sys_role WHERE id=?", id));
        if (roleCode == null) throw new BizException(ErrorCode.NOT_FOUND, "角色不存在");
        if ("ROLE_GOV_ADMIN".equals(roleCode)) {
            throw new BizException(ErrorCode.FORBIDDEN, "内置管理员角色权限不可修改");
        }
        Object perms = body.get("perms");
        Set<String> cleaned = new java.util.LinkedHashSet<>();
        if (perms instanceof List<?> l) {
            for (Object o : l) {
                String p = String.valueOf(o).trim();
                if (!p.isEmpty() && !"*".equals(p)) cleaned.add(p);
            }
        } else if (perms != null) {
            for (String p : String.valueOf(perms).split(",")) {
                if (!p.isBlank() && !"*".equals(p.trim())) cleaned.add(p.trim());
            }
        }
        String joined = String.join(",", cleaned);
        db.update("UPDATE sys_role SET perms=? WHERE id=?", joined, id);
        audit.record(me().getUsername(), "ROLE_GRANT", "auth", roleCode, "perms=" + joined, "-", "SUCCESS");
        return Result.ok(M.of("id", id, "perms", joined));
    }

    // ---------------- 租户 / 组织 / 数据权限 ----------------
    @GetMapping("/tenants/tree")
    @RequirePerm("auth:tenant:view")
    public Result<Object> tenantTree() {
        List<Map<String, Object>> all = db.list("SELECT * FROM sys_tenant ORDER BY tenant_path");
        return Result.ok(M.of("tree", buildTree(all), "total", all.size()));
    }

    private List<Map<String, Object>> buildTree(List<Map<String, Object>> all) {
        Map<Long, List<Map<String, Object>>> byParent = new LinkedHashMap<>();
        for (Map<String, Object> t : all) {
            long pid = Db.longVal(t.get("parentId"), 0L);
            byParent.computeIfAbsent(pid, k -> new ArrayList<>()).add(t);
        }
        List<Map<String, Object>> roots = byParent.getOrDefault(0L, List.of());
        // 用"已访问集合"防止脏数据形成环导致无限递归 / StackOverflow
        Set<Long> visited = new java.util.HashSet<>();
        return attach(roots, byParent, visited, 0);
    }

    private List<Map<String, Object>> attach(List<Map<String, Object>> nodes,
                                             Map<Long, List<Map<String, Object>>> byParent,
                                             Set<Long> visited, int depth) {
        List<Map<String, Object>> out = new ArrayList<>();
        if (depth > 12) return out;
        for (Map<String, Object> t : nodes) {
            long id = Db.longVal(t.get("id"), 0L);
            if (!visited.add(id)) continue;
            Map<String, Object> node = new LinkedHashMap<>(t);
            node.put("children", attach(byParent.getOrDefault(id, List.of()), byParent, visited, depth + 1));
            out.add(node);
        }
        return out;
    }

    @PostMapping("/tenants")
    @RequirePerm("auth:tenant:create")
    public Result<Object> createTenant(@RequestBody Map<String, Object> body) {
        requireAdmin();
        Object parentId = body.getOrDefault("parentId", 0);
        Map<String, Object> parent = db.one("SELECT * FROM sys_tenant WHERE id=?", parentId);
        String path = parent == null ? "/" : String.valueOf(parent.get("tenantPath"));
        int level = parent == null ? 1 : (int) Db.longVal(parent.get("tenantLevel"), 0L) + 1;
        long id = db.insert("INSERT INTO sys_tenant(code,name,parent_id,tenant_path,tenant_level,industry_code,isolation_mode,status,create_time) "
                        + "VALUES(?,?,?,?,?,?,?,'ENABLED',?)",
                body.get("code"), body.get("name"), parentId, path, level,
                body.getOrDefault("industryCode", "GOV"), body.getOrDefault("isolationMode", "SHARED"), LocalDateTime.now());
        db.update("UPDATE sys_tenant SET tenant_path=? WHERE id=?", path + id + "/", id);
        return Result.ok(M.of("id", id));
    }

    @GetMapping("/tenant-visibility")
    @RequirePerm("auth:visibility:view")
    public Result<Object> visibility() {
        return Result.ok(M.of("records", db.list("SELECT * FROM sys_tenant WHERE status='ENABLED'"),
                "grantCount", db.count("SELECT COUNT(*) FROM sec_audit_log WHERE action='GRANT_VISIBILITY'")));
    }

    @PostMapping("/tenant-visibility")
    @RequirePerm("auth:visibility:grant")
    public Result<Object> grantVisibility(@RequestBody Map<String, Object> body) {
        audit.record(me().getUsername(), "GRANT_VISIBILITY", "auth", String.valueOf(body.get("targetTenantId")),
                "跨租户血缘穿透授权", "-", "SUCCESS");
        return Result.ok(M.of("granted", true, "targetTenantId", body.get("targetTenantId"),
                "scope", body.getOrDefault("scope", "LINEAGE")));
    }

    @GetMapping("/data-scopes")
    @RequirePerm("auth:datascope:view")
    public Result<Object> dataScopes() {
        return Result.ok(M.of("records", db.list("SELECT * FROM sys_data_scope ORDER BY id")));
    }

    @PutMapping("/data-scopes/{roleId}")
    @RequirePerm("auth:datascope:edit")
    public Result<Object> updateDataScope(@PathVariable long roleId, @RequestBody Map<String, Object> body) {
        requireAdmin();
        // 修正：原实现 db.scalar(sql, id, "") 多传一个参数 → 异常被吞 → roleCode="null" 写脏数据
        Object code = db.scalar("SELECT code FROM sys_role WHERE id=?", roleId);
        if (code == null) throw new BizException(ErrorCode.NOT_FOUND, "角色不存在");
        String roleCode = String.valueOf(code);
        String scopeType = Req.str(body.getOrDefault("scopeType", "DOMAIN"));
        Object scopeValue = body.get("scopeValue");
        db.update("DELETE FROM sys_data_scope WHERE role_code=?", roleCode);
        db.update("INSERT INTO sys_data_scope(tenant_id,role_code,scope_type,scope_value,create_time) VALUES(?,?,?,?,?)",
                AuthContext.tenantId(), roleCode, scopeType, scopeValue, LocalDateTime.now());
        audit.record(me().getUsername(), "DATASCOPE_UPDATE", "auth", roleCode, scopeType, "-", "SUCCESS");
        return Result.ok(M.of("roleCode", roleCode, "scopeType", scopeType, "scopeValue", scopeValue));
    }

    @GetMapping("/orgs/tree")
    @RequirePerm("auth:org:view")
    public Result<Object> orgs() {
        List<Map<String, Object>> all = db.list("SELECT * FROM sys_org ORDER BY sort_no, id");
        Map<Long, List<Map<String, Object>>> byParent = new LinkedHashMap<>();
        for (Map<String, Object> o : all) {
            byParent.computeIfAbsent(Db.longVal(o.get("parentId"), 0L), k -> new ArrayList<>()).add(o);
        }
        return Result.ok(M.of("tree", attach(byParent.getOrDefault(0L, List.of()), byParent, new java.util.HashSet<>(), 0),
                "total", all.size()));
    }

    @PostMapping("/orgs")
    @RequirePerm("auth:org:create")
    public Result<Object> createOrg(@RequestBody Map<String, Object> body) {
        String name = Req.str(body.get("name"));
        if (Req.blank(name)) throw new BizException(ErrorCode.PARAM_INVALID, "组织名称不能为空");
        String code = Req.str(body.getOrDefault("code", "ORG" + System.currentTimeMillis()));
        if (!Db.isIdentifier(code)) throw new BizException(ErrorCode.PARAM_INVALID, "组织编码不合法");
        long id = db.insert("INSERT INTO sys_org(tenant_id,parent_id,code,name,sort_no,create_time) VALUES(?,?,?,?,?,?)",
                AuthContext.tenantId(), Db.longVal(body.getOrDefault("parentId", 0L), 0L), code, name,
                Db.longVal(body.getOrDefault("sortNo", 0L), 0L), LocalDateTime.now());
        return Result.ok(M.of("id", id, "code", code, "name", name));
    }
}