package com.chinacreator.gzcm.engine.ontology.dao;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import com.chinacreator.gzcm.engine.ontology.model.LineageEvent;
import com.chinacreator.gzcm.runtime.core.database.support.JdbcTemplateDaoSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/**
 * B3c 数据访问收口（R2/B3c）：血缘事件 {@code kb_lineage_event} CRUD。
 *
 * <p>JSONB 读写原样搬运（INSERT {@code ?::jsonb} 强转、SELECT {@code rs.getString}）；
 * 逻辑删除（{@code is_deleted = 0/1}，铁律 4.8）。</p>
 */
@Repository
public class LineageEventDao extends JdbcTemplateDaoSupport {

    private static final Logger log = LoggerFactory.getLogger(LineageEventDao.class);

    private final RowMapper<LineageEvent> ROW_MAPPER = (rs, rowNum) -> {
        LineageEvent event = new LineageEvent();
        event.setId(rs.getLong("id"));
        event.setEventId(rs.getString("event_id"));
        event.setQuery(rs.getString("query"));
        event.setFormat(rs.getString("format"));
        event.setNodesJson(rs.getString("nodes"));
        event.setEdgesJson(rs.getString("edges"));
        event.setParseAt(toLDT(rs.getTimestamp("parse_at")));
        event.setIsDeleted(rs.getInt("is_deleted"));
        return event;
    };

    private static LocalDateTime toLDT(Timestamp ts) {
        return ts != null ? ts.toLocalDateTime() : null;
    }

    public int insert(String eventId, String query, String format, String nodesJson, String edgesJson) {
        return jdbcTemplate.update(
                "INSERT INTO kb_lineage_event (event_id, query, format, nodes, edges, parse_at, is_deleted) " +
                "VALUES (?, ?, ?, ?::jsonb, ?::jsonb, NOW(), 0)",
                eventId, query, format, nodesJson, edgesJson);
    }

    public int update(String eventId, String query, String format, String nodesJson, String edgesJson) {
        return jdbcTemplate.update(
                "UPDATE kb_lineage_event SET " +
                "  query  = COALESCE(?, query), " +
                "  format = COALESCE(?, format), " +
                "  nodes  = COALESCE(?::jsonb, nodes), " +
                "  edges  = COALESCE(?::jsonb, edges) " +
                "WHERE event_id = ? AND is_deleted = 0",
                query, format, nodesJson, edgesJson, eventId);
    }

    public Optional<LineageEvent> findByEventId(String eventId) {
        List<LineageEvent> list = jdbcTemplate.query(
                "SELECT id, event_id, query, format, nodes, edges, parse_at, is_deleted " +
                "FROM kb_lineage_event WHERE event_id = ? AND is_deleted = 0",
                ROW_MAPPER, eventId);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    public List<LineageEvent> findAll() {
        return jdbcTemplate.query(
                "SELECT id, event_id, query, format, nodes, edges, parse_at, is_deleted " +
                "FROM kb_lineage_event WHERE is_deleted = 0 ORDER BY parse_at DESC LIMIT 200",
                ROW_MAPPER);
    }

    public List<LineageEvent> findByFormat(String format) {
        return jdbcTemplate.query(
                "SELECT id, event_id, query, format, nodes, edges, parse_at, is_deleted " +
                "FROM kb_lineage_event WHERE format = ? AND is_deleted = 0 ORDER BY parse_at DESC LIMIT 200",
                ROW_MAPPER, format);
    }

    public int logicalDelete(String eventId) {
        int rows = jdbcTemplate.update(
                "UPDATE kb_lineage_event SET is_deleted = 1 WHERE event_id = ? AND is_deleted = 0",
                eventId);
        if (rows > 0) {
            log.info("LineageEvent logical deleted: eventId={}", eventId);
        }
        return rows;
    }
}
