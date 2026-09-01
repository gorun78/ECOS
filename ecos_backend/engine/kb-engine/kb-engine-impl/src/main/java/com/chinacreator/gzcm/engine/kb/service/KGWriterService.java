package com.chinacreator.gzcm.engine.kb.service;

import com.chinacreator.gzcm.engine.kb.model.KnowledgeEdge;
import com.chinacreator.gzcm.engine.kb.model.KnowledgeNode;
import com.chinacreator.gzcm.engine.kb.repository.KnowledgeEdgeMapper;
import com.chinacreator.gzcm.engine.kb.repository.KnowledgeNodeMapper;
import com.chinacreator.gzcm.engine.ontology.model.ExtractedSubGraph.ExtractedEntity;
import com.chinacreator.gzcm.engine.ontology.model.ExtractedSubGraph.ExtractedRelation;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.neo4j.driver.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.*;

/**
 * KG 写入服务 — 将 Extractor 产出的 ExtractedEntity / ExtractedRelation
 * 批量写入知识图谱存储（KnowledgeNode + KnowledgeEdge）。
 *
 * <p>负责字段映射、去重（实体按 label 幂等）和名称→ID 解析。</p>
 *
 * <p>Neo4j 连接池 (enterprise edition): 最大连接10, 最小空闲2, 30s健康检查, 自动重连。</p>
 */
@Service
public class KGWriterService {

    private static final Logger log = LoggerFactory.getLogger(KGWriterService.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    // ── Neo4j (统一收敛 runtime-access) ──
    // M0 改造 (2026-09): Neo4j Driver 由 runtime-access/Neo4jConfig 统一管理 (收敛铁律 2.5)。
    // Pool 参数 (MAX_CONNECTION_POOL_SIZE 等) 默认在 Neo4jConfig 内设置为 10 / 30s,
    // 如需各 Service 不同 Pool 参数, 由 Neo4jConfig 提供 Factory + Bean name 区分 (后续增强)。
    private static final int MAX_CONNECTION_POOL_SIZE = 10; // 仅用于 log 显示
    private static final int MAX_RETRY_ATTEMPTS = 3;
    private static final long RETRY_DELAY_MS = 1000;

    private final KnowledgeNodeMapper nodeMapper;
    private final KnowledgeEdgeMapper edgeMapper;

    /**
     * Neo4j driver — nullable for standard edition
     * M0 改造 (2026-09): 改用 @Autowired(required=false) 注入 runtime-access 统一 Driver.
     */
    @Autowired(required = false)
    private volatile Driver neo4jDriver;

    private volatile boolean neo4jAvailable = false;

    public KGWriterService(KnowledgeNodeMapper nodeMapper, KnowledgeEdgeMapper edgeMapper) {
        this.nodeMapper = nodeMapper;
        this.edgeMapper = edgeMapper;
    }

    // ── Neo4j 生命周期 ──

    /**
     * 初始化 Neo4j 连接池 (现已统一收敛 runtime-access/Neo4jConfig)。
     * M0 改造 (2026-09): 删除原 GraphDatabase.driver 创建逻辑, 改为消费 runtime-access
     * 提供的共享 Driver Bean (Pool 参数 10 / 30s / 10s acquisition / 30min lifetime 由 Neo4jConfig 统一定义)。
     */
    @PostConstruct
    public void neo4jPoolInit() {
        if (neo4jDriver == null) {
            neo4jAvailable = false;
            log.warn("⚠️  KGWriterService init: Neo4j Driver 不可用 (standard 档 或 neo4j.uri 未配置), KG 写入走 PG fallback");
            return;
        }
        try {
            verifyConnectivity();
            neo4jAvailable = true;
            log.info("✅ KGWriterService init: 使用 runtime-access 统一 Driver (pool≈{} connections)", MAX_CONNECTION_POOL_SIZE);
        } catch (Exception e) {
            neo4jAvailable = false;
            log.warn("⚠️  KGWriterService init: Neo4j 连接验证失败, 走 PG fallback: {}", e.getMessage());
        }
    }

    /**
     * Neo4j 健康检查 — 每30秒 ping。
     * MATCH (n) RETURN count(n) LIMIT 1
     */
    @Scheduled(fixedRate = 30_000)
    public void neo4jHealthCheck() {
        if (neo4jDriver == null) {
            log.debug("Neo4j health check skipped — driver not initialized");
            return;
        }
        try {
            executeWithRetry(() -> {
                try (Session session = neo4jDriver.session()) {
                    Result result = session.run("MATCH (n) RETURN count(n) AS cnt LIMIT 1");
                    if (result.hasNext()) {
                        long count = result.next().get("cnt").asLong();
                        log.debug("Neo4j health OK — node count: {}", count);
                    }
                }
                return null;
            });
            if (!neo4jAvailable) {
                neo4jAvailable = true;
                log.info("✅ Neo4j reconnected");
            }
        } catch (Exception e) {
            neo4jAvailable = false;
            log.warn("⚠️  Neo4j health check failed: {}", e.getMessage());
        }
    }

    @PreDestroy
    public void neo4jPoolDestroy() {
        // M0 改造 (2026-09): Driver 是 runtime-access 管理的 Bean, 不在此 close (生命周期统一)
        log.info("KGWriterService neo4jPoolDestroy: Neo4j Driver 由 runtime-access 管理, 不在此处 close");
    }

    /**
     * Neo4j 连接连通性验证。
     */
    public boolean verifyConnectivity() {
        if (neo4jDriver == null) return false;
        try {
            neo4jDriver.verifyConnectivity();
            return true;
        } catch (Exception e) {
            log.warn("Neo4j connectivity verification failed: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Neo4j 可用性查询。
     */
    public boolean isNeo4jAvailable() {
        return neo4jAvailable && neo4jDriver != null;
    }

    /**
     * 获取 Neo4j 节点总数 (用于健康检查报告)。
     */
    public long getNeo4jNodeCount() {
        if (!isNeo4jAvailable()) return -1;
        return executeWithRetry(() -> {
            try (Session session = neo4jDriver.session()) {
                Result result = session.run("MATCH (n) RETURN count(n) AS cnt");
                return result.hasNext() ? result.next().get("cnt").asLong() : 0;
            }
        });
    }

    // ── 带重试的执行器 ──

    /**
     * 自动重连：连接断开后3次重试, 间隔1s。
     */
    private <T> T executeWithRetry(Neo4jOperation<T> operation) {
        Exception lastException = null;
        for (int attempt = 1; attempt <= MAX_RETRY_ATTEMPTS; attempt++) {
            try {
                return operation.execute();
            } catch (Exception e) {
                lastException = e;
                if (attempt < MAX_RETRY_ATTEMPTS) {
                    log.warn("Neo4j operation failed (attempt {}/{}), retrying in {}ms...",
                            attempt, MAX_RETRY_ATTEMPTS, RETRY_DELAY_MS);
                    try {
                        Thread.sleep(RETRY_DELAY_MS);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("Neo4j retry interrupted", ie);
                    }
                }
            }
        }
        throw new RuntimeException("Neo4j operation failed after " + MAX_RETRY_ATTEMPTS + " attempts", lastException);
    }

    @FunctionalInterface
    private interface Neo4jOperation<T> {
        T execute() throws Exception;
    }

    // ── Public API ──

    /**
     * 批量写入抽取的实体和关系。
     * 实体按名称（label）去重 — 已存在的实体更新属性，不重复创建。
     * 关系在实体写入完成后解析 source/target 实体名称 → node ID。
     *
     * @return 写入结果统计
     */
    public BatchWriteResult writeBatch(List<ExtractedEntity> entities, List<ExtractedRelation> relations) {
        int entitiesCreated = 0;
        int entitiesUpdated = 0;
        int relationsCreated = 0;
        int relationsSkipped = 0;

        // Phase 1: 写入/更新实体，同时构建 name→id 索引
        Map<String, String> nameToId = new HashMap<>();
        if (entities != null) {
            for (ExtractedEntity entity : entities) {
                WriteEntityResult result = writeEntity(entity);
                nameToId.put(entity.getName(), result.nodeId);
                if (result.isNew) {
                    entitiesCreated++;
                } else {
                    entitiesUpdated++;
                }
            }
        }

        // Phase 2: 写入关系（依赖 Phase 1 的 name→id 索引）
        if (relations != null) {
            for (ExtractedRelation rel : relations) {
                boolean created = writeRelation(rel, nameToId);
                if (created) {
                    relationsCreated++;
                } else {
                    relationsSkipped++;
                }
            }
        }

        log.info("Batch write complete: entities(new={}, updated={}), relations(created={}, skipped={})",
                entitiesCreated, entitiesUpdated, relationsCreated, relationsSkipped);

        return new BatchWriteResult(entitiesCreated, entitiesUpdated, relationsCreated, relationsSkipped);
    }

    // ── Entity write ──

    /**
     * 写入单个抽取实体 — 按 name→label 去重。
     * <ul>
     *   <li>实体不存在 → 创建新 KnowledgeNode</li>
     *   <li>实体已存在 → 更新 description 和 propertiesJson</li>
     * </ul>
     */
    public WriteEntityResult writeEntity(ExtractedEntity entity) {
        if (entity == null || entity.getName() == null || entity.getName().isBlank()) {
            log.warn("Skipping entity with null/blank name");
            return new WriteEntityResult(null, false);
        }

        String name = entity.getName().trim();
        KnowledgeNode existing = nodeMapper.findByLabel(name);

        if (existing != null) {
            // 已存在：合并更新
            boolean updated = false;
            if (entity.getType() != null && !entity.getType().equals(existing.getNodeType())) {
                existing.setNodeType(entity.getType());
                updated = true;
            }
            // 合并 properties
            String mergedProps = mergeProperties(existing.getPropertiesJson(), entity.getProperties());
            if (mergedProps != null && !mergedProps.equals(existing.getPropertiesJson())) {
                existing.setPropertiesJson(mergedProps);
                updated = true;
            }
            if (entity.getConfidence() > 0) {
                existing.setDescription("confidence=" + entity.getConfidence());
                updated = true;
            }
            if (updated) {
                existing.setUpdatedAt(System.currentTimeMillis());
                nodeMapper.insert(existing); // re-insert for simplicity (or add update method)
            }
            log.debug("Entity '{}' already exists (id={}), updated={}", name, existing.getId(), updated);
            return new WriteEntityResult(existing.getId(), false);
        }

        // 新实体
        KnowledgeNode node = new KnowledgeNode();
        node.setId(UUID.randomUUID().toString());
        node.setLabel(name);
        node.setNodeType(entity.getType() != null ? entity.getType() : "UNKNOWN");
        node.setDescription("confidence=" + entity.getConfidence());
        node.setPropertiesJson(serializeProperties(entity.getProperties()));
        node.setCreatedAt(System.currentTimeMillis());
        node.setUpdatedAt(System.currentTimeMillis());
        nodeMapper.insert(node);
        log.info("Created entity: '{}' (id={}, type={})", name, node.getId(), node.getNodeType());
        return new WriteEntityResult(node.getId(), true);
    }

    // ── Relation write ──

    /**
     * 写入单个抽取关系 — 需先通过 name→id 映射解析 source/target 实体。
     *
     * @param rel      抽取的关系
     * @param nameToId 实体名称 → node ID 的映射（由前置 entity 写入阶段构建）
     * @return true if created, false if skipped (missing source/target resolution)
     */
    public boolean writeRelation(ExtractedRelation rel, Map<String, String> nameToId) {
        if (rel == null) {
            return false;
        }

        String sourceId = nameToId.get(rel.getSourceEntity());
        String targetId = nameToId.get(rel.getTargetEntity());

        if (sourceId == null || targetId == null) {
            log.warn("Skipping relation '{}' → '{}' ({}) — source or target entity not found",
                    rel.getSourceEntity(), rel.getTargetEntity(), rel.getRelationType());
            return false;
        }

        KnowledgeEdge edge = new KnowledgeEdge();
        edge.setId(UUID.randomUUID().toString());
        edge.setSourceNodeId(sourceId);
        edge.setTargetNodeId(targetId);
        edge.setRelationship(rel.getRelationType() != null ? rel.getRelationType() : "RELATED_TO");
        edge.setWeight(rel.getConfidence());
        edge.setCreatedAt(System.currentTimeMillis());
        edgeMapper.insert(edge);
        log.debug("Created relation: [{}] -[{}]-> [{}] (confidence={})",
                rel.getSourceEntity(), rel.getRelationType(), rel.getTargetEntity(), rel.getConfidence());
        return true;
    }

    // ── Helpers ──

    private String serializeProperties(Map<String, Object> properties) {
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

    /**
     * 合并已有 propertiesJson 和新抽取的 properties。
     * 新值覆盖旧值中同名字段。
     */
    private String mergeProperties(String existingJson, Map<String, Object> newProperties) {
        Map<String, Object> merged = new LinkedHashMap<>();

        // 解析已有 JSON
        if (existingJson != null && !existingJson.isBlank()) {
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> existing = OBJECT_MAPPER.readValue(existingJson, Map.class);
                merged.putAll(existing);
            } catch (Exception e) {
                log.debug("Could not parse existing properties: {}", e.getMessage());
            }
        }

        // 合并新属性（覆盖）
        if (newProperties != null) {
            merged.putAll(newProperties);
        }

        return merged.isEmpty() ? null : serializeProperties(merged);
    }

    // ── Result types ──

    public static class WriteEntityResult {
        public final String nodeId;
        public final boolean isNew;

        WriteEntityResult(String nodeId, boolean isNew) {
            this.nodeId = nodeId;
            this.isNew = isNew;
        }
    }

    public static class BatchWriteResult {
        public final int entitiesCreated;
        public final int entitiesUpdated;
        public final int relationsCreated;
        public final int relationsSkipped;

        BatchWriteResult(int entitiesCreated, int entitiesUpdated, int relationsCreated, int relationsSkipped) {
            this.entitiesCreated = entitiesCreated;
            this.entitiesUpdated = entitiesUpdated;
            this.relationsCreated = relationsCreated;
            this.relationsSkipped = relationsSkipped;
        }

        public int totalEntities() { return entitiesCreated + entitiesUpdated; }
        public int totalRelations() { return relationsCreated + relationsSkipped; }

        @Override
        public String toString() {
            return String.format("BatchWriteResult{entities(new=%d, updated=%d), relations(created=%d, skipped=%d)}",
                    entitiesCreated, entitiesUpdated, relationsCreated, relationsSkipped);
        }
    }
}
