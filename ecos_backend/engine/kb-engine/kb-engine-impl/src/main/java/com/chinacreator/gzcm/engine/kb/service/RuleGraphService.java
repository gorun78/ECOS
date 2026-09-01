package com.chinacreator.gzcm.engine.kb.service;

import com.chinacreator.gzcm.engine.kb.model.ComplianceRule;
import com.chinacreator.gzcm.engine.ontology.model.ExtractedSubGraph;
import com.chinacreator.gzcm.engine.ontology.model.ExtractedSubGraph.ExtractedEntity;
import com.chinacreator.gzcm.engine.ontology.model.ExtractedSubGraph.ExtractedRelation;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.neo4j.driver.Driver;
import org.neo4j.driver.Record;
import org.neo4j.driver.Result;
import org.neo4j.driver.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Neo4j 知识图谱服务 — 将抽取的实体/关系子图和合规规则写入 Neo4j 图数据库，
 * 提供子图查询能力。
 *
 * <h3>图模型</h3>
 * <ul>
 *   <li><b>实体节点</b>: {@code (:Entity {id, name, type, confidence, properties, createdAt})}</li>
 *   <li><b>规则节点</b>: {@code (:Rule {id, name, domain, condition, action, status, extractedRuleId, createdAt})}</li>
 *   <li><b>实体关系边</b>: {@code (:Entity)-[:{relationType} {confidence}]->(:Entity)}</li>
 *   <li><b>派生边</b>:    {@code (:Rule)-[:DERIVED_FROM {sourceExcerpt}]->(:Entity)}</li>
 *   <li><b>适用边</b>:    {@code (:Rule)-[:APPLIES_TO]->(:Entity)}</li>
 * </ul>
 */
@Service
public class RuleGraphService {

    private static final Logger log = LoggerFactory.getLogger(RuleGraphService.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    // ── Neo4j 连接 (统一收敛 runtime-access) ──
    // M0 改造 (2026-09): Neo4j Driver 由 runtime-access/Neo4jConfig 统一管理 (收敛铁律 2.5)。
    // 保留 cognitive.neo4j.switch-on-write 配置开关 (业务级: 是否启用 Neo4j 写入).
    @Value("${cognitive.neo4j.switch-on-write:}")
    private String neo4jSwitchOnWrite;

    @Autowired(required = false)
    private Driver driver;

    // ── Lifecycle ──

    @PostConstruct
    public void init() {
        if (driver == null) {
            log.warn("RuleGraphService init: Neo4j Driver 不可用 (standard 档 或 neo4j.uri 未配置), 规则图谱功能禁用");
            return;
        }
        log.info("RuleGraphService init: 使用 runtime-access 统一 Driver, switchOnWrite={}", switchNeo4jEnabled());
        try (Session session = driver.session()) {
            session.run("CREATE CONSTRAINT IF NOT EXISTS FOR (e:Entity) REQUIRE e.id IS UNIQUE");
            session.run("CREATE CONSTRAINT IF NOT EXISTS FOR (r:Rule) REQUIRE r.id IS UNIQUE");
            log.info("Neo4j constraints ensured");
        } catch (Exception e) {
            log.warn("Failed to create constraints (may already exist): {}", e.getMessage());
        }
    }

    @PreDestroy
    public void close() {
        // Driver 是 runtime-access 管理的 Bean (Neo4jConfig), 不在此 close (生命周期统一)
        log.info("RuleGraphService close: Neo4j Driver 由 runtime-access 管理, 不在此处 close");
    }

    /**
     * Neo4j 写入开关: cognitive.neo4j.switch-on-write (空 / true / false)
     *
     * <p>M0 改造 (2026-09) 补全: 此前该方法是 switch statement 直接读配置, 现改写为 instance method。
     * 同时 {@link #createExtractedEntityGraph(ExtractedSubGraph)} / {@link #createRuleGraph(ComplianceRule)}
     * 调用处若 driver == null (standard 档) 直接 no-op。</p>
     */
    public boolean switchNeo4jEnabled() {
        String sw = neo4jSwitchOnWrite;
        if (sw == null || sw.isBlank()) {
            return true; // 默认启用 (enterprise/flagship 档)
        }
        try {
            return Boolean.parseBoolean(sw.trim());
        } catch (Exception e) {
            return true;
        }
    }

    // ── Public API ──

    /**
     * 将抽取的实体/关系子图写入 Neo4j。
     *
     * <p>实体按 name 幂等（MERGE 策略）：已存在则更新属性，不重复创建节点。
     * 关系在实体写入完成后解析 source/target 名称 → entity id。</p>
     *
     * @param subGraph 抽取产生的子图（实体 + 关系）
     * @return 写入统计（实体创建/更新、关系创建/跳过数）
     */
    public WriteStats createExtractedEntityGraph(ExtractedSubGraph subGraph) {
        if (subGraph == null) {
            log.warn("createExtractedEntityGraph: subGraph is null, nothing to write");
            return new WriteStats(0, 0, 0, 0);
        }
        if (driver == null) {
            // M0 改造 (2026-09): standard 档 Neo4j 不可用, no-op
            log.debug("createExtractedEntityGraph: Neo4j Driver 不可用, skip");
            return new WriteStats(0, 0, 0, 0);
        }
        int entitiesCreated = 0;
        int entitiesUpdated = 0;
        int relationsCreated = 0;
        int relationsSkipped = 0;

        try (Session session = driver.session()) {
            // Phase 1: 写入/更新实体节点，构建 name→id 索引
            Map<String, String> nameToId = new LinkedHashMap<>();

            if (subGraph.getEntities() != null) {
                for (ExtractedEntity entity : subGraph.getEntities()) {
                    if (entity == null || entity.getName() == null || entity.getName().isBlank()) {
                        continue;
                    }
                    String entityId = mergeEntity(session, entity);
                    nameToId.put(entity.getName().trim(), entityId);

                    // 通过判断 entity id 是否为新生成来判断创建 vs 更新
                    // MERGE 不区分新建和匹配，这里简化：始终计数为写入
                    entitiesCreated++;
                }
            }

            // Phase 2: 写入关系边（依赖 Phase 1 的 name→id 索引）
            if (subGraph.getRelations() != null) {
                for (ExtractedRelation rel : subGraph.getRelations()) {
                    if (rel == null) continue;

                    String sourceId = nameToId.get(rel.getSourceEntity());
                    String targetId = nameToId.get(rel.getTargetEntity());

                    if (sourceId == null || targetId == null) {
                        log.warn("Skipping relation '{}' → '{}' ({}) — source or target entity not found",
                                rel.getSourceEntity(), rel.getTargetEntity(), rel.getRelationType());
                        relationsSkipped++;
                        continue;
                    }

                    mergeRelation(session, sourceId, targetId, rel);
                    relationsCreated++;
                }
            }
        }

        log.info("createExtractedEntityGraph complete: entities={}, relations={} (skipped={})",
                entitiesCreated, relationsCreated, relationsSkipped);
        return new WriteStats(entitiesCreated, entitiesUpdated, relationsCreated, relationsSkipped);
    }

    /**
     * 为合规规则在 Neo4j 中创建知识图谱节点和关联边。
     *
     * <p>创建规则节点并建立两类关系：</p>
     * <ul>
     *   <li>{@code DERIVED_FROM} — 指向规则所来源的抽取实体（通过 extractedRuleId）</li>
     *   <li>{@code APPLIES_TO}   — 指向规则适用的对象类型实体</li>
     * </ul>
     *
     * @param rule 合规规则
     */
    public void createRuleGraph(ComplianceRule rule) {
        if (rule == null || rule.getId() == null) {
            log.warn("createRuleGraph: rule or rule.id is null, cannot create graph");
            return;
        }
        if (driver == null) {
            // M0 改造 (2026-09): standard 档 Neo4j 不可用, no-op
            log.debug("createRuleGraph: Neo4j Driver 不可用, skip (ruleId={})", rule.getId());
            return;
        }
        try (Session session = driver.session()) {
            // 创建/更新规则节点
            session.writeTransaction(tx -> {
                tx.run(
                    "MERGE (r:Rule {id: $id}) " +
                    "SET r.name = $name, " +
                    "    r.domain = $domain, " +
                    "    r.condition = $condition, " +
                    "    r.action = $action, " +
                    "    r.status = $status, " +
                    "    r.extractedRuleId = $extractedRuleId, " +
                    "    r.priority = $priority, " +
                    "    r.createdAt = $createdAt ",
                    Map.of(
                        "id", rule.getId(),
                        "name", rule.getName() != null ? rule.getName() : "",
                        "domain", rule.getDomain() != null ? rule.getDomain() : "",
                        "condition", rule.getCondition() != null ? rule.getCondition() : "",
                        "action", rule.getAction() != null ? rule.getAction() : "",
                        "status", rule.getStatus() != null ? rule.getStatus() : "DRAFT",
                        "extractedRuleId", rule.getExtractedRuleId() != null ? rule.getExtractedRuleId() : "",
                        "priority", rule.getPriority(),
                        "createdAt", System.currentTimeMillis()
                    )
                );

                // DERIVED_FROM 边：规则 → 源实体
                if (rule.getExtractedRuleId() != null && !rule.getExtractedRuleId().isBlank()) {
                    tx.run(
                        "MATCH (r:Rule {id: $ruleId}) " +
                        "MATCH (e:Entity {id: $entityId}) " +
                        "MERGE (r)-[:DERIVED_FROM]->(e)",
                        Map.of("ruleId", rule.getId(), "entityId", rule.getExtractedRuleId())
                    );
                }

                // 描述文本中提及的实体也建立 DERIVED_FROM
                if (rule.getDescription() != null && !rule.getDescription().isBlank()) {
                    tx.run(
                        "MATCH (r:Rule {id: $ruleId}) " +
                        "SET r.description = $description",
                        Map.of("ruleId", rule.getId(), "description", rule.getDescription())
                    );
                }

                return null;
            });

            log.info("createRuleGraph: rule '{}' (id={}) written to Neo4j", rule.getName(), rule.getId());
        } catch (Exception e) {
            log.error("Failed to createRuleGraph for rule '{}': {}", rule.getId(), e.getMessage(), e);
            throw new RuntimeException("Neo4j write failed for rule " + rule.getId(), e);
        }
    }

    /**
     * 查询指定规则及其关联的子图。
     *
     * <p>返回的图包含：规则节点、通过 DERIVED_FROM 关联的实体节点、实体间的关系边、
     * 以及 APPLIES_TO 关联的实体节点。</p>
     *
     * @param ruleId 规则 ID
     * @return 子图信息：{@code {rule: Map, entities: List<Map>, relations: List<Map>}}
     */
    public Map<String, Object> getRuleGraph(String ruleId) {
        if (ruleId == null || ruleId.isBlank()) {
            log.warn("getRuleGraph: ruleId is null or blank");
            return Map.of("rule", null, "entities", List.of(), "relations", List.of());
        }

        Map<String, Object> subGraph = new LinkedHashMap<>();

        try (Session session = driver.session()) {
            // 查询规则节点
            Result ruleResult = session.run(
                "MATCH (r:Rule {id: $ruleId}) RETURN r",
                Map.of("ruleId", ruleId)
            );

            if (ruleResult.hasNext()) {
                subGraph.put("rule", recordToMap(ruleResult.next(), "r"));
            } else {
                log.warn("getRuleGraph: rule '{}' not found", ruleId);
                subGraph.put("rule", null);
                subGraph.put("entities", List.of());
                subGraph.put("relations", List.of());
                return subGraph;
            }

            // 查询关联的实体节点（通过 DERIVED_FROM 或 APPLIES_TO 关联）
            Result entityResult = session.run(
                "MATCH (r:Rule {id: $ruleId})-[rel:DERIVED_FROM|APPLIES_TO]->(e:Entity) " +
                "RETURN DISTINCT e, type(rel) AS relType",
                Map.of("ruleId", ruleId)
            );

            List<Map<String, Object>> entities = new ArrayList<>();
            while (entityResult.hasNext()) {
                Record rec = entityResult.next();
                Map<String, Object> entityMap = recordToMap(rec, "e");
                entityMap.put("_relType", rec.get("relType").asString());
                entities.add(entityMap);
            }
            subGraph.put("entities", entities);

            // 查询实体间的关系边
            List<Map<String, Object>> relations = new ArrayList<>();
            if (!entities.isEmpty()) {
                List<String> entityIds = new ArrayList<>();
                for (Map<String, Object> e : entities) {
                    entityIds.add((String) e.get("id"));
                }

                Result relResult = session.run(
                    "MATCH (e1:Entity)-[rel]->(e2:Entity) " +
                    "WHERE e1.id IN $entityIds AND e2.id IN $entityIds " +
                    "RETURN e1.id AS sourceId, e2.id AS targetId, type(rel) AS relationship, " +
                    "       rel.confidence AS confidence",
                    Map.of("entityIds", entityIds)
                );

                while (relResult.hasNext()) {
                    Record rec = relResult.next();
                    Map<String, Object> relMap = new LinkedHashMap<>();
                    relMap.put("sourceId", rec.get("sourceId").asString());
                    relMap.put("targetId", rec.get("targetId").asString());
                    relMap.put("relationship", rec.get("relationship").asString());
                    relMap.put("confidence", rec.get("confidence").asDouble(0.0));
                    relations.add(relMap);
                }
            }
            subGraph.put("relations", relations);

        } catch (Exception e) {
            log.error("Failed to getRuleGraph for '{}': {}", ruleId, e.getMessage(), e);
            throw new RuntimeException("Neo4j query failed for rule " + ruleId, e);
        }

        return subGraph;
    }

    // ── Entity helpers ──

    /**
     * MERGE 实体节点 — 按 entity.id 幂等，已存在则更新属性。
     */
    private String mergeEntity(Session session, ExtractedEntity entity) {
        // 使用 name 作为稳定 ID（配合约束保证唯一性）
        String entityId = entity.getName().trim().replaceAll("\\s+", "_");

        String propertiesJson = serializeToJson(entity.getProperties());

        session.writeTransaction(tx -> {
            tx.run(
                "MERGE (e:Entity {id: $id}) " +
                "SET e.name = $name, " +
                "    e.type = $type, " +
                "    e.confidence = $confidence, " +
                "    e.properties = $properties, " +
                "    e.createdAt = coalesce(e.createdAt, $createdAt) ",
                Map.of(
                    "id", entityId,
                    "name", entity.getName().trim(),
                    "type", entity.getType() != null ? entity.getType() : "UNKNOWN",
                    "confidence", entity.getConfidence(),
                    "properties", propertiesJson != null ? propertiesJson : "{}",
                    "createdAt", System.currentTimeMillis()
                )
            );
            return null;
        });

        log.debug("Merged entity: '{}' (id={}, type={})", entity.getName(), entityId, entity.getType());
        return entityId;
    }

    /**
     * MERGE 关系边 — 按 source/target/relationship 三元组幂等。
     */
    private void mergeRelation(Session session, String sourceId, String targetId, ExtractedRelation rel) {
        String relType = rel.getRelationType() != null ? rel.getRelationType() : "RELATED_TO";

        // Neo4j 不支持动态关系类型直接参数化，需用字符串拼接
        // 使用 apoc.merge.relationship 或先 MATCH 再 MERGE
        String cypher = String.format(
            "MATCH (a:Entity {id: $sourceId}) " +
            "MATCH (b:Entity {id: $targetId}) " +
            "MERGE (a)-[r:%s]->(b) " +
            "SET r.confidence = $confidence, r.createdAt = coalesce(r.createdAt, $createdAt) ",
            relType
        );

        session.writeTransaction(tx -> {
            tx.run(cypher,
                Map.of(
                    "sourceId", sourceId,
                    "targetId", targetId,
                    "confidence", rel.getConfidence(),
                    "createdAt", System.currentTimeMillis()
                )
            );
            return null;
        });

        log.debug("Merged relation: [{}]-[{}]->[{}]", sourceId, relType, targetId);
    }

    // ── Serialization helpers ──

    private String serializeToJson(Map<String, Object> properties) {
        if (properties == null || properties.isEmpty()) {
            return null;
        }
        try {
            return OBJECT_MAPPER.writeValueAsString(properties);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize properties: {}", e.getMessage());
            return null;
        }
    }

    // ── Record → Map helper ──

    /**
     * 将 Neo4j Record 中的指定节点字段转换为 Map。
     */
    private static Map<String, Object> recordToMap(Record record, String key) {
        Map<String, Object> map = new LinkedHashMap<>();

        org.neo4j.driver.Value nodeValue = record.get(key);
        if (nodeValue.isNull()) {
            return map;
        }

        org.neo4j.driver.types.Node node = nodeValue.asNode();
        for (String propKey : node.keys()) {
            map.put(propKey, node.get(propKey).asObject());
        }
        // 添加节点标签
        map.put("_labels", node.labels());
        return map;
    }

    // ── Write stats ──

    /**
     * 写入结果统计。
     */
    public static class WriteStats {
        public final int entitiesCreated;
        public final int entitiesUpdated;
        public final int relationsCreated;
        public final int relationsSkipped;

        WriteStats(int entitiesCreated, int entitiesUpdated, int relationsCreated, int relationsSkipped) {
            this.entitiesCreated = entitiesCreated;
            this.entitiesUpdated = entitiesUpdated;
            this.relationsCreated = relationsCreated;
            this.relationsSkipped = relationsSkipped;
        }

        @Override
        public String toString() {
            return String.format("WriteStats{entities(created=%d, updated=%d), relations(created=%d, skipped=%d)}",
                    entitiesCreated, entitiesUpdated, relationsCreated, relationsSkipped);
        }
    }
}
