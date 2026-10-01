package com.chinacreator.gzcm.buszhi.workflow;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/**
 * JdbcTemplate 仓库 — 工作流持久化 CRUD（W85/W86，《详细设计-03-本体域》）。
 *
 * <p>W85/R-7 归属①：workflow 唯一模型落控制域 {@code {schema}.ecos_workflow}
 * （schema 由 {@code ecos.ontology.control-schema} 注入，默认 {@code ecos_ontology}）；
 * 所有 SQL schema 限定 + 显式列清单 + 无 {@code SELECT *} + 无 {@code ?::jsonb}（JSON 文本以字符串绑定）。
 *
 * <p>W86/O-18：删除改软删（{@code is_deleted=1}，DR05 禁物理 DELETE）；
 * {@code findAll}/{@code findById}/{@code count} 过滤 {@code is_deleted = 0}。
 *
 * <p>投影表（{@code ecos_workflow_node}/{@code ecos_workflow_edge}）走
 * {@link WorkflowNodeRepository}/{@link WorkflowEdgeRepository} 只读；
 * 写路径（save 时拆解 nodes/edges 入投影表）延至 F03-12 M2 — V173 已迁移 11 行 v2 遗留数据。
 */
@Repository
public class WorkflowRepository {

    private static final Logger log = LoggerFactory.getLogger(WorkflowRepository.class);

    private final JdbcTemplate jdbc;
    private final String schema;

    public WorkflowRepository(JdbcTemplate jdbc,
                              @Value("${ecos.ontology.control-schema:ecos_ontology}") String schema) {
        this.jdbc = jdbc;
        this.schema = schema;
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
        String sql = """
            SELECT id, name, description, status, mode, nodes, edges, published_at, created_at, updated_at
            FROM %s.ecos_workflow WHERE is_deleted = 0 ORDER BY updated_at DESC LIMIT ?
            """.formatted(schema);
        return jdbc.query(sql, ROW_MAPPER, limit);
    }

    public Optional<WorkflowEntity> findById(String id) {
        String sql = """
            SELECT id, name, description, status, mode, nodes, edges, published_at, created_at, updated_at
            FROM %s.ecos_workflow WHERE id = ? AND is_deleted = 0
            """.formatted(schema);
        List<WorkflowEntity> list = jdbc.query(sql, ROW_MAPPER, id);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    public int insert(WorkflowEntity entity) {
        String sql = """
            INSERT INTO %s.ecos_workflow
            (id, name, description, status, mode, nodes, edges, created_at, updated_at, is_deleted, domain, version_no)
            VALUES (?, ?, ?, ?, ?, ?, ?, NOW(), NOW(), 0, 'default', '1')
            """.formatted(schema);
        return jdbc.update(sql,
            entity.getId(), entity.getName(), entity.getDescription(),
            entity.getStatus(), entity.getMode(), entity.getNodes(), entity.getEdges());
    }

    public int update(String id, String name, String description, String mode, String nodes, String edges) {
        String sql = """
            UPDATE %s.ecos_workflow SET
                name = COALESCE(?, name),
                description = COALESCE(?, description),
                mode = COALESCE(?, mode),
                nodes = COALESCE(?, nodes),
                edges = COALESCE(?, edges),
                updated_at = NOW()
            WHERE id = ? AND is_deleted = 0
            """.formatted(schema);
        return jdbc.update(sql, name, description, mode, nodes, edges, id);
    }

    public int publish(String id) {
        String sql = """
            UPDATE %s.ecos_workflow SET status = 'active', published_at = NOW(), updated_at = NOW()
            WHERE id = ? AND is_deleted = 0
            """.formatted(schema);
        return jdbc.update(sql, id);
    }

    /**
     * 软删（W86/O-18，DR05 禁物理 DELETE）：标记 {@code is_deleted=1} + 审计列
     * {@code update_by} 取当前操作人（{@link WorkflowActorContext}，未鉴权回退 {@code system}）。
     */
    public int deleteById(String id) {
        String actor = WorkflowActorContext.currentActor();
        String sql = "UPDATE " + schema + ".ecos_workflow SET is_deleted=1, update_time=NOW(), update_by=? WHERE id=?";
        log.info("Soft-delete workflow id={} caller={}", id, actor);
        return jdbc.update(sql, actor, id);
    }

    public long count() {
        String sql = "SELECT COUNT(*) FROM %s.ecos_workflow WHERE is_deleted = 0".formatted(schema);
        Long c = jdbc.queryForObject(sql, Long.class);
        return c != null ? c : 0L;
    }
}
