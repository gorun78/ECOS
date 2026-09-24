package com.chinacreator.gzcm.engine.kb.service;

import com.chinacreator.gzcm.engine.kb.KnowledgeGraphService;
import com.chinacreator.gzcm.engine.kb.model.KnowledgeNode;
import com.chinacreator.gzcm.engine.kb.model.KnowledgeEdge;
import com.chinacreator.gzcm.engine.kb.repository.KnowledgeNodeMapper;
import com.chinacreator.gzcm.engine.kb.repository.KnowledgeEdgeMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Service
public class KnowledgeGraphServiceImpl implements KnowledgeGraphService {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeGraphServiceImpl.class);

    private static final int SEARCH_CACHE_TTL_SECONDS = 30;

    private final KnowledgeNodeMapper nodeMapper;
    private final KnowledgeEdgeMapper edgeMapper;
    // PMO-C T3: 分类白名单下沉（kb_nav_article_rel(scope=category) → node_id）
    private final JdbcTemplate jdbcTemplate;
    /** properties 解析（node.propertiesJson → 查 categoryId/navCategoryId） */
    private static final ObjectMapper JSON = new ObjectMapper();

    /**
     * P99 优化: search 结果 30s 缓存。
     * <p>{@code ILIKE '%..%'} 无法命中 B-tree 索引 (需 pg_trgm GIN 才加速),
     * 同源 query 在 UI 弹跳 / 前端防抖 / 多次重绘等高频场景内命中率很高,
     * 缓存至列表显示稳定后 (30s) 到期重建, 兼顾一致性与开销。</p>
     */
    private final Cache<String, List<KnowledgeNode>> searchCache = Caffeine.newBuilder()
            .maximumSize(512)
            .expireAfterWrite(SEARCH_CACHE_TTL_SECONDS, TimeUnit.SECONDS)
            .build();

    public KnowledgeGraphServiceImpl(KnowledgeNodeMapper nodeMapper, KnowledgeEdgeMapper edgeMapper,
                                     JdbcTemplate jdbcTemplate) {
        this.nodeMapper = nodeMapper;
        this.edgeMapper = edgeMapper;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Map<String, Object> getGraph(String domain) {
        // PMO-C 契约：null = 全量（委托无过滤重载，行为与历史一致）
        return getGraph(domain, null);
    }

    @Override
    public Map<String, Object> getGraph(String domain, List<String> categoryIds) {
        try {
            List<KnowledgeNode> nodes = domain != null ? nodeMapper.findByDomain(domain) : nodeMapper.findAll();
            List<KnowledgeEdge> edges = edgeMapper.findAll();
            // PMO-C T3: categoryIds 非空时，先查 kb_nav_article_rel(scope=category) 的节点白名单
            Set<String> nodeWhitelist = resolveCategoryNodeIds(categoryIds);
            if (nodeWhitelist != null) {
                List<KnowledgeNode> filteredNodes = new ArrayList<>(nodes.size());
                for (KnowledgeNode n : nodes) {
                    if (n == null || n.getId() == null) {
                        continue;
                    }
                    // 锚点 kg-root 始终保留；其余须 properties.categoryId/navCategoryId 命中白名单
                    if ("kg-root".equals(n.getId()) || nodeWhitelist.contains(n.getId())
                            || nodeMatchesCategory(n, categoryIds)) {
                        filteredNodes.add(n);
                    }
                }
                Set<String> kept = new HashSet<>();
                for (KnowledgeNode n : filteredNodes) {
                    kept.add(n.getId());
                }
                List<KnowledgeEdge> filteredEdges = new ArrayList<>(edges.size());
                for (KnowledgeEdge e : edges) {
                    if (e != null && kept.contains(e.getSourceNodeId()) && kept.contains(e.getTargetNodeId())) {
                        filteredEdges.add(e);
                    }
                }
                nodes = filteredNodes;
                edges = filteredEdges;
            }
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("nodes", nodes);
            result.put("edges", edges);
            return result;
        } catch (Exception e) {
            log.error("Failed to fetch graph from PG: {}", e.getMessage(), e);
            throw new RuntimeException("图谱数据获取失败", e);
        }
    }

    /**
     * PMO-C T3: 把 categoryIds 映射为可访问节点白名单（kb_nav_article_rel scope=category 的 node_id）。
     *
     * <p>categoryIds 为空时返回 {@code null}（空/ null 等价 = 无过滤，全量；回归保证）；
     * 非空但查库失败时返回空集合 + warn（降级为仅保留 kg-root，不抛）。</p>
     */
    private Set<String> resolveCategoryNodeIds(List<String> categoryIds) {
        if (categoryIds == null || categoryIds.isEmpty()) {
            return null;
        }
        List<String> cleaned = new ArrayList<>(categoryIds.size());
        for (String cid : categoryIds) {
            if (cid != null && !cid.isBlank()) {
                cleaned.add(cid.trim());
            }
        }
        if (cleaned.isEmpty()) {
            return null;
        }
        // 参数化占位（IR05 红线：禁字符串拼接业务 id）
        List<Object> args = new ArrayList<>(cleaned.size());
        StringBuilder inClause = new StringBuilder("(");
        for (int i = 0; i < cleaned.size(); i++) {
            if (i > 0) {
                inClause.append(" OR ");
            }
            inClause.append("c.id = ?");
            args.add(cleaned.get(i));
        }
        inClause.append(")");
        String sql = "SELECT DISTINCT r.node_id FROM ecos_knowledge.kb_nav_article_rel r "
                + "JOIN ecos_knowledge.kb_nav_category c ON c.id = r.node_id AND c.is_deleted = 0 "
                + "WHERE r.is_deleted = 0 AND r.scope = 'category' AND (" + inClause + ")";
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, args.toArray());
            Set<String> out = new HashSet<>();
            for (Map<String, Object> row : rows) {
                Object nid = row.get("node_id");
                if (nid != null) {
                    out.add(String.valueOf(nid));
                }
            }
            return out;
        } catch (Exception e) {
            log.warn("resolveCategoryNodeIds 失败，降级为空白名单（仅保留 kg-root 锚点）: {}", e.getMessage(), e);
            return new HashSet<>();
        }
    }

    /**
     * PMO-C T3 辅助：判断 node 的 properties（categoryId/navCategoryId）是否命中 categoryIds。
     * propertiesJson 解析失败时返回 false（保守：不放行未标记 nodes）。
     */
    private boolean nodeMatchesCategory(KnowledgeNode node, List<String> categoryIds) {
        String propsJson = node.getPropertiesJson();
        if (propsJson == null || propsJson.isBlank()) {
            return false;
        }
        try {
            NodeProperties p = JSON.readValue(propsJson, NodeProperties.class);
            String cid = p.categoryId != null ? p.categoryId : p.navCategoryId;
            return cid != null && categoryIds.contains(cid);
        } catch (Exception e) {
            log.debug("node properties 解析失败（node={}）: {}", node.getId(), e.getMessage());
            return false;
        }
    }

    /** properties 投影（仅取分类字段，避免全量反序列化耦合） */
    private static final class NodeProperties {
        public String categoryId;
        public String navCategoryId;
    }

    @Override
    public Map<String, Object> getNodeDetail(String nodeId) {
        KnowledgeNode node = nodeMapper.findById(nodeId);
        if (node == null) return null;
        List<KnowledgeEdge> outgoing = edgeMapper.findBySourceNodeId(nodeId);
        List<KnowledgeEdge> incoming = edgeMapper.findByTargetNodeId(nodeId);
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("node", node);
        detail.put("outgoingEdges", outgoing);
        detail.put("incomingEdges", incoming);
        return detail;
    }

    @Override
    public List<KnowledgeNode> search(String query) {
        // P0-3 修: 拼 ILIKE 通配符, 避免 PG 扩展协议对 CONCAT('%',?,'%') 推不出参数类型
        if (query == null || query.isBlank()) {
            return Collections.emptyList();
        }
        // P99 优化: 30s 缓存, 前后端重复 query / 防抖 / 前端重绘等同一 query 复用结果
        String cacheKey = "search:" + query;
        return searchCache.get(cacheKey, k -> nodeMapper.searchByLabelPattern("%" + query + "%"));
    }

    @Override
    public Map<String, Object> getShortestPath(String sourceNodeId, String targetNodeId) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("source", sourceNodeId);
        result.put("target", targetNodeId);
        result.put("path", Collections.emptyList());
        result.put("length", -1);
        result.put("note", "Shortest path requires Neo4j — PG fallback returns empty");
        return result;
    }

    @Override
    public Map<String, Object> getNeighbors(String nodeId, int degree) {
        KnowledgeNode node = nodeMapper.findById(nodeId);
        List<KnowledgeEdge> edges = edgeMapper.findBySourceNodeId(nodeId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("center", node);
        result.put("degree", degree);
        result.put("neighbors", edges);
        return result;
    }

    @Override
    public KnowledgeNode createNode(String label, String nodeType, String description, String propertiesJson) {
        LocalDateTime now = LocalDateTime.now();
        KnowledgeNode node = new KnowledgeNode();
        node.setId(UUID.randomUUID().toString());
        node.setLabel(label);
        node.setNodeType(nodeType);
        node.setDescription(description);
        node.setPropertiesJson(propertiesJson);
        node.setCreatedAt(now);
        node.setUpdatedAt(now);
        try {
            nodeMapper.insert(node);
        } catch (Exception e) {
            log.error("Failed to insert knowledge node: label={}, cause={}", label, e.getMessage(), e);
            throw new RuntimeException("节点创建失败", e);
        }
        log.info("Created knowledge node: {} [{}]", node.getId(), label);
        return node;
    }

    @Override
    public KnowledgeEdge createEdge(String sourceNodeId, String targetNodeId, String relationship, double weight) {
        KnowledgeEdge edge = new KnowledgeEdge();
        edge.setId(UUID.randomUUID().toString());
        edge.setSourceNodeId(sourceNodeId);
        edge.setTargetNodeId(targetNodeId);
        edge.setRelationship(relationship);
        edge.setWeight(weight);
        edge.setCreatedAt(LocalDateTime.now());
        try {
            edgeMapper.insert(edge);
        } catch (Exception e) {
            log.error("Failed to insert knowledge edge: {}->[{}]->{} cause={}",
                    sourceNodeId, relationship, targetNodeId, e.getMessage(), e);
            throw new RuntimeException("关系创建失败", e);
        }
        log.info("Created knowledge edge: {} [{}]-[{}]->[{}]", edge.getId(), sourceNodeId, relationship, targetNodeId);
        return edge;
    }

    @Override
    public String getDataSource() {
        try {
            long count = nodeMapper.count();
            return "PostgreSQL (nodes=" + count + ")";
        } catch (Exception e) {
            return "unavailable: " + e.getMessage();
        }
    }
}