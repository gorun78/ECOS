package com.chinacreator.gzcm.engine.ontology.service;

import com.chinacreator.gzcm.common.service.IGraphService;
import com.chinacreator.gzcm.runtime.access.graph.Neo4jClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A1: Neo4j 图服务实现 — 企业版/旗舰版。
 * 仅在 enterprise / ultimate profile 下激活。
 *
 * <p>PMO-74.3 T3: 本类不再直接引用 Neo4j Driver 类，只消费 runtime-access 的
 * Neo4jClient 门面 (plain-Java 签名)，Cypher 编排留在引擎侧。</p>
 */
@Service
@Profile({"enterprise", "ultimate"})
public class Neo4jGraphService implements IGraphService {

    private static final Logger log = LoggerFactory.getLogger(Neo4jGraphService.class);

    @Autowired(required = false)
    private Neo4jClient neo4jClient;

    @PostConstruct
    public void init() {
        if (neo4jClient == null || !neo4jClient.isAvailable()) {
            log.warn("Neo4jGraphService init: Neo4j 不可用 (standard 档 或 neo4j.uri 未配置), IGraphService 按 no-op 处理");
            return;
        }
        log.info("Neo4jGraphService init: 使用 runtime-access Neo4jClient 统一出口");
    }

    @Override
    public List<Map<String, Object>> query(String cypher, Map<String, Object> params) {
        if (neo4jClient == null || !neo4jClient.isAvailable()) return Collections.emptyList();
        try {
            return neo4jClient.run(cypher, params);
        } catch (Exception e) {
            log.error("Neo4j query failed: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    @Override
    public void createNode(String label, Map<String, Object> props) {
        if (neo4jClient == null || !neo4jClient.isAvailable()) return;
        try {
            StringBuilder cypher = new StringBuilder("CREATE (n:").append(label).append(" {");
            Map<String, Object> paramMap = new LinkedHashMap<>();
            int i = 0;
            for (Map.Entry<String, Object> entry : props.entrySet()) {
                if (i > 0) cypher.append(", ");
                String pname = "p" + i;
                cypher.append(entry.getKey()).append(": $").append(pname);
                paramMap.put(pname, entry.getValue());
                i++;
            }
            cypher.append("})");
            neo4jClient.write(cypher.toString(), paramMap);
        } catch (Exception e) {
            log.error("Neo4j createNode failed: {}", e.getMessage());
        }
    }

    @Override
    public void createRelationship(String fromId, String toId, String relType) {
        if (neo4jClient == null || !neo4jClient.isAvailable()) return;
        try {
            neo4jClient.write("MATCH (a {id: $fromId}), (b {id: $toId}) " +
                "CREATE (a)-[:" + relType + "]->(b)",
                Map.of("fromId", fromId, "toId", toId));
        } catch (Exception e) {
            log.error("Neo4j createRelationship failed: {}", e.getMessage());
        }
    }

    @Override
    public Map<String, Object> getSubgraph(String entityId) {
        if (neo4jClient == null || !neo4jClient.isAvailable()) return Map.of();
        try {
            List<Map<String, Object>> rows = neo4jClient.run(
                "MATCH (n {id: $id})-[r]-(m) RETURN n, r, m LIMIT 50",
                Map.of("id", entityId));
            Map<String, Object> subgraph = new LinkedHashMap<>();
            List<Map<String, Object>> nodes = new ArrayList<>();
            List<Map<String, Object>> edges = new ArrayList<>();
            java.util.Set<String> nodeIds = new java.util.LinkedHashSet<>();
            for (Map<String, Object> row : rows) {
                Map<String, Object> n = asMap(row.get("n"));
                Map<String, Object> m = asMap(row.get("m"));
                Map<String, Object> rel = asMap(row.get("r"));
                if (n != null && nodeIds.add(String.valueOf(n.get("elementId")))) {
                    nodes.add(toExternalNode(n));
                }
                if (m != null && nodeIds.add(String.valueOf(m.get("elementId")))) {
                    nodes.add(toExternalNode(m));
                }
                if (rel != null) {
                    edges.add(Map.of(
                        "id", rel.get("elementId"),
                        "type", rel.get("type"),
                        "source", rel.get("startElementId"),
                        "target", rel.get("endElementId")));
                }
            }
            subgraph.put("nodes", nodes);
            subgraph.put("edges", edges);
            return subgraph;
        } catch (Exception e) {
            log.error("Neo4j getSubgraph failed: {}", e.getMessage());
            return Map.of("nodes", Collections.emptyList(), "edges", Collections.emptyList());
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object value) {
        return value instanceof Map ? (Map<String, Object>) value : null;
    }

    private static Map<String, Object> toExternalNode(Map<String, Object> nodeMap) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", nodeMap.get("elementId"));
        out.put("labels", nodeMap.getOrDefault("labels", List.of()));
        out.put("props", nodeMap.getOrDefault("properties", Map.of()));
        return out;
    }
}
