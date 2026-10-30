package com.dgp.admin;

import com.dgp.auth.MenuCatalog;
import com.dgp.common.BizException;
import com.dgp.common.Db;
import com.dgp.common.ErrorCode;
import com.dgp.common.M;
import com.dgp.common.Result;
import com.dgp.security.AuthContext;
import com.dgp.security.RequirePerm;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** BF-10：菜单树维护、角色-菜单映射、门户-角色映射。 */
@RestController
@RequestMapping("/api/v1/admin/menus")
public class MenuAdminController {

    private final Db db;

    public MenuAdminController(Db db) {
        this.db = db;
    }

    @GetMapping
    @RequirePerm("sys:menu:view")
    public Result<Object> menus() {
        seedIfEmpty();
        return Result.ok(M.of("records", db.list("SELECT * FROM sys_menu ORDER BY sort_no, id")));
    }

    @PostMapping
    @RequirePerm("sys:menu:edit")
    public Result<Object> create(@RequestBody Map<String, Object> b) {
        String code = Db.str(b.get("code"));
        String title = Db.str(b.get("title"));
        if (code == null || code.isBlank() || title == null || title.isBlank()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "菜单编码与标题不能为空");
        }
        if (db.one("SELECT id FROM sys_menu WHERE code=?", code) != null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "菜单编码已存在");
        }
        long id = db.insert("INSERT INTO sys_menu(tenant_id,code,title,path,icon,perm,parent_code,sort_no,status,visible,"
                        + "create_time,update_time) VALUES(?,?,?,?,?,?,?,?,?,?,?,?)",
                AuthContext.tenantId(), code, title, b.get("path"), b.get("icon"), b.get("perm"),
                b.get("parentCode"), b.getOrDefault("sortNo", 0), b.getOrDefault("status", "ENABLED"),
                b.getOrDefault("visible", 1), LocalDateTime.now(), LocalDateTime.now());
        return Result.ok(M.of("id", id));
    }

    @PutMapping("/{id}")
    @RequirePerm("sys:menu:edit")
    public Result<Object> update(@PathVariable long id, @RequestBody Map<String, Object> b) {
        Map<String, Object> r = db.one("SELECT * FROM sys_menu WHERE id=?", id);
        if (r == null) throw new BizException(ErrorCode.NOT_FOUND, "菜单不存在");
        db.update("UPDATE sys_menu SET title=?,path=?,icon=?,perm=?,parent_code=?,sort_no=?,status=?,visible=?,"
                        + "update_time=? WHERE id=?",
                b.containsKey("title") ? b.get("title") : r.get("title"),
                b.containsKey("path") ? b.get("path") : r.get("path"),
                b.containsKey("icon") ? b.get("icon") : r.get("icon"),
                b.containsKey("perm") ? b.get("perm") : r.get("perm"),
                b.containsKey("parentCode") ? b.get("parentCode") : r.get("parentCode"),
                b.containsKey("sortNo") ? b.get("sortNo") : r.get("sortNo"),
                b.containsKey("status") ? b.get("status") : r.get("status"),
                b.containsKey("visible") ? b.get("visible") : r.get("visible"), LocalDateTime.now(), id);
        return Result.ok(M.of("id", id));
    }

    @DeleteMapping("/{id}")
    @RequirePerm("sys:menu:edit")
    public Result<Object> delete(@PathVariable long id) {
        Map<String, Object> r = db.one("SELECT * FROM sys_menu WHERE id=?", id);
        if (r == null) return Result.ok(M.of("deleted", true));
        db.update("DELETE FROM sys_role_menu WHERE menu_code=?", r.get("code"));
        db.update("DELETE FROM sys_menu WHERE id=?", id);
        return Result.ok(M.of("deleted", true));
    }

    // ---------------- 角色-菜单 ----------------
    @GetMapping("/roles/{roleCode}")
    @RequirePerm("sys:menu:view")
    public Result<Object> roleMenus(@PathVariable String roleCode) {
        List<Map<String, Object>> rows = db.list("SELECT menu_code FROM sys_role_menu WHERE role_code=?", roleCode);
        List<String> codes = new ArrayList<>();
        for (Map<String, Object> r : rows) codes.add(String.valueOf(r.get("menuCode")));
        return Result.ok(M.of("roleCode", roleCode, "menuCodes", codes));
    }

    @PostMapping("/roles/{roleCode}")
    @RequirePerm("sys:menu:edit")
    public Result<Object> saveRoleMenus(@PathVariable String roleCode, @RequestBody Map<String, Object> b) {
        Object raw = b.get("menuCodes");
        List<?> codes = raw instanceof List<?> l ? l : List.of();
        db.update("DELETE FROM sys_role_menu WHERE role_code=?", roleCode);
        for (Object c : codes) {
            db.insert("INSERT INTO sys_role_menu(role_code,menu_code) VALUES(?,?)", roleCode, String.valueOf(c));
        }
        return Result.ok(M.of("roleCode", roleCode, "count", codes.size()));
    }

    // ---------------- 门户-角色 ----------------
    @GetMapping("/portals")
    @RequirePerm("sys:menu:view")
    public Result<Object> portals() {
        return Result.ok(M.of("records", db.list("SELECT * FROM sys_portal_role ORDER BY portal_code, role_code")));
    }

    @PostMapping("/portals")
    @RequirePerm("sys:menu:edit")
    public Result<Object> addPortalRole(@RequestBody Map<String, Object> b) {
        String portal = Db.str(b.get("portalCode"));
        String role = Db.str(b.get("roleCode"));
        if (portal == null || portal.isBlank() || role == null || role.isBlank()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "门户与角色不能为空");
        }
        db.update("DELETE FROM sys_portal_role WHERE portal_code=? AND role_code=?", portal, role);
        db.insert("INSERT INTO sys_portal_role(portal_code,role_code,status,remark,create_time) VALUES(?,?,?,?,?)",
                portal, role, b.getOrDefault("status", "ENABLED"), b.get("remark"), LocalDateTime.now());
        return Result.ok(M.of("portalCode", portal, "roleCode", role));
    }

    @DeleteMapping("/portals/{portalCode}/{roleCode}")
    @RequirePerm("sys:menu:edit")
    public Result<Object> removePortalRole(@PathVariable String portalCode, @PathVariable String roleCode) {
        db.update("DELETE FROM sys_portal_role WHERE portal_code=? AND role_code=?", portalCode, roleCode);
        return Result.ok(M.of("deleted", true));
    }

    private void seedIfEmpty() {
        try {
            if (db.count("SELECT COUNT(*) FROM sys_menu") > 0) return;
            int i = 0;
            for (M m : MenuCatalog.all()) {
                String domain = String.valueOf(m.get("domain"));
                String parent = "portal".equals(domain) ? null : domain;
                db.insert("INSERT INTO sys_menu(tenant_id,code,title,path,icon,perm,parent_code,sort_no,status,visible,"
                                + "create_time,update_time) VALUES(?,?,?,?,?,?,?,?,'ENABLED',1,?,?)",
                        AuthContext.tenantId(), m.get("code"), m.get("name"), m.get("path"), m.get("icon"),
                        m.get("perm"), parent, i += 10, LocalDateTime.now(), LocalDateTime.now());
            }
        } catch (Exception ignore) {
        }
    }
}
