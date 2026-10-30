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
 * 最小可用 BPM 引擎（P1）：
 * 流程定义 = 顺序节点（start / approval / end），审批节点按用户或角色派单；
 * 支持发起、通过、驳回、转办与时间线查询。
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
        Map<String, Object> first = firstApproval(d, null);
        long instanceId = db.insert("INSERT INTO bpm_instance(tenant_id,def_id,def_code,def_name,title,business_type,"
                        + "business_id,form_json,status,current_node,starter,start_time) VALUES(?,?,?,?,?,?,?,?,?,?,?,?)",
                AuthContext.tenantId(), d.get("id"), defCode, d.get("name"),
                title == null || title.isBlank() ? d.get("name") + " 审批" : title,
                businessType, businessId, JsonUtil.toJson(form == null ? Map.of() : form),
                first == null ? "APPROVED" : "RUNNING", first == null ? null : first.get("id"),
                starter, LocalDateTime.now());
        if (first == null) {
            db.update("UPDATE bpm_instance SET end_time=? WHERE id=?", LocalDateTime.now(), instanceId);
        } else {
            createTask(instanceId, first);
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
        Map<String, Object> def = db.one("SELECT * FROM bpm_process_def WHERE id=?", inst.get("defId"));
        Map<String, Object> next = firstApproval(def, String.valueOf(task.get("nodeId")));
        if (next == null) {
            db.update("UPDATE bpm_instance SET status='APPROVED',end_time=?,current_node=NULL WHERE id=?",
                    LocalDateTime.now(), inst.get("id"));
        } else {
            db.update("UPDATE bpm_instance SET current_node=? WHERE id=?", next.get("id"), inst.get("id"));
            createTask(((Number) inst.get("id")).longValue(), next);
        }
    }

    public void reject(long taskId, String comment) {
        Map<String, Object> task = handle(taskId);
        db.update("UPDATE bpm_task SET status='REJECTED',comment=?,finish_time=?,operator=? WHERE id=?",
                comment, LocalDateTime.now(), AuthContext.username(), taskId);
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

    private Map<String, Object> firstApproval(Map<String, Object> def, String afterNodeId) {
        List<Map<String, Object>> nodes = nodes(def);
        boolean after = afterNodeId == null;
        for (Map<String, Object> n : nodes) {
            if (!after) {
                if (String.valueOf(n.get("id")).equals(afterNodeId)) after = true;
                continue;
            }
            if ("approval".equals(String.valueOf(n.get("type")))) return n;
        }
        return null;
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

    private void createTask(long instanceId, Map<String, Object> node) {
        String type = String.valueOf(node.getOrDefault("assigneeType", "USER"));
        String who = String.valueOf(node.getOrDefault("assignee", AuthContext.username()));
        String assignee = "ROLE".equalsIgnoreCase(type) ? null : who;
        String role = "ROLE".equalsIgnoreCase(type) ? who : null;
        db.insert("INSERT INTO bpm_task(instance_id,node_id,node_name,assignee_role,assignee,status,create_time) "
                        + "VALUES(?,?,?,?,?,'PENDING',?)",
                instanceId, node.get("id"), node.getOrDefault("name", node.get("id")), role, assignee,
                LocalDateTime.now());
    }
}
