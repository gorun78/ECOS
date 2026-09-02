package com.chinacreator.gzcm.engine.ontology.service;

import org.neo4j.driver.Driver;
import org.neo4j.driver.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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

    // M0 改造 (2026-09): Neo4j Driver 由 runtime-access/Neo4jConfig 统一管理 (收敛铁律 2.5)。
    // standard 档 / neo4j.uri 未配置时, @Autowired(required=false) 留 null, 调用方需判空。
    @Autowired(required = false)
    private Driver driver;

    private final JdbcTemplate jdbc;

    public OntologyKgSyncService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @PostConstruct
    void init() {
        if (driver == null) {
            log.warn("OntologyKgSyncService init: Neo4j Driver 不可用 (standard 档 或 neo4j.uri 未配置), Ontology→KG 同步功能禁用");
            return;
        }
        log.info("OntologyKgSyncService init: 使用 runtime-access 统一 Driver");
    }

    @PreDestroy
    void close() {
        // Driver 是 runtime-access 管理的 Bean, 不在此 close (生命周期统一)
        log.info("OntologyKgSyncService close: Neo4j Driver 由 runtime-access 管理, 不在此处 close");
    }

    /**
     * 将 Ontology 定义同步到 Neo4j Schema。
     *
     * <ul>
     *   <li>从 ecos_ontology_entity 读取实体 → 为每个 entity.code 创建唯一性约束</li>
     *   <li>从 ecos_ontology_relationship 读取关系 → 记录关系数量</li>
     * </ul>
     *
     * <p>只在 enterprise / flagship profile 下激活（@Profile 守卫），
     * 若 driver 为 null（neo4j.uri 未配置或 standard 档），返回 syncedEntities=0/syncedRelationships=-1
     * 并 log.warn 禁用状态，不抛 NPE。</p>
     *
     * @return {"syncedEntities": N, "syncedRelationships": M}
     */
    public Map<String, Object> syncOntologyToNeo4j() {
        // M0 改造 (2026-09): 判空 driver — neo4j.uri 未配置 / standard 档时
        // @Autowired(required=false) 留 null，调用方无需判空。
        if (driver == null) {
            log.warn("OntologyKgSyncService.syncOntologyToNeo4j: Neo4j Driver 不可用 (standard 档 或 neo4j.uri 未配置), 同步功能禁用 — 返回 syncedEntities=0/syncedRelationships=-1");
            Map<String, Object> disabled = new LinkedHashMap<>();
            disabled.put("syncedEntities", 0);
            disabled.put("syncedRelationships", -1);
            disabled.put("disabled", true);
            disabled.put("reason", "Neo4j Driver 不可用 (standard 档 或 neo4j.uri 未配置)");
            return disabled;
        }

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
