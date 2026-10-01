package com.chinacreator.gzcm.engine.kb.service;

import com.chinacreator.gzcm.engine.kb.dao.GraphEdgeQueryDao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
 * <p>
 * SQL 已由 {@link GraphEdgeQueryDao} 统一持有（架构铁律 §2.5 单一数据访问底座 / IR06）。
 *
 * @group GRAPH
 */
@Service
public class GraphEdgeQueryService {

    private static final Logger log = LoggerFactory.getLogger(GraphEdgeQueryService.class);

    private final GraphEdgeQueryDao graphEdgeQueryDao;

    public GraphEdgeQueryService(GraphEdgeQueryDao graphEdgeQueryDao) {
        this.graphEdgeQueryDao = graphEdgeQueryDao;
    }

    /**
     * 加载邻接边（{@code source_id} / {@code target_id} 两列），带条数上限防 OOM。
     *
     * @param limit 最大返回边数（调用方按常量约束）
     * @return 边行列表，键为 {@code source_id} / {@code target_id}
     */
    public List<Map<String, Object>> loadAdjacencyEdges(int limit) {
        List<Map<String, Object>> rows = graphEdgeQueryDao.loadAdjacencyEdges(limit);
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
        return graphEdgeQueryDao.countOutDegrees(ids);
    }

    /**
     * 批量统计入边度（{@code target_id IN (...)} 分组计数）。
     *
     * @param ids 节点 ID 数组（调用方保证非空）
     * @return 度数字行，键为 {@code node} / {@code cnt}；未命中的节点不出现在结果中
     */
    public List<Map<String, Object>> countInDegrees(Object[] ids) {
        return graphEdgeQueryDao.countInDegrees(ids);
    }
}
