package com.chinacreator.gzcm.sysman.dao;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Causal 查询 DAO — 承接 CausalQueryService 中的 JdbcTemplate 操作（铁律 §2.5）。
 * <p>SQL 与行映射自 Controller 原样下沉，字节级等价。
 */
@Repository
public class CausalQueryDao extends com.chinacreator.gzcm.runtime.core.database.support.JdbcTemplateDaoSupport {

    private final RowMapper<Map<String, Object>> NODE_MAPPER = (rs, rowNum) -> {
        Map<String, Object> n = new LinkedHashMap<>();
        n.put("id", rs.getString("id"));
        n.put("name", rs.getString("name"));
        n.put("type", rs.getString("type"));
        n.put("group", rs.getString("domain_group"));
        return n;
    };

    private final RowMapper<Map<String, Object>> EDGE_MAPPER = (rs, rowNum) -> {
        Map<String, Object> e = new LinkedHashMap<>();
        e.put("id", rs.getString("id"));
        e.put("source", rs.getString("source_node_id"));
        e.put("target", rs.getString("target_node_id"));
        e.put("weight", rs.getDouble("weight"));
        e.put("type", rs.getString("edge_type"));
        return e;
    };

    public Integer ping() {
        return jdbcTemplate.queryForObject("SELECT 1", Integer.class);
    }

    public List<Map<String, Object>> listNodes() {
        return jdbcTemplate.query(
                "SELECT id, name, type, domain_group FROM ecos_causal_node ORDER BY id",
                NODE_MAPPER);
    }

    public List<Map<String, Object>> listEdges() {
        return jdbcTemplate.query(
                "SELECT id, source_node_id, target_node_id, weight, edge_type FROM ecos_causal_edge ORDER BY id",
                EDGE_MAPPER);
    }
}
