package com.chinacreator.gzcm.engine.kb.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * GraphEdgeQueryService — 知识图谱边表（{@code ecos_knowledge.graph_edge}）只读查询层
 * （PMO-74 H9-T4）。
 * <p>
 * 从 {@code GraphQueryPostController} 下沉的 SQL 访问：原先 Controller 直接持有
 * {@code JdbcTemplate}（违反硬规则「Controller 禁止直用 JdbcTemplate — 必过 Service」）。
 * 本服务只返回<b>原始列名（snake_case）为键</b>的结果行，图结构装配（邻接表 / 度数表）
 * 仍由调用方完成，以保证下沉前后响应体逐字段等价。
 * <p>
 * 只读：本服务不含任何写路径；边写入仍归 {@code KGWriterService} /
 * {@code KnowledgeEdgeMapper}。
 *
 * @group GRAPH
 */
@Service
public class GraphEdgeQueryService {

    private static final Logger log = LoggerFactory.getLogger(GraphEdgeQueryService.class);

    private final JdbcTemplate jdbc;

    public GraphEdgeQueryService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * 加载邻接边（{@code source_id} / {@code target_id} 两列），带条数上限防 OOM。
     *
     * @param limit 最大返回边数（调用方按常量约束）
     * @return 边行列表，键为 {@code source_id} / {@code target_id}
     */
    public List<Map<String, Object>> loadAdjacencyEdges(int limit) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT source_id, target_id FROM ecos_knowledge.graph_edge LIMIT ?", limit);
        log.debug("loadAdjacencyEdges: limit={}, rows={}", limit, rows.size());
        return rows;
    }

    /**
     * 批量统计出边度（{@code source_id IN (...)} 分组计数）。
     *
     * @param ids 节点 ID 数组（调用方保证非空）
     * @return 度数字行，键为 {@code node} / {@code cnt}；未命中的节点不出现在结果中
     */
    public List<Map<String, Object>> countOutDegrees(Object[] ids) {
        return jdbc.queryForList(
                "SELECT source_id AS node, COUNT(*) AS cnt FROM ecos_knowledge.graph_edge "
                        + "WHERE source_id IN (" + placeholders(ids.length) + ") GROUP BY source_id",
                ids);
    }

    /**
     * 批量统计入边度（{@code target_id IN (...)} 分组计数）。
     *
     * @param ids 节点 ID 数组（调用方保证非空）
     * @return 度数字行，键为 {@code node} / {@code cnt}；未命中的节点不出现在结果中
     */
    public List<Map<String, Object>> countInDegrees(Object[] ids) {
        return jdbc.queryForList(
                "SELECT target_id AS node, COUNT(*) AS cnt FROM ecos_knowledge.graph_edge "
                        + "WHERE target_id IN (" + placeholders(ids.length) + ") GROUP BY target_id",
                ids);
    }

    /** 生成 {@code n} 个 {@code ?} 占位符（逗号分隔），与下沉前 Controller 内实现一致。 */
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
