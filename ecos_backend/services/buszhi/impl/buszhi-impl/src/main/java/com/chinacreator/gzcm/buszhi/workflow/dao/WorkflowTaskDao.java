package com.chinacreator.gzcm.buszhi.workflow.dao;

import com.chinacreator.gzcm.buszhi.workflow.WorkflowTaskEntity;
import com.chinacreator.gzcm.runtime.core.database.support.JdbcTemplateDaoSupport;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * R2-B5 buszhi 数据访问收口（架构铁律 §2.5 #1 / IR06）：
 * {@code {schema}.ecos_workflow_task} 表 JDBC 落点（W85）。
 *
 * <p>承接原 {@link com.chinacreator.gzcm.buszhi.workflow.WorkflowTaskRepository}，
 * SQL / RowMapper / schema 注入原样搬运。显式列清单，无 {@code SELECT *}。</p>
 */
@Repository
public class WorkflowTaskDao extends JdbcTemplateDaoSupport {

    public WorkflowTaskDao(JdbcTemplate jdbc,
                           @Value("${ecos.ontology.control-schema:ecos_ontology}") String schema) {
        this.jdbcTemplate = jdbc;
        this.schema = schema;
    }

    private final String schema;

    /** 显式列清单 — 与 V1.1 DDL 一致。 */
    private static final String COLS = """
        id, instance_id, node_id, task_type, title, assignee, candidate_users, candidate_roles,
        status, priority, form_schema, form_data, result, agent_result,
        due_date, completed_at, completed_by, created_at, updated_at
        """;

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
        String sql = "SELECT " + COLS + " FROM " + schema + ".ecos_workflow_task WHERE id = ?";
        List<WorkflowTaskEntity> list = jdbcTemplate.query(sql, ROW_MAPPER, id);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    public List<WorkflowTaskEntity> findByInstanceId(String instanceId) {
        String sql = "SELECT " + COLS + " FROM " + schema + ".ecos_workflow_task WHERE instance_id = ? ORDER BY created_at";
        return jdbcTemplate.query(sql, ROW_MAPPER, instanceId);
    }

    public List<WorkflowTaskEntity> findByAssignee(String assignee, int limit) {
        String sql = "SELECT " + COLS + " FROM " + schema + ".ecos_workflow_task WHERE assignee = ? ORDER BY created_at DESC LIMIT ?";
        return jdbcTemplate.query(sql, ROW_MAPPER, assignee, limit);
    }

    public List<WorkflowTaskEntity> findByStatus(String status, int limit) {
        String sql = "SELECT " + COLS + " FROM " + schema + ".ecos_workflow_task WHERE status = ? ORDER BY created_at DESC LIMIT ?";
        return jdbcTemplate.query(sql, ROW_MAPPER, status, limit);
    }

    /** 查找待认领的任务（无 assignee + 状态 New） */
    public List<WorkflowTaskEntity> findUnassigned(int limit) {
        String sql = "SELECT " + COLS + " FROM " + schema
            + ".ecos_workflow_task WHERE status = 'New' AND assignee IS NULL ORDER BY created_at LIMIT ?";
        return jdbcTemplate.query(sql, ROW_MAPPER, limit);
    }

    public int insert(WorkflowTaskEntity entity) {
        String sql = """
            INSERT INTO %s.ecos_workflow_task
            (id, instance_id, node_id, task_type, title, assignee, candidate_users, candidate_roles,
             status, priority, form_schema, form_data, result, agent_result,
             due_date, created_at, updated_at)
            VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,NOW(),NOW())
            """.formatted(schema);
        return jdbcTemplate.update(sql,
            entity.getId(), entity.getInstanceId(), entity.getNodeId(), entity.getTaskType(),
            entity.getTitle(), entity.getAssignee(), entity.getCandidateUsers(), entity.getCandidateRoles(),
            entity.getStatus(), entity.getPriority(), entity.getFormSchema(), entity.getFormData(),
            entity.getResult(), entity.getAgentResult(), entity.getDueDate());
    }

    public int claim(String taskId, String assignee) {
        String sql = """
            UPDATE %s.ecos_workflow_task SET assignee = ?, status = 'Assigned', updated_at = NOW() WHERE id = ? AND status = 'New'
            """.formatted(schema);
        return jdbcTemplate.update(sql, assignee, taskId);
    }

    public int complete(String taskId, String result, String completedBy) {
        String sql = """
            UPDATE %s.ecos_workflow_task SET status = 'Completed', result = ?, completed_by = ?, completed_at = NOW(), updated_at = NOW() WHERE id = ?
            """.formatted(schema);
        return jdbcTemplate.update(sql, result, completedBy, taskId);
    }

    public int transfer(String taskId, String newAssignee) {
        String sql = """
            UPDATE %s.ecos_workflow_task SET assignee = ?, status = 'Transferred', updated_at = NOW() WHERE id = ?
            """.formatted(schema);
        return jdbcTemplate.update(sql, newAssignee, taskId);
    }

    public int reject(String taskId, String reason) {
        String sql = """
            UPDATE %s.ecos_workflow_task SET status = 'Rejected', result = ?, updated_at = NOW() WHERE id = ?
            """.formatted(schema);
        return jdbcTemplate.update(sql, reason, taskId);
    }
}
