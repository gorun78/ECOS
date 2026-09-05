package com.chinacreator.gzcm.engine.ontology.service;

import com.chinacreator.gzcm.common.service.IGraphService;
import org.neo4j.driver.*;
import org.neo4j.driver.Record;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.*;

/**
 * A1: Neo4j 图服务实现 — 企业版/旗舰版。
 * 仅在 enterprise / flagship profile 下激活。
 */
@Service
@Profile({"enterprise", "flagship"})
public class Neo4jGraphService implements IGraphService {

    private static final Logger log = LoggerFactory.getLogger(Neo4jGraphService.class);

    // M0 改造 (2026-09): Neo4j Driver 由 runtime-access/Neo4jConfig 统一管理 (收敛铁律 2.5)。
    // standard 档 / neo4j.uri 未配置时, @Autowired(required=false) 留 null, query() 等需判空。
    @Autowired(required = false)
    private Driver driver;

    @PostConstruct
    public void init() {
        if (driver == null) {
            log.warn("Neo4jGraphService init: Neo4j Driver 不可用 (standard 档 或 neo4j.uri 未配置), IGraphService 按 no-op 处理");
            return;
        }
        log.info("Neo4jGraphService init: 使用 runtime-access 统一 Driver");
    }

    @PreDestroy
    public void destroy() {
        // Driver 是 runtime-access 管理的 Bean, 不在此 close (生命周期统一)
        log.info("Neo4jGraphService destroy: Neo4j Driver 由 runtime-access 管理, 不在此处 close");
    }

    @Override
    public List<Map<String, Object>> query(String cypher, Map<String, Object> params) {
        if (driver == null) return Collections.emptyList();
        try (var session = driver.session()) {
            var result = session.run(cypher, params);
            List<Map<String, Object>> list = new ArrayList<>();
            while (result.hasNext()) {
                list.add(result.next().asMap());
            }
            return list;
        } catch (Exception e) {
            log.error("Neo4j query failed: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    @Override
    public void createNode(String label, Map<String, Object> props) {
        if (driver == null) return;
        try (var session = driver.session()) {
            StringBuilder cypher = new StringBuilder("CREATE (n:").append(label).append(" {");
            Map<String, Object> paramMap = new LinkedHashMap<>();
            int i = 0;
            for (var entry : props.entrySet()) {
                if (i > 0) cypher.append(", ");
                String pname = "p" + i;
                cypher.append(entry.getKey()).append(": $").append(pname);
                paramMap.put(pname, entry.getValue());
                i++;
            }
            cypher.append("})");
            session.run(cypher.toString(), paramMap);
        } catch (Exception e) {
            log.error("Neo4j createNode failed: {}", e.getMessage());
        }
    }

    @Override
    public void createRelationship(String fromId, String toId, String relType) {
        if (driver == null) return;
        try (var session = driver.session()) {
            session.run("MATCH (a {id: $fromId}), (b {id: $toId}) " +
                "CREATE (a)-[:" + relType + "]->(b)",
                Map.of("fromId", fromId, "toId", toId));
        } catch (Exception e) {
            log.error("Neo4j createRelationship failed: {}", e.getMessage());
        }
    }

    @Override
    public Map<String, Object> getSubgraph(String entityId) {
        if (driver == null) return Map.of();
        try (var session = driver.session()) {
            var result = session.run(
                "MATCH (n {id: $id})-[r]-(m) RETURN n, r, m LIMIT 50",
                Map.of("id", entityId));
            Map<String, Object> subgraph = new LinkedHashMap<>();
            List<Map<String, Object>> nodes = new ArrayList<>();
            List<Map<String, Object>> edges = new ArrayList<>();
            Set<String> nodeIds = new LinkedHashSet<>();
            while (result.hasNext()) {
                var record = result.next();
                var n = record.get("n").asNode();
                var m = record.get("m").asNode();
                if (nodeIds.add(n.elementId())) {
                    nodes.add(Map.of("id", n.elementId(), "labels", n.labels(), "props", n.asMap()));
                }
                if (nodeIds.add(m.elementId())) {
                    nodes.add(Map.of("id", m.elementId(), "labels", m.labels(), "props", m.asMap()));
                }
                var rel = record.get("r").asRelationship();
                edges.add(Map.of(
                    "id", rel.elementId(),
                    "type", rel.type(),
                    "source", rel.startNodeElementId(),
                    "target", rel.endNodeElementId()));
            }
            subgraph.put("nodes", nodes);
            subgraph.put("edges", edges);
            return subgraph;
        } catch (Exception e) {
            log.error("Neo4j getSubgraph failed: {}", e.getMessage());
            return Map.of("nodes", Collections.emptyList(), "edges", Collections.emptyList());
        }
    }
}
