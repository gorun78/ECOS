package com.chinacreator.gzcm.buszhi.workflow;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 工作流实例仓库 — {@code {schema}.ecos_workflow_instance} 表 CRUD（W85）。
 *
 * <p>R-7 归属①：schema 由 {@code ecos.ontology.control-schema} 注入（默认 {@code ecos_ontology}）；
 * 显式列清单，无 {@code SELECT *}；JSON 文本值以 {@code ?} 绑定 String（DR07 治理，不再 {@code ?::jsonb}）。
 */
@Repository
public class WorkflowInstanceRepository {

    private static final Logger log = LoggerFactory.getLogger(WorkflowInstanceRepository.class);
    private final JdbcTemplate jdbc;
    private final String schema;

    public WorkflowInstanceRepository(JdbcTemplate jdbc,
                                      @Value("${ecos.ontology.control-schema:ecos_ontology}") String schema) {
        this.jdbc = jdbc;
        this.schema = schema;
    }

    /** 显式列清单 — 与 V1.1 DDL 一致。 */
    private static final String COLS = """
        id, workflow_id, workflow_name, version_no, status, trigger_type, triggered_by,
        triggered_object_id, trigger_event, variables, context, current_node_ids,
        started_at, completed_at, error_message, retry_count, created_at, updated_at
        """;

    private final RowMapper<WorkflowInstanceEntity> ROW_MAPPER = (rs, rowNum) -> {
        WorkflowInstanceEntity e = new WorkflowInstanceEntity();
        e.setId(rs.getString("id"));
        e.setWorkflowId(rs.getString("workflow_id"));
        e.setWorkflowName(rs.getString("workflow_name"));
        e.setVersionNo(rs.getString("version_no"));
        e.setStatus(rs.getString("status"));
        e.setTriggerType(rs.getString("trigger_type"));
        e.setTriggeredBy(rs.getString("triggered_by"));
        e.setTriggeredObjectId(rs.getString("triggered_object_id"));
        e.setTriggerEvent(rs.getString("trigger_event"));
        e.setVariables(rs.getString("variables"));
        e.setContext(rs.getString("context"));
        e.setCurrentNodeIds(rs.getString("current_node_ids"));
        e.setStartedAt(toLocalDateTime(rs.getTimestamp("started_at")));
        e.setCompletedAt(toLocalDateTime(rs.getTimestamp("completed_at")));
        e.setErrorMessage(rs.getString("error_message"));
        e.setRetryCount(rs.getInt("retry_count"));
        e.setCreatedAt(toLocalDateTime(rs.getTimestamp("started_at")));  // no created_at semantics, use started_at
        e.setUpdatedAt(toLocalDateTime(rs.getTimestamp("updated_at")));
        return e;
    };

    private static LocalDateTime toLocalDateTime(Timestamp ts) {
        return ts != null ? ts.toLocalDateTime() : null;
    }

    public Optional<WorkflowInstanceEntity> findById(String id) {
        String sql = "SELECT " + COLS + " FROM " + schema + ".ecos_workflow_instance WHERE id = ?";
        List<WorkflowInstanceEntity> list = jdbc.query(sql, ROW_MAPPER, id);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    public List<WorkflowInstanceEntity> findAll(int limit) {
        String sql = "SELECT " + COLS + " FROM " + schema + ".ecos_workflow_instance ORDER BY started_at DESC LIMIT ?";
        return jdbc.query(sql, ROW_MAPPER, limit);
    }

    public List<WorkflowInstanceEntity> findByWorkflowId(String workflowId, int limit) {
        String sql = "SELECT " + COLS + " FROM " + schema + ".ecos_workflow_instance WHERE workflow_id = ? ORDER BY started_at DESC LIMIT ?";
        return jdbc.query(sql, ROW_MAPPER, workflowId, limit);
    }

    public int insert(WorkflowInstanceEntity entity) {
        String sql = """
            INSERT INTO %s.ecos_workflow_instance
            (id, workflow_id, workflow_name, version_no, status, trigger_type, triggered_by,
             triggered_object_id, trigger_event, variables, context, current_node_ids,
             started_at, retry_count, created_at, updated_at)
            VALUES (?,?,?,?,?,?,?,?,?,?,?,?, NOW(), 0, NOW(), NOW())
            """.formatted(schema);
        return jdbc.update(sql,
            entity.getId(), entity.getWorkflowId(), entity.getWorkflowName(),
            entity.getVersionNo(), entity.getStatus(), entity.getTriggerType(),
            entity.getTriggeredBy(), entity.getTriggeredObjectId(), entity.getTriggerEvent(),
            entity.getVariables(), entity.getContext(), entity.getCurrentNodeIds());
    }

    public int updateStatus(String id, String status, String errorMessage) {
        String sql = """
            UPDATE %s.ecos_workflow_instance SET status = ?, error_message = ?, updated_at = NOW()
            WHERE id = ?
            """.formatted(schema);
        return jdbc.update(sql, status, errorMessage, id);
    }

    public int updateCurrentNodes(String id, String currentNodeIds) {
        String sql = "UPDATE " + schema + ".ecos_workflow_instance SET current_node_ids = ?, updated_at = NOW() WHERE id = ?";
        return jdbc.update(sql, currentNodeIds, id);
    }

    public int complete(String id) {
        String sql = """
            UPDATE %s.ecos_workflow_instance SET status = 'Completed', completed_at = NOW(), updated_at = NOW() WHERE id = ?
            """.formatted(schema);
        return jdbc.update(sql, id);
    }
}
