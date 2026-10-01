package com.chinacreator.gzcm.buszhi.workflow.dao;

import com.chinacreator.gzcm.buszhi.workflow.WorkflowLogEntity;
import com.chinacreator.gzcm.runtime.core.database.support.JdbcTemplateDaoSupport;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * R2-B5 buszhi 数据访问收口（架构铁律 §2.5 #1 / IR06）：
 * {@code {schema}.ecos_workflow_log} 表 JDBC 落点（W85）。
 *
 * <p>承接原 {@link com.chinacreator.gzcm.buszhi.workflow.WorkflowInstanceRepository} 同口径的
 * {@link com.chinacreator.gzcm.buszhi.workflow.WorkflowLogRepository}，SQL / RowMapper / schema 注入原样搬运。</p>
 */
@Repository
public class WorkflowLogDao extends JdbcTemplateDaoSupport {

    public WorkflowLogDao(JdbcTemplate jdbc,
                          @Value("${ecos.ontology.control-schema:ecos_ontology}") String schema) {
        this.jdbcTemplate = jdbc;
        this.schema = schema;
    }

    private final String schema;

    /** 显式列清单 — 与 V1.1 DDL 一致。 */
    private static final String COLS =
        "id, instance_id, node_id, node_type, event_type, message, details, duration_ms, trace_id, created_at";

    private final RowMapper<WorkflowLogEntity> ROW_MAPPER = (rs, rowNum) -> {
        WorkflowLogEntity e = new WorkflowLogEntity();
        e.setId(rs.getString("id"));
        e.setInstanceId(rs.getString("instance_id"));
        e.setNodeId(rs.getString("node_id"));
        e.setNodeType(rs.getString("node_type"));
        e.setEventType(rs.getString("event_type"));
        e.setMessage(rs.getString("message"));
        e.setDetails(rs.getString("details"));
        e.setDurationMs(rs.getObject("duration_ms", Long.class));
        e.setTraceId(rs.getString("trace_id"));
        e.setCreatedAt(toLDT(rs.getTimestamp("created_at")));
        return e;
    };

    private static LocalDateTime toLDT(Timestamp ts) {
        return ts != null ? ts.toLocalDateTime() : null;
    }

    public List<WorkflowLogEntity> findByInstanceId(String instanceId) {
        String sql = "SELECT " + COLS + " FROM " + schema + ".ecos_workflow_log WHERE instance_id = ? ORDER BY created_at";
        return jdbcTemplate.query(sql, ROW_MAPPER, instanceId);
    }

    public List<WorkflowLogEntity> findByTraceId(String traceId) {
        String sql = "SELECT " + COLS + " FROM " + schema + ".ecos_workflow_log WHERE trace_id = ? ORDER BY created_at";
        return jdbcTemplate.query(sql, ROW_MAPPER, traceId);
    }

    public int insert(WorkflowLogEntity entity) {
        String id = entity.getId() != null ? entity.getId() : "log-" + UUID.randomUUID().toString().substring(0, 8);
        String sql = """
            INSERT INTO %s.ecos_workflow_log
            (id, instance_id, node_id, node_type, event_type, message, details, duration_ms, trace_id, created_at)
            VALUES (?,?,?,?,?,?,?,?,?, NOW())
            """.formatted(schema);
        return jdbcTemplate.update(sql,
            id, entity.getInstanceId(), entity.getNodeId(), entity.getNodeType(),
            entity.getEventType(), entity.getMessage(), entity.getDetails(),
            entity.getDurationMs(), entity.getTraceId());
    }

    /** 快捷记录日志 */
    public void log(String instanceId, String nodeId, String nodeType, String eventType,
                    String message, String details, Long durationMs, String traceId) {
        WorkflowLogEntity e = new WorkflowLogEntity();
        e.setId("log-" + UUID.randomUUID().toString().substring(0, 8));
        e.setInstanceId(instanceId);
        e.setNodeId(nodeId);
        e.setNodeType(nodeType);
        e.setEventType(eventType);
        e.setMessage(message);
        e.setDetails(details);
        e.setDurationMs(durationMs);
        e.setTraceId(traceId);
        insert(e);
    }
}
