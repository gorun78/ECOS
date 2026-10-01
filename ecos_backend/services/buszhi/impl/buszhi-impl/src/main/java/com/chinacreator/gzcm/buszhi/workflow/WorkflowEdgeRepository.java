package com.chinacreator.gzcm.buszhi.workflow;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/**
 * 工作流边投影只读仓库 — {@code {schema}.ecos_workflow_edge}（W86/F03-12 M1 只读路径）。
 *
 * <p>与 {@link WorkflowNodeRepository} 同口径：只读，写路径拆解延至 F03-12 M2。
 */
@Repository
public class WorkflowEdgeRepository {

    private static final Logger log = LoggerFactory.getLogger(WorkflowEdgeRepository.class);
    private final JdbcTemplate jdbc;
    private final String schema;

    /** 显式列清单 — 随 V173 §2 DDL（source_node_key/target_node_key 即 source/target）。 */
    private static final String COLS =
        "id, workflow_id, edge_key, source_node_key, target_node_key, sort_order, create_time";

    private final RowMapper<WorkflowEdgeEntity> ROW_MAPPER = (rs, rowNum) -> {
        WorkflowEdgeEntity e = new WorkflowEdgeEntity();
        e.setId(rs.getString("id"));
        e.setWorkflowId(rs.getString("workflow_id"));
        e.setEdgeKey(rs.getString("edge_key"));
        e.setSourceNodeKey(rs.getString("source_node_key"));
        e.setTargetNodeKey(rs.getString("target_node_key"));
        e.setSortOrder(rs.getObject("sort_order", Integer.class));
        e.setCreateTime(rs.getTimestamp("create_time") != null
            ? rs.getTimestamp("create_time").toLocalDateTime() : null);
        return e;
    };

    public WorkflowEdgeRepository(JdbcTemplate jdbc,
                                  @Value("${ecos.ontology.control-schema:ecos_ontology}") String schema) {
        this.jdbc = jdbc;
        this.schema = schema;
    }

    public List<WorkflowEdgeEntity> findByWorkflowId(String wfId) {
        String sql = "SELECT " + COLS + " FROM " + schema + ".ecos_workflow_edge WHERE workflow_id = ? ORDER BY sort_order";
        return jdbc.query(sql, ROW_MAPPER, wfId);
    }

    public int countByWorkflowId(String wfId) {
        String sql = "SELECT COUNT(*) FROM " + schema + ".ecos_workflow_edge WHERE workflow_id = ?";
        Long c = jdbc.queryForObject(sql, Long.class, wfId);
        return c != null ? c.intValue() : 0;
    }
}
