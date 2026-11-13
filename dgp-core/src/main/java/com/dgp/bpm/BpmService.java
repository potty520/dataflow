package com.dgp.bpm;

import com.dgp.common.BizException;
import com.dgp.common.Db;
import com.dgp.common.ErrorCode;
import com.dgp.common.JsonUtil;
import com.dgp.common.M;
import com.dgp.security.AuthContext;
import com.dgp.security.AuthUser;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * BPM 引擎（P1）：顺序节点 + 条件分支 + 会签/加签。
 * 节点 JSON：
 * {"nodes":[
 *   {"id":"start","type":"start"},
 *   {"id":"a1","type":"approval","name":"经理审批","assignees":[{"type":"ROLE","value":"ROLE_GOV_ADMIN"}],
 *    "condition":{"field":"amount","op":">=","value":1000}},
 *   {"id":"a2","type":"approval","name":"会签","approvalMode":"ALL",
 *    "assignees":[{"type":"USER","value":"admin"},{"type":"USER","value":"liuqin"}]},
 *   {"id":"end","type":"end"}]}
 */
@Service
public class BpmService {

    private final Db db;

    public BpmService(Db db) {
        this.db = db;
    }

    public List<Map<String, Object>> definitions() {
        return db.list("SELECT * FROM bpm_process_def ORDER BY id");
    }

    public Map<String, Object> createDefinition(Map<String, Object> b) {
        String code = Db.str(b.get("code"));
        String name = Db.str(b.get("name"));
        if (code == null || code.isBlank() || name == null || name.isBlank()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "流程编码与名称不能为空");
        }
        if (db.one("SELECT id FROM bpm_process_def WHERE code=?", code) != null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "流程编码已存在");
        }
        long id = db.insert("INSERT INTO bpm_process_def(tenant_id,code,name,version,status,nodes_json,remark,create_time,update_time) "
                        + "VALUES(?,?,?,1,'DRAFT',?,?,?,?)",
                AuthContext.tenantId(), code, name, JsonUtil.toJson(b.getOrDefault("nodes", List.of())),
                b.get("remark"), LocalDateTime.now(), LocalDateTime.now());
        return M.of("id", id);
    }

    public Map<String, Object> updateDefinition(long id, Map<String, Object> b) {
        Map<String, Object> d = db.one("SELECT * FROM bpm_process_def WHERE id=?", id);
        if (d == null) throw new BizException(ErrorCode.NOT_FOUND, "流程定义不存在");
        if ("PUBLISHED".equals(d.get("status"))) {
            throw new BizException(ErrorCode.PARAM_INVALID, "已发布流程不可直接修改，请复制新版本");
        }
        db.update("UPDATE bpm_process_def SET name=?,nodes_json=?,remark=?,update_time=? WHERE id=?",
                b.containsKey("name") ? b.get("name") : d.get("name"),
                b.containsKey("nodes") ? JsonUtil.toJson(b.get("nodes")) : d.get("nodesJson"),
                b.containsKey("remark") ? b.get("remark") : d.get("remark"), LocalDateTime.now(), id);
        return M.of("id", id);
    }

    public void publish(long id) {
        Map<String, Object> d = db.one("SELECT * FROM bpm_process_def WHERE id=?", id);
        if (d == null) throw new BizException(ErrorCode.NOT_FOUND, "流程定义不存在");
        if (nodes(d).stream().noneMatch(n -> "approval".equals(String.valueOf(n.get("type"))))) {
            throw new BizException(ErrorCode.PARAM_INVALID, "流程至少需要一个审批节点");
        }
        db.update("UPDATE bpm_process_def SET status='PUBLISHED',version=version+1,update_time=? WHERE id=?",
                LocalDateTime.now(), id);
    }

    public void deleteDefinition(long id) {
        if (db.count("SELECT COUNT(*) FROM bpm_instance WHERE def_id=?", id) > 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "已有审批实例引用该流程，不能删除");
        }
        db.update("DELETE FROM bpm_process_def WHERE id=?", id);
    }

    public Map<String, Object> start(String defCode, String title, String businessType, String businessId,
                                     Object form, String starter) {
        Map<String, Object> d = db.one("SELECT * FROM bpm_process_def WHERE code=? AND status='PUBLISHED'", defCode);
        if (d == null) throw new BizException(ErrorCode.PARAM_INVALID, "流程不存在或未发布");
        @SuppressWarnings("unchecked")
        Map<String, Object> formMap = form instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of();
        Map<String, Object> first = firstApproval(d, null, formMap);
        long instanceId = db.insert("INSERT INTO bpm_instance(tenant_id,def_id,def_code,def_name,title,business_type,"
                        + "business_id,form_json,status,current_node,starter,start_time) VALUES(?,?,?,?,?,?,?,?,?,?,?,?)",
                AuthContext.tenantId(), d.get("id"), defCode, d.get("name"),
                title == null || title.isBlank() ? d.get("name") + " 审批" : title,
                businessType, businessId, JsonUtil.toJson(formMap),
                first == null ? "APPROVED" : "RUNNING", first == null ? null : first.get("id"),
                starter, LocalDateTime.now());
        if (first == null) {
            db.update("UPDATE bpm_instance SET end_time=? WHERE id=?", LocalDateTime.now(), instanceId);
        } else {
            createTasks(instanceId, first);
        }
        return M.of("id", instanceId, "status", first == null ? "APPROVED" : "RUNNING");
    }

    public List<Map<String, Object>> todo() {
        AuthUser u = AuthContext.get();
        boolean admin = u != null && "ROLE_GOV_ADMIN".equals(u.getRoleCode());
        String sql = "SELECT t.*, i.title, i.def_name, i.starter, i.start_time, i.business_type, i.business_id,"
                + " i.form_json FROM bpm_task t JOIN bpm_instance i ON i.id=t.instance_id "
                + "WHERE t.status='PENDING'";
        if (admin) {
            return db.list(sql + " ORDER BY t.id DESC");
        }
        return db.list(sql + " AND (t.assignee=? OR t.assignee_role=?) ORDER BY t.id DESC",
                AuthContext.username(), u == null ? "" : u.getRoleCode());
    }

    public List<Map<String, Object>> mine() {
        return db.list("SELECT * FROM bpm_instance WHERE starter=? ORDER BY id DESC", AuthContext.username());
    }

    public Map<String, Object> detail(long id) {
        Map<String, Object> inst = db.one("SELECT * FROM bpm_instance WHERE id=?", id);
        if (inst == null) throw new BizException(ErrorCode.NOT_FOUND, "审批实例不存在");
        inst.put("tasks", db.list("SELECT * FROM bpm_task WHERE instance_id=? ORDER BY id", id));
        return inst;
    }

    public void approve(long taskId, String comment) {
        Map<String, Object> task = handle(taskId);
        Map<String, Object> inst = db.one("SELECT * FROM bpm_instance WHERE id=?", task.get("instanceId"));
        db.update("UPDATE bpm_task SET status='APPROVED',comment=?,finish_time=?,operator=? WHERE id=?",
                comment, LocalDateTime.now(), AuthContext.username(), taskId);
        long pending = db.count("SELECT COUNT(*) FROM bpm_task WHERE instance_id=? AND node_id=? AND status='PENDING'",
                task.get("instanceId"), task.get("nodeId"));
        if (pending > 0) return; // 会签：还有人未处理
        Map<String, Object> def = db.one("SELECT * FROM bpm_process_def WHERE id=?", inst.get("defId"));
        Map<String, Object> form = JsonUtil.parse(String.valueOf(inst.get("formJson")));
        Map<String, Object> next = firstApproval(def, String.valueOf(task.get("nodeId")), form);
        if (next == null) {
            db.update("UPDATE bpm_instance SET status='APPROVED',end_time=?,current_node=NULL WHERE id=?",
                    LocalDateTime.now(), inst.get("id"));
            completeBusiness(inst);
        } else {
            db.update("UPDATE bpm_instance SET current_node=? WHERE id=?", next.get("id"), inst.get("id"));
            createTasks(((Number) inst.get("id")).longValue(), next);
        }
    }

    /** 加签：同一节点追加一名审批人，原审批人仍需处理（转为会签）。 */
    public Map<String, Object> addSign(long taskId, String assignee, String assigneeRole) {
        Map<String, Object> task = handle(taskId);
        if ((assignee == null || assignee.isBlank()) && (assigneeRole == null || assigneeRole.isBlank())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "加签对象不能为空");
        }
        db.update("UPDATE bpm_task SET approval_mode='ALL' WHERE instance_id=? AND node_id=? AND status='PENDING'",
                task.get("instanceId"), task.get("nodeId"));
        long id = db.insert("INSERT INTO bpm_task(instance_id,node_id,node_name,assignee_role,assignee,status,approval_mode,create_time) "
                        + "VALUES(?,?,?,?,?,'PENDING','ALL',?)",
                task.get("instanceId"), task.get("nodeId"), task.get("nodeName"), assigneeRole, assignee, LocalDateTime.now());
        return M.of("id", id, "nodeId", task.get("nodeId"));
    }

    public void reject(long taskId, String comment) {
        Map<String, Object> task = handle(taskId);
        db.update("UPDATE bpm_task SET status='REJECTED',comment=?,finish_time=?,operator=? WHERE id=?",
                comment, LocalDateTime.now(), AuthContext.username(), taskId);
        db.update("UPDATE bpm_task SET status='CANCELED' WHERE instance_id=? AND status='PENDING'", task.get("instanceId"));
        db.update("UPDATE bpm_instance SET status='REJECTED',end_time=? WHERE id=?",
                LocalDateTime.now(), task.get("instanceId"));
    }

    public void transfer(long taskId, String assignee, String assigneeRole, String comment) {
        handle(taskId);
        if ((assignee == null || assignee.isBlank()) && (assigneeRole == null || assigneeRole.isBlank())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "转办对象不能为空");
        }
        db.update("UPDATE bpm_task SET assignee=?,assignee_role=?,comment=? WHERE id=?",
                assignee, assigneeRole, comment, taskId);
    }

    // ---------------- 业务挂接 ----------------
    public List<Map<String, Object>> hooks() {
        return db.list("SELECT * FROM bpm_business_hook ORDER BY id");
    }

    public Map<String, Object> saveHook(Map<String, Object> b) {
        String type = Db.str(b.get("businessType"));
        String defCode = Db.str(b.get("defCode"));
        if (type == null || type.isBlank() || defCode == null || defCode.isBlank()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "业务类型与流程编码不能为空");
        }
        Map<String, Object> exist = db.one("SELECT * FROM bpm_business_hook WHERE business_type=?", type);
        if (exist == null) {
            long id = db.insert("INSERT INTO bpm_business_hook(business_type,def_code,enabled,remark,create_time,update_time) "
                    + "VALUES(?,?,?,?,?,?)", type, defCode, b.getOrDefault("enabled", 0), b.get("remark"),
                    LocalDateTime.now(), LocalDateTime.now());
            return M.of("id", id);
        }
        db.update("UPDATE bpm_business_hook SET def_code=?,enabled=?,remark=?,update_time=? WHERE business_type=?",
                defCode, b.containsKey("enabled") ? b.get("enabled") : exist.get("enabled"),
                b.containsKey("remark") ? b.get("remark") : exist.get("remark"), LocalDateTime.now(), type);
        return M.of("id", exist.get("id"));
    }

    public void deleteHook(long id) { db.update("DELETE FROM bpm_business_hook WHERE id=?", id); }

    public Map<String, Object> startBusiness(String businessType, String businessId, String title, Object form) {
        Map<String, Object> hook = db.one("SELECT * FROM bpm_business_hook WHERE business_type=? AND enabled=1", businessType);
        if (hook == null) return null;
        Map<String, Object> def = db.one("SELECT * FROM bpm_process_def WHERE code=? AND status='PUBLISHED'", hook.get("defCode"));
        if (def == null) return null;
        Map<String, Object> out = start(String.valueOf(hook.get("defCode")), title, businessType, businessId, form, AuthContext.username());
        out.put("defCode", hook.get("defCode"));
        return out;
    }

    private void completeBusiness(Map<String, Object> inst) {
        String type = String.valueOf(inst.get("businessType"));
        String bizId = inst.get("businessId") == null ? "" : String.valueOf(inst.get("businessId"));
        try {
            if ("STANDARD".equals(type)) {
                db.update("UPDATE std_element SET status='PUBLISHED' WHERE id=?", Db.longVal(bizId, 0L));
            } else if ("MODEL".equals(type)) {
                db.update("UPDATE mdl_model SET status='PUBLISHED' WHERE id=?", Db.longVal(bizId, 0L));
            }
        } catch (Exception ignore) {
        }
    }

    // ---------------- helpers ----------------
    private Map<String, Object> handle(long taskId) {
        Map<String, Object> task = db.one("SELECT * FROM bpm_task WHERE id=?", taskId);
        if (task == null) throw new BizException(ErrorCode.NOT_FOUND, "审批任务不存在");
        if (!"PENDING".equals(task.get("status"))) {
            throw new BizException(ErrorCode.PARAM_INVALID, "任务已处理");
        }
        if (!canHandle(task)) throw new BizException(ErrorCode.FORBIDDEN, "无权处理该审批任务");
        return task;
    }

    private boolean canHandle(Map<String, Object> task) {
        AuthUser u = AuthContext.get();
        if (u == null) return false;
        if ("ROLE_GOV_ADMIN".equals(u.getRoleCode())) return true;
        String assignee = task.get("assignee") == null ? null : String.valueOf(task.get("assignee"));
        String role = task.get("assigneeRole") == null ? null : String.valueOf(task.get("assigneeRole"));
        return (assignee != null && assignee.equals(u.getUsername()))
                || (role != null && role.equals(u.getRoleCode()));
    }

    /** 返回 afterNodeId 之后第一个“条件成立”的审批节点。 */
    private Map<String, Object> firstApproval(Map<String, Object> def, String afterNodeId, Map<String, Object> form) {
        List<Map<String, Object>> nodes = nodes(def);
        boolean after = afterNodeId == null;
        for (Map<String, Object> n : nodes) {
            if (!after) {
                if (String.valueOf(n.get("id")).equals(afterNodeId)) after = true;
                continue;
            }
            if (!"approval".equals(String.valueOf(n.get("type")))) continue;
            if (conditionPass(n, form)) return n;
        }
        return null;
    }

    private boolean conditionPass(Map<String, Object> node, Map<String, Object> form) {
        Object raw = node.get("condition");
        if (!(raw instanceof Map<?, ?> c)) return true;
        String field = String.valueOf(c.get("field"));
        Object actual = form.get(field);
        if (actual == null) return false;
        Object opRaw = c.get("op");
        String op = opRaw == null ? "=" : String.valueOf(opRaw);
        Object expected = c.get("value");
        if (isNumber(actual) && isNumber(expected)) {
            double a = Double.parseDouble(String.valueOf(actual));
            double b = Double.parseDouble(String.valueOf(expected));
            return switch (op) {
                case ">" -> a > b;
                case ">=" -> a >= b;
                case "<" -> a < b;
                case "<=" -> a <= b;
                case "!=" -> a != b;
                default -> a == b;
            };
        }
        String a = String.valueOf(actual);
        String b = String.valueOf(expected);
        return switch (op) {
            case "!=" -> !a.equals(b);
            case "contains" -> a.contains(b);
            default -> a.equals(b);
        };
    }

    private static boolean isNumber(Object v) {
        if (v == null) return false;
        try {
            Double.parseDouble(String.valueOf(v));
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> nodes(Map<String, Object> def) {
        try {
            Object raw = def.get("nodesJson");
            if (raw == null) return new ArrayList<>();
            Map<String, Object> parsed = JsonUtil.parse(String.valueOf(raw));
            Object list = parsed.get("nodes");
            if (list == null && parsed.containsKey("id")) return List.of(parsed);
            return list instanceof List<?> l ? (List<Map<String, Object>>) l : new ArrayList<>();
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> assignees(Map<String, Object> node) {
        Object raw = node.get("assignees");
        if (raw instanceof List<?> l && !l.isEmpty()) {
            List<Map<String, Object>> out = new ArrayList<>();
            for (Object o : l) {
                if (o instanceof Map<?, ?> m) out.add((Map<String, Object>) m);
            }
            return out;
        }
        String type = String.valueOf(node.getOrDefault("assigneeType", "USER"));
        String who = String.valueOf(node.getOrDefault("assignee", AuthContext.username()));
        return List.of(M.of("type", type, "value", who));
    }

    private void createTasks(long instanceId, Map<String, Object> node) {
        String mode = String.valueOf(node.getOrDefault("approvalMode", "SINGLE"));
        List<Map<String, Object>> list = assignees(node);
        if (list.size() > 1) mode = "ALL";
        for (Map<String, Object> a : list) {
            String type = String.valueOf(a.getOrDefault("type", "USER"));
            String who = String.valueOf(a.getOrDefault("value", AuthContext.username()));
            String assignee = "ROLE".equalsIgnoreCase(type) ? null : who;
            String role = "ROLE".equalsIgnoreCase(type) ? who : null;
            db.insert("INSERT INTO bpm_task(instance_id,node_id,node_name,assignee_role,assignee,status,approval_mode,create_time) "
                            + "VALUES(?,?,?,?,?,'PENDING',?,?)",
                    instanceId, node.get("id"), node.getOrDefault("name", node.get("id")), role, assignee, mode,
                    LocalDateTime.now());
        }
    }
}
