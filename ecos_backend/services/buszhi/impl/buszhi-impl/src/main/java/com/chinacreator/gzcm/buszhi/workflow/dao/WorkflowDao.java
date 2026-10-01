package com.chinacreator.gzcm.buszhi.workflow.dao;

import com.chinacreator.gzcm.buszhi.workflow.WorkflowActorContext;
import com.chinacreator.gzcm.buszhi.workflow.WorkflowEntity;
import com.chinacreator.gzcm.runtime.core.database.support.JdbcTemplateDaoSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * R2-B5 buszhi 数据访问收口（架构铁律 §2.5 #1 / IR06）：{@code {schema}.ecos_workflow} 表 JDBC 落点。
 *
 * <p>承接原 {@link com.chinacreator.gzcm.buszhi.workflow.WorkflowRepository}，SQL / RowMapper / schema 注入与软删语义
 * （W86/O-18：{@code is_deleted=1 + update_time=NOW() + update_by=?}，禁物理 DELETE）原样搬运，只搬不改。</p>
 */
@Repository
public class WorkflowDao extends JdbcTemplateDaoSupport {

    private static final Logger log = LoggerFactory.getLogger(WorkflowDao.class);

    public WorkflowDao(JdbcTemplate jdbc,
                       @Value("${ecos.ontology.control-schema:ecos_ontology}") String schema) {
        this.jdbcTemplate = jdbc;
        this.schema = schema;
    }

    private final String schema;

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
        return jdbcTemplate.query(sql, ROW_MAPPER, limit);
    }

    public java.util.Optional<WorkflowEntity> findById(String id) {
        String sql = """
            SELECT id, name, description, status, mode, nodes, edges, published_at, created_at, updated_at
            FROM %s.ecos_workflow WHERE id = ? AND is_deleted = 0
            """.formatted(schema);
        List<WorkflowEntity> list = jdbcTemplate.query(sql, ROW_MAPPER, id);
        return list.isEmpty() ? java.util.Optional.empty() : java.util.Optional.of(list.get(0));
    }

    public int insert(WorkflowEntity entity) {
        String sql = """
            INSERT INTO %s.ecos_workflow
            (id, name, description, status, mode, nodes, edges, created_at, updated_at, is_deleted, domain, version_no)
            VALUES (?, ?, ?, ?, ?, ?, ?, NOW(), NOW(), 0, 'default', '1')
            """.formatted(schema);
        return jdbcTemplate.update(sql,
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
        return jdbcTemplate.update(sql, name, description, mode, nodes, edges, id);
    }

    public int publish(String id) {
        String sql = """
            UPDATE %s.ecos_workflow SET status = 'active', published_at = NOW(), updated_at = NOW()
            WHERE id = ? AND is_deleted = 0
            """.formatted(schema);
        return jdbcTemplate.update(sql, id);
    }

    /**
     * 软删（W86/O-18，DR05 禁物理 DELETE）：标记 {@code is_deleted=1} + 审计列
     * {@code update_by} 取当前操作人（{@link WorkflowActorContext}，未鉴权回退 {@code system}）。
     */
    public int deleteById(String id) {
        String actor = WorkflowActorContext.currentActor();
        String sql = "UPDATE " + schema + ".ecos_workflow SET is_deleted=1, update_time=NOW(), update_by=? WHERE id=?";
        log.info("Soft-delete workflow id={} caller={}", id, actor);
        return jdbcTemplate.update(sql, actor, id);
    }

    public long count() {
        String sql = "SELECT COUNT(*) FROM %s.ecos_workflow WHERE is_deleted = 0".formatted(schema);
        Long c = jdbcTemplate.queryForObject(sql, Long.class);
        return c != null ? c : 0L;
    }
}
