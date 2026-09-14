package com.chinacreator.gzcm.common.service;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import com.chinacreator.gzcm.common.event.ObjectCreatedEvent;
import com.chinacreator.gzcm.common.event.ObjectUpdatedEvent;
import com.chinacreator.gzcm.common.event.StatusChangedEvent;
import com.chinacreator.gzcm.common.event.ActionExecutedEvent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Object Runtime 核心服务 — Timeline、版本、关系、附件、事件总线。
 *
 * <p>提供统一的事件记录和发布入口，被 ObjectController、StateMachineEngine、
 * ObjectActionController 等调用。
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

    private final JdbcTemplate jdbc;
    private final ApplicationEventPublisher eventPublisher;

    public ObjectRuntimeService(JdbcTemplate jdbc, ApplicationEventPublisher eventPublisher) {
        this.jdbc = jdbc;
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
            jdbc.update("""
                INSERT INTO ecos_object_timeline (id, object_id, entity_code, event_type, event_summary, actor, details, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?::jsonb, NOW())
                """, id, objectId, entityCode, eventType, summary, actor,
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
        String countSql = "SELECT COUNT(*) FROM ecos_object_timeline WHERE object_id = ?";
        String dataSql = """
            SELECT id, object_id, entity_code, event_type, event_summary, actor, details, created_at
            FROM ecos_object_timeline
            WHERE object_id = ?
            ORDER BY created_at DESC
            LIMIT ? OFFSET ?
            """;

        try {
            Long total = jdbc.queryForObject(countSql, Long.class, objectId);
            List<Map<String, Object>> rows = jdbc.queryForList(dataSql, objectId, size, offset);

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
            Long maxVer = jdbc.queryForObject(
                "SELECT COALESCE(MAX(version_no), 0) FROM ecos_object_version WHERE object_id = ?",
                Long.class, objectId);
            int newVer = (maxVer != null ? maxVer.intValue() : 0) + 1;

            jdbc.update("""
                INSERT INTO ecos_object_version (id, object_id, entity_code, version_no, snapshot, change_summary, created_by, created_at)
                VALUES (?, ?, ?, ?, ?::jsonb, ?, ?, NOW())
                """, id, objectId, entityCode, newVer, toJsonString(snapshot), changeSummary, createdBy);
            log.debug("Version {} created for {}/{}, v{}", id, entityCode, objectId, newVer);
        } catch (Exception e) {
            log.warn("Failed to create version for {}/{}: {}", entityCode, objectId, e.getMessage());
        }
    }

    /**
     * 查询对象版本列表。
     */
    public List<Map<String, Object>> getVersions(String entityCode, String objectId) {
        String sql = """
            SELECT id, object_id, entity_code, version_no, change_summary, created_by, created_at
            FROM ecos_object_version
            WHERE object_id = ?
            ORDER BY version_no DESC
            """;
        try {
            return jdbc.queryForList(sql, objectId);
        } catch (Exception e) {
            log.warn("Failed to query versions for {}/{}: {}", entityCode, objectId, e.getMessage());
            return List.of();
        }
    }

    /**
     * 获取特定版本。
     */
    public Map<String, Object> getVersion(String entityCode, String objectId, int versionNo) {
        String sql = """
            SELECT id, object_id, entity_code, version_no, snapshot, change_summary, created_by, created_at
            FROM ecos_object_version
            WHERE object_id = ? AND version_no = ?
            """;
        try {
            List<Map<String, Object>> rows = jdbc.queryForList(sql, objectId, versionNo);
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
            jdbc.update("""
                INSERT INTO ecos_object_relationship (id, source_object_id, target_object_id, source_entity_code, target_entity_code, relationship_code, relationship_type, properties, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?::jsonb, NOW())
                """, id, sourceObjectId, targetObjectId, sourceEntityCode, targetEntityCode,
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
        String sql = """
            SELECT r.id, r.source_object_id, r.target_object_id, r.source_entity_code, r.target_entity_code,
                   r.relationship_code, r.relationship_type, r.properties, r.created_at
            FROM ecos_object_relationship r
            WHERE r.source_object_id = ? OR r.target_object_id = ?
            ORDER BY r.created_at DESC
            """;
        try {
            List<Map<String, Object>> rows = jdbc.queryForList(sql, objectId, objectId);
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
                List<Map<String, Object>> objRows = jdbc.queryForList(
                    "SELECT name FROM " + table + " WHERE id = ?", objectId);
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

        // ★ PostgreSQL Recursive CTE for graph traversal
        String cteSql = """
            WITH RECURSIVE graph_traverse AS (
                -- Anchor: edges directly connected to the center node
                SELECT
                    r.id,
                    r.source_object_id,
                    r.target_object_id,
                    r.source_entity_code,
                    r.target_entity_code,
                    r.relationship_code,
                    r.relationship_type,
                    1 AS level
                FROM ecos_object_relationship r
                WHERE r.source_object_id = ? OR r.target_object_id = ?
                UNION
                -- Recursive: edges connected to previously discovered nodes
                SELECT
                    r.id,
                    r.source_object_id,
                    r.target_object_id,
                    r.source_entity_code,
                    r.target_entity_code,
                    r.relationship_code,
                    r.relationship_type,
                    g.level + 1
                FROM ecos_object_relationship r
                INNER JOIN graph_traverse g ON (
                    r.source_object_id = g.source_object_id
                    OR r.source_object_id = g.target_object_id
                    OR r.target_object_id = g.source_object_id
                    OR r.target_object_id = g.target_object_id
                )
                AND r.id <> g.id
                WHERE g.level < ?
            )
            SELECT DISTINCT ON (id) id, source_object_id, target_object_id,
                   source_entity_code, target_entity_code,
                   relationship_code, relationship_type, level
            FROM graph_traverse
            ORDER BY id, level
            """;

        // Collect nodes and edges from CTE result
        Set<String> nodeIds = new LinkedHashSet<>();
        List<Map<String, Object>> nodes = new ArrayList<>();
        List<Map<String, Object>> edges = new ArrayList<>();

        // Center node always included
        nodeIds.add(objectId);
        nodes.add(centerNode);

        try {
            List<Map<String, Object>> rels = jdbc.queryForList(cteSql, objectId, objectId, depth);

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
                List<Map<String, Object>> rows = jdbc.queryForList(
                    "SELECT name FROM " + table + " WHERE id = ?", objectId);
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
        int rows = jdbc.update("DELETE FROM ecos_object_relationship WHERE id = ?", relId);
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
        String sql = """
            SELECT id, object_id, entity_code, file_name, file_path, file_size, mime_type, version_no, uploaded_by, created_at
            FROM ecos_object_attachment
            WHERE object_id = ?
            ORDER BY created_at DESC
            """;
        try {
            return jdbc.queryForList(sql, objectId);
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
