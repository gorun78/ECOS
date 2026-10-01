package com.chinacreator.gzcm.common.service;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import com.chinacreator.gzcm.common.dao.ObjectRuntimeDao;
import com.chinacreator.gzcm.common.event.ObjectCreatedEvent;
import com.chinacreator.gzcm.common.event.ObjectUpdatedEvent;
import com.chinacreator.gzcm.common.event.StatusChangedEvent;
import com.chinacreator.gzcm.common.event.ActionExecutedEvent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

/**
 * Object Runtime 核心服务 — Timeline、版本、关系、附件、事件总线。
 *
 * <p>提供统一的事件记录和发布入口，被 ObjectController、StateMachineEngine、
 * ObjectActionController 等调用。
 *
 * <p>架构铁律 §2.5#1 / IR06 收口（R2 B4c-runtime）：本服务不再直持 {@code JdbcTemplate}，
 * 跨表 raw-SQL 一律经 {@link ObjectRuntimeDao}；本服务保留事件编排 / 图谱组装 /
 * label 解析等业务逻辑。</p>
 */
@Service
public class ObjectRuntimeService {

    private static final Logger log = LoggerFactory.getLogger(ObjectRuntimeService.class);
    private static final AtomicInteger ID_SEQ = new AtomicInteger(500);

    /** 实体代码 → 表名映射（与 ObjectController 保持一致） */
    public static final Map<String, String> ENTITY_TABLE = Map.of(
        "Customer", "demo_customer",
        "Supplier", "demo_supplier",
        "Invoice",  "demo_invoice"
    );

    private final ObjectRuntimeDao dao;
    private final ApplicationEventPublisher eventPublisher;

    public ObjectRuntimeService(ObjectRuntimeDao dao, ApplicationEventPublisher eventPublisher) {
        this.dao = dao;
        this.eventPublisher = eventPublisher;
    }

    private String nextId() { return "ort-" + ID_SEQ.incrementAndGet(); }

    // ═══════════════ Timeline ═══════════════════

    /**
     * 记录一条结构化时间线事件。
     */
    public void recordTimeline(String objectId, String entityCode, String eventType,
                                String summary, String actor, Map<String, Object> details) {
        String id = "tl-" + ID_SEQ.incrementAndGet();
        try {
            dao.insertTimeline(id, objectId, entityCode, eventType, summary, actor,
                details != null ? toJsonString(details) : null);
            log.debug("Timeline recorded: {} [{}] for {}/{}", id, eventType, entityCode, objectId);
        } catch (Exception e) {
            log.warn("Failed to record timeline for {}/{}: {}", entityCode, objectId, e.getMessage());
        }
    }

    /**
     * 查询对象的结构化时间线（分页，按时间倒序）。
     */
    public Map<String, Object> getTimeline(String entityCode, String objectId, int page, int size) {
        int offset = (page - 1) * size;

        try {
            Long total = dao.countTimeline(objectId);
            List<Map<String, Object>> rows = dao.queryTimeline(objectId, size, offset);

            List<Map<String, Object>> data = new ArrayList<>();
            for (Map<String, Object> row : rows) {
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("eventId", row.get("id"));
                entry.put("eventType", row.get("event_type"));
                entry.put("timestamp", row.get("created_at") != null ? row.get("created_at").toString() : null);
                entry.put("actor", row.get("actor"));
                entry.put("summary", row.get("event_summary"));
                entry.put("details", row.get("details"));
                data.add(entry);
            }

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("data", data);
            result.put("total", total != null ? total : 0);
            result.put("page", page);
            result.put("pageSize", size);
            return result;
        } catch (Exception e) {
            log.warn("Failed to query timeline for {}/{}: {}", entityCode, objectId, e.getMessage());
            Map<String, Object> empty = new LinkedHashMap<>();
            empty.put("data", List.of());
            empty.put("total", 0);
            return empty;
        }
    }

    // ═══════════════ Version ═══════════════════

    /**
     * 创建对象版本快照。
     */
    public void createVersion(String objectId, String entityCode, Map<String, Object> snapshot,
                               String changeSummary, String createdBy) {
        String id = "ver-" + ID_SEQ.incrementAndGet();
        try {
            // 获取当前最大版本号
            Long maxVer = dao.queryMaxVersion(objectId);
            int newVer = (maxVer != null ? maxVer.intValue() : 0) + 1;

            dao.insertVersion(id, objectId, entityCode, newVer, toJsonString(snapshot), changeSummary, createdBy);
            log.debug("Version {} created for {}/{}, v{}", id, entityCode, objectId, newVer);
        } catch (Exception e) {
            log.warn("Failed to create version for {}/{}: {}", entityCode, objectId, e.getMessage());
        }
    }

    /**
     * 查询对象版本列表。
     */
    public List<Map<String, Object>> getVersions(String entityCode, String objectId) {
        try {
            return dao.queryVersions(objectId);
        } catch (Exception e) {
            log.warn("Failed to query versions for {}/{}: {}", entityCode, objectId, e.getMessage());
            return List.of();
        }
    }

    /**
     * 获取特定版本。
     */
    public Map<String, Object> getVersion(String entityCode, String objectId, int versionNo) {
        try {
            List<Map<String, Object>> rows = dao.queryVersion(objectId, versionNo);
            return rows.isEmpty() ? null : rows.get(0);
        } catch (Exception e) {
            log.warn("Failed to get version {} for {}/{}: {}", versionNo, entityCode, objectId, e.getMessage());
            return null;
        }
    }

    // ═══════════════ Relationship ═══════════════════

    /**
     * 创建对象间关系。
     */
    public Map<String, Object> createRelationship(String sourceObjectId, String targetObjectId,
                                                    String sourceEntityCode, String targetEntityCode,
                                                    String relationshipCode, String relationshipType,
                                                    Map<String, Object> properties) {
        String id = "rel-" + ID_SEQ.incrementAndGet();
        try {
            dao.insertRelationship(id, sourceObjectId, targetObjectId, sourceEntityCode, targetEntityCode,
                relationshipCode, relationshipType, properties != null ? toJsonString(properties) : null);
            log.info("Relationship created: {} [{}→{} via {}]", id, sourceObjectId, targetObjectId, relationshipCode);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("id", id);
            result.put("sourceObjectId", sourceObjectId);
            result.put("targetObjectId", targetObjectId);
            result.put("sourceEntityCode", sourceEntityCode);
            result.put("targetEntityCode", targetEntityCode);
            result.put("relationshipCode", relationshipCode);
            result.put("relationshipType", relationshipType);
            return result;
        } catch (Exception e) {
            log.error("Failed to create relationship: {}", e.getMessage());
            throw new RuntimeException("关系创建失败: " + e.getMessage());
        }
    }

    /**
     * 查询对象的所有直接关系。
     */
    public List<Map<String, Object>> getRelationships(String entityCode, String objectId) {
        try {
            List<Map<String, Object>> rows = dao.queryRelationships(objectId);
            List<Map<String, Object>> result = new ArrayList<>();
            for (Map<String, Object> row : rows) {
                Map<String, Object> rel = new LinkedHashMap<>();
                rel.put("id", row.get("id"));
                rel.put("sourceObjectId", row.get("source_object_id"));
                rel.put("targetObjectId", row.get("target_object_id"));
                rel.put("sourceEntityCode", row.get("source_entity_code"));
                rel.put("targetEntityCode", row.get("target_entity_code"));
                rel.put("relationshipCode", row.get("relationship_code"));
                rel.put("relationshipType", row.get("relationship_type"));
                rel.put("properties", row.get("properties"));

                // Direction indicator
                if (objectId.equals(row.get("source_object_id"))) {
                    rel.put("direction", "source");
                    rel.put("relatedObjectId", row.get("target_object_id"));
                } else {
                    rel.put("direction", "target");
                    rel.put("relatedObjectId", row.get("source_object_id"));
                }
                result.add(rel);
            }
            return result;
        } catch (Exception e) {
            log.warn("Failed to query relationships for {}/{}: {}", entityCode, objectId, e.getMessage());
            return List.of();
        }
    }

    /**
     * 获取以对象为中心的关系图谱（N层展开）。
     * 使用 PostgreSQL 递归 CTE 实现多层关系展开。
     */
    public Map<String, Object> getGraph(String entityCode, String objectId, int depth) {
        if (depth < 1) depth = 1;
        if (depth > 5) depth = 5; // Safety limit

        // Get center node info
        Object centerLabel = objectId;
        String table = ENTITY_TABLE.get(entityCode);
        if (table != null) {
            try {
                List<Map<String, Object>> objRows = dao.queryEntityName(table, objectId);
                if (!objRows.isEmpty() && objRows.get(0).get("name") != null) {
                    centerLabel = objRows.get(0).get("name");
                }
            } catch (Exception e) {
                log.debug("Cannot resolve label for {}/{}: {}", entityCode, objectId, e.getMessage());
            }
        }

        Map<String, Object> centerNode = new LinkedHashMap<>();
        centerNode.put("id", objectId);
        centerNode.put("entityCode", entityCode);
        centerNode.put("label", centerLabel);

        // Collect nodes and edges from CTE result
        Set<String> nodeIds = new LinkedHashSet<>();
        List<Map<String, Object>> nodes = new ArrayList<>();
        List<Map<String, Object>> edges = new ArrayList<>();

        // Center node always included
        nodeIds.add(objectId);
        nodes.add(centerNode);

        try {
            List<Map<String, Object>> rels = dao.queryGraphEdges(objectId, depth);

            for (Map<String, Object> rel : rels) {
                String srcId = (String) rel.get("source_object_id");
                String tgtId = (String) rel.get("target_object_id");
                String srcEntity = (String) rel.get("source_entity_code");
                String tgtEntity = (String) rel.get("target_entity_code");

                // Add nodes
                if (nodeIds.add(srcId)) {
                    nodes.add(buildNode(srcId, srcEntity));
                }
                if (nodeIds.add(tgtId)) {
                    nodes.add(buildNode(tgtId, tgtEntity));
                }

                // Add edge
                Map<String, Object> edge = new LinkedHashMap<>();
                edge.put("id", rel.get("id"));
                edge.put("source", srcId);
                edge.put("target", tgtId);
                edge.put("label", rel.get("relationship_code"));
                edge.put("type", rel.get("relationship_type"));
                edge.put("level", rel.get("level"));
                edges.add(edge);
            }
        } catch (Exception e) {
            log.warn("Graph CTE traversal error for {}/{}: {}", entityCode, objectId, e.getMessage());
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("centerNode", centerNode);
        result.put("nodes", nodes);
        result.put("edges", edges);
        result.put("depth", depth);
        result.put("totalNodes", nodes.size());
        result.put("totalEdges", edges.size());
        return result;
    }

    private Map<String, Object> buildNode(String objectId, String entityCode) {
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("id", objectId);
        node.put("entityCode", entityCode);
        // Try to resolve label
        String table = ENTITY_TABLE.get(entityCode);
        if (table != null) {
            try {
                List<Map<String, Object>> rows = dao.queryEntityName(table, objectId);
                if (!rows.isEmpty() && rows.get(0).get("name") != null) {
                    node.put("label", rows.get(0).get("name"));
                    return node;
                }
            } catch (Exception e) {
                // ignore
            }
        }
        node.put("label", objectId);
        return node;
    }

    /**
     * 删除关系。
     */
    public boolean deleteRelationship(String relId) {
        int rows = dao.deleteRelationship(relId);
        if (rows > 0) {
            log.info("Relationship deleted: {}", relId);
        }
        return rows > 0;
    }

    // ═══════════════ Attachment ═══════════════════

    /**
     * 查询对象附件列表。
     */
    public List<Map<String, Object>> getAttachments(String entityCode, String objectId) {
        try {
            return dao.queryAttachments(objectId);
        } catch (Exception e) {
            log.warn("Failed to query attachments for {}/{}: {}", entityCode, objectId, e.getMessage());
            return List.of();
        }
    }

    // ═══════════════ Event Bus ═══════════════════

    public void publishObjectCreated(String objectId, String entityCode, Map<String, Object> payload) {
        try {
            ObjectCreatedEvent event = new ObjectCreatedEvent(this, objectId, entityCode, payload);
            eventPublisher.publishEvent(event);
            log.info("Event published: ObjectCreated for {}/{}", entityCode, objectId);
        } catch (Exception e) {
            log.warn("Failed to publish ObjectCreated event: {}", e.getMessage());
        }
    }

    public void publishObjectUpdated(String objectId, String entityCode, List<String> changedFields) {
        try {
            ObjectUpdatedEvent event = new ObjectUpdatedEvent(this, objectId, entityCode, changedFields);
            eventPublisher.publishEvent(event);
            log.info("Event published: ObjectUpdated for {}/{}", entityCode, objectId);
        } catch (Exception e) {
            log.warn("Failed to publish ObjectUpdated event: {}", e.getMessage());
        }
    }

    public void publishStatusChanged(String objectId, String entityCode, String fromStatus, String toStatus) {
        try {
            StatusChangedEvent event = new StatusChangedEvent(this, objectId, entityCode, fromStatus, toStatus);
            eventPublisher.publishEvent(event);
            log.info("Event published: StatusChanged for {}/{}: {}→{}", entityCode, objectId, fromStatus, toStatus);
        } catch (Exception e) {
            log.warn("Failed to publish StatusChanged event: {}", e.getMessage());
        }
    }

    public void publishActionExecuted(String objectId, String entityCode, String actionCode, String result, Map<String, Object> params) {
        try {
            ActionExecutedEvent event = new ActionExecutedEvent(this, objectId, entityCode, actionCode, result, params);
            eventPublisher.publishEvent(event);
            log.info("Event published: ActionExecuted for {}/{}: {}", entityCode, objectId, actionCode);
        } catch (Exception e) {
            log.warn("Failed to publish ActionExecuted event: {}", e.getMessage());
        }
    }

    // ═══════════════ Helpers ═══════════════════

    /**
     * 将 Map 转为 JSON 字符串（用于 JSONB 插入）。
     */
    private String toJsonString(Map<String, Object> map) {
        if (map == null) return "{}";
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> e : map.entrySet()) {
            if (!first) sb.append(", ");
            sb.append("\"").append(escapeJson(e.getKey())).append("\": ");
            appendJsonValue(sb, e.getValue());
            first = false;
        }
        sb.append("}");
        return sb.toString();
    }

    private void appendJsonValue(StringBuilder sb, Object val) {
        if (val == null) {
            sb.append("null");
        } else if (val instanceof Number) {
            sb.append(val);
        } else if (val instanceof Boolean) {
            sb.append(val);
        } else if (val instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> m = (Map<String, Object>) val;
            sb.append(toJsonString(m));
        } else if (val instanceof List) {
            sb.append("[");
            boolean first = true;
            for (Object item : (List<?>) val) {
                if (!first) sb.append(", ");
                appendJsonValue(sb, item);
                first = false;
            }
            sb.append("]");
        } else {
            sb.append("\"").append(escapeJson(val.toString())).append("\"");
        }
    }

    private String escapeJson(String s) {
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
