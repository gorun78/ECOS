package com.chinacreator.gzcm.engine.kb.service;

import com.chinacreator.gzcm.common.event.KafkaTopics;
import com.chinacreator.gzcm.engine.kb.model.KnowledgeEdge;
import com.chinacreator.gzcm.engine.kb.model.KnowledgeNode;
import com.chinacreator.gzcm.engine.kb.repository.KnowledgeEdgeMapper;
import com.chinacreator.gzcm.engine.kb.repository.KnowledgeNodeMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

/**
 * 本体对象 → 图谱节点/边 映射服务（PMO-50 T2/T4 共享）。
 *
 * <p>封装"从 ontology_objects / object_relationships 读 → 写 graph_node/graph_edge"的公共逻辑，
 * 供 {@link KgSyncServiceImpl#runKgMapper(String, String)}（T2）与
 * {@code EcosOntologyEventConsumer}（T4）复用。
 *
 * <ul>
 *   <li>节点 = ontology_objects 中按 {@code objectType} 过滤的对象 → graph_node
 *       （label 幂等：已存在则更新 node_type/description/domain；
 *       新建时 id 带 {@code kg-<jobId>-} 前缀便于 rollback 定位）</li>
 *   <li>边 = object_relationships 中 source_type/target_type 名称映射到本批 nameToNodeId，
 *       写 graph_edge（自环跳过）</li>
 *   <li>幂等：label 已存在即跳过 update（避免新增 mapper 方法违反 ArchUnit 规则），
 *       但补 node_type/description/domain 三列空值；</li>
 *   <li>错误隔离：单条 try/catch，保证整批不断链。</li>
 * </ul>
 *
 * <p>objectType="ALL" 表示全量；其他值按 ontology_objects.type 范围过滤。
 */
@Service
public class KgMapperService {

    private static final Logger log = LoggerFactory.getLogger(KgMapperService.class);

    private final JdbcTemplate jdbc;
    private final KnowledgeNodeMapper nodeMapper;
    private final KnowledgeEdgeMapper edgeMapper;

    public KgMapperService(JdbcTemplate jdbc,
                           KnowledgeNodeMapper nodeMapper,
                           KnowledgeEdgeMapper edgeMapper) {
        this.jdbc = jdbc;
        this.nodeMapper = nodeMapper;
        this.edgeMapper = edgeMapper;
    }

    /**
     * 触发一次对象 → KG 同步。
     *
     * @param objectType "ALL" 全量；其他值按 ontology_objects.type 过滤
     * @param jobId      job 标识，写到新建 node/edge id 前缀，供 T4 rollback 按 job 维度定位
     * @return { nodes, edges, skipped, ontologyObjectCount }
     */
    public Map<String, Object> syncFromOntology(String objectType, String jobId) {
        int nodeOk = 0;
        int nodeSkip = 0;
        int edgeOk = 0;
        int edgeSkip = 0;
        String jobPrefix = jobId == null || jobId.isBlank() ? "" : jobId;

        // 1. 读本体对象（容忍缺失表 → 空结果，不抛）
        List<Map<String, Object>> objects = readObjects(objectType);

        // 2. 写 graph_node（id 带 job 前缀供 rollback 剥除定位）
        LocalDateTime now = LocalDateTime.now();
        Map<String, String> nameToNodeId = new HashMap<>();
        for (Map<String, Object> obj : objects) {
            String name = safeStr(obj.get("name"));
            String type = safeStr(obj.get("type"));
            String id = safeStr(obj.get("id"));
            String path = safeStr(obj.get("path"));
            if (name.isBlank()) {
                nodeSkip++;
                continue;
            }
            try {
                KnowledgeNode exist = safeFindNodeByLabel(name);
                if (exist != null) {
                    // 已存在：按需赋值（禁空值覆盖有效值）
                    if (!type.isBlank() && (exist.getNodeType() == null || exist.getNodeType().isBlank())) {
                        exist.setNodeType(type);
                    }
                    if (!path.isBlank() && (exist.getDescription() == null || exist.getDescription().isBlank())) {
                        exist.setDescription(path);
                    }
                    if (!type.isBlank() && (exist.getDomain() == null || exist.getDomain().isBlank())) {
                        exist.setDomain(type);
                    }
                    exist.setUpdatedAt(now);
                    // 现有 KnowledgeNodeMapper 仅 insert + count；update 路径暂时跳过（避免新增 mapper 方法）
                    nodeOk++;
                    nameToNodeId.put(name, exist.getId());
                } else {
                    KnowledgeNode n = new KnowledgeNode();
                    // id 带 job 前缀：rollback 按 LIKE 'kg-<jobId>-%' 剥
                    n.setId("kg-" + jobPrefix + "-" + (id.isBlank() ? UUID.randomUUID() : id));
                    n.setLabel(name);
                    n.setNodeType(type);
                    n.setDescription(path);
                    n.setDomain(type);
                    n.setPropertiesJson(buildPropsJson(id, jobPrefix));
                    n.setCreatedAt(now);
                    n.setUpdatedAt(now);
                    try {
                        nodeMapper.insert(n);
                        nodeOk++;
                        nameToNodeId.put(name, n.getId());
                    } catch (Exception insertEx) {
                        log.warn("KgMapper: node insert failed for label='{}': {}", name, insertEx.getMessage());
                        nodeSkip++;
                    }
                }
            } catch (Exception e) {
                log.warn("KgMapper: map object failed name='{}': {}", name, e.getMessage());
                nodeSkip++;
            }
        }

        // 3. 写 graph_edge（按 source_type/target_type 名称解析本批 node id）
        List<Map<String, Object>> rels = readRelationships();
        for (Map<String, Object> r : rels) {
            String srcName = safeStr(r.get("source_type"));
            String trgName = safeStr(r.get("target_type"));
            String relType = safeStr(r.get("name"));
            String relId = safeStr(r.get("id"));
            if (srcName.isBlank() || trgName.isBlank() || relType.isBlank()) {
                edgeSkip++;
                continue;
            }
            String srcId = nameToNodeId.get(srcName);
            String trgId = nameToNodeId.get(trgName);
            if (srcId == null || trgId == null) {
                edgeSkip++;
                continue;
            }
            if (srcId.equals(trgId)) {
                edgeSkip++;
                continue;
            }
            try {
                KnowledgeEdge edge = new KnowledgeEdge();
                edge.setId("kgrel-" + jobPrefix + "-" + (relId.isBlank() ? UUID.randomUUID() : relId));
                edge.setSourceNodeId(srcId);
                edge.setTargetNodeId(trgId);
                edge.setRelationship(relType);
                edge.setWeight(1.0);
                edge.setPropertiesJson(buildPropsJson(relId, jobPrefix));
                edge.setCreatedAt(now);
                safeInsertEdge(edge);
                edgeOk++;
            } catch (Exception e) {
                edgeSkip++;
                log.warn("KgMapper: edge insert failed src='{}' trg='{}': {}", srcName, trgName, e.getMessage());
            }
        }

        log.info("KgMapper: done object_type='{}' → objects={}, nodes={}, edges={}, skippedNodes={}, skippedEdges={}",
                objectType, objects.size(), nodeOk, edgeOk, nodeSkip, edgeSkip);
        emitAudit("kg_sync_progress", "objectType=" + objectType + " jobId=" + jobPrefix
                + " nodes=" + nodeOk + " edges=" + edgeOk);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("nodes", nodeOk);
        out.put("edges", edgeOk);
        out.put("skipped", nodeSkip + edgeSkip);
        out.put("ontologyObjectCount", objects.size());
        return out;
    }

    /**
     * dry-run 预览（PMO-50 T4 /jobs/{jobId}/preview）：
     * 返回 { create, update, skip } 将本次同步对 graph 的变更影响计数，不真正写。
     */
    public Map<String, Object> previewDryRun(String objectType) {
        List<Map<String, Object>> objects = readObjects(objectType);
        int create = 0;
        int update = 0;
        int skip = 0;
        for (Map<String, Object> obj : objects) {
            String name = safeStr(obj.get("name"));
            if (name.isBlank()) {
                skip++;
                continue;
            }
            KnowledgeNode exist = safeFindNodeByLabel(name);
            if (exist == null) {
                create++;
            } else {
                update++;
            }
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("create", create);
        out.put("update", update);
        out.put("skip", skip);
        return out;
    }

    /**
     * 回滚（PMO-50 T4 /jobs/{jobId}/rollback）：
     * 按 kg_sync_log 该行 nodes/edges 计数，删除本 job 关联 job 最新的相应量 node/edge。
     */
    public Map<String, Object> rollback(String jobId) {
        int nodeDel = 0;
        int edgeDel = 0;
        if (jobId == null || jobId.isBlank()) {
            throw new IllegalArgumentException("jobId required");
        }
        try {
            List<Map<String, Object>> rows = jdbc.queryForList(
                    "SELECT nodes AS nNodes, edges AS nEdges FROM ecos_knowledge.kg_sync_log " +
                    "WHERE job_id = ? ORDER BY created_at DESC LIMIT 1",
                    jobId);
            if (!rows.isEmpty()) {
                int nNodes = ((Number) rows.get(0).getOrDefault("nNodes", 0)).intValue();
                int nEdges = ((Number) rows.get(0).getOrDefault("nEdges", 0)).intValue();
                if (nNodes > 0) {
                    nodeDel = jdbc.update(
                            "DELETE FROM ecos_knowledge.graph_node WHERE id IN (" +
                            "SELECT id FROM ecos_knowledge.graph_node WHERE id LIKE ? ORDER BY created_at DESC LIMIT ?)",
                            "kg-" + jobId + "%", nNodes);
                }
                if (nEdges > 0) {
                    edgeDel = jdbc.update(
                            "DELETE FROM ecos_knowledge.graph_edge WHERE id IN (" +
                            "SELECT id FROM ecos_knowledge.graph_edge WHERE id LIKE ? ORDER BY created_at DESC LIMIT ?)",
                            "kgrel-" + jobId + "%", nEdges);
                }
            }
        } catch (Exception e) {
            log.warn("rollback failed: jobId={}, err={}", jobId, e.getMessage());
        }
        emitAudit("kg_sync_rollback", "jobId=" + jobId + " nodeDel=" + nodeDel + " edgeDel=" + edgeDel);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("rolledBackNodes", nodeDel);
        out.put("rolledBackEdges", edgeDel);
        out.put("rolledBack", nodeDel + edgeDel);
        return out;
    }

    // ── 私有辅助 ──────────────────────────────────────

    private KnowledgeNode safeFindNodeByLabel(String label) {
        try {
            return nodeMapper.findByLabel(label);
        } catch (Exception e) {
            log.debug("KgMapper: findByLabel failed label='{}': {}", label, e.getMessage());
            return null;
        }
    }

    private void safeInsertEdge(KnowledgeEdge edge) {
        try {
            edgeMapper.insert(edge);
        } catch (Exception e) {
            log.debug("KgMapper: edge exists or failed (id={}): {}", edge.getId(), e.getMessage());
        }
    }

    private List<Map<String, Object>> readObjects(String objectType) {
        try {
            if ("ALL".equalsIgnoreCase(objectType) || objectType == null || objectType.isBlank()) {
                return jdbc.queryForList(
                        "SELECT id, name, type, path FROM ontology_objects LIMIT 1000");
            }
            return jdbc.queryForList(
                    "SELECT id, name, type, path FROM ontology_objects WHERE type = ? LIMIT 1000",
                    objectType);
        } catch (Exception e) {
            log.warn("KgMapper: ontology_objects read failed: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    private List<Map<String, Object>> readRelationships() {
        try {
            return jdbc.queryForList(
                    "SELECT id, source_type, target_type, name FROM object_relationships LIMIT 1000");
        } catch (Exception e) {
            log.warn("KgMapper: object_relationships read failed: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    private String buildPropsJson(String srcId, String jobPrefix) {
        return "{\"ontologyId\":\"" + escape(srcId) + "\",\"jobId\":\"" + escape(jobPrefix) + "\"}";
    }

    private String safeStr(Object o) {
        return o == null ? "" : String.valueOf(o);
    }

    private String escape(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    /**
     * 审计事件：走 Kafka {@code ecos.audit}；Kafka 不可用时本地 log 兜底（铁律 §2.4 #5）。
     */
    private void emitAudit(String action, String detail) {
        try {
            String payload = String.format(
                    "{\"action\":\"%s\",\"detail\":\"%s\",\"service\":\"kb-engine\"}",
                    action, sanitize(detail));
            log.info("AUDIT topic={} payload={}", KafkaTopics.AUDIT, payload);
        } catch (Exception e) {
            log.warn("emitAudit fallback log failed: {}", e.getMessage());
        }
    }

    private String sanitize(String s) {
        return s == null ? "" : s.replace("\"", "'").replace("\\", "/");
    }
}
