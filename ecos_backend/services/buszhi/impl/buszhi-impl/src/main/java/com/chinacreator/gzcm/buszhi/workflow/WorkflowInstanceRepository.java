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
 * 工作流实例仓库 — ecos_workflow_instance 表 CRUD。
 */
@Repository
public class WorkflowInstanceRepository {

    private static final Logger log = LoggerFactory.getLogger(WorkflowInstanceRepository.class);
    private final JdbcTemplate jdbc;

    public WorkflowInstanceRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    private final RowMapper<WorkflowInstanceEntity> ROW_MAPPER = (rs, rowNum) -> {
        WorkflowInstanceEntity e = new WorkflowInstanceEntity();
        e.setId(rs.getString("id"));
        e.setWorkflowId(rs.getString("workflow_id"));
        e.setStatus(rs.getString("status"));
        e.setCurrentNodeIds(rs.getString("current_node_id"));
        e.setContext(rs.getString("context_json"));
        e.setStartedAt(toLocalDateTime(rs.getTimestamp("started_at")));
        e.setCompletedAt(toLocalDateTime(rs.getTimestamp("completed_at")));
        e.setCreatedAt(toLocalDateTime(rs.getTimestamp("started_at")));  // no created_at, use started_at
        return e;
    };

    private static LocalDateTime toLocalDateTime(Timestamp ts) {
        return ts != null ? ts.toLocalDateTime() : null;
    }

    public Optional<WorkflowInstanceEntity> findById(String id) {
        List<WorkflowInstanceEntity> list = jdbc.query(
            "SELECT * FROM ecos_workflow_instance WHERE id = ?", ROW_MAPPER, id);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    public List<WorkflowInstanceEntity> findAll(int limit) {
        return jdbc.query("SELECT * FROM ecos_workflow_instance ORDER BY started_at DESC LIMIT ?",
            ROW_MAPPER, limit);
    }

    public List<WorkflowInstanceEntity> findByWorkflowId(String workflowId, int limit) {
        return jdbc.query(
            "SELECT * FROM ecos_workflow_instance WHERE workflow_id = ? ORDER BY started_at DESC LIMIT ?",
            ROW_MAPPER, workflowId, limit);
    }

    public int insert(WorkflowInstanceEntity entity) {
        String sql = """
            INSERT INTO ecos_workflow_instance
            (id, workflow_id, workflow_name, version_no, status, trigger_type, triggered_by,
             triggered_object_id, trigger_event, variables, context, current_node_ids,
             started_at, retry_count, created_at, updated_at)
            VALUES (?,?,?,?,?,?,?,?,?,?::jsonb,?::jsonb,?::jsonb, NOW(), 0, NOW(), NOW())
            """;
        return jdbc.update(sql,
            entity.getId(), entity.getWorkflowId(), entity.getWorkflowName(),
            entity.getVersionNo(), entity.getStatus(), entity.getTriggerType(),
            entity.getTriggeredBy(), entity.getTriggeredObjectId(), entity.getTriggerEvent(),
            entity.getVariables(), entity.getContext(), entity.getCurrentNodeIds());
    }

    public int updateStatus(String id, String status, String errorMessage) {
        String sql = """
            UPDATE ecos_workflow_instance SET status = ?, error_message = ?, updated_at = NOW()
            WHERE id = ?
            """;
        return jdbc.update(sql, status, errorMessage, id);
    }

    public int updateCurrentNodes(String id, String currentNodeIds) {
        return jdbc.update(
            "UPDATE ecos_workflow_instance SET current_node_ids = ?::jsonb, updated_at = NOW() WHERE id = ?",
            currentNodeIds, id);
    }

    public int complete(String id) {
        return jdbc.update(
            "UPDATE ecos_workflow_instance SET status = 'Completed', completed_at = NOW(), updated_at = NOW() WHERE id = ?",
            id);
    }
}
