package com.chinacreator.gzcm.buszhi.workflow.dao;

import com.chinacreator.gzcm.buszhi.workflow.WorkflowNodeEntity;
import com.chinacreator.gzcm.runtime.core.database.support.JdbcTemplateDaoSupport;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * R2-B5 buszhi 数据访问收口（架构铁律 §2.5 #1 / IR06）：
 * {@code {schema}.ecos_workflow_node} 只读投影表 JDBC 落点（W86/F03-12 M1 只读路径）。
 *
 * <p>承接原 {@link com.chinacreator.gzcm.buszhi.workflow.WorkflowNodeRepository}，
 * SQL / RowMapper / schema 注入原样搬运。**只读**。</p>
 */
@Repository
public class WorkflowNodeDao extends JdbcTemplateDaoSupport {

    public WorkflowNodeDao(JdbcTemplate jdbc,
                           @Value("${ecos.ontology.control-schema:ecos_ontology}") String schema) {
        this.jdbcTemplate = jdbc;
        this.schema = schema;
    }

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

    public List<WorkflowNodeEntity> findByWorkflowId(String wfId) {
        String sql = "SELECT " + COLS + " FROM " + schema + ".ecos_workflow_node WHERE workflow_id = ? ORDER BY sort_order";
        return jdbcTemplate.query(sql, ROW_MAPPER, wfId);
    }

    public int countByWorkflowId(String wfId) {
        String sql = "SELECT COUNT(*) FROM " + schema + ".ecos_workflow_node WHERE workflow_id = ?";
        Long c = jdbcTemplate.queryForObject(sql, Long.class, wfId);
        return c != null ? c.intValue() : 0;
    }
}
