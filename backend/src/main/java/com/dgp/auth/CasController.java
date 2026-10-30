package com.dgp.auth;

import com.dgp.common.BizException;
import com.dgp.common.Db;
import com.dgp.common.ErrorCode;
import com.dgp.common.M;
import com.dgp.common.Result;
import com.dgp.security.AuthContext;
import com.dgp.security.AuthService;
import com.dgp.security.RequirePerm;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * CAS 2.0/3.0 客户端接入（P1）：
 * /auth/cas/login 跳转 CAS，/auth/cas/callback 校验 ticket 并换一次性 code，
 * 前端用 /auth/cas/exchange 换取 JWT（避免令牌出现在浏览器地址栏）。
 */
@RestController
@RequestMapping("/api/v1")
public class CasController {

    private static final Pattern CAS_USER = Pattern.compile("<cas:user>(.*?)</cas:user>", Pattern.DOTALL);
    private static final Map<String, Object[]> CODES = new ConcurrentHashMap<>();
    private final Db db;
    private final AuthService authService;

    public CasController(Db db, AuthService authService) {
        this.db = db;
        this.authService = authService;
    }

    @GetMapping("/auth/cas/status")
    public Result<Object> status() {
        Map<String, Object> c = config();
        boolean enabled = c != null && Db.longVal(c.get("enabled"), 0) == 1;
        return Result.ok(M.of("enabled", enabled,
                "portalCode", c == null ? null : c.get("portalCode"),
                "loginUrl", enabled ? "/api/v1/auth/cas/login" : null));
    }

    @GetMapping("/auth/cas/login")
    public void login(HttpServletRequest req, HttpServletResponse resp) throws Exception {
        Map<String, Object> c = requireEnabled();
        String base = baseUrl(c, req);
        String service = base + "/api/v1/auth/cas/callback?portal=" + enc(String.valueOf(c.get("portalCode")));
        String url = String.valueOf(c.get("serverUrl")).replaceAll("/+$", "")
                + "/login?service=" + enc(service);
        resp.sendRedirect(url);
    }

    @GetMapping("/auth/cas/callback")
    public void callback(@RequestParam(required = false) String ticket,
                         @RequestParam(required = false) String portal,
                         HttpServletRequest req, HttpServletResponse resp) throws Exception {
        Map<String, Object> c = requireEnabled();
        String base = baseUrl(c, req);
        String service = base + "/api/v1/auth/cas/callback?portal=" + enc(portal == null ? String.valueOf(c.get("portalCode")) : portal);
        String username = validate(c, service, ticket);
        if (username == null || username.isBlank()) {
            resp.sendRedirect(base + "/#/login?casError=1");
            return;
        }
        Map<String, Object> user = db.one("SELECT * FROM sys_user WHERE username=?", username);
        if (user == null) {
            if (Db.longVal(c.get("autoCreate"), 0) != 1) {
                resp.sendRedirect(base + "/#/login?casError=2");
                return;
            }
            db.insert("INSERT INTO sys_user(tenant_id,username,password,real_name,role_code,dept,status,create_time) "
                            + "VALUES(?,?,?,?,?,?,?,?)",
                    AuthContext.tenantId(), username, AuthService.hash(UUID.randomUUID().toString()),
                    username, c.get("defaultRole"), "CAS", "启用", LocalDateTime.now());
        }
        String role = null;
        String portalCode = portal == null || portal.isBlank() ? String.valueOf(c.get("portalCode")) : portal;
        try {
            Map<String, Object> pr = db.one(
                    "SELECT role_code FROM sys_portal_role WHERE portal_code=? AND status='ENABLED' LIMIT 1", portalCode);
            if (pr != null) role = String.valueOf(pr.get("roleCode"));
        } catch (Exception ignore) {
        }
        if (role == null || role.isBlank()) role = String.valueOf(c.get("defaultRole"));
        Map<String, Object> tokens = authService.externalLogin(username, role, username,
                req.getRemoteAddr(), req.getHeader("User-Agent"));
        String code = UUID.randomUUID().toString().replace("-", "");
        CODES.put(code, new Object[]{tokens, System.currentTimeMillis() + 60_000});
        resp.sendRedirect(base + "/#/cas-callback?code=" + code);
    }

    /** 一次性 code 换取令牌（60 秒有效，使用即删除）。 */
    @PostMapping("/auth/cas/exchange")
    public Result<Object> exchange(@RequestBody Map<String, Object> body) {
        String code = Db.str(body.get("code"));
        if (code == null) throw new BizException(ErrorCode.PARAM_INVALID, "code 不能为空");
        Object[] v = CODES.remove(code);
        if (v == null || ((Long) v[1]) < System.currentTimeMillis()) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "CAS 登录 code 无效或已过期");
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> tokens = (Map<String, Object>) v[0];
        return Result.ok(tokens);
    }

    // ---------------- 管理端 ----------------
    @GetMapping("/admin/cas/config")
    @RequirePerm("sys:cas:view")
    public Result<Object> getConfig() {
        Map<String, Object> c = config();
        if (c == null) {
            c = M.of("enabled", 0, "serverUrl", "", "serviceUrl", "", "portalCode", "GOV_PORTAL",
                    "autoCreate", 1, "defaultRole", "ROLE_ANALYST", "attributeUser", "user",
                    "attributeName", "displayName", "attributeEmail", "mail");
        }
        return Result.ok(c);
    }

    @PutMapping("/admin/cas/config")
    @RequirePerm("sys:cas:edit")
    public Result<Object> saveConfig(@RequestBody Map<String, Object> b) {
        Map<String, Object> exist = config();
        if (exist == null) {
            db.insert("INSERT INTO sys_cas_config(enabled,server_url,service_url,portal_code,auto_create,default_role,"
                            + "attribute_user,attribute_name,attribute_email,update_time,update_by) "
                            + "VALUES(?,?,?,?,?,?,?,?,?,?,?)",
                    b.getOrDefault("enabled", 0), b.get("serverUrl"), b.get("serviceUrl"), b.get("portalCode"),
                    b.getOrDefault("autoCreate", 1), b.getOrDefault("defaultRole", "ROLE_ANALYST"),
                    b.getOrDefault("attributeUser", "user"), b.getOrDefault("attributeName", "displayName"),
                    b.getOrDefault("attributeEmail", "mail"), LocalDateTime.now(), AuthContext.username());
        } else {
            db.update("UPDATE sys_cas_config SET enabled=?,server_url=?,service_url=?,portal_code=?,auto_create=?,"
                            + "default_role=?,attribute_user=?,attribute_name=?,attribute_email=?,update_time=?,update_by=? "
                            + "WHERE id=?",
                    b.containsKey("enabled") ? b.get("enabled") : exist.get("enabled"),
                    b.containsKey("serverUrl") ? b.get("serverUrl") : exist.get("serverUrl"),
                    b.containsKey("serviceUrl") ? b.get("serviceUrl") : exist.get("serviceUrl"),
                    b.containsKey("portalCode") ? b.get("portalCode") : exist.get("portalCode"),
                    b.containsKey("autoCreate") ? b.get("autoCreate") : exist.get("autoCreate"),
                    b.containsKey("defaultRole") ? b.get("defaultRole") : exist.get("defaultRole"),
                    b.containsKey("attributeUser") ? b.get("attributeUser") : exist.get("attributeUser"),
                    b.containsKey("attributeName") ? b.get("attributeName") : exist.get("attributeName"),
                    b.containsKey("attributeEmail") ? b.get("attributeEmail") : exist.get("attributeEmail"),
                    LocalDateTime.now(), AuthContext.username(), exist.get("id"));
        }
        return Result.ok(M.of("saved", true));
    }

    @GetMapping("/admin/portals")
    @RequirePerm("sys:cas:view")
    public Result<Object> portals() {
        return Result.ok(M.of("records", db.list("SELECT * FROM sys_portal ORDER BY id")));
    }

    @PostMapping("/admin/portals")
    @RequirePerm("sys:cas:edit")
    public Result<Object> createPortal(@RequestBody Map<String, Object> b) {
        String code = Db.str(b.get("code"));
        if (code == null || code.isBlank()) throw new BizException(ErrorCode.PARAM_INVALID, "门户编码不能为空");
        if (db.one("SELECT id FROM sys_portal WHERE code=?", code) != null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "门户编码已存在");
        }
        long id = db.insert("INSERT INTO sys_portal(code,name,domain,status,remark,create_time) VALUES(?,?,?,?,?,?)",
                code, b.get("name"), b.get("domain"), b.getOrDefault("status", "ENABLED"), b.get("remark"),
                LocalDateTime.now());
        return Result.ok(M.of("id", id));
    }

    @PutMapping("/admin/portals/{id}")
    @RequirePerm("sys:cas:edit")
    public Result<Object> updatePortal(@PathVariable long id, @RequestBody Map<String, Object> b) {
        Map<String, Object> r = db.one("SELECT * FROM sys_portal WHERE id=?", id);
        if (r == null) throw new BizException(ErrorCode.NOT_FOUND, "门户不存在");
        db.update("UPDATE sys_portal SET name=?,domain=?,status=?,remark=? WHERE id=?",
                b.containsKey("name") ? b.get("name") : r.get("name"),
                b.containsKey("domain") ? b.get("domain") : r.get("domain"),
                b.containsKey("status") ? b.get("status") : r.get("status"),
                b.containsKey("remark") ? b.get("remark") : r.get("remark"), id);
        return Result.ok(M.of("id", id));
    }

    @DeleteMapping("/admin/portals/{id}")
    @RequirePerm("sys:cas:edit")
    public Result<Object> deletePortal(@PathVariable long id) {
        db.update("DELETE FROM sys_portal WHERE id=?", id);
        return Result.ok(M.of("deleted", true));
    }

    // ---------------- helpers ----------------
    private Map<String, Object> config() {
        try {
            return db.one("SELECT * FROM sys_cas_config ORDER BY id LIMIT 1");
        } catch (Exception e) {
            return null;
        }
    }

    private Map<String, Object> requireEnabled() {
        Map<String, Object> c = config();
        if (c == null || Db.longVal(c.get("enabled"), 0) != 1) {
            throw new BizException(ErrorCode.PARAM_INVALID, "CAS 未启用");
        }
        if (Db.str(c.get("serverUrl")) == null) throw new BizException(ErrorCode.PARAM_INVALID, "CAS 服务地址未配置");
        return c;
    }

    private String validate(Map<String, Object> c, String service, String ticket) throws Exception {
        if (ticket == null || ticket.isBlank()) return null;
        String url = String.valueOf(c.get("serverUrl")).replaceAll("/+$", "")
                + "/serviceValidate?service=" + enc(service) + "&ticket=" + enc(ticket);
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();
        HttpResponse<String> resp = client.send(HttpRequest.newBuilder(URI.create(url)).GET().timeout(Duration.ofSeconds(10)).build(),
                HttpResponse.BodyHandlers.ofString());
        Matcher m = CAS_USER.matcher(resp.body());
        return m.find() ? m.group(1).trim() : null;
    }

    private static String baseUrl(Map<String, Object> c, HttpServletRequest req) {
        String configured = Db.str(c.get("serviceUrl"));
        if (configured != null && !configured.isBlank()) return configured.replaceAll("/+$", "");
        String proto = req.getHeader("X-Forwarded-Proto");
        if (proto == null || proto.isBlank()) proto = req.getScheme();
        return proto + "://" + req.getServerName() + (req.getServerPort() > 0 ? ":" + req.getServerPort() : "");
    }

    private static String enc(String s) {
        return URLEncoder.encode(s == null ? "" : s, StandardCharsets.UTF_8);
    }
}
