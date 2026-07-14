package com.chinacreator.gzcm.buszhi.workflow;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 结构化 Timeline 仓库 — 替代旧 td_audit_log 的 LIKE 查询。
 *
 * <p>所有 timeline 事件写入 ecos_object_timeline 表，
 * 提供按对象/实体/事件类型查询的结构化能力。
 */
@Repository
public class TimelineRepository {

    private static final Logger log = LoggerFactory.getLogger(TimelineRepository.class);
    private static final AtomicInteger ID_SEQ = new AtomicInteger(1000);

    private final JdbcTemplate jdbc;

    public TimelineRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private String nextId() { return "tl-" + ID_SEQ.incrementAndGet(); }

    /**
     * 记录一条结构化时间线事件。
     *
     * @param objectId    对象ID
     * @param entityCode  实体代码
     * @param eventType   事件类型 (ObjectCreated/ObjectUpdated/StatusChanged/ActionExecuted)
     * @param summary     事件摘要
     * @param actor       操作人
     * @param details     事件详情 (JSON)
     */
    public void record(String objectId, String entityCode, String eventType,
                        String summary, String actor, Map<String, Object> details) {
        String id = nextId();
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
    public Map<String, Object> findByObject(String entityCode, String objectId, int page, int size) {
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

    /**
     * 按实体 + 事件类型查询 timeline（用于审计/统计）。
     */
    public List<Map<String, Object>> findByEntityAndType(String entityCode, String eventType, int limit) {
        String sql = """
            SELECT id, object_id, entity_code, event_type, event_summary, actor, details, created_at
            FROM ecos_object_timeline
            WHERE entity_code = ? AND event_type = ?
            ORDER BY created_at DESC
            LIMIT ?
            """;
        try {
            return jdbc.queryForList(sql, entityCode, eventType, limit);
        } catch (Exception e) {
            log.warn("Failed to query timeline by entity/type: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * 获取对象最后一条 timeline 事件（用于获取当前状态等）。
     */
    public Map<String, Object> findLatestByObject(String objectId) {
        String sql = """
            SELECT id, object_id, entity_code, event_type, event_summary, actor, details, created_at
            FROM ecos_object_timeline
            WHERE object_id = ?
            ORDER BY created_at DESC
            LIMIT 1
            """;
        try {
            List<Map<String, Object>> rows = jdbc.queryForList(sql, objectId);
            return rows.isEmpty() ? null : rows.get(0);
        } catch (Exception e) {
            log.warn("Failed to get latest timeline for {}: {}", objectId, e.getMessage());
            return null;
        }
    }

    // ═══════════════ Helpers ═══════════════════

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
