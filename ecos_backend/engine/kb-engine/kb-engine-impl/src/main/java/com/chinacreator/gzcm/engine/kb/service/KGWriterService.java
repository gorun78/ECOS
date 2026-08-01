package com.chinacreator.gzcm.engine.kb.service;

import com.chinacreator.gzcm.engine.kb.model.KnowledgeEdge;
import com.chinacreator.gzcm.engine.kb.model.KnowledgeNode;
import com.chinacreator.gzcm.engine.kb.repository.KnowledgeEdgeMapper;
import com.chinacreator.gzcm.engine.kb.repository.KnowledgeNodeMapper;
import com.chinacreator.gzcm.engine.ontology.model.ExtractedSubGraph.ExtractedEntity;
import com.chinacreator.gzcm.engine.ontology.model.ExtractedSubGraph.ExtractedRelation;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * KG 写入服务 — 将 Extractor 产出的 ExtractedEntity / ExtractedRelation
 * 批量写入知识图谱存储（KnowledgeNode + KnowledgeEdge）。
 *
 * <p>负责字段映射、去重（实体按 label 幂等）和名称→ID 解析。</p>
 */
@Service
public class KGWriterService {

    private static final Logger log = LoggerFactory.getLogger(KGWriterService.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final KnowledgeNodeMapper nodeMapper;
    private final KnowledgeEdgeMapper edgeMapper;

    public KGWriterService(KnowledgeNodeMapper nodeMapper, KnowledgeEdgeMapper edgeMapper) {
        this.nodeMapper = nodeMapper;
        this.edgeMapper = edgeMapper;
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
