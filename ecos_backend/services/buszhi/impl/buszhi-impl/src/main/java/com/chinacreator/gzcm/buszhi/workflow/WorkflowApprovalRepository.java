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
 * 工作流审批记录仓库 — ecos_workflow_approval 表 CRUD。
 */
@Repository
public class WorkflowApprovalRepository {

    private static final Logger log = LoggerFactory.getLogger(WorkflowApprovalRepository.class);
    private final JdbcTemplate jdbc;

    public WorkflowApprovalRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    private final RowMapper<WorkflowApprovalEntity> ROW_MAPPER = (rs, rowNum) -> {
        WorkflowApprovalEntity e = new WorkflowApprovalEntity();
        e.setId(rs.getString("id"));
        e.setTaskId(rs.getString("task_id"));
        e.setInstanceId(rs.getString("instance_id"));
        e.setApprover(rs.getString("approver"));
        e.setDecision(rs.getString("decision"));
        e.setOpinion(rs.getString("opinion"));
        e.setFormData(rs.getString("form_data"));
        e.setCreatedAt(toLDT(rs.getTimestamp("created_at")));
        return e;
    };

    private static LocalDateTime toLDT(Timestamp ts) {
        return ts != null ? ts.toLocalDateTime() : null;
    }

    public Optional<WorkflowApprovalEntity> findById(String id) {
        List<WorkflowApprovalEntity> list = jdbc.query(
            "SELECT * FROM ecos_workflow_approval WHERE id = ?", ROW_MAPPER, id);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    public List<WorkflowApprovalEntity> findByTaskId(String taskId) {
        return jdbc.query(
            "SELECT * FROM ecos_workflow_approval WHERE task_id = ? ORDER BY created_at",
            ROW_MAPPER, taskId);
    }

    public List<WorkflowApprovalEntity> findByInstanceId(String instanceId) {
        return jdbc.query(
            "SELECT * FROM ecos_workflow_approval WHERE instance_id = ? ORDER BY created_at",
            ROW_MAPPER, instanceId);
    }

    public int insert(WorkflowApprovalEntity entity) {
        String sql = """
            INSERT INTO ecos_workflow_approval
            (id, task_id, instance_id, approver, decision, opinion, form_data, created_at)
            VALUES (?,?,?,?,?,?,?::jsonb, NOW())
            """;
        return jdbc.update(sql,
            entity.getId(), entity.getTaskId(), entity.getInstanceId(),
            entity.getApprover(), entity.getDecision(), entity.getOpinion(), entity.getFormData());
    }
}
