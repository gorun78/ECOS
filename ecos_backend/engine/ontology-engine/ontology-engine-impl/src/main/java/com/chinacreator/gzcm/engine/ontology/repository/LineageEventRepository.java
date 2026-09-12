package com.chinacreator.gzcm.engine.ontology.repository;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import com.chinacreator.gzcm.engine.ontology.model.LineageEvent;

/**
 * 血缘事件 JDBC 仓库 — kb_lineage_event 表 CRUD。
 *
 * <p>将 LineageController 的 ConcurrentHashMap 内存存储持久化到 PostgreSQL。
 *
 * <p>JSONB 读写：INSERT 使用 {@code ?::jsonb} 强转，SELECT 使用
 * {@code rs.getString()} 读取 JSON 字符串（与 WorkflowTaskRepository 风格一致）。
 *
 * <p>逻辑删除：所有查询过滤 {@code is_deleted = 0}，
 * 删除操作设置 {@code is_deleted = 1}（铁律 4.8）。</p>
 */
@Repository
public class LineageEventRepository {

    private static final Logger log = LoggerFactory.getLogger(LineageEventRepository.class);

    private final JdbcTemplate jdbc;

    public LineageEventRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** RowMapper — kb_lineage_event 行 → LineageEvent */
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

    /**
     * 插入一条血缘事件记录。
     *
     * @param eventId   事件业务 ID (UUID)
     * @param query     解析时的查询/输入描述
     * @param format    血缘格式: openlineage/atlas
     * @param nodesJson 节点列表 JSON
     * @param edgesJson 边列表 JSON
     * @return 影响行数
     */
    public int insert(String eventId, String query, String format, String nodesJson, String edgesJson) {
        return jdbc.update(
                "INSERT INTO kb_lineage_event (event_id, query, format, nodes, edges, parse_at, is_deleted) " +
                "VALUES (?, ?, ?, ?::jsonb, ?::jsonb, NOW(), 0)",
                eventId, query, format, nodesJson, edgesJson);
    }

    /**
     * 更新一条血缘事件记录。
     *
     * @param eventId   事件业务 ID
     * @param query     查询描述（null 表示不更新）
     * @param format    格式（null 表示不更新）
     * @param nodesJson 节点 JSON（null 表示不更新）
     * @param edgesJson 边 JSON（null 表示不更新）
     * @return 影响行数
     */
    public int update(String eventId, String query, String format, String nodesJson, String edgesJson) {
        // 按 COALESCE 风格更新：NULL 参数不覆盖已有值
        return jdbc.update(
                "UPDATE kb_lineage_event SET " +
                "  query  = COALESCE(?, query), " +
                "  format = COALESCE(?, format), " +
                "  nodes  = COALESCE(?::jsonb, nodes), " +
                "  edges  = COALESCE(?::jsonb, edges) " +
                "WHERE event_id = ? AND is_deleted = 0",
                query, format, nodesJson, edgesJson, eventId);
    }

    /**
     * 按事件业务 ID 查询（仅未删除）。
     */
    public Optional<LineageEvent> findByEventId(String eventId) {
        List<LineageEvent> list = jdbc.query(
                "SELECT id, event_id, query, format, nodes, edges, parse_at, is_deleted " +
                "FROM kb_lineage_event WHERE event_id = ? AND is_deleted = 0",
                ROW_MAPPER, eventId);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    /**
     * 列出所有未删除的血缘事件，按解析时间倒序。
     */
    public List<LineageEvent> findAll() {
        return jdbc.query(
                "SELECT id, event_id, query, format, nodes, edges, parse_at, is_deleted " +
                "FROM kb_lineage_event WHERE is_deleted = 0 ORDER BY parse_at DESC LIMIT 200",
                ROW_MAPPER);
    }

    /**
     * 按格式过滤查询（仅未删除），按解析时间倒序。
     */
    public List<LineageEvent> findByFormat(String format) {
        return jdbc.query(
                "SELECT id, event_id, query, format, nodes, edges, parse_at, is_deleted " +
                "FROM kb_lineage_event WHERE format = ? AND is_deleted = 0 ORDER BY parse_at DESC LIMIT 200",
                ROW_MAPPER, format);
    }

    /**
     * 逻辑删除（铁律 4.8）。
     */
    public int logicalDelete(String eventId) {
        int rows = jdbc.update(
                "UPDATE kb_lineage_event SET is_deleted = 1 WHERE event_id = ? AND is_deleted = 0",
                eventId);
        if (rows > 0) {
            log.info("LineageEvent logical deleted: eventId={}", eventId);
        }
        return rows;
    }
}
