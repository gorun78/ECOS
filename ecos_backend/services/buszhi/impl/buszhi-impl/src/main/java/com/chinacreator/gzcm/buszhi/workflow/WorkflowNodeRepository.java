package com.chinacreator.gzcm.buszhi.workflow;

import java.time.LocalDateTime;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/**
 * 工作流节点投影只读仓库 — {@code {schema}.ecos_workflow_node}（W86/F03-12 M1 只读路径）。
 *
 * <p>软删 {schema}.ecos_workflow 唯一模型写路径配套：读侧走投影表检索（DR04 MC02：
 * 主表 {@code nodes_json} 只做存取，不参与 WHERE）。本仓库 **只读**，
 * 写路径（save 时拆解 nodes/edges 入投影表）延至 F03-12 M2 — V173 已迁移 11 行 v2 遗留数据。
 */
@Repository
public class WorkflowNodeRepository {

    private static final Logger log = LoggerFactory.getLogger(WorkflowNodeRepository.class);
    private final JdbcTemplate jdbc;
    private final String schema;

    /** 显式列清单 — 随 V173 §2 DDL（node_key=name/label 源，name=节点称号）。 */
    private static final String COLS =
        "id, workflow_id, node_key, name, node_type, sort_order, create_time";

    private final RowMapper<WorkflowNodeEntity> ROW_MAPPER = (rs, rowNum) -> {
        WorkflowNodeEntity e = new WorkflowNodeEntity();
        e.setId(rs.getString("id"));
        e.setWorkflowId(rs.getString("workflow_id"));
        e.setNodeKey(rs.getString("node_key"));
        e.setName(rs.getString("name"));
        e.setNodeType(rs.getString("node_type"));
        e.setSortOrder(rs.getObject("sort_order", Integer.class));
        e.setCreateTime(rs.getTimestamp("create_time") != null
            ? rs.getTimestamp("create_time").toLocalDateTime() : null);
        return e;
    };

    public WorkflowNodeRepository(JdbcTemplate jdbc,
                                  @Value("${ecos.ontology.control-schema:ecos_ontology}") String schema) {
        this.jdbc = jdbc;
        this.schema = schema;
    }

    /** 只读投影：某工作流全量节点，软删行不过滤（投影表无跨主表软删联动，V173 §5 旧表只停写）。 */
    public List<WorkflowNodeEntity> findByWorkflowId(String wfId) {
        String sql = "SELECT " + COLS + " FROM " + schema + ".ecos_workflow_node WHERE workflow_id = ? ORDER BY sort_order";
        return jdbc.query(sql, ROW_MAPPER, wfId);
    }

    public int countByWorkflowId(String wfId) {
        String sql = "SELECT COUNT(*) FROM " + schema + ".ecos_workflow_node WHERE workflow_id = ?";
        Long c = jdbc.queryForObject(sql, Long.class, wfId);
        return c != null ? c.intValue() : 0;
    }
}
