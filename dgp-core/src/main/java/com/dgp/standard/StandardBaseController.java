package com.dgp.standard;

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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 数据标准基础库（v1.7 / BF-07）：
 * 数据字典、代码集、常用词汇、模型标准四类“分组 + 条目”的维护接口。
 */
@RestController
@RequestMapping("/api/v1/standard/base")
public class StandardBaseController {

    private static final List<String> TYPES = List.of("DICT", "CODESET", "GLOSSARY", "MODEL_STD");

    private final Db db;

    public StandardBaseController(Db db) {
        this.db = db;
    }

    @GetMapping("/stats")
    @RequirePerm("standard:base:view")
    public Result<Object> stats() {
        Map<String, Object> out = new LinkedHashMap<>();
        for (String t : TYPES) {
            out.put(t, db.count("SELECT COUNT(*) FROM std_base_group WHERE type=?", t));
        }
        out.put("items", db.count("SELECT COUNT(*) FROM std_base_item"));
        return Result.ok(out);
    }

    @GetMapping("/groups")
    @RequirePerm("standard:base:view")
    public Result<Object> groups(@RequestParam(required = false) String type,
                                 @RequestParam(required = false) String keyword) {
        StringBuilder sql = new StringBuilder(
                "SELECT g.*, (SELECT COUNT(*) FROM std_base_item i WHERE i.group_id=g.id) AS item_count "
                        + "FROM std_base_group g WHERE 1=1");
        List<Object> args = new ArrayList<>();
        if (type != null && !type.isBlank()) {
            sql.append(" AND g.type=?");
            args.add(type);
        }
        if (keyword != null && !keyword.isBlank()) {
            sql.append(" AND (g.code LIKE ? OR g.name LIKE ?)");
            args.add("%" + keyword.trim() + "%");
            args.add("%" + keyword.trim() + "%");
        }
        sql.append(" ORDER BY g.type, g.id");
        return Result.ok(M.of("records", db.list(sql.toString(), args.toArray())));
    }

    @PostMapping("/groups")
    @RequirePerm("standard:base:edit")
    public Result<Object> createGroup(@RequestBody Map<String, Object> b) {
        String type = requireType(b.get("type"));
        String code = requireCode(b.get("code"));
        if (db.one("SELECT id FROM std_base_group WHERE type=? AND code=?", type, code) != null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "同类型下编码已存在：" + code);
        }
        long id = db.insert("INSERT INTO std_base_group(tenant_id,type,code,name,description,status,create_time,update_time) "
                        + "VALUES(?,?,?,?,?,'ENABLED',?,?)",
                AuthContext.tenantId(), type, code, b.get("name"), b.get("description"),
                LocalDateTime.now(), LocalDateTime.now());
        return Result.ok(M.of("id", id));
    }

    @PutMapping("/groups/{id}")
    @RequirePerm("standard:base:edit")
    public Result<Object> updateGroup(@PathVariable long id, @RequestBody Map<String, Object> b) {
        Map<String, Object> g = requireGroup(id);
        db.update("UPDATE std_base_group SET name=?,description=?,status=?,update_time=? WHERE id=?",
                b.containsKey("name") ? b.get("name") : g.get("name"),
                b.containsKey("description") ? b.get("description") : g.get("description"),
                b.containsKey("status") ? b.get("status") : g.get("status"),
                LocalDateTime.now(), id);
        return Result.ok(M.of("id", id));
    }

    @DeleteMapping("/groups/{id}")
    @RequirePerm("standard:base:edit")
    public Result<Object> deleteGroup(@PathVariable long id) {
        requireGroup(id);
        db.update("DELETE FROM std_base_item WHERE group_id=?", id);
        db.update("DELETE FROM std_base_group WHERE id=?", id);
        return Result.ok(M.of("deleted", true));
    }

    @GetMapping("/groups/{id}/items")
    @RequirePerm("standard:base:view")
    public Result<Object> items(@PathVariable long id, @RequestParam(required = false) String keyword) {
        requireGroup(id);
        StringBuilder sql = new StringBuilder("SELECT * FROM std_base_item WHERE group_id=?");
        List<Object> args = new ArrayList<>();
        args.add(id);
        if (keyword != null && !keyword.isBlank()) {
            sql.append(" AND (code LIKE ? OR name LIKE ?)");
            args.add("%" + keyword.trim() + "%");
            args.add("%" + keyword.trim() + "%");
        }
        sql.append(" ORDER BY sort_no, id");
        return Result.ok(M.of("records", db.list(sql.toString(), args.toArray())));
    }

    @PostMapping("/groups/{id}/items")
    @RequirePerm("standard:base:edit")
    public Result<Object> createItem(@PathVariable long id, @RequestBody Map<String, Object> b) {
        requireGroup(id);
        String code = requireCode(b.get("code"));
        if (db.one("SELECT id FROM std_base_item WHERE group_id=? AND code=?", id, code) != null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "条目编码已存在：" + code);
        }
        long itemId = db.insert("INSERT INTO std_base_item(group_id,code,name,description,sort_no,status,create_time) "
                        + "VALUES(?,?,?,?,?,'ENABLED',?)",
                id, code, b.get("name"), b.get("description"), b.getOrDefault("sortNo", 0), LocalDateTime.now());
        return Result.ok(M.of("id", itemId));
    }

    @PutMapping("/items/{id}")
    @RequirePerm("standard:base:edit")
    public Result<Object> updateItem(@PathVariable long id, @RequestBody Map<String, Object> b) {
        Map<String, Object> item = db.one("SELECT * FROM std_base_item WHERE id=?", id);
        if (item == null) throw new BizException(ErrorCode.NOT_FOUND, "条目不存在");
        db.update("UPDATE std_base_item SET name=?,description=?,sort_no=?,status=? WHERE id=?",
                b.containsKey("name") ? b.get("name") : item.get("name"),
                b.containsKey("description") ? b.get("description") : item.get("description"),
                b.containsKey("sortNo") ? b.get("sortNo") : item.get("sortNo"),
                b.containsKey("status") ? b.get("status") : item.get("status"), id);
        return Result.ok(M.of("id", id));
    }

    @DeleteMapping("/items/{id}")
    @RequirePerm("standard:base:edit")
    public Result<Object> deleteItem(@PathVariable long id) {
        db.update("DELETE FROM std_base_item WHERE id=?", id);
        return Result.ok(M.of("deleted", true));
    }

    /** 批量导入：前端把 CSV/Excel 解析成 [{code,name,description,sortNo}] 提交。 */
    @PostMapping("/groups/{id}/items/batch")
    @RequirePerm("standard:base:edit")
    public Result<Object> batchItems(@PathVariable long id, @RequestBody Map<String, Object> b) {
        requireGroup(id);
        Object raw = b.get("items");
        if (!(raw instanceof List<?> list) || list.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "导入数据不能为空");
        }
        int ok = 0, skip = 0;
        for (Object o : list) {
            if (!(o instanceof Map<?, ?> m)) {
                skip++;
                continue;
            }
            String code = Db.str(m.get("code"));
            String name = Db.str(m.get("name"));
            if (code == null || code.isBlank() || name == null || name.isBlank()) {
                skip++;
                continue;
            }
            if (db.one("SELECT id FROM std_base_item WHERE group_id=? AND code=?", id, code) != null) {
                skip++;
                continue;
            }
            db.insert("INSERT INTO std_base_item(group_id,code,name,description,sort_no,status,create_time) "
                            + "VALUES(?,?,?,?,?,'ENABLED',?)",
                    id, code, name, m.get("description"), m.containsKey("sortNo") ? m.get("sortNo") : 0, LocalDateTime.now());
            ok++;
        }
        return Result.ok(M.of("inserted", ok, "skipped", skip, "total", list.size()));
    }

    private Map<String, Object> requireGroup(long id) {
        Map<String, Object> g = db.one("SELECT * FROM std_base_group WHERE id=?", id);
        if (g == null) throw new BizException(ErrorCode.NOT_FOUND, "标准分组不存在");
        return g;
    }

    private static String requireType(Object value) {
        String t = Db.str(value);
        if (t == null || !TYPES.contains(t)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "类型只能是 DICT/CODESET/GLOSSARY/MODEL_STD");
        }
        return t;
    }

    private static String requireCode(Object value) {
        String c = Db.str(value);
        if (c == null || c.isBlank()) throw new BizException(ErrorCode.PARAM_INVALID, "编码不能为空");
        Db.identifier(c);
        return c.trim();
    }
}
