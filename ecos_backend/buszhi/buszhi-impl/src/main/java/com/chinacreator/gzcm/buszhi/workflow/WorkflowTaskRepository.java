package com.chinacreator.gzcm.buszhi.workflow;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 工作流任务仓库 — ecos_workflow_task 表 CRUD。
 */
@Repository
public class WorkflowTaskRepository {

    private static final Logger log = LoggerFactory.getLogger(WorkflowTaskRepository.class);
    private final JdbcTemplate jdbc;

    public WorkflowTaskRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    private final RowMapper<WorkflowTaskEntity> ROW_MAPPER = (rs, rowNum) -> {
        WorkflowTaskEntity e = new WorkflowTaskEntity();
        e.setId(rs.getString("id"));
        e.setInstanceId(rs.getString("instance_id"));
        e.setNodeId(rs.getString("node_id"));
        e.setTaskType(rs.getString("task_type"));
        e.setTitle(rs.getString("title"));
        e.setAssignee(rs.getString("assignee"));
        e.setCandidateUsers(rs.getString("candidate_users"));
        e.setCandidateRoles(rs.getString("candidate_roles"));
        e.setStatus(rs.getString("status"));
        e.setPriority(rs.getString("priority"));
        e.setFormSchema(rs.getString("form_schema"));
        e.setFormData(rs.getString("form_data"));
        e.setResult(rs.getString("result"));
        e.setAgentResult(rs.getString("agent_result"));
        e.setDueDate(toLDT(rs.getTimestamp("due_date")));
        e.setCompletedAt(toLDT(rs.getTimestamp("completed_at")));
        e.setCompletedBy(rs.getString("completed_by"));
        e.setCreatedAt(toLDT(rs.getTimestamp("created_at")));
        e.setUpdatedAt(toLDT(rs.getTimestamp("updated_at")));
        return e;
    };

    private static LocalDateTime toLDT(Timestamp ts) {
        return ts != null ? ts.toLocalDateTime() : null;
    }

    public Optional<WorkflowTaskEntity> findById(String id) {
        List<WorkflowTaskEntity> list = jdbc.query(
            "SELECT * FROM ecos_workflow_task WHERE id = ?", ROW_MAPPER, id);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    public List<WorkflowTaskEntity> findByInstanceId(String instanceId) {
        return jdbc.query(
            "SELECT * FROM ecos_workflow_task WHERE instance_id = ? ORDER BY created_at",
            ROW_MAPPER, instanceId);
    }

    public List<WorkflowTaskEntity> findByAssignee(String assignee, int limit) {
        return jdbc.query(
            "SELECT * FROM ecos_workflow_task WHERE assignee = ? ORDER BY created_at DESC LIMIT ?",
            ROW_MAPPER, assignee, limit);
    }

    public List<WorkflowTaskEntity> findByStatus(String status, int limit) {
        return jdbc.query(
            "SELECT * FROM ecos_workflow_task WHERE status = ? ORDER BY created_at DESC LIMIT ?",
            ROW_MAPPER, status, limit);
    }

    /** 查找待认领的任务（无 assignee + 状态 New） */
    public List<WorkflowTaskEntity> findUnassigned(int limit) {
        return jdbc.query(
            "SELECT * FROM ecos_workflow_task WHERE status = 'New' AND assignee IS NULL ORDER BY created_at LIMIT ?",
            ROW_MAPPER, limit);
    }

    public int insert(WorkflowTaskEntity entity) {
        String sql = """
            INSERT INTO ecos_workflow_task
            (id, instance_id, node_id, task_type, title, assignee, candidate_users, candidate_roles,
             status, priority, form_schema, form_data, result, agent_result,
             due_date, created_at, updated_at)
            VALUES (?,?,?,?,?,?,?::jsonb,?::jsonb,?,?,?::jsonb,?::jsonb,?::jsonb,?::jsonb,?,NOW(),NOW())
            """;
        return jdbc.update(sql,
            entity.getId(), entity.getInstanceId(), entity.getNodeId(), entity.getTaskType(),
            entity.getTitle(), entity.getAssignee(), entity.getCandidateUsers(), entity.getCandidateRoles(),
            entity.getStatus(), entity.getPriority(), entity.getFormSchema(), entity.getFormData(),
            entity.getResult(), entity.getAgentResult(), entity.getDueDate());
    }

    public int claim(String taskId, String assignee) {
        return jdbc.update(
            "UPDATE ecos_workflow_task SET assignee = ?, status = 'Assigned', updated_at = NOW() WHERE id = ? AND status = 'New'",
            assignee, taskId);
    }

    public int complete(String taskId, String result, String completedBy) {
        return jdbc.update(
            "UPDATE ecos_workflow_task SET status = 'Completed', result = ?::jsonb, completed_by = ?, completed_at = NOW(), updated_at = NOW() WHERE id = ?",
            result, completedBy, taskId);
    }

    public int transfer(String taskId, String newAssignee) {
        return jdbc.update(
            "UPDATE ecos_workflow_task SET assignee = ?, status = 'Transferred', updated_at = NOW() WHERE id = ?",
            newAssignee, taskId);
    }

    public int reject(String taskId, String reason) {
        return jdbc.update(
            "UPDATE ecos_workflow_task SET status = 'Rejected', result = ?::jsonb, updated_at = NOW() WHERE id = ?",
            reason, taskId);
    }
}
