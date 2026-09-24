package com.chinacreator.gzcm.engine.kb;

import com.chinacreator.gzcm.engine.kb.model.KnowledgeNode;
import com.chinacreator.gzcm.engine.kb.model.KnowledgeEdge;

import java.util.List;
import java.util.Map;

public interface KnowledgeGraphService {

    Map<String, Object> getGraph(String domain);

    /**
     * PMO-C T3: 图谱查询带分类白名单过滤（按 {@code kb_nav_article_rel} 中 scope=category
     * 的 node_id 映射 article 级可访问 nodes，白名单外 nodes 被移除，edges 同步裁剪）。
     *
     * <p>{@code categoryIds == null || empty} 时与 {@link #getGraph(String)} 完全一致
     * （全量返回，无过滤）。图谱节点规模通常 < 1K，PE 内存过滤可接受；
     * 跨 Neo4j↔PG 统一存储模型下沉为 P3 优化项。</p>
     */
    Map<String, Object> getGraph(String domain, List<String> categoryIds);

    Map<String, Object> getNodeDetail(String nodeId);

    List<KnowledgeNode> search(String query);

    Map<String, Object> getShortestPath(String sourceNodeId, String targetNodeId);

    Map<String, Object> getNeighbors(String nodeId, int degree);

    KnowledgeNode createNode(String label, String nodeType, String description, String propertiesJson);

    KnowledgeEdge createEdge(String sourceNodeId, String targetNodeId, String relationship, double weight);

    String getDataSource();
}