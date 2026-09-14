package com.chinacreator.gzcm.buszhi.workflow;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/**
 * JdbcTemplate 仓库 — 工作流持久化 CRUD
 */
@Repository
public class WorkflowRepository {

    private static final Logger log = LoggerFactory.getLogger(WorkflowRepository.class);
    private static final DateTimeFormatter DT_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final JdbcTemplate jdbc;

    public WorkflowRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private final RowMapper<WorkflowEntity> ROW_MAPPER = (rs, rowNum) -> {
        WorkflowEntity e = new WorkflowEntity();
        e.setId(rs.getString("id"));
        e.setName(rs.getString("name"));
        e.setDescription(rs.getString("description"));
        e.setStatus(rs.getString("status"));
        e.setMode(rs.getString("mode"));
        e.setNodes(rs.getString("nodes"));
        e.setEdges(rs.getString("edges"));
        e.setPublishedAt(rs.getTimestamp("published_at") != null
            ? rs.getTimestamp("published_at").toLocalDateTime() : null);
        e.setCreatedAt(rs.getTimestamp("created_at") != null
            ? rs.getTimestamp("created_at").toLocalDateTime() : null);
        e.setUpdatedAt(rs.getTimestamp("updated_at") != null
            ? rs.getTimestamp("updated_at").toLocalDateTime() : null);
        return e;
    };

    public List<WorkflowEntity> findAll(int limit) {
        String sql = "SELECT * FROM ecos_workflow_v2 ORDER BY updated_at DESC LIMIT ?";
        return jdbc.query(sql, ROW_MAPPER, limit);
    }

    public Optional<WorkflowEntity> findById(String id) {
        String sql = "SELECT * FROM ecos_workflow_v2 WHERE id = ?";
        List<WorkflowEntity> list = jdbc.query(sql, ROW_MAPPER, id);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    public int insert(WorkflowEntity entity) {
        String sql = """
            INSERT INTO ecos_workflow_v2 (id, name, description, status, mode, nodes, edges, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, NOW(), NOW())
            """;
        return jdbc.update(sql,
            entity.getId(), entity.getName(), entity.getDescription(),
            entity.getStatus(), entity.getMode(), entity.getNodes(), entity.getEdges());
    }

    public int update(String id, String name, String description, String mode, String nodes, String edges) {
        String sql = """
            UPDATE ecos_workflow_v2 SET
                name = COALESCE(?, name),
                description = COALESCE(?, description),
                mode = COALESCE(?, mode),
                nodes = COALESCE(?, nodes),
                edges = COALESCE(?, edges),
                updated_at = NOW()
            WHERE id = ?
            """;
        return jdbc.update(sql, name, description, mode, nodes, edges, id);
    }

    public int publish(String id) {
        String sql = "UPDATE ecos_workflow_v2 SET status = 'active', published_at = NOW(), updated_at = NOW() WHERE id = ?";
        return jdbc.update(sql, id);
    }

    public int deleteById(String id) {
        return jdbc.update("DELETE FROM ecos_workflow_v2 WHERE id = ?", id);
    }

    public long count() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM ecos_workflow_v2", Long.class);
    }
}
