package com.chinacreator.gzcm.engine.kb.dao;

import com.chinacreator.gzcm.runtime.core.database.support.JdbcTemplateDaoSupport;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

@Repository
public class GraphEdgeQueryDao extends JdbcTemplateDaoSupport {

    public List<Map<String, Object>> loadAdjacencyEdges(int limit) {
        return jdbcTemplate.queryForList(
                "SELECT source_id, target_id FROM ecos_knowledge.graph_edge LIMIT ?", limit);
    }

    public List<Map<String, Object>> countOutDegrees(Object[] ids) {
        return jdbcTemplate.queryForList(
                "SELECT source_id AS node, COUNT(*) AS cnt FROM ecos_knowledge.graph_edge "
                        + "WHERE source_id IN (" + placeholders(ids.length) + ") GROUP BY source_id",
                ids);
    }

    public List<Map<String, Object>> countInDegrees(Object[] ids) {
        return jdbcTemplate.queryForList(
                "SELECT target_id AS node, COUNT(*) AS cnt FROM ecos_knowledge.graph_edge "
                        + "WHERE target_id IN (" + placeholders(ids.length) + ") GROUP BY target_id",
                ids);
    }

    /** 生成 {@code n} 个 {@code ?} 占位符（逗号分隔），与下沉前 Service 内实现一致。 */
    private static String placeholders(int n) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append('?');
        }
        return sb.toString();
    }
}
