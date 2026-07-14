package com.chinacreator.gzcm.worldmodel.service;

import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Config;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.neo4j.driver.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * A1 — Ontology → Neo4j Schema 同步引擎。
 *
 * <p>从 ecos_ontology_entity / ecos_ontology_relationship 读取 Ontology 定义，
 * 映射为 Neo4j Label 约束和 Relationship Type。</p>
 *
 * <p>仅在 enterprise / flagship profile 下激活。</p>
 */
@Service
@Profile({"enterprise", "flagship"})
public class OntologyKgSyncService {

    private static final Logger log = LoggerFactory.getLogger(OntologyKgSyncService.class);

    @Value("${neo4j.uri:bolt://localhost:7687}")
    private String neo4jUri;

    @Value("${neo4j.username:neo4j}")
    private String neo4jUser;

    @Value("${neo4j.password:neo4j123}")
    private String neo4jPass;

    private Driver driver;
    private final JdbcTemplate jdbc;

    public OntologyKgSyncService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @PostConstruct
    void init() {
        driver = GraphDatabase.driver(neo4jUri, AuthTokens.basic(neo4jUser, neo4jPass),
                Config.builder().withConnectionTimeout(5, TimeUnit.SECONDS).build());
        log.info("OntologyKgSyncService Neo4j driver initialized: {}", neo4jUri);
    }

    @PreDestroy
    void close() {
        if (driver != null) driver.close();
    }

    /**
     * 将 Ontology 定义同步到 Neo4j Schema。
     *
     * <ul>
     *   <li>从 ecos_ontology_entity 读取实体 → 为每个 entity.code 创建唯一性约束</li>
     *   <li>从 ecos_ontology_relationship 读取关系 → 记录关系数量</li>
     * </ul>
     *
     * @return {"syncedEntities": N, "syncedRelationships": M}
     */
    public Map<String, Object> syncOntologyToNeo4j() {
        int syncedEntities = 0;
        int syncedRelationships = 0;

        // ── 1. 同步实体 → Neo4j Label 约束 ──
        List<Map<String, Object>> entities = jdbc.queryForList(
            "SELECT code FROM ecos_ontology_entity");

        try (Session session = driver.session()) {
            for (Map<String, Object> entity : entities) {
                String code = (String) entity.get("code");
                if (code == null || code.isEmpty()) continue;

                // 安全校验：Label 名仅允许字母、数字、下划线
                if (!code.matches("[A-Za-z0-9_]+")) {
                    log.warn("Skipping entity with unsafe code for Neo4j label: {}", code);
                    continue;
                }

                try {
                    // Label 名无法参数化 (Cypher 限制)，已通过正则安全校验
                    session.run(
                        "CREATE CONSTRAINT IF NOT EXISTS FOR (n:`" + code + "`) REQUIRE n.id IS UNIQUE"
                    );
                    syncedEntities++;
                    log.debug("Created unique constraint for entity label: {}", code);
                } catch (Exception e) {
                    log.warn("Failed to create constraint for entity {}: {}", code, e.getMessage());
                }
            }
        }

        // ── 2. 同步关系 → 统计关系数量 ──
        List<Map<String, Object>> relationships = jdbc.queryForList(
            "SELECT r.id FROM ecos_ontology_relationship r");
        syncedRelationships = relationships.size();

        log.info("Ontology sync completed: {} entities, {} relationships",
                syncedEntities, syncedRelationships);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("syncedEntities", syncedEntities);
        result.put("syncedRelationships", syncedRelationships);
        return result;
    }
}
