package com.chinacreator.gzcm.buszhi.workflow;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 工作流执行日志仓库 — ecos_workflow_log 表。
 */
@Repository
public class WorkflowLogRepository {

    private static final Logger log = LoggerFactory.getLogger(WorkflowLogRepository.class);
    private final JdbcTemplate jdbc;

    public WorkflowLogRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

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
        return jdbc.query(
            "SELECT * FROM ecos_workflow_log WHERE instance_id = ? ORDER BY created_at",
            ROW_MAPPER, instanceId);
    }

    public List<WorkflowLogEntity> findByTraceId(String traceId) {
        return jdbc.query(
            "SELECT * FROM ecos_workflow_log WHERE trace_id = ? ORDER BY created_at",
            ROW_MAPPER, traceId);
    }

    public int insert(WorkflowLogEntity entity) {
        String id = entity.getId() != null ? entity.getId() : "log-" + UUID.randomUUID().toString().substring(0, 8);
        String sql = """
            INSERT INTO ecos_workflow_log
            (id, instance_id, node_id, node_type, event_type, message, details, duration_ms, trace_id, created_at)
            VALUES (?,?,?,?,?,?,?::jsonb,?,?,NOW())
            """;
        return jdbc.update(sql,
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
