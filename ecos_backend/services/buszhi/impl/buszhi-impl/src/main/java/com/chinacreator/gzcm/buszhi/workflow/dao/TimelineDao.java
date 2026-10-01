package com.chinacreator.gzcm.buszhi.workflow.dao;

import com.chinacreator.gzcm.runtime.core.database.support.JdbcTemplateDaoSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * R2-B5 buszhi 数据访问收口（架构铁律 §2.5 #1 / IR06）：
 * {@code ecos_control.ecos_object_timeline} 结构化时间线表 JDBC 落点（V200 后位于
 * 主控制 schema；SQL 内经 search_path 无 schema 前缀）。
 *
 * <p>承接原 {@link com.chinacreator.gzcm.buszhi.workflow.TimelineRepository}，
 * SQL / 分页构造 / JSON 序列化 helper 原样搬运。</p>
 */
@Repository
public class TimelineDao extends JdbcTemplateDaoSupport {

    private static final Logger log = LoggerFactory.getLogger(TimelineDao.class);
    private static final AtomicInteger ID_SEQ = new AtomicInteger(1000);

    public TimelineDao(JdbcTemplate jdbc) {
        this.jdbcTemplate = jdbc;
    }

    private String nextId() { return "tl-" + ID_SEQ.incrementAndGet(); }

    public void record(String objectId, String entityCode, String eventType,
                        String summary, String actor, Map<String, Object> details) {
        String id = nextId();
        try {
            jdbcTemplate.update("""
                INSERT INTO ecos_object_timeline (id, object_id, entity_code, event_type, event_summary, actor, details, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, NOW())
                """, id, objectId, entityCode, eventType, summary, actor,
                details != null ? toJsonString(details) : null);
            log.debug("Timeline recorded: {} [{}] for {}/{}", id, eventType, entityCode, objectId);
        } catch (Exception e) {
            log.warn("Failed to record timeline for {}/{}: {}", entityCode, objectId, e.getMessage());
        }
    }

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
            Long total = jdbcTemplate.queryForObject(countSql, Long.class, objectId);
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(dataSql, objectId, size, offset);

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

    public List<Map<String, Object>> findByEntityAndType(String entityCode, String eventType, int limit) {
        String sql = """
            SELECT id, object_id, entity_code, event_type, event_summary, actor, details, created_at
            FROM ecos_object_timeline
            WHERE entity_code = ? AND event_type = ?
            ORDER BY created_at DESC
            LIMIT ?
            """;
        try {
            return jdbcTemplate.queryForList(sql, entityCode, eventType, limit);
        } catch (Exception e) {
            log.warn("Failed to query timeline by entity/type: {}", e.getMessage());
            return List.of();
        }
    }

    public Map<String, Object> findLatestByObject(String objectId) {
        String sql = """
            SELECT id, object_id, entity_code, event_type, event_summary, actor, details, created_at
            FROM ecos_object_timeline
            WHERE object_id = ?
            ORDER BY created_at DESC
            LIMIT 1
            """;
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, objectId);
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
